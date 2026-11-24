#!/usr/bin/env python3
"""blankframes.py <video.mp4> [from] [to]: real frames whose body (y 14..H-26 of a 108x240 scale, i.e. above the bottom
260 px) is >= 98.5 % one flat colour (a blank screen)."""
import subprocess, sys
from collections import Counter
v = sys.argv[1]; a = float(sys.argv[2]) if len(sys.argv) > 2 else 0; b = float(sys.argv[3]) if len(sys.argv) > 3 else 1e9
W, H = 108, 240
times = [float(x) for x in subprocess.run(["ffprobe","-v","error","-select_streams","v","-show_entries","frame=pts_time","-of","csv=p=0",v],capture_output=True,text=True).stdout.split()]
raw = subprocess.run(["ffmpeg","-loglevel","error","-i",v,"-vf",f"scale={W}:{H}","-fps_mode","passthrough","-f","rawvideo","-pix_fmt","gray","-"],capture_output=True).stdout
n = len(raw)//(W*H); out=[]
for i in range(min(n, len(times))):
    if not (a <= times[i] < b): continue
    f = raw[i*W*H:(i+1)*W*H]; px = [f[y*W+x] for y in range(14, H-26) for x in range(4, W-4)]
    c = Counter(p//3 for p in px).most_common(1)[0][1] / len(px)
    if c >= 0.985: out.append(f"{times[i]:.3f}")
print(f"{len(out)} blank frames" + (": " + " ".join(out) if out else ""))
