#!/usr/bin/env bash
# tracemoments.sh <label> <outdir>: in a fresh process, traces of the first push, first log, first Target done and
# first Next (<outdir>/<label>-<moment>.txt). Count GPU program compiles with shadercompiles.py.
L=$1; O=$2; mkdir -p "$O"; T=$(cd "$(dirname "$0")" && pwd)
U="$(git -C "$T" rev-parse --show-toplevel)/.claude/skills/verify-rebuild/scripts/ui.sh"
fill(){ for field in "entry-kg:40" "entry-reps:10"; do $U tap "${field%%:*}" >/dev/null; adb shell input keyevent KEYCODE_MOVE_END
    for _ in 1 2 3 4 5 6; do adb shell input keyevent KEYCODE_DEL; done; $U type "${field#*:}" >/dev/null; done
  $U back >/dev/null; sleep 1.5; }
skip(){ $U has "Skip rest" && { $U tap "Skip rest" >/dev/null; sleep 1.5; }; true; }
"$T/prep-fresh.sh"
"$T/tracetap.sh" "$O/$L-push1.txt" "${START:-start-scheduled}" 5; $U wait log-set 10 >/dev/null; sleep 1
fill; "$T/tracetap.sh" "$O/$L-log1.txt" log-set 4; skip
fill; "$T/tracetap.sh" "$O/$L-log2.txt" log-set 4; skip
"$T/tracetap.sh" "$O/$L-next1.txt" next-exercise 4; sleep 1
$U tap "More workout actions" >/dev/null; sleep 1; $U tap "Discard workout" >/dev/null; sleep 1.2; $U tap "Discard" >/dev/null; sleep 2
"$T/shadercompiles.py" "$O/$L"-*.txt
