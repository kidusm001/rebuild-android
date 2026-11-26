# UX / Product review: collapsing header

Evidence I opened: the sheets for MORPH/SLIDE/RETURN light+large a-today; MORPH/RETURN light b-history and c-detail; MORPH dark d-more; MORPH/SLIDE/RETURN large e-bodyweight; MORPH light e-bodyweight; MORPH large b-history. Full-resolution crops of MORPH-light-a-today-2-lifted, MORPH-large-a-today-1-drag24 and MORPH-large-c-detail-3-deep. Code: ScreenHeader.kt and the Components.kt diff.

## 1. Ranking

| # | Style | Score | One line |
|---|---|---|---|
| 1 | **MORPH** | 7/10 | The same word shrinks into the bar, so you never lose where you are. Back and the action are always reachable, and top-level and pushed screens end in the same 56 dp bar. But the room it gives back is small on screens without a subtitle, and at 200% it gives none on pushed screens that have an action. |
| 2 | SLIDE | 5.5/10 | It ends in the same state as MORPH, but gets there by cross-fading two titles. In the middle neither title is readable (SLIDE-light-a-today-1-drag24 shows a ghost-grey "Tuesday"), and it adds an extra node for no gain. |
| 3 | RETURN | 5/10 | It gives back the most room and is fine on top-level tabs, where the nav bar already says where you are. On pushed screens it hides Back, the title and "Log" (RETURN-light-c-detail-3-deep: you can't tell which workout you're reading), and its in-between frames clip text (see below). |
| 4 | FIXED | 3/10 | Doesn't fix the owner's complaint at all. |

RETURN's in-between frames, which a held finger can leave on screen:
- RETURN-large-e-bodyweight-1-drag24 cuts "Bodyweight" through the middle.
- RETURN-large-e-bodyweight-1-drag56 leaves a lone "Log" pill with no title.
- RETURN-large-a-today-4-pull shows "20 Oct · Baseline" with no "Tuesday" above it.

A lifter scanning up and down a set list would also see the header keep jumping in and out.

## 2. MORPH: must change before it ships

1. **At 200% text, pushed screens with an action never collapse.** In MORPH-large-e-bodyweight frames 0 to 3-deep, the "← Bodyweight" row and the separate "Log" row (ACTIONS_ROW) stay at full height for the whole scroll, about 110 dp. That is the owner's complaint, at its worst, on the users who need room most. The cause is ScreenHeader.kt:151: `compactH` keeps `actionsRowH`, and with no subtitle `range` is 0. Fix: in the compact state, move the actions into the title row (Back, title ellipsized to one line, then Log), or let ACTIONS_ROW fold away like the subtitle. Back and the action must stay.
2. **The room won is too small where there's no subtitle.** On History and More, the header only drops from about 66 dp to 56 dp. Compare MORPH-light-b-history-0 with -2-lifted: the tab row rises about 10 to 12 dp. At 1x the full win, on Today, is about 35 dp. A top-level compact bar has no Back and no actions, so it doesn't need 48 dp plus 2×4 dp. Make the compact row hug the shrunken title, or (better) see §3.
3. **The compact top-level title doesn't match the pushed-screen title.** MORPH scales headlineLarge: Bold, −0.8 sp tracking (Theme.kt:137). Pushed screens use titleLarge: SemiBold, −0.2 sp. Collapsed "Tuesday" (MORPH-light-a-today-2-lifted) is visibly heavier and tighter than "Push" on c-detail. `compactScale` is also a ratio of the raw `.sp` values (Components.kt diff), which ignores Android 14's non-linear font scaling at 200%, so the two sizes drift apart there too. Fix: when fully collapsed, cross-fade to a real titleLarge in the last ~15%, or accept it as documented. Pick one consciously.
4. **There's no edge between the collapsed bar and the body.** In MORPH-light-a-today-2-lifted, the "Push" card is cut at the bar's bottom edge, with only the scroll fade separating them. It reads as the card being clipped rather than sliding under a bar. Once collapsed, add a tonal surface step or hairline (the M3 "on-scroll container" role) that appears with `fraction`. Don't use a shadow, and check AA contrast in both themes.
5. **Wrong text during the subtitle fold at large text.** In MORPH-large-a-today-1-drag24, "20 Oct · Baseline" sits at about 25% alpha but fully laid out under the title, so it reads as disabled text. It is only transient under a finger, and settle (`onPostFling`, tween Short) prevents it at rest, so it's acceptable. But make the fade finish sooner (by 35% rather than 50%) so the ghost is shorter-lived.
6. **Accessibility:** DESIGN.md (≈ line 399) says "Scrolling bodies sit beneath the fixed header". Update that text, and re-verify the existing rule that a row scrolled partly under the header is never exposed as a sliver control. The header's height now changes, so the clip edge moves. The scaled title also keeps its unscaled semantics bounds (placeWithLayer), so TalkBack's focus rectangle for the heading will be the 32 sp box overlapping the body when collapsed. Check it.
7. Remove the temporary `var headerStyle` switch and the unused SMALL/SLIDE paths before merging.

