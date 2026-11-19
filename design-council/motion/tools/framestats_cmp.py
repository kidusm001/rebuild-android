"""framestats_cmp.py dir: max frame (ms) per moment and build, from framestats dumps named <build>-<moment>.txt."""
import sys, glob, os, collections
sys.path.insert(0, os.path.dirname(__file__))
def maxtot(p):
    rows, header = [], None
    for line in open(p):
        line=line.strip()
        if line.startswith("Flags,"): header=line.rstrip(",").split(","); continue
        if header and line and line[0].isdigit():
            v=line.rstrip(",").split(",")
            if len(v)>=len(header): rows.append(dict(zip(header,map(int,v))))
    tot=[(r["FrameCompleted"]-r["IntendedVsync"])/1e6 for r in rows if 0<r["FrameCompleted"]-r["IntendedVsync"]<1e10]
    return max(tot) if tot else None
data=collections.defaultdict(dict)
for p in glob.glob(sys.argv[1]+"/*.txt"):
    b,kind,m=os.path.basename(p)[:-4].split("-",2)
    data[(kind,m)][b]=maxtot(p)
builds=sorted({b for k in data for b in data[k]})
print("moment".ljust(14)+"".join(f"{b:>10}" for b in builds))
for k in sorted(data): print(f"{k[0]}-{k[1]}".ljust(14)+"".join(f"{(data[k].get(b) or 0):10.0f}" for b in builds))
