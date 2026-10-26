# Rebuild verification

This document separates automated checks from the physical Galaxy A71 acceptance
gate. Run evidence is filled in only after the corresponding commands complete.
The newest version comes first; earlier sections are preserved unchanged.

## 0.1.10 time, distance, cardio and the activity stopwatch

Version 0.1.10/code 11 builds on 0.1.9 commit `4907d19`. It is the last of
three approved stages: timed, distance and cardio exercises with their own
goals, and an activity stopwatch. These are unchanged: sets of reps with
their entry and prefill, kg, plates, bodyweight and assistance, drop and
rest-pause sets, rest after a set, the 0.1.8 extra workouts and plan edits,
the 0.1.9 routines and weekly schedule, and both themes with their colors.
Nothing is recommended or advanced automatically, and no pace, speed,
calories, combined totals or scores are calculated.

**How an exercise is measured.** Each exercise is measured one way for good:
sets of reps, timed sets, distance sets or cardio.

- **The library.** Every existing built-in and custom exercise is sets of
  reps. Catalog revision 2 adds 14 measured built-ins, for 74: five timed
  holds, two loaded carries and seven cardio activities. A custom exercise
  chooses its measure when it is created, and the measure can't change
  later. A planned exercise is swapped only for one measured the same way.
  The original routines' own slots stay sets of reps; a measured exercise
  joins a plan as an addition or a routine exercise.
- **Efforts.** A measured exercise logs efforts (`efforts`), kept apart
  from sets. An effort holds only what was measured; a measurement not
  taken is null, never zero. A timed set has its time, a distance set its
  distance and optionally its time, and cardio a time, a distance or both.
  Holds and carries take a load by the same rules as a set of that
  exercise (kg, assistance or whole plates as it allows, and none for a
  bodyweight hold); cardio never has one. Efforts are never part of a drop
  or rest-pause set, are never per leg and have no RIR.
- **Values.** Times are whole seconds from 1 s to 24 hours. A distance keeps
  the unit it was typed in (m, km or mi) as thousandths of that unit: at
  most three decimals, either decimal separator, up to 1,000 km or
  1,000 mi, and never rounded or converted. Different units are compared
  and added exactly in micrometres (a mile is exactly 1,609.344 m).
- **Entry.** The form offers the last effort's load and distance unit (this
  workout's, else the same position last time, in the same load unit),
  never a time or a distance. With no earlier distance or goal, the unit
  starts at metres for carries and kilometres for cardio; it is always
  visible and changeable.
- **Rest.** A timed or distance set starts the exercise's rest when logged.
  Cardio rests only when Start rest is tapped. History corrections and
  missed efforts never start a rest.

**Goals and weekly credit.**

- **Goals.** A timed or distance goal is a number of sets that each reach a
  minimum time or distance; both are given or neither. A shorter effort is
  logged and listed but doesn't count. A cardio goal is a total time, a
  total distance or both, and its efforts add up exactly across units.
- **Optional today, reviewed for the plan.** A measured exercise added
  today is optional like any added exercise and never blocks completion,
  even with a goal. Save to plan and routines need a complete goal, and
  Save to plan changes only future workouts of the phase, as for sets of
  reps. Logged times and distances never become goals.
- **Weekly credit.** The weekly rule is unchanged. A routine counts by
  itself only when each required exercise reaches its goal with real work.
  An effort is real work even when short of its goal, so it allows explicit
  credit on a finished planned workout. Extra workouts never count.

**The activity stopwatch.** Stopwatch beside Log times one effort at a time,
on a measured exercise of the active workout that isn't skipped.

- **Use.** Pause and Resume as often as needed; paused time never counts.
  Stop fills the time into the form for review. Nothing is logged and no
  rest starts until Log, and the effort keeps the stopwatch's ID. Discard
  asks first. Starting or resuming ends a running rest. While it runs,
  nothing else is logged in that workout and no drop or rest-pause set can
  be open, and its exercise can't be removed, skipped or swapped until the
  effort is logged or discarded. Finishing with an unlogged stopwatch
  effort needs "Discard the stopwatch's effort" ticked. Other exercises
  show a strip back to it, and other screens a bar with its time, Pause or
  Resume, and Open.
- **Clock.** It reads `SystemClock.elapsedRealtime()`, which keeps counting
  in deep sleep and ignores wall-clock and time-zone changes, with
  Android's boot count and the app process of each reading. Running time
  is trusted only on a known matching boot or within the same process, so
  leaving the app, locking the phone or Android ending the process loses
  no time on a phone that reports its boot count. After a reboot, a
  restored backup, or a new process when the boot count is unknown, the
  stopwatch stops without a time and asks for one; nothing is estimated.
  Measured time rounds down to whole seconds. A stopwatch past 24 hours
  keeps its reading, but its time must be typed.
- **Requests.** Each change names the stopwatch step it was made for, so a
  late or repeated Pause, Resume, Stop, Log or Discard changes nothing.
- **No alerts.** It has no notification, alarm or foreground service.

**Data.** The format is now schema 6 with backup envelope 6. Plan revision 1
is unchanged; the exercise catalog moves to revision 2.

- **Migration.** Schema-5 data gains the 14 measured built-ins and nothing
  else. Catalog revision 2 adds only missing IDs, within the 1,000-exercise
  limit, and never changes an existing or custom exercise. Every existing
  exercise and snapshot is sets of reps, with no efforts or measured goals,
  and there is no stopwatch. Older data first gets the unchanged earlier
  migrations. Every earlier value is kept, and a migrated database is
  marked as needing a backup, keeping a marker it already had.
- **Restore preview.** A file from 0.1.9 or earlier says that missing
  library exercises are added where needed, and mentions start dates only
  for files whose workouts get them (`startDatesAdded`). A file holding a
  stopwatch says how it comes back: a running one stopped, for its time to
  be confirmed; a stopped one ready to log or discard; a paused one at its
  time.
- **Rejecting mislabeled files.** A version 1–5 document containing any
  version-six field is rejected, even with default values: `measure`,
  `efforts`, `durationSeconds`, `distance`, `thousandths`, `unit`,
  `effortSeconds`, `effortDistance`, `totalSeconds`, `totalDistance`,
  `stopwatch`, `elapsedMs`, `runningSince`, `boot`, `session`, `stopped`,
  `needsConfirmation` and `step`.
- **Version-six validation.**
  - JSON types are exact: times, thousandths, readings and steps are whole
    numbers, measures and units are known names, and flags are true or
    false.
  - Every snapshot of an exercise is measured as its library entry is.
    Efforts fit the measure as above; a rep-based exercise has none, and a
    measured one has no sets, no RIR target and no original plan slot. At
    most 10,000 efforts per exercise, and sets and efforts share one ID
    space.
  - Goals fit the measure: timed and distance goals have a set count and a
    minimum or neither, cardio goals a total time, a total distance or
    both, and a required entry's goal is never empty.
  - The stopwatch belongs to the active workout and times a measured
    exercise that isn't skipped, under an ID nothing has logged. Its
    reading is at most 10^12 ms (about 31 years) and its step at most
    10^9. Needing its time entered implies stopped. Running, it has its
    start reading and process, with no rest or open drop or rest-pause set
    beside it; paused or stopped, it holds no boot count or process.
- **Older apps.** 0.1.9 refuses schema-6 data and version-six backups, as
  tested below. New backups need 0.1.10; it still restores version-five
  (0.1.9) and older backups.

### Test changes and inventory

All 375 distinct 0.1.9 methods are kept; none were removed. There are 65
new methods:

- **JVM (+52):** `MeasuresTest` 9, `MeasuredWorkoutTest` 14,
  `StopwatchReplayTest` 13, `MeasuredSnapshotCodecTest` 5 and
  `MeasuredPresentationTest` 11.
- **API 34 (+13):** `MeasuredDeviceTest` 12 and one
  `AccessibilityDeviceTest` validator case. The device tests cover holds,
  carries and cardio logged as typed, the stopwatch's time and rest rules,
  its strip and bar from elsewhere, recreation, a restart it can't trust, a
  stopwatch past 24 hours, Discard and Finish, History's missed effort and
  charts, the goal dialogs, and large-text, landscape and real-keyboard
  layouts. The validator case covers the measured entry, the running and
  stopped stopwatch, the discard question, History's measured charts and
  summary, and the missed-effort and effort-correction dialogs. API 33 runs
  the 12 device tests.

Existing tests changed only for the format bump and the larger catalog;
none was weakened:

- Codec, catalog, plate, flexible, group and routine tests expect version 6
  and catalog revision 2. The rejected lists shift by one, to version 7 and
  catalog revision 3, and version-six content relabeled 5 is refused.
  `PlateSnapshotCodecTest` also asserts that each altered document differs
  from the original.
- `TestSupport` builds real v1–v5 documents without version-six keys or
  revision 2's exercises (`versionSixKeys`, `beforeCatalogTwo()`,
  `v5Json()`), and `relabelled()` fails if nothing was relabeled.
- Expected migrations add revision 2's exercises and nothing else; the v3
  and v4 previews now report added content but no added start dates.
  `PlanMigrationTest` seeds data from before revision 2, the catalog counts
  are 74 (60 rep-based, 5 timed, 2 distance, 7 cardio), and
  `RoutineLibraryTest` asks the state for `confirmedExercises`, since what a
  row needs now depends on its exercise's measure.

Twelve deliberate production mutations to measured values and goals, the
stopwatch, rest after efforts, History corrections, backup restore and the
effort form were each turned red by the JVM tests and reverted, with every
source restored and hash-checked (`evidence/mutation`). The run used a git
archive of `ac39d91`: the unmutated baseline passed all 52 tests of the five
Stage 3 classes first, and each mutation then failed the counts below.

| Mutation | Failed | For example |
|---|---|---|
| Readings with an unknown boot count match | 2 | `stopwatchTrustsOnlyAKnownMatchingBootOrTheSameProcess` |
| A late reading stops the stopwatch | 2 | `theStopwatchCountsOnlyPerformedTimeAndStoppingLeavesAnUnsavedReview` |
| Resuming drops the time before a pause | 7 | `aPausedStopwatchKeepsItsTimeThroughRestartsAndAResumeAfterAReboot` |
| Measured time rounds up | 4 | `measuredTimeRoundsDownToWholeSeconds` |
| A too-precise distance isn't refused as such | 2 | `distancesParseExactlyWithEitherDecimalSeparatorAndNeverRound` |
| A timed set under its minimum counts | 3 | `timedAndDistanceSetsCountOnlyEffortsReachingTheMinimumExactly` |
| Cardio starts a rest | 2 | `effortsAreLoggedExactlyAsEnteredAndOnlyHoldsAndCarriesStartTheirRest` |
| A History correction is live | 1 | `aForgottenEffortIsAddedInHistoryWithoutTimersRestOrPlanChanges` |
| An untrusted running time keeps counting | 2 | `aStopwatchWhoseTimeCantBeTrustedStopsForATypedDurationWithNoEstimate` |
| A restored stopwatch keeps running | 1 | `aRestoredRunningStopwatchComesBackStoppedForItsTimeToBeEntered` |
| Version 5 may carry measured fields | 1 | `versionSixRoundTripKeepsGoalsEffortsUnitsAndTheStopwatchExactly` |
| A fresh form offers a distance | 1 | `freshEffortFieldsNeverOfferATimeOrADistance` |

Problems found during development and verification, each shown failing
before its fix and passing after (`reports/red-green`,
`reports/fix-distance-field`):

- **Stale stopwatch requests (production, `0892da9`).** Before requests
  named their step, a replayed Resume could count a paused stretch twice,
  restart a stopped review or end a newer rest; a stale recovery could stop
  a later resume, a stale Stop turn a later pause into a review and a stale
  Discard delete a resumed effort. Six `StopwatchReplayTest` cases failed;
  with the step check all ten passed.
- **A distance offered (production, `0892da9`).** A fresh carry form
  offered last time's 40 m ("expected [, , , M, 24] but was
  [, , 40, M, 24]"). It now offers only the load and the unit.
- **Log after Discard or delete (production, `fe6fdee`).** A stopwatch Log
  arriving after its review was discarded, or after its logged effort was
  deleted, logged the effort again and replaced the current rest: three of
  thirteen replay cases failed. The stopwatch's Log now logs only that
  stopwatch's own stopped effort and is otherwise refused with nothing
  changed. A replayed Log of an effort that still exists remains a no-op
  that keeps later corrections.
- **The unit after a time-only effort (production, `ec219dd`).** After
  1.5 mi and then a time-only interval, the form fell back to km, so a
  typed "1.5" would have been kilometres. The unit now comes from today's
  last effort with a distance, then the suggestion, then the goal, then the
  default.
- **No unit choice in landscape (production, `3ae8b46`).** The new
  landscape device test found that the landscape form had no m/km/mi
  choice, so a phone on its side logged distances only in the unit the form
  already held. The choice now follows each distance field, as in portrait.
  A test helper that applied the 48 dp target check to a plain text was
  then split into separate checks for controls and texts; no control lost
  its check.
- **A silent card edge (production, `cfa5a9f`).** The new validator case
  scrolled History's measured summary until only its bottom 8 px showed,
  and `SpeakableTextPresentCheck` reported an error: the merged card had
  nothing speakable in view. The card and each "All sessions" row now carry
  one complete spoken label, such as "Latest, Sep 20, 2026: 20 minutes,
  5 kilometres; 10 minutes, 1 mile. 1 session since Sep 20, 2026". Visible
  text is unchanged. The strength history's similar card was not
  re-audited.
- **A squeezed goal distance field (production, `ac39d91`).** The upgrade
  rehearsal on the first frozen candidate, `4c82a939` (commit `5da6c41`),
  showed the treadmill's Save to plan dialog with its Total distance field
  about 57 dp wide beside the m/km/mi choice and its label broken into
  "Tot" and "al". Every goal dialog of a distance or cardio exercise had
  it; values were still typed and saved correctly. The field now always
  spans the dialog with its unit choice underneath, as History's effort
  dialog already did.
  `MeasuredDeviceTest#distanceGoalsSpanTheirDialogWithTheUnitUnderneathTodayAndInThePlan`
  fails on the old layout and passes after. Its first passing build failed
  the test's own label check, which was then rewritten to require every
  character shown with breaks only between words, and failed again on the
  old layout. That candidate's results (JVM 303/0, API 34 136/0, light
  15/0, API 33 121/0 and the upgrade rehearsal) are archived as superseded,
  and every final run, the upgrade proof and the captures were redone on
  the new clean freeze.

Final unfiltered commands, one private emulator at a time, all with
`-Dorg.gradle.workers.max=2`:

```sh
./gradlew --offline --no-daemon --console=plain :app:clean
./gradlew --offline --no-daemon --console=plain :app:assembleDebug :app:assembleDebugAndroidTest
./gradlew --offline --no-daemon --console=plain --rerun-tasks \
  :app:testDebugUnitTest :app:lintDebug
ANDROID_SERIAL=emulator-5602 ./gradlew --offline --no-daemon --console=plain \
  :app:connectedDebugAndroidTest   # API 33 AVD
ANDROID_SERIAL=emulator-5600 ./gradlew --offline --no-daemon --console=plain \
  :app:connectedDebugAndroidTest   # API 34 AVD, dark theme
adb -s emulator-5600 shell am instrument -w -r -e class \
  com.rebuild.ui.AccessibilityDeviceTest,com.rebuild.ui.BackupAccessibilityDeviceTest \
  com.rebuild.test/androidx.test.runner.AndroidJUnitRunner   # API 34, light theme
```

All of them ran on the clean freeze of `ac39d91`. Source-to-XML
reconciliation gives **303 JVM + 137 API 34 native = 440 distinct tests**.
API 33 runs **122 native tests**, and the light-theme validator rerun
passes 15 of 15. Every report has zero failures, errors and skipped cases.
API 33 omits the fifteen API-34-only validator methods through the SDK
filter; they are not counted as skipped. Each Gradle run recorded the same
APK hashes before and after, and the light rerun used the installed APKs,
so every final run tested the same clean-built pair.

Lint has one new warning: **0 errors, 20 warnings and 3 information
findings**. The 22 earlier findings are unchanged by ID, severity, message
and file. The new one is `ModifierParameter` on `SetChart` in
`HistoryScreens.kt`: a defaulted format parameter comes before the
defaulted modifier. It is a parameter-order style warning with no effect
on behavior, and was left as is.

### Native accessibility checks

The fifteen API 34 validator tests throw on any ERROR result. Warnings and
information are kept in `atf-evidence.json` and not suppressed. The
deliberately unlabeled negative control is rejected in both themes, and the
rest-bar buttons still export 48 dp provider bounds.

- **Dark (final Gradle suite):** 0 errors, 36 warnings, 95 information.
  - 34 warnings are Material 3 disabled text at 38%: 0.1.9's 26 and 8 in
    the new case, on the docked measured entry's Finish, selected unit,
    Stopwatch and Log while a tap's action was being saved. They share the
    set entry's busy guard. The test waits for each control to be enabled
    before tapping it but doesn't assert this busy state, and its count
    depends on timing (4 in the light run).
  - 2 warnings are the "Selected" labels on the current Set type's check
    icon, retained for the reason given in 0.1.7.
  - The information results are repeated non-interactive text. The new
    case adds 8: "Upper" and "Not done" repeated on Today, and the History
    title.
