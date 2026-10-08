"""Build a reproducible, single-transaction SQL import of HowToLiveBetter.

Usage: python publish-how-to-live-better.py SOURCE_DIR COVER_URL OUTPUT_SQL
The SOURCE_DIR must be a checkout of the exact commit recorded below.
"""

from __future__ import annotations

import hashlib
import json
from pathlib import Path
import re
import subprocess
import sys
from urllib.parse import quote


SOURCE_COMMIT = "a18ee40519ed34562ac12a8dc88e053a9c8a9973"
SOURCE_REPO = "https://github.com/eternity4719/HowToLiveBetter"
LICENSE = "https://creativecommons.org/licenses/by/4.0/"
SNAPSHOT = "2026-10-08"
CATEGORY = "高性价比人生指南"
TAG = "高性价比人生指南"


def sql_text(value: str) -> str:
    return "CONVERT(0x" + value.encode("utf-8").hex() + " USING utf8mb4)"


def source_url(relative: str) -> str:
    encoded = "/".join(quote(part) for part in relative.split("/"))
    kind = "tree" if relative.endswith("/") else "blob"
    return f"{SOURCE_REPO}/{kind}/{SOURCE_COMMIT}/{encoded}"


def render(source: Path, path: Path, article_keys: dict[Path, str]) -> str:
    raw = path.read_text(encoding="utf-8").replace("\r\n", "\n").strip()

    def replace_link(match: re.Match[str]) -> str:
        target = match.group(1)
        if ":" in target or target.startswith(("#", "//", "/")):
            return match.group(0)
        dest, _, fragment = target.partition("#")
        resolved = (path.parent / dest).resolve()
        if resolved == (source / "README.md").resolve():
            updated = "__BLOG_LINK_INDEX__"
        elif resolved in article_keys:
            updated = f"__BLOG_LINK_{article_keys[resolved]}__"
        else:
            try:
                relative = resolved.relative_to(source.resolve()).as_posix()
            except ValueError as exc:
                raise ValueError(f"Link escapes source checkout: {path}: {target}") from exc
            updated = source_url(relative)
        if fragment:
            updated += "#" + fragment
        return "](" + updated + ")"

    body = re.sub(r"\]\(([^)]+)\)", replace_link, raw)
    origin = path.relative_to(source).as_posix()
    notice = (
        "> 转载说明：本文出自[高性价比人生指南]({repo})，作者 eternity4719；"
        "按 [CC BY 4.0]({license}) 转载。同步版本：{snapshot} "
        "（[原文]({original})）。仅调整站内导航和相对链接，正文条目未改写。"
        "医学、法律和政策信息请以最新官方资料及专业人士意见为准。\n\n"
    ).format(repo=SOURCE_REPO, license=LICENSE, snapshot=SNAPSHOT, original=source_url(origin))
    return notice + body + "\n"


