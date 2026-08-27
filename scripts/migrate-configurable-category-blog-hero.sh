#!/bin/bash
set -euo pipefail

CONFIG=/home/wwwroot/zhengluyu-website/config/application-prod.yml
MIGRATION_SQL=/tmp/configurable-category-blog-hero.sql
BACKUP_DIR=/home/wwwroot/zhengluyu-website/backups/$(date +%Y%m%d-%H%M%S)-category-blog-hero

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

FIELD_COUNT=$(mysql -N -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USER" "$DB_NAME" -e "
SELECT COUNT(*) FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_category'
  AND COLUMN_NAME IN ('hero_eyebrow', 'hero_title_accent', 'hero_title', 'hero_description');")

if [[ "$FIELD_COUNT" == "0" ]]; then
  mysqldump -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USER" --single-transaction \
    "$DB_NAME" t_category > "$BACKUP_DIR/before.sql"
  mysql -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USER" "$DB_NAME" < "$MIGRATION_SQL"
elif [[ "$FIELD_COUNT" == "4" ]]; then
  echo "MIGRATION_ALREADY_APPLIED"
else
  echo "MIGRATION_PARTIAL_STATE field_count=$FIELD_COUNT" >&2
  exit 1
fi

VERIFIED_COUNT=$(mysql -N -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USER" "$DB_NAME" -e "
SELECT COUNT(*) FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_category'
  AND COLUMN_NAME IN ('hero_eyebrow', 'hero_title_accent', 'hero_title', 'hero_description');")

unset MYSQL_PWD
test "$VERIFIED_COUNT" = "4"
echo "MIGRATION_OK backup=$BACKUP_DIR"