- **Light (same fifteen tests rerun on API 34):** 0 errors, 138 warnings,
  92 information.
  - 103 warnings are estimates against the `#79736B` field outline, 52 of
    them new, on the measured entry and the effort dialogs. Pixel samples
    of the value text `#1F1B16` measure 15.19:1 on the entry's `#F3F1EE`
    and 14.28:1 on the dialog's `#EDEAE6`.
  - 33 warnings are disabled text (0.1.9's 29 and 4 of the busy state) and
    2 are the "Selected" icons.

Per method, severity and check, the fourteen 0.1.9 methods report exactly
their 0.1.9 counts in both themes (`atf-evidence-0.1.9.json`); every
addition comes from the new case. `ScrollEdgeAccessibilityTest` again logs
the whole 126 px (48 dp) provider bounds of Plan's three phase chips and
five landscape rail pills with a row scrolled under them.

### Native captures

The 73 captures (45 dark, 10 landscape, 10 at 360 dp with 200% text, 8
light) used the exact tested APK on the private API 34 AVD. Data was the
0.1.9 captures' end state, migrated by the first 0.1.10 launch, with the
clock set to Tuesday 6 October 2026, 18:00. Every value was typed through
the UI, and all 415 actions, the last restoring host time, are logged in
`capture-method/interactions.jsonl`. They show:

- History's missed effort: the treadmill added to a past workout as a
  forgotten exercise, with 18 minutes and 2.6 km typed, "added · no
  target".
- Tuesday's Upper: a chest press set, the picker's Timed filter, then the
  plank added as optional with no target, a typed 45 s set and its rest,
  and a stopwatch set timing, stopped at 0:37, reviewed and logged.
- The farmer's carry with its m/km/mi choice, 24 kg per dumbbell and 40 m.
- The treadmill's stopwatch timing, with its strip and bar from the carry
  and from Today.
- The same views in landscape, at 200% text and in light, with the
  stopwatch timing: Today, the timing treadmill, the stopwatch from the
  carry, the paused-stopwatch strip, the carry's distance with the
  keyboard, Save to Upper plan, and History's forgotten effort and
  treadmill history.
- The treadmill effort stopped after those passes, with 24:00 and 3.4 km
  typed over the measured time: "Your typed time is logged instead.", with
  the measured time offered.
- Goal and rest for this workout (30 minutes in total, then 24:00 of
  30:00), Save to Upper plan with total time and distance, and the plan
  line for future Intro 2 workouts.
- A second stopwatch effort discarded after the question, Finish partial
  workout with the count box unticked, and Today's plan with the
  treadmill's goal.
- History of 6 October: the carry's distance set, an effort's correction
  dialog and the treadmill's history over 3 sessions.
- A routine, Conditioning, with timed, distance and cardio goals, started as
  an extra session and finished: plank 0:50, rowing 9:40 and 2 km. Today's
  week then shows Upper partial and not counted, and "Outside the plan this
  week: 1 extra workout".

`end_state_check.py` compares the pulled end-state database with the seed,
re-deriving the 5→6 migration. 34 of the 36 seeded workouts are unchanged;
two past workouts only gained the treadmill as a forgotten exercise, with
their credit unchanged. Tuesday's Upper is partial and not counted, the
plan has the treadmill for future Intro 2 Upper workouts, and the week
reads Done 0 of 5 before and after. There is no active workout, stopwatch
or rest, and every set and effort ID is unique.

This is one clean run with no resume. Before it, the scenario was tried in
batches with the same APK, data and clock; every problem found then was in
the capture tool, none in the app (`capture-method/authoring-trial`). Three
scrolled views were not taken because the capture before already showed
their labels whole: light-07, light-10 and dark-37.

Known appearance items:

- At 360 dp with 200% text and the carry's Distance field focused, "Log
  distance set" is under the keyboard (large-05). The keyboard's Next moves
  through the fields, Done on the last one logs, and the form scrolls. At
  411 dp with 200% text, the device test's logs place Log above the
  keyboard for each field, though that test asserts only the focused field;
  the landscape test asserts Log too.
- At 200% text the paused-stopwatch strip shortens its label with an
  ellipsis (large-04); the hierarchy has the whole text.
- History's session lists were captured only until "All sessions" was
  whole, so few session rows show.
- A light strip sits behind the gesture handle: 63 px of the window
  background `#FAF8F5` from `themes.xml`, on every screen without the
  keyboard, in both orientations and both themes; dialogs dim it. It is the
  same in every delivered capture set back to 0.1.5. Earlier notes
  mentioned it only in landscape, which understated it.
- Not new: the exercise picker dims the status bar, and rest bars show
  "Alerts off" because notifications weren't granted.

### Actual installed upgrade and backup evidence

The proof ran on the private API 33 AVD with the real Files (DocumentsUI)
picker and a real reboot. The superseded candidate's rehearsal was replayed
step by step on the final APK (`evidence/method`), each tap finding its
label in a fresh screen dump and the stopwatch steps at the rehearsal's
spacing. Seven rehearsal rows, a mis-tap and a Back for a stacked activity,
were left out. `verify_upgrade_0110.py` derives every ID and time from the
data, asserts every point below from the pulled databases, backups, screen
hierarchies and logs, and writes `upgrade-0110.json`. It passes on an
unaltered copy and fails in all 16 altered cases
(`upgrade-negative-control.txt`), among them:

- a 0.1.9 bench set one rep higher;
- a paused reading two seconds more than its tap allows, or the paused
  time counted;
- Log starting a rest, or today's treadmill made required by Save to plan;
- the running time trusted across the reboot, or a restored stopwatch
  running;
- 4:11 saved for the 4:10 typed;
- the screen after a lock showing 30 s less;
- Count Upper as done ticked in advance;
- one changed byte in the version-six backup or in two databases;
- the rehearsal's carry mis-tap put back, or the stopwatch's Stop recorded
  as left out;
- from the superseded candidate's own screen, its squeezed distance field.

1. **Starting state.** The 0.1.9 capture fixture: 36 workouts, kg and
   plates history, a custom exercise, a plan addition and removal, a
   library routine and its scheduled copy, extras and explicit credit.
   0.1.9's own UI then started Upper, logged 23.5 kg/DB × 9, left a
   rest-pause set of 5 plates × 12 then × 5 open and exported a
   version-five backup.
2. **Install.** The final 0.1.10 APK went on with `adb install -r`. The
   user ID, first-install time and signature were unchanged, and the
   database was byte-identical after installation.
3. **First launch.** Schema 5→6 (revision 135→136) added the 14 measured
   exercises (61→75) and nothing else: every existing exercise and snapshot
   is sets of reps, with no efforts or measured goals, and there is no
   stopwatch. Every 0.1.9 set and stage is identical.
4. **The open rest-pause set.** It resumed. A third stage (5 plates × 3) was
   added, and Finish set started a rest exactly 90,000 ms after
   `finishedAt`; the countdown showed 87 s.
5. **Added exercises.** A plank of 45 s and a farmer's carry of 40 m at
   24 kg per dumbbell were added to Upper as optional, with no target.
   Upper's own targets were unchanged.
6. **The stopwatch.** The treadmill's stopwatch ran on boot 7. Its Start,
   Pause, Resume and Stop readings each fall inside their tap's bracket of
   `/proc/uptime`, and the 46.5 s paused stretch was not counted. Two
   leave, lock and reopen cycles (the screen off for 30 s in the first)
   and a force-stop relaunch left the stored stopwatch unchanged, with one
   activity after each reopen, and every time shown matched the clock.
   Stop logged nothing, and the stopped review survived a relaunch. Log
   saved 404 s and 1.5 km under the stopwatch's ID, with no rest.
7. **A manual effort.** A distance-only 0.5 mi effort kept its own unit.
8. **Save to plan.** For future Intro 2 Upper workouts: the treadmill with
   20:00 in total and the plank 3 × at least 0:30, each with 90 s rest.
   Today's entries stayed optional.
9. **Finish.** With Count Upper as done unticked, the workout finished
   partial and not counted. On the week, Upper went from in progress to
   partial, and the other four rows were unchanged.
10. **History.** A missed treadmill effort of 10:00 and 2 km was added to
    the finished Upper with no rest, stopwatch or plan change.
11. **Reboot.** An extra workout with the rowing machine's stopwatch
    running was exported as a version-six backup. After a real reboot
    (boot 7→8), the app's existing BOOT_COMPLETED rest-recovery receiver
    started its process without writing. Opening the app stopped the
    stopwatch without a time and asked for one; 4:10 was typed and logged,
    with no rest.
12. **Backups.**
    - The 0.1.9 version-five backup and the 0.1.10 version-six backup each
      equal their database's domain state; the version-six file holds the
      running stopwatch.
    - 0.1.10 restored the 0.1.9 file, which became the same domain state as
      the first-launch migration, with the rest-pause set open again.
    - It restored its own version-six file exactly, except that the running
      stopwatch came back stopped, for its time to be entered.
    - The version-six file relabeled 5 (two bytes changed) was refused, and
      the database stayed byte-identical.
    - Each restore came after the preview, a mandatory safety export and
      confirmation, and each safety export equals the domain state before
      replacement.
13. **Older app.** After a debuggable downgrade that kept data
    (`install -r -d`), 0.1.9 refused the schema-6 database, leaving it
    unchanged. Reseeded with its own pre-upgrade database, it rejected the
    version-six backup, again leaving the database unchanged.

All 439 earlier sets are unchanged in every state, with 1 new set and 6
efforts. Efforts, the stopwatch, measured goals and the missed effort were
all created after the upgrade, because 0.1.9 cannot make them. Force-stop
and reboot recovery show that state persists and that an untrusted time is
never estimated, not that anything alerts while the stopwatch runs.

### Exact artifact and limits

Production was frozen at `ac39d91` and cleanly rebuilt. Every final test,
the captures and the upgrade proof used **11,796,639 bytes**, SHA-256
`a4d9d0e5fe71befadbcc75e737a87c5906c0ce7ab5036a466cba0f10d7a3f761`, signed
with the unchanged Android Debug certificate
`d834bba6e23b6dc1dfab6f213ec474d0ec02dbd2699e1012824757c94642365b`. The
final androidTest APK is `5985c36f…` (5,299,628 bytes). Later commits change
documentation only.

The earlier candidate `4c82a939` (commit `5da6c41`, androidTest `d7f254fe…`)
is superseded, and its results are archived separately. No network
permission, key, personal PDF or competitor artwork is included, and all
data is synthetic. The pause and square icons are Lucide 0.468.0, compared
path for path with the pinned upstream SVGs (`docs/icons.md`).

Known limits:

- **No alert while timing.** The stopwatch has no notification, alarm or
  foreground service, so nothing alerts or shows outside the app while it
  runs. The emulator doesn't truly suspend its CPU, so counting through
  deep sleep rests on the clock's documented behavior, not on a sleeping
  phone. Force-stop and reboot evidence shows persistence and recovery
  only.
- **Keyboard at 200% text.** On a 360 dp screen the carry's Log is under the
  keyboard while Distance is focused, though the keyboard's Next and Done
  still reach logging. At 411 dp it was above, by the device test's logs
  only. Dialogs still pan for the keyboard as in 0.1.8.
- **Truncation.** At 200% text the paused-stopwatch strip ellipsizes its
  label.
- **Gesture strip.** The light window background shows behind the gesture
  handle in both themes (see Native captures). It is not new and is left
  for a possible follow-up.
- **Wording.** The routine editor's introduction still speaks only of
  working sets and a rep range; a measured exercise asks for its own goal.
- **Accessibility.** The busy-state warning count depends on timing, and
  the strength history's merged card was not re-audited at a partial
  scroll.
- **Earlier limits.** The 0.1.9 notes on landscape dialogs, the restore
  preview and Today at 200% text still apply.
- **Not yet checked on the phone.** Emulator runs are not acceptance on the
  physical Galaxy A71, the Samsung keyboard, one-handed reach,
  locked-screen alerts or TalkBack speech.

## 0.1.9 your routines and the weekly schedule

Version 0.1.9/code 10 builds on 0.1.8 commit `381535a`. It is the second of
three approved stages: routines of the user's own and explicit weekly
scheduling. Duration, distance and a stopwatch (Stage 3) are not part of it.
These are unchanged: set entry and prefill, kg, plates, bodyweight and
assistance, drop and rest-pause sets, rest, the 0.1.8 extra workouts, added,
skipped and History-corrected exercises and plan edits, both themes and the
layout. Nothing is recommended or advanced automatically.

**Three separate records.** A library routine, a scheduled copy and a workout
never share an identity, and a name is never one.

- **Library routines** (`routines`) are shared by every phase and scheduled
  nowhere until the user adds them. Each holds 1–100 distinct exercises, each
  with confirmed working sets, a rep range, rest and the per-leg choice, and
  never a load. Saving an edit replaces the routine in place. Deleting one
  keeps every copy and workout and only drops the copies' link
  (`routineId` becomes null).
- **Scheduled copies** (`placements`) are taken when a routine is added to a
  phase's week: their own name and weekday, with exercises kept as plan
  additions of the copy's ID. 0.1.8's plan add, remove and Save to plan act
  on a copy as on an original routine, and Edit routine changes that copy
  only. Library edits never reach a copy; Update from library replaces its
  name and exercises after confirmation, and is offered only when they
  differ. A copy never changes phase.
- **Workouts** started from a copy record the copy's ID as their routine, so
  each copy has its own checklist entry and credit. Start as extra session
  runs a library routine outside the plan: named after it, with its
  exercises as optional ones carrying its goals. It never counts, even after
  the routine is scheduled.

**The weekly schedule.** A phase's week is its original routines not taken off
plus its live copies, by weekday.

- **Choices are explicit.** Adding a routine to, moving a copy to, or putting
  an original back on a day that already has a routine must choose Add
  alongside or name the one routine it Replaces; a request without a choice
  is refused. Repeating a request changes nothing, and reusing its ID for a
  different phase, day or routine is refused.
- **Taking off.** An original taken off or replaced becomes a
  `ScheduleRemoval`, keeps its plan and can be put back. A copy taken off
  loses its future exercises and is kept, marked `removedFromPlan`, only
  while workouts refer to it. A week always keeps at least one routine, so
  the last one can only be replaced.
- **Which copy.** Starting a library routine whose copies are on this week
  asks which day's workout it is, or an extra session, with nothing
  preselected. Two copies on one day read "1 of 2" and "2 of 2". Today
  features the first unfinished routine scheduled for today and names the
  others.

**Weekly credit.** The 0.1.8 rule is unchanged and applies per scheduled
routine. Schedule changes re-derive the checklist at once: adding a routine
to a complete week makes it incomplete, and taking one off removes its row. A
finished workout whose routine has left the week stays finished but no longer
ticks; an original's credit returns when it is put back. Workouts never
change.

**Save as routine.** From a finished workout in History, the editor opens with
its exercises in workout order and only the targets and rest that workout
had. Logged loads and reps never become targets, every row must have
confirmed targets before saving, and the workout and its credit never change.

**Data.** The format is now schema 5 with backup envelope 5. Plan revision 1
and the exercise catalog are unchanged.

- **Migration.** Schema-4 data gains only empty `routines`, `placements` and
  `scheduleRemovals`. Older data first gets the unchanged 0.1.5–0.1.8
  migrations. Every earlier value is kept, and a database migrated on first
  launch is marked as needing a backup.
- **Rejecting mislabeled files.** A version 1–4 document containing any
  version-five field (`routines`, `placements`, `scheduleRemovals`,
  `routineId`, `removedFromPlan`) is rejected, even with default values.
- **Version-five validation.**
  - JSON types are exact: sets, reps, rest and weekdays are whole numbers,
    flags are true or false, and a copy's link is text or null.
  - At most 100 routines and 1,000 copies. Routines, copies and plan routines
    never share an ID. A routine's exercises exist, are distinct and have
    valid targets. A copy's weekday is 1–7 and its link names an existing
    routine or none.
  - A copy taken off keeps no future exercises. Removals are listed once and
    belong to a routine available in their phase, and every phase's week
    keeps at least one routine.
  - A workout may refer to a copy of its own phase, even one taken off.
- **Older apps.** 0.1.8 refuses schema-5 data, as tested below.

### Test changes and inventory

All 339 distinct 0.1.8 methods are kept; none were removed. There are 36 new
methods:

- **JVM (+24):** `RoutineLibraryTest` 7, `RoutineSnapshotCodecTest` 6 and
  `WeeklyScheduleTest` 11.
- **API 34 (+12):** `RoutinesDeviceTest` 8, two `AccessibilityDeviceTest`
  validator cases and two `ScrollEdgeAccessibilityTest` cases. The validator
  cases cover the routine library, start chooser, schedule question and
  editor, and Plan's copies, options, Change day question and taken-off
  list. The device tests include large text, landscape and real-keyboard
  layouts.

Existing tests changed only for the format bump or one moved control; none
was weakened:

- Codec, catalog, plate, flexible and group tests expect version 5. The
  rejected lists shift by one and add version-five content relabeled 4.
  `TestSupport` builds real v1–v4 documents without version-five keys
  (`versionFiveKeys`, `v4Json`).
- `ActionAffordanceTest` now scrolls to Change phase, which Today's new Your
  routines link moved below the fold, checks its 48 dp height and scrolls
  back up for Rest day (commit `c5f0a10`, test only). The first full API 34
  run of the pre-freeze build failed there once; that report is kept.

Ten deliberate production mutations were each turned red by the JVM tests
and reverted, with every source restored and hash-checked
(`evidence/mutation`). The final check ran on a git archive of `731130c`:
the unmutated baseline passed all 24 tests of the three Stage 2 classes
first, and each mutation then failed the counts below. A first run, on the
working tree before the Stage 2 commits, used the same five mutated files
and also killed all ten. It predates
`schedulingIntoACompleteWeekMakesItIncompleteAndKeepsEveryFinishedWorkout`,
which fails under two of the mutations, so those two counts were 8 and 1
there.

| Mutation | Failed | For example |
|---|---|---|
| A repeated request ignores the weekday | 2 | `schedulingOnAFreeDayAddsAnIndependentCopyAndRepeatingTheRequestChangesNothing` |
| A copy taken off keeps its future exercises | 9 | `movingTakingOffAndPuttingBackKeepIdentityCreditAndEveryWorkout` |
| Deleting a routine deletes its copies | 5 | `deletingARoutineKeepsItsScheduledCopiesAndWorkoutsAndOnlyDropsTheLink` |
| A taken day needs no choice | 2 | `aTakenDayNeedsAnExplicitChoiceAndReplaceTakesOffExactlyThatOccurrence` |
| Copies taken off still count as scheduled | 6 | `aDayWithoutALiveOccurrenceIsAnHonestRestDayAndScheduleChangesApplyAtOnce` |
| Today features a finished routine | 2 | `twoCopiesOnOneDayAreTwoOccurrencesAndTodayOffersTheRemainingOneFirst` |
| Rest ignores saved routines | 1 | `restForAnAddedExerciseFallsBackToSavedRoutinesBeforeTheVisibleDefault` |
| The codec lets a taken-off copy keep exercises | 1 | `versionFiveStructureRulesAreEnforcedEachByItsOwnRule` |
| Older documents may carry copies | 1 | `olderDocumentsRejectEveryVersionFiveFieldEvenWithDefaultValues` |
| The codec allows an empty week | 1 | `versionFiveStructureRulesAreEnforcedEachByItsOwnRule` |

Problems found during verification, all resolved:

- **Fixed controls over a scrolled row (production, `7c3b9d8`).** The first
  API 34 run of the new Plan validator case reported Plan's three phase
  chips as 24 dp tall. Compose stretches the accessibility bounds of a row
  scrolled partly under a body's top edge up to 24 dp past that edge, and
  the scrolling body, as the later sibling, claimed that strip first. The
  chip Intro 1 exported 73 of its 126 px, and in landscape a workout rail
  pill lost its lower 29 px. TalkBack, Switch Access and the validator all
  read those bounds. Scrolling bodies under fixed controls now sit beneath
  them in z-order; column siblings never overlap, so nothing visible,
  tappable or in focus order changes. `ScrollEdgeAccessibilityTest` scrolls
  a row under each control and asserts its whole 48 dp provider bounds. It
  fails without the change and passes with it. The red report and the eight
  diagnostic iterations that shaped the test are kept.
- **A taken day's question out of view (production, `731130c`).** The
  upgrade-proof screenshots of the first frozen candidate, `bff8e4f2` (commit
  `9c40b27`), showed a real defect on the most common scheduling path. In
  Add to a weekly plan, picking Saturday (which has Upper) disables the
  confirm and asks Add alongside or Replace Upper below the seven days, and
  on a 1080×2400 screen that question was out of view, so the dialog looked
  stuck. Change day's Replace row was also partly clipped. After the next
  frame the question and its choices are now brought into view, and the
  question is a polite live region; what it does is unchanged.
  `RoutinesDeviceTest#aTakenDayLowInTheListBringsItsQuestionIntoView` fails
  on `bff8e4f2` and passes after. That candidate's results (JVM 251/0, API 33
  109/0 and a full upgrade proof) are archived as superseded, and every final
  run, the upgrade proof and the captures were redone on the new clean freeze.

