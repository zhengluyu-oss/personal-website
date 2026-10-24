"""Prepare a reviewable PostgreSQL import from the original GitHub checkout.

Usage: python import-life-guide.py SOURCE_CHECKOUT OUTPUT_SQL OUTPUT_MANIFEST
The script only writes local artifacts. It never connects to production.
"""

from __future__ import annotations

import hashlib
import json
from pathlib import Path
import re
import subprocess
import sys
from urllib.parse import quote, unquote
import uuid


REPO = "https://github.com/eternity4719/HowToLiveBetter"
ATTRIBUTION = "https://creativecommons.org/licenses/by/4.0/"
OWNER = 1
NAMESPACE = uuid.UUID("69bc8936-fc83-4e3d-a283-d36065176852")
ROOT = Path(__file__).resolve().parents[2]
OLD_MANIFEST = ROOT / "docs/articles/high-value-life-guide-manifest.json"


def encoded_text(value: str) -> str:
    return "convert_from(decode('" + value.encode("utf-8").hex() + "','hex'),'UTF8')"


def source_link(commit: str, relative: str, is_dir: bool = False) -> str:
    path = "/".join(quote(part, safe="") for part in relative.split("/"))
    return f"{REPO}/{'tree' if is_dir else 'blob'}/{commit}/{path}"


def rewrite_links(raw: str, checkout: Path, source: Path, commit: str) -> str:
    def replace(match: re.Match[str]) -> str:
        target = match.group(1)
        if target.startswith(("#", "//", "/")) or re.match(r"^[a-zA-Z][\w+.-]*:", target):
            return match.group(0)
        path, mark, fragment = target.partition("#")
        resolved = (source.parent / unquote(path)).resolve()
        try:
            relative = resolved.relative_to(checkout).as_posix()
        except ValueError as exc:
            raise ValueError(f"Source link escapes checkout: {source}: {target}") from exc
        if not resolved.exists():
            raise ValueError(f"Source link does not exist: {source}: {target}")
        url = source_link(commit, relative, resolved.is_dir())
        if mark:
            url += "#" + fragment
        return "](" + url + ")"

    return re.sub(r"\]\(([^)]+)\)", replace, raw)


