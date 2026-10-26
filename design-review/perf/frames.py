"""Per-frame main-thread breakdown of slow frames in a Perfetto trace of Rebuild.

usage: frames.py TRACE   (needs the `perfetto` Python package)
For every Choreographer frame over 30 ms on the app main thread: recomposition, applying changes, measure/layout,
text layout, recording the draw, and time blocked waiting for the render thread (postAndWait). A frame that is all
postAndWait is GPU-bound (on the emulator: SwiftShader), not app work.
"""
import sys
from perfetto.trace_processor import TraceProcessor
tp = TraceProcessor(trace=sys.argv[1])
main = "select t.utid from thread t join process p using(upid) where p.name='com.rebuild' and t.tid=p.pid"
frames = list(tp.query(f"""
select s.id, s.ts, s.dur from slice s join thread_track tt on s.track_id=tt.id
where tt.utid in ({main}) and s.name like 'Choreographer#doFrame%' and s.dur > 30e6 order by s.ts"""))
t0 = frames[0].ts if frames else 0
for f in frames:
    kids = {r.name: r.d for r in tp.query(f"""
      select s.name, sum(s.dur)/1e6 d from slice s join thread_track tt on s.track_id=tt.id
      where tt.utid in ({main}) and s.ts >= {f.ts} and s.ts < {f.ts + f.dur} and s.depth > 0
      and s.name in ('Recomposer:recompose','AndroidOwner:measureAndLayout','Record View#draw()','postAndWait','Compose:applyChanges','TextLayout:initLayout')
      group by s.name""")}
    print(f"t={(f.ts-t0)/1e9:6.2f}s frame {f.dur/1e6:6.1f}ms  recompose {kids.get('Recomposer:recompose',0):5.1f}  apply {kids.get('Compose:applyChanges',0):5.1f}  layout {kids.get('AndroidOwner:measureAndLayout',0):5.1f}  text {kids.get('TextLayout:initLayout',0):5.1f}  record {kids.get('Record View#draw()',0):5.1f}  waitRT {kids.get('postAndWait',0):5.1f}")