def main() -> None:
    if len(sys.argv) != 4:
        raise SystemExit(__doc__)
    source, cover_url, output = Path(sys.argv[1]).resolve(), sys.argv[2], Path(sys.argv[3])
    commit = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=source, text=True).strip()
    if commit != SOURCE_COMMIT:
        raise RuntimeError(f"Unexpected source commit: {commit}")
    if not cover_url.startswith("https://"):
        raise ValueError("Expected HTTPS cover URL")
    book = sorted((source / "book").glob("*.md"))
    docs = sorted((source / "docs").glob("*.md"))
    if len(book) != 34 or len(docs) != 10:
        raise RuntimeError(f"Unexpected source layout: {len(book)} chapters, {len(docs)} appendices")
    files = book + docs
    keys = {path.resolve(): f"A{index:02d}" for index, path in enumerate(files, 1)}
    title_for = lambda path: ("高性价比人生指南｜" + (path.stem.replace("-", " ", 1) if path in book else "附录 · " + path.stem))
    if any(len(title_for(path)) > 50 for path in files):
        raise ValueError("Article title exceeds database limit")
    sql = [
        "SET NAMES utf8mb4;",
        "START TRANSACTION;",
        f"INSERT INTO t_category (category_name, create_time, update_time, is_deleted) VALUES ({sql_text(CATEGORY)}, NOW(), NOW(), 0);",
        "SET @category_id = LAST_INSERT_ID();",
        f"INSERT INTO t_tag (tag_name, create_time, update_time, is_deleted) VALUES ({sql_text(TAG)}, NOW(), NOW(), 0);",
        "SET @tag_id = LAST_INSERT_ID();",
    ]
    manifest = []
    for path in files:
        key = keys[path.resolve()]
        content = render(source, path, keys)
        title = title_for(path)
        description = f"《高性价比人生指南》{'第 ' + str(int(path.name[:2])) + ' 章' if path in book else '延伸资料'}：{path.stem}。作者 eternity4719；CC BY 4.0；2026-10-08 版本。"
        sql.append(
            "INSERT INTO t_article (user_id,category_id,article_cover,article_title,seo_title,seo_description,seo_keywords,article_content,article_type,is_top,status,visit_count,create_time,update_time,is_deleted) VALUES ("
            f"1,@category_id,{sql_text(cover_url)},{sql_text(title)},{sql_text(title)},{sql_text(description)},{sql_text('高性价比人生指南,生活指南,' + path.stem)},{sql_text(content)},2,0,1,0,NOW(),NOW(),0);"
        )
        sql.append(f"SET @{key} = LAST_INSERT_ID();")
        sql.append(f"INSERT INTO t_article_tag (article_id,tag_id,create_time,is_deleted) VALUES (@{key},@tag_id,NOW(),0);")
        manifest.append({"key": key, "path": path.relative_to(source).as_posix(), "title": title, "source_sha256": hashlib.sha256(path.read_bytes()).hexdigest(), "content_bytes": len(content.encode("utf-8"))})
    index_lines = [
        "# 高性价比人生指南｜阅读目录",
        "",
        "这是一份来自 [eternity4719 的开源项目](" + SOURCE_REPO + ")的生活决策指南，涉及健康、安全、财务、法律、工作和家庭。原项目共 34 章、672 条建议，逐条列出成本、收益和出处。这里按原章节完整转载，并收录 10 份补充材料。",
        "",
        "**转载与版本**：原作《高性价比人生指南》，作者 eternity4719；[CC BY 4.0 许可](" + LICENSE + ")。本站同步的是 2026-10-08、提交 `" + SOURCE_COMMIT + "` 的版本；本站只调整导航和链接，不改写正文。项目仍在更新，医学、法律、政策和价格信息请以[原仓库](" + SOURCE_REPO + ")及最新权威资料为准。",
        "",
        "## 正文 · 34 章",
        "",
    ]
    for path in book:
        index_lines.append(f"- [{path.stem.replace('-', ' ', 1)}](__BLOG_LINK_{keys[path.resolve()]}__)")
    index_lines.extend(["", "## 延伸长文与引用对照", ""])
    for path in docs:
        index_lines.append(f"- [{path.stem}](__BLOG_LINK_{keys[path.resolve()]}__)")
    index_lines.extend([
        "", "## 原项目资料", "",
        "- [逐条核实记录](" + source_url("docs/核实记录/") + ")（131 份原始核查文件）",
        "- [在线检索](https://eternity4719.github.io/HowToLiveBetter/)",
        "- [原项目目录与阅读说明](" + source_url("README.md") + ")",
        "", "本站仅保存上述版本的文章快照。原项目的后续勘误和更新，请以 GitHub 仓库为准。",
    ])
    index_content = "\n".join(index_lines) + "\n"
    index_title = "高性价比人生指南｜阅读目录"
    sql.append(
        "INSERT INTO t_article (user_id,category_id,article_cover,article_title,seo_title,seo_description,seo_keywords,article_content,article_type,is_top,status,visit_count,create_time,update_time,is_deleted) VALUES ("
        f"1,@category_id,{sql_text(cover_url)},{sql_text(index_title)},{sql_text(index_title)},{sql_text('高性价比人生指南完整目录：34章正文、10份补充材料，附原作者、来源和许可信息。')},{sql_text('高性价比人生指南,生活指南,阅读目录')},{sql_text(index_content)},2,0,1,0,NOW(),NOW(),0);"
    )
    sql.append("SET @INDEX = LAST_INSERT_ID();")
    sql.append("INSERT INTO t_article_tag (article_id,tag_id,create_time,is_deleted) VALUES (@INDEX,@tag_id,NOW(),0);")
    for key in [*keys.values(), "INDEX"]:
        marker = f"__BLOG_LINK_{key}__"
        sql.append(f"UPDATE t_article SET article_content=REPLACE(article_content,{sql_text(marker)},CONCAT('/blog/articles/',@{key})) WHERE category_id=@category_id AND INSTR(article_content,{sql_text(marker)})>0;")
    sql.extend([
        "SELECT @INDEX AS index_id, @category_id AS category_id, @tag_id AS tag_id;",
        "SELECT COUNT(*) AS imported_articles FROM t_article WHERE category_id=@category_id AND is_deleted=0;",
        "COMMIT;",
    ])
    output.write_text("\n".join(sql) + "\n", encoding="utf-8")
    output.with_suffix(".manifest.json").write_text(json.dumps({"source_commit": SOURCE_COMMIT, "articles": manifest, "index_title": index_title}, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"Generated {len(files) + 1} articles; SQL {output.stat().st_size} bytes; {output}")


if __name__ == "__main__":
    main()
