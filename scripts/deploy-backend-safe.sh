#!/usr/bin/env bash
set -euo pipefail

package=${1:?release package is required}
expected=${2:?release sha256 is required}
release_name=${3:?release name is required}
[[ "$release_name" =~ ^fixes-[0-9]+-[0-9]{8}-[0-9]{6}$ ]]

target=/home/wwwroot/zhengluyu-website/app/blog-backend.jar
release=/home/wwwroot/zhengluyu-website/releases/$release_name
test -f "$target"
test -f "$package"
test ! -e "$release"
test "$(sha256sum "$package" | awk '{print $1}')" = "$expected"

mkdir -p "$release/backup"
cp -a "$target" "$release/backup/blog-backend.jar"

rollback() {
  trap - ERR
  cp -a "$release/backup/blog-backend.jar" "$target"
  systemctl restart zhengluyu-blog
  echo BACKEND_ROLLED_BACK
}
trap rollback ERR

install -m 644 "$package" "$target.new"
mv "$target.new" "$target"
systemctl restart zhengluyu-blog

healthy=0
for attempt in $(seq 1 30); do
  code=$(curl -s -o /dev/null -w '%{http_code}' --connect-timeout 2 --max-time 4 \
    'http://127.0.0.1:8001/article/blog-feed?pageNum=1&pageSize=1' || true)
  if [ "$code" = 200 ]; then healthy=1; break; fi
  sleep 2
done
test "$healthy" = 1
trap - ERR
echo BACKEND_HEALTHY
