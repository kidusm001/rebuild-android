# Motion critic, round 3: spec v3

**Scope.** I re-checked findings N1–N6 against `spec.md` v3. I diffed it against `spec-v2.md` and checked the new
claims in code and with the curve maths. No app code or data was changed.

## Round-2 findings

| # | Status | Evidence |
|---|---|---|
| N1 (P2): crossfading two identical opaque surfaces dims them | **Fixed** | See the N1 notes below. |
| N2 (P2): the splash icon would be invisible | **Fixed** | See the N2 notes below. |
| N3 (P3): Today's hero card jumps | **Fixed** | The card uses Fade in over plus a size transform: it grows with Expand's size spec and shrinks with Collapse's, so the content below moves with it. Discard, Finish and the day changing at midnight are covered. |
| N4 (P3): wording mismatches | **Fixed** | Rules adds "Expand (opaque bar)" and "Collapse (opaque bar)", and P1-2 names them. Verification 1(b) now says ≥ 0.66, and 1(g) adds the Fade in over property. |
| N5 (P3): the receipt's ordering depended on when the callback ran | **Fixed** | See the N5 notes below. |
| N6 (P3): hidden tabs would re-measure on every frame of the rest bar's entrance | **Fixed** | Layer model item 4: only the target layer (and, during a push or pop, the outgoing one) reads the bar's animated height. Inactive kept tabs read the settled height. Verification 7 counts how often hidden tabs are measured during the Expand (at most once each), using `design-review/perf/composables.py`, which exists. |

### N1 notes

- **The rule.** A new rule, Fade in over, keeps the old surface at alpha 1 underneath while the new opaque one fades
  in on top. Any part of the old surface outside the new one's rectangle fades out. Identical pixels therefore stay at
  100 %, and pixels that differ get a true crossfade.
- **Crossfade is now restricted** to content on a shared background, or to whole screen layers over the canvas.
- **Where it applies:**
  - the dock on an exercise switch (P1-4), built with `EnterTransition.None togetherWith KeepUntilTransitionsFinished`
    plus per-slot alpha, and two `clipRect`s for the case where the old dock is taller;
  - the dock changing kind (P1-3), with a static opaque backing;
  - rest bar ↔ alarm bar;
  - the pill badge;
  - Today's hero card.
- **The checks are real:**
  - a curve test (1(g)) that identical pixels stay at 100 %;
  - a source scan for plain Crossfade at those call sites;
  - step 4b, which samples a 9×9 patch of Log set's fill colour in every frame across Next and a pill tap (and the
    rest bar across the alarm swap), within 4 grey levels.

### N2 notes

- **The fix.** The splash theme now uses parent `Theme.SplashScreen.IconBackground`, with
  `windowSplashScreenIconBackgroundColor = @color/launcher_background` (#8A5900) in both themes and the cream
  foreground as the icon.
- **Contrast, worked out (WCAG):**
  - brown disc on the light canvas: 5.2:1;
  - brown disc on the dark canvas: 3.2:1;
  - cream strokes on the disc: 5.4:1.

  The spec's ≈ 5.3 and ≈ 3.1 are close enough, and both clear its own ≥ 3:1 check.
- **Verification** adds an icon-pixel contrast check and `blankframes.py` = 0 over the splash.

### N5 notes

- `onSaved` now only records `receiptPending` (saveable).
- `receiptFor` and the stack used for rendering (`nav.stack.filterNot { WORKOUT && activeWorkoutId == null }`) are
  both derived in the composition where the state shows the workout finished. The sheet and the route removal can
  therefore never land in different frames.
- Restore and discard never set `receiptPending`.

## New issues at P2 or worse

None.

One non-blocking remark (P3), for the builder:

- P1-3 says "Next sits at its final place from frame 0", but the incoming dock content also rises 8 dp inside its
  static backing. The two statements disagree. While Next is still rising, the 8 dp strip at Next's top and bottom
  edges isn't pixel-identical with Log set under it.
- Either let the button row skip the Rise or reword the sentence. The 4b colour patch at the button's centre is
  unaffected.

## What is good

- Every round-2 answer is in the body, not just in the changes table.
- The new rule comes with a property, a test and a recording check.
- The receipt fix removes the ordering question by construction, rather than by tuning.

VERDICT: SATISFIED
