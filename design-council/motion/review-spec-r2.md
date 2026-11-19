# Motion critic, round 2: spec v2

**Inputs**
- `motion/spec.md` v2, with its "Changes since round 1" table
- `tools/presence.py` and `tools/ease.py`
- the code at 3236a29

**What I checked**
- **Presence baselines.** I re-ran `presence.py` on today's recordings. The spec's baselines reproduce exactly:

  | Transition | Recording | Presence |
  |---|---|---|
  | Tab switch | tabs 3.425 | 0.00 |
  | Pop | tabs 27.063 | 0.35 |
  | Push | workout 10.984 | 0.00 |
  | Chart pop | chart 18.481 | 0.14 |
  | Dark tab switch | dark 8.412 | 0.02 |
  | Setup step | setup | 1.00 |

  Log set (workout 21.95–22.95) and exercise switch (56.7–57.2) both pass at 0.99–1.00.
- **Curves.** I re-ran `ease.py` and my own curve maths. Every number in the Rules table is right:
  - Lateral enter leaves 76 % of its travel (Long) or 52 % (Medium) when alpha reaches 0.5.
  - Chrome shift is 97.5 % done by 75 ms.
  - Sheet in is 98.2 % of the way at 200 ms.
  - Settle at stiffness 800 reaches 85 %, 95 % and 99 % at 120, 168 and 235 ms.
  - Crossfade's summed alpha bottoms out at 0.667 at 100 ms, and never has both above 0.5.
- **Haptic method.** It works on this emulator. A read-only `dumpsys vibrator_manager` lists `com.rebuild`'s
  TOUCH vibrations with millisecond `startTime`s (e.g. `11-18 16:10:16.839`), so the plan's way of checking haptic
  timing is real.
- No app code and no app data were changed.

## Round-1 findings, re-checked

| # | Status | Notes |
|---|---|---|
| 1 | Fixed | **Layers and chrome.** Each layer is laid out with its own final insets, the nav bar and rail are one overlay, and the shared bars follow a "Chrome shift" rule. I traced push and pop against the Chrome shift timings: the bars only ever overlap a layer drawn below alpha 0.5. On push, the bar sits over the incoming dock only while that dock is under 0.5. On pop, the bar moves up over the outgoing dock only once that dock is under 0.5, and otherwise covers only the nav overlay's area. The acceptance (Log set's y ±1 px, landscape included) is right. One perf remark is new P3 finding 6. |
| 2 | Fixed | The spec now states truthfully that the tick comes ≈ 500 ms late. A new `onSaved` callback is called before the cooldown, the full haptic table is added, and the receipt and takeover ticks now fire on visible landing. The verification method is proven (see above). The receipt's use of `onSaved` raises an ordering point: new P3 finding 5. |
| 3 | Fixed | **Lateral travel.** The new `Travel` easing (0.4, 0, 0.2, 1) with fades starting at t = 0 gives about 24 dp of visible travel on push and about 17 dp on an exercise switch. Exits now fade and travel together over 100 ms. It is a numeric rule with a JVM test. Right. |
| 4 | Fixed | `BusyAfter` and `SplashCap` are real time, the spinner grace is gone, and the Remove-animations list says a normal save shows no busy state. |
| 5 | Fixed | **Log choreography.** One table with a single focal element: the value fades in place, the band is the only motion in the ledger, the pill count is cut, and there is an ordered list of what to cut if it reads busy. |
| 6 | Fixed | **Receipt.** Workout → Today is a non-travelling crossfade, the sheet is the only thing that travels, and there is no check animation. The tick fires at 98 % of the sheet's travel. All ten in-app-sheet conditions are listed. |
| 7 | Fixed | P1-6 to P1-10 cover delete, add/remove/skip, stopwatch and alarm bars, snackbar, the error line and the unpinned dock. Each is short but specified. |
| 8 | Partly fixed | **Exercise switch.** The mechanism is buildable: `AnimatedContent` (fade only) plus `transition.animateFloat` applied to the body slot only. It keeps state isolated per exercise. But it puts two identical opaque docks in a crossfade, which dims Log set: new P2 finding 1. |
| 9 | Fixed | The token set is closed, with no delay tokens, `Pop` removed, the takeover sheet drop moved to the Sheet out rule, and a grep check for stray `tween(`/`spring(`. Two small wording points: new P3 finding 4. |
| 10 | Fixed | **Verification plan.** Every P0/P1 item gets sheets at scale 1 and 5, plus the presence ≥ 0.40 check, a JVM curve test, a Log set y scan, the font 2.0 / dark / landscape / scale-0 matrix, and haptic timing. The two contradictory criteria are rewritten correctly. |
| 11 | Partly fixed | **Splash.** The theme attributes now use the library, so they work on minSdk 26, and a load error releases the splash. But the chosen icon would be invisible: new P2 finding 2. |
| 12 | Fixed | The sliding PillChoice is dropped. |
| 13 | Fixed | The 150 ms readability target is stated consistently, the spring timings are real, and the "start after the first drawn frame" mechanism is spelled out (a `LaunchedEffect` that awaits one `withFrameNanos`). That is the right mechanism. |

