#!/usr/bin/env bash
# One invocation owns placement, verification and recovery. No production defaults.
set -Eeuo pipefail
[[ $# == 8 ]] || { echo 'usage: archive app blog admin nginx unit selected host'; exit 2; }
ARCHIVE=$1 APP=$2 BLOG=$3 ADMIN=$4 NGINX_SITE=$5 UNIT=$6 SELECTED=$7 HOST=$8
ATTEMPTS=${DEPLOY_PROBE_ATTEMPTS:-30}
DELAY=${DEPLOY_PROBE_DELAY:-2}
[[ $ATTEMPTS =~ ^[1-9][0-9]?$ && $DELAY =~ ^[0-9]+$ && $UNIT =~ ^[a-zA-Z0-9_.@-]+$ && $HOST =~ ^[a-zA-Z0-9.-]+$ ]] || exit 2
declare -a names=() paths=() modified=()
declare -A existed=()
WORK='' BACKUP='' RECOVERING=0

safe_path() {
  [[ $1 == /* && $1 != / && $1 != *'/../'* && $1 != *'/./'* && $1 != *$'\n'* && $1 != *$'\t'* ]] || return 1
  local resolved
  resolved=$(realpath -m -- "$1")
  [[ $resolved == "$1" && $1 != /tmp && $1 != /home && $1 != /etc && $1 != /usr && $1 != /var && $1 != /opt ]]
}
checksum() {
  # Include paths, file bytes and symlink destinations; an independent copy is retained.
  python3 - "$1" <<'PY'
import hashlib, os, pathlib, sys
p=pathlib.Path(sys.argv[1]); h=hashlib.sha256()
for f in ([p] if not p.is_dir() else sorted(p.rglob('*'))):
    h.update(str(f.relative_to(p) if f != p else '.').encode())
    if f.is_symlink(): h.update(os.readlink(f).encode())
    elif f.is_file(): h.update(f.read_bytes())
print(h.hexdigest())
PY
}
health() {
  local i code
  for ((i=1;i<=ATTEMPTS;i++)); do
    code=$(curl -sS -o "$WORK/health.json" -w '%{http_code}' --connect-timeout 2 --max-time 4 'http://127.0.0.1:8001/site-modules/access') || code=000
    if [[ $code == 200 ]] && python3 - "$WORK/health.json" <<'PY'
import json,sys
try:
    r=json.load(open(sys.argv[1])); d=r['data']
    assert r['code']==200 and isinstance(d,list) and len(d)==6
    assert {x['key'] for x in d} == {'home','experience','blog','shares','photos','about'}
    assert all(isinstance(x['allowed'],bool) and isinstance(x['publicAccess'],bool) for x in d)
except (ValueError,KeyError,AssertionError,TypeError): sys.exit(1)
PY
    then return 0; fi
    ((i == ATTEMPTS)) || sleep "$DELAY"
  done
  echo BACKEND_HEALTH_TIMEOUT
  return 1
}
verify_frontend() {
  local prefix=$1 asset kind
  curl -fsS --max-time 12 --resolve "$HOST:443:127.0.0.1" -D "$WORK/headers" -o "$WORK/page" "https://$HOST${prefix}index.html"
  grep -qi '^Content-Type: *text/html' "$WORK/headers"
  grep -qi '^Cache-Control: .*no-cache' "$WORK/headers"
  python3 - "$WORK/page" "$prefix" > "$WORK/assets" <<'PY'
import re,sys
s=open(sys.argv[1],encoding='utf8').read(); p=sys.argv[2]
assets=re.findall(r'(?:src|href)=[\"\']([^\"\']+\.(?:js|css))(?:\?[^\"\']*)?[\"\']',s)
assets=[a for a in assets if a.startswith(p) and not a.startswith('//') and (('/assets/' in a) or ('/js/' in a) or ('/css/' in a))]
assert any(a.endswith('.js') for a in assets), 'entry script absent'
print('\n'.join(dict.fromkeys(assets)))
PY
  while IFS= read -r asset; do
    curl -fsS --max-time 12 --resolve "$HOST:443:127.0.0.1" -H 'Accept-Encoding: gzip' -D "$WORK/headers" -o /dev/null "https://$HOST$asset"
    if [[ $asset == *.js ]]; then kind='(application|text)/(javascript|x-javascript)'; else kind='text/css'; fi
    grep -Eqi "^Content-Type: *$kind" "$WORK/headers"
    grep -qi '^Cache-Control: .*immutable' "$WORK/headers"
    grep -qi '^Content-Encoding: *gzip' "$WORK/headers"
  done < "$WORK/assets"
}
recover() {
  local i name target failed=0
  set +e
  for ((i=${#modified[@]}-1;i>=0;i--)); do
    name=${modified[i]}; target=''
    for ((j=0;j<${#names[@]};j++)); do [[ ${names[j]} == "$name" ]] && target=${paths[j]}; done
    if [[ -z $target ]] || ! safe_path "$target"; then failed=1; continue; fi
    rm -rf -- "$target" || { failed=1; continue; }
    if [[ ${existed[$name]} == 1 ]]; then
      mkdir -p -- "$(dirname -- "$target")" && cp -a -- "$BACKUP/$name" "$target" || { failed=1; continue; }
      [[ $(checksum "$target") == $(cat "$BACKUP/$name.sha256") ]] || failed=1
    fi
  done
  if [[ " ${modified[*]} " == *' jar '* ]]; then
    if [[ ${existed[jar]} == 1 ]]; then
      systemctl restart "$UNIT" && health || failed=1
    else
      systemctl stop "$UNIT" || failed=1
      echo FIRST_INSTALL_NO_PREVIOUS_BACKEND
    fi
  fi
  if [[ " ${modified[*]} " == *' nginx '* || " ${modified[*]} " == *' blog '* || " ${modified[*]} " == *' admin '* ]]; then
    nginx -t && nginx -s reload || failed=1
  fi
  return "$failed"
}
fail() {
  local status=${1:-1} line=${2:-explicit}
  trap - ERR INT TERM
  ((RECOVERING == 0)) || exit 2
  RECOVERING=1
  echo "DEPLOYMENT_FAILED line=$line status=$status backup=$BACKUP"
  if recover; then echo DEPLOYMENT_RECOVERED; else echo "RECOVERY_FAILED manual_backup=$BACKUP"; exit 2; fi
  exit 1
}
trap 'fail "$?" "$LINENO"' ERR
trap 'fail 130 signal' INT TERM

for tool in python3 realpath sha256sum curl nginx tar; do command -v "$tool" >/dev/null; done
for target in "$APP" "$BLOG" "$ADMIN"; do safe_path "$target"; [[ ! -L $target && ! -L ${target}.previous ]]; done
# Resolve the sites-enabled symlink, preserving the real configuration path in the manifest.
NGINX_SITE=$(realpath -m -- "$NGINX_SITE")
safe_path "$NGINX_SITE"
for target in "$APP" "$BLOG" "$ADMIN" "$NGINX_SITE"; do
  for other in "$APP" "$BLOG" "$ADMIN" "$NGINX_SITE"; do
    [[ $target == "$other" || $target != "$other"/* ]] || { echo OVERLAPPING_TARGETS; exit 2; }
  done
done
WORK=$(mktemp -d /tmp/luyu-release-stage-XXXXXX)
BACKUP=$(mktemp -d "${DEPLOY_BACKUP_PARENT:-/tmp}/luyu-release-backup-XXXXXX")
tar -tzf "$ARCHIVE" > "$WORK/entries"
# Only the local staging layout is accepted; no archive link can write outside extraction.
python3 - "$ARCHIVE" <<'PY'
import tarfile,sys,pathlib
with tarfile.open(sys.argv[1]) as t:
    for m in t:
        p=pathlib.PurePosixPath(m.name)
        assert not p.is_absolute() and '..' not in p.parts and not m.issym() and not m.islnk()
        assert m.isfile() or m.isdir()
        assert not p.parts or p.parts[0] in ('app','blog','admin','nginx')
PY
tar -xzf "$ARCHIVE" -C "$WORK"
IFS=',' read -ra requested <<< "$SELECTED"
for component in "${requested[@]}"; do
  case "$component" in
    backend)
      [[ -f $WORK/app/blog-backend.jar ]]
      start=$(systemctl show "$UNIT" --property=ExecStart --value)
      grep -Eq -- '--spring.profiles.active=prod([ ;}]|$)' <<< "$start"
      grep -q -- '--spring.config.additional-location=file:/' <<< "$start"
      [[ $start == *"$APP/blog-backend.jar"* ]]
      names+=(jar); paths+=("$APP/blog-backend.jar");;
    blog|admin)
      [[ -f $WORK/$component/index.html ]]
      if [[ $component == blog ]]; then target=$BLOG; else target=$ADMIN; fi
      names+=("$component" "$component.previous"); paths+=("$target" "$target.previous");;
    *) echo INVALID_COMPONENT; exit 2;;
  esac
done
if [[ -f $WORK/nginx/zhengluyu-blog ]]; then names+=(nginx); paths+=("$NGINX_SITE"); fi
# Complete all snapshots before the first replacement. A failed backup changes nothing.
printf 'component\ttarget\texisted\tsha256\n' > "$BACKUP/manifest.tsv"
for ((i=0;i<${#names[@]};i++)); do
  name=${names[i]}; target=${paths[i]}; existed[$name]=0
  if [[ -e $target ]]; then
    existed[$name]=1
    cp -a -- "$target" "$BACKUP/$name"
    checksum "$target" > "$BACKUP/$name.sha256"
    [[ $(checksum "$BACKUP/$name") == $(cat "$BACKUP/$name.sha256") ]]
  else printf 'absent\n' > "$BACKUP/$name.sha256"; fi
  printf '%s\t%s\t%s\t%s\n' "$name" "$target" "${existed[$name]}" "$(cat "$BACKUP/$name.sha256")" >> "$BACKUP/manifest.tsv"
done
for component in "${requested[@]}"; do
  if [[ $component == backend ]]; then
    modified+=(jar)
    mkdir -p -- "$APP"
    cp -- "$WORK/app/blog-backend.jar" "$APP/blog-backend.jar"
  else
    if [[ $component == blog ]]; then target=$BLOG; else target=$ADMIN; fi
    modified+=("$component.previous" "$component")
    rm -rf -- "$target.previous"
    [[ ! -d $target ]] || cp -a -- "$target" "$target.previous"
    rm -rf -- "$target"
    mkdir -p -- "$target"
    cp -a -- "$WORK/$component/." "$target/"
  fi
done
if [[ -f $WORK/nginx/zhengluyu-blog ]]; then
  modified+=(nginx)
  mkdir -p -- "$(dirname -- "$NGINX_SITE")"
  cp -- "$WORK/nginx/zhengluyu-blog" "$NGINX_SITE"
fi
if [[ ,$SELECTED, == *,backend,* ]]; then systemctl restart "$UNIT"; health; fi
if [[ " ${modified[*]} " == *' nginx '* || ,$SELECTED, == *,blog,* || ,$SELECTED, == *,admin,* ]]; then
  nginx -t
  nginx -s reload
fi
if [[ ,$SELECTED, == *,blog,* ]]; then verify_frontend /; fi
if [[ ,$SELECTED, == *,admin,* ]]; then verify_frontend /admin/; fi
trap - ERR INT TERM
printf 'success\n' > "$BACKUP/result"
echo "REMOTE_HEALTH_OK manifest=$BACKUP/manifest.tsv"
# Exactly one previous frontend is retained; successful backup snapshots need not accumulate.
rm -rf -- "$WORK" "$BACKUP"
rm -f -- "$ARCHIVE"