(Not caused by this change, but visible in every large bodyweight frame: the weeks list renders "Mon 19 Oc / t– / Sun 25 Oct". That breaks the rule that values never wrap apart at 200%, so file it separately.)

## 3. The hybrid I'd prefer

**Top-level tabs: the title scrolls away entirely, with no compact bar. Pushed screens: MORPH.**

- On Today, History, Plan and More, the selected bottom-nav or rail item already tells you where you are. A 56 dp bar repeating "History" above a sticky tab row is the wasted room the owner is complaining about. Let the large title and subtitle scroll away 1:1 (the RETURN geometry, but expanding only when the body is back at its top, as MORPH does). That gives up all ~66 to 100 dp. On History, the sticky Workouts/Exercises/Body tab row becomes the top edge, which is the best anchor there anyway.
- Don't use quick-return there. Re-revealing on every upward nudge makes the page jump while a tired lifter scans back for a set.
- On pushed screens, the bar carries Back, the screen's name and its action, so it must stay: use MORPH, with finding 1 fixed so the action shares the 56 dp row.
- The result is consistent in the way users actually notice: tabs go to full body, and pushed screens always keep a way back.

If the owner insists on one style everywhere, ship MORPH with findings 1 to 4 fixed.

## 4. Verdict on MORPH as-is

**NOT SATISFIED.** Finding 1 leaves the owner's complaint unfixed at 200% text on pushed screens, and finding 2 means the gain on History and More is only about 10 dp, which the owner will barely feel.

---

# Round 2: revised MORPH

What I checked: the new ScreenHeader.kt, the Components.kt and DESIGN.md diffs, HeaderCollapseTest.kt, and Motion.settle(). From the new frames I opened the sheets2 sheets header-large-e-bodyweight, header-light-b-history, header-light-a-today and header-large-a-today, plus full-resolution crops of hdr2 header-light-b-history-3-deep, header-large-e-bodyweight-1-drag24, header-large-e-bodyweight-3-deep and header-light-a-today-1-drag24.

## Status of my round-1 findings

1. **Large-text action row: fixed.** From header-large-e-bodyweight-2 onward, the compact row reads "← Bodyweight … Log" on one line. The extra ~56 dp row is gone, and Back and Log both stay reachable. In the in-between frame (-1-drag24), Log is mid-climb, below and to the right of the title, and it is clean: it doesn't overlap the title or get clipped. When the action doesn't fit, it falls back to its own row, which is the right call. A test covers this (`atLargeTextTheActionsJoinTheCompactRow`).
2. **Too little room won: fixed well enough.** The History compact row (header-light-b-history-3-deep) hugs the title. The tab row now rises about 24 dp, and Today gains about 48 dp. History still keeps a title plus a sticky tab row, but that is a fair price for always knowing where you are. I accept the council's rejection of hiding the title.
3. **Bold scaled title vs SemiBold on pushed screens: accepted as a deliberate choice.** The reasoning holds: a cross-fade brings back SLIDE's moment with two titles. The slightly heavier top-level title also reads as a level above, not as a mistake. Computing `compactScale` from px at the current font scale closes the non-linear scaling drift.
4. **No divider: accepted, with a caveat.** In the full-resolution header-large-e-bodyweight-3-deep crop, the chart's date labels ("29 Jul / 20 Oct") are cut hard at the header edge. In header-light-b-history-3-deep, a 2–3 dp white sliver of the previous card sits under the tab row. That is ordinary body scrolling, not the header's fault, and DESIGN.md's top-edge fade is meant to cover it. The cut doesn't look faded in these frames, though, so confirm on the emulator that the fade actually covers the moving edge. This is a nit, not a blocker.
5. **Ghost subtitle: fixed.** The subtitle is gone by a third of the collapse and no longer placed once it is invisible (header-light-a-today-1-drag24 shows a clean "Tuesday" with no grey text under it). `aTopLevelHeaderShrinksDeep…` proves TalkBack doesn't read it.
6. **Accessibility docs: done.** The DESIGN.md "Headers" bullet was added and the "fixed header" wording corrected. One nit carries over: the scaled title is still placed with a layer, so its semantics bounds stay at the full 32 sp size, and TalkBack's focus box for the collapsed heading will overhang the body. That's cosmetic for a heading, so I'm not blocking on it.
7. **Temporary style switch: gone.** `headerStyle`, HeaderStyle and SMALL no longer appear anywhere in ui/.

## New observations

