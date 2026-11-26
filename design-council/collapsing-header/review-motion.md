# Motion review: collapsing screen header

Evidence: the frames in `scratchpad/hdr/` and `scratchpad/sheets/` that I opened (listed in each finding), plus `ui/ScreenHeader.kt`, `ui/Components.kt` (diff), `ui/theme/Motion.kt` and DESIGN.md's Motion rule. I didn't record on an emulator.

## 1. Ranking

| Rank | Style | Score | Reason |
|---|---|---|---|
| 1 | MORPH | 7/10 | The only candidate where one element visibly turns into the other, so the motion explains itself. The title stays readable at every scroll position (`MORPH-light-a-today-1-drag24`, `MORPH-large-a-today-1-drag56`). It moves only when the body leaves or reaches its top. |
| 2 | SLIDE | 5/10 | Gives the same room, but the title is swapped for another rather than transformed. Around f≈0.6 neither title can be read: the large one is gone at f=0.625 and the small one only starts at f=0.6 (`ScreenHeader.kt:204,208`). `SLIDE-light-a-today-1-drag24` shows "Tuesday" as a ~20 % ghost. |
| 3 | FIXED | 3/10 | Correct and calm, but it doesn't fix the problem the owner reported. |
| 4 | RETURN | 2/10 | Chrome moves on every small reverse scroll even though nothing changed, which breaks "only state changes move". The on-screen Back and the primary action (Log) scroll away on pushed screens, then come back half-clipped (`RETURN-light-e-bodyweight` sheet, frames 2-4 and 7). A lifter scrolling one-handed between sets gets a header that bobs. |

## 2. Findings for MORPH (must change before it ships)

1. **P2: the settle ignores the finger and uses a curve instead of the system's release spring.**
   - Where: `ScreenHeader.kt:83-90`.
   - What's wrong: it settles to the *nearer* state from rest with `tween(Short, Standard)`. The curve starts at zero velocity, so a slow lift mid-collapse stalls for a moment before the header re-accelerates. Choosing the nearer state also ignores direction: a user pushing upward who lifts at f=0.45 sees the header spring back open and the body slide down without being touched.
   - `Motion.kt` already has the token for this exact case: `settle()` (critically damped spring for "drag … drop, predictive-back cancel").
   - What would satisfy me:
     - settle with `Motion.settle()`, seeded with the release velocity;
     - choose the target by velocity sign when the release speed is above a small threshold, and by nearer state otherwise;
     - no inline `tween(...)` in a screen file. `Motion.kt:61` says screens never write their own specs, and this is a new one.
     - Name the rule in Motion.kt (for example `headerSettle`) and add a line to DESIGN.md Motion.

2. **P2: a new touch during the settle may fight it.**
   - Where: `ScreenHeader.kt:88`.
   - What's wrong: `animate(...)` runs inside `onPostFling`. Foundation dispatches post-fling outside the scroll mutex, so a finger that lands within the 150 ms is not guaranteed to cancel it. The animation keeps writing `offset` each frame while `onPreScroll` writes it from the finger, and the header jitters under the thumb.
   - What would satisfy me: the settle is cancelled by the first `onPreScroll` from `NestedScrollSource.UserInput` (keep the Job, or run it through a `MutatorMutex` at `UserInput` priority). Add a test that touches mid-settle and asserts the offset follows the finger.

3. **P2: on History, More and Plan the whole morph happens in about 12 dp of finger travel.**
   - Arithmetic from `ScreenHeader.kt:131-151`:
     - full header = 4 + (16 + 40 + 4) + 4 = 68 dp;
     - compact = 4 + 48 + 4 = 56 dp;
     - so the range is 12 dp.
   - What's wrong: a 32→22 sp change, about 30 % smaller, over 12 dp reads as a twitch rather than a scroll-linked motion (`MORPH-light-b-history` and `MORPH-light-d-more` sheets: fully compact by drag24). It also buys only 12 dp. Most of the compact bar is air: a 27.5 dp scaled title centred in a 48 dp row with no Back or actions.
   - What would satisfy me: on top-level screens with no nav or actions, size the compact row to the title, not 48 dp. That makes the range about 25-30 dp on tabs without a subtitle and about 45 dp on Today. Equally acceptable: drop the scale entirely on screens whose range is under ~24 dp. Distance and visual change should match.

