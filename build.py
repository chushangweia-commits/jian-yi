#!/usr/bin/env python3
"""Build the JianYi release with the established clean identity."""
from pathlib import Path
import copy
import hashlib
import json
import os
import shutil
import struct
import subprocess
import sys
import zipfile

ROOT = Path(__file__).resolve().parent
SECURITY = Path(os.environ.get("JIANYI_ANDROID_SECURITY", ROOT.parent / "security-clean"))
TOOLS = Path(os.environ.get("JIANYI_ANDROID_TOOLS", ROOT / "tools/android-analysis"))
JDK = TOOLS / "jdk/jdk-21.0.12.1+1/Contents/Home/bin"
AAPT2 = SECURITY / "build-tools/aapt2-real"
SOURCE = ROOT / "src"
ASSETS = ROOT / "assets"
SECRET_SCAN = ROOT / "tools/release-secret-scan.py"
BUILD = ROOT / "build"
STAGED_SOURCE = BUILD / "src/app/jianyi/yixue/runtime"
CLASSES = BUILD / "classes"
DEX = BUILD / "dex"
COMPILED_RES = BUILD / "compiled-res.zip"
BASE = BUILD / "base.apk"
UNSIGNED = BUILD / "jianyi-fresh-unsigned.apk"
JAR = BUILD / "jianyi-fresh-classes.jar"
OLD_NAMESPACE = "com.jianyi.yixue.app"
NEW_NAMESPACE = "app.jianyi.yixue.runtime"


def run(command):
    subprocess.run([str(value) for value in command], check=True)


