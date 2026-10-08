"""One-off publisher for the domain/HTTPS/ICMP article.

Run on the blog server. Reads existing application credentials without printing
them, uploads the generated cover to the configured OSS bucket, then inserts
the article and its existing category/tag relation in one MySQL transaction.
"""

from __future__ import annotations

import argparse
import base64
import hashlib
import hmac
import os
from pathlib import Path
import subprocess
from urllib.parse import quote, urlparse
import urllib.error
import urllib.request
import uuid
from email.utils import formatdate


CONFIG = Path('/home/wwwroot/zhengluyu-website/config/application-prod.yml')
TITLE = '从域名到 HTTPS：DNS、HTTP 和 ICMP 各做了什么'
SEO_DESCRIPTION = '从个人网站升级 HTTPS 的实践出发，讲清域名与 DNS 解析、HTTP 与 HTTPS、TLS 证书、ICMP 和 ping 的关系，以及腾讯云轻量服务器的配置与排查顺序。'
SEO_KEYWORDS = '域名,DNS,HTTP,HTTPS,TLS,ICMP,ping,腾讯云轻量应用服务器'
CATEGORY_ID = 20  # 网络防护
TAG_ID = 17       # 技术学习
ARTICLE_ID = 89


def section_values(name: str, indent: int) -> dict[str, str]:
    lines = CONFIG.read_text(encoding='utf-8').splitlines()
    marker = ' ' * indent + name + ':'
    try:
        start = next(i for i, line in enumerate(lines) if line == marker)
    except StopIteration as exc:
        raise RuntimeError(f'Config section missing: {name}') from exc
    result = {}
    for line in lines[start + 1:]:
        if not line.strip() or line.lstrip().startswith('#'):
            continue
        leading = len(line) - len(line.lstrip())
        if leading <= indent:
            break
        if leading == indent + 2 and ':' in line:
            key, value = line.strip().split(':', 1)
            result[key] = value.strip().strip('"\'')
    return result


def db_config() -> tuple[str, str, str, str]:
    values = section_values('datasource', 2)
    parsed = urlparse(values['url'].removeprefix('jdbc:'))
    return parsed.hostname or '127.0.0.1', parsed.path.lstrip('/'), values['username'], values['password']


def mysql(sql: str) -> str:
    host, database, username, password = db_config()
    env = dict(os.environ, MYSQL_PWD=password)
    result = subprocess.run(
        ['mysql', '--default-character-set=utf8mb4', '--batch', '--skip-column-names',
         '--host', host, '--user', username, '--database', database, '--execute', sql],
        env=env, text=True, capture_output=True, check=False,
    )
    if result.returncode:
        raise RuntimeError(f'MySQL failed: {result.stderr.strip()}')
    return result.stdout.strip()


def literal(value: str) -> str:
    return "CONVERT(0x" + value.encode('utf-8').hex() + " USING utf8mb4)"


def inspect() -> None:
    print('Database:', db_config()[1])
    print('Articles:', mysql("SELECT id, user_id, article_title, status FROM t_article WHERE is_deleted=0 ORDER BY id DESC LIMIT 5"))
    print('Category:', mysql(f'SELECT id, category_name FROM t_category WHERE id={CATEGORY_ID} AND is_deleted=0'))
    print('Tag:', mysql(f'SELECT id, tag_name FROM t_tag WHERE id={TAG_ID} AND is_deleted=0'))
    print('Existing title count:', mysql(f'SELECT COUNT(*) FROM t_article WHERE article_title={literal(TITLE)} AND is_deleted=0'))
    print('Article columns:', mysql('SHOW COLUMNS FROM t_article'))
    print('Article tag columns:', mysql('SHOW COLUMNS FROM t_article_tag'))
    oss = section_values('oss', 0)
    print('Cover public domain:', oss.get('domain', '<default endpoint>'))
    print('Cover bucket:', oss['bucket-name'])