4. **P2: pushed screens at 200 % text barely give anything back, the case the owner called worst.**
   - Evidence: `MORPH-large-e-bodyweight-3-deep` keeps a full Back + title row plus a separate "Log" actions row (about 125 dp) while deep in the list.
   - Why: `compactH` keeps `actionsRowH` (`ScreenHeader.kt:151`).
   - What would satisfy me: at large text the `ACTIONS_ROW` folds away with the subtitle (or moves into the compact row as an icon button) and returns at the top. Show this in a `large-e` frame where the compact header is ≤ 1 row.

5. **P3: a saved collapse can outlive the body's scroll.**
   - Where: `Components.kt` diff, `rememberSaveable` offset.
   - What's wrong: the offset is saved, but the body's scroll position may be lost or its content may shrink (Today after finishing a workout). The screen can then show a compact header over a body sitting at its top, a state MORPH otherwise never shows. It only opens with an extra pull.
   - What would satisfy me: when the body is at its top and can't scroll further, the header is full. Either derive this on composition or clamp when the body's `canScrollBackward` is false.

6. **P3: the subtitle crowds the title at large text mid-morph.**
   - Evidence: `MORPH-large-a-today-1-drag24`. The 40 %-alpha subtitle rides up flush under the still-large title, a second line of half-ink text touching the first.
   - What would satisfy me: fade it out by f=0.35 instead of 0.5 (`ScreenHeader.kt:193`). Exiting things should leave faster than the thing that stays moves.

7. **P3: scrolling a short page moves chrome for nothing.**
   - What's wrong: `onPreScroll` collapses the header even when the body has nothing to scroll, so a page that fits still has a header that shrinks.
   - What would satisfy me: only consume when the body can scroll forward.

Animator duration scale 0: `animate()` (and `settle()` if adopted) completes in its first frame under `MotionDurationScale`. The finger-driven part is instant by design, so the final states are correct. This is fine as written, but confirm it once on device with Remove animations after finding 1.

Jank: the header changes its *measured height* every scroll frame, which re-measures the body (LazyColumn, charts) every frame. I can't judge this from static frames. Before shipping, take a 60 fps contact sheet of a fling on History → Workouts and on Bodyweight (chart) and look for uneven steps.

## 3. Hybrid

Use MORPH on top-level screens. On pushed screens, also MORPH, extended per finding 4: subtitle and the large-text actions row fold away, while Back and the title stay.

I'd reject RETURN anywhere. Hiding Back and Log on a pushed screen is a bigger cost than the 56 dp it saves, and quick-return bobbing reads as motion without a state change.

What is already good:
- the finger is followed 1:1 with no lag;
- the header only expands once the body is back at its top, so reverse scrolling is calm;
- the tab row on History sticks and the list scrolls under it (`MORPH-light-b-history`);
- the existing top-edge fade separates the compact bar from the content without needing a divider (`MORPH-light-a-today-3-deep`);
- the workout screen is correctly excluded;
- the scale uses a layer, so the text isn't re-laid out per frame.

## 4. Verdict

MORPH as-is: NOT SATISFIED. Findings 1-4 are P2.

VERDICT: NOT SATISFIED

---

# Round 2

Evidence:
- Code: the new `ScreenHeader.kt`, the diffs of `Components.kt` and DESIGN.md (Headers bullet), and `HeaderCollapseTest.kt`.
- Frames opened: `sheets2/header-{light-a-today, large-a-today, light-b-history, large-e-bodyweight}`, `hdr2/header-large-e-bodyweight-1-drag24` and `hdr2/header-light-a-today-1-drag96`.
- Bytecode of the project's Compose Foundation 1.10.6, read with `javap`: `ScrollingLogic$onScrollStopped$performFling$1`, `ScrollableNode`, `DefaultFlingBehavior`.

