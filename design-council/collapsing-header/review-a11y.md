# Accessibility / platform review: collapsing screen header

Read: brief, AGENTS.md accessibility bar, DESIGN.md (Motion, Navigation, Accessibility), `git diff` (Components.kt, WorkoutScreen.kt), ScreenHeader.kt. Opened the large (200%) sheets for MORPH a/b/c/e, SLIDE a/e, RETURN e; light sheets MORPH c/d; full frames MORPH-large-a-today-1-drag24 and -3-deep. Checked Compose foundation 1.10.6 bytecode (the BOM's version, from the Gradle cache) for how accessibility scrolls and flings reach nested scroll.

## 1. Ranking

| # | Style | Score | Reason |
|---|---|---|---|
| 1 | MORPH | 8/10 | The heading, Back and actions are never removed or clipped. The compact row is at least 48 dp. At 200% the deep frame keeps a readable ~64 dp bar (MORPH-large-a-today-3-deep). |
| 2 | SLIDE | 6/10 | Once collapsed, the large title (the only `heading()`) is placed entirely above the clip, so it leaves the semantics tree. The visible small title is `clearAndSetSemantics {}`, so the collapsed screen has **no heading** for TalkBack heading navigation. Two copies of the title are also drawn mid-scroll. |
| 3 | FIXED | 5/10 | Fully accessible, but it doesn't fix the reported problem. The problem is worst at 200% and in landscape, which is exactly where the large-text frames show the most header. |
| 4 | RETURN | 3/10 | At height 0, Back, the title heading and actions (for example "Log") are clipped out of the a11y tree. Held frames have a partly clipped Back button, so a target under 48 dp (RETURN-large-e-bodyweight-1-drag24). Programmatic or `scrollToIndex` scrolls back to the top bypass nested scroll, so the header can stay gone at the top of the page. Frame 4 shows "Log" back with no title above it. |

## 2. MORPH: must change before it ships

1. **The hidden subtitle stays in the semantics tree, on top of the compact row.** (ScreenHeader.kt:192-194) At f=1 the subtitle is placed at `padV+cRowH+actionsRowH-subtitleH`, inside the header's bounds, at alpha 0. Because it is placed after nav and title, it is topmost for accessibility hit-testing. At 200% a two-line subtitle covers Back and the title (pushed detail, MORPH-large-c-detail). Explore-by-touch on Back can then focus an invisible "13 Oct 2026", and linear TalkBack reads a line that isn't on screen.
   - Fix: don't place the subtitle once its alpha reaches 0 (f ≥ 0.5). An unplaced node is dropped from both drawing and semantics.
   - Also place it *before* the title so it paints under it. In MORPH-large-a-today-1-drag24 the ghost "20 Oct · Baseline" paints over the bottom of "Tuesday", so it doesn't really "fold under" it.
2. **A saved offset larger than the current range returns consumption with the wrong sign.** (ScreenHeader.kt:47-49, 59-62, 75-78) The getter returns the raw `saved.floatValue`, and only the setter clamps. After rotation, a font-scale change, or the subtitle's own `AnimatedVisibility` collapsing under the keyboard on `compactWhenKeyboard` screens, `range` shrinks below the saved offset.
   - On the next upward drag, `before > range`, so `offset = range` and the code returns `-(range - before)` > 0 while `dy < 0`. That reports a consumption in the opposite direction of the scroll, and the body jumps.
   - Fix: clamp in the getter (`saved.floatValue.coerceIn(0f, range)`), or clamp `saved` whenever `range` changes.
3. **`range` is snapshot state that is written inside measure** (ScreenHeader.kt:51, 154). Layout reads it (`state.range != range`) and writes it in the same pass, which invalidates its own measure. Only the nested-scroll callbacks need it, so make it a plain `Float` field.
4. **The settle can't be interrupted** (ScreenHeader.kt:83-90). `dispatchPostFling` runs after the fling's `scroll {}` mutex has been released, inside the job launched by `onDragStopped`. A finger that lands within 150 ms of lifting writes `offset` while `animate` is still writing it. Verdict: PLAUSIBLE; I didn't run it.
   - Fix: keep the settle `Job` and cancel it in `onPreScroll` / `onPostScroll` when `source == UserInput`, or run the settle under a `MutatorMutex`.
5. **Prove "instant at animator scale 0" with a test.** In 1.10.6, `DefaultScrollMotionDurationScale` (fixed at 1) is applied only by `DefaultFlingBehavior` (checked in the bytecode). The `animate()` in `onPostFling` therefore runs in the node's scope with the window's `MotionDurationScale`, so it should be instant. AGENTS.md requires that, and nothing currently asserts it.
6. **Tests that should exist before merge** (Robolectric):
   - at fontScale 2, collapsed: exactly one `heading()` node, Back ≥ 48 dp and in the tree, and no `screen-subtitle` node;
   - restoring a saved offset larger than the new range doesn't move the body on the next drag;
   - a TalkBack `ScrollForward` action collapses the header. It will: in 1.10.6, the scrollable's semantics `scrollBy` dispatches through `NestedScrollScope.scrollBy(…, UserInput)`, so this is a regression guard.
7. **Code quality** (Components.kt):
   - remove the temporary global `headerStyle` var and the FIXED/SLIDE/RETURN branches once a style is chosen;
   - replace the fully qualified `androidx.compose.runtime.saveable.rememberSaveable`, `mutableFloatStateOf`, `TextOverflow` and `AnimatedVisibility` with imports;
   - fix the unsorted imports added at the end of the block;
   - `remember(style)` keyed on a non-state global is fragile;
   - `slot(HeaderSlot.TITLE)!!` should be an explicit `requireNotNull` with a message.

Should fix, but not blocking:

8. **Pushed screens at 200% with a text action never compact.** The `ACTIONS_ROW` ("Log") stays as a second row in the collapsed state (MORPH-large-e-bodyweight-3-deep), so the 200% case keeps two rows. When the action fits beside a single-line, ellipsized title, move it into the compact row. Otherwise keep it: an action that stays reachable beats one that hides.
9. **History's pager shares one collapse across three pages** that each have their own scroll position. Switching to a page that is at its top leaves the header collapsed until the user pulls. `PagerDefaults`' nested-scroll connection consumes only horizontal deltas, so the vertical chain itself is correct. Consider expanding when `settledPage` changes and the new page's list is at its top.
10. **Bodies that fit, or barely overflow, still collapse.** `onPreScroll` consumes upward deltas whether or not the body can scroll. A drag on a page that fits hides the subtitle and leaves empty space at the bottom. This is harmless, and pulling down restores it through `onPostScroll`. Gating the collapse on `canScrollForward` would be cleaner.
11. **Bypassing scrolls.** `animateScrollToItem` (HistoryScreens.kt:413, RoutinesUi.kt:937), `bringIntoView`, and the lazy `scrollToIndex` semantics don't touch the header. Under MORPH that is benign, because the header just stays as it was, and it is a key reason MORPH beats RETURN. Document it in the KDoc.

## 3. Hybrid

Use **MORPH everywhere** (pushed screens already reduce to "fold the subtitle away"). Don't pair it with RETURN on pushed screens: hiding Back and the actions is the worst outcome for TalkBack and for switch users, and pushed screens are the ones carrying actions like "Log". The only variation worth having is finding 8, which merges a fitting action into the compact row at large text.

## 4. Verdict

MORPH as-is: **NOT SATISFIED**. Findings 1 and 2 must be fixed, and findings 3-6 should land with them. Once they are in, I expect to be satisfied.

---

# Round 2

Re-read: the new ScreenHeader.kt, `git diff` (Components.kt, DESIGN.md), and HeaderCollapseTest.kt. Opened sheets2 `header-large-{a-today,c-detail,e-bodyweight}` and the full frame `hdr2/header-large-e-bodyweight-3-deep`.

## Status of the round 1 findings

| # | Status | Notes |
|---|---|---|
| 1 Hidden subtitle in the tree | **Fixed** | The subtitle isn't placed once its alpha reaches 0 (from f ≥ 1/3), so it is dropped from both drawing and semantics. It is placed first, so it paints and hit-tests beneath Back and the title. The test asserts it isn't placed once collapsed. |
| 2 Stale offset larger than the range | **Fixed** | The getter clamps (`raw.coerceIn(0f, range)`) and so does the range setter. Using a plain `remember` is fine: the manifest's `configChanges` covers orientation, so rotation keeps it. A recreation (font scale, uiMode) or returning to a pushed screen opens the header with the body restored mid-page, which is harmless because the next upward drag collapses it. |
| 3 Range as state | **Fixed** | `range` is a plain field. The setter still writes `raw` (state) during measure, but only when the range shrinks, so it costs at most one extra measure. Acceptable. |
| 4 Settle can't be interrupted | **Fixed** | `settling?.cancel()` runs on `UserInput` pre-scroll. Since `onPreScroll` always runs before `onPostScroll`, both a drag down at the top and a TalkBack `ScrollBy` (also `UserInput`) cancel it. The cancelled child inside `coroutineScope` doesn't fail the fling. `Motion.settle()` is critically damped (`dampingRatio = 1f`), so it doesn't overshoot, and the clamp would cover it if it did. |
| 5 Instant at animator scale 0 | Not asserted | Accepted as non-blocking. The settle runs under the window's `MotionDurationScale`, and a spring finishes on its first frame at scale 0. A one-line test running the settle with `MotionDurationScale(0f)` in context would close it. |
| 6 Tests | **Done** | One heading, no placed subtitle, Back's touch bounds ≥ 48 dp, the action kept, Log joining the title row at 200% with a 48 dp target, and `ScrollBy` collapsing the header. Together they cover the TalkBack and large-text bar. |
| 7 Code quality | **Done** | The global and the losing styles are gone, and `requireNotNull` has a message. The reason `AnimatedVisibility` stays fully qualified is valid. |
| 8 Large-text actions | **Fixed** | `header-large-e-bodyweight-3-deep`: Back, "Bodyweight" and Log sit in one row of about 64 dp, and Log is a full pill. Traversal order is still Back, then title, then Log. |
| 9 Pager shares one collapse | Not done | Accepted: the header reopens with a pull at the top, and nothing is lost or unreachable. |
| 10 Pages that fit still collapse | Not done | Accepted: harmless, and a pull restores it. |
| 11 Code scrolls | **Documented** | Now in the KDoc. |

## New notes (none blocking)

- The compact title's scale is computed in px at the current font scale. That correctly follows Android 14's nonlinear font scaling, where the 32 sp title grows less than the 22 sp one. Good.
- Pre-existing and outside this change (seen in `header-large-e-bodyweight-3-deep`): Bodyweight's week labels break mid-word at 200% ("Mon 19 Oc / t –", "12 O / ct"). That breaks the AGENTS.md rule that values never wrap apart. Worth a separate issue.

VERDICT: SATISFIED

---

# Round G: see-through header

Read: brief-glass.md, `git diff` (Components.kt ScreenScaffold/ScrollBody/fadingEdges, HistoryScreens.kt LazyBody), and ScreenHeader.kt (HeaderInset, UnderHeader, reachUnderHeader, padUnderHeader, UnderHeaderPadding, headerGlass, BlocksTouchesBeneath). Opened `cmp-glass-dark`, `cmp-glassnf-light`, gsheets `G80NF-large-c-detail`, `G92-large-c-detail`, `GRAD-light-c-detail`, `G92-large-e-bodyweight`, `G65NF-large-e-bodyweight`, `G92-light-b-history`, and the full frame `glass/G100-header-large-e-bodyweight-0`.

## Contrast, measured (WCAG 2.x, sRGB alpha compositing, colours from Theme.kt)

Header colour = α·surface + (1−α)·X. The worst-case X is content the same colour as the header text: onSurface ink, the amber fills, or white cards. Ratios are for title/Back (onSurface) and subtitle (onSurfaceVariant):

| α | light title | light subtitle | dark title | dark subtitle | "ghost" contrast of ink beneath (light / dark) |
|---|---|---|---|---|---|
| 1.00 | 15.3 | 7.9 | 15.6 | 11.3 | 1.00 / 1.00 |
| 0.98 (GRAD top) | 14.7 | 7.6 | 15.1 | 11.0 | 1.04 / 1.03 |
| 0.92 | 13.1 | 6.7 | 13.2 | 9.6 | 1.17 / 1.18 |
| 0.85 (current default) | 11.3 | 5.8 | 10.9 | 7.9 | 1.36 / 1.43 |
| 0.80 | 10.1 | 5.2 | 9.3 | 6.7 | 1.51 / 1.68 |
| 0.70 (GRAD bottom) | 8.0 | 4.1 | 6.6 | 4.8 | 1.91 / 2.36 |
| 0.65 | 7.1 | **3.6** | 5.6 | **4.05** | 2.16 / 2.80 |

- **AA isn't the binding constraint.** The title, at 22–32 sp (large text, 3:1) and bold, passes at every α. Back is a non-text icon (3:1) and passes everywhere. The actions are opaque tonal pills. The subtitle is 14 sp body text (4.5:1) and fails in light at α ≤ 0.70. It only shares the header with content while collapsing, but GRAD's 0.70 bottom edge is exactly where it sits.
- **The real measure is the ghost:** how strongly the text underneath competes with the title. The ratio is uniform-background WCAG, so it doesn't capture moving glyphs crossing the title strokes.
  - Above about 1.3:1, the ghost text reads as text. The frames confirm this: `G80NF-large-c-detail` frames 3, 5 and 6 show a glyph between Back and "Push" and "Incline dumbbell" running through the title, and `GRAD-light-c-detail` frame 6 shows "Push/DB × 9".
  - At 200% the moving glyphs are as large as the title, so the effect is worse.
  - Dark is worse than light at the same α.
- **α ≥ 0.92 with the top fade kept** is the only setting where the ghost stays at or below 1.2:1.

## 1. Ranking

| Variant | Score | Reason |
|---|---|---|
| G92 | 7/10 | Title at or above 13:1 and ghost at or below 1.18 in both themes. The page reads as passing under the bar without competing with it. The fade keeps a soft edge. |
| G100 | 7/10 | Same legibility as today and no new risk, but it isn't what the owner asked for. All the code findings below apply to it too, because the reach-under machinery is shared. |
| GRAD | 5/10 | Its bottom (0.70) is where the compact row and the folding subtitle sit: subtitle 4.1:1 in light, and dark ghost 2.4:1 behind Back and the title. |
| G92NF | 5/10 | Same α as G92, but with no fade the rows meet the bar with a hard ghost line. |
| G80 / G80NF | 4/10 | Ghost 1.5–1.7: readable text crossing "Push" at 200% (`G80NF-large-c-detail`). |
| G65 / G65NF | 2/10 | The subtitle fails AA in light (3.6) and in dark it is at the floor (4.05). Ghost 2.2–2.8. |

My pick is **G92** with the top fade kept.

## 2. Must fix (these apply to every variant, G100 included)

1. **A fixed control's body reaches under the header and paints over the History tab row.** (`reachUnderHeader`: `onPlaced { under.first = it.positionInParent().y < 1f }`) `positionInParent` is relative to the *immediate* parent. A `LazyBody` inside a HorizontalPager page is at y=0 in its page, so it decides it is first under the header, even though the tab row sits between them.
   - Evidence: `G92-light-b-history` frames 2–7 show the calendar and workout rows painted over "Workouts / Exercises / Body". In `glass/G100-header-large-e-bodyweight-0` the muscle bars sit over the tab row **at rest**, with opaque glass.
   - The pager is placed after the tab row, so it also wins pointer hit-testing over the pills, and the uncovered-region pass gives it their accessibility bounds. The tabs become untappable and unreadable to TalkBack.
   - The brief's own claim ("bodies with fixed controls above them don't reach under") doesn't hold.
   - Fix: decide this structurally, not from geometry. Either let ScreenScaffold provide `LocalHeaderInset` only to a body that opts in, or have any fixed control clear it for what follows. Alternatively, compare against the scaffold's body origin (`positionInRoot` vs the scaffold's own) rather than the immediate parent's.
   - Add a test: History's tab pills keep their click and semantics bounds after the Workouts page is scrolled deep.
