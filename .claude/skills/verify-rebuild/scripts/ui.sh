#!/usr/bin/env bash
# Drive the Rebuild app like a finger would, by stable handles.
#   ui.sh tap <handle>             tap the first node matching the handle
#   ui.sh hold <handle> [ms]       press and hold in place (default 900 ms), then let go
#   ui.sh drag <handle> <dx> [dy]  long-press, then drag by dx/dy pixels and drop
#   ui.sh wait <handle> [seconds]  wait until the handle is on screen (default 10 s, plus at most one screen read)
#   ui.sh gone <handle> [seconds]  wait until the handle is no longer on screen
#   ui.sh has <handle>             exit 0 if on screen now
#   ui.sh type <text>              type into the focused field (spaces become %s)
#   ui.sh back | home              system Back / Home
#   ui.sh list                     every visible handle: resource id, text, content description, bounds
# A handle is matched, in order, against: "id=text" (resource id and exact text together), the Compose test tag
# (exposed as resource id), the exact text, the content description, then a substring of either. Test tags are the
# stable choice; prefer them. Only controls actually on screen count.
set -euo pipefail
adb="${ANDROID_HOME:-/opt/android-sdk}/platform-tools/adb"

# A fresh read of the screen, or failure: the old file is removed first so a failed dump can never be mistaken for
# the current screen, and a dump that didn't report success stops the caller.
# uiautomator refuses to read a screen that is still animating ("could not get idle state"), so a failed read is
# retried a few times before it counts as a failure.
dump() {
  local attempt
  for attempt in 1 2 3; do
    "$adb" shell rm -f /sdcard/rv.xml >/dev/null 2>&1 || return 1
    if "$adb" shell uiautomator dump /sdcard/rv.xml 2>&1 | grep -q "dumped to"; then
      "$adb" exec-out cat /sdcard/rv.xml; return 0
    fi
    sleep 0.7
  done
  return 1
}

# Prints "x y" (the centre of the first match) or nothing.
# Exits 0 with "x y" when found, 0 with nothing when not found, and 2 when the screen couldn't be read.
find_xy() {
  local xml
  xml=$(dump) || { echo "could not read the screen (device gone or busy?): run emu.sh doctor" >&2; return 2; }
  printf '%s' "$xml" | python3 -c '
import re, sys
q = sys.argv[1]
nodes = []
for m in re.finditer(r"<node [^>]*>", sys.stdin.read()):
    n = m.group(0)
    get = lambda k: (re.search(k + r"=\"([^\"]*)\"", n) or [None, ""])[1]
    rid, text, desc = get("resource-id"), get("text"), get("content-desc")
    # Keep the sign: items of a closed drawer sit at negative x, off screen, and must not count as visible.
    b = list(map(int, re.findall(r"-?\d+", get("bounds"))))
    if len(b) == 4 and b[2] > b[0] and b[3] > b[1] and b[2] > 0 and b[3] > 0 and b[0] >= 0 and b[1] >= 0:
        nodes.append((rid, text, desc, b))
# "id=text" pins both, for when the same text appears twice on screen (a toolbar title and a drawer item).
rid_q, _, text_q = q.partition("=") if "=" in q else ("", "", "")
for test in (lambda r, t, d: bool(rid_q) and r.split("/")[-1] == rid_q and t == text_q,
             lambda r, t, d: r == q or r.endswith(":id/" + q), lambda r, t, d: t == q,
             lambda r, t, d: d == q, lambda r, t, d: q in t or q in d):
    for r, t, d, b in nodes:
        if test(r, t, d):
            print((b[0] + b[2]) // 2, (b[1] + b[3]) // 2); sys.exit()
' "$1"
}

# Sets x and y to the handle's centre, or stops the script: a missing handle must never become a tap at nowhere.
locate() { local xy; xy=$(find_xy "$1") || exit 2; [ -n "$xy" ] || { echo "not on screen: $1" >&2; exit 1; }; read -r x y <<<"$xy"; }

# Polls until the handle's presence matches what's wanted, against a real deadline (a screen read takes about a
# second, so counting attempts would overrun). A failed screen read stops the wait instead of counting as an answer.
poll() {
  local want=$1 handle=$2 deadline=$(( $(date +%s) + $3 )) xy
  while :; do
    xy=$(find_xy "$handle") || exit 2
    if [ "$want" = present ] && [ -n "$xy" ]; then return 0; fi
    if [ "$want" = absent ] && [ -z "$xy" ]; then return 0; fi
    [ "$(date +%s)" -ge "$deadline" ] && return 1
    sleep 0.3
  done
}

case ${1:-} in
  tap)  locate "$2"; "$adb" shell input tap "$x" "$y" ;;
  hold) locate "$2"; ms=${3:-900}
        "$adb" shell input motionevent DOWN "$x" "$y"; sleep "$(awk -v ms="$ms" 'BEGIN { print ms / 1000 }')"; "$adb" shell input motionevent UP "$x" "$y" ;;
  drag) locate "$2"; dx=$3; dy=${4:-0}
        "$adb" shell input motionevent DOWN "$x" "$y"; sleep 0.8
        for i in 1 2 3 4 5 6 7 8 9 10; do "$adb" shell input motionevent MOVE $((x + dx * i / 10)) $((y + dy * i / 10)); sleep 0.04; done
        sleep 0.3; "$adb" shell input motionevent UP $((x + dx)) $((y + dy)) ;;
  wait) poll present "$2" "${3:-10}" || { echo "timed out waiting for: $2" >&2; exit 1; } ;;
  gone) poll absent "$2" "${3:-10}" || { echo "still on screen: $2" >&2; exit 1; } ;;
  has)  xy=$(find_xy "$2") || exit 2; [ -n "$xy" ] ;;
  type) "$adb" shell input text "${2// /%s}" ;;
  back) "$adb" shell input keyevent BACK ;;
  home) "$adb" shell input keyevent HOME ;;
  list) { dump || { echo "could not read the screen" >&2; exit 2; }; } | python3 -c '
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
