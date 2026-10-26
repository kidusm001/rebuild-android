# Rebuild user guide

How every part of Rebuild works, in detail. The [README](../README.md) is the overview; [DESIGN.md](../DESIGN.md)
records why each surface looks the way it does.

## Using the plan

Select your phase explicitly. Four-day phases train Tuesday, Thursday, Saturday
and Sunday; five-day phases add Wednesday and replace introductory Tuesday with
Push. Monday and Friday are rest days. Suggestions use the current local weekday
and this week's checklist, not missed-workout debt. Any phase-appropriate session
can be selected manually.

The first phase uses one working set at about 4 RIR; the next four-day phase uses
two at about 3 RIR. Five days starts with two at about 3 RIR. Approach baseline
gradually, adding only 2–4 total weekly sets as recovered, usually near 2 RIR.
Nothing advances automatically. Baseline is 66 working sets, all exercises at two
is 54, and all optional caps sum to 86, **not a weekly target**.

Wednesday's prescribed vertical pull is **Lat pulldown, 3 baseline sets of 8–12,
advisory upper 3, 180 seconds rest**. Earlier phases retain their normal set/RIR
targets. Choose a controllable load, keep about two clean reps left at baseline,
and build reps before adding weight. No pull-up ability is required.
Introductory and Saturday pulldowns remain unchanged.

The earlier plan migration updates only the future Wednesday prescription and
legacy pull-up overrides when upgrading or restoring a pre-migration backup. Existing custom substitutions and
edited targets remain; completed and already-started workout snapshots, loading
types and weights never convert. The home screen explains the next-session change
when resuming an old Pull workout. Legacy pull-up catalog identities stay valid
for history, not as prescribed alternatives. Migration is recorded once and marks
the changed plan as needing backup.