Final unfiltered commands, one private emulator at a time, all with
`-Dorg.gradle.workers.max=2`:

```sh
./gradlew --offline --no-daemon --console=plain :app:clean
./gradlew --offline --no-daemon --console=plain :app:assembleDebug :app:assembleDebugAndroidTest
ANDROID_SERIAL=emulator-5602 ./gradlew --offline --no-daemon --console=plain \
  :app:connectedDebugAndroidTest   # API 33 AVD
./gradlew --offline --no-daemon --console=plain --rerun-tasks \
  :app:testDebugUnitTest :app:lintDebug
ANDROID_SERIAL=emulator-5600 ./gradlew --offline --no-daemon --console=plain \
  :app:connectedDebugAndroidTest   # API 34 AVD, dark theme
adb -s emulator-5600 shell am instrument -w -r -e class \
  com.rebuild.ui.AccessibilityDeviceTest,com.rebuild.ui.BackupAccessibilityDeviceTest \
  com.rebuild.test/androidx.test.runner.AndroidJUnitRunner   # API 34, light theme
```

All of them ran on the clean freeze of `731130c`. Source-to-XML
reconciliation gives **251 JVM + 124 API 34 native = 375 distinct tests**.
API 33 runs **110 native tests**, and the light-theme validator rerun passes
14 of 14. Every report has zero failures, errors and skipped cases. API 33
omits the fourteen API-34-only validator methods through the SDK filter;
they are not counted as skipped. Each Gradle run recorded the same APK
hashes before and after, and the light rerun used the installed APKs, so
every final run tested the same clean-built pair.

Lint is unchanged from 0.1.8: **0 errors, 19 warnings and 3 information
findings**, the same 22 findings by ID, severity, message and file.

### Native accessibility checks

The fourteen API 34 validator tests throw on any ERROR result. Warnings and
information are kept in `atf-evidence.json` and not suppressed. The
deliberately unlabeled negative control is rejected in both themes, and the
rest-bar buttons still export 48 dp provider bounds.

- **Dark (final Gradle suite):** 0 errors, 28 warnings, 87 information.
  - 26 warnings are Material 3 disabled text at 38%: the 14 states of 0.1.8,
    and 12 dialog confirms that wait for a choice (Start in the start
    chooser, Add to a week while a taken day is unanswered, and Move in
    Change day). Each disabled state is asserted by its test.
  - 2 warnings are the "Selected" labels on the current Set type's check
    icon, retained for the reason given in 0.1.7.
  - The information results are repeated non-interactive text. The new
    methods add 36, such as "Same as this routine" on each weekly copy and
    repeated weekday names.
- **Light (same fourteen tests rerun on API 34):** 0 errors, 82 warnings,
  84 information.
  - 51 warnings are estimates against the `#79736B` field outline, 4 of
    them on the routine editor's target fields. Pixel samples of the value
    text `#1F1B16` on `#F3F1EE` measure 15.19:1.
  - 29 warnings are disabled text (the same 12 new confirms) and 2 are the
    "Selected" icons.

Per method, severity and check, the twelve 0.1.8 methods report exactly
their 0.1.8 counts in both themes (`atf-evidence-0.1.8.json`).
`ScrollEdgeAccessibilityTest` is not a validator method and also runs on
API 33. On API 34 it logs the provider bounds of Plan's three phase chips
and five landscape rail pills with a row scrolled under them: each is the
whole 126 px (48 dp).

### Native captures

The 75 captures (46 dark, 10 landscape, 10 at 360 dp with 200% text, 9
light) used the exact tested APK on the private API 34 AVD. Data was the
0.1.8 captures' end state, migrated by the first 0.1.9 launch, with the
clock set to Saturday 3 October 2026. Every value was typed through the UI,
and all 465 actions are logged in `capture-method/interactions.jsonl`. They
show:

- Your routines from Today and the empty library. A new routine: picker
  search, required targets, the picker's "in this routine" mark, a
  duplicate pick opening the existing exercise, remove with Undo, reorder.
- The routine page. Add to a weekly plan on Saturday, which already has
  Upper, answering Add alongside; then Wednesday, a rest day.
- Today's "Also on Saturday" line and the week list with both copies.
- The start chooser, and the Saturday copy logged in kg per dumbbell,
  plates and kg with the routine's 1:30 rest. It finished counted once:
  "Done 3 of 6".
- A library edit that marks both copies as differing, then an explicit
  Update of the Wednesday copy only.
- Plan: copy notes and options, Change day from Wednesday to Sunday
  replacing Lower B, putting Lower B back alongside, and taking off the
  Saturday copy. Today's week then reads "Done 2 of 5" and lists the
  Saturday workout as off this phase's checklist.
- Save as routine from History, then deleting Arms and delts from the
  library while its Sunday copy stays on Plan.
- Ten views each in landscape, at 200% text and in the light theme: Today,
  the library, the routine page, its weekly copies, the start chooser, both
  day questions, the targets dialog with the keyboard, a copy's options and
  Save as routine. The light theme has no separate weekly-copies capture,
  since its routine page shows both copies whole.

`end_state_check.py` compares the pulled end-state database with the seed.
It has one new workout, counted automatically, and the 35 seeded workouts
unchanged. The library holds only the routine saved from History. The
Saturday copy is kept without exercises because that workout refers to it;
the Sunday copy keeps its three. Lower B is back on Sunday.

This is the second capture run. The first, on the same APK, data and clock,
was replaced for capture-authoring defects, none of them in the app: three
repeated frames, a landscape field typed as "102" and a cut subtitle. The
second run repeated one frame, light-04 matching light-03. It was stopped
before any change to data, the driver learned to skip a capture whose
labels the previous one already shows whole, and it resumed with the light
theme.

Known appearance items:

- A targets dialog keeps its "Enter working sets" message after the field is
  corrected, until the next Add checks again. 0.1.8's Save to plan dialog
  does the same.
- Landscape dialogs scroll with no cue, and long names in Today's preview
  rows break mid-word at 200% text (see the limits below).
- Not new in 0.1.9: the light strip behind the landscape gesture handle, and
  the exercise picker dimming the status bar.

### Actual installed upgrade and backup evidence

The proof ran on the private API 33 AVD with the real Files (DocumentsUI)
picker. The superseded candidate's rehearsal was replayed step by step on
the final APK (`evidence/method`), each tap finding its label in a fresh
screen dump; the two swipes the rehearsal needed only to reach the taken-day
question were left out. `verify_upgrade_019.py` derives every ID and time
from the data, asserts every point below from the pulled databases, backups,
screen hierarchies and interaction log, and writes `upgrade-019.json`. It
passes on an unaltered copy and fails in all 17 altered cases
(`upgrade-negative-control.txt`), among them:

- an extra session from a routine marked counted;
- credit forged on the partial Upper;
- a library edit reaching the Saturday copy;
- the taken-off copy put back on the week;
- a set or drop stage written by 0.1.8 changed;
- a changed goal in the version-five backup;
- one changed byte in two databases;
- "Done 4 of 6" on screen;
- the taken-day question preselected or confirmable before a choice and,
  from the superseded candidate's own screens, out of view or reached by a
  swipe.

1. **Starting state.** The 0.1.8 capture fixture: 35 workouts, kg and plates
   history, a custom exercise, a plan addition and removal, extras and
   explicit credit. 0.1.8's own UI then started Upper, logged 24 kg/DB × 8
   @ 2 RIR, left a drop set of 4 then 3 plates open and exported a
   version-four backup.
2. **Install.** The final 0.1.9 APK went on with `adb install -r`. The user
   ID, first-install time and signature were unchanged, and the database was
   byte-identical after installation.
3. **First launch.** Schema 4→5 (revision 114→115) added only empty
   `routines`, `placements` and `scheduleRemovals`.
4. **The open drop set.** It resumed. A third stage (2 plates × 6 @ 0) was
   added, and Finish set started a rest exactly 90,000 ms after `finishedAt`.
   The countdown matched the deadline, also after a force-stop and reopen.
5. **A library routine.** Arms and delts was built in the editor: Dumbbell
   curl 2 × 10–15, Cable face pull 1 × 12–15 and Cable triceps pushdown
   2 × 10–15, each with 90 s rest. Every row needed confirmed targets, picking
   curl again opened its row, and a reorder and a removal with Undo worked.
6. **An extra session.** Start as extra session gave an extra workout named
   after the routine, with its three goals on optional entries. It logged
   16 kg/DB × 12 @ 2 and 5 plates × 13 @ 2 and finished uncounted.
7. **Scheduled copies.** The routine went on Intro 2's Saturday, alongside
   Upper, and on Wednesday. Starting it asked which day's workout it was,
   with nothing preselected. The Saturday copy's workout counted
   automatically: Done 3 of 6. Right after a taken day was picked, in the
   schedule dialog and in Change day, the question was whole on screen
   without scrolling, nothing was chosen, and the confirm waited for a
   choice.
8. **Edits.** A library edit (face pull 2 × 10–15) reached no copy. Update
   from library, confirmed on the Wednesday copy only, replaced its
   exercises and kept its plan IDs; the Saturday copy and its finished
   workout were unchanged.
9. **Schedule changes.** Change day moved the Wednesday copy to Sunday,
   replacing Lower B, which was marked taken off with its routine unchanged.
   Put back returned Lower B alongside the copy. Taking off the Saturday
   copy, which a workout refers to, removed its future exercises and kept it
   marked: Done 2 of 5, with its workout listed as also this week, off the
   checklist.
10. **Save as routine and deletion.** Arms finisher was saved from the
    finished copy workout in History with that workout's own targets, and
    the workout didn't change. Deleting Arms and delts kept both copies with
    their own settings and cleared their link.
11. **Persistence and totals.** A workout on the Sunday copy was started, and
    a force-stop and reopen left the database byte-identical. All **434
    earlier sets are unchanged**, with 9 new ones.
12. **Backups.**
    - The 0.1.8 version-four backup and the 0.1.9 version-five backup each
      equal their database's domain state. The version-five file keeps the
      routines, the copies, the taken-off copy and the active workout.
    - 0.1.9 restored the 0.1.8 file, which became the same domain state as
      the first-launch migration, with the drop set open again.
    - It then restored its own version-five file exactly, and the copy's
      workout resumed.
    - A version-four file with routines added was refused, and the database
      stayed byte-identical.
    - Each restore came after the preview, a mandatory safety export and
      confirmation. Each safety export equals the domain state before
      replacement, and no rest alert was restarted.
13. **Older app.** After a debuggable downgrade that kept data
    (`install -r -d`), 0.1.8 refused the schema-5 database, leaving it
    unchanged. On its own version-four database it rejected the version-five
    backup, again leaving the database unchanged.

Routines, copies, schedule changes and Save as routine were all created after
the upgrade, because 0.1.8 cannot make them. Force-stop recovery shows that
state persists, not that rest alerts are delivered.

### Exact artifact and limits

Production was frozen at `731130c` and cleanly rebuilt. Every final test, the
captures and the upgrade proof used **11,516,820 bytes**, SHA-256
`1acb906412c8703f3f04dc11823e6249a1e5761b5f8f045d271e09dfbd00a4c6`, signed
with the unchanged Android Debug certificate
`d834bba6e23b6dc1dfab6f213ec474d0ec02dbd2699e1012824757c94642365b`. The
final androidTest APK is `e7853177…` (5,261,088 bytes). Later commits change
documentation only.

The earlier candidate `bff8e4f2` (commit `9c40b27`, androidTest `440dda6e…`)
is superseded, and its results are archived separately. No network
permission, key, personal PDF or competitor artwork is included, and all
data is synthetic.

Known limits:

- **Dialogs and the keyboard.** As in 0.1.8, dialogs pan for the keyboard,
  and at 200% text the lower buttons can sit behind the keyboard until it is
  closed or the body scrolled. The 0.1.8 note on a newer Gboard's taller
  landscape number pad also still applies.
- **Restore preview.** It lists routines, copies and originals taken off,
  but still not plan-edit counts.
- **Landscape dialogs.** A dialog body taller than the screen scrolls with
  no visible cue. In landscape, two new choices are below the fold until the
  body is scrolled: the start chooser's third choice, Extra session, and the
  day dialogs' Replace choice. The day question itself names both choices
  ("Add this as another workout that day, or replace one"), and its confirm
  stays off until one is chosen. Every dialog shares this body, unchanged in
  0.1.9. In portrait at the default text size the question and both choices
  are whole on screen; at 200% text the Replace row shows partly.
- **Today at 200% text.** On a 360 dp screen, long exercise names in the
  Today card's preview rows can break mid-word. The row is unchanged in
  0.1.9.
- **Not yet checked on the phone.** Emulator runs are not acceptance on the
  physical Galaxy A71, the Samsung keyboard, one-handed reach, locked-screen
  alerts or TalkBack speech.

## 0.1.8 flexible workouts and plan editing

Version 0.1.8/code 9 builds on 0.1.7 commit `662b127`. It is the first of
three approved stages: extra workouts, optional exercises added to a planned
workout, skipping today, History correction and edits to the future plan.
Reusable custom routines and scheduling (Stage 2) and duration, distance and
a stopwatch (Stage 3) are not part of it. These are unchanged: set entry and
prefill, kg, plates, bodyweight and assistance, drop and rest-pause sets,
rest, the checklist rules for planned workouts, both themes and the 0.1.7
layout. Nothing is recommended or advanced automatically.

**Extra workouts.** Start empty workout (Today, under Outside the plan)
creates a workout with `extra=true`, no routine and no exercises.

- **Logging.** Exercises come from the shared library picker and sets are
  logged at once; no target is needed. Goal and rest gives an optional goal
  (sets and a rep range, both ends or neither) and the rest. A goal never
  blocks finishing and never counts.
- **Finishing.** A logged set is required. Warm-ups alone are enough: the
  workout is kept with 0 working sets and no rest. A truly empty extra workout
  can only be discarded.
- **Counting.** Extras appear in History, exercise history and charts like
  any workout, but never on the checklist. Today names the ones with working
  sets under "Outside the plan this week". The data format refuses an extra
  marked as counted.

**Added exercises in a planned workout.** Add exercise appends an entry with
`optional=true` and no plan slot or prescription.

- **Optional today.** It never blocks finishing, never counts toward the
  routine's targets and has no target unless given an optional goal.
- **Duplicates.** Choosing an exercise already in the workout opens that
  entry. A repeated add request with the same entry ID changes nothing; the
  same ID for a different exercise is refused. Adjust today can still
  substitute an exercise that is already in the workout, as in 0.1.7.
- **Order.** Reorder exercises moves entries up or down for this workout
  only. The plan's order is unchanged.
