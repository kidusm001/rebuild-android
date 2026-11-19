#!/usr/bin/env python3
"""shadercompiles.py trace.txt...: GPU program compiles (`shader_compile` slices, with the op that needed each and its
ms) on com.rebuild's RenderThread, per text atrace (`adb shell atrace -a com.rebuild -t N gfx view`)."""
import re, sys
pat = re.compile(r'^\s*(.+?)-(\d+)\s+\(\s*(\d+|-+)\)\s+\[\d+\].*?\s(\d+\.\d+): tracing_mark_write: ([BE])\|(\d+)\|?(.*)$')
for path in sys.argv[1:]:
    app = None; st = {}; res = []
    for line in open(path, errors='replace'):
        m = pat.match(line)
        if not m: continue
        comm, tid, tg, ts, kind, pid, name = m.groups()
        if app is None and comm.strip().endswith('com.rebuild') and tid == pid: app = pid
        if pid != app: continue
        s = st.setdefault(tid, [])
        if kind == 'B': s.append((name, float(ts)))
        elif s:
            n, t0 = s.pop()
            if n == 'shader_compile': res.append(((float(ts) - t0) * 1000, s[-1][0] if s else '?'))
    print(f"{path.split('/')[-1]:28s} n={len(res):2d} total={sum(r[0] for r in res):6.0f}ms  " +
          " ".join(f"{o[:14]}:{d:.0f}" for d, o in res))
