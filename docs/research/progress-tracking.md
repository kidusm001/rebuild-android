# Progress tracking: pounds, bodyweight, measurements, History graphs

Researched October 2026. Where a vendor doesn't publish its method, this says so rather than
guessing. Sources are at the end.

## What the best apps do

**Bodyweight trend**
- **MacroFactor:** a pale scale-weight line under a strong trend line, headlined "Current
  Trend". The trend is a recency-weighted average whose formula isn't published. Gaps are
  linearly interpolated, and the weekly rate comes from the trend's change over 20 days. It
  asks for at least 3 weigh-ins a week, and says plainly that the trend lags.
- **Hacker's Diet** (the root of most trend apps): an EWMA, `T = T_prev + 0.1·(W − T_prev)`,
  which lags about as much as a 20-day simple moving average.
- **Happy Scale:** moving-average options it doesn't document, a this-7-days vs previous-7
  comparison, and week, month, year and all-time stats. Ranges are "last X days", "since date"
  or custom, and the logbook has notes and edit/delete. It leads with predictions and
  milestones, and its line is that weight loss isn't linear.
- **Libra** (Android): a smoothed trend that tolerates sparse entries. Its parameters aren't
  public.

**Measurements**
- **Strong:** neck, shoulders, chest, L/R biceps and forearms, upper abs, waist, lower abs,
  hips, L/R thighs and calves. You log with "+" and delete by swiping.
- **FitNotes:** the cleanest model. Weight and body fat are on by default and other sites are
  opt-in. Each site has its own unit and an optional goal. A row shows the latest value, the
  change from the last entry and how long ago it was. The input shows the previous value, and
  each site has its own graph.
- **JEFIT:** tracks waist, chest, arms and legs against goals.
- **Health Connect / Samsung Health:** weight and body fat, but no circumference types.

**Exercise progress**
- **Strong:** the exercise screen has History, Charts and Records tabs. Records shows bests at
  each rep count plus projected bests. Its e1RM uses Brzycki up to about 12 reps and is
  labeled "always estimates".
- **Hevy:** graphs of heaviest weight, 1RM, best set and session volume, and most reps.
  Tapping a record opens its workout. Ranges are 30 d, 3 M, 1 Y and All. Weekly sets per
  muscle has week arrows.
- **FitNotes:** e1RM, max weight, volume, reps and weight at N reps. Points and a trend line
  can be toggled. Records separate *actual* bests at each rep count from estimates, and fade
  records that a heavier set at more reps supersedes. Warm-ups never count.
- **Boostcamp:** an e1RM curve, bests from 1 to 12 reps, PR boards and a per-muscle heatmap.
- **Liftosaur:** weekly sets per muscle against target ranges, with synergist muscles counted
  at 0.5.

**kg / lb**
- **Boostcamp:** per-exercise lb/kg.
- **Strong:** lb, kg "or a combination".
- **FitNotes:** a global default with per-exercise overrides. Changing an exercise's unit asks
  "Convert existing values" or "Just change unit".
- **Liftosaur:** rounds to the user's own plates.
- **Usual jumps:** 2.5 kg or 5 lb on a barbell.

## Recommendations for Rebuild

### 1. Pounds as a third load unit
- **Per exercise, remembered, never converted.** Add `lb` to the existing labeled unit
  selector beside `kg` and `plates`. Each set already stores its unit. A global setting only
  picks the default for new exercises.
- **Same-unit comparison only.** "Last time" pairs same-unit sets only, and a set in another
  unit gets "No comparison: last time was in kg". Don't show "≈ 45.4 kg": in a gym, a
  different unit usually means different equipment.
- **Steppers and typing:** steps are 2.5 kg, 5 lb or 1 plate. A typed value is kept exactly,
  so 22.5 lb stays 22.5 lb.
- **Spoken labels** say "pounds" in full.
- **No barbell plate calculator yet.**

### 2. Bodyweight
**Record:** `id, date, time, value (Decimal, as typed), unit (kg|lb), note?`. Several entries
a day are allowed.

**Entry**
- **On Today:** a quiet row, "Bodyweight · Not logged today · Log", becoming "82.4 kg · 07:10".
- **On the workout receipt:** the same row, so a workout day's entry takes one tap.
- **The dialog:** one labeled field plus a kg/lb choice defaulting to the last unit, a time
  and a note.