2. **Rows under the glass stay in the TalkBack tree at full size, over the header's empty space.** Compose's uncovered-region pass only subtracts *semantics* nodes drawn above. The header Box has no semantics, so only the title, Back and actions occlude anything. The result:
   - explore-by-touch on the header's empty area focuses a row behind the glass;
   - double-tap then sends `ACTION_CLICK` straight to the row, which bypasses `BlocksTouchesBeneath`, because pointer consumption doesn't apply to accessibility actions;
   - swipe navigation lands on rows hidden under the title and draws the focus ring through it.
   This is exactly the case DESIGN.md's rule about rows "scrolled partly under the top edge" (`yieldsToFixedControls`) was written for, and the rule is now broken. Status: PLAUSIBLE, from Compose's documented uncovered-node behaviour; prove it with a test.
   - Fix: give the header container a semantics node (for example `Modifier.semantics { isTraversalGroup = true }`), so its whole rect is subtracted from what lies beneath.
   - Add a test: after scrolling, a row partly under the header reports accessibility bounds whose top is at or below the header's bottom.
3. **Bring-into-view treats the glass as visible.** A verticalScroll or LazyColumn viewport now extends to the top of the screen, so `bringIntoView` and TalkBack's `ACTION_SHOW_ON_SCREEN` consider a row under the header to be on screen. Lazy lists ignore `contentPadding` for this.
   - Today these calls can leave a field or a TalkBack-focused row hidden behind the bar: SetEditing.kt:189, MeasuredEntry.kt:451/849, LogEntry.kt:262, RoutinesUi.kt:511.
   - Fix: provide a `LocalBringIntoViewSpec` (or a `BringIntoViewResponder`) on bodies that reach under, treating the top `under.px` as off-screen.
   - Add a test: a field focused just under the header is scrolled clear of it.
