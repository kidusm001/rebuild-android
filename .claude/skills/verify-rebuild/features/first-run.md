# First run

A fresh install opens on "Choose where to start": four training phases, a button that starts with the chosen one, and "Restore a backup" for someone moving phones. Choosing a phase lands on Today with this week's plan.

## Sub-features

- `first-run-phase` choosing a phase starts the app on Today with that phase's weekly plan.
- `first-run-restore` "Restore a backup" leads into the restore flow (see backup-restore).

## How to get to it (user POV)

- Open the app after installing it for the first time, or after clearing its data.

## Driving it with ui.sh

Preconditions:

- `$S/emu.sh reset` has just run.

- **First screen.** Open the app. Run `$S/emu.sh launch`, then `$S/ui.sh wait phase-INTRO_ONE 20`. "Choose where to start" and four phases show. The start button reads "Choose a phase" and is disabled.
- **Choose.** Pick Build consistency. Run `$S/ui.sh tap phase-INTRO_TWO`. The button reads "Start with Build consistency".
- **Start.** Run `$S/ui.sh tap "Start with"`, then `$S/ui.sh wait week-checklist 10`. Today shows the week with 4 workouts.
- **Persists.** Run `adb shell am force-stop com.rebuild` and open the app again. Today shows again, not the first-run screen.
- **Proof.** `$S/evidence.sh shot first-run-chosen` before tapping Start, and `$S/evidence.sh shot first-run-today` after it.

## Gotchas

- The phase tags use the enum names: `phase-INTRO_ONE`, `phase-INTRO_TWO`, `phase-FIVE_TWO`, `phase-BASELINE`.
- `flows.sh first-run` does all of this in one line, for recipes that only need a chosen phase.
