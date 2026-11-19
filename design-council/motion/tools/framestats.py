"""framestats.py file...: per framestats dump: frames, max total, max main-thread, median, frames >50 ms, span."""
import sys, statistics
def parse(path):
    rows, header = [], None
    for line in open(path):
        line = line.strip()
        if line.startswith("Flags,"): header = line.rstrip(",").split(","); continue
        if header and line and line[0].isdigit():
            v = line.rstrip(",").split(",")
            if len(v) >= len(header): rows.append(dict(zip(header, map(int, v))))
    # Drop frames with an unset completion time (a garbage value of ~7e10 ms otherwise).
    return [r for r in rows if 0 < r["FrameCompleted"] - r["IntendedVsync"] < 10_000_000_000]
for p in sys.argv[1:]:
    rows = parse(p)
    if not rows: print(f"{p}: no frames"); continue
    tot = [(r["FrameCompleted"] - r["IntendedVsync"]) / 1e6 for r in rows]
    main = [(r["SyncStart"] - r["IntendedVsync"]) / 1e6 for r in rows]
    span = (max(r["FrameCompleted"] for r in rows) - min(r["IntendedVsync"] for r in rows)) / 1e6
    print(f"{p.split('/')[-1]:28s} frames {len(rows):3d}  max {max(tot):7.1f}  maxMain {max(main):7.1f}  median {statistics.median(tot):6.1f}  >50ms {sum(t>50 for t in tot):2d}  span {span:7.0f}")