def upload_cover(path: Path) -> str:
    body = path.read_bytes()
    if not 0 < len(body) < 300 * 1024:
        raise ValueError('Cover must be smaller than 300 KiB')
    oss = section_values('oss', 0)
    endpoint = oss['endpoint'].removeprefix('https://').removeprefix('http://').rstrip('/')
    bucket = oss['bucket-name']
    key = 'article/articleCover/' + str(uuid.uuid4()) + '.webp'
    date = formatdate(usegmt=True)
    content_type = 'image/webp'
    canonical = f'PUT\n\n{content_type}\n{date}\n/{bucket}/{key}'
    signature = base64.b64encode(hmac.new(oss['secret-key'].encode(), canonical.encode(), hashlib.sha1).digest()).decode()
    url = f'https://{bucket}.{endpoint}/{quote(key, safe="/")}'
    request = urllib.request.Request(url, data=body, method='PUT', headers={
        'Date': date,
        'Content-Type': content_type,
        'Authorization': f"OSS {oss['access-key']}:{signature}",
    })
    try:
        with urllib.request.urlopen(request, timeout=20) as response:
            if response.status != 200:
                raise RuntimeError(f'OSS returned HTTP {response.status}')
    except urllib.error.HTTPError as exc:
        raise RuntimeError(f'OSS upload failed with HTTP {exc.code}') from exc
    public_base = oss.get('domain', '').rstrip('/') or f'https://{bucket}.{endpoint}'
    public_url = public_base + '/' + key
    with urllib.request.urlopen(public_url, timeout=20) as response:
        if response.status != 200 or response.read() != body:
            raise RuntimeError('Public cover verification failed')
    print(public_url)
    return public_url


def publish(content_path: Path, cover_url: str) -> None:
    content = content_path.read_text(encoding='utf-8').strip()
    if len(content) < 2000:
        raise ValueError('Article content unexpectedly short')
    if not cover_url.startswith('https://') or '/article/articleCover/' not in cover_url:
        raise ValueError('Cover URL is not a configured HTTPS article cover')
    if mysql(f'SELECT COUNT(*) FROM t_article WHERE article_title={literal(TITLE)} AND is_deleted=0') != '0':
        raise RuntimeError('Article title already exists; refusing duplicate publish')
    author = mysql('SELECT user_id FROM t_article WHERE status=1 AND is_deleted=0 ORDER BY id DESC LIMIT 1')
    if not author.isdigit():
        raise RuntimeError('No existing published article author found')
    if not mysql(f'SELECT id FROM t_category WHERE id={CATEGORY_ID} AND is_deleted=0'):
        raise RuntimeError('Category missing')
    if not mysql(f'SELECT id FROM t_tag WHERE id={TAG_ID} AND is_deleted=0'):
        raise RuntimeError('Tag missing')
    sql = f"""
START TRANSACTION;
INSERT INTO t_article
  (user_id, category_id, article_cover, article_title, seo_title,
   seo_description, seo_keywords, article_content, article_type, is_top,
   status, visit_count, create_time, update_time, is_deleted)
VALUES
  ({author}, {CATEGORY_ID}, {literal(cover_url)}, {literal(TITLE)}, {literal(TITLE)},
   {literal(SEO_DESCRIPTION)}, {literal(SEO_KEYWORDS)}, {literal(content)},
   1, 0, 1, 0, NOW(), NOW(), 0);
SET @article_id = LAST_INSERT_ID();
INSERT INTO t_article_tag (article_id, tag_id, create_time, is_deleted)
VALUES (@article_id, {TAG_ID}, NOW(), 0);
SELECT @article_id;
COMMIT;
"""
    article_id = mysql(sql).splitlines()[-1]
    if not article_id.isdigit():
        raise RuntimeError('Could not read created article ID')
    print('Published article ID:', article_id)


def update_content(content_path: Path) -> None:
    content = content_path.read_text(encoding='utf-8').strip()
    if len(content) < 2000:
        raise ValueError('Article content unexpectedly short')
    matching = mysql(
        f'SELECT COUNT(*) FROM t_article WHERE id={ARTICLE_ID} '
        f'AND article_title={literal(TITLE)} AND status=1 AND is_deleted=0'
    )
    if matching != '1':
        raise RuntimeError('Published article identity did not match; refusing update')
    result = mysql(f'''
START TRANSACTION;
UPDATE t_article SET article_content={literal(content)}, update_time=NOW()
WHERE id={ARTICLE_ID} AND article_title={literal(TITLE)} AND status=1 AND is_deleted=0;
SELECT ROW_COUNT();
COMMIT;
''')
    if result.splitlines()[-1] != '1':
        raise RuntimeError('Article content was not updated exactly once')
    print('Updated article ID:', ARTICLE_ID)


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('action', choices=['inspect', 'upload', 'publish', 'update'])
    parser.add_argument('path', nargs='?')
    parser.add_argument('cover_url', nargs='?')
    args = parser.parse_args()
    if args.action == 'inspect':
        inspect()
    elif args.action == 'upload':
        upload_cover(Path(args.path))
    elif args.action == 'publish':
        publish(Path(args.path), args.cover_url)
    elif args.action == 'update':
        update_content(Path(args.path))