- The spring settle (critically damped, stiffness 800) starts from the release speed, follows the direction the finger was moving, and is interruptible. That is better than the 150 ms tween: a hard flick never "snaps back" against the finger. A partly collapsed header can only exist while the body is at its top (collapse happens before the body scrolls, and the header only opens from post-scroll), so settling open can never reveal the large title mid-page. Good.
- Opening only when the body is back at its top fits the tired one-handed lifter: scanning back up a set list doesn't make the header jump.
- Nothing blocking is left. The 'Mon 19 Oc/t' wrap is pre-existing; file it separately.

VERDICT: SATISFIED

---

# Round G: see-through header

What I opened: cmp-glass-{light,dark}, cmp-glassnf-{light,dark}, and the gsheets G80-light-b-history, G80-light-e-bodyweight and G100-light-e-bodyweight. I also built my own crops of every variant's c-detail-1-drag96 (light and dark) and e-bodyweight-3-deep, and opened full frames of G80-light-e-bodyweight-3-deep, G80-light-b-history-3-deep and G92NF-large-c-detail-3-deep. Code: the ScreenHeader.kt glass section and the Components.kt/HistoryScreens.kt diffs.

## Blockers that apply to every variant, G100 included

1. **History's list now draws over its own tab row.** In G80-light-b-history-3-deep (full frame), a "13 · Baseline · 9 working sets" row covers the Workouts/Exercises/Body labels and cuts them in half. In the b-history sheet, "October 2026" sits on top of the tabs in frames 3 and 4. In the e-bodyweight crops, a dashed line strikes through "Workouts" in all 8 variants, including G100.

   The cause is `reachUnderHeader` (ScreenHeader.kt ~250): it sets `under.first = positionInParent().y < 1f`. History's LazyBody sits at y = 0 *of its pager page*, below the tab row, so it counts itself as "first under the header". It then grows up by the header's height and draws over the tabs. That breaks the brief's "History doesn't reach under".

   It is also a touch bug, not just a visual one. The list is drawn above the tab row, so a tap on a tab label that has a row behind it will open that workout instead of switching tabs.

   Fix: decide "first under the header" against the scaffold's body (for example, compare positionInRoot with the header's bottom, or have ScreenScaffold pass an explicit opt-in), never against the immediate parent. Then add a test: on History, scroll deep, and check that a tap on "Exercises" switches the tab.
2. **The e-bodyweight evidence is wrong.** Every `*-e-bodyweight-*` frame in glass/ shows the History tab, not Bodyweight (compare G100-light-e-bodyweight with G80-light-b-history). The harness didn't navigate there. The screen with an action ("Log") over moving text is therefore unverified. Recapture it.

## Ranking (as a UX/product call; all variants assume blocker 1 is fixed)

| # | Variant | Score | One line |
|---|---|---|---|
| 1 | **G92NF** | 7/10 | The only variant where the owner will actually notice "a bit transparent": you see that rows pass under the bar ("+1 more" ghosting in cmp-glassnf), while the title stays clean at 1x in light and dark. |
| 2 | G100 | 6.5/10 | "Don't do it" is defensible. It's zero risk and the owner already got the room they asked for. |
| 3 | GRAD | 6/10 | Right instinct (solid behind the title, lighter at the edge), but the 32 dp fade under it hides nearly everything, so the owner pays for the complexity and sees almost nothing. |
| 4 | G92 | 5/10 | With the fade kept under the glass, the transparency doesn't show (cmp-glass: about the same as G100). It's complexity for nothing. |
| 5 | G80NF | 4.5/10 | It reads as glass, but in c-detail-1-drag96, "6:30 PM · 9 working sets" ghosts through right behind Back and "Push". A tired glance has to separate two layers of text. |
| 6 | G80 | 4/10 | It has G80NF's ghosting near the title but loses its clean bottom edge. |
| 7 | G65 / G65NF | 2/10 | Noise. In the detail crops the ghost text behind "Push" is nearly as strong as the body text. It reads as a rendering bug, not polish. |

On contrast: every variant passes WCAG numerically. Composited against the worst case (ink text underneath), the title contrast stays well above AA:
- Light: G80 bg ≈ 0.8·surface + 0.2·ink, about 10:1. G65 is about 7:1.
- Dark: G80 is about 9:1.

So the problem is legibility clutter, glyphs overlapping glyphs, not the ratio.

## Must-fix for G92NF