## Earlier findings re-checked

1. **Settle (P2): partly fixed.**
   - Fixed: it now uses `Motion.settle()`, picks the target by direction above 600 px/s and by the nearer side below it, there is no inline tween, and DESIGN.md names the rule.
   - Still wrong: the spring's starting velocity. See R2-1.
2. **Touch during the settle (P2): fixed.**
   - `ScrollableNode` launches `onDragStopped` in its own coroutine. Post-fling therefore runs alongside a new drag, which is exactly the case where cancelling on `UserInput` in `onPreScroll` is needed, and the code now does it (`ScreenHeader.kt`, first line of `onPreScroll`).
   - No test covers it (see R2-2).
3. **12 dp range on tabs (P2): fixed.**
   - A top-level compact row with no controls now hugs the scaled title. History's range is about 24 dp and Today's about 48 dp.
   - `sheets2/header-light-b-history`: the title shrinks progressively across drag24, 56 and 96 instead of snapping. `hdr2/header-light-a-today-1-drag96`: the compact "Tuesday" sits tight over the top-edge fade, with no empty band.
4. **200 % pushed screens (P2): fixed.**
   - `sheets2/header-large-e-bodyweight` frames 3-7: Log sits in the Back + title row, and the compact header is one row.
   - `hdr2/header-large-e-bodyweight-1-drag24`: mid-join, Log only travels vertically and never crosses the title.
   - A test asserts Log is in the title's row and keeps a 48 dp target.
5. **Saved collapse outliving the scroll (P3): mostly fixed.**
   - Fixed: the offset is no longer saved, it clamps when the range shrinks, and a recreated screen starts full.
   - Still open, accepted as P3: History's pages share one collapse, so swiping from a deep Workouts list to Exercises (scrolled to its top) leaves a compact header over a page at its top. One pull opens it.
6. **Subtitle crowding (P3): fixed.**
   - The subtitle is gone by a third of the collapse and is not placed after that. `sheets2/header-large-a-today` frame 2 shows no half-ink second line.
7. **Pages that fit still collapse (P3): not fixed, and the team chose not to.** I accept that: it does no harm and a pull restores the header.

**Remove animations (scale 0): confirmed from the library code.**
- In 1.10.6, `DefaultScrollMotionDurationScale` (always 1) wraps only the decay inside `DefaultFlingBehavior`.
- `dispatchPostFling` runs outside it, in the node's own coroutine context, so the settle spring follows the device's scale and finishes in its first frame at 0.
- A test asserting this would still be welcome, but it doesn't block.

**Jank (open, not a finding):** the header still changes its measured height on every scroll frame. Before merge, take a 60 fps contact sheet of a fling on History → Workouts and on Bodyweight (chart).

## New findings

**R2-1 (P2): the settle spring starts at the release velocity, even after the fling has already stopped.**
- Where: `ScreenHeader.kt`, `onPostFling`: `val velocity = -consumed.y - available.y`, which is then passed to `AnimationState(offset, velocity)`.
- Why it happens: in Foundation 1.10.6 (`performFling$1`), `consumed` is the release velocity minus what is left, and `available` is what is left. Their sum is therefore the velocity at the moment of release, not at the end of the fling.
- When it happens: a gentle flick of roughly 300-1000 px/s travels less than the header's range (126 px on Today). The fling collapses the header partly through `onPreScroll`, decays to rest mid-header, then the spring starts again at the full release speed.
- What the user sees: the header coasts, stops, then jerks forward again. That breaks continuity on exactly the small flick a one-handed lifter makes most.
- What would satisfy me:
  - choose the direction from the release velocity (`consumed + available`), as now;
  - seed the spring only with what's left of the fling (`-available.y`), which is about 0 after a natural decay and the real speed when the body hits an edge;
  - a test: a gentle flick that ends inside the range settles without the offset speeding up again after it stopped.