def sha256(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


required_paths = [JDK / "javac", JDK / "java", AAPT2, TOOLS / "platform35/android.jar",
                  TOOLS / "lunar-1.7.7.jar", TOOLS / "r8-9.5.26.jar", SECRET_SCAN]
missing = [str(path) for path in required_paths if not path.exists()]
if missing:
    raise SystemExit(
        "Missing build prerequisites. Set JIANYI_ANDROID_TOOLS and "
        "JIANYI_ANDROID_SECURITY as described in README.md."
    )

run([sys.executable, SECRET_SCAN, "--source"])

if BUILD.exists():
    shutil.rmtree(BUILD)
STAGED_SOURCE.mkdir(parents=True)
CLASSES.mkdir()
DEX.mkdir()

sources = sorted(SOURCE.glob("*.java"))
if len(sources) != 41:
    raise SystemExit(f"Expected 41 reviewed Java sources, found {len(sources)}")
for source in sources:
    text = source.read_text()
    if not text.startswith("package " + OLD_NAMESPACE + ";"):
        raise SystemExit("Unexpected source namespace: " + str(source))
    text = text.replace("package " + OLD_NAMESPACE + ";", "package " + NEW_NAMESPACE + ";", 1)
    text = text.replace('"guest_', '"jianyi_')
    (STAGED_SOURCE / source.name).write_text(text)

run([JDK / "javac", "--release", "8", "-cp",
     str(TOOLS / "platform35/android.jar") + ":" + str(TOOLS / "lunar-1.7.7.jar"),
     "-d", CLASSES, *sorted(STAGED_SOURCE.glob("*.java"))])
with zipfile.ZipFile(JAR, "w", zipfile.ZIP_DEFLATED) as output:
    for path in sorted(CLASSES.rglob("*.class")):
        output.write(path, path.relative_to(CLASSES).as_posix())
run([JDK / "java", "-cp", TOOLS / "r8-9.5.26.jar", "com.android.tools.r8.D8",
     "--release", "--min-api", "24", "--lib", TOOLS / "platform35/android.jar",
     "--output", DEX, JAR, TOOLS / "lunar-1.7.7.jar"])
if sorted(path.name for path in DEX.glob("classes*.dex")) != ["classes.dex"]:
    raise SystemExit("Fresh release must contain exactly one DEX")

run([AAPT2, "compile", "--dir", ROOT / "res", "-o", COMPILED_RES])
run([AAPT2, "link", "-o", BASE, "-I", TOOLS / "platform35/android.jar",
     "--manifest", ROOT / "AndroidManifest.xml", "--min-sdk-version", "24",
     "--target-sdk-version", "35", "--version-code", "29", "--version-name", "2.3.11",
     "--auto-add-overlay", "-R", COMPILED_RES])

with zipfile.ZipFile(BASE) as source, zipfile.ZipFile(UNSIGNED, "w") as output:
    for info in source.infolist():
        if info.filename.upper().startswith("META-INF/"):
            continue
        item = copy.copy(info)
        item.extra = b""
        if item.compress_type == zipfile.ZIP_STORED:
            start = output.fp.tell() + 30 + len(item.filename.encode("utf-8")) + 4
            padding = (-start) % 4
            item.extra = struct.pack("<HH", 0xD935, padding) + bytes(padding)
        output.writestr(item, source.read(info.filename))
    dex_info = zipfile.ZipInfo("classes.dex")
    dex_info.compress_type = zipfile.ZIP_STORED
    start = output.fp.tell() + 30 + len(dex_info.filename.encode("utf-8")) + 4
    padding = (-start) % 4
    dex_info.extra = struct.pack("<HH", 0xD935, padding) + bytes(padding)
    output.writestr(dex_info, (DEX / "classes.dex").read_bytes())
    excluded = {"private-api-bootstrap.json", "guests-activity-map.json"}
    for path in sorted(ASSETS.iterdir()):
        if path.is_file() and path.name not in excluded:
            output.write(path, "assets/appdata/" + path.name, compress_type=zipfile.ZIP_DEFLATED)

with zipfile.ZipFile(UNSIGNED) as apk:
    names = apk.namelist()
    if len(names) != len(set(names)):
        raise SystemExit("Duplicate ZIP entries")
    if apk.testzip() is not None:
        raise SystemExit("ZIP integrity failure")
    if [name for name in names if name.startswith("classes") and name.endswith(".dex")] != ["classes.dex"]:
        raise SystemExit("Unexpected DEX inventory")
    if any(name.startswith(("lib/", "assets/guest/", "META-INF/")) for name in names):
        raise SystemExit("Forbidden archive path")
    if any(".." in Path(name).parts or name.startswith("/") for name in names):
        raise SystemExit("Unsafe ZIP path")
    dex_bytes = apk.read("classes.dex")
    forbidden = [b"com/jianyi/yixue", b"com/idcsc/gwxzy_app", b"Personal APK Repair",
                 b"REPAIR", b"GuestMode", b"guest_visual", b"guest_meihua"]
    for marker in forbidden:
        if marker in dex_bytes:
            raise SystemExit("Forbidden DEX marker: " + marker.decode(errors="replace"))
    for required in [b"app/jianyi/yixue/runtime", b"FoundationPracticeActivity", b"LiuYaoEngine",
                     b"jy.pv1.2026.gm.7f4d2a9c61e8"]:
        if required not in dex_bytes:
            raise SystemExit("Missing DEX marker: " + required.decode())
    payload = b"".join(apk.read(name) for name in names)
    if b"sk-" in payload:
        raise SystemExit("Possible embedded API key")

report = {
    "application_id": "app.jianyi.yixue",
    "runtime_namespace": NEW_NAMESPACE,
    "version_code": 29,
    "version_name": "2.3.11",
    "sources": len(sources),
    "provenance_marker": "jy.pv1.2026.gm.7f4d2a9c61e8",
    "unsigned_apk": str(UNSIGNED),
    "unsigned_sha256": sha256(UNSIGNED),
}
(BUILD / "build-report.json").write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n")
run([sys.executable, SECRET_SCAN, "--apk", UNSIGNED])
print(json.dumps(report, ensure_ascii=False))
