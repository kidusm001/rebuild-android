#!/usr/bin/env python3
"""hapt.py <dir> <name> [offset_ms]: for each TOUCH vibration in <name>.vib during <name>.mp4, the first real frame around it
where the body above the dock changes (y 300..1700) and where the ledger cells change (x 600..1030, y 780..1300),
and the tick's position against each (+: tick before the frame, -: after). Ticks are placed by the device wall clock
read just before screenrecord started (<name>.start) plus [offset_ms], the measured delay from that read to frame 0."""
import re, subprocess, sys, datetime
d, n = sys.argv[1], sys.argv[2]; off0 = float(sys.argv[3]) / 1000 if len(sys.argv) > 3 else 0.0
v = f"{d}/{n}.mp4"
start = int(open(f"{d}/{n}.start").read().split()[0])
tz = datetime.timezone(datetime.timedelta(hours=3))
ticks = set()
for m in re.finditer(r"startTime: (\d\d)-(\d\d) (\d\d):(\d\d):(\d\d)\.(\d{3}).*?effect=(\w+)", open(f"{d}/{n}.vib").read()):
    mo, da, h, mi, s, ms, eff = m.groups()
    t = datetime.datetime(2026, int(mo), int(da), int(h), int(mi), int(s), int(ms) * 1000, tzinfo=tz).timestamp() * 1000
    o = (t - start) / 1000 - off0
    if 0 <= o <= 300: ticks.add((round(o, 3), eff))
times = [float(q) for q in subprocess.run(["ffprobe", "-v", "error", "-select_streams", "v", "-show_entries", "frame=pts_time",
        "-of", "csv=p=0", v], capture_output=True, text=True).stdout.split()]
def frames(x, y, w, h):
    W, H = w // 4, h // 4
    raw = subprocess.run(["ffmpeg", "-loglevel", "error", "-i", v, "-vf", f"crop={w}:{h}:{x}:{y},scale={W}:{H}", "-fps_mode",
          "passthrough", "-f", "rawvideo", "-pix_fmt", "gray", "-"], capture_output=True).stdout
    k = W * H
    return [raw[i * k:(i + 1) * k] for i in range(min(len(times), len(raw) // k))]
body = frames(0, 300, 1080, 1400); cells = frames(600, 780, 430, 520)
def diff(fs, i): return sum(abs(p - q) for p, q in zip(fs[i], fs[i - 1])) / len(fs[i])
def first_change(fs, t0, t1, thr):
    for i in range(1, len(fs)):
        if t0 <= times[i] <= t1 and diff(fs, i) > thr: return times[i]
    return None
for t, eff in sorted(ticks):
    b = first_change(body, t - 1.2, t + 0.8, 0.3); c = first_change(cells, t - 1.2, t + 0.8, 1.5)
    fmt = lambda x: "none" if x is None else f"{x:.3f} (tick {1000 * (x - t):+.0f} ms)"
    print(f"tick {t:.3f} {eff}: body first change {fmt(b)}; ledger cells first change {fmt(c)}")
