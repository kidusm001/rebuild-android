# Brief: how should screen headers give way when the page scrolls?

Repo: /home/cross/workspace/github.com/kidusm001/rebuild-android (branch feat/collapsing-header, uncommitted).
Read DESIGN.md (Motion, Navigation, Accessibility) and PRODUCT.md first.

## The problem (owner's words)
"When we scroll up the top bar covers the body content too much because it stays the same size."
Every screen uses `ScreenScaffold` (app/src/main/java/com/rebuild/ui/Components.kt). Top-level tabs
(Today, History, Plan, More) name themselves in a 32 sp headlineLarge title with 16 dp above it and
often a subtitle; pushed screens show Back + a titleLarge title (+ subtitle, sometimes actions).
The header never shrinks, so on a phone it eats ~90–100 dp (much more at 200% text) for the whole scroll.

## Candidates (all in app/src/main/java/com/rebuild/ui/ScreenHeader.kt, switched by `headerStyle`)
All are driven by the body's nested scroll, 1:1 with the finger; when the finger lifts or a fling ends
with the header part-way, it settles to the nearer state in `tween(Motion.Short, Standard)`.
The workout screen is excluded (`collapsesOnScroll = false`): its body is the exercise in focus over a fixed dock.

- **FIXED** — today's behaviour (baseline).
- **MORPH** — the large title scales down (32→22 sp, via a layer scale) and slides into a 56 dp compact
  row; subtitle fades out by halfway and folds under it. Actions recentre in the row. Expands again only
  when the body is back at its top. Pushed screens: only the subtitle folds away.
- **SLIDE** — large title, subtitle scroll away with the body and fade as they approach the top; a
  separate small titleLarge title fades in over the last 40% in a 56 dp bar. Back/actions ride up then stick.
- **RETURN** — the whole header (including Back and actions) scrolls away entirely; any pull toward the
  top brings it back before the body moves (quick return).

## Evidence
Robolectric renders (Galaxy A71-sized 411×914 dp, xxhdpi), real app, real seeded data:
`/tmp/claude-1000/-home-cross-workspace-github-com-kidusm001-rebuild-android/fd0adfeb-c88d-4e7c-94e9-308c83c94f8e/scratchpad/hdr/`
Names: `<STYLE>-<light|dark|large>-<screen>-<frame>.png`
Screens: a-today, b-history (Workouts list under the tab row), c-detail (pushed past workout with subtitle),
d-more, e-bodyweight (pushed, with a "Log" action).
Frames: 0 rest · 1-drag24/56/96 finger held having dragged that many dp · 2-lifted (finger lifted, settled) ·
3-deep (flung deep) · 4-pull (then a 40 dp pull toward the top, finger held) · 5-top (back at the top).
`large` = dark theme at 200% font scale.
Contact sheets (top half of each frame, 8 frames per row) are in `.../scratchpad/sheets/`.
Look at the images (the Read tool shows PNGs). A frame you didn't open proves nothing.
Static frames at known scroll distances are the right evidence for scroll-linked motion; the settle
animation is a 150 ms tween you can judge from code. No emulator recording (the shared emulator is in use).

## What to return
Write your review to `.../scratchpad/review-<your-role>.md`:
1. Rank the four candidates, with a score /10 each and the one-line reason.
2. Numbered findings for your top pick that must change before it ships (cite the frame file or code line).
3. Any hybrid you'd prefer (e.g. MORPH on top-level + RETURN on pushed), and why.
4. Verdict on your top pick as-is: SATISFIED / NOT SATISFIED.
Be terse and concrete. Do not edit the app's code.
