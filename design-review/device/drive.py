"""Emulator driver for the 0.1.12 design review: seeds the same data into a build, then captures one tour.

usage: drive.py tour OUTDIR THEME   (THEME = dark | light)
       drive.py body OUTDIR THEME   (weigh-in, measuring and History › Body on a real window)
       drive.py seed DBFILE
Taps target visible text or content descriptions from the uiautomator hierarchy; shots save PNG + XML.
"""
import os, pathlib, re, subprocess, sys, time
import xml.etree.ElementTree as ET

ADB = os.path.expanduser("~/Android/Sdk/platform-tools/adb")
SERIAL = os.environ.get("ANDROID_SERIAL", "emulator-5610")
PKG = "com.rebuild"

def adb(*args, **kw):
    return subprocess.check_output([ADB, "-s", SERIAL, *args], **kw)

def tree():
    for _ in range(5):
        try:
            adb("shell", "rm", "-f", "/sdcard/rb.xml")
            adb("shell", "uiautomator", "dump", "/sdcard/rb.xml")
            xml = adb("exec-out", "cat", "/sdcard/rb.xml")
            ET.fromstring(xml)
            return xml
        except (subprocess.CalledProcessError, ET.ParseError):
            time.sleep(0.8)
    raise RuntimeError("hierarchy dump failed")

def find(pattern):
    rx = re.compile(pattern)
    for n in ET.fromstring(tree()).iter("node"):
        label = n.attrib.get("text") or n.attrib.get("content-desc") or ""
        if rx.fullmatch(label):
            x1, y1, x2, y2 = map(int, re.findall(r"\d+", n.attrib["bounds"]))
            if x2 > x1 and y2 > y1:
                return (x1 + x2) // 2, (y1 + y2) // 2
    return None

def tap(pattern, required=True, scrolls=0):
    at = find(pattern)
    for _ in range(scrolls):
        if at is not None:
            break
        adb("shell", "input", "swipe", "540", "1700", "540", "700", "300")
        time.sleep(0.8)
        at = find(pattern)
    if at is None:
        if required:
            raise RuntimeError(f"nothing matches {pattern!r}")
        return False
    adb("shell", "input", "tap", str(at[0]), str(at[1]))
    time.sleep(1.2)
    return True

def shot(out, name):
    time.sleep(1.0)
    path = pathlib.Path(out) / name
    path.parent.mkdir(parents=True, exist_ok=True)
    path.with_suffix(".xml").write_bytes(tree())
    path.with_suffix(".png").write_bytes(adb("exec-out", "screencap", "-p"))
    print("shot", path.with_suffix(".png"))

def back():
    adb("shell", "input", "keyevent", "4")
    time.sleep(1.0)

def launch():
    adb("shell", "am", "force-stop", PKG)
    adb("shell", "am", "start", "-W", "-n", f"{PKG}/.MainActivity")
    time.sleep(2.5)

def seed(db):
    adb("shell", "am", "force-stop", PKG)
    adb("shell", "run-as", PKG, "mkdir", "-p", "databases")
    adb("shell", "run-as", PKG, "rm", "-f", "databases/rebuild.db-journal", "databases/rebuild.db-wal",
        "databases/rebuild.db-shm")
    with open(db, "rb") as f:
        subprocess.run([ADB, "-s", SERIAL, "shell", "run-as", PKG, "sh", "-c", "'cat > databases/rebuild.db'"],
                       stdin=f, check=True)

def tour(out, theme):
    adb("shell", "cmd", "uimode", "night", "yes" if theme == "dark" else "no")
    launch()
    p = f"{theme}-"
    shot(out, p + "01-today")
    tap("History"); shot(out, p + "04-history")
    # The newest workout in the list.
    rows = [n for n in ET.fromstring(tree()).iter("node")
            if re.search(r"working set", n.attrib.get("text", "") + n.attrib.get("content-desc", ""))]
    if rows:
        x1, y1, x2, y2 = map(int, re.findall(r"\d+", rows[0].attrib["bounds"]))
        adb("shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2)); time.sleep(1.2)
        shot(out, p + "05-history-detail"); back()
    tap("Plan"); shot(out, p + "06-plan")
    tap("More"); shot(out, p + "07-more")
    tap("Today")
    if tap(r"Start [A-Z].*", required=False):
        time.sleep(1.5)
        shot(out, p + "03-workout")
        if tap(r"Log set", required=False):
            time.sleep(1.5)
            shot(out, p + "03b-workout-logged-rest")
        back()
        shot(out, p + "02-today-active")

def typed(text):
    adb("shell", "input", "text", text.replace(" ", "%s"))
    time.sleep(0.8)

def body(out, theme):
    adb("shell", "cmd", "uimode", "night", "yes" if theme == "dark" else "no")
    launch()
    p = f"{theme}-body-"
    tap("Log", scrolls=5)
    typed("81.4")
    shot(out, p + "01-log-weight-keyboard")
    tap("lb"); shot(out, p + "02-log-weight-lb")
    tap("kg"); tap("Save"); time.sleep(1.0)
    shot(out, p + "03-today-logged")
    tap("Measure this week", scrolls=5); tap("Waist.*"); typed("88.5")
    shot(out, p + "04-measure-keyboard")
    back(); tap("Save measurements"); time.sleep(1.0)
    tap("History"); tap("Body"); shot(out, p + "05-history-body")
    tap("Workouts"); shot(out, p + "06-history-calendar")

if __name__ == "__main__":
    cmd = sys.argv[1]
    if cmd == "seed":
        seed(sys.argv[2])
    elif cmd == "tour":
        tour(sys.argv[2], sys.argv[3])
    elif cmd == "body":
        body(sys.argv[2], sys.argv[3])
