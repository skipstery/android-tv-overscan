#!/usr/bin/env python3
"""Guided ADB setup and launcher for Android TV Overscan. Python 3.9+, no pip dependencies."""

import argparse
import getpass
import hashlib
import os
import platform
import re
import shlex
import shutil
import subprocess
import sys
import tempfile
import time
import urllib.request
import webbrowser
import zipfile
from pathlib import Path

PACKAGE = "com.skipstery.projectorcalibrator"
AUTHORITY = PACKAGE + ".bridge"
PLATFORM_TOOLS = "https://developer.android.com/tools/releases/platform-tools"
ADB_GUIDE = "https://developer.android.com/tools/adb#connect-to-a-device-over-wi-fi"
LOG = "/data/local/tmp/android-tv-overscan-bridge.log"


class SetupError(Exception):
    pass


def ask(prompt, interactive):
    if not interactive:
        raise SetupError(
            "Interactive setup is needed. Run without --non-interactive in a terminal."
        )
    return input(prompt).strip()


def run(command, *, timeout=45, check=True, input_text=None):
    try:
        result = subprocess.run(
            command,
            check=False,
            input=input_text,
            capture_output=True,
            text=True,
            encoding="utf-8",
            errors="replace",
            timeout=timeout,
        )
    except subprocess.TimeoutExpired as error:
        raise SetupError(
            "Command timed out. Check the streamer and network, then run again."
        ) from error
    except OSError as error:
        raise SetupError(str(error)) from error
    output = (result.stdout + result.stderr).strip()
    if check and result.returncode:
        raise SetupError(output or "Command failed: " + command[0])
    return output


def download_adb(cache):
    suffix = {"Darwin": "darwin", "Linux": "linux", "Windows": "windows"}.get(
        platform.system()
    )
    if not suffix:
        raise SetupError(
            "Install Android SDK Platform-Tools for your system and use --adb PATH."
        )
    url = (
        "https://dl.google.com/android/repository/platform-tools-latest-"
        + suffix
        + ".zip"
    )
    cache.mkdir(parents=True, exist_ok=True)
    print("Downloading Android SDK Platform-Tools from dl.google.com...")
    with tempfile.TemporaryDirectory(prefix="android-tv-overscan-") as temporary:
        archive = Path(temporary) / "platform-tools.zip"
        with (
            urllib.request.urlopen(url, timeout=60) as response,
            archive.open("wb") as target,
        ):
            shutil.copyfileobj(response, target)
        with zipfile.ZipFile(archive) as source:
            for entry in source.infolist():
                parts = Path(entry.filename).parts
                if (
                    not parts
                    or parts[0] != "platform-tools"
                    or ".." in parts
                    or Path(entry.filename).is_absolute()
                ):
                    raise SetupError("Unexpected path in Platform-Tools archive.")
            source.extractall(cache)
    executable = cache / "platform-tools" / ("adb.exe" if os.name == "nt" else "adb")
    if os.name != "nt":
        executable.chmod(0o755)
    return str(executable)


def find_adb(explicit, interactive):
    cache = Path.home() / ".cache" / "android-tv-overscan"
    candidates = (
        [explicit]
        if explicit
        else [
            shutil.which("adb"),
            str(cache / "platform-tools" / ("adb.exe" if os.name == "nt" else "adb")),
        ]
    )
    for candidate in candidates:
        if candidate and Path(candidate).is_file():
            run([candidate, "version"])
            return candidate
    if explicit:
        raise SetupError(
            "ADB was not found at --adb. Provide the path to adb or adb.exe."
        )
    print(
        "ADB is not installed. The utility can download Google's Android SDK Platform-Tools."
    )
    print("Read Google's SDK license at: " + PLATFORM_TOOLS)
    answer = ask("Open the license page in your browser? [y/N] ", interactive).lower()
    if answer == "y":
        webbrowser.open(PLATFORM_TOOLS)
    answer = ask(
        "Accept Google's SDK license and download Platform-Tools? [y/N] ", interactive
    ).lower()
    if answer == "y":
        executable = download_adb(cache)
        run([executable, "version"])
        return executable
    path = Path(
        ask(
            "Or enter the full path to your existing adb executable: ", interactive
        ).strip('"')
    ).expanduser()
    if not path.is_file():
        raise SetupError(
            "ADB is required. Download Platform-Tools, extract the ZIP, and run again."
        )
    run([str(path), "version"])
    return str(path)


