#!/usr/bin/env bash
# tracetap.sh <out.txt> <handle> [secs]: a text atrace (app sections, gfx, view) around one tap on <handle>.
out=$1; h=$2; secs=${3:-4}; T=$(cd "$(dirname "$0")" && pwd)
p=$("$T/xy.sh" "$h"); [ -n "$p" ] || { echo "missing $h"; exit 1; }
adb shell atrace -a com.rebuild -t "$secs" -b 32768 gfx view input > "$out" 2>/dev/null &
pid=$!; sleep 1.2; adb shell input tap $p; wait $pid; echo "$out $(wc -l < "$out") lines"
