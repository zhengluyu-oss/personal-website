"""Run on the blog server: inspect, stage SQL with backup, or finalize after deployment.

Only article 62 in category 21 is in scope. Never print application credentials.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
from datetime import datetime
from urllib.parse import urlparse
from urllib.request import urlopen

CONFIG = Path('/home/wwwroot/zhengluyu-website/config/application-prod.yml')
BACKUPS = Path('/home/wwwroot/zhengluyu-website/backups')


def connection():
    lines = CONFIG.read_text(encoding='utf-8').splitlines()
    start = lines.index('  datasource:')
    values = {}
    for line in lines[start + 1:]:
        if not line.strip() or line.lstrip().startswith('#'):
            continue
        indent = len(line) - len(line.lstrip())
        if indent <= 2:
            break
        if indent == 4 and ':' in line:
            key, value = line.strip().split(':', 1)
            values[key] = value.strip().strip('"\'')
    url = urlparse(values['url'].removeprefix('jdbc:'))
    return url.hostname or '127.0.0.1', url.path.lstrip('/'), values['username'], values['password']


def mysql(sql):
    host, database, username, password = connection()
    result = subprocess.run(['mysql', '--default-character-set=utf8mb4', '--batch', '--skip-column-names',
                             '--host', host, '--user', username, '--database', database],
                            input=sql, env=dict(os.environ, MYSQL_PWD=password),
                            text=True, capture_output=True)
    if result.returncode:
        raise RuntimeError(result.stderr.strip())
    return result.stdout.strip()


def inspect():
    print('Source:', mysql("SELECT id,article_title,status,CHAR_LENGTH(article_content) FROM t_article WHERE category_id=21"))
    print('Category:', mysql("SELECT id,category_name FROM t_category WHERE id=21"))
    for table in ['t_comment', 't_like', 't_favorite']:
        print(table, mysql(f'SELECT COUNT(*) FROM {table} WHERE type=1 AND type_id=62'))


def backup():
    host, database, username, password = connection()
    BACKUPS.mkdir(parents=True, exist_ok=True)
    path = BACKUPS / ('website-share-' + datetime.now().strftime('%Y%m%d-%H%M%S') + '.sql')
    fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(fd, 'wb') as output:
        result = subprocess.run(['mysqldump', '--single-transaction', '--no-tablespaces',
                                 '--host', host, '--user', username, database],
                                env=dict(os.environ, MYSQL_PWD=password), stdout=output, stderr=subprocess.PIPE)
    if result.returncode or path.stat().st_size < 1000:
        raise RuntimeError('Database backup failed; migration stopped')
    print('Database backup:', path)
    return path


def stage(path):
    if mysql("SELECT COUNT(*) FROM t_article WHERE category_id=21 AND is_deleted=0") != '1':
        raise RuntimeError('Source category no longer contains exactly one article; inspect before migration')
    backup_path = backup()
    mysql(path.read_text(encoding='utf-8'))
    print('Staged:', mysql('SELECT id,source_article_id,title,CHAR_LENGTH(content) FROM t_website_share WHERE source_article_id=62'))
    print('Use this backup path for finalize:', backup_path)


def finalize(backup_path):
    resolved = backup_path.resolve(strict=True)
    if resolved.parent != BACKUPS.resolve() or not resolved.name.startswith('website-share-') or resolved.stat().st_size < 1000:
        raise RuntimeError('A valid server-side migration backup is required')
    for table in ['t_comment', 't_like', 't_favorite']:
        if mysql(f'SELECT COUNT(*) FROM {table} WHERE type=1 AND type_id=62') != '0':
            raise RuntimeError('Source article has interactions; preserve them and review migration first')
    target = mysql('SELECT id FROM t_website_share WHERE source_article_id=62 AND status=1 AND is_deleted=0')
    if not target.isdigit():
        raise RuntimeError('Published migration target missing')
    with urlopen(f'https://www.zhengluyu.com/api/website-share/{target}', timeout=20) as response:
        payload = json.load(response)
    if payload.get('code') != 200 or not payload.get('data', {}).get('content'):
        raise RuntimeError('Public detail API verification failed')
    content_hash = hashlib.sha256(payload['data']['content'].encode()).hexdigest()
    if content_hash != mysql('SELECT SHA2(article_content,256) FROM t_article WHERE id=62 AND category_id=21'):
        raise RuntimeError('Public content differs from source; old data preserved')
    sql = '''
START TRANSACTION;
SELECT id FROM t_article WHERE id=62 FOR UPDATE;
SELECT id FROM t_website_share WHERE source_article_id=62 FOR UPDATE;
SET @ready = (SELECT COUNT(*) FROM t_article a JOIN t_website_share s ON s.source_article_id=a.id
 JOIN t_category c ON c.id=a.category_id
 WHERE a.id=62 AND a.category_id=21 AND c.category_name='网站分享'
 AND s.status=1 AND s.is_deleted=0 AND a.article_title=s.title
 AND BINARY a.article_content=BINARY s.content
 AND (a.article_cover <=> s.cover_image) AND (a.seo_title <=> s.seo_title)
 AND (a.seo_description <=> s.seo_description) AND (a.seo_keywords <=> s.seo_keywords)
 AND (SELECT COUNT(*) FROM t_article WHERE category_id=21)=1);
DELETE FROM t_article_tag WHERE article_id=62 AND @ready=1;
DELETE FROM t_article WHERE id=62 AND category_id=21 AND @ready=1;
DELETE FROM t_category WHERE id=21 AND category_name='网站分享' AND @ready=1
 AND NOT EXISTS (SELECT 1 FROM t_article WHERE category_id=21);
SELECT @ready;
COMMIT;
'''
    if mysql(sql).splitlines()[-1] != '1':
        raise RuntimeError('Final comparison failed; source records preserved')
    print('Migrated target:', target)
    print('Removed source article 62, its tag relations, and category 21. Recovery backup:', resolved)


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('action', choices=['inspect', 'stage', 'finalize'])
    parser.add_argument('path', nargs='?')
    args = parser.parse_args()
    if args.action == 'inspect':
        inspect()
    elif args.action == 'stage':
        stage(Path(args.path))
    else:
        finalize(Path(args.path))
