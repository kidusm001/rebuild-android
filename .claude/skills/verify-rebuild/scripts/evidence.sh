#!/usr/bin/env bash
# Capture proof into $REBUILD_EVIDENCE (default /tmp/rebuild-verify), which cleanup never touches.
#   evidence.sh shot <name>                  full-resolution screenshot -> <name>.png
#   evidence.sh record <name> <seconds>      screen recording -> <name>.mp4 (runs in the foreground)
#   evidence.sh frames <name> [fps]          split <name>.mp4 into <name>-frames/f###.png (default 10 fps)
#   evidence.sh notifications <name>         open the shade, screenshot it, close it -> <name>.png
#   evidence.sh theme light|dark             switch the system theme (the app follows it)
# Prints the path of what it wrote.
set -euo pipefail
adb="${ANDROID_HOME:-/opt/android-sdk}/platform-tools/adb"
out=${REBUILD_EVIDENCE:-/tmp/rebuild-verify}
mkdir -p "$out"
case ${1:-} in
  shot) sleep 0.5; "$adb" exec-out screencap -p >"$out/$2.png"; echo "$out/$2.png" ;;
  record)
    "$adb" shell rm -f /sdcard/rv.mp4
    "$adb" shell screenrecord --time-limit "$3" /sdcard/rv.mp4
    "$adb" pull /sdcard/rv.mp4 "$out/$2.mp4" >/dev/null; echo "$out/$2.mp4" ;;
  frames)
    rm -rf "$out/$2-frames"; mkdir -p "$out/$2-frames"
    ffmpeg -loglevel error -i "$out/$2.mp4" -vf "fps=${3:-10}" "$out/$2-frames/f%03d.png"; echo "$out/$2-frames" ;;
  notifications)
    "$adb" shell cmd statusbar expand-notifications; sleep 2
    "$adb" exec-out screencap -p >"$out/$2.png"; "$adb" shell cmd statusbar collapse; echo "$out/$2.png" ;;
  theme) "$adb" shell cmd uimode night "$([ "$2" = dark ] && echo yes || echo no)" >/dev/null; sleep 2; echo "theme $2" ;;
  *) sed -n '2,8p' "$0"; exit 2 ;;
esac
