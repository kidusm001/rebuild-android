#!/usr/bin/env bash
# drive-r2.sh <outdir> <name>: the moments of build round 2, with device-ms marks per step (<name>.marks).
# From Today with no workout active and no rest running: Start (START, default start-scheduled), Back key to Today
# (the hero card on the pop), resume, a slow back swipe held and let go on the workout (commit), resume, log a set,
# skip its rest, Finish -> receipt -> Done.
out=$1; marks=$out/$2.marks; : > "$marks"; T=$(cd "$(dirname "$0")" && pwd)
U="$(git -C "$T" rev-parse --show-toplevel)/.claude/skills/verify-rebuild/scripts/ui.sh"
mark(){ echo "$1 $(adb shell date +%s%3N)" >> "$marks"; }
tapm(){ local p; p=$("$T/xy.sh" "$2"); [ -n "$p" ] || { echo "missing $2"; return 1; }; mark "$1"; adb shell input tap $p; }
tapm start "${START:-start-scheduled}"; $U wait log-set 10 >/dev/null; sleep 2
mark backkey; adb shell input keyevent KEYCODE_BACK; sleep 3
tapm resume1 "Resume workout"; $U wait log-set 10 >/dev/null; sleep 2
mark backswipe; adb shell input swipe 3 1300 700 1300 1800; sleep 4
tapm resume2 "Resume workout"; $U wait log-set 10 >/dev/null; sleep 2
tapm log log-set; sleep 3; $U has "Skip rest" && { $U tap "Skip rest" >/dev/null; sleep 2; }
tapm finish "Finish"; sleep 2
tapm confirm "Finish"; sleep 4
tapm done "Done"; sleep 2