## New findings

### 1. P2: Crossfading two identical opaque surfaces dims them, so Log set flickers on every Next

- **Moment.** Every exercise switch (P1-4, pinned dock). The same happens when the rest bar becomes the alarm bar
  (P1-2), which is two amber bars in the same place.
- **Evidence.** The P1-4 crossfade has both copies of the dock in it. The old dock fades 1→0 over 100 ms; the new one
  fades 0→1 over 150 ms on top of it.
  - Where the two copies are the same pixels (the dock surface, the Warm-up chip, the amber Log set pill), what shows
    is `b + (1 − b)·a`.
  - That works out to 100 %, 83 %, 67 %, **63 % at 70–80 ms**, 67 % and 80 %, back to 100 % at 150 ms.
  - So the amber Log set button drops to about two-thirds strength for about 60 ms on every Next, in exactly the spot
    the spec promises is still. The amber rest bar does the same when it becomes the ringing alarm bar.
- **Why the plan misses it.** `presence.py` can't catch this, because a dimmed amber is still "not canvas". The
  crossfade's "summed alpha ≥ 0.66" property is exactly the dip.
- **Would satisfy me.**
  - A rule for replacing an opaque surface in place: the old one stays at alpha 1 underneath until the new one is at
    1 ("fade in over"). Because the new surface is opaque, this is still a true crossfade for the parts that differ,
    and identical parts never dim.
  - Alternatively, only the content that differs is crossfaded inside a container that stays put.
  - Applied to the dock in P1-4 and to rest bar ↔ alarm bar.
  - `MotionCurvesTest` asserts that identical pixels never drop below 95 %.
  - The verification plan samples the Log set fill colour every frame across a Next: it stays within a few grey
    levels of its settled value.

### 2. P2: The splash icon as specified would be invisible on the canvas

- **Evidence.**
  - P0-6 sets `windowSplashScreenAnimatedIcon = the launcher foreground` on `windowSplashScreenBackground` = canvas.
  - The foreground is cream strokes `#FFF3D9` on transparent (`res/drawable/ic_rebuild_foreground.xml`). Its brown
    `#8A5900` comes only from the adaptive icon's background layer (`res/drawable-v26/ic_rebuild.xml`, color
    `launcher_background`).
  - Cream on `#F3EFE9` is about 1.05:1 contrast. Launch would show a flat canvas for up to 2 s, the very "blank" the
    spec exists to remove. The verification's `blankframes.py` = 0 would fail at build time.
- **Would satisfy me.** One of:
  - keep the icon's brown disc: parent `Theme.SplashScreen.IconBackground` with
    `windowSplashScreenIconBackgroundColor = @color/launcher_background`, light and dark; or
  - use the full adaptive icon.

  Plus a verification line that the splash's icon pixels differ from the canvas.

