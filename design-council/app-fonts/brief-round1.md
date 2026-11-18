# App fonts, round 1: shared brief

## What changed since the numerals round

The owner has decided Rebuild **bundles its own fonts for all text**, words and
numbers, because mixing the phone's global font (on their Galaxy A71, One UI's
font) with the app's design looks messy. That is settled; don't argue it.
The question now is **which fonts** best fit Rebuild's design and personality.
The numerals round ([../numerals-font/decision.md](../numerals-font/decision.md))
chose Roboto Condensed assuming words stayed on the system font. That choice is
open again.

## The product (read PRODUCT.md and the top of DESIGN.md)

An offline, private workout notebook for a returning lifter, used one-handed in
a bright gym and at home in a dim room. Personality: calm, direct, dependable.
"Ink + Ember" design system: warm tinted canvas, cards separated by tone, signal
amber `#FFB224` as the one action and rest colour, pill buttons, generous
rounding (12/20/28 dp). Numbers (rest countdown, set values, stat tiles, chart
readouts) are read at a glance and must use equal-width digits.

## Background research (read both)

- [competitors.md](competitors.md): what workout and calm utility apps use. Key
  points: lifting apps keep type invisible (system fonts, or Inter, DM Sans,
  Figtree, Rubik or Poppins on their websites); the brands that care set numbers
  in a separate condensed or DIN-style face (WHOOP uses DINPro for numbers, so
  DIN-like numbers on a dark canvas would read as WHOOP); calm utility apps use
  one neutral grotesque. Condensed numerals from the same family's width axis is
  open space.
- [curator-candidates.md](curator-candidates.md): ten proposed pairings with
  width measurements, script coverage and fit next to Amharic.

## Candidates rendered

Each was rendered on the **real app** (Robolectric screenshot harness, Galaxy
A71-sized phone at 411×914 dp, xxhdpi) with the candidate for every word and
number. Static instances, 400/500/600/700 for words, 500/600/700 for numerals.

| Set | Words | Numerals |
| --- | --- | --- |
| `baseline` | Today: Roboto (system) | Today: sans-serif-condensed Bold (system) |
| `roboto` | Roboto wdth 100 | Roboto wdth 75 |
| `noto` | Noto Sans wdth 100 | Noto Sans wdth 75 |
| `robotoflex` | Roboto Flex wdth 100 opsz 16 | Roboto Flex wdth 72 opsz 36 |
| `archivo` | Archivo wdth 100 | Archivo wdth 75 |
| `fira` | Fira Sans | Fira Sans Condensed |
| `sofia` | Sofia Sans | Sofia Sans Condensed |
| `atkinson` | Atkinson Hyperlegible Next | Fira Sans Condensed |
| `asap` | Asap wdth 100 | Asap wdth 75 |
| `nunitosans` | Nunito Sans wdth 100 opsz 12 | Nunito Sans wdth 75 |
| `instrument` | Instrument Sans wdth 100 | Instrument Sans wdth 75 |
| `googlesansflex` | Google Sans Flex wdth 100 opsz 16 | Google Sans Flex wdth 75 opsz 36 |
| `monasans` | Mona Sans wdth 100 | Mona Sans wdth 80 |

Google Sans Flex and Mona Sans were dropped by the curator as brand-associated
(Google's own UI font; GitHub's brand face) but proposed by the researcher.
Judge them, including that risk.

Sizes, weights, tracking and colours are today's, unchanged. A candidate that
looks too small or too heavy might be fixed by tuning in round 2; say so instead
of rejecting it outright.

## Evidence

- **Comparison sheets** in [round1/](round1/): one screen across every set,
  labelled, at half scale. `<mode>-<screen>.png`; modes are `light`, `dark` and
  `large` (dark at 200% font scale).
- **Full-size screens** for any set:
  `/tmp/claude-1000/-home-cross-workspace-github-com-kidusm001-rebuild-android/d455c089-d14e-4f60-8ab4-1f4f9fb40efe/scratchpad/screens/<set>/<mode>-<screen>.png`.
  Open full size before you judge fine details (digit shapes, colon, small
  labels). There are 31 screens per mode, including today, active today,
  workout, drop set, set editor, history, history detail, plan, more, finish
  dialog, receipt, exercises, exercise history (chart), routines, library,
  phase, body charts, measurements, rest alert, rest-end flash and sweep, alarm
  bar.

Known caveats: Robolectric renders close to a device but not identically
(hinting, antialiasing). `↳` (drops) is missing from every candidate and falls
back to a system font whatever we choose.

## Your task

Assess independently; do not read other critics' review files. Write your review
to the path in your instructions, under 900 words:

1. **Top 3**, ranked, each with one paragraph on why, citing specific screens.
2. **Scores** 0–4 for every set on: `fit` (calm, direct, dependable; Ink +
   Ember), `legibility` (glance-reading mid-set, small labels, dark mode),
   `numerals` (countdown, ledger, tiles: shape, equal width, colon), `large`
   (200% screens: wrapping, clipping, crowding), `distinct` (looks like Rebuild,
   not a clone of a brand or "generic Material").
3. **Eliminate**: sets that should not go to round 2, with the reason.
4. **Round 2 tuning**: for your top 3, what to try (weights, sizes, tracking,
   width axis value, opsz, a different numerals face from another family, etc.).
5. **Problems found** in any screen (clipping, wrapping, glyph fallback,
   misaligned digits), with the file name.