- **The field starts empty,** with "Last: 82.6 kg · Tue" beneath it. A prefill would let a
  skipped weigh-in pass as a real reading.
- **A one-line tip:** weigh under the same conditions each time, on waking and before food.

**Body → Weight screen**
1. **This week:** "Mon 20 – Sun 26 Oct: 82.1 kg average of 3 weigh-ins", then last week's line.
   Weeks run Monday to Sunday, like This week. The average is the mean of daily means, so a
   double-weighed day counts once.
2. **Change:** "Last 4 weeks: −1.6 kg · about −0.4 kg a week", which is
   `(avg(week now) − avg(week now−4)) / 4`. Show it only when both weeks have data, name the
   weeks, and keep the wording neutral with no green or red.
3. **Chart:** raw entries as small hollow muted dots, and weekly averages as a solid line with
   filled points at mid-week. A week without entries breaks the line and isn't interpolated.
   Range chips are 1M, 3M (default), 6M, 1Y and All, and the y-axis fits the data.
4. **Weeks list:** the text equivalent, with each week's average, its count and its entries.
   Tapping an entry opens Edit or Delete, and Delete asks to confirm.

**Why weekly averages and not an EWMA by default**
- **They're what was asked for,** and anyone can check the arithmetic.
- **They have no tuning parameter.**
- **They're honest with sparse data.** An α = 0.1 EWMA assumes daily readings. At 3–4 a week
  it lags badly, or needs interpolated values Rebuild shouldn't invent.
- **The layout itself takes the edge off daily swings:** the average is always the headline
  rather than today's reading, the dots stay muted, and nothing uses goal-pressure colors.

**Optional later: "Smoothed trend (calculated)".** Off by default and drawn dashed, as a
time-aware EWMA: `T_i = T_{i−1} + (1 − 0.9^{Δd})·(W_i − T_{i−1})`, where Δd is the days since
the previous entry. It's seeded with the first week's average and drawn only after 8 or more
entries over 21 or more days.

**Mixed units:** one body is one quantity. Averages and the chart use the display unit,
converting other entries exactly (1 lb = 0.45359237 kg). The list keeps "entered as 181.0 lb",
and nothing is converted when it's saved.

**Goal line:** none in v1. If one is added later, it's a thin optional reference line with no
ETA.

### 3. Body measurements (weekly)
**Record:** `id, date, site, value (Decimal, as typed), unit (cm|in), note?`. A site stores its
name, its landmark text and, for limbs, its side.

**Sites**
- **On by default:** Waist, at the navel. It's the best single fat-loss signal, and the US
  Army moved to a one-site abdominal tape test in 2023.
- **One tap to enable:** Neck (below the larynx), Chest (fullest, under the armpits), Hips
  (widest point of the buttocks), Upper arm (widest, relaxed, one side), Thigh, Calf and
  Shoulders.
- **Custom sites.**
- **"Waist (WHO midpoint)"** as a separate site: midway between the lowest rib and the top of
  the hip bone. Landmarks are never merged.

**Guidance under each field:** same morning conditions, a snug non-elastic tape, a relaxed
belly, and measuring after a normal breath out.

**Screens**
- **"Measure this week":** all enabled sites on one form. Fields start empty with "Last:
  91.5 cm · 3 wk ago" beneath, and Save stores only the filled sites.
- **Site list:** the latest value and date, "since last", and "−3.5 cm since 4 Aug" (the first
  entry).
- **Site detail:** a line with points and no smoothing, ranges defaulting to All, and the
  dated list.
- **Optional weekly reminder** on a day the user picks, with no streak and no missed-week
  count.

**US Navy body-fat estimate: don't build it.**
- It needs height, neck and waist.
- Its error is about ±3–4 %BF and it's biased at the extremes. A 2023 study against DXA
  found circumference methods underestimated fat change during training.
- So it's an inferred number that's weakest at the one thing Rebuild would use it for. The
  waist trend carries the signal without inventing anything.
- If the user insists, put it behind a setting as "Estimate (US Navy formula, ±3–4 %)",
  never as a headline and never on the same chart as measured values.

### 4. History revamp
History has three segments:
- **Workouts:** a month calendar. Days with a finished workout get a dot plus a text label,
  with no streak count and no intensity heatmap. The existing month list sits below.
