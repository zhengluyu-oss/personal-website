"""Read-only release gate. Diagnostics contain paths/rule names, never matched values."""
import argparse
import gzip
from pathlib import Path
import re
import zipfile

CANARY = b"LY_SECURITY_CANARY_"
PATTERNS = {
    "private-key": re.compile(rb"-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----"),
    "aws-access-id": re.compile(rb"\bAKIA[A-Z0-9]{16}\b"),
    "oss-access-id": re.compile(rb"\bLTAI[A-Za-z0-9]{16,32}\b"),
}
PRIVATE_NAME = re.compile(r"(?:^|/)(?:application-[^/]+\.(?:ya?ml|properties)|\.env(?:\.[^/]+)?|rebel\.xml)$", re.I)
TEST_LIBRARY = re.compile(r"^BOOT-INF/lib/(?:junit|mockito|assertj|spring-boot-test|spring-test|byte-buddy-agent)[^/]*\.jar$", re.I)
MAX_ENTRY = 100 * 1024 * 1024

def private_values(files):
    values = set()
    for filename in files:
        for line in Path(filename).read_text(encoding="utf-8-sig").splitlines():
            match = re.match(r"\s*(?:password|key|secret-key|access-key|client-secret)\s*:\s*(.*?)\s*$", line, re.I)
            if not match:
                continue
            value = match.group(1).split(" #", 1)[0].strip("\"'")
            if len(value) >= 8 and not value.startswith(("${", "[")):
                values.add(value.encode("utf-8"))
    return values

def inspect_bytes(name, data, known_values):
    findings = []
    if CANARY in data:
        findings.append((name, "synthetic-private-canary"))
    for label, pattern in PATTERNS.items():
        if pattern.search(data):
            findings.append((name, label))
    if any(value in data for value in known_values):
        findings.append((name, "known-local-private-value"))
    return findings

def scan_jar(path, known_values=()):
    findings = []
    with zipfile.ZipFile(path) as archive:
        names = set(archive.namelist())
        required = {"BOOT-INF/classes/application.yml",
                    "BOOT-INF/classes/xyz/kuailemao/config/ProductionConfigurationGuard.class"}
        for name in sorted(required - names):
            findings.append((name, "missing-production-guard"))
        if not ({"META-INF/spring.factories", "BOOT-INF/classes/META-INF/spring.factories"} & names):
            findings.append(("META-INF/spring.factories", "missing-production-guard"))
        for entry in archive.infolist():
            if entry.is_dir():
                continue
            if PRIVATE_NAME.search(entry.filename):
                findings.append((entry.filename, "private-config-file"))
            if TEST_LIBRARY.search(entry.filename):
                findings.append((entry.filename, "test-only-runtime-dependency"))
            if entry.file_size > MAX_ENTRY:
                findings.append((entry.filename, "scan-size-budget-exceeded"))
                continue
            # Dependency archives are inventoried by path above; application bytes are scanned directly.
            if entry.filename.startswith("BOOT-INF/classes/") or entry.filename == "META-INF/spring.factories":
                findings.extend(inspect_bytes(entry.filename, archive.read(entry), known_values))
    return findings

def scan_dist(path, known_values=()):
    root = Path(path)
    if not (root / "index.html").is_file():
        return [(str(root), "missing-built-index")]
    findings = []
    for item in root.rglob("*"):
        if item.is_symlink():
            findings.append((str(item.relative_to(root)), "unexpected-symlink"))
            continue
        if not item.is_file():
            continue
        name = item.relative_to(root).as_posix()
        if PRIVATE_NAME.search(name):
            findings.append((name, "private-config-file"))
        if item.suffix not in {".html", ".js", ".css", ".json", ".map", ".txt", ".gz", ".yml", ".yaml"}:
            continue
        if item.stat().st_size > MAX_ENTRY:
            findings.append((name, "scan-size-budget-exceeded")); continue
        if item.suffix == ".gz":
            with gzip.open(item, "rb") as stream:
                data = stream.read(MAX_ENTRY + 1)
        else:
            data = item.read_bytes()
        if len(data) > MAX_ENTRY:
            findings.append((name, "scan-size-budget-exceeded")); continue
        findings.extend(inspect_bytes(name, data, known_values))
    return findings

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--jar", type=Path)
    parser.add_argument("--dist", type=Path, action="append", default=[])
    parser.add_argument("--private-config", type=Path, action="append", default=[])
    args = parser.parse_args()
    if not args.jar and not args.dist:
        parser.error("Specify a JAR or dist directory")
    try:
        values = private_values(args.private_config)
        findings = scan_jar(args.jar, values) if args.jar else []
        for dist in args.dist:
            findings.extend((str(dist / name), rule) for name, rule in scan_dist(dist, values))
    except (OSError, ValueError, zipfile.BadZipFile):
        print("FAIL: artifact could not be safely read")
        return 2
    for name, rule in findings:
        print(f"FAIL {rule}: {name}")
    if findings:
        return 1
    print("PASS: release resource/secret gate (not a complete dependency vulnerability audit)")
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
