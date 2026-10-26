"""Which composables cost what during a tab switch, from a Perfetto trace with composition tracing.

usage: composables.py TRACE
Record with a build that adds androidx.compose.runtime:runtime-tracing and androidx.tracing:tracing-perfetto(-binary),
enable it with `am broadcast -a androidx.tracing.perfetto.action.ENABLE_TRACING com.rebuild/androidx.tracing.perfetto.TracingReceiver`,
and a Perfetto config with the track_event data source. Prints self and inclusive time per slice in the app-bound
frames (those not blocked on the render thread).
"""
import sys
from perfetto.trace_processor import TraceProcessor
tp = TraceProcessor(trace=sys.argv[1])
tp.query("""create perfetto table st as
select s.id, s.ts, s.dur, s.name, s.depth, s.parent_id, s.dur - coalesce((select sum(c.dur) from slice c where c.parent_id=s.id),0) self
from slice s""")
frames = list(tp.query("""select s.ts, s.dur from slice s where s.name like 'Choreographer#doFrame%' and s.dur>40e6
  and (select sum(c.dur) from slice c where c.name='postAndWait' and c.ts between s.ts and s.ts+s.dur) < 10e6 order by ts"""))
print("app-bound frames:", [round(f.dur/1e6) for f in frames])
cond = " or ".join(f"(ts>={f.ts} and ts<{f.ts+f.dur})" for f in frames)
print("\n== top self time (all app-bound frames) ==")
for r in tp.query(f"select name, sum(self)/1e6 s, sum(dur)/1e6 i, count(*) n from st where {cond} group by name order by s desc limit 30"):
    print(f"  self {r.s:6.1f}  incl {r.i:7.1f} n={r.n:5d}  {r.name[:105]}")
print("\n== com.rebuild composables by inclusive (top-level occurrences only) ==")
for r in tp.query(f"""select name, sum(dur)/1e6 i, count(*) n from st where ({cond}) and name like 'com.rebuild%'
   and not exists (select 1 from slice p where p.id = st.parent_id and p.name = st.name)
   group by name order by i desc limit 40"""):
    print(f"  incl {r.i:7.1f} n={r.n:5d}  {r.name[:110]}")
