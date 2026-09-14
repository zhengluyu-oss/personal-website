#!/usr/bin/env bash
set -euo pipefail

package=${1:?release package path is required}
expected=${2:?release sha256 is required}
release_name=${3:?release name is required}
target=/home/wwwroot/zhengluyu-website/blog
release=/home/wwwroot/zhengluyu-website/releases/$release_name

test -d "$target"
test ! -e "$release"
test "$(sha256sum "$package" | awk '{print $1}')" = "$expected"

mkdir -p "$release/backup"
cp -a "$target/." "$release/backup/"
mv "$package" "$release/package.tar.gz"

# Publish immutable resources first and switch the uncached HTML entry last.
tar -xzf "$release/package.tar.gz" -C "$target" --exclude='./index.html'
tar -xOf "$release/package.tar.gz" ./index.html > "$target/.index.html.new"
test -s "$target/.index.html.new"
if test -e "$target/index.html.gz"; then
  mv "$target/index.html.gz" "$release/stale-index.html.gz"
fi
mv "$target/.index.html.new" "$target/index.html"

sha256sum "$target/index.html"
du -sh "$release/backup"
