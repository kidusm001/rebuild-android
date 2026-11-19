#!/usr/bin/env bash
# hrec.sh <outdir> <name> <secs> <command...>: screenrecord while a command runs, noting the device's wall-clock ms at
# the recorder's start (<name>.start) and dumping vibrator_manager afterwards (<name>.vib).
out=$1; name=$2; secs=$3; shift 3
adb shell "rm -f /sdcard/h.mp4; date +%s%3N; exec screenrecord ${BUGREPORT:+--bugreport} --bit-rate 12000000 --time-limit $secs /sdcard/h.mp4" > "$out/$name.start" & rec=$!
sleep 1.2; "$@"; sleep 0.8
adb shell pkill -INT screenrecord >/dev/null 2>&1; wait $rec; sleep 0.5
adb pull /sdcard/h.mp4 "$out/$name.mp4" >/dev/null
adb shell dumpsys vibrator_manager > "$out/$name.vib"
echo "$out/$name.mp4 start=$(head -1 $out/$name.start)"