**R2-2 (P3): the settle has no test.**
- Add a Robolectric test for each of the three settle rules:
  - a slow lift past halfway completes the collapse;
  - a slow lift before halfway reopens the header;
  - a touch mid-settle hands control straight back to the finger, so the offset follows the drag frame by frame.
- `mainClock.autoAdvance = false` makes all three deterministic.

## What's good in round 2

- One clear style is left, written down in DESIGN.md's Headers bullet.
- The compact row hugs the title, so the morph's distance now matches its visual change.
- Actions joining the title row at large text fixes the case the owner felt most.
- Fully faded parts are not placed, so a screen reader never reads them.
- Cancelling the settle on a new touch is correct.
- Scaling the title by the px ratio holds up under non-linear font scaling.

VERDICT: NOT SATISFIED (one P2 remains: R2-1. Seeding the spring with only the leftover velocity, plus its test, would satisfy me.)

---

# Round 3

**Evidence:**
- **Code:** `ScreenHeader.kt`, `onPostFling`.
- **Tests:** `HeaderSettleTest` in `HeaderCollapseTest.kt`. I ran `./gradlew :app:testDebugUnitTest --tests HeaderSettleTest --tests HeaderCollapseTest` myself and all 8 passed (4 + 4, 0 failures).

## Earlier findings re-checked

- **R2-1 (P2), spring seeded with the release velocity: fixed.**
  - The release speed (`-consumed.y - available.y`) still chooses the side, and the spring now starts from `-available.y`.
  - In 1.10.6 that is the velocity left after the fling, so it is about 0 after a natural decay and real only when the body hit an edge. That is the right seed.
  - `aGentleFlickThatStoppedPartWayGoesOnFromRest` reproduces the exact case: −800 px/s, nothing left, stopped at 40 %. It asserts:
    - the header reaches the side it was heading for;
    - it never steps backward;
    - its first step is under 8 px.
  - The coordinator reports the test fails with the old seed (first step about 12.7 px), so it guards the regression.
- **R2-2 (P3), no settle test: fixed.** There are tests for:
  - a slow lift past halfway;
  - a slow lift before halfway;
  - the gentle flick;
  - a touch mid-settle, after which the offset never moves again.
  
  One minor weakness: `aTouchDuringTheSettleTakesOver` doesn't assert the settle had already moved the header before the touch (`held < 100f` would also pass if it never started). That's not blocking.
- **Round-1 items 1–6:** still fixed. Nothing changed since round 2 outside `onPostFling`.
- **Open but accepted:**
  - **Item 7 (P3):** pages that fit still collapse.
  - **Item 5 remainder (P3):** History's pages share one collapse.
- **Remove animations:** unchanged and still correct. The settle runs outside Foundation's fixed-scale fling context, so `Motion.settle()` finishes in its first frame at scale 0.

## Still open before merge, not a finding

- **Jank:** no 60 fps device contact sheet of a fling on History → Workouts and Bodyweight yet. The header re-measures the body on every scroll frame, which is the one risk static frames and Robolectric can't rule out.
- The emulator holds an install that must not be replaced without asking, so deferring is correct. Record it as a named pre-merge check for the owner, not a silent gap.

No new findings.

VERDICT: SATISFIED

---

# Round G: a see-through header

**Evidence:**
- **Code:** `ScreenHeader.kt` (from `HeaderGlass` to the end), the `Components.kt` diff (`ScreenScaffold` Layout, `ScrollBody`, `fadingEdges`), the `HistoryScreens.kt` diff (`LazyBody`), and `ScreenshotHarness.headerTour`.
- **Images:** `cmp-glass-light.png` and `cmp-glassnf-dark.png`, plus my own top-of-screen crops of every variant: `scratchpad/gcrop-light-a-today-1-drag96.png`, `gcrop-light-c-detail-3-deep.png`, `gcrop-dark-e-bodyweight-3-deep.png`, `gx-c.png`, `gx-e.png` and `gx-h.png` (G100 / G65NF / G100 History deep).

## G1. Ranking

