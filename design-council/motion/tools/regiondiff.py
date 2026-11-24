#!/usr/bin/env python3
"""regiondiff.py <video> x y w h [from] [to]: per real frame, mean abs grey difference of a region vs the previous frame."""
import subprocess, sys
v=sys.argv[1]; x,y,w,h=map(int,sys.argv[2:6]); a=float(sys.argv[6]) if len(sys.argv)>6 else 0; b=float(sys.argv[7]) if len(sys.argv)>7 else 1e9
t=[float(q) for q in subprocess.run(["ffprobe","-v","error","-select_streams","v","-show_entries","frame=pts_time","-of","csv=p=0",v],capture_output=True,text=True).stdout.split()]
W,H=w//4,h//4
raw=subprocess.run(["ffmpeg","-loglevel","error","-i",v,"-vf",f"crop={w}:{h}:{x}:{y},scale={W}:{H}","-fps_mode","passthrough","-f","rawvideo","-pix_fmt","gray","-"],capture_output=True).stdout
n=W*H; prev=None; out=[]
for i in range(min(len(t),len(raw)//n)):
    f=raw[i*n:(i+1)*n]
    if prev is not None and a<=t[i]<b: out.append(f"{t[i]:.3f}:{sum(abs(p-q) for p,q in zip(f,prev))/n:.1f}")
    prev=f
print(" ".join(out))
