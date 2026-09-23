#!/usr/bin/env python3
import argparse
import os
import subprocess
import tempfile
from pathlib import Path

root = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser(
    description="Run geometry and safety-bound checks without an Android device"
)
parser.add_argument("--java-home", type=Path, default=os.environ.get("JAVA_HOME"))
args = parser.parse_args()


def jdk(name):
    return (
        str(args.java_home / "bin" / (name + (".exe" if os.name == "nt" else "")))
        if args.java_home
        else name
    )


with tempfile.TemporaryDirectory(prefix="calibrator-tests-") as output:
    subprocess.run(
        [
            jdk("javac"),
            "--release",
            "8",
            "-d",
            output,
            str(root / "android/src/com/skipstery/projectorcalibrator/Insets.java"),
            str(root / "tests/InsetsTest.java"),
        ],
        check=True,
    )
    subprocess.run([jdk("java"), "-cp", output, "InsetsTest"], check=True)