Logging saves actual completed sets, never planned or prefilled sets. Loads are in
kg or lb, chosen per exercise (More › Units sets the default), or plates per stack on
machines; dumbbell loads are per dumbbell. Bodyweight, assistance and external load are
separate identities. Warm-ups do not count toward working targets or comparisons.
Today's edits remain local to that workout unless explicitly saved to the plan.
Existing workout snapshots and past exercise definitions are not rewritten by
future targets or [plan edits](#flexible-workouts). Optional caps warn about plan changes but do not limit actual
set logging.

Only one workout can be active. Resume it, finish partially or explicitly discard
it before starting another. A fresh **Resume workout** opens the first exercise still
below its working target (the first exercise once every target is met); Today names
it before you tap. An exercise you already have open stays selected, and you can
pick any exercise or finish partially at any time. Completed sets and explicitly
saved notes are durable.
Note drafts survive switching exercises; leaving an editor prompts to save,
discard or keep editing. Unsubmitted numeric input is not a completed set.

Each exercise reads as a set-by-set logbook: **Last time** (dated, same unit only)
beside **Today** for every working set. Today shows what you actually saved, with
its own unit and a correction action; sets not logged yet are dashed placeholders
labeled Up next or Not logged, never numbers. Warm-ups are listed separately and
never paired or counted. On a roomy portrait screen the next-set entry and **Log
set** stay docked at the bottom; with large text, in landscape or on short windows
the same entry sits inline and scrolls with the page, keeping any typed values.
Target details contains baseline, advisory upper, today's adjustment and rest.
After the chosen target is logged, Next becomes the primary action; **Extra set**
still allows truthful extras.
History opens as results, grouped by month, with explicit note editing and set corrections.
Repeated history actions include the exercise name in their accessibility label.
Navigation and utility controls use a small, consistent rounded-outline
[Lucide icon set](icons.md), with the same labels, selected indicators and
48 dp touch targets. No runtime icon library or network access is added.

Light mode uses near-neutral warm surfaces and ink primary buttons; the existing
warm charcoal/amber dark palette is preserved. Both follow the system appearance.
Loads name kg or plates per stack on every row, with a dash for unrecorded RIR.
Large text turns ledger rows into labeled blocks; Show all exposes older sets from
last time. Comparison, prefill, manual logging and the meaning of stored workout
data are preserved.

## This week's checklist

Today lists the current phase's workouts for this week, **Monday to Sunday** by
the phone's calendar and time zone, with the date range and "Done X of N". Each
workout counts once a week, whatever day you do it: doing Lower A on a Push day
ticks Lower A and leaves Push not done. The schedule and past dates never shift.
Workouts are matched by plan routine, not by name, so introductory Upper
(Tuesday) and Saturday Upper are separate entries, and each copy of
[your own routine](#your-routines-and-the-weekly-schedule) on a week is its own
entry. A workout belongs to the week it
**started** in, so one started late on Sunday and finished after midnight
counts for that Sunday's week only.

A finished workout counts by itself when it met every working target and
[measured goal](#time-distance-and-cardio) saved in it with real work.
A finished drop or rest-pause set counts as one set. Empty, warm-up-only and
discarded workouts never count. A finished partial workout with real working sets
or efforts, on any of its exercises, shows **Partial · not counted** until you choose
**Count as … done for its week**, either when finishing or later in History. That
choice never adds or changes sets, and you can undo it. Repeating a done workout is
allowed and never counts twice.

Today offers the scheduled workout until it is done this week. After that it
offers the first unfinished workout in plan order: a suggestion, not a readiness
claim. Rest days stay rest days, with unfinished workouts listed below to start
only if you choose. When every workout is done, Today shows **Week complete**:
rest, or repeat one.

Nothing is stored as a counter or reset. The list is recomputed from dated
workouts, so a new week simply starts on Monday at 00:00. Earlier weeks stay in
History, and missed workouts never carry over as debt. Changing phase shows that
phase's workouts. Earlier workouts keep their phase; any done this week that
aren't on the new list are named under "Also this week". Workouts recorded before
0.1.7 were dated once, at upgrade, by the local day they started in the phone's
time zone; their timestamps are unchanged.

## Flexible workouts

**Start empty workout** (Today, under Outside the plan) opens an extra workout
with no routine and no targets. Add exercises as you go and log sets straight
away. **Goal and rest** gives an exercise an optional goal and its own rest.
Extra workouts appear in History and exercise progress like any other workout,
but never count on the weekly checklist; Today lists them as "Outside the plan
this week". Finishing needs at least one logged set or effort. Warm-ups alone are enough:
the workout is saved to History with them and shows 0 working sets; warm-ups start
no rest, and Today's line names only extra workouts with working sets. An empty
extra workout can only be discarded.

During a planned workout, **Add exercise** (after the exercise pills, or in More
workout actions) appends an exercise from the shared library picker. It is
**optional today**: it never blocks finishing, never counts toward the routine's
own targets and has no target unless you give it an optional goal. Its working
sets are real effort, so if they are all a partial workout has, you can still
choose Count as … done for its week; it never makes the routine done by itself.
Choosing an exercise that is already in the workout opens that entry instead of
adding a copy. **Reorder exercises** moves exercises up or down for this workout
only; the plan keeps its own order. **Remove from this workout** takes an added
exercise out. If anything is logged for it, the confirmation names its sets,
stages and warm-ups, which are deleted only when you confirm.

A planned exercise still below its target can be skipped with **Skip today ·
target stays**. Its target and any logged sets stay, it shows as skipped, and
Resume and Next pass over it. **Undo skip** brings it back. A workout with a
skipped exercise finishes as partial and counts for its week only if you choose
Count as … done.

**Save to *routine* plan…** on an added exercise asks you to review its working
sets, rep range (or a [measured exercise's goal](#time-distance-and-cardio)) and rest, then adds it as a required exercise to future workouts
of that routine in this phase. Today it stays optional, and started and finished
workouts keep what they had. In **Plan**, **Add exercise to *routine*** does the
same directly; the targets are required. Tap an added exercise to edit it or
remove it from future workouts. An original exercise can also be removed from
the phase's future workouts and restored later. A routine always keeps at least
one exercise, and a workout or routine holds at most 100.

An added exercise's rest is the rest last used for that exercise, else its rest
in the plan, else 90 seconds. Goal and rest shows where the suggestion came from
and lets you change it. When nothing settles how a one-leg exercise's reps are
counted, Rebuild asks once: per leg or not. The answer stays with that entry,
comparisons only use sets counted the same way, and reps are never converted.

In History, **Add forgotten exercise** adds an exercise you did but didn't log,
then its missed sets. It's saved as added that day: no timer starts, the plan
doesn't change, and it never turns a partial workout into a done one by itself.
Count as … done needs at least one finished working set or logged effort on any
exercise, added ones included, and stays your choice. An added exercise can also be removed from
History, with the same confirmation.

## Your routines and the weekly schedule

**Your routines** (from Today, Plan or More) is a library of routines you build,
shared by every phase. **New routine** opens an editor: name it, add exercises
from the exercise library, and give each one working sets, a rep range and rest
(a timed, distance or cardio exercise takes its goal instead of a rep range).
Move up, Move down and Remove (with Undo) arrange the list; choosing an exercise
that is already listed opens it instead of adding it twice. Rest starts at the
exercise's last-used rest, else its rest in the plan or another routine, else a
visible 90 seconds you can change. In History, **Save as routine** on a finished
workout opens the same editor with its exercises in workout order and only the
targets that workout had. The loads, reps, times and distances you logged never become targets, and
the workout itself, including its weekly credit, never changes. A routine can't
be saved until every exercise has its targets and is listed once.

A routine is optional until you schedule it. **Start as extra session** runs it
outside the plan: it shows in History and progress but never counts on the
weekly checklist, including after it is saved as a routine or scheduled.
**Add to a weekly plan** (or, in Plan, **Add a routine to the … week**) asks for
a phase and a day and puts an independent **copy** there, with its own settings.
If that day already has a routine, choose **Add alongside** or **Replace** one
named routine; nothing is replaced, and no second workout is assumed, without
your choice. The same routine can go on several days: each copy is a separate
checklist entry with its own credit, and two on one day are told apart as "1 of
2" and "2 of 2". When a routine with copies on this week is started from the
library, you choose which day's workout it is, or an extra session; nothing is
preselected. Starting from the checklist already says which one.

A copy is never changed by editing its library routine. When they differ, the
routine and the copy in Plan offer **Update**, which replaces the copy's name and
exercises after confirmation. **Edit routine** on a copy in Plan changes that day
only, and Plan's add and remove exercise actions work on copies as on original
routines. **Change day** moves a copy, with the same Add alongside or Replace
question. **Take off this week** removes any routine from the phase's week; a
week always keeps at least one routine, so the last one can only be replaced.
Originals taken off are kept with their plan and listed in Plan with **Put back**.
Deleting a library routine keeps its copies on weekly plans and every workout.

Schedule changes apply at once, to future starts and to this week's checklist:
adding a routine to a complete week makes it incomplete again, and taking a
routine off removes its row. Started and finished workouts never change. A
workout whose routine has since left the week is still finished and kept, but
no longer ticks the checklist; an original's credit returns if it is put back.
Copies, removals and the library are part of the backup.

## Drops and rest-pause

A **drop** is part of the set before it, never a set of its own. After a set, tap **Add drop to set N** (on the
Target done panel, or first in the set-type menu) and log the lighter load. The set keeps its own row (load × reps,
RIR and its pencil), the drop is listed beneath it as "↳ Drop 30 × 8", and the working-set count doesn't change.
The next entry stays a drop, with empty fields, until you log a normal set, choose another type or tap Cancel, so a
second drop is never logged as a new set. Each drop restarts the set's rest.

A **rest-pause** set is one set with short pauses between mini-sets. Choose **Rest-pause** in the set-type menu
before its first stage, then **Add mini-set** for each one, and **Finish set**. It counts as one working set. Its
rest starts when you tap Finish set, and **Start 20 s pause** is an optional short timer between mini-sets, not the
rest between sets. While it's open, other exercises offer a way back to it or to finish it; finishing the workout
finishes it with the stages logged, and an open set survives app restarts. The load unit stays fixed while it's open.

These are logging options, not advice. Every drop and mini-set is saved at once with its own load, reps and optional
RIR; later ones are never generated. Tap a set to open **Set N and its drops**: correct any of them, add a missed
one, change the type, or delete the whole set. Corrections never start a timer. A drop logged as a set of its own in
an older version can be made part of the set before it from there: **Make them drops of set N**. Charts, records and
the finish receipt read each set by its own first stage; drops are never plotted. A set holds at most 20 stages.

## Time, distance and cardio

The library's **type** filter finds 14 measured exercises beside the 60
rep-based ones: timed holds (Plank, Side plank, Weighted plank, Dead hang, Wall
sit), loaded carries (Farmer's carry, Suitcase carry) and cardio (Treadmill,
Stationary bike, Rowing machine, Elliptical, Stair climber, Walking, Running).
A custom exercise chooses how it is measured when it is created: Sets of reps,
Timed sets, Distance sets or Cardio. Measured exercises are added to workouts,
saved to the plan and put in routines like any other; a planned one is swapped
only for another measured the same way.

Each effort records only what was measured. A timed set has its time and, if
loaded, its kg or plates. A distance set has its distance and optionally its
time and load. Cardio records time, distance or both, never a load. Times are
minutes and seconds, up to 24 hours. A distance keeps the unit it was entered
in (m, km or mi), with at most three decimals, and is never converted or
rounded. With no earlier distance or goal to follow, the unit starts at metres
for carries and kilometres for cardio; it is always visible and changeable.

A timed or distance goal is a number of sets that each reach a minimum, shown as
"Reached 0:30: 2 of 3 · 1 under, not counted". A shorter effort is logged and
listed but doesn't count toward the goal. A cardio goal is a total time, a total
distance or both; logged efforts are added up exactly, across units, for
example "Logged: 12:00 of 20:00 · 1.5 km of 3 km · 2 efforts". Without a goal
the efforts are simply listed. An added measured exercise is optional like any
other; Save to plan and routines need its goal. Logged times and distances never
become goals.

The entry offers the last effort's load and distance unit (this workout's, else
the same position last time, in the same load unit), never a time or distance.
Rest starts when a timed or distance set is logged. Cardio never rests by itself:
**Start rest** after an effort starts the exercise's rest when you want one, for
example between intervals.

Today's efforts sit beside last time's, like sets. History lists every effort as
logged and charts each measurement separately: time per set or effort, distance
in each unit it was entered in, and a hold's or carry's load. No pace, speed,
calories, combined totals or scores are calculated. In History, **Add missed
timed set** (distance set, effort) records one afterwards. Corrections and
missed efforts never start a rest or the stopwatch.

### Activity stopwatch

**Stopwatch** beside Log times an effort instead of typing it. Pause and Resume as
often as needed; paused time never counts. **Stop** fills the time into the entry
for review, where you can add its distance or load. Nothing is logged, and no
rest starts, until you tap Log; **Discard** asks before dropping the time.
Starting or resuming the stopwatch ends a running rest, and while it is timing
nothing else is logged in that workout. One effort is timed at a time. Other
exercises show a strip back to it, and other screens show a bar with its time,
Pause or Resume, and Open.

The stopwatch reads the phone's since-boot clock, which keeps counting in deep
sleep and ignores wall-clock and time-zone changes, together with Android's boot
count. Leaving the app, locking the phone or Android ending the app's process
doesn't lose time. It has **no notification, alarm or foreground service**, so
nothing alerts you while it runs. If it was running when the phone restarted,
when a backup holding it was restored, or whenever the same boot can't be
confirmed, it stops without a time: the review says when it started and what was
timed before it last resumed, and you enter the time or discard it. Nothing is
estimated. One that ran past 24 hours keeps its reading, but its time must be
typed. Finishing a workout while the stopwatch holds an unlogged effort asks you
to log it first or tick **Discard the stopwatch's effort**.

## Exercises and cable plates

Open **Exercises** from More, or **Browse exercises** from Plan. The library holds
60 common rep-based exercises and [14 timed, distance and cardio
ones](#time-distance-and-cardio), with type, equipment and primary-muscle filters,
name/alias search and custom exercises. Browsing never adds sets or changes the plan.
**All exercises** in Edit plan or Adjust today, Add exercise during a workout,
Add exercise to a routine in Plan and Add forgotten exercise in History use the
same picker; the future-plan/today-only guards still apply.
Custom exercises can leave metadata unknown, or explicitly select their equipment
and primary muscle. Existing custom metadata is editable from its library detail.
Saved sets are never reclassified; an active exercise whose equipment was unknown
can explicitly choose its newly configured cable unit without deleting kg sets.

For cable exercises or explicitly selected selectorized-stack equipment, use
**Load unit** near the active exercise entry to choose kg or **plates per stack**.
An explicit choice is remembered for future workouts of that exercise. Enter
whole physical plate counts from 1 to 1000; fractional, blank, zero and negative
counts are rejected. On dual-stack equipment, enter one stack's plate count,
not the combined number on both stacks. Use the same machine for comparisons:
stack sizes and pulley ratios vary, and Rebuild never estimates a kg conversion.
This is not a barbell plate calculator or plate-based assistance.

Each set stores its own unit. You can switch during a workout without converting
earlier sets; unsaved load input resets or prefills only from matching-unit sets.
History corrections affect the chosen set, not the exercise's future preference.
Previous results and chart tabs separate kg from plates, including rep series.
Warm-ups remain excluded from working-set comparisons.

The version-six backup format (timed, distance and cardio exercises, their
efforts and goals, and the stopwatch) requires **Rebuild 0.1.10 or newer**. Older
apps reject it, leaving their data unchanged, rather than dropping those parts.
Version-five backups from 0.1.9 restore with their saved routines, scheduled
copies and routines taken off a week; the version-five format requires 0.1.9 or
newer. Version-four
backups from 0.1.8 restore with their extra workouts, added and skipped exercises
and plan additions and removals; the version-four format requires 0.1.8 or newer.
Version-three backups from 0.1.7 restore with their drop and rest-pause sets,
start dates and weekly credit. Version-one and version-two backups remain
importable as normal sets. Their numeric weights stay kg, plate counts stay
plates, and bodyweight sets still have no load. The catalog updates add missing
entries, including the 14 measured exercises, without replacing edited or
custom records or changing workout prescriptions. New metadata and unit defaults
are a versioned migration, not a claim that the database is byte-identical.

Rest stays above the numeric keyboard. Large text uses wrapping headers and
scrollable confirmation bodies with separate actions. Dialogs pan for the keyboard
as before: the field being typed stays visible, Done on a goal dialog's last
field saves, and the dialog's buttons are whole once the keyboard closes. In landscape with the
keyboard open, exercise/loading context, fields, Log and compact rest take
priority; Android Back restores the full exercise picker and other controls.

## Rest alerts and Android limits

A newly logged working set starts the exercise's rest period (compound default
180 seconds, isolation 90; an [added exercise](#flexible-workouts) uses its own). For drop and rest-pause sets, the rest starts when you
tap Finish set. A timed or distance set starts it when logged; cardio only when
you tap Start rest, and starting the stopwatch ends it. Warm-ups, stages and edits to existing/history sets don't start it.
The app persists a deadline and schedules an Android wakeup alarm. Adjusting or
skipping replaces/cancels the old alarm; finish/discard cancels outstanding rest.

Grant notifications for a silent vibrating notification, and optionally exact
alarm access. Without exact access Android schedules best-effort delivery.
Battery saver, Samsung sleeping-app controls, Do Not Disturb, notification-channel
settings and force-stop can suppress or delay delivery. Logging and the on-screen
timer never require permissions. **Force-stop is not ordinary process recreation
and blocks Android alarms until the app is reopened.** No perfect timing guarantee
is made. Rest alerts from restored backups never replay, and a stopwatch that was
running in a restored backup comes back stopped, for you to enter its time.

## Backups and privacy

Save a full versioned JSON backup using Android's file picker, or explicitly share
it with an app of your choice. The file includes exercises, phase, plan edits,
workouts and settings needed to round-trip. It is private **unencrypted** data:
choose a local location or a destination you trust. Rebuild itself has no network
access. Android cloud backup and device-transfer extraction are disabled.

Restore validates a file, shows a preview, requires a successfully saved backup of
current data, then asks for explicit replacement confirmation. It replaces,
never merges. A stale safety backup, cancellation, invalid file, unsupported
version or failed database write leaves existing workout data intact. Imports
are bounded to 10 MiB and reject malformed Unicode as well as invalid references
and numbers. A timer update can stale a safety backup: cancel and repeat the
restore flow with a fresh current-data backup rather than bypassing this guard.
A share-sheet launch cannot prove a file was saved, so it
does not clear the weekly unbacked-up-change reminder.

Near-limit snapshots can take several seconds to open. A 10,484,736-byte padded
fixture took about 7.4–7.6 seconds to read on the test emulator, before decoding.
The app shows a loading screen and does not expose editable/default data until
the saved state is ready. This is a known limit, not a timing guarantee for other
devices or real histories.

## Pounds, bodyweight and measurements

**Units.** Any exercise that takes a load records in kg or lb; machines with a defined stack also offer plates per
stack. Switch with **Load unit** under the exercise name. A set keeps the unit it was lifted in and is never
converted, and Last time, records and gains compare only sets of the same unit. More › Units sets the default for
new and unpinned exercises.

**Bodyweight.** Log a weigh-in from Today, the finish receipt or History › Body, in kg or lb, with an optional note.
History › Body › Trend and weigh-ins shows every reading as a hollow dot and the weekly average as a line. The average
gives each day equal weight, and the line breaks over a missed week instead of bridging it. The summary leads with
this week once it has three weigh-ins, otherwise with last week, and shows the change over four weeks only when both
weeks have readings.

**Measurements.** **Measure this week** is one form for your chosen sites (waist at the navel by default; neck,
shoulders, chest, hips, upper arm, thigh, calf and the WHO waist midpoint under Sites), in cm or in. Each site has
its own chart and dated list with the change since last and since first. Measuring again in the same week corrects
that week. No body-fat percentage is estimated.

**Charts.** Every chart can be touched: press or drag to select the nearest point. A readout above names it, for
example "83.7 kg average · Mon 31 Aug – Sun 6 Sep · 3 days". With TalkBack, a chart works like a slider.

**History** opens on a month calendar of finished workouts and the working sets per primary muscle for any week.
Each exercise lists its **Records**: the heaviest set actually lifted at each rep count up to 15. Nothing is
estimated.

## Tests and physical release gate

```sh
./gradlew --no-daemon :app:testDebugUnitTest :app:lintRelease :app:assembleRelease
ANDROID_SERIAL=YOUR_TEST_EMULATOR_SERIAL ./gradlew --no-daemon :app:connectedDebugAndroidTest
REBUILD_SCREENS=build/screens ./gradlew --no-daemon :app:testDebugUnitTest --tests 'com.rebuild.ui.ScreenshotHarness'
```

The last line renders the screenshot tour (light, dark, 200% text and landscape) with Robolectric.

Use a disposable, explicitly selected emulator for automated tests, not the
owner's real workout database. The native AccessibilityValidator cases require
API 34 or later; the runner omits them on API 33. Run on API 34+ for meaningful
accessibility coverage, not Robolectric. See [verification](verification.md)
for actual run evidence, warning triage and the still-required Galaxy A71 and
TalkBack checks.
