#!/usr/bin/env bash
# perfrun.sh <label> <apk|-> <outdir> <release|debug>: optionally install <apk> (release: one launch, then compile
# speed-profile), then in a fresh process: Start (push1), log1, log2 (first Target done), Next (next1), log3, log4,
# Next (next2), discard, Start (push2). One `gfxinfo framestats` dump per moment: <outdir>/<label>-<moment>.txt.
# START names the Start control (default: today's scheduled one, start-scheduled; on a rest day e.g.
# START="Start Lower A (Thursday)"). Pass "-" as the apk for another fresh process on what is installed. Summarise with framestats_cmp.py <outdir>.
set -u
L=$1; APK=$2; O=$3; KIND=$4; mkdir -p "$O"
T=$(cd "$(dirname "$0")" && pwd); U="$(git -C "$T" rev-parse --show-toplevel)/.claude/skills/verify-rebuild/scripts/ui.sh"
rs(){ adb shell dumpsys gfxinfo com.rebuild reset >/dev/null; }
dm(){ sleep "${2:-2.5}"; adb shell dumpsys gfxinfo com.rebuild framestats > "$O/$L-$1.txt"; }
measure(){ local p; p=$("$T/xy.sh" "$2"); [ -n "$p" ] || { echo "missing $2 for $1"; return 1; }; rs; adb shell input tap $p; dm "$1" "${3:-3}"; }
fill(){ for field in "entry-kg:40" "entry-reps:10"; do $U tap "${field%%:*}" >/dev/null; adb shell input keyevent KEYCODE_MOVE_END
    for _ in 1 2 3 4 5 6; do adb shell input keyevent KEYCODE_DEL; done; $U type "${field#*:}" >/dev/null; done
  $U back >/dev/null; sleep 1.5; }
skip(){ $U has "Skip rest" && { $U tap "Skip rest" >/dev/null; sleep 1.5; }; true; }
discard(){ $U tap "More workout actions" >/dev/null; sleep 1; $U tap "Discard workout" >/dev/null; sleep 1.2; $U tap "Discard" >/dev/null; sleep "${1:-2}"; }
if [ "$APK" != - ]; then
  adb install -r "$APK" >/dev/null || exit 1
  adb shell am force-stop com.rebuild
  if [ "$KIND" = release ]; then
    adb shell am start -W -n com.rebuild/.MainActivity >/dev/null; sleep 10; adb shell am force-stop com.rebuild
    adb shell cmd package compile -m speed-profile -f com.rebuild >/dev/null
  fi
fi
"$T/prep-fresh.sh"
measure push1 "${START:-start-scheduled}" 4; $U wait log-set 10 >/dev/null; sleep 1
fill; measure log1 log-set 3; skip
fill; measure log2 log-set 3; skip
measure next1 next-exercise 3; sleep 1
fill; measure log3 log-set 3; skip
fill; measure log4 log-set 3; skip
measure next2 next-exercise 3; sleep 1
discard 3
measure push2 "${START:-start-scheduled}" 4; $U wait log-set 10 >/dev/null; sleep 1
discard
echo "done $L"
