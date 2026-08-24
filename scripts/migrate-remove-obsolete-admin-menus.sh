#!/bin/bash
set -euo pipefail

CONFIG=/home/wwwroot/zhengluyu-website/config/application-prod.yml
MIGRATION_SQL=/tmp/remove-obsolete-admin-menus.sql
BACKUP_DIR=/home/wwwroot/zhengluyu-website/backups/$(date +%Y%m%d-%H%M%S)-admin-menu-cleanup

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
mysqldump -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USER" --single-transaction \
  "$DB_NAME" sys_menu sys_role_menu > "$BACKUP_DIR/before.sql"
mysql -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USER" "$DB_NAME" < "$MIGRATION_SQL"

OBSOLETE_COUNT=$(mysql -N -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USER" "$DB_NAME" \
  -e "SELECT COUNT(*) FROM sys_menu WHERE id IN (21, 38, 42, 69, 70);")
ROOT_COUNT=$(mysql -N -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USER" "$DB_NAME" \
  -e "SELECT COUNT(*) FROM sys_menu WHERE parent_id IS NULL AND id IN (1, 28);")
unset MYSQL_PWD

test "$OBSOLETE_COUNT" = "0"
test "$ROOT_COUNT" = "2"
echo "MIGRATION_OK backup=$BACKUP_DIR"
