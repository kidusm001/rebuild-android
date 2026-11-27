# Changelog

Every version installs over the one before it with the same signing key and keeps all workout data. From 0.1.16
on, release APKs are built and published by [GitHub Actions](.github/workflows/release.yml); notes for each are in
[docs/releases](docs/releases/).

## 0.6.0 (version code 28)
- Switch exercise: swap an exercise during a workout from the hold menu or Switch exercise sheet (including unlogged
  exercises added today), with movement-aware grouped suggestions also in Edit plan, plan additions, and routines.
  Custom exercises now require a muscle and equipment so they get same-muscle swaps too.
- Collapsing headers: screen headers collapse on scroll under frosted glass, including in right-to-left layouts.
- Redesigned exercise editor in Edit plan and Adjust today, and grouped choice rows throughout the app.
- Fixes: Show lists every unfinished exercise; the hold menu no longer flashes while scrolling the exercise strip;
  quieter selection pills and destructive dialog buttons.

## 0.5.2 (version code 27)
- A new app icon: the Rebuild R in amber on ink, also on the launch screen and the welcome page. With themed icons
  on (Android 13+), the home screen shows a tinted R instead of a blank tile.

## 0.5.1 (version code 26)
- Large text: a logged set such as "42.5 kg/DB × 10" stays on one line, History's tabs and the 1M–All ranges always
  fit, chart dates no longer run together, and a chart's readout is never cut off.
- Shorter labels: Measure, Weigh-ins, New exercise and Change; phases read "2 RIR" like the rest of the app.
- A partial workout offers "Count toward the week of …" in both the finish dialog and History.
- A large history opens faster: the saved data is read in bigger pieces, about 12 times quicker for a full one.

## 0.5.0 (version code 25)
- New installs open on a short guided setup that builds a weekly plan from your experience, days and equipment.
  Plan gains Change split, with new splits including Power and hypertrophy, Torso and limbs, Bro and Arnold.
- First-time tips explain the app in place; More > Tips turns them back on.
- A "Today" home screen widget shows today's workout, live progress, the rest countdown and what's up next.
- The app ships its own font, Archivo, and Phosphor Bold icons, so it looks the same on every phone.
- One motion system: no blank flashes between screens, a calmer receipt, and buttons that no longer grey out on save.
- Rest: the bar fits one line in landscape, Skip can be undone for a few seconds, the bar slides away when a rest
  ends, and the picture-in-picture timer no longer breaks after a theme or text-size change.
- Fixes: History detail's tiles stack at large text; a wide logged set no longer runs under its edit pencil.

## 0.4.1 (version code 24)
- During a workout, dragging an exercise to reorder it lands where you drop it. The pill stays under your finger, the
  strip no longer jumps, holding at an edge scrolls gently, and the drop eases into place.
- A move chosen from the hold menu right after letting go now shows straight away.

## 0.4.0 (version code 23)
- History: each exercise has a chart of your heaviest set each workout, with the reps on every dot, the last 3 months
  or All, and an option to split it by workout. The first touch on any chart now reads it.
- History's exercise list shows your latest top set, sorted by Recent, A–Z or muscle; Records say what each rep count
  was before.
- During a workout, the exercise header is two lines (name and unit, then the target), and plate loads read
  "6 plates × 15" so they stay on one line at larger font sizes.
- Presses are amber instead of a grey or white flash; History's tabs press as a pill.
- Text fields share one look, and a focused field's highlight no longer wraps the hint beneath it.

## 0.3.1 (version code 22)
- During a workout, hold an exercise at the top and drag it to change today's order, or hold and let go to move it a
  place or remove it from today. A planned exercise is skipped for today and stays in your plan.
- Dark mode: the amber buttons, rest bar and full-screen rest alerts are softer on black; charts and highlights keep
  their full amber.

## 0.3.0 (version code 21)
- Rest alerts: More > Rest alert chooses how a rest's end is announced: a buzz (as before), long buzz, chime, an
  alarm that rings until stopped, or silent. While Rebuild is open it can also show an edge light around the screen,
  or "Up next", where the rest bar fills the screen with your next set. Choices that contradict each other can't be
  made.
