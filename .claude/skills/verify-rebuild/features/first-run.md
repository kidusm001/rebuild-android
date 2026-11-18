# First run

A fresh install opens on setup:
- **Welcome:** "Get started", or "I have a backup" for someone moving phones.
- **Three questions:** experience, days a week, and equipment with the weight unit.
- **The plan they lead to:** "Start with this plan" saves it.
- **Rest alerts:** the last step, which can be skipped. It lands on Today with the week of the chosen split.

## Sub-features

- `first-run-setup` answering the three questions shows one plan, and starting it lands on Today with that split's week.
- `first-run-back` Back goes one step back with every answer kept; a Change on the plan comes straight back to it.
- `first-run-restore` "I have a backup" leads into the restore flow (see backup-restore).
- `first-run-rest-alerts` the last step asks for notifications only after the plan is saved, and can be skipped.

## How to get to it (user POV)

- Open the app after installing it for the first time, or after clearing its data.

## Driving it with ui.sh

Preconditions:

- `$S/emu.sh reset` has just run. Run `adb shell pm revoke com.rebuild android.permission.POST_NOTIFICATIONS` first to see the rest step's first state ("Turn on rest alerts" / "Not now").

- **Welcome.** Run `$S/emu.sh launch`, then `$S/ui.sh wait setup-action 20`. "Your training, written down." shows, with Get started (`setup-action`) and I have a backup (`setup-restore`).
- **Questions.** Run `$S/ui.sh tap setup-action` and pick `setup-experience-regular`. The action is disabled until something is picked. Then `setup-action`, `setup-days-4`, `setup-action`, `setup-gear-gym`, `setup-action`.
- **Plan.** `split-name` reads "Upper and lower". Days open with `split-day-<template id>`.
- **Start.** Run `$S/ui.sh tap setup-action`. The rest step shows. Run `$S/ui.sh tap setup-rest-skip` (or `setup-rest-done` when notifications are already allowed), then `$S/ui.sh wait week-checklist 10`.
- **Persists.** Run `adb shell am force-stop com.rebuild` and open the app again. Today shows again, not setup.
- **Proof.** `$S/evidence.sh shot first-run-plan` on the plan, and `$S/evidence.sh shot first-run-today` after it.

## Gotchas

- Tags:
  - `setup-experience-{new,returning,regular,experienced}`
  - `setup-days-{2..6}`
  - `setup-gear-{gym,dumbbells,bodyweight}`
  - `setup-unit-{kg,lb}`
- At more than 130% font scale, or on a short window, the action sits at the end of the scroll instead of pinned. Scroll before tapping it.
- Pages slide in for about 260 ms, and their action stays disabled until the slide ends. Wait about 0.6 s between steps.
- `flows.sh first-run [experience] [days] [gear]` does the whole walk in one line.