4. **The geometry is learned one frame late and feeds back into layout.** `UnderHeader.first` is state written in `onPlaced` and read in the same subtree's measure (`under.px` in `reachUnderHeader`, `padUnderHeader`, `UnderHeaderPadding`). The first frame lays out without the reach, and the next relayouts with it. Nothing moves visually, because the reach and the padding cancel, but semantics bounds are computed on a frame that is then corrected.
   - `inset.height` is written in ScreenScaffold's measure and read by the body's measure in the same pass. Correct, but it invalidates the body's measure on every collapse frame. Acceptable, since the body moved anyway.
   - Fixing finding 1 structurally removes the `onPlaced` write entirely, which is the better outcome.
5. **The subtitle at large text sits on content while it folds.** It is 14 sp × 2 at 200% and is visible for the first third of a collapse, which is the only time content is beneath it. At α 0.92 it is 6.7:1, so it passes. Don't take α below 0.80 in light without re-measuring this.

Non-blocking:
- `BlocksTouchesBeneath` is needed: a Box with no pointer modifier isn't hit, so the touch would fall through. It consumes in the Main pass after the children, so Back and the actions still work.
  - Worth one test: a tap on empty header space doesn't click the row beneath.
- `headerGlass` is a mutable global again. Make it a constant once α is chosen.
- In light, the white cards (`surfaceContainerLowest`) under 0.92 glass tint the bar slightly lighter for a moment as they pass. That's fine.

