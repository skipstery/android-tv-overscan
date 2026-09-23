#!/usr/bin/env python3
"""Build the APK using Android SDK platform 34, build tools 36 and JDK 17+, without Gradle."""

import argparse
import os
import shutil
import subprocess
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--sdk",
        type=Path,
        default=os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT"),
    )
    parser.add_argument("--android-jar", type=Path)
    parser.add_argument("--build-tools", type=Path)
    parser.add_argument("--java-home", type=Path, default=os.environ.get("JAVA_HOME"))
    parser.add_argument(
        "--keystore",
        type=Path,
        default=Path.home() / ".cache/projector-calibrator/signing.p12",
    )
    args = parser.parse_args()
    jar = args.android_jar or (
        args.sdk / "platforms/android-34/android.jar" if args.sdk else None
    )
    build_tools = args.build_tools or (
        args.sdk / "build-tools/36.0.0" if args.sdk else None
    )
    if (
        jar is None
        or build_tools is None
        or not jar.is_file()
        or not build_tools.is_dir()
    ):
        parser.error(
            "Install SDK platform android-34 and build-tools 36.0.0; pass --sdk or the explicit paths."
        )
    build = ROOT / "build"
    classes = build / "classes"
    if classes.exists():
        shutil.rmtree(classes)
    classes.mkdir(parents=True)
    (ROOT / "dist").mkdir(exist_ok=True)
    (build / "dex").mkdir(exist_ok=True)
    java_bin = args.java_home / "bin" if args.java_home else None

    def jdk(name):
        return (
            str(java_bin / (name + (".exe" if os.name == "nt" else "")))
            if java_bin
            else name
        )

    def tool(name):
        return str(build_tools / (name + (".exe" if os.name == "nt" else "")))

    def run(*command):
        subprocess.run([str(part) for part in command], check=True)

    source = sorted((ROOT / "android/src").rglob("*.java"))
    run(
        jdk("javac"),
        "--release",
        "8",
        "-encoding",
        "UTF-8",
        "-cp",
        jar,
        "-d",
        classes,
        *source,
    )
    # Put class files in a JAR to avoid Windows command line length limits.
    with zipfile.ZipFile(build / "classes.jar", "w") as archive:
        for file in classes.rglob("*.class"):
            archive.write(file, file.relative_to(classes))
    run(
        jdk("java"),
        "-cp",
        build_tools / "lib/d8.jar",
        "com.android.tools.r8.D8",
        "--min-api",
        "34",
        "--lib",
        jar,
        "--output",
        build / "dex",
        build / "classes.jar",
    )
    run(
        tool("aapt2"),
        "compile",
        "--dir",
        ROOT / "android/res",
        "-o",
        build / "resources.zip",
    )
    unsigned = build / "unsigned.apk"
    run(
        tool("aapt2"),
        "link",
        "-I",
        jar,
        "--manifest",
        ROOT / "android/AndroidManifest.xml",
        "-o",
        unsigned,
        build / "resources.zip",
    )
    with zipfile.ZipFile(unsigned, "a", zipfile.ZIP_DEFLATED) as archive:
        for file in (build / "dex").glob("*.dex"):
            archive.write(file, file.name)
    run(tool("zipalign"), "-f", "4", unsigned, build / "aligned.apk")
    # Keep this local development key across builds so upgrades preserve TV preferences.
    # Releases must retain their signing key; never commit it or publish it as an asset.
    password = os.environ.get("CALIBRATOR_STORE_PASSWORD", "local-development")
    env_name = "CALIBRATOR_BUILD_STORE_PASSWORD"
    os.environ[env_name] = password
    args.keystore.parent.mkdir(parents=True, exist_ok=True)
    if not args.keystore.exists():
        run(
            jdk("keytool"),
            "-genkeypair",
            "-keystore",
            args.keystore,
            "-storetype",
            "PKCS12",
            "-storepass:env",
            env_name,
            "-alias",
            "calibrator",
            "-keyalg",
            "RSA",
            "-keysize",
            "3072",
            "-validity",
            "10000",
            "-dname",
            "CN=Android TV Overscan",
        )
        if os.name != "nt":
            args.keystore.chmod(0o600)
    apk = ROOT / "dist/android-tv-overscan.apk"
    run(
        jdk("java"),
        "-jar",
        build_tools / "lib/apksigner.jar",
        "sign",
        "--ks",
        args.keystore,
        "--ks-pass",
        "env:" + env_name,
        "--ks-key-alias",
        "calibrator",
        "--out",
        apk,
        build / "aligned.apk",
    )
    run(
        jdk("java"),
        "-jar",
        build_tools / "lib/apksigner.jar",
        "verify",
        "--verbose",
        apk,
    )
    print("Built " + str(apk))


if __name__ == "__main__":
    main()
