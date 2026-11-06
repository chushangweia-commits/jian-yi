#!/usr/bin/env python3
"""Reject likely secrets in public source or the unsigned release APK.

Only file paths are reported. Matched values are intentionally never printed.
"""
from __future__ import annotations

import argparse
from pathlib import Path
import re
import zipfile


ROOT = Path(__file__).resolve().parents[1]
IGNORED_PARTS = {
    ".git", "build", "dist", "out", ".gradle", ".idea", "node_modules",
    "ai-test", "backup-test", "engine-check", "engine-test", "entry-compile",
    "host-review", "host-test", "host-test-classes", "host-test-src", "policy-test",
    "upgrade-test", "archive",
}
LOCAL_ONLY_ASSETS = {"assets/guests-activity-map.json", "assets/private-api-bootstrap.json"}
CREDENTIAL_PATTERN = re.compile(
    rb"(?<![A-Za-z0-9_])(?:sk-[A-Za-z0-9_-]{20,}|AIza[0-9A-Za-z_-]{20,}|"
    rb"gh[pousr]_[A-Za-z0-9_]{20,}|github_pat_[A-Za-z0-9_]{20,}|"
    rb"xox[baprs]-[A-Za-z0-9-]{10,}|-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----)"
)
ASSIGNMENT_PATTERN = re.compile(
    rb"(?im)(?:^|[;,{\n])\s*(?:export\s+)?(?:const|let|var)?\s*[\"']?"
    rb"(?:api[_-]?key|access[_-]?token|auth[_-]?token|client[_-]?secret|"
    rb"private[_-]?key|data[_-]?encryption[_-]?key)[\"']?\s*[:=]\s*"
    rb"[\"']?([^\s\"';,}]{12,})"
)
PLACEHOLDER_PREFIXES = (b"${", b"{{", b"$", b"your_", b"your-", b"example", b"replace", b"change", b"redacted", b"<")


def is_placeholder(value: bytes) -> bool:
    return not value or value.lower().startswith(PLACEHOLDER_PREFIXES)


def contains_secret(data: bytes) -> bool:
    return bool(CREDENTIAL_PATTERN.search(data)) or any(
        not is_placeholder(match.group(1)) for match in ASSIGNMENT_PATTERN.finditer(data)
    )


def relative(path: Path) -> str:
    try:
        return path.relative_to(ROOT).as_posix()
    except ValueError:
        return path.name


def scan_source() -> list[str]:
    findings: list[str] = []
    for path in ROOT.rglob("*"):
        if not path.is_file() or IGNORED_PARTS.intersection(path.relative_to(ROOT).parts):
            continue
        if relative(path) in LOCAL_ONLY_ASSETS:
            continue
        try:
            if contains_secret(path.read_bytes()):
                findings.append(relative(path))
        except OSError:
            findings.append(relative(path) + " (unreadable)")
    return findings


def scan_apk(apk: Path) -> list[str]:
    findings: list[str] = []
    try:
        with zipfile.ZipFile(apk) as archive:
            for entry in archive.infolist():
                if not entry.is_dir() and contains_secret(archive.read(entry)):
                    findings.append(f"{apk.name}!{entry.filename}")
    except (OSError, zipfile.BadZipFile):
        findings.append(f"{apk.name} (unreadable APK)")
    return findings


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", action="store_true", help="scan the public source tree")
    parser.add_argument("--apk", type=Path, help="scan each entry of an unsigned APK")
    args = parser.parse_args()
    findings = scan_source() if args.source else []
    if args.apk is not None:
        findings.extend(scan_apk(args.apk.resolve()))
    if findings:
        print("Sensitive-value scan failed; values are withheld:")
        for path in sorted(set(findings)):
            print(f" - {path}")
        raise SystemExit(1)
    print("Sensitive-value scan passed; no values were displayed.")


if __name__ == "__main__":
    main()
