# Motion critic, round 1: spec v1, before it is built

Inputs: `motion/spec.md`, the recordings in `before/` and `designer/`, and the code at `feature/motion` 3236a29.
My own every-frame sheets are in `motion/critic/`: `workout-r-10.6`, `receipt-r-1.15`, `tabs-r-3.35`,
`tabs-r-26.95`, `rest-r-62.6`, `workout-r-21.95`, `strip2-r-5.45` and `rest-r-11.0`. I did not record anything new,
because the given recordings settled every claim I checked.

## Claims about today that I checked

I re-ran `blankframes.py`. The counts match the spec exactly: tabs 19, receipt 10, dark 8, chart 3, strip 3 and
workout 2. I then checked these claims frame by frame:

- **P0-1, confirmed.** The tab gets "In progress · Upper B" at 10.647. Blank frames at 10.984 and 11.028. The workout
  grows from the top-left with its right and bottom edges clipped (11.129–11.264); Log set is cut off at 11.264. The
  pop ghost shows at tabs 27.063, with Change split and Plan text superimposed.
- **P0-2, confirmed.** "Saving…" shows on Log set behind the dialog (1.24–1.50). Bare canvas under the fading dialog
  from 1.521. Fully blank 1.673–1.772. Canvas plus nav bar only 1.804–2.046. Today and the receipt appear together at
  2.286, so the sheet's slide is never seen.
- **P0-3, confirmed.** Blank frames at 3.425 and 3.454.
- **P0-4, confirmed.** Log set is grey and reads "Saving…" from 22.029 to 22.593. Log set and Skip stay grey until
  22.864. Dragging a pill also turns Log set into "Saving…" (strip2 5.66–6.0).
- **P0-6, confirmed.** The splash pixel is #FFFFFF; the first app frame is #F1EFE6 (≈ #F3EFE9 after the codec).
- **P0-7, confirmed.** At 62.650–62.715 the "1" is drawn over the uncovered workout.
- **P0-8, confirmed.** The label "Machi|able row" is cut from 5.54 onwards and still overdrawn at 6.006.
- **P0-9, confirmed.** At 11.073→11.120 the rail and subtitle vanish in one frame.

The evidence holds up, and the spec's frame-time discipline is good. The findings below are about the proposals.

---

## Findings

### 1. P1: Push and pop as written would make the workout's dock jump by the nav bar's height

- **Moment.** Every push from a tab to a screen without a nav bar (workout, workout detail, editors), and every pop
  back to a tab.
- **Evidence.** The spec says the nav bar's "space [is] kept until the transition ends so the outgoing tab does not
  re-layout", and that "its height change applies at the transition's end on push and at its start on pop" (spec
  :151–153).
  - In code, the nav bar, the rest bar and the stopwatch bar all sit in the Scaffold `bottomBar`. Every route lays
    out inside the same `Box(Modifier.padding(inner))` (`RebuildApp.kt:300–372`). Both layers therefore get one
    shared bottom padding.
  - Built literally, the incoming workout lays out with its pinned dock about 80 dp too high for 300 ms. Log set then
    drops 80 dp in one frame at the end of the push.
  - On pop, the outgoing workout's dock jumps up 80 dp in the pop's first frame, while it is still nearly opaque.
- **Also missing.** At ≥ 600 dp, the `NavigationRail` (`RebuildApp.kt:285–297`) removes 80 dp of width on push. The
  spec never mentions it.
- **Would satisfy me.**
  - Each layer is measured with its own final insets: the outgoing layer with the bar, the incoming one without it.
  - The nav bar or rail is drawn as a fading overlay that no longer drives the shared content padding during the
    transition.
  - The rail case is specified the same way.
  - Verify: in an every-frame sheet of Today→workout and workout→Today (light and dark, plus one landscape or wide
    recording), the workout's Log set has the same y-position in every frame where it is visible.

### 2. P1: The set's confirm haptic fires about 500 ms after the set lands, and the spec says it fires at save

