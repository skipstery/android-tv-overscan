#!/usr/bin/env python3
"""Package the built APK and Python launcher for distribution."""

import hashlib
import zipfile
from pathlib import Path

root = Path(__file__).resolve().parents[1]
output = root / "dist"
archive_path = output / "android-tv-overscan-0.2.0.zip"
with zipfile.ZipFile(archive_path, "w", zipfile.ZIP_DEFLATED) as archive:
    for source, target in [
        (root / "android_tv_overscan.py", "android_tv_overscan.py"),
        (root / "README.md", "README.md"),
        (output / "android-tv-overscan.apk", "android-tv-overscan.apk"),
        (root / "docs/architecture.md", "docs/architecture.md"),
        (root / "docs/verification.md", "docs/verification.md"),
        (
            root / "docs/images/overscan-calibration.png",
            "docs/images/overscan-calibration.png",
        ),
    ]:
        archive.write(source, "android-tv-overscan/" + target)
files = [archive_path, output / "android-tv-overscan.apk"]
(output / "SHA256SUMS.txt").write_text(
    "".join(
        hashlib.sha256(file.read_bytes()).hexdigest() + "  " + file.name + "\n"
        for file in files
    ),
    encoding="utf-8",
)
print(archive_path)