- **Removal.** Remove from this workout applies only to added entries. With
  anything logged, the confirmation names its sets, stages and warm-ups, and
  the request carries that exact record count, so a stale confirmation is
  refused rather than deleting newer work.
- **Rest.** The rest last used for that exercise, else its rest in the plan,
  else a visible, editable 90 s.
- **One-leg reps.** If neither the plan nor history settles whether reps are
  per leg, the app asks once. The answer stays with the entry, comparisons
  only use sets counted the same way and reps are never converted.

**Skipping today.** Skip today · target stays marks a planned exercise that is
still below its target as `skipped`. An exercise that has met its target, or
one with an open drop or rest-pause set, can't be skipped. Its target and
logged sets stay, Resume and Next pass over it and Undo skip restores it. A
workout with a skipped exercise finishes partial unless explicitly credited.

**The future plan.** Save to *routine* plan… needs reviewed sets, rep range
and rest, then stores a `planAdditions` record (phase, routine, exercise,
sets, reps, rest, per-leg).

- **Additions.** Only later workouts of that routine and phase include the
  addition, as a required exercise. Today's entry stays optional, and started
  and finished workouts keep their snapshots. Plan's Add exercise to
  *routine* does the same directly; tapping an addition edits or removes it.
- **Removals.** An original exercise can be removed from that phase's future
  workouts (`planRemovals`) and restored.
- **Limits.** A routine keeps 1–100 exercises and never lists an exercise
  twice.

**Weekly credit (contract v3).** The rule is
`countsForWeek = templateId != null && hasWorkingEffort() && (targetsMet() || countedForWeek)`.

- **Automatic.** Every non-optional exercise must meet its saved target with
  working sets; a finished group counts as one. Optional entries neither block
  nor fill it, and a skipped exercise keeps its target.
- **Explicit.** Count as … done, at Finish or in History, needs a finished
  workout with at least one working set on any exercise, added ones included.
  Warm-ups alone, an open group or an extra workout never qualify.
- **Unticking.** It never uncounts a workout that met its targets.

**History correction.** Add forgotten exercise adds an optional entry to a
finished workout, then its missed sets, stamped at the workout's finish. No
timer starts and the plan doesn't change. It never turns a partial workout
into a done one by itself. An added entry can also be removed from History,
with the same exact-count confirmation.

**Data.** The format is now schema 4 with backup envelope 4. Plan revision 1
and the exercise catalog are unchanged.

- **Migration.** Schema-3 data, in the database or a backup, gains only
  `optional=false` and `skipped=false` on each workout exercise, plus empty
  `planAdditions` and `planRemovals`. Older data first gets the unchanged
  0.1.5–0.1.7 migrations. Every set value, unit, ID, timestamp, group, start
  date, credit, active workout and rest deadline is kept, and a database
  migrated on first launch is marked as needing a backup.
- **Rejecting mislabeled files.** A version-1, -2 or -3 document containing
  any version-four field is rejected, even if the value is the default.
- **Version-four validation.**
  - JSON types must be exact. Plan additions' sets, reps and rest are whole
    numbers, and flags are true or false.
  - An optional entry has no slot, baseline, upper limit, RIR or skip; its goal
    and rest are range-checked. A planned entry keeps its slot and target, and
    an entry started from a plan addition has no baseline or upper limit.
  - An extra workout has no routine, holds only optional entries and never
    counts; once finished it has 1–100 exercises. A planned workout keeps at
    least one planned entry and no slot twice.
  - Plan edits must belong to routines available in their phase and to
    existing exercises, and must not reuse an original slot ID. Every routine
    keeps 1–100 exercises with no added exercise listed twice.
- **Older apps.** 0.1.7 refuses schema-4 data, as tested below.

### Test changes and inventory

All 296 distinct 0.1.7 methods are kept; none were removed. There are 43 new
methods:

- **JVM (+25):** `FlexibleWorkoutTest` 20 and `FlexibleSnapshotCodecTest` 5.
- **API 34 (+18):** `FlexibleWorkoutDeviceTest` 15 and three
  `AccessibilityDeviceTest` validator cases: the extra workout's picker,
  per-leg question and goal dialog; the skipped panel, reorder and Save to
  plan; and plan removal/restore with History correction. The device tests
  include large text, landscape and real-keyboard layouts.

Existing tests changed only for the format bump, nullable fields or harness
synchronization; none was weakened:

- Codec, catalog, plate and group tests expect version 4. The rejected lists
  shift by one and add version-four content relabeled 3. `TestSupport` builds
  real v1/v2/v3 documents without version-four keys (`versionFourKeys`,
  `v3Json`), and a catalog test also asserts the new `contentUpdated`
  preview flag.
- `RepositoryTest` and `WeekChecklistTest` add `!!` where `slotId` and
  `targetSets` are now nullable.
- `FakeRepository` starts workouts from `plannedExercises` and passes the
  Stage 1 actions to the real reducer.
- Device tests that drive a real repository give the UI a
  `MainThreadCollection(repo)`, so Compose collects on the main thread as in
  production. Every assertion still reads the real repository.

The first JVM run after the format bump had 26 failures, all fixture labels
(v1/v2 fixtures emitting version-four keys, and literal version 3 tokens),
not production defects (`evidence/jvm/README-red-green-1.txt`).

Eleven deliberate production mutations were each turned red by the JVM tests
and reverted. The final run applied them one at a time to a `git archive` of
the final test commit `cb04a65`, with the unmutated baseline green (36 of 36
in the `Flexible*` and `WeekChecklistTest` classes); results are in
`evidence/jvm/mutations/final-source`:

| Mutation | Tests that failed |
|---|---|
| M1 Optional-only effort can't be credited | `optionalOnlyWorkKeepsAPlannedWorkoutPartialUntilExplicitlyCredited`, `optionalOnlyWorkCanBeCountedWhenFinishingButWarmUpsAndOpenSetsCannot` |
| M2 Added exercises block completion | `addedExercisesAreOptionalAndNeverBlockTheRoutine`, `optionalOrSurplusSetsNeverStandInForAMissingPlannedSet` |
| M3 Plan removals ignored | `removedPlanExercisesLeaveStartedSnapshotsValidAndAreNeverBroughtBackFromToday`, `versionFourStructureRulesAreEnforced` |
| M4 Plan-addition field types not checked | `versionFourFieldTypesAreStrict` |
| M5 Skip allowed after the target is met | `skippingTodayKeepsTheTargetAndWorkAndStaysPartialUnlessCredited` |
| M6 The same exercise added twice | `duplicateSelectionIsRejectedReplayChangesNothingAndConflictingReuseFails`, `adjustTodayStillSubstitutesAnExerciseAlreadyInTheWorkout` |
| M7 Backup accepts a counted extra workout | `versionFourStructureRulesAreEnforced` |
| M8 Substitution relabels per-leg reps | `adjustTodayCanSaveToAnExistingAdditionButNeverRelabelsReps` |
| M9 The removed substitution rule restored | `adjustTodayStillSubstitutesAnExerciseAlreadyInTheWorkout` |
| M10 Optional or surplus sets fill a planned target | `optionalOrSurplusSetsNeverStandInForAMissingPlannedSet` |
| M11 A skipped exercise counts as met | `skippingTodayKeepsTheTargetAndWorkAndStaysPartialUnlessCredited` |

M6's second test also asserts that adding a copy is refused, and M9 is the
Adjust today rule described below.

Problems found during verification, all resolved:

- **During development** (device logs and notes in `evidence/native-dev`):
  - Done on a goal dialog's last field did nothing. Fixed: it saves.
  - A dialog keyboard-resize experiment left the focused field clipped and
    kept Compose from going idle. It was fully reverted, so dialogs pan for
    the keyboard as in 0.1.7.
  - An unrequested rule refused an Adjust today substitution with an
    exercise already in the workout, which broke a 0.1.7 accessibility
    fixture. It was removed (mutation M9 above).
  - Three landscape keyboard geometry cases failed identically on the
    delivered 0.1.7 APKs. The AVD's Gboard had auto-updated to 18.2, whose
    landscape number pad is taller. Gboard was reverted to the image's
    preload, the emulator's network was turned off and every assertion was
    kept.
  - Harness timing: Compose frames on an IO thread, a dialog asserted before
    its first frame, and Finish tapped during the 500 ms double-tap guard.
    The tests now wait for those states, bounded at 5 s.
- **Contract v3 (production).** The coordinator's v3 clarifications arrived
  after the first freeze, `72b0ea8` (APK `7baee5c9`). Optional-only effort
  can now be explicitly credited, and a nonempty extra workout of only
  warm-ups can finish. Tests were written red first: 4 JVM failures, and 2
  device failures on `7baee5c9` itself. The fix is commit `183431a`. A
  lengthened dialog sentence that broke a large-text test was restored, not
  the assertion. `7baee5c9` and its runs are archived as superseded.
- **Large-text goal dialog (test only).** The first API 33 run of the final
  candidate failed
  `largeTextEmptyWorkoutAndGoalDialogWithKeyboardStayReachable`. The stacked
  Reps to field started below the dialog body's viewport when measured, so
  its clipped bounds were empty; the layout itself was correct. Commit
  `35d763a` compares unclipped bounds, adds a same-width check and scrolls
  each field into view to prove it is whole with a 48 dp target. The app APK
  is unchanged. The red report and the superseded androidTest APK `81793a90`,
  with its passing API 34 runs, are kept separately.
- **Mutation record (tests only).** Re-running the mutations on the final
  source found two things:
  - The first run's M1 changed a helper that production no longer calls
    after contract v3, so its kill came only from direct test assertions. It
    was replaced by the v3 M1 shown above.
  - The second run added M10 and M11. M10 counts a routine complete once its
    total working sets reach the sum of its planned targets, so sets on an
    added exercise, or a surplus set on another planned one, stand in for a
    planned set that wasn't done. It survived all 226 JVM tests. Commit
    `cb04a65` adds
    `optionalOrSurplusSetsNeverStandInForAMissingPlannedSet`. It passes on
    the final source and fails under M10, and adding the missing planned set
    later in History completes the routine. The commit changes no production
    or device-test file. Both earlier runs are kept as superseded records.

Final unfiltered commands, one private emulator at a time, all with
`-Dorg.gradle.workers.max=2`:

```sh
./gradlew --offline --no-daemon --console=plain :app:clean
./gradlew --offline --no-daemon --console=plain :app:assembleDebug :app:assembleDebugAndroidTest
ANDROID_SERIAL=emulator-5602 ./gradlew --offline --no-daemon --console=plain \
  :app:testDebugUnitTest :app:lintDebug :app:connectedDebugAndroidTest   # API 33 AVD
ANDROID_SERIAL=emulator-5600 ./gradlew --offline --no-daemon --console=plain \
  :app:connectedDebugAndroidTest   # API 34 AVD, dark theme
adb -s emulator-5600 shell am instrument -w -r -e class \
  com.rebuild.ui.AccessibilityDeviceTest,com.rebuild.ui.BackupAccessibilityDeviceTest \
  com.rebuild.test/androidx.test.runner.AndroidJUnitRunner   # API 34, light theme
./gradlew --offline --no-daemon --console=plain --rerun-tasks \
  :app:testDebugUnitTest :app:lintDebug   # at cb04a65, after the test was added
```

The connected and light runs used `35d763a`. The last command reran the
whole JVM suite and lint at `cb04a65`, and left the tested APKs untouched.

Source-to-XML reconciliation gives **227 JVM + 112 API 34 native = 339
distinct tests**. API 33 runs **100 native tests**, and the light-theme
validator rerun passes 12 of 12. Every report has zero failures, errors and
skipped cases. API 33 omits the twelve API-34-only validator methods through
the SDK filter; they are not counted as skipped. Each Gradle connected run
recorded the same APK hashes before and after the run, and the light rerun
used the installed APKs, so every final run tested the same clean-built pair.

Lint is unchanged from 0.1.7: **0 errors, 19 warnings and 3 information
findings**, with the same 11 dependency-version, 4 KTX, 1 obsolete SDK, 1
unused resource, 1 monochrome icon and 1 window-size warnings and 3
state-boxing notes.

### Native accessibility checks

The twelve API 34 validator tests throw on any ERROR result. Warnings and
information are kept in `atf-evidence.json` and not suppressed. The
deliberately unlabeled negative control is rejected in both themes, and the
rest-bar buttons still export 48 dp provider bounds.

- **Dark (final Gradle suite):** 0 errors, 16 warnings, 51 information.
  - 14 warnings are Material 3 disabled text at 38%, the same busy and
    unchanged states as 0.1.7.
  - 2 warnings are the "Selected" labels on the current Set type's check icon,
    retained for the reason given in 0.1.7.
  - The information results are repeated non-interactive text. The new
    methods add 32: the Plan title matching its navigation label, identical
    per-exercise target lines in the plan editor, and History correction
    lines such as "No sets logged yet."
- **Light (same twelve tests rerun on API 34):** 0 errors, 66 warnings,
  48 information.
  - 47 warnings are estimates against the `#79736B` field outline. Pixel
    samples of the value text `#1F1B16` on `#F3F1EE` measure 15.19:1.
  - 17 warnings are disabled text and 2 are the "Selected" icons.

The nine 0.1.7 methods give 16 W in dark and 40 W in light (0.1.7: 16 and 46).
The 6 fewer are a timing-dependent extra validator pass during a save
cooldown, which the superseded 0.1.7 candidate also lacked. The three new
methods add only 26 light outline estimates and repeated-text information.
The superseded androidTest APK gave identical counts.

### Native captures

The 56 captures (49 dark, 7 light) used the exact tested APK on the private
API 34 AVD. Data was the 0.1.7 upgrade proof's final database, migrated by
the first 0.1.8 launch, with the clock set to Saturday 3 October 2026. Every
value was typed through the UI, and all 286 actions are logged in
`capture-method/interactions.jsonl`. They show:

- An extra workout from empty: search, the per-leg question, kg and plates
  sets, finishing, and Today's "Outside the plan" line.
- The Saturday Upper:
  - an added optional exercise and the picker's in-workout marks;
  - duplicate focus and session-only reorder;
  - Save to plan;
  - skip today with Undo;
  - removing an added entry with logged work;
  - a partial finish left uncounted.
- Lower A credited at finish with only optional work, and a warm-up-only
  extra in History.
- History: credit unticked and ticked again, the skipped entry and a
  forgotten exercise added.
- Plan: an addition, a removal and a restore. The next Upper start includes
  the addition as required and omits the removal; it was then discarded.
- Exercise history with extra and planned sessions.
- Landscape with and without the keyboard, and 360 dp at 200% text,
  including the goal dialog with the keyboard and an empty extra's finish.
- The light theme.

The pulled end-state database agrees with the captures: four new workouts,
one plan addition and one removal. The 31 seeded workouts are unchanged
apart from the new `optional` and `skipped` defaults, and discarded workouts
leave no trace.

Known appearance items, not new in 0.1.8: the light strip behind the
landscape gesture handle, and the light theme's exercise picker dimming the
system bars.

### Actual installed upgrade and backup evidence

The proof ran on the private API 33 AVD with the real Files (DocumentsUI)
picker. `verify_upgrade_018.py` asserts every point below from the pulled
databases, backups and screen hierarchies and writes `upgrade-018.json`.
Run on altered copies, it fails in all 8 cases (`upgrade-negative-control.txt`):

- credit left on after unticking;
- the forgotten exercise made required;
- a skipped exercise losing its target;
- an extra marked counted;
- a changed rep range in the backup;
- one changed byte in two databases;
- a changed checklist line on screen.

1. **Starting state.** The 0.1.7 capture fixture: 31 workouts, kg and plates
   history, a custom exercise and a plan override. 0.1.7's own UI then
   started Upper, logged 22.5 kg/DB × 10 @ 2 RIR, left a rest-pause of
   7 plates × 9, × 4 open and exported a version-three backup.
2. **Install.** The final 0.1.8 APK went on with `adb install -r`. The user
   ID, first-install time and signature were unchanged, and the database was
   byte-identical after installation.
3. **First launch.** Schema 3→4 (revision 71→72) added only the defaults
   listed above. All **427 earlier sets are unchanged**.
4. **The open rest-pause.** It resumed. A third stage (× 3) was added and
   Finish set started a rest exactly 180,000 ms after `finishedAt`.
5. **Optional entry.** Cable face pull was added and logged (5 plates × 12,
   90 s rest). Picking it again opened the same entry, and the reorder
   changed only this workout. Force-stop and reopen left the database
   byte-identical.
6. **Plan and skip.** Face pull was saved to Upper's plan for Intro 2 at
   2 × 12–15, 90 s; today's entry stayed optional. Dumbbell curl was skipped
   with its target (2 × 10–15 @ 3 RIR) kept.
7. **Partial finish.** The workout finished with 3 working sets and was not
   counted. Today showed Done 1 of 4.
8. **Optional-only credit.** Lower A had working sets only on an added
   Barbell hip thrust (60 kg × 10) and was counted at finish. Unticked in
   History it showed Done 1 of 4; ticked again, Done 2 of 4.
9. **Extras.**
   - An extra workout logged Goblet squat at 16 kg × 12 (90 s rest, with no
     earlier rest or plan rest) and Seated cable row at 8 plates × 10 (180 s,
     its last-used rest).
   - A warm-up-only extra (Dumbbell lateral raise, 6 kg × 15) finished with
     0 working sets.
   - Neither counted. Today showed Done 2 of 4 and 1 extra workout.
10. **Forgotten exercise.** Cable rear-delt fly at 4 plates × 12 was added to
    the finished Upper in History. It was stamped at the workout's finish,
    with no rest timer and no plan change.
