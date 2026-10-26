# Changelog

Every version installs over the one before it with the same signing key and keeps all workout data. From 0.1.16
on, release APKs are built and published by [GitHub Actions](.github/workflows/release.yml); notes for each are in
[docs/releases](docs/releases/).

## 0.1.16 (version code 17)
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