- **Evidence.** The tick is in the runner's `onSuccess` (`LogEntry.kt:306–309`). `ActionRunner.run` calls
  `onSuccess()` only after `delay(cooldownMs)` (`ActionRunner.kt:44–57`), and Log set passes
  `NEW_SET_COOLDOWN_MS = 500`.
  - In workout.mp4 the save completes at about 22.59 and the set lands at 22.618, so the tick comes at about 23.1.
  - The spec says the opposite twice: "the confirm haptic of the set (LogEntry.kt:308) stays where it is (at save)"
    (P1-3), and "the confirm tick fires on save success … motion never delays them" (What must not move).
- **Why it matters.** With P1-1 the set arrives in about 220 ms, so the lifter would see the set settle and then feel
  a tick about a third of a second later. On the most frequent event in the app, sight and touch would disagree.
- **Would satisfy me.**
  - The spec states the real timing.
  - The tick fires in the frame the saved state arrives, which is the same frame the ledger's arrival starts. That
    needs a hook that runs after the save and before the cooldown; the cooldown guard stays.
  - The same audit is done for the other ticks:
    - receipt (`WorkoutReceipt.kt:112`);
    - takeover (`RestEndMotion.kt:199`, which looks aligned);
    - drag lift (`WorkoutScreen.kt:546`; the lift animation must start in that frame);
    - slot change (`WorkoutScreen.kt:449`; it must fire in the frame the neighbour starts to move).
  - Verify: timestamps from `adb shell dumpsys vibrator_manager` against the frame where the set lands, within one
    frame (about 16 ms on a device; looser on the emulator, with the method stated).

### 3. P2: The sideways moves will barely be seen; with these easings and fade timings they read as plain fades

- **Evidence.** I worked out `Arrive` (0.05, 0.7, 0.1, 1) and `Leave` (0.3, 0, 0.8, 0.15) with the spec's alpha
  timings:

  | Transition | Planned travel | What happens | Travel the eye sees |
  |---|---|---|---|
  | Push, incoming | 24 dp over 300 ms, `Arrive` | 18 of the 24 dp is done by 50 ms, while alpha is still 0. At alpha 0.33 (100 ms), 2.7 dp remain. | about 6 dp, almost all at alpha < 0.5 |
  | Pop, outgoing | 24 dp over 220 ms, `Leave` | Alpha reaches 0 at 100 ms, when it has moved 3 dp. | about 3 dp |
  | Exercise switch (P1-4), incoming | 24 dp over 220 ms, `Arrive` | 4.5 dp remain when alpha starts. | about 4.5 dp |
  | Exercise switch (P1-4), outgoing | 12 dp over 220 ms, `Leave` | Alpha reaches 0 at 100 ms, at 1.5 dp. | about 1.5 dp |

- **What is wrong.** The spec's "why" for P0-1, P1-4 and P2-2 rests on direction ("deeper", "comes from the right …
  makes a wrong tap obvious"). These numbers can't deliver it. Pairing an accelerating exit with a 100 ms fade-out is
  also wasted: the travel happens after the layer is invisible.
- **Would satisfy me.** Either:
  - **(a)** say honestly that these are fades with a hint, and drop the direction claims (and the Shift token where it
    is invisible); or
  - **(b)** make the direction legible: start the incoming fade with no delay, and use an easing that keeps travel for
    the visible part (`Standard` or an emphasized curve over the whole duration). For any travelling exit, the alpha
    lasts as long as the travel.

  Write the acceptance as a number: for example, "≥ 50 % of the incoming travel remains when its alpha reaches 0.5".
  Check it with an every-frame sheet at scale 1 and at scale 5.

### 4. P2: Timers that carry information are scaled by the animator setting, which brings the grey flicker back for Remove-animations users

