#!/usr/bin/env bash
# drive-history-back.sh <outdir> <name>: History > Exercises > the first exercise, then a slow back swipe held and let
# go (commit). Marks device ms per step.
out=$1; marks=$out/$2.marks; : > "$marks"; T=$(cd "$(dirname "$0")" && pwd)
U="$(git -C "$T" rev-parse --show-toplevel)/.claude/skills/verify-rebuild/scripts/ui.sh"
mark(){ echo "$1 $(adb shell date +%s%3N)" >> "$marks"; }
$U tap History >/dev/null; sleep 2; $U tap Exercises >/dev/null; sleep 2
p=$("$T/xy.sh" "Leg press"); mark open; adb shell input tap $p; sleep 3
mark backswipe; adb shell input swipe 3 1300 700 1300 1800; sleep 4
$U tap Today >/dev/null; sleep 1
