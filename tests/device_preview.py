#!/usr/bin/env python3
"""Opt-in TV check. Start the calibrator first; this tests preview, restores it, then selects Done."""

import argparse
import re
import subprocess
import time
import xml.etree.ElementTree as ET

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--serial", required=True)
args = parser.parse_args()
base = ["adb", "-s", args.serial, "shell"]


def shell(command):
    return subprocess.check_output(base + [command], text=True, timeout=30)


def nodes():
    shell("uiautomator dump /data/local/tmp/projector-calibrator-test-ui.xml")
    xml = shell("cat /data/local/tmp/projector-calibrator-test-ui.xml")
    return list(ET.fromstring(xml).iter("node"))


def click(label):
    matches = [n for n in nodes() if n.get("text") == label]
    assert len(matches) == 1, "Missing unique button: " + label
    bounds = list(map(int, re.findall(r"\d+", matches[0].get("bounds"))))
    shell(
        "input tap %d %d" % ((bounds[0] + bounds[2]) // 2, (bounds[1] + bounds[3]) // 2)
    )
    time.sleep(0.3)


def projection():
    dump = shell("dumpsys SurfaceFlinger")
    match = re.search(
        r"orientedDisplaySpace=ProjectionSpace\{bounds=Rect\(0, 0, (\d+), (\d+)\), content=Rect\((\d+), (\d+), (\d+), (\d+)\)",
        dump,
    )
    assert match, "Physical projection not found"
    return tuple(map(int, match.groups()))


before = projection()
print("Calibrated output rectangle:", before[2:], flush=True)
labels = [n.get("text") for n in nodes()]
assert "Android TV Overscan" in labels, "Open the calibrator before running this test"
assert "Restore" not in labels, "Start with correction enabled"
try:
    click("Show original" if "Show original" in labels else "Original")
    actual = projection()
    labels = [n.get("text") for n in nodes()]
    size = next(label for label in labels if label.startswith("Output "))
    print("Original output rectangle:", actual[2:])
    print("UI:", size)
    assert actual == (before[0], before[1], 0, 0, before[0], before[1]), (
        "Original did not remove the physical correction"
    )
    assert "Picture %d × %d" % before[:2] in size, (
        "Preview still displays the corrected picture dimensions"
    )
    edges = [
        n for n in nodes() if re.match(r"^(Left|Right|Top|Bottom)\s", n.get("text", ""))
    ]
    assert len(edges) == 4 and all(
        re.search(r"‹\s+0 px", n.get("text")) for n in edges
    ), "Preview margins must show zero"
    assert all(n.get("enabled") == "false" for n in edges), (
        "Preview margins must not edit the saved calibration"
    )
finally:
    if any(n.get("text") == "Restore" for n in nodes()):
        click("Restore")
    assert projection() == before, "The calibrated rectangle was not restored"
print("PASS: original preview, displayed dimensions, and restored calibration")
click("Done")
deadline = time.monotonic() + 5
while True:
    resumed = [
        line
        for line in shell("dumpsys activity activities").splitlines()
        if "ResumedActivity" in line
    ]
    if resumed and all(
        "com.skipstery.projectorcalibrator" not in line for line in resumed
    ):
        break
    assert time.monotonic() < deadline, "Done did not close calibration"
    time.sleep(0.3)
deadline = time.monotonic() + 5
while True:
    actual = projection()
    if actual == before:
        break
    assert time.monotonic() < deadline, (
        "Done changed the calibrated rectangle: %r -> %r" % (before, actual)
    )
    time.sleep(0.3)
print("PASS: Done closes calibration and keeps the calibrated output")
