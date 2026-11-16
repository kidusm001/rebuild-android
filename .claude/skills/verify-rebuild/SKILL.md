---
name: verify-rebuild
description: Drive the Rebuild Android app on the rebuild_api34 emulator the way a lifter does (first run, logging sets, rests and rest alerts, the exercise strip, history, backup and restore) and capture screenshots, recordings and notification evidence. Use to prove a change works in the real app, to capture before/after screenshots for a PR, or whenever "check it on the emulator" comes up.
---

# Verify Rebuild on the emulator

Rebuild is an offline Android workout notebook (Kotlin, Jetpack Compose). The user surface is the app on a phone; there is no server. This skill boots the project's emulator, installs the checkout's debug build, drives it through `adb`, and keeps proof outside the repo.

Everything runs from the repo root. Helpers live in `.claude/skills/verify-rebuild/scripts/`; each prints its usage when run without arguments.

## Launch

```bash
S=.claude/skills/verify-rebuild/scripts
$S/emu.sh up        # boots rebuild_api34 if no device is attached; returns when the system is usable
$S/emu.sh install   # ./gradlew installDebug, then grants notifications and exact alarms
$S/emu.sh reset     # optional: wipe app data to get the first-run screen (a fresh install)
$S/emu.sh launch    # open the app (install and reset don't; flows.sh first-run and seed-history do it themselves)
```

Ready means `emu.sh doctor` passes and the app shows either the first-run screen ("Choose where to start") or Today. The emulator needs about 3 GB of RAM; the host has 14 GB, but Gradle daemons, a second checkout's daemons or the IDE can push it over. Check `doctor`'s RAM line before long runs.

Only one emulator runs at a time on this machine. If a device is already attached (a run you didn't start, or the owner's phone over USB), do not reset or reinstall it without asking: `reset` wipes that device's Rebuild data.

## Doctor

```bash
$S/emu.sh doctor
```

Read-only. It checks the device is booted, the app is installed (and prints its version next to the checkout's), notifications are granted, the screen is awake and the host has RAM. Run it before the first drive, and again after anything surprising: a drive that didn't find its handle, a recording with no change, or `adb` saying `device not found` (the emulator crashed; see `emulator.log` in the evidence directory, then `emu.sh up` again).

## Drive

`ui.sh` finds controls by **handle**, in this order: `id=text` (a resource id and its exact text together, for when the same text shows twice), the Compose test tag (the app exposes test tags as resource ids), exact text, content description, then a substring of text or description. Only controls actually on screen count. Prefer test tags: they're what the device tests use and they don't change with copy. `ui.sh list` prints every handle on screen. If a screen can't be read (the device went away, or it never stopped animating after three tries), `ui.sh` stops with exit 2 rather than guessing.

```bash
$S/ui.sh list                       # what's on screen, with tags
$S/ui.sh tap log-set                # tap by test tag
$S/ui.sh tap "Rest options"         # or by content description / text
$S/ui.sh hold exercise-chip-1       # press and hold in place, then let go (opens the exercise menu)
$S/ui.sh drag exercise-chip-0 700   # long-press, drag right 700 px, drop (reorders today)
$S/ui.sh wait rest-bar 10           # wait up to 10 s for something to appear
$S/ui.sh type 42.5                  # type into the focused field
```

Journeys used again and again are in `flows.sh`:

```bash
$S/flows.sh first-run INTRO_TWO     # fresh install -> Build consistency -> Today
$S/flows.sh seed-history            # fresh install -> restore ~10 weeks of history through the real restore flow
$S/flows.sh start-workout           # Today -> the scheduled workout (or resume the active one)
$S/flows.sh log-set 40 10           # log one working set; waits for the rest bar
$S/flows.sh set-rest 30             # make the running rest end in 30 s
$S/flows.sh rest-alert CHIME SWEEP  # More > Rest alert: sound, and optionally what shows on screen
```

The features each journey proves, and how to reach them, are in [`features/`](features/README.md). Read the feature's file before driving it.

## Evidence

```bash
$S/evidence.sh shot workout-after               # screenshot
$S/evidence.sh record rest-end 8                # 8-second screen recording (foreground)
$S/evidence.sh frames rest-end 15               # split it into frames to inspect motion
$S/evidence.sh notifications countdown          # the notification shade
$S/evidence.sh theme dark                       # the app follows the system theme
```

Evidence goes to `$REBUILD_EVIDENCE` (default `/tmp/rebuild-verify`), outside the repo. Cleanup never deletes it. Look at every image you rely on (the Read tool shows PNGs); a screenshot nobody looked at proves nothing. For PR screenshots, upload with `.claude/skills/pull-requests/scripts/upload.sh` (see the `pull-requests` skill).

What counts as proof:
- Drive the real user path: taps, holds and typing through the UI. Not repository calls, not `adb` intents into internals, not test-only hooks.
- Capture the action and the state it leads to, not just a final screen: for example the held pill mid-drag, then the strip after the drop.
- Check side effects alongside the screen. A posted notification: `adb shell dumpsys notification --noredact | grep -A3 com.rebuild` (channel, flags such as `FLAG_INSISTENT`). A scheduled alarm: `adb shell dumpsys alarm | grep -A2 com.rebuild`. Saved data survives `adb shell am force-stop com.rebuild` and relaunch.
- Motion (rest-end flash, Up next, drags) needs a recording split into frames. The emulator renders in software and drops frames, so judge timing from the code and the curve tests, and the look from frames. Say "not verified on a real GPU" when smoothness matters.

## Cleanup

```bash
$S/emu.sh down      # stops only an emulator this skill started; stops Gradle daemons; keeps evidence
```

Also put back anything a run changed on a shared device: the system theme (`evidence.sh theme light` if you switched it) and font scale. Never `pkill` by name: a `pkill -f` pattern can match the shell running it and kill the command itself.

## Gotchas

- With the keyboard up, fields move. Tap by handle, never by coordinates taken before the keyboard opened.
- Compose dialogs and popup menus are separate windows; their test tags don't show as resource ids, so find their controls by text or description.
- A fresh install has no previous sets, so set entry starts empty; `flows.sh log-set` fills both fields.
- The device tests (`./gradlew connectedDebugAndroidTest`) uninstall the app, so its data is gone afterwards; run `emu.sh install` and the flow you need again.
- DocumentsUI (the system file picker used by backup and restore) differs between Android versions; `seed-history` is written for this API 34 image. It reopens in the last folder it showed, and its toolbar and drawer can both say "Downloads", so `seed-history` handles both starts and taps `title=Downloads`.
- `wait` and `gone` keep a real deadline, but a screen read in progress (about a second) can finish after it.

## Keeping this skill true

When the app changes, the map drifts. The `maintain-verification-skill` skill is the upkeep pass: it reads each feature from source, drives every feature live, and ships one PR of proven corrections to this directory only.
