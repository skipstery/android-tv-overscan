import hashlib
import io
import shlex
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

import projector_calibrator as app


class LauncherTests(unittest.TestCase):
    def test_pairing_code_is_sent_through_stdin(self):
        with (
            patch.object(app.getpass, "getpass", return_value="123456"),
            patch.object(app, "run", return_value="Successfully paired") as run,
            patch("sys.stdout", new=io.StringIO()),
        ):
            app.pair("adb", "192.168.1.2:37123", True)
        self.assertEqual(run.call_args.args[0], ["adb", "pair", "192.168.1.2:37123"])
        self.assertEqual(run.call_args.kwargs["input_text"], "123456\n")

    def test_wireless_guide_uses_distinct_pairing_and_connection_ports(self):
        with (
            patch.object(
                app, "ask", side_effect=["1", "192.168.1.2:37123", "192.168.1.2:40211"]
            ),
            patch.object(app, "pair") as pair,
            patch.object(app, "run", return_value="connected") as run,
            patch("sys.stdout", new=io.StringIO()),
        ):
            app.guide_connection("adb", True)
        pair.assert_called_once_with("adb", "192.168.1.2:37123", True)
        self.assertEqual(run.call_args.args[0], ["adb", "connect", "192.168.1.2:40211"])

    def test_unverified_apk_cannot_be_started_as_shell(self):
        commands = []

        def fake_run(command, **kwargs):
            commands.append(command)
            return {
                "getprop ro.build.version.sdk": "34",
                "pm has-feature android.software.leanback": "true",
                "am get-current-user": "0",
                "pm path --user 0 " + app.PACKAGE: "package:/data/app/test/base.apk",
                "sha256sum /data/app/test/base.apk": "0" * 64
                + "  /data/app/test/base.apk",
            }.get(command[-1], "Success")

        with (
            tempfile.TemporaryDirectory() as folder,
            patch.object(app, "run", side_effect=fake_run),
            patch("sys.stdout", new=io.StringIO()),
        ):
            apk = Path(folder) / "app.apk"
            apk.write_bytes(b"trusted package")
            with self.assertRaisesRegex(app.SetupError, "verification failed"):
                app.launch("adb", "streamer", apk)
        self.assertFalse(any("app_process" in command[-1] for command in commands))

    def test_device_states_and_model(self):
        found = app.parse_devices(
            "List of devices attached\nserial1 unauthorized usb:1\nserial2 device product:kirkwood model:Google_TV_Streamer\nserial3 offline\n"
        )
        self.assertEqual(
            found,
            [
                ("serial1", "unauthorized", "serial1"),
                ("serial2", "device", "Google TV Streamer"),
                ("serial3", "offline", "serial3"),
            ],
        )

    def test_selection_never_picks_an_unauthorized_device(self):
        with (
            patch.object(
                app, "run", return_value="serial1 unauthorized\nserial2 device model:TV"
            ),
            patch("sys.stdout", new=io.StringIO()),
        ):
            self.assertEqual(app.select_device("adb", None, False), "serial2")

    def test_ambiguous_selection_needs_explicit_target(self):
        with (
            patch.object(app, "run", return_value="one device\ntwo device"),
            patch("sys.stdout", new=io.StringIO()),
        ):
            with self.assertRaisesRegex(app.SetupError, "--serial"):
                app.select_device("adb", None, False)

    def test_shell_bootstrap_quotes_package_path(self):
        path = "/data/app/unusual ' $(touch no)/base.apk"
        command = app.helper_command(path, "0")
        tokens = shlex.split(command)
        self.assertEqual(tokens[0], "CLASSPATH=" + path)
        self.assertEqual(tokens[-1], "&")
        with self.assertRaises(app.SetupError):
            app.helper_command(path, "0; reboot")

    def test_endpoint_validation(self):
        self.assertEqual(app.endpoint("192.168.1.2:39123"), "192.168.1.2:39123")
        self.assertEqual(app.endpoint("[fe80::12]:5555"), "[fe80::12]:5555")
        for value in [
            "192.168.1.2",
            "--help",
            "host:99999",
            "host:0",
            "host:5555; reboot",
        ]:
            with self.assertRaises(app.SetupError):
                app.endpoint(value)

    def test_running_helper_is_reused_without_reinstall(self):
        commands = []

        def fake_run(command, **kwargs):
            commands.append(command)
            operation = command[-1]
            return {
                "getprop ro.build.version.sdk": "34",
                "pm has-feature android.software.leanback": "true",
                "am get-current-user": "0",
                "pm path --user 0 " + app.PACKAGE: "package:/data/app/test/base.apk",
                "sha256sum /data/app/test/base.apk": hashlib.sha256(
                    b"test apk"
                ).hexdigest()
                + "  /data/app/test/base.apk",
            }.get(operation, "Bundle[{ready=true, protocol=1}]")

        with (
            tempfile.TemporaryDirectory() as folder,
            patch.object(app, "run", side_effect=fake_run),
            patch("sys.stdout", new=io.StringIO()),
        ):
            apk = Path(folder) / "app.apk"
            apk.write_bytes(b"test apk")
            app.launch("adb", "streamer", apk)
        self.assertFalse(any("install" in command for command in commands))
        self.assertFalse(any("nohup" in command[-1] for command in commands))

    def test_unsupported_android_stops_before_installing(self):
        with patch.object(app, "run", return_value="33") as run:
            with self.assertRaisesRegex(app.SetupError, "Android TV 14"):
                app.launch("adb", "streamer", Path("app.apk"))
            self.assertEqual(run.call_count, 1)


if __name__ == "__main__":
    unittest.main()
