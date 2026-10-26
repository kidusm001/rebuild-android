import argparse
import datetime
import json
import os
import pathlib
import re
import subprocess
import time
import xml.etree.ElementTree as ET

ROOT = pathlib.Path(__file__).parent
ADB = "/opt/android-sdk/platform-tools/adb"
SERIAL = os.environ.get("ANDROID_SERIAL", "emulator-5592")


def adb(*args):
    return subprocess.check_output([ADB, "-s", SERIAL, *args])


def log(action, **values):
    with (ROOT / "interactions.jsonl").open("a") as stream:
        stream.write(json.dumps({"at": datetime.datetime.now(datetime.timezone.utc).isoformat(),
                                 "action": action, **values}) + "\n")


def tree():
    for attempt in range(3):
        try:
            adb("shell", "rm", "-f", "/sdcard/rebuild-ux-audit.xml")
            adb("shell", "uiautomator", "dump", "/sdcard/rebuild-ux-audit.xml")
            xml = adb("exec-out", "cat", "/sdcard/rebuild-ux-audit.xml")
            ET.fromstring(xml)
            return xml
        except (subprocess.CalledProcessError, ET.ParseError) as error:
            log("hierarchy-retry", attempt=attempt + 1, error=str(error))
            if attempt == 2:
                raise
            time.sleep(0.5)


def nodes():
    return list(ET.fromstring(tree()).iter("node"))


def bounds(node):
    return list(map(int, re.findall(r"\d+", node.attrib["bounds"])))


def touch(node):
    x1, y1, x2, y2 = bounds(node)
    adb("shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))


parser = argparse.ArgumentParser()
parser.add_argument("action", choices=["capture", "tap", "point", "field", "swipe", "key", "mark"])
parser.add_argument("values", nargs="*")
args = parser.parse_args()
v = args.values
if args.action == "capture":
    name = v[0]
    xml = tree()
    (ROOT / f"{name}.xml").write_bytes(xml)
    (ROOT / f"{name}.png").write_bytes(adb("exec-out", "screencap", "-p"))
    density_text = adb("shell", "wm", "density").decode()
    density = int(re.findall(r"(?:Physical|Override) density: (\d+)", density_text)[-1])
    info = {"name": name, "density": density, "size": adb("shell", "wm", "size").decode().strip(),
            "fontScale": adb("shell", "settings", "get", "system", "font_scale").decode().strip(),
            "night": adb("shell", "cmd", "uimode", "night").decode().strip(), "nodes": []}
    for n in ET.fromstring(xml).iter("node"):
        a = n.attrib
        x1, y1, x2, y2 = bounds(n)
        label = a.get("text") or a.get("content-desc")
        record = {**a, "widthDp": round((x2-x1)*160/density, 2),
                  "heightDp": round((y2-y1)*160/density, 2)}
        info["nodes"].append(record)
        if label or a.get("class") == "android.widget.EditText":
            print(label or "<empty field>", a.get("class"), a.get("bounds"),
                  "click=" + a.get("clickable", ""), "enabled=" + a.get("enabled", ""))
    (ROOT / f"{name}.json").write_text(json.dumps(info, indent=2) + "\n")
    (ROOT / f"{name}-ime.txt").write_bytes(adb("shell", "dumpsys", "input_method"))
    log("capture", name=name)
elif args.action == "tap":
    matches = [n for n in nodes() if v[0] in (n.attrib.get("text"), n.attrib.get("content-desc"))]
    index = int(v[1]) if len(v) > 1 else 0
    if len(matches) <= index:
        raise RuntimeError(f"No match {v[0]!r} index {index}")
    node = matches[index]
    log("tap", label=v[0], index=index, bounds=node.attrib["bounds"])
    touch(node)
elif args.action == "field":
    fields = [n for n in nodes() if n.attrib.get("class") == "android.widget.EditText"]
    n = fields[int(v[0])]
    touch(n)
    adb("shell", "input", "keyevent", "123")
    text = n.attrib.get("text", "")
    if text:
        adb("shell", "input", "keyevent", *(["67"] * len(text)))
    if v[1]:
        adb("shell", "input", "text", v[1].replace(" ", "%s"))
    log("field", index=int(v[0]), previous=text, value=v[1], bounds=n.attrib["bounds"])
elif args.action == "swipe":
    adb("shell", "input", "swipe", *v)
    log("swipe", values=v)
elif args.action == "point":
    adb("shell", "input", "tap", *v[:2])
    log("point", x=v[0], y=v[1], label=" ".join(v[2:]))
elif args.action == "key":
    adb("shell", "input", "keyevent", *v)
    log("key", values=v)
else:
    log("mark", label=" ".join(v))
