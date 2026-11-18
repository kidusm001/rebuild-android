# App fonts, round 2: shared brief

Round 1's brief ([brief-round1.md](brief-round1.md)) still applies: the owner
has decided to bundle fonts for all text, and the product, personality and
constraints are unchanged. Read it first if you haven't.

## What round 1 found

Four Opus critics reviewed 13 sets on the real app screens.

| Critic | 1st | 2nd | 3rd |
| --- | --- | --- | --- |
| [UX / accessibility](review-r1-ux.md) | Archivo | Roboto Flex | Noto Sans (if narrowed) |
| [Visual / typography](review-r1-visual.md) | Archivo | Instrument Sans | Fira |
| [Platform](review-r1-platform.md) | Roboto Flex | Archivo | Roboto (control) |
| [Product / brand](review-r1-product.md) | Fira | Archivo | Sofia Sans (if tuned) |

**Archivo was in every top 3.** Eliminated by three or more critics: Google
Sans Flex (reads as Pixel UI, ledger wraps), Mona Sans (GitHub's face, slashed
zeros), Atkinson + Fira Condensed (two voices, two zero styles), Asap (colon
jams `2:50`), Nunito Sans (small, soft, generic), Instrument Sans (missing
glyphs, cramped middots, wraps at 200%), Sofia Sans (too light: the instances
are correct weights, the design itself is light). Noto Sans (reflows at 200%)
is out with no champion.

Specific concerns raised about the finalists:

- **Archivo** at `wdth 75` makes the ledger cramped; it needs `tnum` on every
  numeral style (Theme.kt already does it); Latin only (no Cyrillic/Greek); on
  near-black it may lean toward WHOOP's DIN look.
- **Roboto Flex** looks almost the same as Roboto; its upside is optical size and
  the `GRAD` axis (thinner on dark without width change), which a static render
  can't show.
- **Fira** words are about 104% of Roboto's width with no width axis, and wrap at
  200%; the colon is narrow; 300 KB of static files.
- Platform: release builds' `shrinkResources` strips unreferenced raw
  resources, so the licence must go in `assets/`.

## Round 2 sets

| Set | Words | Numerals | Numerals width vs today |
| --- | --- | --- | --- |
| `roboto` | Roboto wdth 100 | Roboto wdth 75 | 100% (control) |
| `robotoflex` | Roboto Flex wdth 100 opsz 16 | wdth 72 opsz 36 | 100–101% |
| `archivo` | Archivo wdth 100 | **wdth 75** wght 700 | 92–96% |
| `archivo-w80` | Archivo wdth 100 | **wdth 80** wght 700 | 97–100% |
| `archivo-w75-800` | Archivo wdth 100 | wdth 75 **wght 800** (each numeral weight +100) | ~95–99% |
| `fira` | Fira Sans | Fira Sans Condensed | 97–99% |

`archivo-w80` uses the headroom the UX critic found, to open the ledger without
widening past today's countdown. `archivo-w75-800` tests the visual critic's
heavier countdown.

## Evidence

- [round2/glance.png](round2/glance.png): the confusable-digit sheet asked for
  in round 1. `1:11 3:38 6:08 10:01` at 64 sp on amber and on the dark canvas;
  a ledger value and small labels (`Il1 O0`, `RIR 1`, `Last time`) on dark; a
  28 sp title and a 16 sp line beside Amharic set in Noto Sans Ethiopic (the
  system fallback); then the countdowns again, **blurred** to approximate weak
  vision at arm's length. The last column, `din-ref`, is Barlow Semi Condensed
  (a DIN-like face) as a **reference only**, to judge which finalists drift
  toward a WHOOP-style DIN look. It is not a candidate.
- [round2/](round2/) comparison sheets: one real app screen across the six sets
  (`<mode>-<screen>.png`, half scale). The countdown moments are included:
  `light-03-workout` (rest bar), `dark-22-rest-end-sweep-hold`,
  `light-23-rest-alarm-bar`.
- Full-size screens for any set:
  `/tmp/claude-1000/-home-cross-workspace-github-com-kidusm001-rebuild-android/d455c089-d14e-4f60-8ab4-1f4f9fb40efe/scratchpad/screens/<set>/<mode>-<screen>.png`
  (31 screens per mode; modes `light`, `dark`, `large`).

## Your task

Assess independently; do not read the other critics' round 2 files. You may
re-read your own round 1 review. Write your review to the path in your
instructions, under 600 words:

1. **Final pick**: one set, and the exact settings to ship (words: family, wdth,
   weights; numerals: family, wdth, weight). Say if a setting between the
   rendered ones would be better (for example numerals wdth 78).
2. **Runner-up**, and the one condition under which it should win instead.
3. **Scores** 0–4 for the six sets on `fit`, `legibility`, `numerals`, `large`,
   `distinct` (same meaning as round 1).
4. **Must-fix before shipping** your pick: anything in the screens that has to be
   tuned (tracking, a size, a style) or verified on a device.
5. **Did round 2 change your mind?** One line: what moved you, or why not.
