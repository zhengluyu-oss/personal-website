#!/usr/bin/env bash
# Manual, encrypted knowledge metadata backup. Never run from application startup.
# Required: KNOWLEDGE_PG_DATABASE, KNOWLEDGE_MYSQL_DATABASE,
# KNOWLEDGE_MYSQL_DEFAULTS_FILE, KNOWLEDGE_BACKUP_RECIPIENT_FILE.
set -Eeuo pipefail
umask 077

: "${KNOWLEDGE_PG_DATABASE:?set the PostgreSQL database}"
: "${KNOWLEDGE_MYSQL_DATABASE:?set the MySQL database}"
: "${KNOWLEDGE_MYSQL_DEFAULTS_FILE:?set a 0600 client defaults file}"
: "${KNOWLEDGE_BACKUP_RECIPIENT_FILE:?set an age recipient file}"
backup_root=${KNOWLEDGE_BACKUP_ROOT:-/root/luyu-knowledge-backups}
[[ $backup_root == /root/luyu-knowledge-backups || $backup_root == /var/backups/luyu-knowledge ]]
[[ ! -L $backup_root && ! -L $KNOWLEDGE_MYSQL_DEFAULTS_FILE && ! -L $KNOWLEDGE_BACKUP_RECIPIENT_FILE ]]
[[ -f $KNOWLEDGE_MYSQL_DEFAULTS_FILE && -f $KNOWLEDGE_BACKUP_RECIPIENT_FILE ]]
[[ $(stat -c %a "$KNOWLEDGE_MYSQL_DEFAULTS_FILE") == 600 ]]
read -r recipient < "$KNOWLEDGE_BACKUP_RECIPIENT_FILE"
[[ $recipient == age1* ]]

install -d -m 0700 "$backup_root"
exec 9>"$backup_root/.backup.lock"
flock -n 9 || { echo 'KNOWLEDGE_BACKUP_BUSY' >&2; exit 1; }
stamp=$(date -u +%Y%m%dT%H%M%SZ)
pending=$(mktemp -d "$backup_root/.pending-$stamp-XXXXXXXX")
final="$backup_root/knowledge-v1-$stamp"
[[ ! -e $final ]]
trap 'echo "KNOWLEDGE_BACKUP_INCOMPLETE $pending" >&2' ERR

# PostgreSQL schema dump includes entry history, labels, relations, jobs,
# outbox and export snapshots. It is intentionally limited to knowledge.
runuser -u postgres -- pg_dump --no-password --format=custom --schema=knowledge "$KNOWLEDGE_PG_DATABASE" |
  age --recipient "$recipient" --output "$pending/postgres-knowledge.dump.age"

# Only the knowledge/article idempotency mapping is copied from MySQL;
# the ordinary blog backup must independently cover t_article and its assets.
mysqldump --defaults-extra-file="$KNOWLEDGE_MYSQL_DEFAULTS_FILE" --single-transaction \
  --no-tablespaces --set-gtid-purged=OFF "$KNOWLEDGE_MYSQL_DATABASE" t_knowledge_article_export |
  age --recipient "$recipient" --output "$pending/mysql-export-map.sql.age"

# The inventory lists database-referenced private objects. It is not a copy
# of object bytes; OSS durability/versioning and a separate object copy are
# required for recovery after Bucket loss.
runuser -u postgres -- psql --no-psqlrc --quiet --tuples-only --dbname="$KNOWLEDGE_PG_DATABASE" \
  --command="COPY (SELECT owner_id,id,entry_id,object_key,sha256,size_bytes,state FROM knowledge.file_version WHERE state IN ('CLAIMED','CLEANUP_PENDING') ORDER BY owner_id,id) TO STDOUT WITH (FORMAT csv,HEADER true)" |
  age --recipient "$recipient" --output "$pending/private-object-inventory.csv.age"

for file in "$pending"/*.age; do test -s "$file"; done
(
  cd "$pending"
  sha256sum -- *.age > SHA256SUMS
)
printf 'format=knowledge-v1\ncreated_utc=%s\npg_database=%s\nmysql_database=%s\nobject_inventory=database-referenced-only\n' \
  "$stamp" "$KNOWLEDGE_PG_DATABASE" "$KNOWLEDGE_MYSQL_DATABASE" > "$pending/MANIFEST"
chmod 0600 "$pending"/*
mv -- "$pending" "$final"
trap - ERR
echo "KNOWLEDGE_ENCRYPTED_BACKUP_OK $final"