1. Blocker 1 above (History tabs: drawing and hit-testing).
2. Blocker 2: recapture Bodyweight with "Log" over moving text, in light, dark and large.
3. **At 200% in dark, the ghost behind the title is too strong.** In G92NF-large-c-detail-3-deep, "Incline dumbbell / press" in large type sits visibly behind "Push" at the same scale, so you're reading two headlines. Use a slight gradient: about 0.97 behind the title row, down to about 0.88 at the header's bottom edge, with no fade. That keeps the transparency where it's noticed (the edge) and out from behind the words. If that can't be done, G100 at large text is the honest fallback.
4. **TalkBack.** Rows behind the glass stay in the semantics tree and keep bounds that overlap Back and the title. That breaks DESIGN.md's existing rule that a row partly under the top edge never takes a control's touch area. Prove with a test that explore-by-touch on Back focuses Back, not the row beneath it. Better still, clip those rows' semantics to the area below the header, as was done before for chips and pills.
5. `BlocksTouchesBeneath` consumes every event in the header's pointerInput. Confirm it doesn't swallow the system Back gesture's edge swipe and doesn't block the header's own buttons. Back and Log still need a test tap after this change.
6. Clean-up: remove the temporary `var headerGlass` and the unused variant fields once one variant is chosen. Update DESIGN.md's Spacing rule ("Scrolling pages fade at an edge only when there is content beyond it") for the case where the glass replaces the fade.

## Consistency note

History, the workout, the library and the routine editor don't reach under the header, so their header looks opaque, while Today, More and the pushed detail screens look glassy. At 0.92 the difference is subtle enough to read as "that's what scrolling looks like" rather than two designs. At 0.80 or below, the mismatch between tabs would be noticeable. That's one more reason not to go lower than about 0.9.

## Verdict on G92NF as-is

**NOT SATISFIED.** Blocker 1 is a visual regression and a touch regression on History in every variant. The Bodyweight evidence is missing, and the ghost text behind the title at 200% in dark needs the gradient fix.

VERDICT: NOT SATISFIED

---

# Round G2: final glass (α 0.95, 12 dp feather, no fade under the header)

What I opened:
- The gsheets2 sheets light-b-history, light-a-today and large-e-bodyweight.
- A crop sheet of glass2 frames: light/dark c-detail-1-drag96, large/dark c-detail-3-deep, light/dark b-history-3-deep, large/light e-bodyweight-3-deep.
- Top-strip crops of large c-detail-1-drag96, large a-today-3-deep and large d-more-3-deep.
- Pixel scans of large/light/dark a-today-3-deep and large c-detail-3-deep.

Code: the ScreenHeader.kt glass section, HeaderCollapseTest's test list, and the DESIGN.md Headers bullet.

## Round-G findings

1. **History tabs: fixed.** In light and dark b-history-3-deep, Workouts/Exercises/Body are clean, and the list starts below the pill in every frame of the history sheet. `aListBelowFixedControlsNeverReachesOverThem` covers the tap path, and the coordinator reports it fails on the old code.
2. **Bodyweight evidence: fixed.** The real Bodyweight screen is captured now. In large-e-bodyweight, "← Bodyweight … Log" shares the compact row from frame 2 onward. The Log pill keeps its own tonal fill over the moving rows, so it reads fine in light and dark.
3. **Ghost text behind the title: fixed.** Measured on the frames, text scrolled under the solid part of the glass lifts the background by only about 9–11 levels out of 255: dark is 14→25, light is 240→231. That is a hint of depth, not a second headline. Title contrast stays effectively at its opaque value (well above AA). Nothing faint sits behind "Tuesday", "Push" or Back at 1x or at 200%.
4. **TalkBack and hit-testing: addressed.** The header is a traversal group, taps on its empty space are consumed (`theHeadersEmptySpaceNeverTapsARowBeneathIt`), and bring-into-view treats the strip under the glass as hidden. That is what I asked for.
5. **Touch blocker in the Main pass: fine.** Back and Log get the tap first, and the system edge-back gesture happens at window level.
6. **Clean-up: done.** The temporary switches are gone (`HEADER_GLASS_ALPHA`/`FEATHER` are constants), and the DESIGN.md Headers bullet covers the glass, the feather replacing the fade, and the History exception.

## New observations (nits, not blockers)

- **The feather at 200%.** The compact row hugs the title, and the 12 dp feather begins right at the title's bottom. In large c-detail-3-deep, the body line "press" reaches near-full contrast about 1–2 dp below "Push". In large c-detail-1-drag96, "6:30 PM–7:25 PM · 9 working" sits directly under "Push" and reads like a subtitle for a moment. An opaque bar of the same height would show that same line in the same place, so this isn't a regression. If the owner finds it busy, give the compact row 4 dp more bottom padding at large text so the feather clears the glyphs.
- **The owner may not see much transparency.** At 0.95 the "see-through" effect is mostly the soft dissolve at the bar's edge, not visible content behind the title. I think that's the right call for a lifter glancing between sets: it looks like polish, not noise. But have the owner judge it on a phone before merging. If they say "I can't see it", raise the feather (to 16–20 dp) before lowering the alpha behind the title.
- **Consistency.** On History, the opaque-looking header with tabs below sits next to Today's glass, and at 0.95 the difference is invisible at a glance. Good.

VERDICT: SATISFIED
