# Round 2 review: platform and shipping

Measured with fontTools on `fonts-dl/Archivo-VF.ttf` (widths use `tnum`, no kerning, as a % of today's Roboto Condensed Bold). Scripts are in `scratchpad/platform/w2.py` and `s2.py`.

## Final pick: Archivo

- **Words**: Archivo `wdth 100`, `wght` 400/500/600/700, unchanged from the renders.
- **Numerals**: Archivo **`wdth 78`, `wght 700`**, between the two rendered widths. Declare numerals at 700 only, which matches today's family, where every numeral style already draws Bold.

| Numerals | 2:50 | 1:08:42 | +0:01 | 107.5 kg × 12 | 42.5 kg/DB × 10 | 12,480 |
|---|---|---|---|---|---|---|
| w75 / 700 | 95 | 95 | 99 | 92 | 93 | 96 |
| **w78 / 700** | **98** | **98** | 102 | **95** | **96** | **98** |
| w80 / 700 | 100 | 100 | 104 | 97 | 98 | 100 |
| w75 / 800 | 98 | 98 | 102 | 95 | 95 | 99 |

`wdth 78` opens the ledger most of the way to w80. It stays under today's width everywhere except the short alarm-bar `+0:01` (102%), which has plenty of room (`light-23`). At w80, `42.5 kg/DB × 10` already runs up against the pencil button (`dark-03c`).

If the visual critics want more weight, `wdth 78 wght 750` measures 96–100% (103% for +0:01) and is still safe. I would not use 800: it adds no width safety, and it leans further toward DIN.

**Ship form**: one subset variable file for words (wght 400–700 at wdth 100, used through four `Font(…, variationSettings)` entries) plus one static numerals file. That is **64 KB deflated** for Latin, Latin-1, Ext-A and punctuation. As a fallback, if the variable path misbehaves in Robolectric, ship five static files: 101 KB. Both beat Roboto Flex (124 KB) and Fira (307 KB).

Coverage: full Latin-1 and Ext-A, `· – × − … — “ ” › NBSP`. Missing: `● ○ ↳`, Cyrillic and Greek. Licence: OFL with no Reserved Font Name, so a modified subset may keep its name.

## Runner-up: Roboto Flex (wdth 72, opsz 36, wght 700 numerals)

It should win only if exercise names in **Cyrillic or Greek have to render in the app's own face**. Archivo is Latin only, so those names would fall back to the phone's font, which is the mixing this change is meant to end.

## Scores (fit / legibility / numerals / large / distinct)

| Set | fit | leg | num | large | dist |
|---|---|---|---|---|---|
| roboto | 3 | 3 | 3 | 3 | 1 |
| robotoflex | 3 | 3 | 4 | 4 | 2 |
| archivo | 3 | 3 | 3 | 4 | 3 |
| archivo-w80 | 3 | 3 | 4 | 4 | 3 |
| archivo-w75-800 | 3 | 3 | 3 | 4 | 2 |
| fira | 3 | 3 | 2 | 2 | 3 |

Roboto's `large` drops to 3 because `42.5 kg/DB × 10` wraps in `dark-03c`. The baseline and Fira wrap there too.

## Must-fix before shipping

1. **The word space is too narrow in the numerals.** Archivo's space is 0.30 of a digit against today's 0.46, so `42.5kg/DB` runs together (`dark-03c`, worst in w75-800). In the subset build, widen U+0020 and U+00A0 in the numerals file to about 0.40 of a digit. The OFL allows this because Archivo has no Reserved Font Name.
2. **`●` and `○`** in the chart legends (`Charts.kt` 306) fall back to a system glyph. Draw them as shapes, and draw **`↳`** (`SetGroupsUi.kt`) as a vector.
3. **Licence**: ship `OFL.txt` as `assets/licenses/archivo-OFL.txt`. `res/raw` is stripped by `shrinkResources`, and that already happens to `lucide_license.txt`.
4. **Tests**:
   - Archivo's default digits are proportional, so assert that every Typography style is the bundled family and carries `tnum`.
   - Add a `Paint.hasGlyph` test over the app's literal symbols.
   - Pixel-diff the variable path against statics in the harness.
5. **Existing clipping**: stat tile labels clip with no ellipsis at 200% ("Workin", "Exercis" in `large-05`, every set). Fix it, or check that it doesn't get worse.
6. **On the A71**: confirm that One UI's font style no longer reaches app text, and look at an Amharic name and a Cyrillic one, the second being the fallback case.

## Did round 2 change my mind?

Yes. I moved Archivo from second to first for two reasons. `dark-03c` shows it fixing a wrap that Roboto, Fira and today's build all have. And as a words variable file plus one numerals static, it ships at about half Roboto Flex's size, with no optical-size or grade axes to get wrong.
