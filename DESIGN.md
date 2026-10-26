# Rebuild native design

A lifter uses the phone under bright gym lighting, then in a dim room at home:
respect system light/dark mode rather than imposing either.

## Design system (0.1.12, "Bolder")

Everything visual comes from `ui/theme/Theme.kt` and the shared components in
`ui/Components.kt`; screens never pick their own sizes or colors.

- **Color.** Warm neutrals with a committed amber accent, as Compose sRGB values.
  Amber carries primary actions, current selection and state, never decoration.
  Light: surface `#FAF9F7`, primary `#805300` with white labels, selection and the
  rest bar `#FFE3B0`. Dark: surface `#121010`, primary `#FFB94A` with `#3A2500`
  labels, selection `#5A4318`. Five surface-container steps separate docks, bars,
  cards and lists without borders. `ThemeTest` holds every text and control pair at
  WCAG 4.5:1 (3:1 for outlines) in both themes. A night window background matches
  the dark surface, so nothing light shows behind system bars.
- **Type.** One Roboto scale, every role with tabular figures. Titles and numbers are
  heavy (SemiBold/Bold); body stays regular. Top-level screens name themselves in
  `headlineSmall`; pushed screens use `titleLarge` beside Back; section headings are
  always `SectionHeader` (`titleSmall`). The number a user looks for is the largest
  on its surface: the rest countdown is `headlineMedium`.
- **Shape.** 8 dp chips and fields, 12 dp cards, 16 dp sheets, 28 dp dialogs.
- **Spacing.** A 4/8/12/16/24 dp grid; 24 dp between sections, 12 dp within.
  Content is a centered column capped at 640 dp on wide windows.
- **Motion.** Only state changes move, in 150–220 ms with ease-out: the ledger grows
  as sets are logged, options expand, a panel that replaces another (Target done)
  rises in once. Android's animation scale, including Remove animations, applies.
- **Haptics.** A confirm tick when a set is logged and when a workout is saved.
- **Navigation.** Bottom navigation bar on phones; a navigation rail at 600 dp and
  wider. Predictive Back is enabled. Bars paint behind the gesture area in their
  own color and ride above the keyboard. The four tabs are built once, warmed
  in the background after launch, and kept: switching tabs shows an
  already-built screen with its scroll position and sub-tab, fading in through
  its layer alone. Hidden tabs are absent from the semantics tree and Back, and
  don't recompose while another screen changes the data. Pushed screens
  (workout, details, editors) still start fresh each time they're opened. See
  `docs/performance.md`.
- **Dialogs.** One `AdaptiveDialog`: scrolling body, actions in Material order
  (dismiss, then confirm) that wrap only when they don't fit, and Back that closes
  the keyboard before the dialog.