| Rank | Variant | Score | Reason |
|---|---|---|---|
| 1 | G100 (opaque) | 6/10 | No seam and nothing moves behind the title. It still carries the reach-under bugs (G2-1, G2-2), because those are in the layout, not the alpha. |
| 2 | G92 / G92NF | 5/10 | Glass barely shows, yet text passing under the edge is still sliced (G2-3). It pays the cost and gives little to see. |
| 3 | GRAD | 4/10 | 0.70 at the bottom edge makes the seam *more* visible than G92 at the exact place where rows cross it. Gradient in the wrong direction (see G2-3). |
| 4 | G80 / G80NF | 4/10 | Clear seam: the top half of a word is mid-grey, the bottom half full ink. |
| 5 | G65 / G65NF | 2/10 | Moving rows read as ghost text behind the title. That's noise in exactly the zone a lifter glances at, and the seam is strongest here. |

The fade and no-fade versions are indistinguishable in every pair I compared (`gcrop-light-a-today-1-drag96`, `gx-c`). The kept top fade now runs over 0–32 dp of a body that starts at the screen's top, which is entirely behind a header at least 44 dp tall. So it fades content the glass already covers, and does nothing at the header's edge. That comparison doesn't test what the brief intended (see G2-4).

My pick is a variant nobody rendered: opaque or near-opaque glass, about 0.92, with a feathered bottom edge. See G2-3. Among what was rendered, G100.

## G2. Findings (in the shared layout, so they apply to every variant)

**G2-1 (P1): History's list paints over its own tab row and takes its touches.**
- Evidence: `gx-h.png`. In `G100-` and `G65NF-header-dark-b-history-3-deep`, the "Workouts / Exercises / Body" pills are cut in half by list rows ("13 Baseline · 9 working sets") drawn over them. Round 2 (`sheets2/header-light-b-history`) shows the tab row intact, so this is new. It happens in G100 too.
- Cause: `reachUnderHeader` decides "first under the header" with `onPlaced { positionInParent().y < 1f }`. A History page's `LazyBody` is at y=0 *of its pager page*, so it reaches up by the header's height, under the tab row. Because it is placed after the tab row, it draws above it and hit-tests first.
- Same evidence: the `e-bodyweight` frames of every glass variant show History → Workouts, not Bodyweight (`gx-e.png` left). The tour didn't get through the Body tab. The brief's claim that dense text scrolls under Back + title on Bodyweight is therefore unproven: no Bodyweight frame exists this round.
- What would satisfy me:
  - only the body that is first *in the scaffold's body column* reaches under, decided by the scaffold (explicit opt-in, or position relative to the scaffold's body, not to any parent);
  - History, the workout, the library and the routine editor are provably excluded;
  - a test asserting that History's tab-row bounds are not overlapped by any list node, and that tapping the Body tab by touch (not `performClick`) switches pages;
  - a regenerated e-bodyweight set that actually shows Bodyweight.

**G2-2 (P2): taps and accessibility under the glass.**
- `BlocksTouchesBeneath` correctly keeps taps on the header off the rows beneath it. Back and actions still work, since children see Main pass first.
- Problem: a row scrolled under the glass is now visible, inside the list's viewport, and present in the accessibility tree.
  - A finger tapping what it can see does nothing, with no feedback.
  - TalkBack can focus a row hidden behind the title. Bring-into-view considers it already visible, so the row is never scrolled out from under.
  - DESIGN.md's Accessibility paragraph exists to prevent exactly this ("a row scrolled partly under the top edge never takes the lower part of a chip…"). The diff edits that sentence to "beneath the header" but the guarantee it described no longer holds.
- What would satisfy me:
  - bring-into-view and accessibility focus treat the header's height as an obscured inset, so focusing a row scrolls it fully below the glass;
  - the DESIGN.md sentence says what is actually true now;
  - a test for the focus case.

