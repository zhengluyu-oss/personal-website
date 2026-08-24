#!/bin/bash
set -euo pipefail
CONFIG=/home/wwwroot/zhengluyu-website/config/application-prod.yml
value() { sed -n "s/^[[:space:]]*$1:[[:space:]]*//p" "$CONFIG" | head -n 1 | sed -E "s/^['\"]|['\"]$//g"; }
JDBC_URL=$(value url); DB_USER=$(value username); DB_PASSWORD=$(value password)
DB_NAME=${JDBC_URL#jdbc:mysql://*/}; DB_NAME=${DB_NAME%%\?*}
ADDRESS=${JDBC_URL#jdbc:mysql://}; ADDRESS=${ADDRESS%%/*}; DB_HOST=${ADDRESS%%:*}; DB_PORT=3306
if [[ "$ADDRESS" == *:* ]]; then DB_PORT=${ADDRESS##*:}; fi
export MYSQL_PWD="$DB_PASSWORD"
mysql -N -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USER" "$DB_NAME" -e "SELECT id,title,path,COALESCE(parent_id,0) FROM sys_menu WHERE is_deleted=0 ORDER BY COALESCE(parent_id,0),id;"
ROOTS=$(mysql -N -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USER" "$DB_NAME" -e "SELECT COUNT(*) FROM sys_menu WHERE is_deleted=0 AND parent_id IS NULL AND id IN (1,28);")
OBSOLETE=$(mysql -N -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USER" "$DB_NAME" -e "SELECT COUNT(*) FROM sys_menu WHERE id IN (21,38,42,69,70);")
unset MYSQL_PWD
test "$ROOTS" = 2
test "$OBSOLETE" = 0
