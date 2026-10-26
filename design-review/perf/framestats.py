"""Splits each frame from `dumpsys gfxinfo PKG framestats` into main-thread and render/GPU time.

usage: framestats.py < framestats.txt
Main thread = IntendedVsync → SyncStart (input, animation, measure/layout, record); render = SyncStart →
FrameCompleted (sync, draw commands, GPU). Prints medians and 90th percentiles in milliseconds.
"""
import statistics, sys

rows, header = [], None
for line in sys.stdin:
    line = line.strip()
    if line.startswith("Flags,"):
        header = line.rstrip(",").split(",")
        continue
    if header and line and line[0].isdigit():
        values = line.rstrip(",").split(",")
        if len(values) >= len(header):
            row = dict(zip(header, map(int, values)))
            if row["Flags"] == 0:
                rows.append(row)

def ms(a, b):
    return [(r[b] - r[a]) / 1e6 for r in rows]

def pct(xs, p):
    xs = sorted(xs)
    return xs[min(len(xs) - 1, int(len(xs) * p))]

if not rows:
    sys.exit("no frames")
for name, (a, b) in {
    "total": ("IntendedVsync", "FrameCompleted"),
    "main thread": ("IntendedVsync", "SyncStart"),
    "  layout+measure": ("PerformTraversalsStart", "DrawStart"),
    "  draw (record)": ("DrawStart", "SyncStart"),
    "render thread+GPU": ("SyncStart", "FrameCompleted"),
}.items():
    xs = ms(a, b)
    print(f"{name:20s} median {statistics.median(xs):7.1f}  p90 {pct(xs, 0.9):7.1f}  max {max(xs):7.1f}")
print(f"frames: {len(rows)}")
