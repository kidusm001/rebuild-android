"""Main-thread cost of switching tabs, per APK, from a Perfetto scheduler trace.

usage: measure.py LABEL APK [ROUNDS]      (FIRST=1 measures the first visit to each tab after launch, ROUNDS=1)
Installs APK over the current app (data kept), launches it once so profileinstaller can write its profile, compiles
the app the way the phone's idle maintenance would (speed-profile), then records 4 rounds of Today → History → Plan →
More → Today. For each tap it reports how long the app's main thread actually ran on a CPU in the next 900 ms: the
work a tab switch costs, apart from the emulator's software GPU. Needs the `perfetto` Python package.
"""
import os, re, statistics, subprocess, sys, time
import xml.etree.ElementTree as ET
from perfetto.trace_processor import TraceProcessor

ADB = os.path.expanduser("~/Android/Sdk/platform-tools/adb")
SERIAL = os.environ.get("ANDROID_SERIAL", "emulator-5610")
PKG = "com.rebuild"
ORDER = ("History", "Plan", "More", "Today")

def adb(*a):
    return subprocess.run([ADB, "-s", SERIAL, *a], capture_output=True, text=True).stdout

def tabs():
    adb("shell", "uiautomator", "dump", "/sdcard/t.xml")
    root = ET.fromstring(adb("exec-out", "cat", "/sdcard/t.xml"))
    at = {}
    for n in root.iter("node"):
        label = n.attrib.get("text") or n.attrib.get("content-desc") or ""
        if label in ORDER and label not in at:
            x1, y1, x2, y2 = map(int, re.findall(r"\d+", n.attrib["bounds"]))
            at[label] = ((x1 + x2) // 2, (y1 + y2) // 2)
    return at

def main():
    label, apk = sys.argv[1], sys.argv[2]
    rounds = int(sys.argv[3]) if len(sys.argv) > 3 else 4
    adb("install", "-r", apk)
    adb("shell", "am", "start", "-W", "-n", f"{PKG}/.MainActivity"); time.sleep(6)
    adb("shell", "cmd", "package", "compile", "-m", "speed-profile", "-f", PKG)
    adb("shell", "am", "force-stop", PKG)
    adb("shell", "am", "start", "-W", "-n", f"{PKG}/.MainActivity"); time.sleep(3)
    at = tabs()
    # One untimed round, unless FIRST=1: then the timed round is the first visit to each tab after a fresh launch.
    if os.environ.get("FIRST") != "1":
        for t in ORDER:
            adb("shell", "input", "tap", *map(str, at[t])); time.sleep(1.0)
    trace = f"/data/misc/perfetto-traces/{label}.pftrace"
    subprocess.Popen([ADB, "-s", SERIAL, "shell", "perfetto", "-o", trace, "-t", f"{rounds * 4 * 1.3 + 3:.0f}s",
                      "-b", "64mb", "sched"], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    time.sleep(2)
    # Taps run on the device, each stamped with CLOCK_BOOTTIME (the clock Perfetto timestamps use).
    script = "; ".join(f"cat /proc/uptime; input tap {at[t][0]} {at[t][1]}; sleep 1.3" for _ in range(rounds) for t in ORDER)
    out = adb("shell", script)
    stamps = [float(line.split()[0]) for line in out.splitlines() if line.strip()]
    taps = [t for _ in range(rounds) for t in ORDER]
    time.sleep(3)
    local = f"/tmp/{label}.pftrace"
    adb("pull", trace, local)
    tp = TraceProcessor(trace=local)
    pid = int(adb("shell", "pidof", PKG).split()[0])
    utid = list(tp.query(f"select utid from thread where tid={pid}"))[0].utid
    runs = list(tp.query(f"select ts, dur from thread_state where utid={utid} and state='Running' order by ts"))
    starts = [x * 1e9 for x in stamps]
    per = {t: [] for t in ORDER}
    for t, s in zip(taps, starts):
        busy = sum(min(r.ts + r.dur, s + 900e6) - max(r.ts, s) for r in runs if r.ts < s + 900e6 and r.ts + r.dur > s)
        per[t].append(busy / 1e6)
    print(f"[{label}] main-thread CPU per tab switch (ms, median of {rounds}):")
    for t in ORDER:
        print(f"  → {t:8s} {statistics.median(per[t]):7.1f}   all: {[round(x) for x in per[t]]}")
    allv = [x for v in per.values() for x in v]
    print(f"  overall median {statistics.median(allv):.1f} ms, mean {statistics.mean(allv):.1f} ms")

if __name__ == "__main__":
    main()