- **Exercises:** a searchable list, each row showing the latest best set and date.
- **Body:** a Weight card and a Measurements card.

**Exercise detail** (extends the existing Latest, charts and All sessions pattern)
1. **Latest:** "60 kg × 8 · 14 Oct".
2. **Charts:**
   - **Best set load** (default): the heaviest normal working set per session, with its reps
     on tap.
   - **Reps at a chosen load.**
   - **Working sets per session.**
   - **One series per unit:** a kg/lb/plates chip appears only when the history holds more
     than one unit.
3. **Records (actual):** the heaviest load at each rep count from 1 to 15, per unit.
   Superseded records fold away, FitNotes-style, and each one links to its workout.
4. **All sessions:** the text equivalent.

**No e1RM for now.** It's a calculated strength number, close to the score PRODUCT.md rules
out, and it means nothing for plates. If it's added later, use a secondary toggle labeled
"Estimated 1RM (Epley, sets ≤ 10 reps)".

**Volume:** skip tonnage, since it can't be summed across units and it favors high reps.
Instead:
- **Weekly working sets per muscle:** a horizontal bar list for one Monday–Sunday week, with
  Previous/Next arrows.
- **Primary muscle only:** only each exercise's primary muscle in the library counts, with no
  0.5 synergist credit.
- **Caption:** "Normal working sets · warm-ups excluded · primary muscle only".
- **No target bands** unless the plan itself states one.

**Empty states**
- **Weight:** "No weigh-ins yet. Log one from Today or after a workout."
- **Measurements:** "Waist is ready to measure. Turn on other sites in Edit sites."
- **An exercise with one session:** "One session so far. The chart grows as you train this."

### Chart and accessibility rules
- **Drawing:** Compose Canvas or Vico, Theme tokens only, and tabular numerals on the axes.
- **Never color alone:** raw readings are hollow dots, averages are a solid line with filled
  points, and the legend uses words.
- **Every chart has a list below it** as its text equivalent.
- **TalkBack** reads the chart as one summary, for example "Weekly average weight, 3 months,
  84.0 to 82.1 kilograms, 12 weeks". The points themselves aren't focusable.
- **Range chips** are 48 dp, and tapping a point selects its list row.
- **Above 130 % font scale,** the chart height stays fixed and the lists reflow.
- **Motion:** only a 150 ms cross-fade when the range changes.

## Sources
- MacroFactor: https://help.macrofactorapp.com/dashboard/weight_trend/ · https://help.macrofactorapp.com/en/articles/19-change-rate · https://macrofactor.com/macrofactors-algorithms-and-core-philosophy/
- Hacker's Diet: https://fourmilab.ch/hackdiet/www/subsection1_4_1_0_1.html · https://fourmilab.ch/hackdiet/www/subsubsection1_2_4_0_4_3.html
- Happy Scale: https://happyscale.com/ · https://apps.apple.com/app/id532430574
- Libra: https://play.google.com/store/apps/details?id=net.cachapa.libra
- Strong: https://help.strongapp.io/article/237-about-exercise-detail · https://help.strongapp.io/article/238-add-measurements · https://help.strongapp.io/article/133-1rm · https://apps.apple.com/app/id464254577
- Hevy: https://www.hevyapp.com/features/gym-performance/ · https://www.hevyapp.com/features/sets-per-muscle-group-per-week/
- FitNotes: http://www.fitnotesapp.com/progress_tracking/ · http://www.fitnotesapp.com/body_tracker/ · http://www.fitnotesapp.com/exercises/
- Boostcamp: https://www.boostcamp.app/workout-tracker
- Liftosaur: https://www.liftosaur.com/blog/posts/liftosaur-overview/
- JEFIT: https://www.jefit.com/blog/body-stat-goals-how-to-set-and-track-them
- Health Connect: https://developer.android.com/health-and-fitness/health-connect/data-types
- Waist protocols: https://www150.statcan.gc.ca/n1/pub/82-003-x/2012003/article/11707-eng.htm · https://www.phenxtoolkit.org/protocols/view/21602
- Army tape test: https://www.hprc-online.org/physical-fitness/training-performance/how-military-measures-body-composition
- Navy equation (Hodgdon & Beckett 1984): https://apps.dtic.mil/sti/pdfs/ADA143890.pdf
- Circumference vs DXA (2023): https://pmc.ncbi.nlm.nih.gov/articles/PMC10282178