Finishing a workout ends on a **receipt** sheet: "Push saved", the date and time
range, one line of working sets, exercises and minutes, then each exercise's best
normal working set with any real gain over the last comparable session ("+2.5 kg
vs last time", "+2 reps"). A lighter day shows as it was, never as a loss. It
states whether the workout counts for its week, and offers View workout and Done.
No score, streak or celebration beyond a confirming tick.

A drop that continues the set just logged is the first item of the set-type menu,
"Add drop to set 2", so the dock keeps one chip (Warm-up). After the target is met,
Next (or Finish workout) is the one full-width action; "Add drop to set N" and
"Extra set" sit quietly beneath it.

A drop is part of its set, never a set of its own and never a relabelling of it.
Set 2 keeps reading as set 2 everywhere (the ledger, a finished workout, an
exercise's history): its own load × reps and RIR, its pencil, then each drop
beneath it as "↳ Drop 32.5 × 7" with its RIR, the unit implied by the set above.
Rest-pause stages read the same way as "↳ Pause". Last time shows an earlier
set with drops the same way, muted. Charts, records and the receipt's best set
read the set by its own first stage; drops are never plotted. The set's editor lists "Set", then "↳ Drop 1", "↳ Drop 2", and offers
"Add a missed drop". Spoken: "Set 2: 42.5 kg per dumbbell × 9 @ 1 RIR, with
1 drop: 32.5 kg per dumbbell × 7 @ 0 RIR."

The rest bar leads with a small "Rest" label over a large tabular countdown, an
explicit "+30 s" text action, Skip and Options; −30 s and Set time live in Options.
Leaving the app during a rest shows a picture-in-picture countdown and an ongoing
countdown notification. At text above 130% or in a short landscape window the bar
folds into one line ("Rest 2:30 · alerts off") so it never takes the workspace.

**Units.** Each exercise records in kg, lb, or plates per stack where the plan
defines a stack; More › Units sets the default for new and unpinned exercises.
A set keeps the unit it was entered in and is never converted. Comparisons
(Last time, records, receipt gains) only pair sets of the same unit, and screen
readers hear "pounds", not "lb".

**Body.** Weigh-ins and weekly circumference checks live under History › Body and
on Today beneath the week. The approach copies what the research
(`docs/research/progress-tracking.md`) found most trustworthy across MacroFactor,
Happy Scale, Libra, Strong and Hevy:

- *Weight.* Each reading is a hollow dot; the weekly average (the mean of each
  day's mean, so two weigh-ins on one day count once) is a solid amber line that
  breaks across a missing week instead of drawing through it. Each average sits
  on the mean day of its weigh-ins, never in days still to come. The summary
  card leads with this week's average once it has three weigh-ins, and until
  then with last week's, with "So far this week" beneath; the change over four
  weeks is shown only when both weeks have readings. Ranges 1M/3M/6M/1Y/All. Neutral signs: a change is
  "−0.8 kg", never green or red.
- *Measurements.* One weekly form for the chosen sites (waist at the navel by
  default; the WHO midpoint is a separate site, so the two are never mixed), each
  with its landmark and last value. Every site has its own chart and list, with
  change since last and since first written out on each row. A gap of more than
  a missed week breaks a site's line. No body-fat estimate is derived.
- Every chart names its unit, labels its start, middle and end dates
  ("d MMM yyyy"), and has a text equivalent directly beneath it and one spoken
  summary. Values keep their unit and dates their month on one line
  (non-breaking spaces), so 200% text never splits "82.3 kg".
- Every chart can be touched. A press or a horizontal drag selects the nearest
  point (a session on exercise charts), marks it with a guide line and a ring,
  and gives a light tick as the selection moves. A readout above the chart
  names it: "83.7 kg average · Mon 31 Aug – Sun 6 Sep · 3 days", or
  "27.5 kg/DB × 2 · 15 Sep 2026 · 2 sets". Until the first touch it shows the
  latest point and a small "Touch to read". The readout keeps its height, so
  touching never moves the chart, and vertical drags still scroll the page.
  Screen readers use the chart like a slider: swiping up or down steps through
  the points and reads each one.

**History** opens on a month calendar of finished workouts (a filled day opens
the workout; today is a neutral outline, so it never reads as a workout) and
working sets per primary muscle, starting on the latest week that has any, then
the list. Tabs never break a word: at large text they scroll. The tabs are pages: swipe left or right between
them, and the indicator slides with the finger. A horizontal drag on a chart reads the chart instead, so charts
keep their touch.
Exercises have a search field; each exercise's page adds **Records**: the
heaviest working set actually lifted at each rep count up to 15, with ones beaten
at more reps folded away. No estimated one-rep max.

The rest of this document records how each feature's surfaces were decided.

Workout is the primary destination, organized since 0.1.6 as a set-first logbook.
Numbered exercise pills (order badge, name, working-set count; a check once the
target is logged) sit above one focused exercise: its name, loading/unit line and
today's target. The set ledger follows, one row per working-set ordinal with Set,
**Last time** and **Today** columns. Last time holds same-unit working sets from
the dated last comparable workout, paired in the order the entry prefill already
uses. Today holds the saved set with its own unit, RIR and a correction action,
or a dashed, explicitly labeled placeholder (Up next / Not logged), never a
projected value. Warm-ups are separate W rows outside pairing and counts; a set in
another unit has no comparison; extras beyond the target are labeled.
Secondary actions (next exercise, Target details with baseline, advisory upper,
today's adjustment and rest, exercise note) form one aligned list under the
ledger; target details remain distinct from today's chosen target.
After the target is met, Next is primary and logging an extra set is explicit
but unrestricted. Rest remains visible without preventing logging. History, plan,
backup and concise help are secondary. Use open lists and dividers instead of
nested cards. Destructive replacement/discard requires explicit confirmation.

The next set's entry (warm-up toggle, labeled numeric fields, Log) is pinned in a
bottom dock on roomy portrait windows: window height at least 600 dp and font
scale at most 130%. The dock is capped at 60% of the height so the ledger stays
visible, and scrolls itself if its content is ever taller. At larger text, in
landscape or in short windows the same entry moves inline into the scrolling
workspace rather than consuming the viewport. Placement follows the window, never
the keyboard, so focusing a field cannot move it; the dock takes the full height
only while its own field has the keyboard. Both placements share one draft, so
typed values, the stable draft key and the stale-draft guard survive the move.

Drop and rest-pause sets (0.1.7) live inside the same entry, not a separate mode.
A quiet set-type control sits at the end of the entry's context line (Normal,
Drop set, Rest-pause, each explained in one menu line). Normal keeps the prefilled
one-tap Log. A chosen type relabels the one primary action: Log stage 1, then Add
drop or Add mini-set. An outlined Finish set sits beside it, and rest-pause adds
a text-style Start 20 s pause. In the ledger, a drop or rest-pause set is one
numbered row with its method and stacked stages. An open set has an accent outline
and "in progress", and isn't counted until finished. Its spoken label is one set
with numbered stages. Tapping a set opens one dialog for stage corrections, a
missed stage, its type, Finish set and deletion. Another exercise's entry is
replaced by a short notice with Go to and Finish that set, so new sets never
interleave with an open one. No new palette roles, icons or panels.

Flexible workouts (0.1.8) reuse these surfaces rather than adding a builder. The
exercise pills end with an outlined Add exercise pill, and More workout actions
holds Add exercise and Reorder exercises. An added exercise's pill, spoken label
and target line say it is optional ("Added today · optional, no target", or its
optional goal). Its options list holds Goal and rest, Save to *routine* plan… and
Remove from this workout. A planned exercise below target adds Skip today · target
stays. A skipped exercise's entry becomes a short panel that keeps its target in
words, with Next (or Finish workout) and Undo skip, stacked at large text. Reorder
is a dialog of rows with labeled 48 dp Move up and Move down icon buttons, for
this workout only. An extra workout opens on one clear action, Add your first
exercise, and its header says it is outside the plan. Its Finish dialog asks for
one logged set (a warm-up is enough) and offers Discard only while it is empty. Removing an added exercise
that holds records uses the destructive confirmation, naming what is deleted.
Plan lists the user's own exercises among the numbered slots, and removed
originals sit in a quiet "Removed from future … workouts" list with Restore. No
new palette roles; three Lucide icons (arrow-up, arrow-down, undo-2) are added.

Reusable routines (0.1.9) add one deliberately plain builder rather than a new
mode. **Your routines** (from Today, Plan and More) is an open list: name, then
exercise and working-set counts, then where its copies are scheduled, by phase.
A routine opens on its primary Start (Start as extra session, or Start *name* when
this phase's week has copies), with one line saying how it will count. Add to a
weekly plan, Edit routine and Delete follow, then its numbered exercises and an
"On weekly plans" list that offers Update only where a copy differs. The editor
is shared by New routine, Edit, Save as routine from History and a copy's Edit:
a name field, numbered exercise rows with their targets, labeled 48 dp Move up,
Move down and Remove icon buttons (stacked below the row above 130% font scale),
an inline Undo line after Remove, and Add exercise from the same library picker.
A row missing targets, listed twice or counting reps differently from the plan
says so in error text on the row itself; Save names the first problem and
scrolls to it. Leaving with changes asks before discarding.

Choosing is never guessed. Starting a routine whose copies are on this week opens
radio rows, one per copy (its day, with "2 of 2" for same-named twins, and this
week's status) plus Extra session, with nothing preselected. Scheduling picks a
phase (from the library only) and a day; each day row names what is already
there, or Rest day. A taken day adds a short question below the days: Add
alongside, or Replace one named routine, each with one line saying what happens.
The confirm button stays disabled until the choice is complete. Plan shows each
routine of the viewed week as a day header with its status words and an Options
menu: copies offer Edit routine, Change day and (only when it differs) Update
from library; every routine offers Take off this week, a destructive
confirmation. Originals taken off sit in a quiet "Taken off the … week" list
with Put back. Plan's outlined "Add a routine to the … week" is the only new
entry point there. Four Lucide icons (library, calendar, calendar-plus,
refresh-cw) are added; no new palette roles.

Measured exercises (0.1.10) reuse the logbook instead of adding a mode. A timed,
distance or cardio entry lists its fields in the order the effort is measured: a
carry's distance first, a hold's or cardio's minutes and seconds, then cardio's
distance, then any load. Every distance has an explicit m, km or mi choice right
after its field, upright and in landscape; above 130% font scale the fields stack.
Log stays the one primary action, with an outlined Stopwatch (the timer glyph)
beside it. While timing, the time gets a display-size tabular line and the actions
become a filled Stop (square) with an outlined Pause (pause) or Resume (play).
Stopping returns to Log with the measured time in its fields and one line saying
nothing is saved until Log. Discard is a quiet text action with a destructive
confirmation; a time typed over the measured one offers "Use the measured …".
Cardio adds a text-style Start rest, since it never rests by itself. While the
stopwatch times another exercise, this entry becomes a short notice with that
time, Go to and Pause stopwatch, as an open drop set does. A paused or stopped
effort elsewhere is a one-line strip under the exercise rail, and away from the
workout a bar with Pause or Resume and Open, stacked with the rest bar above
navigation. The ledger keeps its Set (# for cardio), Last time and Today columns;
the count line reads "Reached 0:30: 2 of 3 · 1 under, not counted" or cardio's
"Logged: … of …", and each effort says whether it reached the minimum or is extra.
Measured history leads with a Latest card, then one chart per measurement and
unit, then All sessions as the charts' text equivalent. The card and each session
row are spoken whole, every time beside its distance, so a card scrolled to a
sliver still has its words. The running time is spoken as "Stopwatch 1 minute 15
seconds, timing" and never announced every second. No pace, totals or scores are
drawn. Two Lucide icons (pause, square) are added; no new palette roles.

Loads name kg or plates per stack on every row. Assistance and per-dumbbell units
stay explicit; bodyweight has no load. Missing RIR is a dash, never zero. Older
comparable sets beyond today's rows fold behind a labeled Show all action.
Above 130% font scale, ledger rows become labeled blocks instead of squeezing
columns. Entry suggestions still use the existing comparison/working-set ordinal
logic; the display never sets a new target or logs a set. Native tabular numerals
align read-only values, entry values and the rest countdown.

Today is a session summary, not a dashboard: one surface with the scheduled or
active session's name, phase and working-set totals, a single primary Start or
Resume action, and a numbered read-only exercise list with targets or logged/target
counts. Other sessions stay startable below, each with its day block; the schedule
never shifts. A fresh Resume opens the first exercise still below its working
target (the first exercise when every target is met). An exercise already open in
a valid workout stays selected; manual exercise choice and partial finishing
remain available. Backup, rest-alert and phase notices follow. No streaks, scores,
projections or advice. After the week list, an "Outside the plan" section offers an
outlined Start empty workout and a quiet link to Your routines.

Below it, **This week** is a plain Monday–Sunday checklist for the current phase:
the date range, "Done X of N", then one list row per scheduled routine, so two
copies of one routine are two rows. Each row has the
routine's day block, its name, and its status: Done · date, In progress,
Partial · not counted, or Not done. A quiet outlined Start or Repeat button
follows; the featured routine's Start is on the card above, never twice. The card
above says why it is offered: scheduled for today; today's routine done, so the
next unfinished one; Rest day; or Week complete with rest or repeat. When a day
holds several routines, the card features the first unfinished one and one line
names the others ("Also on …") to start from the list. While a
workout is active, the list is read-only. Status is text, not color alone, and the
whole section uses the existing list and day-block motifs, without a progress ring
or metric hero. Extra workouts done this week are named in one line below the list
and are never counted.

Secondary screens share two quiet motifs: a day block (short weekday over the date)
for History and Plan day headers, and a numbered order badge that becomes a check
once the working target is logged. Badges are decorative to accessibility services;
their row supplies the words. History groups workouts by month with factual
working-set counts; its detail is a read-first set table with explicit corrections.
A finished workout's detail states whether it counts for its week. A partial one
with working sets on any exercise, added ones included, offers the explicit
"Count as … done for its week" checkbox, which never adds sets; an extra workout
says it is outside the plan. A workout of only warm-ups lists them beside its
0 working sets, so saved work never looks empty.
Exercise charts plot normal working sets only. When there are drop or rest-pause
sets, a one-line note says so (or that there are no charts at all), and they are
listed with their stages.
Exercise progress leads with the latest logged session and draws older sessions at
lower emphasis. Plan shows weekly working sets as a small ledger (this phase,
established baseline, advisory upper) before numbered day slots. The library keeps
search, filters, result count and Add custom exercise above its open list; More
groups Training, Your data and Rest alerts, ending with the installed version.

The visual council drew pattern inspiration from the aligned previous/current
data in [Hevy](https://play.google.com/store/apps/details?id=com.hevy),
[Strong](https://play.google.com/store/apps/details?id=io.strongapp.strong) and
[Boostcamp](https://play.google.com/store/apps/details?id=com.bpmhealth.boostcamp),
and dated prior data in
[Alpha Progression](https://play.google.com/store/apps/details?id=com.alphaprogression.alphaprogression).
These are publisher screenshots, not proof of their interaction behavior.
No competitor artwork or screen implementation is included. Rebuild retains its
own offline, focused-exercise flow, rather than adopting a workout spreadsheet.

Minimum 48 dp touch targets, scalable labels, scrollable content with IME insets,
clear errors and bottom-reachable primary actions. Charts depict logged loads and
reps only, with textual equivalents and separate exercise/loading/unit identities.

The exercise library is an open, searchable native list reached from Plan or More,
not another root tab or workout builder. Type (All types, Sets of reps, Timed,
Distance, Cardio), equipment and muscle filters have explicit reset and empty states. The same full-height picker serves today's substitution
and future-plan editing without broadening the prescribed alternatives, and adds
exercises to a workout, a routine or a History correction. An exercise already
there is marked "in this workout" or "in this routine"; choosing it again opens it.

Cable load units are visible near entry, with a labeled native selector on request.
Plates always means whole physical plates per stack, on the same machine. Never
equate plate counts with kg or change a saved set's unit because a preference changed.
Entry, dated previous rows, history corrections and chart filters name the unit;
switching clears unsaved load and isolates matching-unit suggestions. Library
preferences apply to future sessions; active entry selection changes the next set
without rewriting earlier sets. No palette or icon-family change accompanies this
feature.

Respect user font scale. Headers wrap outside fixed toolbar heights; confirmation
bodies scroll with independently stacked actions. Dialogs keep the platform pan for
the keyboard: the focused field stays visible, Done on a goal dialog's last field
saves, and the actions are whole once the keyboard closes. Forcing dialog windows to
resize was tried and rejected: Compose foundation 1.8 did not bring the focused
field back into view and its cursor handle relaid out every frame. The compact rest row consumes
safe horizontal/navigation insets and stays above the IME. During landscape
numeric entry, secondary chrome and the exercise picker temporarily give way to
exercise/loading context, side-by-side fields and Log, and rest controls. Only the
context, fields and Log are kept in view while typing. The set type, Finish set
and the short pause follow, a scroll away, so an open set never pushes the entry
out of the keyboard-shortened viewport.
Android Back dismisses the keyboard and restores navigation without logging.
Portrait entry scrolls its complete field/action group into view as the IME opens.
Relocation follows the measured viewport resize, not composition-time keyboard
insets, so the complete Log action remains visible after the layout settles.

Validation identifies and focuses the invalid field without clearing input.
Saving and target completion have explicit feedback; the countdown does not
announce every second. Its progress line is decorative and excluded from
accessibility semantics: the readable countdown supplies its meaning, and the
Material progress indicator's expanded semantics must not clip adjacent rest
buttons in Android's accessibility provider. History and saved notes are read-first, with explicit
correction/Add/Edit actions. Unsaved notes still use the durable navigation guard.
Library rows carry their name and metadata as one label, so a row clipped to a
sliver at a scroll edge is never exported as an unlabeled control. Scrolling
bodies sit beneath the fixed header, phase chips and exercise rail stacked above
them, so a row scrolled partly under the top edge never takes the lower part of a
chip or pill in the accessibility provider.
Native emulator geometry and semantics checks are evidence, not a claim of
physical-device or TalkBack speech certification.

Icons use the curated Lucide rounded-outline family: a 24 dp optical box,
2-unit stroke and round caps/joins. Workout, history, plan and settings use
dumbbell, history-clock, clipboard-list and sliders glyphs. Keep the visible tab
labels and selected pill/color; selection never depends on an icon alone.
Utility icons share the same family and keep their existing accessible names
and 48 dp interactive parents (distinct from Material's 40 dp visual button).
The launcher uses the same dumbbell in Android's adaptive-icon safe zone.
See `docs/icons.md` for pinned provenance and packaged licensing.