11. **Plan edits.**
    - Cable triceps pushdown was removed and restored, then Dumbbell curl was
      removed.
    - The next Upper started with the six remaining slots plus face pull,
      now required at 2 × 12–15.
    - The finished Upper's snapshot was unchanged, and the new workout was
      discarded.
12. **Totals.** 7 new sets in all. The checklist went from Intro Upper to
    Intro Upper and Lower A.
13. **Backups.**
    - The 0.1.7 version-three backup and the 0.1.8 version-four backup each
      equal their database's domain state. The version-four file keeps
      optional, skipped and extra workouts, plan edits and credit.
    - 0.1.8 restored the 0.1.7 file, which became the same domain state as
      the first-launch migration: the rest-pause open, Done 1 of 4.
    - It then restored its own version-four file exactly: Done 2 of 4 and
      1 extra workout.
    - Each restore came after the preview, a mandatory safety export and
      confirmation. Each safety export equals the domain state before
      replacement, and no rest alert was restarted.
14. **Older app.** After a debuggable downgrade that kept data, 0.1.7 refused
    the schema-4 database, leaving it unchanged. On its own version-three
    database it rejected the version-four backup, again leaving the database
    unchanged.

Optional entries, Save to plan, skips, extras, the forgotten exercise and plan
removals were all created after the upgrade, because 0.1.7 cannot make them.
Force-stop recovery shows that state persists, not that rest alerts are
delivered.

### Exact artifact and limits

Production was frozen at `183431a`. Commit `35d763a` changed one device test
only; a clean rebuild from it gave the same app APK byte for byte. Every final
test, capture and the upgrade proof used **11,317,382 bytes**, SHA-256
`ca6aecc731e2fd4b20801f15ed13efb631456567a029c36fba204f293e8e5794`, signed
with the unchanged Android Debug certificate
`d834bba6e23b6dc1dfab6f213ec474d0ec02dbd2699e1012824757c94642365b`. The
final androidTest APK is `ae5c6541…` (5,223,768 bytes). Commit `cb04a65`
adds one JVM test, and later commits change documentation only.

The earlier candidate `7baee5c9` (commit `72b0ea8`) and androidTest APKs
`81793a90` and `881a1afd` are superseded, and their results are archived
separately. No network permission, key, personal PDF or competitor artwork is
included, and all data is synthetic.

Known limits:

- **Dialogs and the keyboard.** Dialogs pan for the keyboard. The field being
  typed stays visible and Done on a goal dialog's last field saves. At 200%
  text, or for Save to plan's Cancel at default size, the lower buttons can
  sit behind the keyboard until it is closed or the body scrolled.
- **Newer Gboard in landscape.** With Gboard 18.2 at a large display size,
  the set entry's fields can be partly covered (about 32 px) while typing in
  landscape. The same applies to 0.1.7.
- **Restore preview.** It doesn't list plan-edit counts.
- **Not yet checked on the phone.** Emulator runs are not acceptance on the
  physical Galaxy A71, the Samsung keyboard, one-handed reach, locked-screen
  alerts or TalkBack speech.

## 0.1.7 drop sets, rest-pause and weekly checklist

Version 0.1.7/code 8 builds on 0.1.6 commit `2b4be7c`. It adds grouped drop-set
and rest-pause logging and a Monday–Sunday routine checklist. Both features
record what happened and when; neither gives advice. These are unchanged: the
plan, the routine guidance, prefill for normal sets, both themes and the 0.1.6
layout. Nothing suggests when to use either method.

**Drop sets and rest-pause.** The next-set entry has a Set type control. It
defaults to Normal, so the prefilled one-tap Log set works as before.

- **Saving stages.** Logging with Drop set or Rest-pause selected starts a
  group. Each stage is saved as its own logged set as soon as it is logged,
  with its own ID, timestamp, reps, optional RIR and unit; a repeated save
  request for a stage changes nothing. Stages link by `groupId` to a group
  record on their exercise (`setGroups`: ID, method, `finishedAt`). Add drop /
  Add mini-set adds a stage and Finish set closes the group.
- **Counting.** A finished group counts as **one working set**. That applies to
  targets, Resume, Today, History and Plan summaries. This is a tracking
  convention, not a claim that the stages have one set's training effect. An
  open group shows as in progress and counts nothing.
- **What is required.** A one-stage group is allowed. Nothing requires a
  lighter load, failure or another stage, and no stage value is calculated.
- **Units.** Live stages keep the group's unit: kg or whole plates per stack.
  Bodyweight and assistance keep their existing meanings. Switching unit in the
  middle of a group is refused and nothing is lost.
- **Corrections.** Tapping a grouped set opens its dialog, where any stage can
  be corrected, including its unit; the other stages, IDs and times are
  unchanged.
- **Limits.** A group holds at most 20 stages. A 21st is refused with an
  explanation and nothing is truncated. Warm-ups stay plain sets and cannot be
  stages.
- **Rest.** Logging a stage clears a running rest. The exercise's normal rest
  starts once, at Finish set; a repeated Finish does nothing. While a
  rest-pause set is open, an explicit "Start 20 s pause" button shows a labeled
  Pause in the rest bar. Pause lengths are not recorded. Corrections and
  history edits start no timer.
- **Other exercises.** Opening another exercise while a group is open offers
  "Go to …" or "Finish that set".
- **Finishing or discarding.** If a group is open when the workout is
  finished, the dialog says the set will be finished with its logged stages.
  Discarding asks for confirmation as before, and its count includes each
  stage.
- **Display.** The ledger, Last time, History detail, exercise history and the
  set's dialog show the method and every stage, for example "Drop set: 12.5 kg
  × 12; 10 kg × 8; 7.5 kg × 6". Screen readers hear one set with its number of
  stages.
- **Prefill.** Normal prefill never comes from a stage, and stages are
  prefilled only from recorded values in the same unit:
  - A stage takes the same stage of last time's set in the same position if
    that set used the same method.
  - Otherwise a first stage may take last time's normal set, labeled as a
    normal set.
  - Otherwise a rest-pause mini-set offers this set's load with reps blank,
    and a drop stage is left blank.
- **Charts.** Charts plot normal working sets only and say so. Stages are
  listed but never summed or averaged.

**Weekly checklist.** The week runs from Monday 00:00 to the next Monday 00:00
in the phone's time zone. It is computed on calendar dates, so DST and year-end
are handled, and the locale's first day of the week does not move it.

- **Which week.** New workouts record their local start date (`startDate`). A
  workout belongs to the week it started in, so a Sunday workout that ends after
  midnight fills one week only.
- **Which routine.** Routines are matched by template ID within the current
  phase. Doing Lower A on a Push day ticks Lower A and leaves Push pending.
  Templates with the same name stay distinct.
- **Automatic credit.** A workout counts once it is finished with every saved
  working target met; a finished group counts as one set.
- **Partial credit.** A finished partial workout counts only if "Count … as done
  for its week" is ticked. The option is off by default and appears in the
  finish dialog and later in History. It sets `countedForWeek`, which never
  adds or removes sets. Unticking it never uncounts a workout that met its
  targets.
- **What never counts.** Empty, warm-up-only and discarded workouts never
  count, and a repeat counts once.
- **Today's choices.** Resume always comes first. Next is today's routine if it
  is unfinished, otherwise the next unfinished routine in plan order. Rest days
  stay rest days with optional starts. A complete week offers rest or Repeat.
- **No stored counter.** Every view derives the week from dated records. Monday
  shows a fresh week without deleting history or carrying anything over. Edits,
  deletions, restores and clock changes recalculate it.
- **Phase changes.** Changing phase switches the checklist to that phase's
  templates. Old workouts keep their phase and saved targets.

**Data.** The data format is now schema 3 with backup envelope 3. Plan revision
1 and the 60 built-in exercises are unchanged.

