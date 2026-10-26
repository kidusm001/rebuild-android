"""Tab-switch frame benchmark for Rebuild on an attached emulator or phone.

usage: tabswitch.py LABEL [ROUNDS]
Taps Today → History → Plan → More → Today ROUNDS times (default 8) with 900 ms between taps, then reads
`dumpsys gfxinfo` for the app: frames, janky frames and the frame-time percentiles. Run it on a freshly launched app
so the numbers cover tab switches only.
"""
import os, re, subprocess, sys, time
import xml.etree.ElementTree as ET

ADB = os.path.expanduser("~/Android/Sdk/platform-tools/adb")
SERIAL = os.environ.get("ANDROID_SERIAL", "emulator-5610")
PKG = "com.rebuild"

def adb(*a):
    return subprocess.check_output([ADB, "-s", SERIAL, *a]).decode(errors="replace")

def tabs():
    adb("shell", "uiautomator", "dump", "/sdcard/t.xml")
    root = ET.fromstring(adb("exec-out", "cat", "/sdcard/t.xml"))
    at = {}
    for n in root.iter("node"):
        label = n.attrib.get("text") or n.attrib.get("content-desc") or ""
        if label in ("Today", "History", "Plan", "More") and label not in at:
            x1, y1, x2, y2 = map(int, re.findall(r"\d+", n.attrib["bounds"]))
            at[label] = ((x1 + x2) // 2, (y1 + y2) // 2)
    return at

def main():
    label = sys.argv[1]
    rounds = int(sys.argv[2]) if len(sys.argv) > 2 else 8
    adb("shell", "am", "force-stop", PKG)
    adb("shell", "am", "start", "-W", "-n", f"{PKG}/.MainActivity")
    time.sleep(3)
    at = tabs()
    # One warm-up round so first-use class loading is measured separately from steady switching.
    def round_trip():
        for t in ("History", "Plan", "More", "Today"):
            adb("shell", "input", "tap", *map(str, at[t]))
            time.sleep(0.9)
    adb("shell", "dumpsys", "gfxinfo", PKG, "reset")
    round_trip()
    first = adb("shell", "dumpsys", "gfxinfo", PKG)
    adb("shell", "dumpsys", "gfxinfo", PKG, "reset")
    for _ in range(rounds):
        round_trip()
    steady = adb("shell", "dumpsys", "gfxinfo", PKG)
    keys = ["Total frames rendered", "Janky frames", "50th percentile", "90th percentile", "95th percentile",
            "99th percentile", "Number Slow UI thread", "Number Frame deadline missed"]
    for name, out in (("first round", first), (f"steady ({rounds} rounds)", steady)):
        print(f"[{label}] {name}")
        for k in keys:
            m = re.search(rf"^{re.escape(k)}: (.*)$", out, re.M)
            if m:
                print(f"  {k}: {m.group(1)}")

if __name__ == "__main__":
    main()