def parse_devices(output):
    devices = []
    for line in output.splitlines():
        fields = line.split()
        if len(fields) >= 2 and fields[1] in {
            "device",
            "offline",
            "unauthorized",
            "no",
        }:
            model = next(
                (
                    part[6:].replace("_", " ")
                    for part in fields[2:]
                    if part.startswith("model:")
                ),
                fields[0],
            )
            devices.append((fields[0], fields[1], model))
    return devices


def endpoint(value, default_port=None):
    value = value.strip()
    if not re.fullmatch(
        r"(?:[A-Za-z0-9][A-Za-z0-9._-]*|\[[0-9a-fA-F:]+\])(?::[0-9]{1,5})?", value
    ):
        raise SetupError(
            "Enter an IP address or hostname and port, such as 192.168.1.50:37123."
        )
    has_port = re.search(r":([0-9]+)$", value)
    if not has_port:
        if default_port is None:
            raise SetupError("Include the port shown on the TV after a colon.")
        return value + ":" + str(default_port)
    if not 1 <= int(has_port.group(1)) <= 65535:
        raise SetupError("The port must be between 1 and 65535.")
    return value


def pair(adb, address, interactive):
    address = endpoint(address)
    if not interactive:
        raise SetupError(
            "Pairing needs a code from the TV. Run interactively or use adb pair first."
        )
    code = getpass.getpass("Six-digit pairing code shown on the TV: ").strip()
    if not re.fullmatch(r"\d{6}", code):
        raise SetupError("Use the six-digit code from 'Pair device with pairing code'.")
    print(run([adb, "pair", address], input_text=code + "\n", timeout=60))


def guide_connection(adb, interactive):
    print("\nOn your Google TV, use the remote:")
    print("  1. Open Settings > System > About.")
    print(
        "  2. Select Android TV OS build seven times, until developer mode is enabled."
    )
    print("  3. Go back to System > Developer options.")
    print(
        "Menu names vary by manufacturer. Some TVs put About under Device Preferences."
    )
    print("\nKeep the computer and streamer on the same local network.")
    print("  1. Wireless debugging with a pairing code, recommended when available")
    print("  2. Network/ADB debugging with an IP address and port")
    print("  3. USB debugging with a data cable, or retry an existing connection")
    choice = ask("Connection method [1/2/3, default 1]: ", interactive) or "1"
    if choice == "1":
        print("Enable Wireless debugging and allow it for this network.")
        print("Open Pair device with pairing code. Leave that dialog open.")
        print("Use the IP address and PAIRING port from this dialog.")
        pair(adb, ask("Pairing IP:port: ", interactive), interactive)
        print("Return to the main Wireless debugging screen on the TV.")
        print(
            "Its IP address & port is the CONNECT address. It usually has a different port."
        )
        address = endpoint(ask("Connection IP:port: ", interactive))
        print(run([adb, "connect", address], check=False))
    elif choice == "2":
        print("Enable Network debugging or ADB debugging if your TV offers it.")
        print(
            "Use the address shown there. If it explicitly uses port 5555, enter IP:5555."
        )
        print("Enabling USB debugging alone does not enable network ADB on every TV.")
        address = endpoint(ask("Connection IP:port: ", interactive))
        print(run([adb, "connect", address], check=False))
        print(
            "If the TV asks 'Allow USB debugging?', approve this computer with the remote."
        )
        ask("Press Enter after approving the TV prompt. ", interactive)
    elif choice == "3":
        print(
            "Enable USB debugging in Developer options and connect a data-capable USB cable."
        )
        print(
            "Only use this route if your streamer's USB port supports ADB data access."
        )
        print("On the TV, approve 'Allow USB debugging?' for this computer.")
        print(
            "Windows may need the manufacturer's USB driver. Wireless pairing avoids that step."
        )
        ask("Press Enter to check the connection again. ", interactive)
    else:
        print("Choose 1, 2, or 3.")


