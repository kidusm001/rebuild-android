#!/usr/bin/env bash
# drive-bars-pop.sh <outdir> <name>: on the workout, log a set so a rest runs, then Back to Today (the rest bar moves up
# by the navigation bar's height), resume (it moves back down), and skip the rest. Marks device ms per step.
out=$1; marks=$out/$2.marks; : > "$marks"; T=$(cd "$(dirname "$0")" && pwd)
U="$(git -C "$T" rev-parse --show-toplevel)/.claude/skills/verify-rebuild/scripts/ui.sh"
mark(){ echo "$1 $(adb shell date +%s%3N)" >> "$marks"; }
tapm(){ local p; p=$("$T/xy.sh" "$2"); [ -n "$p" ] || { echo "missing $2"; return 1; }; mark "$1"; adb shell input tap $p; }
tapm log log-set; sleep 3
mark backkey; adb shell input keyevent KEYCODE_BACK; sleep 3
tapm resume "Resume workout"; $U wait log-set 10 >/dev/null; sleep 3
$U has "Skip rest" && $U tap "Skip rest" >/dev/null; sleep 1