**G2-3 (P2): the glass's bottom edge slices moving text in half.**
- Evidence: `gcrop-light-a-today-1-drag96`. Today's 32 sp "Push" crossing the edge is pale above the line and full ink below it in every translucent variant, a hard horizontal cut through glyphs. Strength: G65 > G80 ≈ GRAD > G92. G100 has a clean occlusion instead.
- Why it matters for motion: the cut follows the header's edge as it collapses, and every row is sliced as it passes. It reads as a rendering fault, not as depth. A step in alpha is a step no matter its size, so lowering α only trades seam strength for ghost-text noise.
- What would satisfy me: the glass fades out at its bottom. Over the last ~12–16 dp the page colour ramps from α to 0, extending below the header's layout bottom if needed so Back and the title keep full α behind them. That is the old top fade relocated to the header's edge, which also restores DESIGN's "pages fade at an edge only when there is content beyond it". GRAD as built goes the wrong way: lighter toward the edge.

**G2-4 (P3): the kept top fade is dead code in effect.** In `fadingEdges` with `under.px > 0 && fadeUnder`, the fade covers the body's 0–32 dp, all behind the header. Either delete it (the feathered glass in G2-3 *is* the edge) or move it to start at `under.px`. Don't keep a `fadeUnder` flag whose two settings render identically.

**G2-5 (P3): legibility, computed on composited colours.**
- Content only goes under the glass once the header is fully collapsed. The header collapses before the body scrolls, and the subtitle is gone by a third of the collapse, so the only things over moving text are the title, Back and the actions (their own filled containers).
- Worst case is a glyph of body ink directly behind the title (sRGB mix, relative luminance L):

| Theme | α | Composited background | Title contrast | Grey text (onSurfaceVariant) |
|---|---|---|---|---|
| Light | 0.65 | ≈ #A8A8A3, L≈0.39 | ≈ 7:1 | ≈ 2.9:1 (fail) |
| Light | 0.80 | — | — | ≈ 4.2:1 (fail) |
| Light | 0.92 | — | — | ≈ 5.4:1 |
| Dark | 0.65 | — | ≈ 5:1 | — |

- No grey text sits in the collapsed bar today, so this passes for now. But anything grey that is ever added there (a collapsed subtitle, a count) needs α ≥ 0.9.
- What would satisfy me: fix α ≥ 0.9 as a rule, and add a test computing the worst-case composited contrast for title and icons in both themes.