def select_device(adb, serial, interactive):
    while True:
        devices = parse_devices(run([adb, "devices", "-l"]))
        if serial:
            devices = [item for item in devices if item[0] == serial]
        ready = [item for item in devices if item[1] == "device"]
        if len(ready) == 1:
            print("Connected: " + ready[0][2])
            return ready[0][0]
        if len(ready) > 1:
            for i, (_, _, model) in enumerate(ready, 1):
                print("  " + str(i) + ". " + model + " [" + ready[i - 1][0] + "]")
            if not interactive:
                raise SetupError(
                    "Several devices are connected. Choose the streamer with --serial SERIAL."
                )
            choice = ask("Choose your streamer: ", interactive)
            if choice.isdigit() and 1 <= int(choice) <= len(ready):
                return ready[int(choice) - 1][0]
            print("Enter a number from the list.")
            continue
        if not interactive:
            raise SetupError(
                "No authorized streamer is connected. Run interactively for ADB setup."
            )
        if any(item[1] == "unauthorized" for item in devices):
            print("On the TV, approve 'Allow USB debugging?' for this computer.")
            print(
                "If the prompt is missing, toggle debugging off and on, then reconnect."
            )
            ask("Press Enter after approving access. ", interactive)
        else:
            if devices:
                print(
                    "The connection is offline. Re-enable debugging and use the TV's current port."
                )
            if serial:
                print("Requested device is not connected: " + serial)
                print("If its wireless address changed, restart without --serial.")
            print("No connected streamer yet. Setup guide: " + ADB_GUIDE)
            guide_connection(adb, interactive)


def helper_command(apk, user):
    if not apk.startswith("/") or "\n" in apk or not str(user).isdigit():
        raise SetupError("Invalid installed APK path or Android user ID.")
    return (
        "CLASSPATH=" + shlex.quote(apk) + " nohup app_process /system/bin "
        "--nice-name=android-tv-overscan-bridge "
        + PACKAGE
        + ".ShellBridge "
        + str(user)
        + " > "
        + shlex.quote(LOG)
        + " 2>&1 < /dev/null &"
    )