### 3. P3: A Crossfade with no size transform makes Today's hero card jump

- **Where.** P0-5's "Today's hero card swap itself uses Crossfade (no size transform)".
- **What is wrong.** When the card's height changes (Rest day ↔ In progress, or Discard from Today), the content below
  jumps in one frame. The push hides this on Start, but not on Discard.
- **Would satisfy me.** Use the Expand/Collapse size spec on that `AnimatedContent`, or state that the jump is
  accepted.

### 4. P3: Two wording mismatches in the rules

- The Expand rule includes "content Fade in", but P1-2 says the bars use Expand with "No fade". Name it as a variant,
  for example "Expand (opaque bar: no fade)", so the table stays closed.
- Verification 1(b) says summed alpha ≥ 0.6, but the Crossfade row says 0.66. Use one number. Finding 1 changes this
  property anyway.

### 5. P3: The receipt's ordering depends on when the callback runs

- **What is wrong.** P0-2 sets `receiptFor` in `onSaved` "in the same snapshot as the finished state". But the app's
  state reaches Compose through a separate `collectAsStateWithLifecycle` (`MainActivity.kt`). A callback in the runner
  isn't guaranteed to land in the same frame as the state.
- **Would satisfy me.** The callback only marks the receipt as pending. In composition, the sheet shows and the route
  is removed in the frame where the state shows the workout finished.

### 6. P3: Hidden kept tabs would re-measure on every frame of a rest-bar Expand

- **What is wrong.** In the layer model, each layer's bottom inset follows the bars' animated height. `KeepAliveTabs`
  measures every kept tab on every pass (`RebuildApp.kt:529`). Unless hidden tabs read the settled height, all four
  tabs re-lay out on every frame of the rest bar's Expand, which happens on every log.
- **Would satisfy me.** Say that inactive layers read the final bar height, and include it in the `frames.py` check.

## On the new parts specifically

- **Layers and chrome, and the Chrome shift rule.** Sound and buildable. The z-order list is complete, with the
  takeover over the receipt. Moving a shared bar by 80 dp during a sideways push is correct here, because it is the
  same object finding its new seat in 100 ms.
- **`onSaved` before the cooldown.** Right. The tick may lead the visual change by up to one frame, which is fine.
  The receipt ordering is P3 finding 5.
- **Travel easing and a 32 dp Shift.** Right. The numbers are verified, the travel is visible, and pages are readable
  by 75 ms.
- **The closed token table.** Good, apart from P3 finding 4. The named exception for the takeover is honest.
- **AnimatedContent plus a body-slot offset.** Buildable and keeps state isolated. It needs P2 finding 1 for the dock.
- **ErrorText above the Log row.** Right. The dock is pinned to the bottom, so the line grows the dock upwards and Log
  set stays put. It also sits directly under the fields it is about. I checked: the line is at `LogEntry.kt:446`,
  after the Log row, today.
- **Verification plan.** Real and runnable. The presence tool reproduces, the haptic method is proven, and the matrix
  is complete. It needs the Log set colour check from P2 finding 1 and the splash-icon check from P2 finding 2.

## What is good

- The answers are precise and grounded in code, and everything in the "Changes since round 1" table is actually done
  in the body.
- The layer model fixes a whole class of problems, not just one symptom.
- The single log choreography with an ordered cut list is exactly the kind of decision a builder needs.
- The haptics are audited end to end.
- Remove animations is handled correctly: animations scale, information thresholds don't.
- The new checks are measurable and come with today's baselines.

VERDICT: NOT SATISFIED

Two P2s remain, both small spec edits: a "fade in over" rule for replacing an opaque surface in place (dock on Next,
rest bar ↔ alarm bar), and a visible splash icon. Once both are fixed, I expect to be satisfied with the spec.
