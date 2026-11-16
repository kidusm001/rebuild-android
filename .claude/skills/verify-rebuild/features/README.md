# Rebuild verification map

The maintained source for proving Rebuild's user-facing behaviour on the emulator. Read this index, then the feature's file, before driving.

## Baseline preconditions

- The emulator is up and the checkout's debug build is installed: `$S/emu.sh up && $S/emu.sh install` (`S=.claude/skills/verify-rebuild/scripts`).
- `$S/emu.sh doctor` passes.
- Start each recipe from the state its `Preconditions:` names: a fresh install (`$S/emu.sh reset`), a chosen phase (`$S/flows.sh first-run`), or seeded history (`$S/emu.sh reset && $S/flows.sh seed-history`).
- Never reset or reinstall a device this run didn't set up.

## Driving conventions

- Tap by test tag where one exists (`ui.sh list` shows them); text and descriptions are the fallback.
- Treat commands as literal; keep quoted names unchanged.
- Light theme unless a recipe says otherwise; put the theme back afterwards.

## Proof and skip reporting

- Capture the action and the resulting state, not only the final screen.
- A screenshot counts only after you've looked at it.
- Check side effects (`dumpsys notification`, `dumpsys alarm`, data surviving a force-stop) alongside the screen.
- Report an unreachable path with the command tried and the precondition that wasn't met. Never report a path as verified through a different one.

## Feature entry contract

Each file has an H1, one paragraph on the user-visible behaviour, then exactly four H2s in order: `Sub-features`, `How to get to it (user POV)`, `Driving it with ui.sh`, `Gotchas`.

## Features

- [First run](./first-run.md): choosing a phase, or restoring a backup instead.
- [Logging a workout](./workout-logging.md): starting a workout, logging sets, the rest bar and its time picker.
- [Exercise strip](./exercise-strip.md): selecting, holding to drag into a new order, and the hold menu (move, remove from today).
- [Rest alerts](./rest-alerts.md): the sound and on-screen choices, the edge light and Up next, the alarm's Stop, and the rest notifications.
- [History](./history.md): workouts, exercise history and its charts.
- [Backup and restore](./backup-restore.md): exporting a backup and restoring one.
