#!/bin/bash
set -euo pipefail

CONFIG=/home/wwwroot/zhengluyu-website/config/application-prod.yml
HERO_SQL=/tmp/home-hero-copy.sql
ARTICLE_SQL=/tmp/article-tdk.sql
BACKUP_DIR=/home/wwwroot/zhengluyu-website/backups/$(date +%Y%m%d-%H%M%S)-home-seo

value() {
  sed -n "s/^[[:space:]]*$1:[[:space:]]*//p" "$CONFIG" | head -n 1 | sed -E "s/^['\"]|['\"]$//g"
}

JDBC_URL=$(value url)
DB_USER=$(value username)
DB_PASSWORD=$(value password)
ADDRESS=${JDBC_URL#jdbc:mysql://}
ADDRESS=${ADDRESS%%/*}
DB_NAME=${JDBC_URL#jdbc:mysql://*/}
DB_NAME=${DB_NAME%%\?*}
DB_HOST=${ADDRESS%%:*}
DB_PORT=3306
if [[ "$ADDRESS" == *:* ]]; then DB_PORT=${ADDRESS##*:}; fi

mkdir -p "$BACKUP_DIR"
export MYSQL_PWD="$DB_PASSWORD"
mysqldump -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USER" --single-transaction "$DB_NAME" sys_website_info t_article > "$BACKUP_DIR/before.sql"
mysql -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USER" "$DB_NAME" < "$HERO_SQL"
mysql -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USER" "$DB_NAME" < "$ARTICLE_SQL"
mysql -N -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USER" "$DB_NAME" -e "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND ((TABLE_NAME='sys_website_info' AND COLUMN_NAME LIKE 'hero_%') OR (TABLE_NAME='t_article' AND COLUMN_NAME LIKE 'seo_%'));"
unset MYSQL_PWD
echo "MIGRATION_OK backup=$BACKUP_DIR"
