"""Update only the existing published cryptography article on the blog server."""

from __future__ import annotations

import argparse
import importlib.util
from pathlib import Path


ARTICLE_ID = 61
TITLE = '加密算法'
DESCRIPTION = '从 JWT 登录切入，系统梳理对称与非对称加密、混合加密、密钥协商、哈希、HMAC、数字签名和 HTTPS，厘清各类密码学技术的用途与常见误区。'
KEYWORDS = 'JWT,加密算法,对称加密,非对称加密,混合加密,密码哈希,HMAC,数字签名,AES-GCM,HTTPS'
EXPECTED_OLD_SHA256 = '2a5a9585d24bb7578bc69763243997fb4355e53a00446c9567ecfe2291c2ff60'


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument('content', type=Path)
    parser.add_argument('--apply', action='store_true')
    args = parser.parse_args()

    helper_path = Path('/tmp/migrate-website-share.py')
    spec = importlib.util.spec_from_file_location('blog_db_helper', helper_path)
    if spec is None or spec.loader is None:
        raise RuntimeError('Database helper unavailable')
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    mysql = module.mysql

    content = args.content.read_text(encoding='utf-8').strip()
    if len(content) < 5000 or 'RFC 8725' not in content:
        raise RuntimeError('Content appears incomplete')

    def lit(value: str) -> str:
        return 'CONVERT(0x' + value.encode('utf-8').hex() + ' USING utf8mb4)'

    original = mysql(
        f'SELECT COUNT(*) FROM t_article WHERE id={ARTICLE_ID} '
        f'AND article_title={lit(TITLE)} AND SHA2(article_content, 256)={lit(EXPECTED_OLD_SHA256)} '
        'AND status=1 AND is_deleted=0'
    ).strip()
    if original != '1':
        raise RuntimeError('Article no longer matches the reviewed placeholder; refusing update')
    print('Verified article 61 and prior published content; new characters:', len(content))
    if not args.apply:
        return

    sql = f'''
START TRANSACTION;
UPDATE t_article SET article_content={lit(content)},
    seo_description={lit(DESCRIPTION)}, seo_keywords={lit(KEYWORDS)}, update_time=NOW()
WHERE id={ARTICLE_ID} AND article_title={lit(TITLE)}
  AND SHA2(article_content, 256)={lit(EXPECTED_OLD_SHA256)}
  AND status=1 AND is_deleted=0;
SELECT ROW_COUNT();
COMMIT;
'''
    result = mysql(sql).strip().splitlines()
    if not result or result[-1] != '1':
        raise RuntimeError('Expected exactly one updated article')
    verified = mysql(
        f'SELECT COUNT(*) FROM t_article WHERE id={ARTICLE_ID} '
        f'AND BINARY article_content=BINARY {lit(content)} '
        f'AND seo_description={lit(DESCRIPTION)} AND status=1 AND is_deleted=0'
    ).strip()
    if verified != '1':
        raise RuntimeError('Post-update verification failed')
    print('Verified published article 61 content and description')


if __name__ == '__main__':
    main()
