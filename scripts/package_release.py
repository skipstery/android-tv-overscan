#!/usr/bin/env python3
"""Package the built APK and Python launcher for distribution."""

import hashlib
import zipfile
from pathlib import Path

root = Path(__file__).resolve().parents[1]
output = root / "dist"
archive_path = output / "projector-calibrator-0.1.0.zip"
with zipfile.ZipFile(archive_path, "w", zipfile.ZIP_DEFLATED) as archive:
    for source, target in [
        (root / "projector_calibrator.py", "projector_calibrator.py"),
        (root / "README.md", "README.md"),
        (output / "projector-calibrator.apk", "projector-calibrator.apk"),
        (root / "docs/architecture.md", "docs/architecture.md"),
        (root / "docs/verification.md", "docs/verification.md"),
    ]:
        archive.write(source, "projector-calibrator/" + target)
files = [archive_path, output / "projector-calibrator.apk"]
(output / "SHA256SUMS.txt").write_text(
    "".join(
        hashlib.sha256(file.read_bytes()).hexdigest() + "  " + file.name + "\n"
        for file in files
    ),
    encoding="utf-8",
)
print(archive_path)
