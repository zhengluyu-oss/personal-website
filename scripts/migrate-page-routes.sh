#!/bin/bash
set -euo pipefail

CONFIG=/home/wwwroot/zhengluyu-website/config/application-prod.yml
MIGRATION_SQL=/tmp/normalize-page-routes.sql
BACKUP_DIR=/home/wwwroot/zhengluyu-website/backups/$(date +%Y%m%d-%H%M%S)-page-routes

value() { sed -n "s/^[[:space:]]*$1:[[:space:]]*//p" "$CONFIG" | head -n 1 | sed -E "s/^['\"]|['\"]$//g"; }
JDBC_URL=$(value url)
DB_USER=$(value username)
DB_PASSWORD=$(value password)
ADDRESS=${JDBC_URL#jdbc:mysql://}; ADDRESS=${ADDRESS%%/*}
DB_NAME=${JDBC_URL#jdbc:mysql://*/}; DB_NAME=${DB_NAME%%\?*}
DB_HOST=${ADDRESS%%:*}; DB_PORT=3306
if [[ "$ADDRESS" == *:* ]]; then DB_PORT=${ADDRESS##*:}; fi

mkdir -p "$BACKUP_DIR"
export MYSQL_PWD="$DB_PASSWORD"
mysqldump -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USER" --single-transaction "$DB_NAME" sys_menu sys_role_menu > "$BACKUP_DIR/before.sql"
mysql -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USER" "$DB_NAME" < "$MIGRATION_SQL"
mysql -N -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USER" "$DB_NAME" -e "SELECT path FROM sys_menu WHERE id IN (29,30,31,32) ORDER BY id;" > "$BACKUP_DIR/verified-routes.txt"
unset MYSQL_PWD
grep -qx '/site/info' "$BACKUP_DIR/verified-routes.txt"
grep -qx '/articles/:id' "$BACKUP_DIR/verified-routes.txt"
grep -qx '/articles/new' "$BACKUP_DIR/verified-routes.txt"
grep -qx '/articles' "$BACKUP_DIR/verified-routes.txt"
echo "MIGRATION_OK backup=$BACKUP_DIR"
