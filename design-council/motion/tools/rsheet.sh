#!/usr/bin/env bash
# rsheet.sh <dir> <name> <from> <secs> [cols] [width] : every REAL frame (no resampling) in window, stamped with pts
set -euo pipefail
d=$1; n=$2; from=$3; secs=$4; cols=${5:-8}; w=${6:-240}
cnt=$(ffprobe -v error -select_streams v -show_entries frame=pts_time -of csv=p=0 "$d/$n.mp4" | awk -F, -v a=$from -v b=$(python3 -c "print($from+$secs)") '$1>=a && $1<b' | wc -l)
rows=$(( (cnt + cols - 1) / cols )); [ $rows -lt 1 ] && rows=1
ffmpeg -loglevel error -y -i "$d/$n.mp4" -vf "select='between(t,$from,$from+$secs)',scale=$w:-1,drawtext=text='%{pts\:flt}':x=4:y=4:fontsize=15:fontcolor=white:box=1:boxcolor=black@0.7,tile=${cols}x${rows}:padding=3:color=gray" -fps_mode passthrough -frames:v 1 "$d/$n-r-$from.png"
echo "$d/$n-r-$from.png ($cnt frames)"