- **Evidence.** P0-4's `showsBusy` threshold is "≥ 300 ms (scaled by `motionDelay`)". P0-6's spinner grace is "400 ms
  (`motionDelay`)".
  - At animator scale 0, both thresholds become 0 ms. Every save then greys the workout and shows "Saving…" in one
    frame (exactly today's P0-4 bug), and the spinner flashes on every launch.
  - The spec's own rule says "hold times that carry information … stay wall-clock". These two are information
    thresholds (is this save slow? is loading slow?), not choreography.
- **Would satisfy me.** Both are wall-clock. The scale-0 list says so, and adds "a normal save shows no busy state at
  scale 0".

### 5. P2: The log-a-set moment has no single choreography, and two of its motions go in opposite directions

- **Evidence.** In the save frame, P1-1, P1-2 and P1-3 together start up to six motions:
  - the new value rises 8 dp **upwards** into row n;
  - the current-row band slides **downwards** from row n to row n+1;
  - "Today n of N" rolls;
  - the pill's "n/N" rolls;
  - the rest bar enters while the dock rises about 86 dp;
  - on the last set, the dock's content changes kind, the Target done check and pill badge pop, and the count turns
    accent.
- **What is wrong.**
  - An 8 dp rise beside a 48 dp band moving down in the same rows is the "two things moving against each other" case.
  - Nothing says where the eye should go. It is one glance between sets.
- **Would satisfy me.** One table for this moment: each element, its start, duration, direction and easing, plus the
  single focal element. Opposing directions in the ledger are resolved, for example by having the value settle in
  place (fade, or a slight scale) while the band moves, or by starting the band once the value has landed, within the
  220 ms budget. State which motions are cut if it reads as busy at scale 1.

### 6. P2: The finish → receipt choreography stacks too much, and two layers move at right angles at the same time

- **Evidence.** Spec P0-2, all within about 450 ms:
  - the platform dialog exits;
  - the workout pops, sliding **right**;
  - Today fades and slides in;
  - the scrim rises;
  - the sheet rises from the bottom;
  - the rows rise 8 dp at 120 ms;
  - the check pops with overshoot at 150 ms.
- **What is wrong.**
  - Horizontal and vertical travel at the same time.
  - The rows move inside a sheet that is itself still moving.
  - DESIGN.md asks for "no … celebration beyond a confirming tick".
- **Would satisfy me.**
  - Under the receipt, workout → Today is a non-travelling crossfade (or a cut, hidden by the scrim). The sheet is the
    only thing that travels.
  - The rows arrive with the sheet; no separate rise.
  - The check is at most a plain `Arrive` scale with no overshoot, and the tick lands with it.
  - Keep: Today is always visible behind it, and there are 0 blank frames.

### 7. P2: The inventory is missing several real state changes

The brief asked specifically about list items being inserted and removed. The spec does not cover these:

- **Deleting a set** (Edit set → Delete, `SetEditing.kt:268`). Do the rows below reflow, does the band move, does the
  count roll down?
- **Adding an exercise** (`AddWorkoutExercise`, via the picker at `WorkoutScreen.kt:276`) and **removing one**
  (`RemoveWorkoutExercise`, `FlexibleWorkout.kt:208`). A pill is inserted or removed in the strip, the focus moves,
  and with it the direction rules of P1-4.
- **The `StopwatchBar`** (`RebuildApp.kt:342–347`) appears and disappears in the same `bottomBar` as the rest bar. It
  moves the dock the same way and must share P1-2's spec.
- **The error snackbar** (`RebuildApp.kt:206, 306`) appears above that same `bottomBar`. Say whether Material's default
  is kept, and that it never pushes the dock.
- **The validation or error line** in the dock (`draft.error`), which appears and changes the dock's height.
- **Large text.** At large font scale the dock is not pinned (`WorkoutScreen.kt:1001`, `if (pinned)`), so it scrolls
  with the body. P1-4's "the dock does not slide" therefore needs a large-text answer.

**Would satisfy me:** an entry for each, in the same where/today/should/verify format. They can be short; "no motion,
final state in one frame" is an acceptable answer if it is stated.

### 8. P2: P1-4 isn't buildable as written: the dock lives inside the content that would slide

- **Evidence.** `key(focused.id) { ExerciseFocus(…) }` (`WorkoutScreen.kt:265`). The dock is a slot of `ExerciseFocus`
  (`WorkoutScreen.kt:1001`, `dock = { if (pinned) LogDock … }`). Wrapping `ExerciseFocus` in `AnimatedContent` slides
  the dock with the body.
- **Why the obvious fixes conflict.** Hoisting the dock out breaks the "fresh composition per exercise" isolation
  (entry drafts and dialogs), which the spec itself says to keep.
- **Would satisfy me.** The spec names the mechanism. Two options:
  - two `AnimatedContent`s keyed by the same id, one for the body (sliding) and one for the dock (crossfade only), with
    the per-exercise state kept in a keyed holder; or
  - the dock layer not translated.

  It also states what the unpinned large-text dock does.

### 9. P2: The token system isn't consistent yet

The spec says "one vocabulary", but the same kind of change gets different specs:

- **Vertical expand.** P0-9 and P1-2 use `Arrive`; P2-4 and P1-3's `SizeTransform` use `Standard`.
- **Vertical shrink.**

  | Where | Duration | Easing |
  |---|---|---|
  | P0-9 | `Fast` | `Standard` |
  | P1-2 | `Fast + 50` | `Leave` |
  | P2-4 | `Fast + 50` | `Standard` |

  `Fast + 50` is an ad-hoc 150 ms that is not a token (it equals `Short`).
- **Lateral exits.** The push's outgoing layer travels with `Standard`, but the pop's outgoing layer uses `Leave`.
  "Exits that travel" are meant to use `Leave`.
- **Sheet drop.** The receipt drops in `Medium` (220 ms); the takeover in 280 ms. Both are `Leave` sheet exits.
- **Offsets.** The spec calls `Stagger` (50 ms) "the one allowed offset", yet uses 60 ms (splash content), 80 ms
  (Target done check), 120 ms (receipt rows) and 150 ms (receipt check), plus the takeover's 170 and 180 ms.
- **`Motion.Pop`.** It scales 0.6→1 with a 2.8 % overshoot of a 0.4 travel. That is about 1 % of a 28 dp badge,
  roughly 0.3 dp, which can't be seen. The token adds a spring type for no visible effect; an `Arrive` tween does the
  same job.

**Would satisfy me:**
- one rule per kind of change: expand, collapse, lateral enter, lateral exit, sheet in and out, fade, colour, roll;
- every offset is a token or a named, justified exception;
- `Pop` is removed, or justified with a visible effect;
- every inventory entry refers only to those rules.

### 10. P2: The verification plan can't catch several of the defects the spec is about

Six problems:

- **Mostly scale 5.** Most checks are "≥ 3 intermediate positions at scale 5". Scale 5 hides dropped frames, and work
  bunched into the first frame or two. The lifter uses scale 1.
- **`blankframes.py` misses dips.** With a 98.5 % flat threshold it will pass the proposed fade-through dip, where
  both layers are below alpha 0.35 for about 50 ms around t = 75–110 ms, even when it reads as a flash on a device.
- **Contradictory wording (superimposed text).** "No frame shows two screens' text superimposed" (P0-1) contradicts
  the spec's own crossfade, where both layers are non-zero from 50 to 100 ms.
- **Contradictory wording (reps field).** "The reps field never moves while focused" (P0-9) contradicts the dock
  riding the IME (rest.mp4 11.12–11.31, correct today).
- **Untested conditions.** No large-font (1.3 and 2.0), landscape or wide recordings anywhere, and dark appears only
  for P0-1, P0-3 and P0-6.
- **Missing checks.** Haptic timing is not verified at all, and scale 0 is listed but has no recording step.

**Would satisfy me:**
- Each P0/P1 check has a scale-1 every-frame sheet and a scale-5 one.
- A numeric continuity rule: the minimum content contrast in a frame (for example, the share of body pixels that
  differ from canvas, compared with the settled screen), so a dip below about 40 % of the settled screen's content
  fails.
- The two contradictory criteria rewritten (for example, "no frame where both layers have alpha > 0.5"; "the reps
  field moves only with the IME inset").
- A scale-0 recording of the log, the push, the tab switch and the receipt.
- Font scale 2.0 and dark recordings for the log, Target done, the receipt and the push.
- The haptic check from finding 2.

### 11. P3: Splash details that need settling before it is built

- **minSdk is 26.** `values-v31` attributes alone do nothing on API 26–30. With core-splashscreen, use its
  `Theme.SplashScreen` attributes (`windowSplashScreenBackground`, `postSplashScreenTheme`) in `values` and
  `values-night`.
- **Load errors.** The keep-on-screen condition must also release on `loadError`
  (`repository == null && loadError == null`). Otherwise a failed load waits the 2 s cap before showing "Try again".
- **One surface.** Animate the splash view out (fade, and scale if wanted) over content that is already drawn. Don't
  also run a separate alpha 0→1 on the app content; one surface coming into focus means one layer moving.

### 12. P3: P2-8's sliding PillChoice indicator is over-design for its use

- **Where it is used.** Setup (unit, sound) and the set-group type editor (`SetGroupsUi.kt:476`).
- **Why it doesn't fit.** Its labels are allowed to wrap at large text ("a zero-width space after the hyphen"), so a
  sliding pill across options of different heights gets awkward. Material's own segmented buttons don't slide.
- **Would satisfy me.** A colour crossfade over `Short`, or a stated plan for wrapped options.

### 13. P3: Smaller inconsistencies

- **P1-3.** It says "Next must be readable within 200 ms", but its check allows "≤ 270 ms". Pick one.
- **P0-8.** It says "~120 ms to visually settle" for a critically damped spring at stiffness 500, which is about 75 %
  of the way at 120 ms and about 300 ms to 1 %. State the real figure.
- **P0-1's "start the alpha clock on the first frame the incoming layer is placed".** This needs the actual mechanism
  (for example, the transition is advanced from a `LaunchedEffect` in the incoming layer after its first
  `withFrameNanos`). The workout's long first composition must not eat the opening frames. In today's recording, the
  stall falls before the transition's first frame (10.647→10.984), so check this explicitly at scale 1.

---

## The lead's decisions

1. **core-splashscreen: agree,** with finding 11 applied.
2. **In-app receipt sheet: agree.** This is the right call for reliable choreography, on these conditions:
   - **Z-order.** It draws above the Scaffold's bottom bar, rest bar and snackbar, and is ordered explicitly against
     `RestEndOverlay`.
   - **TalkBack.** The app behind it gets cleared semantics and takes no input. Focus moves into the sheet, and its
     `paneTitle` is announced once.
   - **Back.** Back closes the sheet before the stack.
   - **Scrolling.** The sheet scrolls under the status bar at font scale 2.0.
   - **Insets.** It keeps the navigation bar inset.
   - **Dialogs.** The `BodyweightDialog` (`WorkoutReceipt.kt:232`) still opens above it.
   - **Rotation.** Its open state survives rotation (`receiptFor` is already saveable).
   - **Gestures.** Drag-to-dismiss has a velocity threshold, and a scrim tap dismisses.
   - **Tests.** Tests that find the receipt through a dialog root still pass.
   - **Predictive back.** Back on the sheet with a predictive animation is a plus, not a requirement.
3. **Changing the "Saving…" test: agree,** provided:
   - the delay is wall-clock (finding 4);
   - the test also asserts that a second tap during the guard does nothing;
   - a fake save longer than the threshold still shows the busy label.
4. **A fixed 8 dp rise: agree.** Note that `EnterOnce` is shared (`Components.kt:704`). Every caller changes, so list
   them.

On the open question of 300 ms for push and pop against DESIGN.md's 150–220 ms: I accept 300 ms for navigation and
sheets, provided input goes to the target from frame 0 (as the spec says) and content can be read by about 150 ms.
With `Arrive`, the perceived duration is far shorter than 300 ms anyway; that is finding 3's other side. The new
DESIGN.md wording is fine, once it also says that an information threshold (busy, loading) is wall-clock.

## What is already good

- The audit is honest and backed by real frame times. Every P0 I checked is right, and the blank-frame tool counts
  exactly what is claimed.
- It correctly finds the root causes:
  - the default `SizeTransform(clip = true)` growing from the top-left;
  - the unplaced tab;
  - `receiptFor` set in a later effect;
  - `busy` covering the cooldown;
  - `delay()` not following the animator scale.
- It keeps what already works: the Up next takeover (fixing only the clip), platform dialogs, the hold menu, and
  touch-driven chart and drag motion.
- "What must not move" is the right list: no rolling countdown, fixed entry fields, no stagger mid-workout, and the
  nav bar never slides.
- Scale 0 is treated as a correctness case, not an afterthought.
- Outgoing layers get cleared semantics, and duplicate test tags are anticipated.
- The performance risks come with a measured fallback (P1-2).
- The test fallout is listed file by file.
- The P0-4 split between guard and look is the most valuable single change in the spec.

VERDICT: NOT SATISFIED
