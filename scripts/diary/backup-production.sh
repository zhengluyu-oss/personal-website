#!/usr/bin/env bash
# Manual installation as /usr/local/sbin/luyu-diary-backup; never run from app startup.
set -Eeuo pipefail
umask 077
exec 9>/run/lock/luyu-diary-backup.lock
flock -n 9 || exit 0
directory=/root/luyu-diary-backups
install -d -m 0700 "$directory"
[[ $(realpath "$directory") == /root/luyu-diary-backups && ! -L $directory ]]
read -r recipient < /etc/luyu-diary/backup-recipient.txt
[[ $recipient == age1* ]]
output="$directory/diary-$(date -u +%Y%m%dT%H%M%S)-$$.dump.age"
temporary="$output.partial"
trap 'rm -f -- "$temporary"' EXIT
runuser -u postgres -- pg_dump --format=custom --no-password diary_db | age --recipient "$recipient" --output "$temporary"
test -s "$temporary"
mv -- "$temporary" "$output"
sha256sum "$output" > "$output.sha256"
echo "DIARY_ENCRYPTED_BACKUP_OK $(basename "$output")"
# Only this service's encrypted backup files are eligible for retention cleanup.
find "$directory" -maxdepth 1 -type f -name 'diary-*.dump.age*' -mtime +14 -delete
