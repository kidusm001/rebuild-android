#!/usr/bin/env python3
"""presence.py <video.mp4> <from> <to> [light|dark|grey] [-v]: content presence of every real frame in [from, to).
Share of body pixels (below status bar, above the bottom 11 %) differing from the canvas by > 10 grey levels, divided by
the smaller of the first and last frame's share (the two settled screens). Passes when min >= 0.40."""
import subprocess, sys
v, a, b = sys.argv[1], float(sys.argv[2]), float(sys.argv[3])
W, H = 108, 240
times = [float(x) for x in subprocess.run(["ffprobe","-v","error","-select_streams","v","-show_entries","frame=pts_time",
         "-of","csv=p=0",v],capture_output=True,text=True).stdout.split()]
raw = subprocess.run(["ffmpeg","-loglevel","error","-i",v,"-vf",f"scale={W}:{H}","-fps_mode","passthrough",
       "-f","rawvideo","-pix_fmt","gray","-"],capture_output=True).stdout
allf = [(times[i], raw[i*W*H:(i+1)*W*H]) for i in range(min(len(raw)//(W*H), len(times)))]
before = [f for f in allf if f[0] < a]
frames = before[-1:] + [f for f in allf if a <= f[0] < b]
if len(frames) < 2: sys.exit("fewer than two frames in the window")
def body(f): return [f[y*W+x] for y in range(14, H-26) for x in range(4, W-4)]
arg = sys.argv[4] if len(sys.argv) > 4 else "light"
canvas = {"light": 236, "dark": 14}.get(arg) or int(arg)
def share(f): px = body(f); return sum(1 for p in px if abs(p-canvas) > 10) / len(px)
s = [(t, share(f)) for t, f in frames]
ref = min(s[0][1], s[-1][1]) or 1e-9
low = min(s, key=lambda x: x[1])
print(f"canvas={canvas} ref={ref:.3f} frames={len(s)} min presence {low[1]/ref:.2f} at {low[0]:.3f}  " + ("PASS" if low[1]/ref >= 0.4 else "FAIL"))
if "-v" in sys.argv: print(" ".join(f"{t:.3f}:{x/ref:.2f}" for t, x in s))