- Rest notifications show a large live countdown with the exercise and what's next, plus +30 s and Skip buttons.
  "Rest over" shows the next set.
- A rest that ends with Rebuild open ends right on time on screen, and the screen stays on while a rest runs.
- Rest picker: Clear sits apart from Cancel and Set, and the quick times form an even grid.
- Exercise history: sets read as clean lines with RIR at the end and drops beneath, without the explanatory notes.
- Number fields inside dialogs are visible before you tap them.

## 0.2.0 (version code 20)
- A new look: warm tonal cards instead of outlines and dividers, bold condensed numbers, amber main buttons and pill
  dialog buttons, in light and dark.
- The rest timer is a full-width amber sheet with a large countdown; rest times are set with minute and second wheels
  and one-tap choices.
- Set entry uses large number tiles with the target in each label; a set with drops reads exactly like any other set.
- History: charts redesigned with an amber trend line and large readouts, cards for the calendar and muscle bars,
  grouped lists, and stat tiles on each finished workout.
- Unlikely loads ask for confirmation before saving; one name per training phase everywhere; clearer Plan and More.

## 0.1.18 (version code 19)
- Compose BOM 2026.03.01: no more scroll-into-view animation crash when focusing an entry while the keyboard slides in.
- Plan screen: weekly working sets totals are explicitly labelled on their rows for accessibility.
- Continuous delivery: signed releases are automatically built and published after CI passes on main.

## 0.1.17 (version code 18)
- Swipe left and right between History's tabs, with an indicator that follows the finger. A horizontal drag on a
  chart still reads the chart.

## 0.1.16 (17)
- A drop is never counted as a working set, and never triggers the over-target warning. After a drop, the next entry
  stays a drop until you log a normal set, so a second drop can't be logged as a new set by accident. The set-type
  menu no longer offers a separate "Drop set" that started a new counted set. A drop logged as its own set in an
  earlier version can be made part of the set before it from that set's details.
- CI on every push (unit tests, the screenshot tour, lint, release build, and the full device suite on an Android
  emulator), and signed releases published from version tags.

## 0.1.15 (16)
- A drop is part of its set: the set keeps its own row, with each drop listed beneath it. Charts, records and the
  finish receipt read the set by its own first stage.

## 0.1.14 (15)
- Touchable charts: press or drag to read the nearest point, with a guide, a readout, a haptic tick and slider-style
  TalkBack stepping.

## 0.1.13 (14)
- Tab switching rebuilt after profiling with Perfetto: tabs are kept alive and pre-warmed, the shared screen
  environment is remembered, and the calendar day became an explicit input. This cut main-thread work per switch from
  about 193 ms to 40 ms, and the first History visit from about 190 ms to 22 ms. See
  [docs/performance.md](docs/performance.md).
- Release build type with R8 and baseline profiles; the APK went from 12 MB to 1.8 MB.

## 0.1.12 (13)
- Loads in pounds per exercise. Bodyweight with weekly averages and a trend chart. Weekly circumference measurements.
- History calendar, working sets per muscle each week, and actual rep records.
- Finish receipt. A bolder design system, and a UI clean-up across every screen.
- First release whose instrumented device suite actually ran.

## 0.1.11 (12)
- Drops join the set before them. Rest countdown notification and picture-in-picture.
- Plan exercise reordering; a working-set stepper when adding an exercise.
- Back hides the keyboard before closing a dialog.

## 0.1.10 (11)
- Timed, distance and cardio exercises, and an activity stopwatch that survives process death.

## 0.1.9 (10)
- Your own routine library, and putting copies of routines on a weekly schedule.

## 0.1.8 (9)
- Flexible workouts: extra sessions, added and skipped exercises, and plan editing.

## 0.1.7 (8)
- Drop and rest-pause sets, and a Monday-to-Sunday weekly checklist.

## 0.1.6 (7)
- Set-first logbook redesign: Last time beside Today for every set.

## 0.1.0 – 0.1.5 (1–6)
- The offline MVP (plan, logging, rest alerts, history, backups), then accessibility, large-text and keyboard-layout
  work, cable plates per stack, and the exercise library.
