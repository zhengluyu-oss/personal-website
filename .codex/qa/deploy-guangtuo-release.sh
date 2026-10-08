#!/bin/bash
set -euo pipefail
ROOT=/home/wwwroot/zhengluyu-website
STAMP=20261005-guangtuo
LIVE="$ROOT/blog"
NEXT="$ROOT/releases/$STAMP"
BACKUP="$ROOT/backups/blog-$STAMP"
test "$(readlink -f "$LIVE")" = "$ROOT/blog"
test ! -e "$NEXT"
test ! -e "$BACKUP"
test -s /tmp/guangtuo-blog-release.tar.gz
mkdir -p "$NEXT"
# Keep existing uploaded galleries and hashed assets available to older browser tabs.
cp -a "$LIVE/." "$NEXT/"
tar -xzf /tmp/guangtuo-blog-release.tar.gz -C "$NEXT"
test -s "$NEXT/index.html"
chmod -R a+rX "$NEXT"
mv "$LIVE" "$BACKUP"
rollback() {
  trap - ERR
  if [ -d "$LIVE" ]; then mv "$LIVE" "$ROOT/releases/$STAMP-failed"; fi
  mv "$BACKUP" "$LIVE"
  echo FRONTEND_ROLLED_BACK
}
trap rollback ERR
mv "$NEXT" "$LIVE"
curl -fsS https://www.zhengluyu.com/experience/2 -o /dev/null
echo "FRONTEND_DEPLOYED backup=$BACKUP"
