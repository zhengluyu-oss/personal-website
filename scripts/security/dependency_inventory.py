"""Read final JAR + every checked-in frontend lock; optionally query public OSV by coordinates only.

No source, configuration, private registry URL or local absolute path is sent to OSV.
Output is generated evidence, not a claim that every listed advisory is reachable.
Requires PyYAML for pnpm locks. No install or lockfile modification is performed.
"""
import argparse
import hashlib
import io
import json
import re
import urllib.request
import zipfile
from pathlib import Path
import yaml

ROOT = Path(__file__).resolve().parents[2]


def fingerprint(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def jar_inventory(path, dependency_tree=None):
    resolved = {}
    if dependency_tree:
        for line in dependency_tree.read_text(encoding="utf-8").splitlines():
            match = re.search(r"([\w.\-]+):([\w.\-]+):jar:([^\s:]+):(?:compile|runtime|test|provided)", line)
            if match:
                group, artifact, version = match.groups()
                resolved[f"{artifact}-{version}.jar"] = (group + ":" + artifact, version)
    rows = []
    with zipfile.ZipFile(path) as jar:
        for name in sorted(jar.namelist()):
            if not name.startswith("BOOT-INF/lib/") or not name.endswith(".jar"):
                continue
            coordinates = []
            with zipfile.ZipFile(io.BytesIO(jar.read(name))) as library:
                for metadata in library.namelist():
                    if metadata.startswith("META-INF/maven/") and metadata.endswith("/pom.properties"):
                        props = dict(re.findall(r"^(groupId|artifactId|version)=(.+)$", library.read(metadata).decode("utf-8").replace("\r", ""), re.M))
                        if len(props) == 3:
                            coordinates.append((props["groupId"] + ":" + props["artifactId"], props["version"]))
            # Many Spring jars omit Maven metadata; their stable group is determined explicitly.
            filename = name.split("/")[-1]
            if filename in resolved:
                coordinates = [resolved[filename]]
            match = re.match(r"(.+?)-(\d[^/]*).jar$", filename)
            if not coordinates and match:
                artifact, version = match.groups()
                groups = [("spring-boot-", "org.springframework.boot"), ("spring-security-", "org.springframework.security"),
                          ("spring-data-", "org.springframework.data"), ("spring-", "org.springframework"),
                          ("tomcat-embed-", "org.apache.tomcat.embed")]
                group = next((g for prefix, g in groups if artifact.startswith(prefix)), None)
                if group:
                    coordinates = [(group + ":" + artifact, version)]
            rows.append({"source": "backend-jar", "entry": name, "scope": "packaged-runtime",
                         "coordinates": [{"ecosystem": "Maven", "name": n, "version": v} for n, v in coordinates],
                         "coordinateStatus": "resolved" if coordinates else "unresolved-review-required"})
    return rows


def lock_inventory(path):
    source = path.relative_to(ROOT).as_posix()
    rows = []
    if path.name == "package-lock.json":
        lock = json.loads(path.read_text(encoding="utf-8"))
        for location, package in lock["packages"].items():
            if not location or not package.get("version"):
                continue
            name = package.get("name") or location.rsplit("node_modules/", 1)[-1]
            rows.append({"source": source, "scope": "dev-only" if package.get("dev") else "production-or-shared",
                         "coordinates": [{"ecosystem": "npm", "name": name, "version": package["version"]}]})
    else:
        lock = yaml.safe_load(path.read_text(encoding="utf-8"))
        for coordinate, package in lock["packages"].items():
            bare = coordinate.lstrip("/").split("(", 1)[0]
            name, separator, version = bare.rpartition("@")
            if not separator or not name:
                raise ValueError("Unsupported pnpm package coordinate")
            rows.append({"source": source, "scope": "dev-only" if package.get("dev") is True else "lock-entry-reachability-unverified",
                         "coordinates": [{"ecosystem": "npm", "name": name, "version": version}]})
    return rows


def audit_osv(rows):
    coordinates = sorted({(c["ecosystem"], c["name"], c["version"]) for row in rows for c in row["coordinates"]})
    results = []
    for offset in range(0, len(coordinates), 100):
        batch = coordinates[offset:offset + 100]
        body = {"queries": [{"package": {"ecosystem": e, "name": n}, "version": v} for e, n, v in batch]}
        request = urllib.request.Request("https://api.osv.dev/v1/querybatch", data=json.dumps(body).encode(), headers={"Content-Type": "application/json"})
        with urllib.request.urlopen(request, timeout=60) as response:
            answers = json.load(response)["results"]
        if not isinstance(answers, list) or len(answers) != len(batch):
            raise ValueError("Incomplete advisory response; audit is not complete")
        for coordinate, answer in zip(batch, answers):
            if answer.get("vulns") or answer.get("next_page_token"):
                results.append({"ecosystem": coordinate[0], "name": coordinate[1], "version": coordinate[2],
                                "advisories": answer.get("vulns", []), "paginationPending": bool(answer.get("next_page_token"))})
    return results


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--jar", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--osv", action="store_true")
    parser.add_argument("--maven-tree", type=Path, help="Matching Maven dependency:tree output to resolve jars without embedded metadata")
    args = parser.parse_args()
    locks = [ROOT / "blog-frontend/kuailemao-blog/pnpm-lock.yaml",
             ROOT / "blog-frontend/kuailemao-admin/pnpm-lock.yaml"]
    rows = jar_inventory(args.jar, args.maven_tree)
    for lock in locks:
        rows.extend(lock_inventory(lock))
    result = {"schema": 1, "jarSha256": fingerprint(args.jar),
              "lockSha256": {p.relative_to(ROOT).as_posix(): fingerprint(p) for p in locks}, "dependencies": rows,
              "auditStatus": "not-run", "note": "Inventory/advisories are not reachability or exploitability verdicts. Deployment uses each project's pinned pnpm version and frozen lockfile."}
    if args.osv:
        result["advisoryMatches"] = audit_osv(rows)
        result["auditStatus"] = "pagination-pending" if any(x["paginationPending"] for x in result["advisoryMatches"]) else "completed-coordinate-query"
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"entries": len(rows), "unresolvedJarEntries": sum(x.get("coordinateStatus") == "unresolved-review-required" for x in rows),
                      "auditStatus": result["auditStatus"], "affectedCoordinates": len(result.get("advisoryMatches", []))}))


if __name__ == "__main__":
    main()
