# Brief round G: a see-through header

Owner's request: "Can we make our top bar a bit transparent" — on top of the shipped MORPH collapsing header.

## What changed (uncommitted, branch feat/collapsing-header)
- `ScreenScaffold` (Components.kt) is now a custom Layout: header measured first and drawn OVER the body.
  The body column sits below the header; the scrolling body that is FIRST under the header (`ScrollBody`,
  `LazyBody`) reaches up beneath it (`reachUnderHeader`) and pads its content by the header's current height
  (`padUnderHeader` / `UnderHeaderPadding`), so at rest nothing moves and scrolled rows pass under the glass.
  Bodies with fixed controls above them (History's tab row, the workout rail, the exercise library, routine
  editor) don't reach under; their header looks as before.
- The header gets a page-colour (`surface`) background at alpha α (`Modifier.headerGlass`), and
  `BlocksTouchesBeneath` keeps taps on empty header space from reaching rows beneath.
- No real blur (Compose has no backdrop blur short of RenderEffect on API 31+ or a new library).
- Code: ScreenHeader.kt (bottom of file), Components.kt ScreenScaffold/ScrollBody/fadingEdges, HistoryScreens.kt LazyBody.

## Variants (prefix in file names)
- `G100` α 1.0 — opaque; visually the same as before this round (baseline).
- `G92`, `G80`, `G65` — α 0.92 / 0.80 / 0.65, page's 32 dp top fade kept (it now sits under the header).
- `GRAD` — gradient α 0.98 at top → 0.70 at the header's bottom edge, fade kept.
- `G92NF`, `G80NF`, `G65NF` — same alphas with NO top fade under the header (the glass is the edge).

## Evidence
`/tmp/claude-1000/-home-cross-workspace-github-com-kidusm001-rebuild-android/fd0adfeb-c88d-4e7c-94e9-308c83c94f8e/scratchpad/glass/<VARIANT>-header-<light|dark|large>-<screen>-<frame>.png`
(screens/frames as in the first brief; 3-deep and 1-drag96 show content under the header best; c-detail and
e-bodyweight have dense text scrolling under Back + title). Sheets: `.../scratchpad/gsheets/<VARIANT>-<mode>-<screen>.png`.
Side-by-side Today deep: `.../scratchpad/cmp-glass-{light,dark}.png` (G100 G92 G80 G65 GRAD) and
`cmp-glassnf-{light,dark}.png` (G100 G80 G92NF G80NF G65NF). Open the images; a frame you didn't open proves nothing.

## Return
Append "Round G" to your review file:
1. Rank the variants (score /10, one line each). "Don't do it" (G100) is a legitimate answer.
2. For your pick: numbered must-fix findings (legibility/contrast of the title, Back and actions over moving
   text — measure or reason WCAG on the composited colours; TalkBack/hit testing of rows under the header;
   motion/continuity; code).
3. Verdict on your pick as-is: SATISFIED / NOT SATISFIED.
