# Rebuild motion spec — v3 (audit of `feature/motion` @ 3236a29)

Status: revised after motion critic rounds 1 and 2. No app code changed.

## Changes since round 2

| # | Finding | Answer |
|---|---|---|
| N1 | P2 — crossfading two identical opaque surfaces dims them to ≈ 63 % at 70–80 ms (dock on Next, rest bar ↔ alarm bar) | **Changed.** New rule **Fade in over**: the outgoing surface stays at alpha 1 underneath; the incoming surface, opaque over its whole rect (it draws its container colour behind its content while it fades), fades in over it by Fade in; the outgoing is removed the frame the incoming reaches 1; any part of the outgoing outside the incoming's rect fades out by Fade out. Identical pixels stay at 100 %; differing pixels get a true crossfade. **Crossfade** is now limited to content on a shared, persistent background (text, digits, chart drawings) and to whole screen layers over the canvas. Applied to: P1-4 dock copies, P1-3 dock content (static opaque backing, only its content rises), rest bar ↔ alarm bar (P1-2), pill badge n → ✓, Today's hero card (N3). Checks: `MotionCurvesTest` asserts identical pixels ≥ 95 % (it is 100 %) under Fade in over and that the dip of a plain Crossfade over identical pixels (63 %) is never used for opaque surfaces (source scan of the call sites listed above); verification step 4b samples Log set's fill colour every frame across Next and a pill tap. |
| N2 | P2 — splash icon would be invisible (cream foreground on canvas ≈ 1.05:1) | **Changed.** Parent `Theme.SplashScreen.IconBackground` with `windowSplashScreenIconBackgroundColor = @color/launcher_background` (#8A5900) in light and dark; animated icon = `ic_rebuild_foreground` (cream strokes on the brown disc, as on the launcher). Disc vs canvas ≈ 5.3:1 light, ≈ 3.1:1 dark. Verify: the splash's icon-centre pixels differ from the canvas by ≥ 3:1, and `blankframes.py` = 0 over the splash. |
| N3 | P3 — Today's hero card jumps (no size transform) | **Changed.** Today's hero card uses **Fade in over** plus a size transform: growing by **Expand**'s size spec, shrinking by **Collapse**'s; content below moves with it. Covers Start (hidden by the push), Discard, Finish and day changes. |
| N4 | P3 — Expand wording vs bars; 0.6 vs 0.66 | **Changed.** Rules table adds the variants **Expand (opaque bar)** / **Collapse (opaque bar)**: size only, no fade; P1-2 names them. Verification 1(b) now says ≥ 0.66 for Crossfade, and 1(g) adds the Fade in over property. |
| N5 | P3 — receipt ordering depended on the callback's frame | **Changed.** `onSaved` only records `receiptPending = workoutId` (saveable). In composition, the frame in which the collected state shows that workout finished and no active workout both shows the sheet (`receiptFor` derived there) and drops `workout` from the stack used for rendering (`renderStack = nav.stack.filterNot { it == WORKOUT && state.activeWorkoutId == null }`); the existing `removeAll` effect then cleans the real stack. |
| N6 | P3 — hidden kept tabs would re-measure every frame of the rest bar's Expand | **Changed.** Layer model item 4: only the target layer and, during a push/pop, the outgoing layer read the animated bar height; inactive kept tabs read the settled (target) height, so they re-measure once per bar change, not per frame. Verification 7 counts measure passes of hidden tabs during the Expand (must be ≤ 1 each). |

## Changes since round 1

| # | Finding | Answer |
|---|---|---|
| 1 | P1 — push/pop would move the dock by the nav bar's height; rail not covered | **Changed.** New layer model (P0-1, "Layers and chrome"): every layer is laid out with its own final insets (top, bottom bar, nav bar or rail); the Scaffold no longer pads the content. The nav bar and the rail are one overlay instance whose alpha follows the layers on push/pop and stays opaque on tab switches. The shared bars (rest, stopwatch, alarm) move by the nav height with a "Chrome shift" rule timed so they never overlap a layer whose alpha > 0.5. Acceptance: Log set's top edge has the same y (± 1 px) in every frame it is visible; rail case specified and recorded in landscape. |
| 2 | P1 — set haptic fires ~500 ms late (after the cooldown) | **Changed and stated truthfully.** Today the tick runs in `onSuccess`, after `delay(NEW_SET_COOLDOWN_MS)` (ActionRunner.kt:44–57) → ≈ 500 ms after the set lands. New: `run(…, onSaved = …)` is called right after `repository.execute` returns, before the cooldown; the tick moves there. Full haptic audit added (Haptics table): set, receipt, takeover (fires on visual landing, not on spring end), drag lift, slot change. Verified with `dumpsys vibrator_manager` against a `screenrecord --bugreport` timestamp overlay. |
| 3 | P2 — lateral travel is invisible with `Arrive` + delayed fades | **Changed (option b) with a numeric rule.** Travelling enters use a new `Travel` easing (FastOutSlowIn 0.4,0,0.2,1); fades start at t = 0 (no delay); travelling exits fade and travel over the same 100 ms linearly. `Shift` raised to 32 dp. Acceptance: ≥ 50 % of an incoming travel remains when its alpha reaches 0.5 (push/pop: 76 %, ≈ 24 dp; exercise switch and rise: 52 %). Checked by a JVM curve test and by every-frame sheets at scale 1 and 5. |
| 4 | P2 — busy threshold and spinner grace were animator-scaled | **Changed.** Both are wall-clock (`Motion.BusyAfter` 300 ms, splash cap 2000 ms, real time); the spinner grace is gone (the splash covers loading). Scale-0 list says: a normal save shows no busy state. DESIGN.md wording adds "information thresholds are real time". |
| 5 | P2 — "set logged" has no single choreography; opposed motions in the ledger | **Changed.** One table (P1-1) with a single focal element (the new value, fade only — no rise), the band moving down from row n to n+1 being the only ledger motion, the header count rolling up, the pill count cut to an instant swap, and an ordered cut list if it reads busy at scale 1. |
| 6 | P2 — receipt stacked too much, orthogonal travel | **Changed.** Under the receipt, workout → Today is a non-travelling crossfade; the sheet is the only thing that travels; rows ride with the sheet; no check animation and no overshoot; the tick fires when the sheet first reaches 98 % of its travel. In-app sheet with every condition from lead decision 2. |
| 7 | P2 — missing state changes | **Added**: P1-6 set delete, P1-7 exercise add/remove/skip in the strip, P1-2 now covers the stopwatch and alarm bars, P1-8 snackbar, P1-9 dock error line (with a placement change that keeps Log set still), P1-10 unpinned dock at large font/landscape. |
| 8 | P2 — P1-4 not buildable (dock lives inside `ExerciseFocus`) | **Changed.** Mechanism named: one `AnimatedContent(focused.id)` with **fade-only** enter/exit (keeps the fresh composition per exercise), and the lateral offset applied **only to the body slot** through `transition.animateFloat` inside the content scope, passed into `ExerciseFocus` as a body modifier. The dock is never translated. Unpinned (large text, landscape, short window): the whole content crossfades, no travel. |
| 9 | P2 — tokens inconsistent | **Changed.** A closed rule table ("Rules"): one rule per kind (fade in/out, crossfade, colour, expand, collapse, lateral enter/exit, rise, sheet in/out, move-within, page, roll, chrome shift, settle). No `Fast + 50`, no ad-hoc offsets: there are **no delay tokens** at all now (every motion starts at t = 0 or on an event); the only exception is the Up next takeover's own tuned constants, named as such. `Pop` removed. Takeover sheet drop changed 280 → 220 ms to share the sheet-out rule. |
| 10 | P2 — verification can't catch the defects | **Changed.** New "Verification plan": every P0/P1 check at scale 1 (timing) and scale 5 (shape); a pixel **presence** metric (`tools/presence.py`, fails below 40 % of the settled screen's content; today's baselines included: tab switch 0.00, push 0.00, pop 0.35, chart pop 0.14, receipt 0.00); a JVM `MotionCurvesTest` for the curve properties; scale-0, font-2.0, dark and landscape/wide recordings; haptic timing. The two contradictory criteria are rewritten ("no frame where both layers' alpha > 0.5"; "the reps field moves only with the IME inset"). |
| 11 | P3 — splash | **Changed.** core-splashscreen `Theme.SplashScreen` attrs in `values` and `values-night` (works on minSdk 26); keep-on-screen = `repository == null && loadError == null`, capped at 2 s; only the splash view animates out over content already drawn. |
| 12 | P3 — sliding PillChoice | **Changed.** Dropped. Colour crossfade over `Short`. |
| 13 | P3 — small inconsistencies | **Fixed.** Next readable (fully opaque) ≤ 150 ms after the state arrives, check says the same; spring timings stated (Settle: 85 % at 120 ms, 95 % at 168 ms, 99 % at 235 ms); the "alpha clock starts after the incoming layer's first frame" mechanism is spelled out (P0-1) and checked at scale 1. |
| Lead decisions | core-splashscreen; in-app receipt sheet; Saving… test change; fixed 8 dp rise | Applied: P0-6; P0-2 lists all ten sheet conditions; P0-4 test changes (wall-clock threshold, second-tap assertion, slow fake save); `EnterOnce` has exactly one caller (`LogEntry.kt:606`, `TargetMetPanel`) and is replaced by the dock-content rule, which uses the fixed `Nudge` 8 dp. |

---

## Evidence used

All on `rebuild_api34` (software renderer), debug build of this branch, font scale 1.0. Times are **real frame
timestamps** (screenrecord is variable frame rate; a gap between timestamps means nothing changed *or* the app
stalled).

| Recording | What it shows |
|---|---|
| `before/setup.mp4`, `before/workout.mp4`, `before/receipt.mp4`, `before/tabs.mp4`, `before/history.mp4` | given (light) |
| `designer/coldlaunch.mp4` | cold launch with seeded data |
| `designer/strip.mp4`, `designer/strip2.mp4` | start workout (seeded), hold menu, drag reorder, select |
| `designer/rest.mp4` | Target and options, keyboard, log set, Rest options → Set time, Up next takeover, Edit set |
| `designer/dark.mp4` | dark: predictive-back gesture on the workout, tab switch, workout open |
| `designer/chart.mp4` | History › Exercises › Cable crunch, chart touch, back |

Tools (`motion/tools/`, scratch only):
- `rsheet.sh <dir> <name> <from> <secs> [cols] [width]` — every **real** frame in a window, stamped with its pts.
- `blankframes.py <video> [from] [to]` — frames whose body is ≥ 98.5 % one flat colour. Today: tabs 19, receipt 10,
  dark 8, chart 3, strip 3, workout 2. (Flat amber frames of the takeover, rest.mp4 65.23–65.33, are intended.)
- `presence.py <video> <from> <to> [light|dark]` — **new.** Per frame, the share of body pixels that differ from the
  canvas grey, divided by the smaller share of the two settled screens around the window (the frame before `from`
  and the last frame). Fails below **0.40**. Baselines today:

  | Transition | Window | Min presence |
  |---|---|---|
  | tabs Today→History | tabs 3.30–3.70 | **0.00** at 3.425 FAIL |
  | Plan → Change split (push) | tabs 24.0–24.6 | **0.00** at 24.091 FAIL |
  | Change split → Plan (pop) | tabs 26.95–27.9 | **0.35** at 27.063 FAIL (crossfade dip) |
  | Today → workout | workout 10.90–11.50 | **0.00** at 10.984 FAIL |
  | Finish → receipt | receipt 1.0–2.6 | **0.00** at 1.673 FAIL |
  | dark tab switch | dark 8.3–8.9 | **0.02** at 8.412 FAIL |
  | Cable crunch → History (pop) | chart 17.9–19.3 | **0.14** at 18.481 FAIL |
  | setup step | setup 7.1–7.8 | 1.00 PASS |
- `ease.py` — evaluates the cubic curves used below (the numbers in "Rules" come from it).

Smoothness is judged with care (the emulator drops frames); continuity, choreography and timing come from frames and
code. Nothing here is verified on a real GPU.

---

## Principles

1. Motion answers a question — where did the set go, what replaced the button, which way did I move — or it is cut.
2. Never a blank, half-drawn or doubled frame: the old surface stays until the new one can draw; each layer is laid out where it will end.
3. Logging never waits: nothing on the workout blocks input or moves a control under the thumb sideways, and nothing there needs more than 150 ms to be readable.
4. One vocabulary from a closed rule table: entering travel decelerates and is visible, exits are short, sideways means sequence, counts roll in the direction they change.
5. Animation times follow the animator scale; information thresholds (is this save slow?) are real time; at scale 0 the end state is instant and complete.

---

## Tokens (`app/src/main/java/com/rebuild/ui/theme/Motion.kt`)

### Durations

| Token | ms |
|---|---|
| `Motion.Fast` | 100 |
| `Motion.Short` | 150 |
| `Motion.Medium` | 220 |
| `Motion.Long` | 300 |

No delay tokens: every rule starts at t = 0 of its event. (The receipt, push, Target done and expand rules that used
offsets in v1 now start at 0.)

### Real-time thresholds (not animations; never scaled)

| Token | ms | Use |
|---|---|---|
| `Motion.BusyAfter` | 300 | a save running longer than this shows the busy look (P0-4) |
| `Motion.SplashCap` | 2000 | longest the splash waits for data before the loading screen (P0-6) |

### Easing

| Token | Curve | Kind of motion |
|---|---|---|
| `Motion.Standard` | `CubicBezierEasing(0.2f, 0f, 0f, 1f)` | size and position changes within a screen |
| `Motion.Travel` | `CubicBezierEasing(0.4f, 0f, 0.2f, 1f)` (FastOutSlowIn) | anything that travels **while fading in**: keeps the travel in the visible part |
| `Motion.Arrive` | `CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)` | opaque sheets coming in (no fade, so early travel is seen) |
| `Motion.Leave` | `CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)` | opaque sheets going out |
| `LinearEasing` | — | alpha, colour, travelling exits (alpha and travel together over `Fast`) |

`Arrive` and `Leave` move here from RestEndMotion.kt:66–69.

### Springs

| Token | Spec | Timing | Use |
|---|---|---|---|
| `Motion.Settle` | `spring(dampingRatio = 1f, stiffness = 800f)` | 85 % at 120 ms, 95 % at 168 ms, 99 % at 235 ms | drag lift and drop, reorder placement, predictive-back cancel |
| `Motion.Press` | `spring(stiffness = Spring.StiffnessMediumLow)` | existing | pill-tab press squish (Components.kt:1148) |

The takeover's `spring(0.92, 420)` stays inside RestEndMotion.kt as part of the named exception below.

### Distances

| Token | Value | Use |
|---|---|---|
| `Motion.Shift` | 32 dp | lateral travel of the top/peer layer |
| `Motion.Parallax` | 8 dp | lateral travel of the layer beneath |
| `Motion.Nudge` | 8 dp | rise of content arriving in place |
| `Motion.LiftScale` | 1.06, 8 dp shadow | held pill (existing `LIFT_SCALE`) |
| `Motion.BackPeek` | scale 0.94, 24 dp toward the edge, corners 0→28 dp | predictive-back preview |

### Rules (the closed set; every inventory entry names one of these)

| Rule | Spec | Property it guarantees (asserted in `MotionCurvesTest`) |
|---|---|---|
| **Fade in** | alpha 0→1, `Short`, Linear, from t = 0 | readable (alpha ≥ 0.5) by 75 ms |
| **Fade out** | alpha 1→0, `Fast`, Linear | gone by 100 ms |
| **Crossfade** | Fade out of A + Fade in of B, same place, both from t = 0. Only for content on a shared, persistent background (labels, digits, chart drawings) and for whole screen layers over the canvas — never for two opaque surfaces in the same place | never both > 0.5 (A 0.5 at 50 ms while B 0.33); summed alpha ≥ 0.66 (min at 100 ms) |
| **Fade in over** | replacing an opaque surface in place: the outgoing stays at alpha 1 underneath; the incoming, opaque over its whole rect (container colour drawn behind its content), fades in by Fade in on top; the outgoing is removed the frame the incoming reaches 1; any part of the outgoing outside the incoming's rect fades out by Fade out | identical pixels stay at 100 % at every t (≥ 95 % asserted); differing pixels: true crossfade, readable by 75 ms |
| **Colour** | `Short`, Linear | — |
| **Expand** | size `Medium` `Standard`; content Fade in | — |
| **Collapse** | size `Short` `Standard`; content Fade out | — |
| **Expand (opaque bar)** / **Collapse (opaque bar)** | the size part only (`Medium`/`Short`, `Standard`), no fade — for opaque amber or tonal bars whose edge travelling is the motion | — |
| **Lateral enter** | Fade in + translationX ±`Shift` (top/peer) or ±`Parallax` (beneath) → 0, `Travel`, over `Long` (navigation) or `Medium` (inside the workout) | ≥ 50 % of travel left at alpha 0.5: Long 76 %, Medium 52 % |
| **Lateral exit** | alpha 1→0 and translationX 0→∓`Shift` (top/peer) or ∓`Parallax` (beneath), both `Fast`, Linear | half the travel happens at alpha ≥ 0.5 |
| **Rise** | Fade in + translationY `Nudge`→0, `Medium`, `Travel` | 52 % of the rise left at alpha 0.5 |
| **Sheet in** | translationY full height→0, `Long`, `Arrive`; scrim 0→0.32, `Medium`, Linear | 98 % of travel at 200 ms |
| **Sheet out** | translationY →full height, `Medium`, `Leave`; scrim →0 `Fast` Linear | — |
| **Move within** | offset/position, `Medium`, `Standard` | — |
| **Page** | programmatic pager move, `Long`, `Standard` | — |
| **Roll** | digit column clipped to its line box; new digit from ±100 % line height → 0, old to ∓100 %, `Short`, `Travel`, no alpha; direction = sign of the change | — |
| **Chrome shift** | the shared bottom bars move by the nav bar's height, `Fast`, `Standard`; on push from t = 0, on pop from the frame the outgoing layer's alpha ≤ 0.5 | bars never overlap a layer whose alpha > 0.5 (on push 97.5 % of the shift is done when the incoming reaches 0.5) |
| **Settle** | `Motion.Settle` spring | — |

**Named exception — the Up next takeover** (`RestEndMotion.kt:186–217`): cover spring 0.92/420, words 150 ms after
180 ms, roll 420 ms `Arrive` after 170 ms, hold 1800 ms (wall-clock, carries information), words out 120 ms. It is the
app's one hero moment, already tuned; only its sheet drop joins **Sheet out** (280 → 220 ms), its delays go through
`motionDelay`, and P0-7's clip is added.

### Helpers (Motion.kt)

- `suspend fun motionDelay(ms: Long)` — `delay((ms * scale).toLong())`, scale from
  `coroutineContext[MotionDurationScale]`. Used only by the takeover exception (the rules have no delays).
- Rule builders: `Motion.fadeIn()`, `fadeOut()`, `crossfade()`, `expand(from)`, `collapse(towards)`,
  `lateralEnter(dir, nav: Boolean, layer: Top|Beneath)`, `lateralExit(...)`, `rise()`, `sheetIn()`, `sheetOut()`,
  `moveWithin()`, `page()`, `roll(dir)`, `colour()`. Screens never write `tween(…)` themselves; lint-by-grep in
  review: no `tween(` / `spring(` outside Motion.kt and RestEndMotion.kt.
- `@Composable fun animationsOff(): Boolean` — the animator scale == 0 (pattern at RestAlert.kt:224–227), for the few
  places that change *behaviour* at scale 0 (predictive-back preview, takeover still frame).

### DESIGN.md "Motion" rule — proposed wording

> Only state changes move. State changes take 150–220 ms; navigation and sheets up to 300 ms, readable by 150 ms;
> exits are faster than entrances. Travel that comes with a fade stays visible; sideways means sequence, counts roll in
> the direction they change. Motion that follows a finger is instant. Every value comes from `ui/theme/Motion.kt`.
> Android's animation scale, including Remove animations, applies to animation; thresholds that carry information
> (a slow save, a slow load) are real time. No frame is ever blank.

---

## Layers and chrome (the model behind P0-1, P0-2, P0-3, P1-2)

Today every route lays out inside one `Box(Modifier.padding(inner))` under a Scaffold whose `bottomBar` holds the
stopwatch bar, the rest bar (or alarm bar) and the nav bar (`RebuildApp.kt:300–372`); the `NavigationRail` sits beside
the Scaffold at ≥ 600 dp (`:285–297`). Any change of bars changes the shared padding of every route.

New model:

1. **Content area = full window.** The Scaffold no longer pads content by its bottom bar (`contentWindowInsets` and the
   `inner` bottom/start padding are not applied to the route box).
2. **Each layer pads itself with its own final insets**: status bar; start = rail width if *its* route shows the rail;
   bottom = shared-bars height (measured, a state) + nav bar height if *its* route shows the nav bar; IME as today.
   During push/pop the outgoing and incoming layers therefore differ by the nav height or rail width, and neither ever
   changes during the transition.
3. **Nav bar / rail: one overlay instance**, drawn above the layers, bottom- or start-aligned. Its alpha:
   - tab switch (both routes show it): 1, indicator animates as Material does today;
   - push to a route without it: follows the outgoing layer's alpha (Fade out);
   - pop to a route with it: follows the incoming layer's alpha (Fade in);
   - it never slides and never changes size; it takes input only when its alpha is 1 and its route is the target.
4. **Shared bars** (stopwatch, rest, alarm): one overlay column above the nav overlay, bottom offset = nav height ×
   `navShown`, where `navShown` follows the **Chrome shift** rule (push: 1→0 over 100 ms from t = 0; pop: 0→1 over
   100 ms starting when the outgoing alpha ≤ 0.5, i.e. t = 50 ms). Bars appear/disappear by **Expand/Collapse**
   (P1-2); the target layer (and, during a push/pop, the outgoing layer) take their bottom inset from the bars' animated
   height, so the dock rides it. Inactive kept tabs read the bars' **settled** (target) height, so they re-measure once
   per bar change, not on every frame of the Expand (`KeepAliveTabs` measures every kept tab each pass,
   `RebuildApp.kt:529`).
5. **Snackbar** is an overlay above the shared bars and the nav overlay; it never changes any layer's insets.
6. **z-order** (bottom → top): layers (outgoing below incoming on push, incoming below outgoing on pop), nav overlay,
   shared bars, snackbar, receipt sheet + scrim, RestEndOverlay (topmost; a rest ending over the receipt covers it).

Acceptance (in the verification plan): in every frame of Today→workout and workout→Today, with and without a running
rest, light and dark, and in landscape (rail), the workout's Log set top edge is at the same y (± 1 px) whenever it is
visible, and no shared bar overlaps a layer drawn at alpha > 0.5.

---

## Inventory, in priority order

Format: **where** · **today** (frame evidence) · **should** (rules) · **why** · **verify** (scale 1 for timing,
scale 5 for shape; every P0/P1 gets both).

### P0 — continuity bugs

#### P0-1 Push and pop between screens

- **Where.** `RebuildApp.kt:469–487` (`AnimatedContent`, spec :479), `KeepAliveTabs` `:508–535`, chrome `:278–370`.
- **Today.** The tab is unplaced the frame the push starts; the pushed screen starts at alpha 0 **and size 0×0**
  (default `SizeTransform(clip = true)` from empty content), so it grows from the top-left.
  workout.mp4: blank 10.984, 11.028; clipped 11.129–11.264 (Log set cut at 11.264); settled 11.318. strip.mp4 blank
  11.736–12.154; tabs.mp4 Plan › Change split blank 24.091–24.168, clipped to 24.340; dark.mp4 black 16.793–16.888;
  chart.mp4 blank 3.468–3.751. Pop superimposes both screens (tabs 27.063, chart 18.0, dark 3.992; presence 0.35 and
  0.14). Plan's "Change split" replays its frozen ripple after return (tabs 27.108–27.5). Nav bar vanishes in the push
  frame.
- **Should.**
  - Replace the route `AnimatedContent` with a `StackTransition` that owns tab and pushed layers, knows direction from
    the stack depth, and uses the layer model above (no size animation; every layer full size from its first frame).
  - **Push:** outgoing **Lateral exit** as the layer beneath (alpha and −`Parallax` over 100 ms); incoming **Lateral
    enter** as the top layer (+`Shift`→0 over `Long`, `Travel`; alpha over `Short`). Nav overlay follows the outgoing
    alpha; shared bars **Chrome shift** from t = 0.
  - **Pop:** outgoing top layer **Lateral exit** (alpha and +`Shift` over 100 ms); incoming beneath **Lateral enter**
    (−`Parallax`→0 over `Long`; alpha over `Short`). Nav overlay follows the incoming alpha; bars Chrome shift from
    t = 50 ms.
  - **t = 0 is the first frame the incoming layer has been drawn.** Mechanism: each layer has a
    `MutableTransitionState<Boolean>`; on a stack change the incoming layer is composed and placed at alpha 0 with the
    outgoing layer untouched at alpha 1; a `LaunchedEffect(Unit)` in the incoming layer awaits one `withFrameNanos {}`
    (its first frame has then been drawn) and only then sets both target states. A long first composition (the
    workout's ≈ 340 ms on the emulator) therefore shows the old screen, never canvas or a half-grown screen.
  - Input and semantics go to the target layer from t = 0 (outgoing gets cleared semantics, as Setup.kt:198 does, and a
    consuming `pointerInput`).
  - Ripples: reset a tab's `InteractionSource`s when it becomes inactive, so a returned-to screen is still.
- **Why.** Opening the workout is the first act at the gym; a blank flash and a screen drawing itself from the corner
  reads as a crash. The visible 24 dp of travel says "deeper" on push and "back" on pop.
- **Verify.** Scale 1 and 5, light and dark, with and without a running rest, portrait and landscape (rail):
  Today→workout→Today, Plan→Change split→back, History→exercise→back. `blankframes.py` = 0; `presence.py` ≥ 0.40;
  Log set y constant ± 1 px in every frame it is visible (rsheet at scale 1 and a per-frame column scan of the amber
  button's top edge); the first frame in which the workout is visible shows its title, Finish, dock and Log set
  whole; at scale 1 the time from tap to "incoming alpha ≥ 0.5" is ≤ first-frame time + 75 ms.

#### P0-2 Finish → receipt

- **Where.** `RebuildApp.kt:266–273` (`receiptFor` set in a later effect), `:422–424` (workout route removed in another
  effect), `:393–397` + `WorkoutReceipt.kt:123–127` (`ModalBottomSheet`), finish dialog `WorkoutScreen.kt:328`,
  receipt haptic `WorkoutReceipt.kt:112`.
- **Today (receipt.mp4).** Log set reads "Saving…" behind the dialog 1.24–1.50; bare canvas under the fading dialog
  from 1.521; fully blank 1.673–1.772; canvas + nav bar 1.804–2.046; Today and the receipt appear together at 2.286
  (the sheet's travel is never seen). `blankframes.py` 10, presence 0.00.
- **Should.**
  - The runner's `onSaved` for `FinishWorkout` only records `receiptPending = workoutId` (saveable). Everything visible
    keys off the **composed** state: in the composition where `state` shows that workout finished and
    `activeWorkoutId == null`, `receiptFor` is derived (`receiptPending?.takeIf { finished }`), the sheet starts, and
    the render stack drops the workout (`nav.stack.filterNot { it == Routes.WORKOUT && state.activeWorkoutId == null }`)
    — all in that one frame. The existing `removeAll` effect (`RebuildApp.kt:422–424`) then cleans the real stack; a
    restore or a discard never sets `receiptPending`, so they never show a receipt.
  - Under the receipt, workout → Today is a **Crossfade** with no travel (the scrim covers it); Today is laid out with
    its final insets (layer model), nav overlay fading in with it.
  - The receipt is an **in-app sheet** drawn in `RebuildApp` (not a window): **Sheet in** from t = 0; its rows ride
    with it (no separate rise); no check animation, no overshoot (DESIGN.md: no celebration beyond a tick).
  - Haptic: the confirm tick fires on the first frame the sheet reaches 98 % of its travel (≈ 200 ms), so the tick and
    the landing coincide.
  - Done / scrim tap / drag past threshold: **Sheet out**.
  - In-app sheet conditions (lead decision 2), all required:
    1. z-order above the bottom bars, shared bars and snackbar, below `RestEndOverlay` (layer model, item 6);
    2. the app behind has cleared semantics and takes no input; focus moves into the sheet; `paneTitle` announced once;
    3. Back closes the sheet before the stack (a `BackHandler` registered while it is shown);
    4. the sheet's content scrolls and stays below the status bar at font scale 2.0 (max height = window − status bar);
    5. it keeps the navigation-bar inset at its foot;
    6. `BodyweightDialog` (`WorkoutReceipt.kt:232`) still opens above it (a window, so it is above by construction);
    7. open state survives rotation (`receiptFor` is already saveable; sheet starts at rest after recreation, no
       replayed entrance);
    8. drag-to-dismiss with a velocity threshold (`AnchoredDraggableState`, positional threshold 50 %, velocity
       ≥ 1000 dp/s), and a scrim tap dismisses;
    9. tests that find the receipt through a dialog root are updated to find `workout-receipt` in the main tree;
    10. predictive back on the sheet (sheet follows `BackEventCompat.progress` down to 20 % of its height) is welcome
        but optional.
- **Why.** Finishing should feel settled and certain; an empty app after "Finish" makes a lifter doubt the save.
- **Verify.** Scale 1 and 5, light and dark, font 1.0 and 2.0: `blankframes.py` 0, `presence.py` ≥ 0.40 between the
  dialog's last frame and the sheet at rest; the sheet is seen at ≥ 3 positions at scale 1 on a device (≥ 2 on the
  emulator) and its only travel is vertical; Today visible (dimmed) in every frame; haptic within one frame of the
  98 % frame (method in the verification plan).

#### P0-3 Tab switch

- **Where.** `KeepAliveTabs` `RebuildApp.kt:513–516`.
- **Today.** tabs 3.425, 3.454 blank, History fades in 3.506–3.566; 2–3 blank frames per switch (19 total); dark
  8.412–8.440 black. Presence 0.00 / 0.02.
- **Should.** **Crossfade**: the outgoing tab stays placed for 100 ms and fades out; the incoming fades in from t = 0,
  where t = 0 is its first drawn frame (P0-1 mechanism). No travel (tabs are peers). Nav overlay opaque; indicator as
  Material.
- **Why.** A tab tap should simply change the screen; a flash reads as a reload, which kept tabs exist to avoid.
- **Verify.** Scale 1 and 5, light and dark: `blankframes.py` 0, `presence.py` ≥ 0.40 for Today→History→Plan→More→
  Today. Perf: `design-review/perf/measure.py` main-thread CPU per tap does not regress (placing a kept tab for 100 ms
  is draw-only).

#### P0-4 The workout greys out on every log, skip and start

- **Where.** `ActionRunner.kt:20–60` (`busy` through the 500 ms cooldown, :72), 81 `enabled = !env.runner.busy` sites
  (Log set `LogEntry.kt:344/346`, Finish `WorkoutScreen.kt:205`, unit chip, rest bar +30 s/Skip), label
  `LogEntry.kt:335`.
- **Today.** workout.mp4 Log set 22.029: Log set, Finish, unit chip grey and "Saving…" until 22.593; set lands
  22.618; +30 s/Skip and Log set grey until 22.864. Skip 33.277–33.65 shows "Saving…" on Log set. A drag reorder
  shows "Saving…" on Log set (strip2 5.66–6.0). Start grey 9.861–10.591 (strip.mp4 1.3 s with seeded data).
- **Should.**
  - `busy` stays the input guard (`run` returns false while busy, cooldown included).
  - New `runner.showsBusy`: true only after a save has run ≥ `BusyAfter` (300 ms, **wall-clock**: a coroutine
    `delay` on a non-animation clock, i.e. not inside `MotionDurationScale`), never during the cooldown. Controls use
    `enabled = !runner.showsBusy`; `onClick` checks `busy`.
  - "Saving…" only on the button whose action is running (`run(action, tag = "log-set")`), only while `showsBusy`;
    label **Crossfade**; disabled colours **Colour** rule.
- **Why.** The most frequent visual event in the app is noise: the set saved and the screen flashed grey.
- **Test changes (lead decision 3).** `AccessibilityDeviceTest.kt:176`: use a fake repository whose save takes
  1000 ms, assert `log-set` shows "Saving" and is not enabled after 300 ms of real time; add an assertion that a second
  tap during the guard logs nothing (cf. `SetGroupsDeviceTest.kt:264`); add `ActionRunnerTest` cases: `showsBusy`
  false before 300 ms, false during cooldown, true after 300 ms of a slow save, independent of animator scale.
- **Verify.** Scale 1 and **scale 0**: between Log set and the ledger update, Log set keeps its amber fill and label
  (on the emulator's ≈ 560 ms save it may dim from 300 ms; on a device it should not dim); +30 s/Skip never grey after
  the bar appears; Skip never relabels Log set.

#### P0-5 Start workout: Today rewrites itself before leaving

- **Where.** `HomeScreen.kt:99–100` (push in `onSuccess`, after the state recomposed Today).
- **Today.** workout.mp4 10.647: the full "In progress · Upper B" card replaces "Rest day" and stays ≈ 340 ms while
  the workout composes.
- **Should.** Push in the frame the new state arrives: `opening = true` before `run`; in composition
  `if (opening && state.activeWorkoutId != null) SideEffect { opening = false; onOpenWorkout() }`. With P0-1 the
  workout's first composition happens behind a Today that has not started fading; at most one frame of the new card can
  draw. Today's hero card swap itself uses **Fade in over** with a size transform
  (growing by **Expand**'s size spec, shrinking by **Collapse**'s), so the content below moves instead of jumping —
  this also covers Discard and Finish from Today and the day changing at midnight.
- **Verify.** Scale 1 rsheet over the Start tap: no frame after the tap shows the "In progress" card at alpha > 0.5
  for more than one frame.

#### P0-6 Cold launch

- **Where.** `res/values/themes.xml` (`windowBackground #FAF9F7`), `res/values-night/themes.xml` (`#121010`),
  `MainActivity.kt:85–101` (spinner screen).
- **Today.** coldlaunch.mp4: white splash 1.749–3.065 (pixel #FFFFFF), icon fades 3.065, cut to canvas + spinner
  3.275, still loading at 5.05. setup.mp4: spinner 3.32–3.67, welcome cuts in 3.90.
- **Should.**
  - Add `androidx.core:core-splashscreen`. In `values/themes.xml` and `values-night/themes.xml` a
    `Theme.Rebuild.Starting` with parent `Theme.SplashScreen.IconBackground`: `windowSplashScreenBackground` = canvas
    (`#F3EFE9` / `#0F0E0D`), `windowSplashScreenIconBackgroundColor` = `@color/launcher_background` (#8A5900, both
    themes), `windowSplashScreenAnimatedIcon` = `@drawable/ic_rebuild_foreground` (cream strokes `#FFF3D9` on the brown
    disc, exactly the launcher icon; the foreground alone on the canvas would be ≈ 1.05:1, invisible),
    `postSplashScreenTheme` = `Theme.Rebuild`;
    the manifest activity uses the starting theme (works API 26+). `Theme.Rebuild`'s `windowBackground` becomes the
    same canvas colours.
  - `installSplashScreen()` before `super.onCreate`; `setKeepOnScreenCondition { model.repository.value == null &&
    model.loadError.value == null && elapsed < SplashCap }` (real time).
  - Exit (`setOnExitAnimationListener`): only the splash view animates, by **Fade out**, over app content that is
    already drawn at alpha 1 underneath (one surface moving). No scale, no content fade. Then `remove()`.
  - If loading exceeds 2 s or fails: the spinner/"Try again" screen appears by **Fade in**.
- **Why.** The first second should look like one surface coming into focus.
- **Verify.** Light and dark: the splash background pixel equals the first app frame's canvas pixel (± codec); the
  icon disc's pixel differs from the canvas by ≥ 3:1 contrast (expected ≈ 5.3:1 light, ≈ 3.1:1 dark) and the strokes
  are visible on it; `blankframes.py` = 0 over the splash frames; no
  spinner on a normal launch; `blankframes.py` 0; a forced load error shows "Try again" without waiting 2 s.

#### P0-7 Up next takeover: the old numeral is drawn outside the sheet

- **Where.** `RestEndMotion.kt:243–251` (sheet top), `:291–300` (old numeral at alpha 1 until the roll).
- **Today.** rest.mp4 62.650–62.715: the sheet has risen ~15 % but a 240 sp "1" is drawn over the uncovered workout.
- **Should.** Clip the content column to the sheet: `drawWithContent { clipRect(top = sheetTop) { drawContent() } }`
  with the same `top` the sheet uses. Keep the rest of the choreography (named exception); drop 280 → 220 ms
  (**Sheet out**); delays via `motionDelay`. Haptic: fire on the first frame `cover ≥ 0.98` (`snapshotFlow`), not when
  `animateTo` returns — a 0.92-damped spring reaches its visibility threshold well after it looks landed.
- **Verify.** Scale 1 and 5 rsheet of a rest end: ink numerals only ever on amber pixels; haptic within one frame of
  the first frame the sheet's top is within 2 % of the screen top.

#### P0-8 Exercise strip drag

- **Where.** `WorkoutScreen.kt:493` (`zIndex`), `:545` (`lift.snapTo(1f)`), `:546` (lift haptic), `:449` (slot
  haptic), `:524` (drop tween 180), `:361–366` (scroll on index change), pill colour `:736`.
- **Today (strip2.mp4).** Lift in one frame (4.318). A neighbour's label drawn over the lifted pill 5.540–6.006
  ("Machi|able row"), still overdrawn after the drop. Drop settles ≈ 6.0; a second, separate strip scroll 6.69–6.91.
- **Should.**
  - Lift: scale, shadow and border alpha by **Settle** (85 % at 120 ms), launched in the same `onDragStart` callback as
    the haptic. Translation follows the finger from the first move (never animated).
  - Z-order: draw the lifted pill in an overlay above the `LazyRow` at `dragLeft` and leave an empty same-width slot in
    the row; neighbours' placement animations can no longer draw over it.
  - Neighbours make way: `animateItem(placementSpec = Settle)`.
  - Drop: the overlay pill **Settle**s into its slot, then is swapped for the row item in the same frame.
  - After the drop: no strip scroll if the dropped pill is ≥ 50 % visible (compare ids, not indices); otherwise one
    scroll by **Move within** starting at the drop.
- **Why.** The held pill must stay on top, and the strip must stop moving when the finger lets go.
- **Verify.** Scale 1 and 5 rsheet of a drag past two pills: the lifted label never cut; after the drop one motion only;
  haptics (verification plan) within one frame of the lift's first frame and of each neighbour's first moving frame.

#### P0-9 Keyboard: rail and subtitle pop

- **Where.** `WorkoutScreen.kt:247–248`, `Components.kt:155–163`.
- **Today (rest.mp4).** 11.073→11.120 rail + subtitle vanish in one frame, ledger jumps ~70 px; on close 13.234 they
  pop back.
- **Should.** One `AnimatedVisibility` for rail + subtitle: hide by **Collapse** (towards top) as soon as the field is
  focused; show by **Expand** once the IME has finished hiding (`isImeVisible` false and `imeAnimationTarget` bottom 0).
- **Verify.** Scale 1 and 5 rsheet over a field tap and Back: the ledger's first row passes through intermediate
  positions; **the reps field moves only with the IME inset** (the dock riding the keyboard, as today).

### P1 — every set

#### P1-1 Set logged (one choreography)

- **Where.** `SetLedger.kt:160` (`animateContentSize`), `TableRow` `:263–266` (band), `RowToday` `:307–336`,
  `TodayCell` `:340+`, `LedgerHeader` count, pill progress `WorkoutScreen.kt:763`, rest bar `RebuildApp.kt:345–355`,
  tick `LogEntry.kt:306–309`.
- **Today.** workout.mp4 22.593 → 22.618: one frame swaps row 1, the band, "Up next", "Today 1 of 2", the pill's 1/2,
  "Set 2 of 2", and pops the rest bar in; the tick follows ≈ 500 ms later (after the cooldown).
- **Should.** t = 0 is the frame the saved state is composed. **Focal element: the new value in row n.**

  | Element | Starts | Rule | Direction | Ends |
  |---|---|---|---|---|
  | Haptic confirm tick | t = 0 (`onSaved`, before the cooldown) | — | — | — |
  | New value + its card in row n (focal) | 0 | **Fade in** (dashed slot: **Fade out**) | none (in place) | 150 ms |
  | Current-row band, row n → n+1 (one band drawn behind the rows at an animated y) | 0 | **Move within** | down | 220 ms |
  | Ordinal accent colour, row n → n+1 | 0 | **Colour** | — | 150 ms |
  | "Today n of N" number | 0 | **Roll** | up (count increased) | 150 ms |
  | Pill "n/N" | 0 | instant (cut: too small, outside the glance) | — | 0 |
  | Rest bar in, dock riding it | 0 | **Expand** (P1-2) | bar edge up | 220 ms |
  | Entry fields, prefill, "Set n+1 of N" | 0 | instant (must not move) | — | 0 |
  | *Last set only:* dock content Entry → Target done | 0 | P1-3 | up (dock top) | 220 ms |
  | *Last set only:* count colour → accent | 0 | **Colour** | — | 150 ms |
  | *Last set only:* pill badge n → ✓ | 0 | **Fade in over** (opaque badge discs) | — | 150 ms |

  The value no longer rises, so nothing moves up against the band moving down in the same rows; the only upward
  motions are outside the ledger (header digit, the bar's edge).
  **Cut list if it reads busy at scale 1** (in this order): (1) header Roll → **Crossfade**; (2) band **Move within** →
  band **Crossfade** between rows; (3) rest bar Expand stays (it's the dock's reason to move).
  Extra rows (beyond target) and warm-ups add a row: the ledger grows by **Expand** (`animateContentSize` with the
  Expand size spec), the new row's value **Fade in**.
- **Why.** The ledger is the proof of work: the set appears where it belongs and the "next" band steps down — one
  glance answers "did it log, what's next".
- **Verify.** Scale 1 and 5 rsheets over set 1 and set 2 (Target done), light and dark, font 1.0: everything at rest
  ≤ 220 ms after the state frame at scale 1; the value is at alpha ≥ 0.5 by 75 ms; the band at ≥ 3 positions at
  scale 5; tick within one frame of the state frame. Font 2.0: same ledger rules on stacked rows (band behind the
  block).

#### P1-2 Shared bottom bars: rest, stopwatch, alarm

- **Where.** `RebuildApp.kt:338–355` (stopwatch bar, rest bar, alarm bar), `RestBar.kt:100–118`.
- **Today.** Rest bar appears at full size in one frame and the dock jumps ≈ 86 dp (workout 22.618); Skip removes it
  in one frame (33.545; the Target done panel drops ≈ 240 px at 50.929). After a takeover the jump is hidden by the
  sheet (rest 65.48).
- **Should.**
  - Rest bar and stopwatch bar in: **Expand (opaque bar)** with `expandFrom = Alignment.Top` (the bar rises from the
    bottom edge, the box grows, the target layer's bottom inset follows, so the dock rises in lockstep).
  - Out (Skip, Dismiss, end with Flash/None, stopwatch logged/discarded): **Collapse (opaque bar)** towards the top
    edge.
  - Out when the Up next takeover starts: instant (the sheet grows from the bar's exact top edge).
  - Rest bar ↔ alarm bar (`:350–354`): **Fade in over** (two amber bars in one place: a crossfade would dim the amber
    to ≈ 63 % for ≈ 60 ms); a height difference by Expand/Collapse (opaque bar).
  - Progress line: per-second updates stay instant; jumps from +30 s / −30 s / Set time > 2 % use **Move within**.
  - On push/pop the bars follow the **Chrome shift** rule (layer model).
- **Why.** The bar arriving from the bottom edge says "a timer started down here"; the dock moving with it rather than
  jumping keeps Log set findable.
- **Verify.** Scale 1 and 5 rsheets over a log, a Skip, a stopwatch start/log: bar top and dock top always the same
  distance apart; ≥ 3 positions at scale 5; takeover start still grows from the bar's edge.

#### P1-3 Target done: dock content changes kind

- **Where.** `LogEntry.kt:592–655` (`TargetMetPanel` in `EnterOnce`, `Components.kt:700–711`; `EnterOnce`'s only
  caller is `LogEntry.kt:606`), dock `LogEntry.kt:693–719`, `OrderBadge` `Components.kt:643–666`.
- **Today.** workout 43.880: entry gone, dock taller, row filled, rest bar in, all in one frame; empty dock 43.880 and
  43.919; panel rises 43.952–44.088; pill badge 1→✓ in one frame.
- **Should.** Dock content is one `AnimatedContent(entryKind)` (Entry / TargetMet / Skipped / open-set notice /
  stopwatch-elsewhere) using **Fade in over**: the incoming kind gets a **static** opaque backing in the dock colour
  filling the dock's content area (it does not travel), fading in by Fade in over the outgoing kind, which stays at
  alpha 1 until the backing is opaque; inside the backing the incoming content **Rise**s 8 dp (the fixed `Nudge`, lead
  decision 4). So Log set's amber and Next's amber, which overlap, never dim. Dock size by **Expand** or **Collapse**
  (whichever way the height goes), so the dock's top edge glides and Next sits at its final place from frame 0.
  `EnterOnce` is deleted. The ✓ in "Target done" has no separate animation. Pill badge **Fade in over** (P1-1 table).
- **Why.** "Target done" is the app's one small reward; it lands once, quietly, and Next is readable fast.
- **Verify.** Scale 1 and 5 rsheet over the last set: no frame with an empty dock; dock top continuous; **Next at alpha
  ≥ 0.5 by 75 ms and fully opaque by 150 ms** after the state frame.

#### P1-4 Next exercise and selecting a pill

- **Where.** `WorkoutScreen.kt:265` (`key(focused.id) { ExerciseFocus(…) }`), dock slot `:1001`, rail scroll
  `:361–366`, pill colour `:736`.
- **Today.** workout 56.801: everything swaps in one frame; strip scrolls 56.80–57.06 afterwards.
- **Should (mechanism).**
  - Replace `key(focused.id)` with `AnimatedContent(targetState = focused.id, transitionSpec = { EnterTransition.None
    togetherWith ExitTransition.KeepUntilTransitionsFinished })` — no slide, no size transform; alpha and offset are
    applied per slot from the scope's `transition` (below). Each content is still a fresh `ExerciseFocus` keyed by id (state isolation kept:
    drafts, dialogs and `rememberSaveable(we.id)` stay per exercise).
  - Inside the content lambda, read the scope's `transition` (`Transition<EnterExitState>`) and derive
    `val dx by transition.animateFloat(transitionSpec = { lateral spec for this segment }) { when (it) { PreEnter ->
    dir * Shift; Visible -> 0f; PostExit -> -dir * Shift } }`, `dir = sign(newIndex − oldIndex)`. Pass
    `Modifier.graphicsLayer { translationX = dx }` into `ExerciseFocus` as `bodyModifier`, applied to the
    `DockedLayout` **body slot only**, with the body's alpha from the same transition (**Crossfade**: the body is
    content over the canvas).
  - The dock slot gets no translation and uses **Fade in over**: the incoming dock is opaque (its Surface) and fades in
    by Fade in on top; the outgoing dock's alpha is held at 1 inside the incoming dock's rect and fades out by Fade out
    outside it (`drawWithContent` with two `clipRect`s) — the case where a Target done panel (taller) is replaced by
    an entry. Log set's amber, the field tiles and the Warm-up chip never dim; differing values truly crossfade.
  - Body enter segment: **Lateral enter** (`Medium`, `Travel`); body exit segment: **Lateral exit** (`Fast`, Linear).
    `AnimatedContent` keeps the outgoing content until the longest of these (220 ms) has finished.
  - Unpinned (font > 130 %, landscape, short window): the entry is inside the body, so `dir = 0` — the whole content
    crossfades with no travel (Log set never travels).
  - Strip: same frame, the strip scrolls by **Move within** (`animateScrollBy` from layout info); selected pill colours
    by **Colour**.
  - Outgoing content: cleared semantics, consuming `pointerInput` (no duplicate `log-set` tags).
- **Why.** Next moves along the strip; the body arriving from the right with the strip confirms the order and makes a
  wrong tap obvious. 52 % of the 32 dp travel (≈ 17 dp) is seen at alpha ≥ 0.5.
- **Verify.** Scale 1 and 5: body from the right on Next, from the left going back; Log set y and x constant every
  frame (pinned); Log set's fill colour within 4 grey levels of its settled value in every frame across Next and a pill
  tap (verification 4b); font 2.0: no horizontal travel; body readable (alpha ≥ 0.5) by 75 ms.

#### P1-5 Hold menu on a pill

- **Where.** `WorkoutScreen.kt:652–704` (M3 `DropdownMenu`).
- **Today.** strip 25.27–26.63 press ripple while held; menu 26.660 with Material's scale+fade; Back 27.755.
- **Should.** Keep Material's menu motion. While the menu is open the held pill shows the lift border at full alpha
  (no scale, no shadow), applied by **Colour**, so it is clear which pill the menu belongs to.
- **Verify.** rsheet: the pill under the menu is marked while it is open.

#### P1-6 Set deleted (Edit set → Delete) — new

- **Where.** `SetEditing.kt:268` (`Action.DeleteSet`, dialog closes on success).
- **Today.** Not recorded; by code the ledger swaps in one frame after the platform dialog exit.
- **Should.** The dialog exits as the platform does. In the ledger (t = the state frame): the deleted row's value
  **Fade out**; if the row remains as a slot (it was within the target), its dashed slot **Fade in** (Crossfade);
  if rows are removed (extras, warm-ups), the ledger shrinks by **Collapse** and rows below move up within it; rows
  above never move. Band **Move within** to the new next row (up). "Today n of N" **Roll** down. Pill count instant.
  If the target becomes unmet, the dock content changes back by P1-3's rule. No haptic (today none).
- **Verify.** rsheet at scale 1 and 5 deleting set 2 of 2 and an extra set 3: no row above the deleted one moves;
  count rolls down.

#### P1-7 Exercises added, removed or skipped in the strip — new

- **Where.** `WorkoutScreen.kt:276–298` (add via picker → `AddWorkoutExercise`, focus `pendingFocus`),
  `FlexibleWorkout.kt:208` (`RemoveWorkoutExercise`), hold menu remove/skip `WorkoutScreen.kt:399–410`.
- **Should.**
  - Pill inserted: `animateItem(fadeInSpec = Fade in, placementSpec = Move within, fadeOutSpec = Fade out)` on every
    pill (non-lifted); the Add pill slides right by Move within.
  - Focus moves to the new exercise: P1-4 with `dir = +1` (it is later in the order); strip scrolls by Move within.
  - Pill removed: Fade out; neighbours close the gap by Move within; focus moves to the next exercise by P1-4 with
    `dir = sign(newIndex − removedIndex)` (normally +1, −1 if the last one was removed).
  - Skipped today: pill label "Skipped" **Crossfade**; dock content to the Skipped panel by P1-3.
- **Verify.** rsheet at scale 1 and 5 for add, remove (middle and last), skip: one strip motion and one body motion, no
  pill drawn over another.

#### P1-8 Error snackbar — new

- **Where.** `RebuildApp.kt:206` (`runner.errors` → `snackbar.showSnackbar`), host `:306`.
- **Should.** Keep Material's `SnackbarHost` enter/exit. It is an overlay above the shared bars and nav overlay (layer
  model item 5) and never changes any layer's insets, so it never pushes the dock.
- **Verify.** Force a save error: Log set's y unchanged while the snackbar shows.

#### P1-9 Dock error line — new

- **Where.** `LogEntry.kt:446` (`ErrorText(draft.error)` **below** the Log row), `Components.kt:248`.
- **Today.** By code, an error grows the bottom-pinned dock at its foot, so Log set moves up by the line's height.
- **Should.** Move `ErrorText` **above** the Warm-up/Log row (directly under the fields it is about). The dock is
  bottom-pinned, so growth above the Log row moves only the fields up and Log set stays put. Line appears by
  **Expand**, disappears by **Collapse**.
- **Verify.** Log a set with an invalid value at scale 1: Log set's y unchanged; the fields rise with the line.

#### P1-10 Unpinned entry at large text, landscape, short windows — new

- **Where.** `WorkoutScreen.kt:1001` (`if (pinned)`), inline `if (!pinned) Entry(…)`.
- **Should.** Every rule above applies to the inline entry with these differences: exercise switch has no travel
  (P1-4); Target done's content change uses P1-3 inside the body (the body grows/shrinks by Expand/Collapse, rows below
  move with it); the compact rest row uses P1-2's Expand/Collapse.
- **Verify.** Font 2.0 and landscape recordings of a log, Target done and Next: no horizontal travel; no blank or
  doubled frames.

### P2 — navigation and the rest

#### P2-1 Predictive back

- **Where.** `RebuildApp.kt:383` (`BackHandler`), manifest `enableOnBackInvokedCallback="true"`, activity-compose
  1.10.1.
- **Today.** dark 2.25–3.76: only the system chevron; the workout doesn't move. Release 3.99 → superimposed crossfade.
- **Should.** For pushed screens without a draft guard, `PredictiveBackHandler` seeks the P0-1 pop
  (`SeekableTransitionState`): top layer scales to `BackPeek` 0.94, moves up to 24 dp toward the gesture edge, corners
  0→28 dp, 8 dp shadow; the layer beneath is placed with alpha 0.6 + 0.4 p and −`Parallax`·(1 − p); nav overlay alpha
  follows it. Commit: finish the pop's remaining fraction (Lateral exit). Cancel: **Settle**. Routes whose Back asks
  first (`WorkoutScreen.kt:183` dirty notes, `Routes.isEditor`, MeasureScreen) keep a plain `BackHandler`. At scale 0
  (`animationsOff()`): plain `BackHandler`.
- **Verify.** Slow swipe on Exercise history and on the workout, commit and cancel: preview follows the finger; no
  superimposed text on commit; cancel returns without a jump.

#### P2-2 Setup

- **Where.** `Setup.kt:185–191`, `SetupProgress` `:316–325`, `RebuildApp.kt:378–386`.
- **Today.** setup 7.21 Get started greys; steps slide `it/5` (≈ 216 px); header slides with the page (7.442). Plan
  save greys 24.93–25.63 then **cuts** to "Know when to lift again" (25.700); Go to Today **cuts** 30.32→30.52.
- **Should.** Header (back + progress) fixed outside the `AnimatedContent`; segments fill by **Colour**. Steps:
  **Lateral enter/exit** (navigation, `Long`). Plan → rest-alert step joins the same `AnimatedContent`; the progress bar
  **Fade out**. Setup → Today: P0-1 push with the nav overlay fading in. Keep `LocalSettled`; don't grey the tapped
  button (P0-4).
- **Verify.** setup recording at scale 1: no cut between any two steps; presence ≥ 0.40; progress bar stationary.

#### P2-3 History pager

- **Where.** `HistoryScreens.kt:139`, `:172`.
- **Today.** history 12.41–12.77 stalled ~15 % into a two-page move, then a jump at 12.80.
- **Should.** `beyondViewportPageCount = HISTORY_TABS.size - 1`; tab taps scroll by **Page**.
- **Verify.** rsheet at scale 1 over Body→Workouts: page offset at ≥ 4 positions, no stall.

#### P2-4 Expand/collapse lists and options

- **Where.** `WorkoutScreen.kt:982–983`, `HomeScreen.kt:260–264`, `Setup.kt:647–648`, `ExerciseProgress.kt:445–446`.
- **Should.** **Expand**/**Collapse** everywhere; chevron rotates 0→180° by **Move within**; label **Crossfade**.
- **Verify.** rsheet: chevron at intermediate angles; never both labels > 0.5 alpha.

#### P2-5 Charts

- **Where.** `ExerciseProgress.kt:252`, `:274`, `Charts.kt:246`.
- **Should.** Range change: **Crossfade** (both charts). Split by workout: lines and labels **Move within**, key
  **Expand/Collapse**. Touch selection instant.

#### P2-6 Dialogs

Keep the platform window animations (rest.mp4 23.46–23.57 enter, 33.86–33.95 exit; Rest options → Set time clean
24.08–24.61). Inside dialogs only P2-4.

#### P2-7 Choice cards, day buttons, segmented controls

- **Where.** `Setup.kt:391–393, 468–469`, `Components.kt:1148–1149`, `Components.kt:1201` (`PillChoice`, used at
  `Setup.kt:236`, `Setup.kt:732`, `SetGroupsUi.kt:476`).
- **Should.** All fills by **Colour**; press squish **Press**. `PillChoice` keeps an in-place colour change (no sliding
  indicator: its labels wrap at large text).

---

## Haptics (audit)

| Tick | Where | Today | Should | Check |
|---|---|---|---|---|
| Set logged (Confirm) | `LogEntry.kt:306–309` | in `onSuccess`, after the 500 ms cooldown → ≈ 500 ms after the set lands | new `run(…, onSaved)` called right after `repository.execute`, before the cooldown; tick there; the ledger choreography starts on the next composition (same frame or the next) | ≤ 1 frame from the state frame |
| Finish → receipt (Confirm) | `WorkoutReceipt.kt:112` | at the sheet's first composition (before any of it is seen) | first frame the sheet reaches 98 % of its travel (≈ 200 ms) | ≤ 1 frame from that frame |
| Up next lands (LongPress) | `RestEndMotion.kt:199` | when `animateTo` returns (spring threshold, later than it looks) | first frame `cover ≥ 0.98` | ≤ 1 frame |
| Pill lift (LongPress) | `WorkoutScreen.kt:546` | same callback as `snapTo` | same callback as the Settle launch (the lift's first frame is the next frame) | ≤ 1 frame |
| Slot change (TextHandleMove) | `WorkoutScreen.kt:449` | when the order changes; neighbours move on the next layout | unchanged (fires in the input event; the neighbour starts moving in the next frame) | ≤ 1 frame |
| Chart selection tick | `Charts.kt` | follows the finger | unchanged | — |

---

## What must not move

- **The countdown digits** (bar, PiP, notification): change in place once a second, no roll.
- **Entry fields and their values** (kg, reps, RIR, prefill, Warm-up chip, set type): update instantly.
- **Log set**: never travels sideways; its y changes only with the rest/stopwatch bar (P1-2), the dock changing kind
  (P1-3) or the IME — never during push/pop, exercise switch, an error (P1-9) or a snackbar.
- **Chart touch** (ring, guide, readout): follows the finger, no easing; the readout keeps its height.
- **Rows above the changed ledger row**: no reflow and no re-entry when a set is logged, edited, deleted or the exercise
  reopens. No staggered list entrances on the workout or Today.
- **Scroll position and kept tab state** across tab switches.
- **Haptics** fire at the moments in the Haptics table; motion never delays them.
- **The takeover's hold** (1800 ms, tap to dismiss).
- **Nav bar and rail**: never slide or resize; only fade with their layer.
- **Text being read in a transition** is never scaled.

---

## Reduced motion and animator scale

- Compose `animate*`, `Animatable`, `Transition`/`AnimatedContent`/`AnimatedVisibility`, `animateItem`, `animateScrollBy`
  follow `ANIMATOR_DURATION_SCALE` via `MotionDurationScale`. `delay()`, `withFrameNanos` loops and wall-clock timers
  do not.
- **Animations** (the rules) scale. **Information thresholds** (`BusyAfter`, `SplashCap`) and **information holds**
  (takeover hold, the rest itself) are real time at every scale.
- **Scale 0 (Remove animations)**:
  - push/pop/tab: target drawn in the frame after the incoming layer's first frame; never blank (the outgoing layer stays
    until then);
  - ledger, counts, bars, dock size, Target done, strip: final state in one frame;
  - **a normal save shows no busy state** (threshold is real time);
  - drag: the pill still follows the finger; lift and settle instant;
  - predictive back: no preview, Back commits instantly;
  - takeover: existing still frame for 1.5 s; Flash: one 700 ms glow;
  - receipt: sheet at rest at once; haptic on that frame;
  - splash: removed without animation.
- **Scale 5** is for reading shape on the emulator only; timing is judged at scale 1.
- Motion never carries information alone: values, counts, "Up next", the countdown and Target done are present in the
  final frame and in semantics; rolling counts expose one label on the container.

---

## Verification plan

Run after implementation, recordings in `motion/after/`, tools in `motion/tools/`.

1. **Curves (JVM, `MotionCurvesTest`)** over the rule builders: (a) every Lateral enter and Rise leaves ≥ 50 % of
   its travel when alpha reaches 0.5; (b) Crossfade: no t with both alphas > 0.5, summed alpha ≥ 0.66 at all t;
   (c) Chrome shift ≥ 95 % done when the incoming alpha reaches 0.5 on push, starts only when outgoing ≤ 0.5 on pop;
   (d) Sheet in reaches 98 % by 200 ms; (e) no rule longer than `Long`; (f) no `tween(`/`spring(` outside Motion.kt and
   RestEndMotion.kt (source scan); (g) Fade in over: the composite of an identical pixel
   (`in·p + (1 − in)·out·p` with out held at 1) stays ≥ 95 % of `p` at every t (it is 100 %), while a plain Crossfade
   over identical pixels would dip to 63 % — and a source scan finds no Crossfade at the opaque-surface call sites
   (P1-2 alarm swap, P1-3 dock content, P1-4 dock, pill badge, Today hero card).
2. **Every P0/P1 item: two every-frame sheets** (`rsheet.sh`), one at animator scale 1 (timing: readable by the stated
   time, at rest within the rule's duration after the state frame, measured from real frame times) and one at scale 5
   (shape: direction, positions, z-order).
3. **Continuity** on every navigation, tab, receipt and dock-change recording: `blankframes.py` = 0 and
   `presence.py` ≥ **0.40** (today's baselines in Evidence).
4. **Log set y**: per-frame scan of the amber button's top edge (column at the button's centre) across push, pop, tab,
   exercise switch, error, snackbar: constant ± 1 px except during P1-2/P1-3/IME.
   **4b. Log set colour**: sample the mean of a 9×9 px patch at the button's centre in every real frame across Next
   (Target done → next exercise) and across a pill tap between two unfinished exercises, light and dark, scale 1: within
   4 grey levels (per channel) of its settled value. Same check on the rest bar's centre across a rest bar → alarm bar
   swap.
5. **Matrix**:
   - light and dark × font 1.0 and **2.0**: log a set, Target done, push/pop to the workout, receipt;
   - **landscape** (rail, ≥ 600 dp; `adb shell settings put system user_rotation 1` with auto-rotate off): push/pop,
     tab switch, log a set (compact rest row, inline entry);
   - **scale 0** (`animator_duration_scale 0`, and transition/window scales 0): log, push, tab switch, receipt —
     `blankframes.py` 0, final state on the next frame, no busy look on a normal save.
6. **Haptics**: record with `adb shell screenrecord --bugreport` (device-time overlay on every frame) and read
   `adb shell dumpsys vibrator_manager` ("Previous vibrations for usage TOUCH": `startTime` per tick, ms). Pair each
   tick with the frame named in the Haptics table: ≤ 1 frame (≈ 16 ms) on a device; ≤ 50 ms on the emulator (state
   the method; emulator frames are irregular).
7. **Performance**: `design-review/perf/measure.py` tab taps (no regression); `frames.py` on a log with the rest bar
   entering: layout ≤ 4 ms per frame during the Expand, else use the fallback in Risks; with a composition/layout trace
   (`composables.py`), each hidden kept tab is measured at most once during the Expand (it reads the settled bar
   height).
8. Reset after runs: animator scales 1, font 1.0, theme light, rotation portrait.

---

## Risks

- **Performance.** Two layers placed during push/tab transitions (draw-only, ≤ 300 ms). P1-2 re-measures the workout
  for 220 ms per bar change — fallback if `frames.py` shows > 4 ms layout per frame: the bar translates over the dock
  for `Medium` and each layer's inset is applied once at the end (one jump, hidden under the moving bar). P1-1 animates
  only the newest row and one band. The in-app receipt removes a window creation.
- **Layer model.** Moving the Scaffold padding into each layer touches every route; `imePadding` and
  `consumeWindowInsets` must be applied per layer exactly as today. The landscape rail and the compact rest row need
  their own recordings (verification 5).
- **Tests.**
  - `AccessibilityDeviceTest.kt:176` (changed per P0-4), new `ActionRunnerTest` cases, `MotionCurvesTest`.
  - Paused-clock device tests: `RestAlertDeviceTest` (takeover drop 280 → 220, haptic moment), `HoldToReorderDeviceTest`
    (:55–63 advance one frame after a hold: lift now animates; the overlay pill changes the tree), `SetupFlowDeviceTest`
    (:131–136 steps), `AdaptiveUiTest:193`. Re-run all.
  - Receipt tests that look in a dialog root (decision 2, item 9).
  - `ScreenshotHarness`: transitions start at their first state at time 0; the harness advances its clock past `Long`
    or the builders start at rest in `LocalInspectionMode`.
  - Two copies of a screen or exercise during transitions: outgoing copies cleared of semantics, so `onNodeWithTag`
    still finds one node.
- **Accessibility.** Outgoing layers inert from t = 0; one spoken label for rolling counts; the receipt's focus and
  `paneTitle`; predictive-back preview announces nothing until commit; all distances fixed dp, so large text does not
  travel further.
- **Not verified on a real GPU.**

---

## Observed, outside motion (for triage)

- strip2.mp4 8.2–10.8: after a drag-reorder, a later tap on `exercise-chip-1` ended with chip 0's hold menu open
  (10.82). Harness timing or `heldTap`/`menuFor` (WorkoutScreen.kt:393, 569, 581). Not investigated.
- A 500 px drag of a 546 px pill does not reorder (strip 33.6): correct per `dragSlot`; long names need long drags.
- `themes.xml` window backgrounds differ from the design canvas (fixed by P0-6).

## Emulator state left behind

Light theme, font 1.0. Changed by the audit: Rest alert = Silent + Up next; an active workout "Upper" (2 sets logged,
order changed). App on the History tab.