def build(checkout: Path) -> tuple[str, dict]:
    checkout = checkout.resolve(strict=True)
    remote = subprocess.check_output(
        ["git", "-C", str(checkout), "remote", "get-url", "origin"], text=True
    ).strip().removesuffix(".git")
    if remote != REPO:
        raise ValueError(f"Unexpected source repository: {remote}")
    if subprocess.check_output(["git", "-C", str(checkout), "status", "--porcelain"], text=True).strip():
        raise ValueError("Source checkout must be clean")
    commit = subprocess.check_output(["git", "-C", str(checkout), "rev-parse", "HEAD"], text=True).strip()
    old = json.loads(OLD_MANIFEST.read_text(encoding="utf-8"))
    source_files = [item["path"] for item in old["articles"]]
    actual_files = sorted(
        p.relative_to(checkout).as_posix()
        for folder in ("book", "docs")
        for p in (checkout / folder).glob("*.md")
    )
    if len(source_files) != 44 or sorted(source_files) != actual_files:
        raise ValueError("GitHub chapters or appendices differ from the reviewed 44-file inventory")

    category = uuid.uuid5(NAMESPACE, "category:人生指南")
    tag = uuid.uuid5(NAMESPACE, "tag:高性价比人生指南")
    records = []
    for item in [*old["articles"], {"path": "README.md", "title": old["index_title"]}]:
        relative = item["path"]
        source = checkout / relative
        raw = source.read_text(encoding="utf-8").replace("\r\n", "\n").strip()
        body = (
            f"> 原文：[高性价比人生指南]({REPO})，作者 eternity4719；"
            f"按 [CC BY 4.0]({ATTRIBUTION}) 转载。"
            f"本知识库保存 GitHub 提交 `{commit}` 的快照，"
            f"[查看原文]({source_link(commit, relative)})。\n\n"
            + rewrite_links(raw, checkout, source, commit)
            + "\n"
        )
        title = item["title"]
        if not 1 <= len(title) <= 200 or len(body.encode("utf-8")) > 2097152:
            raise ValueError(f"Entry exceeds knowledge limits: {relative}")
        records.append({
            "id": str(uuid.uuid5(NAMESPACE, "entry:" + relative)),
            "path": relative,
            "title": title,
            "body": body,
            "source_url": source_link(commit, relative),
            "source_sha256": hashlib.sha256(source.read_bytes()).hexdigest(),
            "body_sha256": hashlib.sha256(body.encode("utf-8")).hexdigest(),
            "body_md5": hashlib.md5(body.encode("utf-8"), usedforsecurity=False).hexdigest(),
            "body_bytes": len(body.encode("utf-8")),
        })

    ids = ",".join("'" + record["id"] + "'::uuid" for record in records)
    statements = [
        "\\set ON_ERROR_STOP on",
        "BEGIN;",
        "SET LOCAL ROLE knowledge_runtime;",
        f"SELECT set_config('knowledge.owner_id','{OWNER}',true);",
        "SELECT pg_advisory_xact_lock(1936876915, 17);",
        "DO $$ BEGIN",
        f"  IF EXISTS(SELECT 1 FROM knowledge.entry WHERE owner_id={OWNER} AND id IN ({ids})) THEN",
        "    RAISE EXCEPTION 'Life guide already imported; refusing to create duplicates';",
        "  END IF;",
        f"  IF EXISTS(SELECT 1 FROM knowledge.label WHERE owner_id={OWNER} AND id IN ('{category}'::uuid,'{tag}'::uuid)) THEN",
        "    RAISE EXCEPTION 'Life guide labels already exist; refusing an ambiguous retry';",
        "  END IF;",
        "END $$;",
        f"INSERT INTO knowledge.owner_control(owner_id) VALUES ({OWNER}) ON CONFLICT DO NOTHING;",
        f"INSERT INTO knowledge.label(owner_id,id,kind,name,parent_category_id,revision) VALUES ({OWNER},'{category}','CATEGORY',{encoded_text('人生指南')},NULL,1),({OWNER},'{tag}','TAG',{encoded_text('高性价比人生指南')},'{category}',1);",
    ]
    for record in records:
        entry_id = record["id"]
        content = json.dumps({
            "kind": "NOTE", "title": record["title"], "body": record["body"],
            "sourceUrl": record["source_url"], "categoryId": str(category), "tagIds": [str(tag)],
        }, ensure_ascii=False, separators=(",", ":"))
        statements.extend([
            f"INSERT INTO knowledge.entry(owner_id,id,kind,title,body,source_url,category_id,revision) VALUES ({OWNER},'{entry_id}','NOTE',{encoded_text(record['title'])},{encoded_text(record['body'])},{encoded_text(record['source_url'])},'{category}',1);",
            f"INSERT INTO knowledge.entry_tag(owner_id,entry_id,tag_id) VALUES ({OWNER},'{entry_id}','{tag}');",
            f"INSERT INTO knowledge.entry_revision(owner_id,entry_id,revision,content,saved_at,action) SELECT {OWNER},'{entry_id}',1,{encoded_text(content)}::jsonb,updated_at,'SAVE' FROM knowledge.entry WHERE owner_id={OWNER} AND id='{entry_id}';",
        ])
    statements.extend([
        f"DO $$ BEGIN IF (SELECT count(*) FROM knowledge.entry WHERE owner_id={OWNER} AND category_id='{category}' AND deleted_at IS NULL) <> 45 THEN RAISE EXCEPTION 'Imported entry count mismatch'; END IF; END $$;",
        "COMMIT;",
    ])
    public_manifest = {
        "repository": REPO, "commit": commit, "license": "CC BY 4.0",
        "owner_id": OWNER, "category_id": str(category), "tag_id": str(tag),
        "entry_count": len(records),
        "entries": [{k: value for k, value in record.items() if k != "body"} for record in records],
    }
    return "\n".join(statements) + "\n", public_manifest


def main() -> None:
    if len(sys.argv) != 4:
        raise SystemExit(__doc__)
    sql_path, manifest_path = Path(sys.argv[2]), Path(sys.argv[3])
    if sql_path.exists() or manifest_path.exists():
        raise ValueError("Output already exists")
    sql, manifest = build(Path(sys.argv[1]))
    sql_path.write_text(sql, encoding="utf-8")
    manifest_path.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Prepared {manifest['entry_count']} entries from {manifest['commit']}")


if __name__ == "__main__":
    main()
