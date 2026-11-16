# History

History has three tabs: Workouts (every finished workout, newest first), Exercises (each exercise's history: the latest session, charts of weight and reps, records and all sessions) and Body (bodyweight and measurements).

## Sub-features

- `history-workouts` the workouts list and a workout's detail.
- `history-exercise` an exercise's latest session, its charts (touch to read a point) and every session.
- `history-body` bodyweight and measurement trends.

## How to get to it (user POV)

- The History tab, then a tab at the top: Workouts, Exercises, Body.

## Driving it with ui.sh

Preconditions:

- History is seeded: `$S/emu.sh reset && $S/flows.sh seed-history`.

- **Workouts.** Run `$S/ui.sh tap History`, `$S/ui.sh tap history-tab-0`. A list of finished workouts shows, newest first; tap one to open its detail with stat tiles.
- **Exercise history.** Run `$S/ui.sh tap history-tab-1`, then `$S/ui.sh tap "Lat pulldown"`. The latest session card shows each set as "46.25 kg × 9" with RIR at the end, then the Weight and Reps charts.
- **Read a chart.** Tap a point on a chart. The readout shows that session's date and sets.
- **Body.** Run `$S/ui.sh tap history-tab-2`. Bodyweight and waist trends show.
- **Proof.** Shots of each tab and of an exercise's history with a chart touched.

## Gotchas

- The seed's dates are relative to today, so exact dates change between runs; check shapes, not values.
- Lists are lazy: scroll (`adb shell input swipe 540 1900 540 600 300`) to reach rows below the fold before tapping them.
