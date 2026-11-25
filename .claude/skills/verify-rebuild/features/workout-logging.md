# Logging a workout

From Today a lifter starts the scheduled workout, logs each working set with load, reps and optional RIR, and rests between sets. The rest bar is an amber sheet at the bottom with the countdown, +30 s, Skip and options (including setting the time on minute and second wheels).

## Sub-features

- `workout-start` Today starts the scheduled workout, or resumes the active one.
- `set-log` logging a set adds it to the ledger and starts the rest.
- `rest-bar` the countdown, +30 s and Skip.
- `rest-set-time` Rest options › Set time opens the duration picker with wheels and quick times.
- `workout-finish` Finish ends the workout with a receipt.

## How to get to it (user POV)

- Today › Start on the scheduled workout's row, or "Resume workout" on the active banner.
- A rest starts by itself after each logged set.

## Driving it with ui.sh

Preconditions:

- A phase is chosen (`$S/flows.sh first-run`), or history is seeded.

- **Start.** Run `$S/flows.sh start-workout`. The workout screen shows the first exercise, its target and the set entry dock (`log-set` visible).
- **Log a set.** Run `$S/flows.sh log-set 40 10`. Set 1 reads "40 kg × 10", "Working sets: 1 of N" goes up, and the rest bar (`rest-bar`) appears counting down from the exercise's rest.
- **+30 s.** Run `$S/ui.sh tap "Add 30 seconds"`. The countdown jumps by about 30 s.
- **Set time.** Run `$S/flows.sh set-rest 30`. The picker opens (wheels plus a 3×2 grid of quick times), closes on Set, and the bar counts down from 0:30.
- **Skip.** Run `$S/ui.sh tap "Skip rest"`. The rest bar goes away.
- **Proof.** Shots of the ledger after the set, the rest bar, and the open picker (`$S/evidence.sh shot ...` while it's open).

## Gotchas

- A fresh install has no previous sets to prefill from; with history seeded, the fields start filled from last time's sets (the label just says "Set 1 of 2"; other sources, such as "prefilled from this workout", are named), and `log-set` replaces them.
- The rest's alert (sound, edge light, Up next) depends on More › Rest alert; see rest-alerts.
- `set-rest` takes only the quick values: 30, 60, 90, 120, 180, 300.
