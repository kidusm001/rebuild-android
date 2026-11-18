# App fonts: after-check brief

The council's decision ([decision.md](decision.md)) is implemented on branch
`design/app-fonts`. This is the check before the PR: did the implementation land
what you asked for, and is anything left that must change before it ships?

## What was built

- `scripts/fonts/build_archivo.py` builds five static files from Archivo
  (pinned commit and SHA-256), subset to Latin, Latin-1, Latin Extended-A and
  punctuation: words at `wdth 100`, 400/500/600/700; numerals at `wdth 80`, 700.
  About 52 KB each.
- Must-fixes from round 2, as implemented:
  - **Number–unit space:** the numerals file's space and no-break space are
    widened to 0.40 of a tabular figure (from 0.31). "42.5 kg" no longer runs
    together.
  - **Vertical centring:** every file gets Roboto's ascent and descent (0.928 /
    0.244 em), so text sits on the baselines the layouts were tuned for. The
    rest-end "2" and the alarm bar countdown are back where today's are.
  - **↳ ● ○** are drawn into each file, matched to that weight's stem, instead of
    falling back to a system font.
  - **Licence:** `assets/licenses/Archivo-OFL.txt`.
  - **Tests:** `FontTest`: every style is Archivo with `tnum`; `0:00`–`9:59`
    never change width; every non-ASCII character in the app's code and resources
    is in every font file (read from the file's cmap); Roboto metrics; the wider
    space.
- `Theme.kt`: every text style uses Archivo; display styles and `Numerals` use
  the width-80 file. Notification layouts (countdown and rest-over, collapsed
  and big) use the numerals file with `tnum` and Archivo for their text; the
  window theme uses Archivo.
- Not done: the optional 650 headline weight (unchanged at 700).

## A trade-off to judge

In the workout's set list, the logged chip "42.5 kg/DB × 10" wraps to two lines
("42.5 kg/DB" / "× 10") because of the wider space (see
`after/dark-03c-workout-drop.png`). `FitText` already shrinks to 75% before
wrapping. Today's fonts (`baseline`) wrap it in the same place; only round 2's
`archivo-w80`, with the narrow space you called cramped, fitted one line.

## Evidence

- [after/](after/): each sheet is one real screen, `baseline` (today's system
  fonts) on the left, `impl1` (this implementation) on the right. Light, dark,
  and `large` (200% text, dark).
- Full-size screens:
  `/tmp/claude-1000/-home-cross-workspace-github-com-kidusm001-rebuild-android/d455c089-d14e-4f60-8ab4-1f4f9fb40efe/scratchpad/screens/impl1/<mode>-<screen>.png`
  (31 per mode: `light`, `dark`, `large`, `land`), and the same names under
  `screens/baseline/`.
- Font files: `app/src/main/res/font/archivo_*.ttf`.

## Your task

Under 300 words, to the path in your instructions:

1. **Verdict:** `ship` or `fix first`.
2. For `fix first`: each blocking item, the file and screen it shows in, and the
   exact change.
3. Your view on the chip wrap: accept, or what to do instead.
4. Anything non-blocking worth a follow-up.