**G2-6 (open, not a finding): jank risk rises.** The body is now re-measured *and* re-padded every scroll frame (`reachUnderHeader` changes its constraints, and `padUnderHeader` / `UnderHeaderPadding` change with the header's height), on top of the round-2 re-measure. The 60 fps device sheet is now more important before merge, especially History (LazyColumn) and Bodyweight (chart).

## What's good

- At rest nothing moves. Reach-under and content padding cancel exactly, and the G100 frames match round 2 except History.
- Placement order gives the right z-order between header and body.
- Consuming touches on the header's empty space is the right call.
- The glass is the page colour, so the resting look is unchanged.
- A real blur wasn't faked with an API-31-only RenderEffect.

## Verdict

My pick as built (G100, or G92 if the owner insists): NOT SATISFIED.
- G2-1 is a P1 in every variant.
- G2-2 and G2-3 are P2.

I'd be satisfied with a scaffold-decided reach-under (History tab row intact, tested), a feathered glass edge at α ≈ 0.92 replacing the dead fade, focus and bring-into-view that respect the glass inset, and fresh frames including a real Bodyweight set.

VERDICT: NOT SATISFIED

---

# Round G2

**Evidence:**
- **Code:** `ScreenHeader.kt` (`HEADER_GLASS_*`, `HeaderInset.column`, `UnderHeader.placed`, `UnderHeaderBringIntoView`, `headerGlass`, `CoversThePageBeneath`) and the `Components.kt` scaffold.
- **Tests:** I ran them myself: `HeaderCollapseTest` 6/6 and `HeaderSettleTest` 4/4 pass.
- **Frames:** the deep and drag96 frames of all 6 screens × 3 modes from `glass2/`, joined into strips: `scratchpad/g2-light.png`, `g2-dark.png`, `g2-large.png`, `g2-crops.png`.
- **Pixel measurements:** taken with `magick` on `glass2/header-light-d-more-3-deep` and `header-light-e-bodyweight-3-deep`.

## Round G findings re-checked

- **G2-1 (P1), History's list over its tabs: fixed.**
  - "First" is now measured against the scaffold's body column.
  - In `g2-light`, `g2-dark` and `g2-large` (History deep), the tab row is intact, with the list starting below it.
  - The e-bodyweight frames are real Bodyweight screens now.
  - `aListBelowFixedControlsNeverReachesOverThem` passes (the coordinator reports it failed on the old code).
- **G2-2 (P2), taps and accessibility under the glass: fixed in code, needs a device check.**
  - The header consumes taps on its empty space (tested).
  - `UnderHeaderBringIntoView` counts the strip under the header as hidden, so a focused field or a requested row scrolls out from under the glass.
  - DESIGN.md is updated.
  - One residual I can't settle from Robolectric: TalkBack's own swipe-to-next only scrolls when Compose reports a node *not* visible to the user. A row behind the glass is unclipped, so it may still get a focus ring behind the title without a bring-into-view request.
  - I'd accept this as an open device check alongside the 60 fps one. It belongs to the a11y critic's verdict, not mine.
- **G2-3 (P2), the hard seam: the seam is fixed, but the fix creates G3-1 below.**
  - No variant shows a hard cut any more.
  - Behind the solid part of the glass, page text is a faint ghost. On More, the darkest ghost pixel measures 227 against a page of about 240 (`header-light-d-more-3-deep`, rows y35–80). That's about 6 % of the ink, consistent with α 0.95.
- **G2-4 (P3), the dead fade: fixed.** The top fade is off while the body sits under the glass, and the flag is gone.
- **G2-5 (P3), contrast: fixed.**
  - At α 0.95 the title sits on at least 0.95 page colour, so its contrast stays close to the page's own.
  - DESIGN.md now states that the subtitle is never on the glass.

## New finding

**G3-1 (P2): body text emerges right under the title and reads as the header's subtitle.**
- **Evidence:** `g2-crops.png` row 2, i.e. `glass2/header-light-e-bodyweight-3-deep`.
  - The title's glyphs end at about y130 px.
  - A row's "Wed 14 Oct · 7:30 AM" starts showing at y156 and reaches full ink (73) by y180.
  - Under the compact "Bodyweight" it looks exactly like a grey subtitle: the same bodyMedium grey a pushed screen's subtitle uses.
  - The same thing happens at 200 % in dark, where "press" sits directly under "Push" (`header-large-c-detail-3-deep`, `g2-crops` row 4).
- **Why:**
  - The old 32 dp top fade, which used to separate the bar from the page, is now switched off under the glass.
  - The replacement 12 dp feather is drawn *inside* the header's bounds.
  - So the calm band under the title has shrunk from about 38 dp in round 2 (opaque bar plus 32 dp fade below it) to about 19 dp. Text is half-visible only ~10 dp under the title.
- **For a glancing lifter:** the header now appears to carry whatever row happens to sit under it, so the eye can't tell the header's own information from the page's.
- **What would satisfy me:**
  - keep the glass at full α over the header's whole box;
  - draw the feather *below* the header's bottom edge, over the top of the body;
  - make it about as long as the old fade (24–32 dp, or the `fadingEdges` edge value so large text keeps its 48 dp).
  - The result: text crosses a soft edge well below the title and fades out *before* it reaches the bar.
  - At rest there's nothing under it, because the body's own top padding (12 dp in `ScrollBody`, 8 dp in `LazyBody`) sits there. Check the first row isn't dimmed at rest: if the feather is longer than that padding, it should only draw while `canScrollBackward`, as the old fade did.
  - Show it in `e-bodyweight-3-deep` and `c-detail-3-deep` at light and large-dark.

## Still open, not findings

- **60 fps device contact sheet** of History and Bodyweight flings. The body is now re-measured *and* re-padded every scroll frame.
- **TalkBack swipe focus** on a row behind the glass (see G2-2).
- **Owner expectation:** at α 0.95 the transparency is subtle (about 6 % ghost). That's right for legibility, but the owner should see a frame and confirm that "a bit transparent" means this.

## What's good in this round

- Reach-under is scoped to the scaffold, and a touch test proves the tabs work.
- The glass is consistently α 0.95 everywhere.
- No seam.
- Header taps are covered by a test.
- Bring-into-view respects the glass.
- DESIGN.md's accessibility paragraph and Headers bullet match the code.
- At rest, every screen is unchanged from round 2.

VERDICT: NOT SATISFIED (one P2 remains: G3-1, the feather drawn inside the header instead of below it)

---

# Round G3

**Evidence:**
- **Code:** `ScreenHeader.kt` (`headerGlass`, `HEADER_GLASS_FEATHER` 24 dp / `_LARGE` 40 dp, `inset.scrolled`), `Components.kt:254,916`, `HistoryScreens.kt:929`.
- **Tests:** I ran them myself: `HeaderCollapseTest` 8/8 and `HeaderSettleTest` 4/4 pass.
- **Frames:** deep-scroll frames for light and large-dark Bodyweight and workout detail, light More, light Today drag24, and the at-rest frames for light Today, light History and large-dark workout detail. They're collected in `scratchpad/g3-crops.png` and `scratchpad/g3-rest.png`.
- **Pixel readings:** taken from `glass3/header-large-c-detail-3-deep.png`, column x=400.

## G3-1 (P2), body text reading as the header's subtitle: fixed

- **Glass is solid over the whole header box.**
  - Large-dark workout detail: page 15 against ghost glyphs at most 26 behind the title (y130), which is about 5 % of the ink, as α 0.95 predicts.
  - Light Bodyweight: "82.57 kg / Wed 14 Oct" behind the title is a faint ghost, no longer a crisp grey line under "Bodyweight" (`g3-crops` row 1).
- **The feather sits below the header's edge,** so text dissolves on its way in:
  - "press" under the large "Push" reads 109 at y230, mid-feather;
  - "External load" reads 207 at y330, below the feather, against full ink of about 235.
  
  The band under the title is calm again, and nothing reads as a subtitle.
- **At rest there is no feather.** Today, History and the large workout detail are identical to round 2 (`g3-rest.png`), and the first row isn't dimmed.
- **History (tabs) has no feather.** No body reaches under its header, so `scrolled` stays false and it looks as before.

## Earlier Round G items

- **G2-1:** still fixed. The tab row is intact.
- **G2-2:** fixed. Bring-into-view is now scoped so it doesn't leak into body contents or dialogs (tested).
- **G2-3, G2-4, G2-5:** still fixed.

## Remaining (P3, polish; not blocking)

**G4-1 (P3): the feather pops in fully on the first scrolled pixel.**
- `inset.scrolled()` is a boolean. The moment `canScrollBackward` turns true, the full 24/40 dp gradient appears in one frame over the top of the first card.
- This is the same behaviour the old `fadingEdges` top fade always had, so it's consistent with the system, not a regression.
- A nicer version scales the feather's length (or alpha) by `min(scrolled px, feather)`, so it grows in with the finger. If done, do it in `fadingEdges` too, so both edges behave the same.

## Still open before merge (named for the owner)

- A 60 fps device contact sheet of History and Bodyweight flings. The body is re-measured and re-padded every frame.
- A device check of TalkBack swipe focus on a row behind the glass.
- The owner confirming that "a bit transparent" means α 0.95.

## What's good

The glass now behaves as one system:
- solid over the bar;
- a soft edge below it only when there's content beyond;
- no seam;
- no false subtitle;
- unchanged at rest;
- scoped to the body that's actually under it.

DESIGN.md, the code and the frames agree. The settle and collapse work from rounds 1–3 is intact.

VERDICT: SATISFIED