def launch(adb, serial, apk, reinstall=False, restart=False):
    base = [adb, "-s", serial]

    def shell(command, **kwargs):
        return run(base + ["shell", command], **kwargs)

    sdk = shell("getprop ro.build.version.sdk")
    if not sdk.isdigit() or int(sdk) != 34:
        raise SetupError(
            "This release supports Android TV 14 only. This device reports API "
            + sdk
            + "."
        )
    if shell("pm has-feature android.software.leanback").strip() != "true":
        raise SetupError(
            "Select an Android TV or Google TV streamer, not a phone or emulator."
        )
    user = shell("am get-current-user").strip()
    if not user.isdigit():
        raise SetupError("Could not determine the active Android user.")
    if not apk.is_file():
        raise SetupError(
            "APK not found. Download the complete release ZIP, or pass --apk PATH."
        )
    local_digest = hashlib.sha256(apk.read_bytes()).hexdigest()

    def package_path():
        locations = shell(
            "pm path --user " + user + " " + PACKAGE, check=False
        ).splitlines()
        return next(
            (
                line[len("package:") :]
                for line in locations
                if line.startswith("package:") and line.endswith("/base.apk")
            ),
            None,
        )

    def matches_local(path):
        if not path:
            return False
        output = shell("sha256sum " + shlex.quote(path), check=False).split()
        return bool(output) and output[0] == local_digest

    installed_apk = package_path()
    if reinstall or not matches_local(installed_apk):
        print("[3/4] Installing the TV app...")
        print(run(base + ["install", "--user", user, "-r", str(apk)], timeout=120))
        installed_apk = package_path()
        if not matches_local(installed_apk):
            raise SetupError(
                "Installed APK verification failed. Display control was not started."
            )
    else:
        print("[3/4] TV app is already installed.")
    if restart:
        shell("am force-stop --user " + user + " " + PACKAGE)
    shell("am start --user " + user + " -n " + PACKAGE + "/.MainActivity")
    status_command = (
        "content call --user "
        + user
        + " --uri content://"
        + AUTHORITY
        + " --method status"
    )
    status = shell(status_command, check=False)
    if "ready=true" not in status:
        if installed_apk is None:
            raise SetupError("Could not locate the installed APK.")
        print("[4/4] Starting display control...")
        shell(helper_command(installed_apk, user))
        deadline = time.monotonic() + 15
        while time.monotonic() < deadline:
            time.sleep(0.5)
            if "ready=true" in shell(status_command, check=False):
                break
        else:
            log = shell("tail -n 35 " + shlex.quote(LOG), check=False)
            raise SetupError("Display control could not start. Details:\n" + log)
    else:
        print("[4/4] Display control is already running.")
    print("\nReady. Use your TV remote to calibrate the picture.")
    print("  Up / Down: choose an edge. Left / Right: decrease / increase its margin.")
    print("  Done: save and close. Grid only: Back or OK restores controls.")
    print(
        "  Show original: temporarily show the uncorrected picture. Reset: save zero margins."
    )
    print(
        "You can close this terminal. After a full streamer reboot, run this utility again."
    )


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adb", help="path to adb or adb.exe")
    parser.add_argument(
        "--serial", help="ADB device serial when several devices are connected"
    )
    parser.add_argument(
        "--connect", metavar="IP:PORT", help="connect to this network ADB address"
    )
    parser.add_argument(
        "--pair", metavar="IP:PORT", help="pair using a code shown on the TV"
    )
    parser.add_argument(
        "--apk", type=Path, help="TV APK path, defaults to the release folder or dist/"
    )
    parser.add_argument(
        "--reinstall",
        action="store_true",
        help="reinstall the APK while keeping saved margins",
    )
    parser.add_argument(
        "--restart", action="store_true", help="restart the app and its ADB helper"
    )
    parser.add_argument(
        "--non-interactive",
        action="store_true",
        help="fail with instructions instead of asking questions",
    )
    args = parser.parse_args(argv)
    interactive = not args.non_interactive and sys.stdin.isatty()
    try:
        print("Android TV Overscan\n[1/4] Checking ADB...")
        adb = find_adb(args.adb, interactive)
        run([adb, "start-server"])
        print("[2/4] Connecting to your streamer...")
        if args.pair:
            pair(adb, args.pair, interactive)
        if args.connect:
            print(run([adb, "connect", endpoint(args.connect)], check=False))
        serial = select_device(adb, args.serial, interactive)
        folder = Path(__file__).resolve().parent
        apk = args.apk or folder / "android-tv-overscan.apk"
        if not args.apk and not apk.is_file():
            apk = folder / "dist" / "android-tv-overscan.apk"
        launch(adb, serial, apk, args.reinstall, args.restart)
        return 0
    except (SetupError, OSError, zipfile.BadZipFile) as error:
        print("\nSetup incomplete: " + str(error), file=sys.stderr)
        print(
            "Your saved TV margins have been kept. Fix the issue and run the utility again.",
            file=sys.stderr,
        )
        return 1
    except (KeyboardInterrupt, EOFError):
        print("\nSetup stopped. Run the utility again when ready.", file=sys.stderr)
        return 130


if __name__ == "__main__":
    sys.exit(main())
