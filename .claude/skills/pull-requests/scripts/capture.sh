#!/usr/bin/env bash
# Capture the emulator's current screen as a PNG.
# Usage: capture.sh <out-dir> <name> [light|dark] [font-scale]
#   capture.sh shots/before workout            # light theme, 100% text
#   capture.sh shots/after workout-dark dark   # dark theme
#   capture.sh shots/after today-200 light 2.0 # 200% text
# Theme and text size are set before the capture and restored after it.
set -euo pipefail
out=$1 name=$2 theme=${3:-light} scale=${4:-1.0}
adb=${ADB:-/opt/android-sdk/platform-tools/adb}
mkdir -p "$out"
"$adb" get-state >/dev/null
[ "$theme" = dark ] && "$adb" shell cmd uimode night yes >/dev/null
[ "$scale" != 1.0 ] && "$adb" shell settings put system font_scale "$scale"
restore() {
  "$adb" shell cmd uimode night no >/dev/null || true
  "$adb" shell settings put system font_scale 1.0 || true
}
trap restore EXIT
sleep 2
"$adb" exec-out screencap -p > "$out/$name.png"
echo "$out/$name.png"
