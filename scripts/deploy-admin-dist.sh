#!/usr/bin/env bash
set -euo pipefail

package=${1:?release package is required}
expected=${2:?release sha256 is required}
release_name=${3:?release name is required}
[[ "$release_name" =~ ^fixes-567-[0-9]{8}-[0-9]{6}-admin$ ]]

target=/home/wwwroot/zhengluyu-website/admin
release=/home/wwwroot/zhengluyu-website/releases/$release_name
test -d "$target"
test ! -e "$release"
test "$(sha256sum "$package" | awk '{print $1}')" = "$expected"

mkdir -p "$release/backup"
cp -a "$target/." "$release/backup/"
tar -xzf "$package" -C "$target" --exclude='./index.html'
tar -xOf "$package" ./index.html > "$target/.index.html.new"
test -s "$target/.index.html.new"
mv "$target/.index.html.new" "$target/index.html"
echo ADMIN_PUBLISHED
