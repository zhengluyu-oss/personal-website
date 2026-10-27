#!/usr/bin/env bash
set -euo pipefail
umask 077

CONFIG=/home/wwwroot/zhengluyu-website/config/application-prod.yml
MIGRATION_SQL=/tmp/home-hero-mobile-video.sql
BACKUP_DIR=/home/wwwroot/zhengluyu-website/backups/$(date +%Y%m%d-%H%M%S)-home-hero-mobile-video

value() {
  sed -n "s/^[[:space:]]*$1:[[:space:]]*//p" "$CONFIG" | head -n 1 | sed -E "s/^['\"]|['\"]$//g"
}

JDBC_URL=$(value url)
DB_USER=$(value username)
DB_PASSWORD=$(value password)
[[ $JDBC_URL == jdbc:mysql://* && -n $DB_USER && -n $DB_PASSWORD ]] || {
  echo 'DATABASE_CONFIG_UNAVAILABLE' >&2
  exit 1
}
ADDRESS=${JDBC_URL#jdbc:mysql://}
ADDRESS=${ADDRESS%%/*}
DB_NAME=${JDBC_URL#jdbc:mysql://*/}
DB_NAME=${DB_NAME%%\?*}
DB_HOST=${ADDRESS%%:*}
DB_PORT=3306
if [[ $ADDRESS == *:* ]]; then DB_PORT=${ADDRESS##*:}; fi
[[ -n $DB_HOST && -n $DB_NAME && $DB_PORT =~ ^[0-9]+$ ]] || exit 1

export MYSQL_PWD=$DB_PASSWORD
unset DB_PASSWORD
column_count() {
  mysql -N -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USER" "$DB_NAME" -e "
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_website_info'
      AND COLUMN_NAME = 'hero_mobile_video_key';"
}

if [[ $(column_count) == 0 ]]; then
  mkdir -p "$BACKUP_DIR"
  mysqldump -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USER" --single-transaction \
    "$DB_NAME" sys_website_info > "$BACKUP_DIR/before.sql"
  test -s "$BACKUP_DIR/before.sql"
  mysql -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USER" "$DB_NAME" < "$MIGRATION_SQL"
else
  echo MIGRATION_ALREADY_APPLIED
fi

test "$(column_count)" = 1
echo "MIGRATION_OK backup=$BACKUP_DIR"
