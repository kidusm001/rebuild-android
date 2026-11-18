#!/usr/bin/env bash
# Capture proof into $REBUILD_EVIDENCE (default /tmp/rebuild-verify), which cleanup never touches.
#   evidence.sh shot <name>                  full-resolution screenshot -> <name>.png
#   evidence.sh record <name> <seconds>      screen recording -> <name>.mp4 (runs in the foreground)
#   evidence.sh record-while <name> <max seconds> <command...>   record while a command drives the app -> <name>.mp4
#   evidence.sh frames <name> [fps]          split <name>.mp4 into <name>-frames/f###.png (default 10 fps)
#   evidence.sh sheet <name> <from s> <secs> [fps] [cols]   one contact sheet of <name>.mp4 from <from> for <secs>,
#                                            each frame stamped with its time -> <name>-sheet-<from>.png (30 fps, 10 cols)
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
  record-while)
    # For motion: the recording starts before the first tap and stops when the command ends (or at the limit).
    name=$2; secs=$3; shift 3
    "$adb" shell rm -f /sdcard/rv.mp4
    "$adb" shell screenrecord --bit-rate 12000000 --time-limit "$secs" /sdcard/rv.mp4 & rec=$!
    sleep 1.2
    status=0; "$@" || status=$?
    sleep 0.8; "$adb" shell pkill -INT screenrecord >/dev/null 2>&1 || true; wait $rec || true; sleep 0.5
    "$adb" pull /sdcard/rv.mp4 "$out/$name.mp4" >/dev/null; echo "$out/$name.mp4"; exit $status ;;
  frames)
    rm -rf "$out/$2-frames"; mkdir -p "$out/$2-frames"
    ffmpeg -loglevel error -i "$out/$2.mp4" -vf "fps=${3:-10}" "$out/$2-frames/f%03d.png"; echo "$out/$2-frames" ;;
  sheet)
    # Recordings are variable frame rate (a still screen writes no frames), so fps resamples to an even clock.
    fps=${5:-30}; cols=${6:-10}
    n=$(python3 -c "import math; print(max(1, math.ceil($4 * $fps)))"); rows=$(( (n + cols - 1) / cols ))
    ffmpeg -loglevel error -y -ss "$3" -t "$4" -i "$out/$2.mp4" -vf "fps=$fps,scale=270:-1,drawtext=text='%{pts\\:hms}':x=6:y=6:fontsize=16:fontcolor=white:box=1:boxcolor=black@0.6,tile=${cols}x${rows}:padding=4:color=gray" \
      -frames:v 1 "$out/$2-sheet-$3.png"; echo "$out/$2-sheet-$3.png" ;;
  notifications)
    "$adb" shell cmd statusbar expand-notifications; sleep 2
    "$adb" exec-out screencap -p >"$out/$2.png"; "$adb" shell cmd statusbar collapse; echo "$out/$2.png" ;;
  theme) "$adb" shell cmd uimode night "$([ "$2" = dark ] && echo yes || echo no)" >/dev/null; sleep 2; echo "theme $2" ;;
  *) sed -n '2,11p' "$0"; exit 2 ;;
esac
