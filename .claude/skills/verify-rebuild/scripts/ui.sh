#!/usr/bin/env bash
# Drive the Rebuild app like a finger would, by stable handles.
#   ui.sh tap <handle>             tap the first node matching the handle
#   ui.sh hold <handle> [ms]       press and hold in place (default 900 ms), then let go
#   ui.sh drag <handle> <dx> [dy]  long-press, then drag by dx/dy pixels and drop
#   ui.sh wait <handle> [seconds]  wait until the handle is on screen (default 10 s)
#   ui.sh gone <handle> [seconds]  wait until the handle is no longer on screen
#   ui.sh has <handle>             exit 0 if on screen now
#   ui.sh type <text>              type into the focused field (spaces become %s)
#   ui.sh back | home              system Back / Home
#   ui.sh list                     every visible handle: resource id, text, content description, bounds
# A handle is matched, in order, against: the Compose test tag (exposed as resource id), the exact text, the
# content description, then a substring of either. Test tags are the stable choice; prefer them.
set -euo pipefail
adb="${ANDROID_HOME:-/opt/android-sdk}/platform-tools/adb"

dump() { "$adb" shell uiautomator dump /sdcard/rv.xml >/dev/null 2>&1; "$adb" exec-out cat /sdcard/rv.xml; }

# Prints "x y" (the centre of the first match) or nothing.
find_xy() {
  dump | python3 -c '
import re, sys
q = sys.argv[1]
nodes = []
for m in re.finditer(r"<node [^>]*>", sys.stdin.read()):
    n = m.group(0)
    get = lambda k: (re.search(k + r"=\"([^\"]*)\"", n) or [None, ""])[1]
    rid, text, desc = get("resource-id"), get("text"), get("content-desc")
    b = list(map(int, re.findall(r"\d+", get("bounds"))))
    if len(b) == 4 and b[2] > b[0] and b[3] > b[1]:
        nodes.append((rid, text, desc, b))
for test in (lambda r, t, d: r == q or r.endswith(":id/" + q), lambda r, t, d: t == q,
             lambda r, t, d: d == q, lambda r, t, d: q in t or q in d):
    for r, t, d, b in nodes:
        if test(r, t, d):
            print((b[0] + b[2]) // 2, (b[1] + b[3]) // 2); sys.exit()
' "$1"
}

# Sets x and y to the handle's centre, or stops the script: a missing handle must never become a tap at nowhere.
locate() { local xy; xy=$(find_xy "$1"); [ -n "$xy" ] || { echo "not on screen: $1" >&2; exit 1; }; read -r x y <<<"$xy"; }

case ${1:-} in
  tap)  locate "$2"; "$adb" shell input tap "$x" "$y" ;;
  hold) locate "$2"; ms=${3:-900}
        "$adb" shell input motionevent DOWN "$x" "$y"; sleep "$(awk -v ms="$ms" 'BEGIN { print ms / 1000 }')"; "$adb" shell input motionevent UP "$x" "$y" ;;
  drag) locate "$2"; dx=$3; dy=${4:-0}
        "$adb" shell input motionevent DOWN "$x" "$y"; sleep 0.8
        for i in 1 2 3 4 5 6 7 8 9 10; do "$adb" shell input motionevent MOVE $((x + dx * i / 10)) $((y + dy * i / 10)); sleep 0.04; done
        sleep 0.3; "$adb" shell input motionevent UP $((x + dx)) $((y + dy)) ;;
  wait) for _ in $(seq 1 $(( ${3:-10} * 2 ))); do [ -n "$(find_xy "$2")" ] && exit 0; sleep 0.5; done; echo "timed out waiting for: $2" >&2; exit 1 ;;
  gone) for _ in $(seq 1 $(( ${3:-10} * 2 ))); do [ -z "$(find_xy "$2")" ] && exit 0; sleep 0.5; done; echo "still on screen: $2" >&2; exit 1 ;;
  has)  [ -n "$(find_xy "$2")" ] ;;
  type) "$adb" shell input text "${2// /%s}" ;;
  back) "$adb" shell input keyevent BACK ;;
  home) "$adb" shell input keyevent HOME ;;
  list) dump | python3 -c '
import re, sys
for m in re.finditer(r"<node [^>]*>", sys.stdin.read()):
    n = m.group(0)
    get = lambda k: (re.search(k + r"=\"([^\"]*)\"", n) or [None, ""])[1]
    rid, text, desc = get("resource-id").split("/")[-1], get("text"), get("content-desc")
    bounds = get("bounds")
    if rid or text or desc: print(f"{rid:28.28} | {text[:40]:40} | {desc[:60]:60} | {bounds}")
' ;;
  *) sed -n '2,13p' "$0"; exit 2 ;;
esac