## 3. Verdict

G92 as-is: **NOT SATISFIED**. Finding 1 is a shipped-quality regression visible at rest even at α 1.0. Findings 2 and 3 break the DESIGN.md accessibility rule and bring-into-view. After fixes 1–3 with tests, plus α = 0.92 and the fade kept, I expect to be satisfied.

VERDICT: NOT SATISFIED

---

# Round G2: final glass (α 0.95, 12 dp feather, no top fade)

Read: the new ScreenHeader.kt (HEADER_GLASS_ALPHA/FEATHER, HeaderInset.column, UnderHeader.placed, UnderHeaderBringIntoView, ProvideUnderHeaderBringIntoView, headerGlass, CoversThePageBeneath), ScrollBody (Components.kt:248-271), LazyBody (HistoryScreens.kt:920-940), and the HeaderCollapseTest list (10 tests). Opened gsheets2 `large-b-history`, `large-e-bodyweight`, `large-a-today` and `light-c-detail`, plus crops of glass2 `header-large-a-today-4-pull`, `header-light-c-detail-4-pull`, `header-light-a-today-4-pull` and `header-dark-a-today-3-deep`.

## Round G findings

| # | Status | Notes |
|---|---|---|
| 1 Tab row covered | **Fixed** | Measuring against `HeaderInset.column` is correct. `large-b-history` frames 2–7 keep "Workouts / Exercises / Body" clean, and the new test fails on the old check, which is the right kind of proof. The lag left by `onPlaced` can't be seen at rest because the reach and the padding cancel. It relies on the column's `onPlaced` having run before the body's, and the frames show it working (`dark-a-today-3-deep` has rows under the glass). Acceptable. |
| 2 TalkBack and hit testing under the glass | **Fixed** | The `isTraversalGroup` node gives the header's whole rect a semantics node, so Compose's uncovered-region pass subtracts it from the rows beneath. Taps are covered by `theHeadersEmptySpaceNeverTapsARowBeneathIt`. **I accept having no explore-by-touch test.** If you want one later, Robolectric can do it without TalkBack: enable the AccessibilityManager shadow, then read `AccessibilityNodeInfo.getBoundsInScreen` for a row scrolled under the header from the ComposeView's `accessibilityNodeProvider`. That is the same uncovered-bounds path TalkBack uses. Not blocking. |
| 3 Bring-into-view | Fixed for the body, but **it leaks** | See finding 1 below. |
| 4 Measure-time writes | Acceptable | `inset.height` (written in measure, read in the child's measure) and `first` (from `onPlaced`) remain. Both are bounded and verified. |
| 5 Subtitle on glass | **Fixed** | Content stays at the header's bottom until the header is fully collapsed, so the subtitle (gone by a third of the way) never sits over content. |

Contrast at α 0.95: title/Back 13.9:1 (light) and 14.2:1 (dark), worst case over same-colour text. The ghost is 1.10 in both themes. `light-c-detail-4-pull` confirms "25 kg/DB × 9" reads only as faint texture beside "Push". This is the right α.

## Must fix

1. **`LocalBringIntoViewSpec` leaks into everything inside the body, including dialogs.** `ProvideUnderHeaderBringIntoView` wraps the whole body, so every scrollable composed inside it inherits a spec that treats its leading `under.px` (about 56–64 dp) as hidden.
   - This includes `AdaptiveDialog`'s `verticalScroll` body: AlertDialog's subcomposition inherits composition locals.
   - It also includes DurationPicker's `horizontalScroll` chip row (DurationPicker.kt:164), where the spec then applies on the *horizontal* axis.
   - The effect: a focused field near the top of a dialog opened from a screen that reaches under is treated as hidden, so the dialog scrolls it down by about a header's height. That breaks DESIGN.md's dialog rule ("the focused field stays visible"; "actions are whole once the keyboard closes").
   - Fix: capture `val outer = LocalBringIntoViewSpec.current` before providing the spec, and re-provide `outer` around `content` inside the scroll container, so only the body's own `verticalScroll`/`LazyColumn` sees it.
   - Add a test: open an `AdaptiveDialog` with a field from inside a ScrollBody, focus the field, and assert the dialog body's scroll stays 0.
2. **At 200% text the feather runs into the compact top-level title.**
   - The compact top-level row hugs its title (`compactTitleH + 8 dp`), leaving about 8 dp of header below the glyphs. The 12 dp feather therefore starts *above* the title's bottom.
   - In `header-large-a-today-4-pull` the lower part of "Tuesday" sits on fading glass, and the row's "Push" ghost reads clearly through "ay". At about α 0.32 near the bottom of the feather, the title measures 2.1:1 (dark) and 2.7:1 (light) against same-colour text beneath, below the 3:1 large-text floor. The ghost there is 5.7–7.5:1.
   - At 100% (`header-light-a-today-4-pull`) only the descender of "y" enters the feather, which is fine.
   - Fix: keep the feather off the header's content. Either draw it *below* the header's bounds (the glass Box isn't clipped; at rest the body's 8–12 dp top padding sits under it, so no row is dimmed), or give the compact top-level row a bottom margin of at least `HEADER_GLASS_FEATHER`.
   - Re-capture `large-a-today-4-pull` and `-3-deep` afterwards.

Non-blocking:
- The ghost of a row's tonal icon button can sit exactly where header actions would be (the pencil circle in `header-light-c-detail-4-pull`) and read for a moment like a disabled action. At 0.95 it's faint, so this is acceptable.
- `UnderHeader.placed` returns early when `column` is null. Add a one-line comment that the body's later re-placement (any scroll relayout) recovers it, or check it again when the column is placed.

## Verdict

The final glass as-is: **NOT SATISFIED**, on two small fixes: scope the bring-into-view spec to the body's own scrollable (finding 1), and keep the feather off the 200% title (finding 2). Everything else from Round G is fixed and tested, and with those two in I'd be satisfied.

VERDICT: NOT SATISFIED

---

# Round G3

Read: ScreenHeader.kt (HeaderInset.scrolled, UnderHeader.placed, UnderHeaderBringIntoView, ProvideUnderHeaderBringIntoView/`inside`, InsideScope, headerGlass), ScrollBody (Components.kt:250-272) and LazyBody (HistoryScreens.kt:925-941). Opened crops of glass3 `header-large-a-today-4-pull`, `header-light-e-bodyweight-3-deep` and `header-dark-c-detail-3-deep`, and sampled pixels from `header-large-a-today-4-pull`.

## G2 must-fixes

1. **Bring-into-view leak: fixed.** The outer spec is captured before the new one is provided and handed back through `inside`. ScrollBody wraps all its content in it, and LazyBody wraps every `item`/`items`/`stickyHeader` through `InsideScope`, so only the body's own `verticalScroll`/`LazyColumn` sees `UnderHeaderBringIntoView`. Dialogs composed from inside the content inherit the outer spec. `theStripUnderTheHeaderMeansNothingInsideTheBody` asserts exactly that, and `bringingARowIntoViewClearsTheHeader` covers the positive case.
2. **Feather over the 200% title: fixed.** The glass is solid α 0.95 over the header's whole box, and the feather is drawn *below* the box's edge, only while the body has scrolled.
   - Pixel check on `header-large-a-today-4-pull`, at the columns where the "Push" ghost crosses "Tuesday" (x≈470–505 px): the ghost strokes reach a channel max of 24–26 over the page's 15, which is α 0.95 as specified (EDE6DD·0.05 + 0F0E0D·0.95 ≈ 26). That puts the ghost at about 1.10:1 and the title at about 14.2:1.
   - The title's glyphs end at about y=145 px, and the gradient only starts at about y=170 px. The collapsed title sits wholly on solid glass.
   - At 1× (`dark-c-detail-3-deep`, `light-e-bodyweight-3-deep`) Back, the title and Log all sit on solid glass, and rows dissolve through the feather beneath them.

## Remaining notes (non-blocking)

- **`HeaderInset.scrolled` is swapped as a plain lambda in `onPlaced`.** Draw only re-runs when a state read inside the *previous* lambda changes, or when the header resizes. On a pushed screen with no subtitle (range 0, so the header never resizes) the glass could keep the initial `{ false }` until something else invalidates its draw.
  - The frames show the feather present on Bodyweight (the "Wed 14 Oct" row just below the header is dimmed), so in practice something invalidates it.
  - For robustness, hold the lambda (or the `first` body's `UnderHeader`) in a `mutableStateOf`, so swapping it invalidates the draw.
- **The hidden strip for bring-into-view is the header only, not header plus feather.** A field brought into view lands at the header's bottom, under the densest part of the 24/40 dp feather. This matches the old 32/48 dp top `fadingEdges`, which also sat at the body's top edge, so it's not a regression.
  - Optional: add the feather's px to `hidden` while the body is scrolled, so a focused field lands fully clear.
- **Taps in the feather strip reach the dimmed rows**, which is also the same as under the old fade. That's acceptable.
- **I accept having no explore-by-touch test** (see Round G2 for a Robolectric route if you want one later).

## Verdict

The final glass (α 0.95, feather below the edge, MORPH collapse): **SATISFIED**.
- Title and Back stay at or above 13.9:1 in both themes and at 200%.
- The subtitle never sits on the glass.
- Rows under the glass are covered for taps and TalkBack, and fixed controls are never reached over.
- Bring-into-view is scoped to the body, and the header's behaviour is under test (12 header tests).

VERDICT: SATISFIED