- **Migration.** Older data, in the database or a backup, gains only these
  defaults: `startDate` (from `startedAt` in the phone's zone),
  `countedForWeek=false`, `setGroups=[]`, `groupId=null` and `shortPause=false`.
  Schema-1 data first gets the unchanged 0.1.5 library migration. Every set
  value, unit, ID, timestamp, note, phase, override, active workout and rest
  deadline is kept. A database migrated on first launch is marked as needing
  a backup.
- **Rejecting mislabeled files.** A version-1 or version-2 document that contains
  any version-three field is rejected, even if the field holds its default value.
  It is not reinterpreted.
- **Version-three validation.**
  - JSON types must be exact.
  - Group IDs are unique.
  - Each group has 1–20 stages, kept together in order. Every stage belongs to a
    group on its own exercise and is never a warm-up.
  - At most one group is open, and none is open in a finished workout.
  - Weekly credit applies only to finished workouts.
  - A short pause belongs only to the active workout's open rest-pause set.
  - The start date is a real ISO date within one day of `startedAt`.
- **Older apps.** 0.1.6 rejects version-three data, as tested below.

### Test changes and inventory

All 251 distinct 0.1.6 methods are kept; none were removed. There are 45 new
methods:

- **JVM (+28):** `GroupLoggingTest` 10, `GroupSnapshotCodecTest` 5,
  `WeekChecklistTest` 11 and two `LoadPresentationTest` cases. The week tests fix
  the clock and time zone:
  - America/New_York covers the 167- and 169-hour DST weeks and year-end.
  - A start date recorded in New York gives the same week when read in Addis
    Ababa or Kiritimati. An undated legacy record would follow the current
    zone, which is why migration stores the date once.
  - Exact Monday bounds hold under US, French, Saudi Arabic and Amharic
    locales.
- **API 34 (+17):**
  - `SetGroupsDeviceTest` (6) and `WeekChecklistDeviceTest` (6).
  - Three `AdaptiveUiTest` cases keep an open set's entry and actions reachable
    above the real keyboard in the docked, 200%-text and landscape layouts.
  - Two `AccessibilityDeviceTest` validator cases: one for the open drop-set
    entry, rest-pause pause and a grouped set's dialog, and one for the week
    checklist with partial credit.

Existing tests changed only for the format bump or a changed signature; none was
weakened:

- `SnapshotCodecTest`, `PlateSnapshotCodecTest`, `CatalogMigrationTest` and
  `ExerciseCatalogTest` now expect version 3. The rejected-version lists also
  include 2, so version-three data labeled 2 is refused.
- `PlanMigrationTest` builds its legacy input with a version-two encoder.
- Three `PreviousSetsTest` calls pass the exercise to the new `SetLedger`
  signature.
- The `LargeSnapshotDeviceTest` fixture adds `startDate`.
- `TestSupport` and `FakeRepository` gained group and week helpers.

Six deliberate production mutations were each turned red by the new JVM tests,
then reverted (log in `evidence/jvm/mutations.txt`):

| Mutation | Tests failed |
|---|---|
| Stages count as sets | 5 |
| Week taken from finish time | 2 |
| Credit without effort | 2 |
| A replayed stage is not a no-op | 1 |
| Finish is not idempotent | 1 |
| Normal prefill taken from a stage | 1 |

Problems found during verification, all fixed:

- **During development** (device logs in `evidence/native-dev`). Device runs
  caught Log set overlapping the short-pause control and, in landscape with the
  keyboard, the entry's exercise context line being clipped. Both layouts were
  fixed in production code before the first freeze, and the assertions that
  caught them are unchanged. The other failures in those logs were also
  resolved before the first freeze; the final suites run the committed
  assertions.
- **Lazy history list (test only).** The first API 33 run, on the superseded
  candidate, failed
  `groupsOnlyHistoryListsEveryStageWithNoChartAndChartsPlotNormalSetsOnly`.
  The lazy exercise-history list had not yet composed the session row below
  the charts on the shorter window: 2 nodes were expected and 1 was found.
  Commit `8bbb434` scrolls to each row; the expectations are unchanged. The red
  report is kept.
- **Stale texts (production).** Rehearsing the upgrade on candidate `08832c0d`
  found two outdated texts. The Backup screen still said new backups need
  0.1.5. The older-file restore preview still described the 0.1.5 kg-only
  migration. Commit `b576548` corrected both. The clean build and every final
  run were then repeated, and the superseded results are kept separately.

Final unfiltered commands, one private emulator at a time:

```sh
./gradlew --offline --no-daemon --console=plain :app:clean
./gradlew --offline --no-daemon --console=plain :app:assembleDebug :app:assembleDebugAndroidTest
ANDROID_SERIAL=emulator-5600 ./gradlew --offline --no-daemon --console=plain \
  :app:connectedDebugAndroidTest   # API 34 AVD, dark theme
ANDROID_SERIAL=emulator-5602 ./gradlew --offline --no-daemon --console=plain \
  :app:testDebugUnitTest :app:lintDebug :app:connectedDebugAndroidTest   # API 33 AVD
adb -s emulator-5600 shell am instrument -w -r -e class \
  com.rebuild.ui.AccessibilityDeviceTest,com.rebuild.ui.BackupAccessibilityDeviceTest \
  com.rebuild.test/androidx.test.runner.AndroidJUnitRunner   # API 34, light theme
```

Source-to-XML reconciliation gives **202 JVM + 94 API 34 native = 296 distinct
tests**. API 33 runs **85 native tests**, and the light-theme validator rerun
passes 9 of 9. Every report has zero failures, errors and skipped cases. API 33
omits the nine API-34-only validator methods through the SDK filter; they are
not counted as skipped. Every Gradle connected run reported the package tasks
up to date, and the light rerun used the APKs already installed, so every run
tested the same clean-built APKs.

Lint is unchanged from 0.1.6: **0 errors, 19 warnings and 3 information
findings**. The warnings are 11 dependency-version, 4 KTX, 1 obsolete SDK
check, 1 unused license resource, 1 monochrome launcher icon and 1 window-size
configuration warning. The information findings are 3 state-boxing notes.

### Native accessibility checks

The nine API 34 validator tests throw on any ERROR result. Warnings and
information are kept in `atf-evidence.json` and are not suppressed. The
deliberately unlabeled negative control is rejected in both themes, and the
rest-bar buttons still export 48 dp provider bounds.

- **Dark (final Gradle suite):** 0 errors, 16 warnings, 22 information.
  - 14 warnings are Material 3 disabled text at 38%: busy-state Finish, Set
    type, Load unit, Saving and Log set, the unselected Choose a phase, or an
    unchanged note's Save. The disabled states are asserted by the tests.
  - 2 warnings flag the "Selected" label on the check icon of the current Set
    type. Menu items expose no selected state, so the label is what tells a
    screen reader which type is current; it is kept.
  - The 22 information results are repeated non-interactive text: a routine
    name in both the session preview and the week checklist, repeated
    "Not done" badges, or a screen title matching its navigation label.
- **Light (same nine tests rerun on API 34):** 0 errors, 46 warnings,
  16 information.
  - 23 warnings are disabled text.
  - 21 warnings are estimates against the `#79736B` field outline. Pixel samples
    of the value text `#1F1B16` on `#F3F1EE` measure 15.19:1.
  - 2 warnings are the "Selected" icons.

0.1.6 had 11 W / 3 I (dark) and 30 W / 3 I (light). The increase comes from the
new set-type control, the "Selected" icons, more fields in drop-set entries and
the checklist's repeated text. The superseded `08832c0d` candidate gave the
same dark counts; its light run had 6 fewer disabled-text warnings, from one
validator pass fewer during the save cooldown.

### Native captures

The 45 captures used the exact tested APK on the private API 34 AVD. The data
was the upgrade proof's after-launch database, every input was typed through
the UI, and every action is logged in `capture-method/interactions.jsonl`.

- **Rest-pause on Lat pulldown (plates).** The rest-pause and 20 s pause flow
  ends at Finish set with "Working sets 2 of 2". The 3:00 rest started once, at
  Finish.
- **kg drop set on Cable lateral raise.** Each stage was logged as entered, and
  the running rest stopped when the next stage began.
- **Adaptive entry with the real keyboard.** The kg drop's open entry was
  captured in landscape and at 360 dp with 200% text. The entry switches to the
  inline layout, and the fields, Add drop and Finish set stay full size above
  the keyboard. A typed draft survived the rotation and the density and font
  changes.
- **Set dialog and plates drop.** The finished drop set's dialog lists its
  stages, Add missed stage, the type help and Delete this set. A plates drop
  was then started on Cable rear-delt fly.
- **Open-group guard and partial finish.** Opening another exercise with that
  group open shows the guard. The partial finish dialog has the credit
  checkbox off by default.
- **Today, Plan and History after finishing.** These include History's credit
  toggle. The exercise chart excludes stages and the latest summary lists them.
- **One week with a simulated clock.**
  - Friday is a rest day; Lower A was done anyway and counted automatically.
  - Saturday is scheduled Upper, which was still offered after Lower B was
    done.
  - Then the Saturday Upper: "Week complete … a repeat doesn't count twice",
    Done 4 of 4.
- **Light theme.** A Repeat with an open drop set shows Resume first. A
  discarded repeat leaves the week complete. The next Monday shows a fresh
  Done 0 of 4 with history retained.

The pulled end-state database agrees with these captures. It holds three
finished groups on the partially credited Upper, three automatically counted
full workouts, and no trace of the discarded repeat.

Known cosmetic items in the frozen build, documented and not changed:

- The landscape entry heading reads "drop set set 2, stage 3".
- History session-detail rows for groups show no visible set number. Their
  accessible text includes it, and the workout ledger shows it.

### Actual installed upgrade and backup evidence

The proof ran on the private API 33 AVD with the real Files (DocumentsUI)
picker. `verify_upgrade_017.py` asserts every point below from the pulled
databases, backups and screen hierarchies, and writes `upgrade-017.json`. Run
on altered copies, it fails: a reverted stage correction, a stage stripped of
its group and one changed database byte are each caught
(`upgrade-negative-control.txt`).

1. **Starting state.** The 0.1.6 delivery state was in place: 31 workouts,
   mixed kg/plates, a custom exercise, a plan override and an active workout.
   0.1.6's own UI then logged 12.5 kg × 13 @ 2 RIR and exported a version-two
   backup.
2. **Install.** The final 0.1.7 APK went on with `adb install -r`. The user
   ID, first-install time and signature were unchanged, and the database was
   byte-identical after installation.
3. **First launch.** The first launch migrated schema 2→3 (revision 48→49) and
   added only the defaults listed above. All **415 earlier sets are
   unchanged**.
4. **Groups in the 0.1.7 UI.**
   - A rest-pause set with 2 plate stages stayed open across a force-stop. No
     rest ran while it was open. After a third stage, Finish set started a rest
     whose deadline was exactly 180,000 ms after `finishedAt`.
   - A kg drop set had its stage 2 corrected from 8 to 9 reps, keeping
     `completedAt`.
   - A plates drop set was closed by Finish workout.
   - Nine new sets in all.
5. **Weekly credit.** The workout was partial and counted by explicit choice;
   Today showed Done 1 of 4.
6. **Reopen.** Reopening left the database byte-identical.
7. **Restores in 0.1.7.** 0.1.7 restored the 0.1.6 backup, which became the
   same domain state as the migration, then its own version-three backup,
   which kept the groups, stages and credit. Each restore came after the
   preview, a mandatory safety export and confirmation, and each safety export
   equals the domain state before replacement.
8. **Older app.** After a debuggable downgrade that kept data, 0.1.6 refused
   both the schema-3 database and the version-three backup, leaving its
   database unchanged.

Groups and credit were created only after the upgrade, because 0.1.6 cannot
make them. Force-stop recovery proves that state is recovered, not that rest
alerts are delivered.

### Exact artifact and limits

Production was frozen at `b576548`. That source was built clean once, and every
final test, capture and the upgrade proof used that APK: **11,168,020 bytes**,
SHA-256 `ed0abd2f8547ac595b1e4432b080d6709b6dee950c17ed694a454d85a8c373a0`,
signed with the unchanged Android Debug certificate
`d834bba6e23b6dc1dfab6f213ec474d0ec02dbd2699e1012824757c94642365b`. Later
commits change documentation only.

The earlier candidate `08832c0d` (source `2f5ad88`) is superseded, and its
results are archived separately. No network permission, key, personal PDF or
competitor artwork is included, and all data is synthetic. Emulator captures
are not acceptance on the physical Galaxy A71, the Samsung keyboard, one-handed
reach, locked-screen alerts or TalkBack speech; those checks remain pending on
the phone.

## 0.1.6 set-first logbook UI revamp

Version 0.1.6/code 7 is a user-interface revamp on top of 0.1.5 commit `7947000`.
Workout became a set-first logbook: one ledger pairs the last comparable
session's working sets with today's rows, unlogged rows are dashed "up next /
planned, not logged yet" placeholders rather than values, warm-ups sit apart and
never count toward targets, and secondary actions collapse under the ledger.
On roomy portrait windows the next-set entry is docked at the bottom; at font
scale above 1.3, in landscape or on windows shorter than 600 dp the same entry
moves inline and scrolls. Both placements share one draft, so the stable
draft/request IDs, correction timestamps, prefill and stale-draft guard are
unchanged. Resume opens the first exercise still below its working target.
Today, History (month groups, session detail and exercise progress), Plan, the
exercise library and More (grouped rows) were recomposed around the same session
and ledger vocabulary. Seven locally bundled Lucide icons were added and compared
path-for-path (see [icons](icons.md)).

No data, domain, platform, manifest, dependency or theme-role change accompanies
the revamp: schema 2, backup envelope 2, plan revision 1, the 60 built-in
exercises, per-set `KG`/`PLATES` meaning, same-unit comparison/prefill and every
0.1.5 validation rule are the 0.1.5 code. The production diff from `7947000` is
limited to `app/src/main/java/com/rebuild/ui/`, new drawables and the version
code/name. No automatic progression, estimate, score, streak or new statistic
was added.

### Test changes and inventory

All 244 methods from 0.1.5 are retained. Seven are new: four JVM
`SessionPresentationTest` cases (history month grouping without reordering,
resume target selection, all-logged/empty fallbacks, warm-ups never counting)
and three native cases (kg and plate exercises rebinding the whole workspace and
persisting each Log to the selected exercise, workspace/save rebinding in the
app, and fresh Resume focus while an already-open choice stays). Three existing
`PreviousSetsTest` cases now render the new `SetLedger` instead of the removed
`PreviousSets` list; their assertions moved from column headers to the per-row
text (for example `42 kg/DB × 9`), and the empty case also asserts explicit
not-logged placeholders and no fabricated rows.

Final unfiltered commands, one private emulator at a time, two Gradle workers:

```sh
./gradlew --offline --no-daemon --console=plain \
  :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest
ANDROID_SERIAL=emulator-5600 ./gradlew --offline --no-daemon --console=plain \
  :app:connectedDebugAndroidTest   # API 34 AVD, dark theme
ANDROID_SERIAL=emulator-5602 ./gradlew --offline --no-daemon --console=plain \
  :app:connectedDebugAndroidTest   # API 33 AVD, light theme
```

Source-to-XML reconciliation is **174 JVM + 77 API 34 native = 251 distinct
tests**; API 33 runs **70 native tests**. Every report has zero failures, errors
and skipped cases. The same seven API-34-only ATF methods are omitted by the SDK
filter on API 33, not counted as skipped. Connected runs reused the exact built
APKs (Gradle reported them up to date; no source file is newer).

Lint has **0 errors, 19 warnings and 3 information findings**: the unchanged
11 dependency-version, 4 KTX, 1 obsolete SDK check, 1 unused license resource and
1 monochrome launcher warnings, one new `ConfigurationScreenWidthHeight` warning
and 3 state-boxing information findings (0.1.5 had 4). The new warning is
intentional: dock placement reads the window configuration, which follows the
window and never the keyboard, so the IME cannot flip the layout mid-entry.

### Native accessibility checks

ERROR results throw in the seven API 34 validator tests; warnings and information
are retained in `atf-evidence.json`, not suppressed. The deliberately unlabeled
negative control is rejected in both themes, and all three rest-bar buttons
still export 126 px/48 dp provider bounds.

- Dark (final Gradle suite): **0 errors, 11 contrast warnings, 3 duplicate-text
  information**. All 11 warnings are Material 3 disabled text at 38% (busy
  Finish, Load unit, Saving/Log set, unselected Choose a phase, unchanged note
  Save); disabled states are asserted by the tests.
- Light (same seven tests rerun on API 34): **0 errors, 30 contrast warnings,
  3 duplicate-text information** (0.1.5 light: 25 warnings, 11 information).
  Fourteen are the disabled texts above. Sixteen are estimates against the
  `#79736B` field outline, which now sits on the docked-entry surface `#F3F1EE`
  (4.16:1; 0.1.5's fields sat on `#FAF9F7`, 4.46:1). Pixel samples of the same
  captures show the value text `#1F1B16` at 15.19:1 on that surface. The backup
  preview case now flags the unchanged onboarding "Choose a phase" disabled
  button where 0.1.5 reported a duplicate heading; `PhaseScreens.kt` did not
  change, so this is recorded as a validator-capture difference, not certified.

### Native captures

The exact tested APK was captured on the private API 34 AVD with the synthetic
mixed-unit fixture restored before each flow: dark, light, 360 dp with 200% text
(inline entry, real numeric IME), landscape with the real IME, the load-unit
chooser, library alias search, History/progress, Plan and More. Matched 0.1.5
captures of the same fixture and steps form before/after sheets. Keyboard
geometry was read from the settled window/IME insets and the UI hierarchy: in
portrait the docked fields, Warm-up and Log set remain above the IME; at 200% and
in landscape the focused inline entry, Log set and the rest bar stay visible
above it.

### Actual installed upgrade and backup evidence

On the private API 33 AVD, 0.1.5 migrated the synthetic version-one fixture
(custom exercise, future-plan override, active workout), then its own UI logged
42.5 kg × 10 and 5 plates/stack × 12 and exported a backup through Android Files.
The tested 0.1.6 APK installed over it with `adb install -r`, without
uninstalling: same user ID and first-install time, and a **byte-identical
database** after installation and after first launch. The 0.1.6 UI then logged
42.5 kg × 9 and 6 plates/stack × 10; all 412 earlier sets were unchanged and the
only catalog change was the remembered plate preference for Lat pulldown.
Force-stopping and reopening preserved the database, active workout and rest
deadline. This proves state recovery, not rest-alert delivery.

0.1.6 restored the 0.1.5 backup and then its own version-two export, each after
preview, a mandatory current-data safety export and confirmation; every safety
export equals the pre-replacement domain state. One attempt was refused because
the rest timer expired between preview and confirmation ("data changed after
the preview"), leaving domain data unchanged, as designed. After a debuggable
downgrade that kept data, 0.1.5 opened the 0.1.6 database and restored the 0.1.6
backup. `verify_upgrade_016.py` asserts these facts from the pulled databases
and backup files and writes `upgrade-016.json`.

### Exact artifact and limits

The delivered APK is the exact tested artifact: **11,052,488 bytes**, SHA-256
`b95e919afb468a6e888d718ca5f0a71e24467dcd2e13842869399db150580a94`, signed
with the unchanged Android Debug certificate
`d834bba6e23b6dc1dfab6f213ec474d0ec02dbd2699e1012824757c94642365b`.
No network permission, key, personal PDF or competitor artwork is included.
All data is synthetic. Emulator captures are not physical Galaxy A71, Samsung
keyboard, one-handed reach, locked-screen alert or TalkBack speech acceptance;
those remain pending on the phone.

### Clean rebuild

A clean build of this source is deterministic but not byte-identical to the
tested APK: same size, SHA-256
`bd87068a2ed1049903426eb0d9ffb9e7a4f14631533af3c4af262cc32240b9cd`. Only
`classes4.dex` differs, in 63 `com.rebuild.ui` methods. The incremental
development build read eight classes' Compose `$stable` fields at runtime; all
are 8 (unstable), which selects the `changedInstance` call a clean build makes
directly. Excluding those alternatives, all 3,861 methods have identical call,
field, allocation and cast sequences. The clean APK also passed the unfiltered
API 34 suite (77/77). Its ATF results match, except three duplicate-text
information results from the weekday-dependent Today list behind the backup
dialog. The delivery's `reports/reproducibility` folder holds the scripts and
outputs.

## 0.1.5 exercise library and explicit cable units

Version 0.1.5/code 6 adds 60 common rep-based exercises, equipment/muscle metadata,
name/alias search, filters, custom records and a shared native picker for future
plan edits and today's substitutions. Browsing is not a new workout or a change
to the prescribed routine. Plan revision 1, the Lat pulldown prescription, icons
and both 0.1.4 theme definitions are unchanged.

Each logged set explicitly records `KG` or `PLATES`. `kg` retains its kilogram
meaning; a separate integer `plates` stores 1–1000 physical plates **per stack**.
No conversion, pulley ratio, combined dual-stack count, fractional plate count or
plate-based assistance is inferred. Unit choice is available at active entry and
remembered for future workouts. Switching never reinterprets saved sets or
unsaved numeric input. Corrections affect the selected record, while previous
working sets, prefill and both chart series are separated by exercise identity
and unit. Existing custom exercises can explicitly supply equipment metadata.

Schema/envelope version 2 requires Rebuild 0.1.5 or newer. Version-one weights
remain kg and bodyweight loads remain absent. Missing catalog entries are added
idempotently without replacing edited/custom records, plans or past snapshots.
The existing plan migration remains independently revision-gated. Validation
rejects incompatible units, nonfinite loads, malformed references/duplicates and
fractional/exponent plate tokens; failed migrations and restore operations do not
replace valid disk state.

### Actual installed upgrade, correction and backup evidence

The preserved 0.1.4 APK was installed on the private offline API 34 emulator.
Android Files restored a synthetic legacy fixture containing finished and active
workouts, a 20 kg warm-up, a 42.5 kg working set, notes, one custom exercise and
a custom future-plan override. The old app exported an actual version-one backup.
The tested 0.1.5 APK then installed with `adb install -r`, without uninstalling.

Database comparisons prove exact old workout/catalog meaning, IDs, weights,
timestamps, notes, phase, templates, overrides, active workout, rest state and
plan revision were retained. Schema changed from 1 to 2, the catalog grew from
32 to 61 including the custom record, and migration added one dirty revision.
This is intentionally **not** a byte-identical database claim.

Native entry cleared the kg prefill when selecting plates, then explicitly
logged 5 plates/stack × 12 exactly once. Backgrounding, killing only the observed
app PID and relaunching preserved the complete snapshot and running rest timer.
After explicitly finishing partially, a history correction changed only that
plate count from 5 to 6. Every kg/warm-up record, ID, repetition count and
completion timestamp remained unchanged. Separate chart tabs and a subsequent
workout showed 42.5 kg × 8 versus 6 plates/stack × 12, never cross-unit prefill.

The new app actually restored the old exported version-one file, then restored
its own version-two mixed-unit export. Both flows saved mandatory current-data
safety backups through Android Files before confirmation. Comparisons establish
exact domain-record and preference roundtrips; restore/backup bookkeeping is
intentionally excluded from byte equality. Rest alerts were not restarted.
Proofs and synthetic snapshots are in the delivery's `evidence/upgrade/`.
The preserved 0.1.4 APK also actually rejected this new version-two export through
Android Files with an unsupported-format error. Its database was byte-identical
before and after the attempt; plate values were not interpreted as kilograms.

### Measured accessibility correction and test synchronization

The new real-repository ATF case exposed an existing semantic overlap when a
matching running rest timer rendered the Material progress indicator. Although
the three buttons were visibly and Compose-touch-bounded at 48 dp, Android's
accessibility provider exported only 38 dp of their height. Failure and settled
screenshots were byte-identical; waiting did not fix the defect.

Only the decorative progress line now clears its semantics. The readable
countdown retains the same accessible meaning. Native provider assertions prove
all three button bounds now equal the rendered 126 px/48 dp bounds; button roles
and the readable countdown remain. No timer, padding, palette, validator threshold
or suppression changed. Temporary diagnostic waits were removed.

An API 33 run also exposed a new test-driver race after saved-state recreation:
it clicked the unit selector during the existing 500 ms duplicate-log cooldown.
The unchanged exact case reproduced the failure, and an enabled-state probe
reported the actual button as disabled. The test now waits for actual enabled
semantics and committed set count rather than sleeping, changing production
timing or weakening the duplicate-tap guard. Failed run/probe evidence is retained.

### Final commands, inventory and retained warnings

After the synchronization correction, both final commands completed unfiltered
with the scoped JDK/SDK, two Gradle workers and only one private emulator at a time:

```sh
ANDROID_SERIAL=emulator-5590 ./gradlew --offline --no-daemon --console=plain \
  :app:connectedDebugAndroidTest
ANDROID_SERIAL=emulator-5592 ./gradlew --offline --no-daemon --console=plain \
  :app:testDebugUnitTest :app:lintDebug :app:assembleDebug \
  :app:connectedDebugAndroidTest
```

Final source-to-XML reconciliation is **170 JVM + 74 API 34 native = 244 distinct
tests**, retaining all 190 previous methods and adding 54. The additional API 33
run passes **67 native tests**. All final reports have zero failures, errors and
skipped cases. Seven API-34-only ATF methods (six accessibility-device cases and
one backup case) are omitted by the SDK filter on API 33, not counted as skipped
XML cases. Every executed/source method is listed in `test-inventory.json`.

Build and lint pass with **0 errors, 18 warnings and 4 information findings**:
11 dependency-version, 1 obsolete SDK check, 4 state boxing, 1 unused resource,
1 monochrome launcher and 4 KTX information findings. No new dependency was added.
Native ATF has **0 unexpected errors, 25 contrast-warning occurrences and
11 duplicate-speakable-text information occurrences**. Eight contrast estimates
concern tested disabled controls; 17 concern the existing field-outline/text
estimator category. Exact lines and images remain available; not every instance
is independently pixel-certified or dismissed as a false positive. The
deliberately unlabeled negative control is still rejected and asserted.

The final APK is **11,001,141 bytes**, SHA-256
`997d1fce0a199b3f50a5d0a76ffee02ac9b0376cd3f0ba8213998f3add2a7686`.
It is the exact artifact used for actual upgrade/backup evidence; subsequent
test-only synchronization changes did not change it. The Android Debug
certificate remains
`d834bba6e23b6dc1dfab6f213ec474d0ec02dbd2699e1012824757c94642365b`.
No network permission, key, personal PDF, cloud publication or dependency/runtime
upgrade accompanies this delivery.

### Evidence scope and limits

Native captures cover both themes, library alias search, explicit unit choice,
empty input after switching, same-unit previous rows/prefill, mixed history,
correction, separate charts, and 200% real-keyboard library/plate entry.
The existing real-IME portrait/landscape geometry cases remain in the full suite.
All records are synthetic; no personal workout, PDF, signing key or competitor
artwork is included. Captures identify their actual theme, font scale, dimensions
and keyboard state. They are not physical Galaxy A71, Samsung keyboard,
one-handed reach or TalkBack speech acceptance.

## Reproducible environment

- Gradle 8.13, Android Gradle Plugin 8.9.2, Kotlin 2.1.20, Compose BOM 2025.04.01.
- JDK 21; Android compile/target 35, build tools 35.0.0; minimum API 26.
- At most two Gradle workers and a 2 GiB Gradle heap on the shared host.
- Official Android command-line tools build 13114758: SHA-1 published in Google's
  repository metadata `5fdcc763663eefb86a5b8879697aa6088b041e70`.
- Gradle distribution SHA-256:
  `20f1b1176237254a6fc204d8434196fa11a4cfb387567519c61556e8710aed78`.
- Gradle wrapper JAR SHA-256:
  `81a82aaea5abcc8ff68b3dfcb58b3c3c429378efd98e7433460610fecd7ae45f`.
- Temurin JDK 21.0.12.1+1 archive SHA-256 from Adoptium release metadata:
  `ce79869e1307ed8ee1e2baa86a412b1eb5b75d10a01006d788a6f968bcfaee94`.
- Private test emulator: API 33 default x86_64 image, 2 cores, 2 GiB RAM,
  1080 x 2400 display at 420 dpi, Wi-Fi and mobile data disabled. SDK image
  assets were copied from an approved read-only installation, not a user's AVD.

## Final 0.1.4 theme and workout hierarchy refinement

Light surfaces are now faintly warm near-whites, with ink primary actions and
restrained amber selection. All **29 explicit dark color tokens are unchanged**
from 0.1.3. Previous comparable working sets have dated, aligned Set/kg/Reps/RIR
rows, explicit loading units, a dash for absent RIR and labeled reflow above 130%
font size. Records longer than four sets have an explicit Show all action.
Comparison selection, prefill, working-set ordinals and persistence are unchanged.
Secondary actions retain visible outlines or labeled decorative icons, native
roles, contextual accessibility descriptions and 48 dp hit targets.

The three-model council reviewed the same six first-party reference sets and
neutral native Rebuild packet independently. It then reviewed the first candidate
once. Its concrete rest-day/history affordance concern was addressed and the
originating reviewer confirmed resolution. Scores remain subjective judgments,
not market rankings or interaction measurements. The ink light CTA was a recorded
design decision, not unanimous preference. No competitor artwork ships.

### Final commands and complete inventory

Both commands ran unfiltered, with the private JDK/SDK, at most two Gradle workers
and only one private emulator running at a time:

```sh
ANDROID_SERIAL=emulator-5592 ./gradlew --offline --no-daemon --console=plain \
  :app:testDebugUnitTest :app:connectedDebugAndroidTest :app:lintDebug \
  :app:assembleDebug :app:assembleDebugAndroidTest
ANDROID_SERIAL=emulator-5590 ./gradlew --offline --no-daemon --console=plain \
  :app:connectedDebugAndroidTest
```

**128 JVM + 62 API 34 native tests = 190 distinct tests**, all zero
failures/errors/skips. This retains all 172 previous cases and adds three theme,
seven comparison presentation, seven real-IME geometry and one action-affordance
case. The additional API 33 run passes **56 native tests**, also zero
failures/errors/skips. Six API-34-only validator methods are omitted by the SDK
filter on API 33, not reported as skipped XML cases. `test-inventory.json`
reconciles every source method against both final report sets.

The final suites ran after the measured-viewport correction below. Lint/build
pass with **0 errors, 18 warnings and 4 information findings**, the same retained
dependency/KTX/icon-resource/boxing categories as 0.1.3. No dependencies, database
schema, backup format, phase, plan revision, prescription or signing key changed.

### Large-text keyboard finding and correction

The same seven real-IME scenarios first passed on the preserved 0.1.3 APK and on
the initial candidate: portrait 360/411 dp at 100%, 130% and 200% text, plus
landscape at normal text. These check the whole Log action before keyboard after
scroll, every numeric field and Log above rest with keyboard, control hit bounds
and draft-preserving Back.

A later unfiltered run exposed an intermittent failure in the existing
`AdaptiveUiTest.largeTextNumericImeKeepsWholeEntryAndRestActionsVisible`.
Isolated repetitions reproduced it twice. One capture had only 27 of 168 px of
Log visible, still unchanged 500 ms later with IME bottom and target both 767 px.
This was a real settled obstruction, not dismissed as a transient screenshot or
OS dialog. The existing relocation request could race the actual viewport resize
when keyed to keyboard insets read during composition.

The correction keys the same complete-entry BringIntoView request to measured
scroll-viewport height. No padding, font shrinking, timer model, relaxed
assertion or extended timeout was used. The original exact case then passed
**12 consecutive repetitions**, followed by both final unfiltered suites.
Failure-only screenshot/geometry diagnostics remain in the test; the temporary
500 ms investigation probe was removed. Retained failure logs and images,
`geometry-measured-*.log`, and final reports distinguish the initial failures
from the final result.

### Final native visuals and contrast

Matched native `before-*` (0.1.3) and `release-*` (final APK) screenshots cover
paired dark/light Today, prior/entry/rest, history detail and initial 200% views.
They use the same synthetic INTRO_TWO history, targets and logged-set values.
Clock/countdown values advance; screenshots are not pixel-identical fixtures.
Explicitly scrolled 200% captures and real-IME captures show the reachable whole
entry. In the extra light IME capture rest has expired; the dark one is running.
All captures are API 34 at 1080 x 2400, 420 dpi, system theme, font 1.0 or 2.0.
Controlled-Tuesday scheduled-primary screenshots are separately labeled native
test-clock/minimal-template fixtures, not actual date/full-plan screens.

JVM tests cover all used light foreground/selection/error/focus/outline pairs,
both themes' secondary actions, and exact dark tokens. Native pixel evidence
and calculated pairs include ink/cream CTA **11.41:1**, secondary text on high
surface **7.09:1**, numeric focus **7.33:1**, and outline on the darkest light
surface **3.60:1**. These are specific pairs, not whole-app certification.

The six native API 34 ATF cases include a deliberately unlabeled clickable
negative control that must throw. Final logs retain **21 TextContrastCheck
warning occurrences and 9 DuplicateSpeakableTextCheck information occurrences**;
there are no unexpected validator errors. Eight contrast occurrences concern
disabled Material controls, demonstrated by screenshots/native disabled-state
assertions. Thirteen estimate outline `#79736B` as text. Exact correction-field
crops show actual value `#1F1B16` at **14.28:1**, label `#514C45` at **7.09:1**,
and outline at **3.91:1** against `#EDEAE6`. The representative correction fields
are demonstrated misclassification; other field instances remain explicitly
retained warnings, not individually certified false positives. The nine
information messages concern non-clickable titles repeated in navigation.
No warning suppression was added. `atf-evidence.json` retains exact identifiers,
per-case source logs, bounds, estimated pairs and screenshot pixel counts.

### Final APK upgrade and offline continuity

The preserved 0.1.3/code 4 APK restored a synthetic backup through Android Files
and saved the mandatory safety backup. It then logged a working set to establish
a live timer. The final 0.1.4/code 5 APK installed with `install -r`, without
uninstalling: **the complete SQLite file was byte-identical**, with integrity
checks and equality of every snapshot field, including custom overrides,
completed bodyweight history, active assisted working/warm-up sets, plan revision,
notes, local revision and rest deadline.

With Wi-Fi and mobile data disabled, one explicit prefilled 20 kg assistance x 4
working set was added on the new APK and started a new rest timer. After Home,
only that synthetic app's identified background process was terminated by PID;
relaunch recreated it with a new PID and exactly the same post-log snapshot and
rest. This is not a force-stop alarm-delivery test. `upgrade/comparison.json`,
`upgrade/offline-resume.json`, database files and native captures retain proof.
The final capture setup also exercised guarded actual Files restore on 0.1.4.

Final APK: **11,274,442 bytes**, SHA-256
`53e602e9a5e0985d353dd348292e3fceb66304f7341e9d63daa77d8c53e5d0e4`.
Its debug certificate matches all earlier deliveries. The hash stayed identical
across final API 34/API 33 runs, actual upgrade and final native captures.
The versioned local bundle includes source commit/archive, signature/manifest,
complete reports, before/after evidence and checksums; older bundles are intact.

Physical Galaxy A71 acceptance, actual TalkBack speech and one-handed reach
remain **unverified**. No further account/package searches were performed.
Native validator/semantics checks are not substitutes for those gates.

## Preserved 0.1.3 plan and icon update evidence

The Wednesday vertical pull is now Lat pulldown: 3 baseline sets, advisory upper
3, 8-12 reps and 180 seconds rest. Introductory/Saturday prescriptions and weekly
totals 54/66/86 are unchanged. A versioned, transactional migration updates only
the known future slot and its legacy pull-up overrides. It preserves edited
targets, custom substitutions, catalog loading identities, completed history and
already-started workout snapshots. Older backups normalize through the same
migration before replacement. New backups require 0.1.3 or later.

The rounded-outline navigation, utility, launcher and notification assets are
curated Lucide vectors; see [provenance and license](icons.md). Labels, routes,
selected pills and expanded touch targets remain. Removing the unused extended
Material icon library adds no replacement runtime dependency.

### Final commands and results

The commands below ran without class/method filters, using scoped JDK/SDK paths
and one private emulator at a time:

```sh
ANDROID_SERIAL=emulator-5592 ./gradlew --offline --no-daemon --console=plain \
  :app:testDebugUnitTest :app:connectedDebugAndroidTest :app:lintDebug \
  :app:assembleDebug :app:assembleDebugAndroidTest
ANDROID_SERIAL=emulator-5590 ./gradlew --offline --no-daemon --console=plain \
  :app:connectedDebugAndroidTest
```

**125 JVM + 47 API 34 Android tests = 172 distinct tests**, all zero
failures/errors/skips, retain the prior 159 cases and add ten JVM migration cases,
one native SQLite migration case and two native plan/icon cases. The additional
unfiltered API 33 run passed **41 Android tests**. Six API-34-only accessibility
methods are omitted by the SDK filter on API 33, not reported as six skipped XML
cases. `test-inventory.json` reconciles every source method with the final XML.
The APK hash stayed identical across both final runs and the upgrade proof.

API 34 used the official image/fingerprint documented below, 1080 x 2400 at
420 dpi. API 33 compatibility and fresh-install captures used 1080 x 2160 at
440 dpi. Tests also exercise large text and landscape. Both AVDs use two cores
and 2 GiB; no physical phone or unrelated emulator was changed.

Lint reports **0 errors, 18 warnings and 4 information findings**. The 15 previous
warnings remain. Three new nonblocking icon-resource warnings are retained:
the redundant `drawable-v26` qualifier at minSdk 26, the intentionally packaged
but code-unreferenced license, and the adaptive launcher lacking an Android 13
themed monochrome variant. Its normal adaptive icon was checked on the native
launcher; themed launcher support is not claimed. No warning suppression was added.

One emulator process aborted before testing. After its restart, a System UI ANR
dialog stole focus: two existing IME cases timed out before any geometry assertion.
The screenshot, window/input dumps and failing logs are retained. Dismissing the
OS dialog with Wait made the exact same two cases pass; the subsequent full run
passed without app/test changes, relaxed bounds, retries inside tests or longer
timeouts. This is not presented as an app defect fix.

### Actual old-install upgrade, restore and fresh installation

On the disposable API 34 device, the preserved 0.1.2 APK restored a synthetic old
backup using the real Android Files picker and mandatory saved safety backup.
The final 0.1.3 APK then installed with `install -r`, without uninstalling.
SQLite integrity checks and exact JSON comparisons verify:

- Only `planRevision`, the local revision, Wednesday's default/alternatives and
  its BASELINE legacy assisted override changed. The edited 210-second rest,
  unrelated intro override and custom FIVE_TWO substitution remained exact.
- Completed bodyweight history retained its null kg/3 reps. The active assisted
  workout retained its 30 kg x 5 warm-up, 20 kg x 4 working set, notes and all
  snapshot fields. Catalog records and loading identities were identical.
- Actual old-backup restore on 0.1.3 matched the normalized source, preserving
  those records. An actual new-format export and guarded restore also round-tripped
  all substantive fields; safety/dirty revision bookkeeping was checked separately.
- After explicitly finishing the legacy active workout, the next Pull used
  external-load Lat pulldown, 3 sets of 8-12 and the preserved 210-second rest.
  All entry fields were blank: no assistance/bodyweight load was copied.
- A fresh API 33 install independently produced the unedited 3/3, 8-12,
  180-second Wednesday slot with no prescribed alternatives or prior workouts.

The database is intentionally **not byte-identical** across this upgrade.
`upgrade/comparison.json`, `restore-comparison.json`,
`roundtrip-next-session.json` and `fresh-comparison.json` record the exact
accepted differences. The fixture and databases contain synthetic data only.

### Visual and accessibility boundaries

Nine before/after native state pairs cover home/navigation, workout, note, plan,
history, dark history, 200% navigation, rest and rest options. Full screenshots,
UIAutomator labels/bounds and comparison crops are retained; these are visual
comparisons, not identical-data pixel tests. A bounded self-check found coherent
24 dp, 2-unit rounded strokes, preserved selected pills/labels and unclipped
controls. Native tests assert actual expanded 48 dp touch bounds rather than the
40 dp visual IconButton layout. The upgraded next-session and fresh launcher
screens provide additional current-APK evidence.

All six API 34 accessibility cases, including the genuinely failing unlabeled
negative control, passed again. Full per-case ATF logs remain available:
21 TextContrast warning events (disabled controls/numeric outline estimation),
three repeated Start-action warning events, and nine non-clickable title
duplication information events. The Start warnings also occur identically in the
preserved 0.1.2 full backup test; they are a **residual pre-existing limitation**,
not suppressed or described as a new icon regression. Prior exact contrast
color-pair/state evidence remains in the 0.1.2 bundle; typography and colors did
not change here. The current event inventory retains each check identifier,
bounds and color estimate.

Physical Galaxy A71 acceptance and TalkBack speech/traversal remain unverified.
No further Store/account/package searches were performed. Existing near-cap
snapshot latency and the fail-safe stale safety-backup guard remain unchanged.

### Exact 0.1.3 artifact

`com.rebuild`, **0.1.3/code 4**, development/debug signed, **11,281,199 bytes**:

- APK SHA-256: `178e3308d27485948a73f0958531169719910cdabd8eaecd2d79b8c2bf7913fe`.
- Certificate SHA-256:
  `d834bba6e23b6dc1dfab6f213ec474d0ec02dbd2699e1012824757c94642365b`.
- APK v2 signature verified; the same private debug identity as 0.1.2.
- No INTERNET permission, runtime icon network dependency or personal PDF.

The local `delivery-0.1.3` bundle contains the APK, source commit, full reports,
native evidence, synthetic upgrade comparisons, manifest/signature and checksums.
The 0.1.0/0.1.1/0.1.2 bundles are preserved. PDF regeneration is a separate
coordinator-owned delivery; this worktree neither includes nor modifies it.

## Preserved 0.1.2 accessibility and regression evidence

The bounded accessibility pass changed only the exercise context in the
accessibility descriptions of repeated Add missed set, Exercise history and
Add/Edit exercise note actions. Visual wording, persistence, timer behavior and
workout workflows are unchanged. The only new dependency is test-only
`androidx.compose.ui:ui-test-junit4-accessibility`, resolved to **1.8.0** by the
existing BOM, with Accessibility Test Framework **4.1.1**. No runtime dependency,
minimum SDK, target SDK or AGP upgrade was made.

The final unfiltered run passed **115 JVM + 44 Android tests on API 34 (159
distinct tests)**, zero failures/errors/skips. An additional unfiltered API 33 run
passed all **38 existing Android tests**. The runner omits the six API-34-only
methods through `@SdkSuppress(minSdkVersion = 34)` on API 33; it does not report
them as six skipped XML cases. Every class and method was reconciled against the
source inventory. The original 153 correctness/UX cases remain.

```sh
ANDROID_SERIAL=emulator-5592 ./gradlew --offline --no-daemon --console=plain \
  :app:testDebugUnitTest :app:connectedDebugAndroidTest :app:lintDebug \
  :app:assembleDebug :app:assembleDebugAndroidTest
# After stopping the API 34 emulator and starting the private API 33 emulator:
ANDROID_SERIAL=emulator-5590 ./gradlew --offline --no-daemon --console=plain \
  :app:connectedDebugAndroidTest
```

Both commands used scoped JDK/SDK paths, no class/method filters, no `--continue`
and no skipped-test suppression. Lint remains **0 errors, 15 warnings and
4 information findings**: 11 newer-dependency notices, 4 KTX recommendations
and 4 primitive-state boxing notices. Both debug APKs assembled. The frozen app
APK hash remained unchanged after the API 33 run.

| Additional Android class | API 34 executed | Boundary |
| --- | ---: | --- |
| AccessibilityDeviceTest | 5 | Negative control; phase/home; field error/focus/Saving/rest; selected 200% entry/expired rest; history/note/correction |
| BackupAccessibilityDeviceTest | 1 | Production MainActivity restore preview, real written safety backup and guarded replacement confirmation |

The private API 34 Google Play x86_64 revision 14 image was installed through
official SDK Manager, limited to two cores and 2 GiB. Fingerprint:
`google/sdk_gphone64_x86_64/emu64xa:14/UE1A.230829.036.A4/12096271:user/release-keys`.
The published image SHA-1 is `01d32617fd1937e540faf8731d70cf50e35af854`.
No global Java/SDK, other project's emulator or physical device was modified.

The six focused cases also passed separately in light and dark, each `OK (6 tests)`.
They use the native Compose accessibility tree and explicit root/manual checks,
not Robolectric. The deliberately unlabeled 56 dp clickable fixture **really
throws** `AccessibilityViewCheckException` with `SpeakableTextPresentCheck`; its
test passes only after asserting that expected failure. A separate contextual
history-label regression failed before the production fix, then passed.

The validator throws on ERROR; warnings are retained, not blanket-suppressed:

- Repeated actionable `DuplicateSpeakableTextCheck` warnings prompted the fix.
  Final focused runs have only six informational repetitions for non-clickable
  Today/History destination titles matching navigation, not repeated action buttons.
- Final light/dark runs log 24/11 `TextContrastCheck` warning events, not unique
  defects. Disabled phase/Saving/Finish/unchanged-note controls account for the
  disabled-state warnings. Their actual disabled states are asserted/captured.
- In light numeric fields the image estimator identifies outline `#7E7667` as
  text, yielding 3.62 or 4.24. Actual screenshot pixels and theme tokens show
  value `#1F1B16` at **13.7825:1** and label `#4C4639` at **7.5391:1** against
  `#EBE6DE`; the outline itself meets the 3:1 non-text threshold. Full bounds,
  pixel counts, crops and reproducible calculations are preserved. This specific
  false-positive classification is not an exemption for future warnings.

The backup test uses actual production preview/safety state and file writes, but
substitutes private file URIs for the Android Files picker. Separate upgrade
verification below uses the real picker. Neither semantics nor validator checks
prove TalkBack speech. The official image lacks TalkBack; Google's release
artifacts offer source, not an installable APK, and the official Play Store
listing requires sign-in. The sign-in gate and installed-service inventory are
preserved. No credentials, account creation, third-party APK mirror or large
source build was used. **TalkBack speech/traversal and physical A71 acceptance
remain pending.**

### Exact 0.1.1 to 0.1.2 upgrade

After instrumentation, the preserved 0.1.1 APK was installed on the disposable
API 34 AVD and populated through actual Android Files restore, including the
mandatory saved current-data safety copy. The final 0.1.2 APK was then installed
with `adb -s emulator-5592 install -r`, without uninstalling between versions.

Every SQLite schema/data row, and the complete database file hash, was identical
before and after launch of 0.1.2: 32 catalog exercises, three workouts (two
finished with two sets each and one active with seven sets), phase and remaining
stored fields. The synthetic fixture contains no overrides or timer; no wider
fixture coverage is implied. Actual post-upgrade UIAutomator captures show the
new contextual labels for distinct history exercises.

Preserved artifact: `delivery-0.1.2/rebuild-0.1.2-debug.apk`, package
`com.rebuild`, **0.1.2/code 3**, **17,573,849 bytes**.

- APK SHA-256: `4f79230f72ab005dc7c9fc991e2e561d4e045f0b02c0173e67cdfff2f5cf46dd`.
- Same Android Debug certificate SHA-256:
  `d834bba6e23b6dc1dfab6f213ec474d0ec02dbd2699e1012824757c94642365b`.
- APK v2 signature verified; this remains a development/debug-signed candidate.
- No INTERNET permission. No key, personal PDF/profile, SDK or APK is committed.

The versioned local `delivery-0.1.2` bundle preserves the APK, source commit,
all final XML/HTML reports, reconciled inventory, commands, focused light/dark
logs/screenshots, negative control, warning triage, official-source provenance
and database upgrade comparison. Prior 0.1.0/0.1.1 deliveries and the UX audit are
unchanged. Only this pass's two private emulators were stopped; an unrelated
emulator present at final inspection was left untouched. No physical A71 was
attached.

The existing near-cap latency, fail-safe stale safety-backup guard and minor
identity/default duplication remain nonblocking follow-ups. No broad UX campaign
or architecture refactor was added.

## Preserved 0.1.1 automated evidence

The final unfiltered 0.1.1 run passed **115 JVM tests and 38 Android tests (153
total)**, with zero failures, errors or skipped tests. Every source test method
and class was reconciled against the XML reports, not inferred from build success.
All original 136 tests remain, with intended UI navigation updates, plus eight
typed-validation JVM tests and nine native UX/regression tests.

```sh
./gradlew --offline --no-daemon --console=plain \
  :app:testDebugUnitTest :app:connectedDebugAndroidTest :app:lintDebug \
  :app:assembleDebug :app:assembleDebugAndroidTest
```

The run used explicitly scoped JDK/SDK environment variables and
`ANDROID_SERIAL=emulator-5590`. No test selector, exclusion or `--continue` was
used in the successful final run. Both debug APKs assembled. Lint reported
**0 errors, 15 warnings and 4 informational findings**: 11 newer-dependency
notices, 4 KTX convenience recommendations and 4 primitive-state boxing notices.
Compatible versions remain pinned; warnings were not suppressed to pass.
The test-only production-window setup also emits a deprecated
`SOFT_INPUT_ADJUST_RESIZE` compiler warning. It mirrors the app's manifest on the
API 33 test host; it is not a skipped test.

| JVM test class | Executed |
| --- | ---: |
| BackupReminderTest | 10 |
| BackupTest | 11 |
| RepositoryTest | 23 |
| RestTimerTest | 8 |
| SnapshotCodecTest | 14 |
| SqlitePersistenceTest | 10 |
| SeedTest | 7 |
| UserTextTest | 4 |
| BackupViewModelTest | 7 |
| RestAlarmSchedulerTest | 8 |
| RestRecoveryTest | 3 |
| ActionRunnerTest | 2 |
| SetValidationTest | 8 |

| Android test class | Executed | Boundary exercised |
| --- | ---: | --- |
| RebuildAppTest | 20 | Real Compose runtime, fake repository/platform |
| AdaptiveUiTest | 7 | Whole-element geometry, real IME, landscape, safety actions, field errors, truthful extras |
| DatabaseDeviceTest | 4 | Real Android SQLite persistence and replacement |
| LargeSnapshotDeviceTest | 2 | Actual greater-than-2-MiB save/restore/reopen |
| UnicodeSnapshotDeviceTest | 4 | Native Unicode safety and near-limit read |
| ColdLoadDeviceTest | 1 | Production MainActivity and repository hydration |

JVM coverage includes exact phase/seed totals and schedules, caps, numeric
validation, snapshots, substitutions, warm-up filtering, comparable history,
idempotence, partial finish/resume, backup graph validation and atomic failure,
dirty reminders, deadlines, cancellation, denied permissions and recovery races.
Compose restoration tests preserve saved state while a write is pending, remove
the UI, let the write commit, then restore and append without overwriting.
These controlled restoration tests are not physical process-death claims.

Reports are generated at:

- `app/build/test-results/testDebugUnitTest/` and
  `app/build/reports/tests/testDebugUnitTest/`.
- `app/build/outputs/androidTest-results/connected/debug/` and
  `app/build/reports/androidTests/connected/debug/`.
- `app/build/reports/lint-results-debug.{html,xml,txt}`.

The local delivery bundle preserves reports, a source-matched test inventory,
the full final command log, selected red/green logs and screenshots.

## Confirmed review regressions and fixes

| Finding | Reproduction | Final fix and retained regression |
| --- | --- | --- |
| P1 large CursorWindow row | Both native large save/reopen and Unicode restore/reopen threw `SQLiteBlobTooBigException` | One read transaction, bounded 16-Ki-code-point chunks (at most 64 KiB per row), code-point offsets and cumulative UTF-8 limit; both native tests pass |
| P1 restored new-set identity | Legacy saved draft overwrote the first completed set; distinct IDs were 1 instead of 2 | Persisted-generation composition key plus committed-ID guard; retained 40 kg x 8 and appended 45 kg x 9 |
| P2 rest recovery race | Recovery of expired A deleted a newer B in a deterministic repository interleaving | Conditional timer-ID consumption under the repository mutex; scheduler reads current state inside its own monitor |
| Unicode corruption | A 4095-code-unit prefix plus truncated emoji saved, then native reopen failed with an incomplete JSON string | Shared surrogate-safe UI limit and codec validation of every text/ID field; malformed imports reject before mutation, valid boundary emoji survive reopen |
| Restored missed-set dialog | After a background commit, restored dialog closed but the next enabled Add action did not open a fresh editor | Saveable per-add request identity, renewed only on explicit Add; restoration closes the committed request, then a distinct new set can be added |

The three original spec fixes also received an independent read-only follow-up
review. Execution evidence above was produced separately, not claimed by that
reviewer.

Standards A, B and E were confirmed and fixed: phase changes are visibly disabled
during an active workout; note drafts have guarded navigation and sequential
saving; UI bounds accept the same kg/rest/set ranges as the codec. A chained-note
save regression also checks that the action runner remains busy for the second
write. The exact new-set cooldown boundary is covered at 100/499/500 ms, alongside
a real 100-ms Compose double tap.

Identity/default duplication (C/D) remains a small maintainability follow-up, not
a speculative refactor in this candidate. The stale safety-backup guard (F)
remains fail-safe: timer-only revisions can require canceling and repeating the
restore flow with a fresh safety backup. No revision guard was weakened.

## Native UX fixes in 0.1.1

| Audited issue | Implemented result and evidence |
| --- | --- |
| Large text | Wrapping headers, compact rest and scrollable dialog bodies with stacked actions. At 360 dp/200%, real restore buttons changed from 76 px overlap to a 12 px (4 dp) gap. The complete partial-finish reassurance is reachable. |
| IME and horizontal insets | Rest remains above the keyboard and outside landscape navigation. Whole fields, Log and 48 dp rest hit regions pass native geometry assertions. Landscape retains exercise/loading context, places fields and Log side by side and temporarily hides secondary chrome; Android Back restores navigation without changing input or data. |
| Logging hierarchy | Target/comparable results/entry precede the growing set table. Baseline/advisory/rest settings are disclosed on demand. Target completion promotes Next; extras remain explicit and unrestricted. A real prefilled repeat took one Log tap and zero scrolls. |
| Validation and saving | Typed field errors preserve input and focus the invalid field; Saving is separate from the idempotency cooldown. Existing rapid-tap, pending-write restoration and chained-note regressions remain. |
| Read-first history | Results and saved notes are content, with explicit correction/Add/Edit actions, lazy history lists, larger chart labels and exact-data text alternatives. |

The native matrix sampled 360 and approximately 411 dp, font scales 1.0/1.3/2.0,
light/dark, actual numeric IME shown/hidden and landscape. It included denied and
allowed notifications, active/expired rest, prior/no prior results, target
completion, errors, notes, substitutions, missing-set restoration and guarded
backup replacement. It was representative, not every screen in every Cartesian
combination. Landscape/IME geometry was asserted at normal font scale; large-font
IME geometry was asserted in portrait.

Two independent review regressions were reproduced and retained: production
Adjust today/Custom exercise dialogs must have only one scrolling owner, and
clearing a retained exercise-note draft must not close its editor. A restored
target-complete draft test now explicitly opens the extra-entry form before
checking that restored values replace stale input.

A fresh bounded read-only design check found no remaining must-fix in ten
representative native captures. This is a materially more usable, clean native
candidate, not proof of universally optimal UX. TalkBack was not installed on the
API 33 AVD: semantics/focus checks are not speech testing or WCAG certification.
The later bounded API 34 pass is documented separately above.

## Original 0.1.0 production evidence

The offline workflow was also exercised through the actual app and Android Files,
not just fake repositories: manual Tuesday selection on Sunday, warm-up and
working-set logging, saved notes, rotation, ordinary process death, screen-off
rest expiry, partial finish/history and file export/restore. A separate final
repeat verified delivery while Android's swipe keyguard was actually showing.

For these checks, the app was backgrounded and its exact PID terminated on the
disposable emulator, not force-stopped. A cold receiver process delivered the
notification. Android recorded `sound=null` and vibration
`[0, 200, 120, 200]`. This proves scheduling/notification behavior on that emulator,
not that a physical Samsung vibrated or will deliver under every OEM setting.

The Android Files round trip compared all substantive state after export,
preview/cancel, mandatory safety backup, explicit replacement and re-export:
31 catalog exercises, phase, one finished workout, 40 kg x 8 and a saved note
were preserved. The safety copy retained the intervening baseline-phase change.
Restored timers did not replay.

The exact final APK was then installed and exercised offline again: fresh phase
selection, manual Tuesday start on Sunday, 40 kg x 8 and the note
`Final candidate smoke`, a 30-second screen-off rest deadline, ordinary process
death (PID 11194) and cold receiver delivery (PID 11822), resume and partial finish.
Its Android Files export, mandatory safety copy, replacement and re-export
matched all substantive fields, excluding only revision/backup bookkeeping.
There was still exactly one performed set, 31 catalog exercises and no active
workout or rest timer. Screenshots 07–12 and `final-round-trip-comparison.json`
in the delivery evidence correspond to this exact APK.

The initial AVD had keyguard disabled, so those runs must not be described as
keyguard-lock tests. Swipe keyguard was then enabled on this disposable emulator
only. The final 35-second repeat killed PID 13935 and received the alert through
cold PID 14319. Android window-policy dumps confirmed `showing=true` and
`mIsShowing=true` both while the screen was off and after waking it. Screenshot
09 shows that final keyguard state; the policy dumps and
`keyguard-final-comparison.json` preserve the distinction. This was non-secure
swipe keyguard, not a PIN/biometric or physical Samsung test.

**Measured near-cap latency:** a valid whitespace-padded seed of 10,484,736 UTF-8
bytes took 7374 ms to reopen/read and 58 ms to decode on the two-core API 33
emulator. Repeated runs measured 7472/54 ms and 7554/51 ms. The padded seed isolates
large snapshot reading; it does not represent all real histories or devices.
The correctness-first transactional chunk format was retained rather than
redesigned late. The production cold-load test verifies a visible loading state,
no phase/start/log controls before hydration, exact loaded state and no reseeding
or rewriting of the padded snapshot.

## Final 0.1.1 production evidence

After all instrumentation, the preserved 0.1.0 APK was installed on the disposable
AVD and populated through Android Files with a synthetic backup. Then the exact
0.1.1 APK was installed with `adb -s emulator-5590 install -r`, without uninstall.
Every SQLite schema/data row was identical before and after upgrade: two finished
workouts, one active workout with seven working sets, phase, custom exercise and
the remaining stored fields. This fixture had no plan overrides; broader override
round-tripping remains covered by repository tests.

On that exact APK, an explicit extra set became the eighth working set. A
40-second rest was armed with notifications allowed. After backgrounding, only
the app's PID 13594 was terminated; cold PID 14226 delivered the rest notification
while window-policy dumps confirmed actual non-secure keyguard `showing=true`.
The notification had no sound and vibration `[0, 200, 120, 200]`. This is emulator
notification evidence, not proof of physical vibration or Samsung delivery.

The app resumed all eight sets, saved a workout note, and finished partially with
the full reassurance visible through scrolling at 360 dp/200%. Final Android
Files export, mandatory safety copy, replacement and re-export matched every
substantive field: 32 catalog exercises, three finished workouts with 2/2/8 working
sets, the note and phase, no active workout or replayed timer. Only revision and
backup bookkeeping changed. The old APK and baseline audit were preserved.

The preserved artifact is `delivery-0.1.1/rebuild-0.1.1-debug.apk`,
package `com.rebuild`, version `0.1.1` / code 2, 17,945,761 bytes.

- APK SHA-256:
  `eac3ce2c447ec564e3cbfe518b9f9db9108622d8fe1b20b2943b5151cf827007`.
- Development/debug certificate SHA-256:
  `d834bba6e23b6dc1dfab6f213ec474d0ec02dbd2699e1012824757c94642365b`.
- APK v2 signature verified; this is not production release signing.

No private key, SDK installation, source health PDF or generated APK is committed.
No physical Galaxy A71 was attached at final device inspection.

The versioned `delivery-0.1.1` bundle contains the exact APK, signature, source
commit, full reports/method inventory, native screenshots and JSON comparisons.
Known nonblocking limits remain: near-cap snapshot reads around 7.4 seconds,
same-day chart dates that may repeat, a scroll to secondary warm-up controls in
landscape, minor identity/default duplication, and the intentionally fail-safe
stale safety-backup guard. No dependency or storage redesign was added.

## Galaxy A71 acceptance checklist

No authorized physical device was attached at the initial ADB inspection.
Keep this checklist pending until the owner runs it on the actual phone:

1. Record Android/API version and confirm the APK signature matches any prior
   installation. Install with `adb -s SERIAL install -r APK`, without uninstalling
   or deleting existing app data.
2. Disconnect network or use airplane mode. Choose a phase and start today's
   session; manually select a different workout on a rest day.
3. Log a warm-up and working sets with kg, reps and optional RIR. Confirm warm-ups
   do not satisfy working targets or start rest. Confirm prefilled fields are
   not marked performed until the completion action.
4. Rotate, background, lock and reopen. All completed sets, notes and the one
   active session remain. An unsubmitted text field is not a completed set.
5. With notifications allowed and exact-alarm access granted if available, log
   a working set, lock the phone and verify the silent vibrating expiry alert.
   Test permission denial as well: logging still works, limitation/remedy is
   visible. Inspect Samsung battery/sleeping-app and Do Not Disturb settings.
6. Adjust and skip rest; finish partial or discard explicitly. Obsolete alarms
   must not alert. Normal process recreation should preserve data. Force-stop
   is a separate Android restriction and is not an alarm reliability test.
7. Finish and inspect workout/exercise history. Correct a completed set and
   confirm history/chart values change. Check different variants/loading types
   stay separate and warm-ups are excluded.
8. Edit a future plan and substitute an unlogged exercise today. Verify today's
   changes remain today-only unless saved explicitly, past snapshots stay fixed,
   advisory-cap warnings can be acknowledged, and actual extra sets can be logged.
9. Save a backup using Android Files to an owner-controlled location. Change
   some data, choose that backup, preview it, save the mandatory current-data
   safety backup and confirm replacement. Verify phase, custom exercises, plan
   edits and workouts round-trip. Restore must not replay old rest alerts.
10. Cancel a restore and try corrupt/unsupported files. Existing data must remain
    intact. Verify the app can still start/log/finish/reopen without network.

Do not wipe a phone or run destructive automated test fixtures against its real
database. Export a private safety copy before manual acceptance testing.
