# Design council review: claude-opus-5.5 (effort high)

This is an independent expert judgment, not a user study. I opened all 18 reference images (`references/R*/…`) and ten Rebuild baseline captures (`B/` means `~/.copilot/session-state/7fe3ff42-fc79-4d02-b465-e3abb277ed6a/files/design-council-baseline/`). I rated only the app UI inside the promotional artwork. I did not exercise any interaction, so the images cannot show tap counts, touch-target size, keyboard behaviour, persistence or dynamic text. The contrast figures below are WCAG arithmetic on token pairs from `color-tokens.json`, not pixel measurements.

## 1. Rubric (0–4; H hierarchy, Lg legibility, A action clarity, O orientation, T theming, F Rebuild fit)

| ID | App (platform · theme) | H | Lg | A | O | T | F | Sum | Coverage | Confidence |
|---|---|---|---|---|---|---|---|---|---|---|
| R1 | Hevy (Android listing, iOS chrome · light) | 3 | 3 | 3 | 3 | 3 | 3 | 18/24 | 6/6 | medium |
| R2 | Strong (Android chrome · dark and light) | 3 | 3 | 4 | 3 | 3 | 3 | 19/24 | 6/6 | medium |
| R3 | Fitbod (Android chrome · dark) | 3 | 3 | 2 | 2 | 3 | 2 | 15/24 | 6/6 | medium-low |
| R4 | Boostcamp (Android listing, iOS chrome · dark) | 2 | 2 | 3 | 3 | 3 | 3 | 16/24 | 6/6 | medium |
| R5 | Alpha Progression (Android listing, iPhone frame · dark) | 3 | 3 | 2 | 2 | 3 | 3 | 16/24 | 6/6 | medium-low |
| R6 | Liftin' (iOS only · dark) | 3 | 3 | 2 | 2 | 3 | 2 | 15/24 | 6/6 | low |
| RB | Rebuild 0.1.3 (native API 34 emulator · dark and light) | 3 | 3 | 3 | 3 | 2 | 3 | 17/24 | 6/6 | medium-high |

For RB, the theming score of 2 combines dark (≈3) and light (≈2). For RB, "fit" means how well it fits its own product constraints. The scores are not a market ranking.

## 2. Per-reference findings

**R1 Hevy** (`01-workout-log.png`, `02-exercise-chart.png`, `03-routines.png`; https://play.google.com/store/apps/details?id=com.hevy)
- Strengths: the Sets / Previous / KG / Reps / ✓ row puts the prior result in grey right beside the bold current value, in the same order. The "Heaviest Weight" chart plots actual load against dated axes.
- Tradeoffs: the Time/Volume/Sets strip and the note compete with the table. In unchecked set 3, the grey "70 / 6" values could be either placeholders or prefilled values.
- Adopt: prior-beside-current alignment for each set. Avoid: volume totals, the PR badge, the One Rep Max selector and exercise illustrations.

**R2 Strong** (`01-log-dark.png`, `02-log-light.png`, `03-progress.png`; https://play.google.com/store/apps/details?id=io.strongapp.strong)
- Strengths: rows have three states that don't rely on colour alone. Completed rows lose their field boxes and gain a filled check, pending rows keep boxed values, and the active field shows an outline and caret. The rest bar (1:37, 0:34) sits inline directly after the set just completed. The light variant keeps surfaces neutral near-white and uses colour only for state: green for done, blue for the timer and Finish, and a pale note band.
- Tradeoffs: rows are dense, so field size for one-handed use is unproven. The keypad/plate area is composite artwork. The chart is estimated 1RM.
- Adopt: state treatment that doesn't depend on colour, and the light theme's accent restraint. Avoid: Est. 1RM, the dense small-field table and Health Connect.

**R3 Fitbod** (`01-workout-overview.jpg`, `02-set-entry.jpg`, `03-strength-progress.jpg`; https://play.google.com/store/apps/details?id=com.fitbod.fitbod)
- Strengths: in S2, completed values are mint, the current row has bright labelled outlines ("Reps", "Weight (lb)") and later rows are subdued. Numbered markers on a vertical line show sequence.
- Tradeoffs: no set-completion control is visible. The media hero pushes the sets down. Italic display numerals are used for data. The strength scores (68, "mSTRENGTH") are opaque derived metrics.
- Adopt: a current row that is labelled and outlined, and future rows that are quieter. Avoid: strength scores, generated plans, the video hero and stylised numerals.

**R4 Boostcamp** (`01-logging-rpe-input.png`, `02-workout-table.png`, `03-exercise-chart.png`; https://play.google.com/store/apps/details?id=com.bpmhealth.boostcamp)
- Strengths: charcoal with yellow, the closest palette to Rebuild's dark theme, holds together (Finish, checks, selected "Daily"). Set-type markers (W/1/2/F/D), labelled Add Set / Swap, and an effort grid with verbal anchors ("Moderate", "Hard") make the actions readable.
- Tradeoffs: five columns plus Target sub-lines and tiny effort superscripts (6.5) are dense. Yellow is also used for exercise titles, which dilutes its role as the action colour.
- Adopt: confirmation that charcoal and yellow work together, and verbal anchors if RIR ever gets a picker (it must stay optional and blank by default). Avoid: % of 1RM targets, the volume/bodyweight chart and yellow on non-actionable text.

**R5 Alpha Progression** (`01-set-logger.png`, `02-plan-days.png`, `03-progress-charts.png`; https://play.google.com/store/apps/details?id=com.alphaprogression.alphaprogression)
- Strengths: the compact "Yesterday · Legs" table under the logger shows the whole prior session in aligned columns. The plan-day tabs (Pull / Push / Legs…) with set/rep chips are easy to scan.
- Tradeoffs: the purpose of the blue target row isn't obvious, the wrench/pencil icons in column headers are cryptic, the thumbnail strip has no labels, and the future-set text has low contrast.
- Adopt: a prior-session block as an aligned mini-table. Avoid: 10RM, "autopilot" progression and thumbnail-only navigation.

**R6 Liftin'** (`01-set-log-rest.jpg`, `02-workout-list.jpg`, `03-progress-calendar.jpg`; https://apps.apple.com/us/app/liftin-gym-workout-tracker/id1445041669)
- Strengths: the elapsed/target rest format ("0:30 / 1:30 MIN") is easy to read, though it's enlarged outside the phone. Workout rows put the load/sets/reps summary right under the name.
- Tradeoffs: the tap target of the rep circle is unclear (a finger covers it), there's no visible entry field, the surfaces are pure black and the UI is illustration-heavy. It's iOS only.
- Adopt: at most the idea of showing remaining time against target. Avoid: PR cards, the calendar highlight used as motivation, and illustrations.

## 3. Cross-reference practices

1. **Show the prior result beside the value being entered, set by set** (R1-S1, R2-S1/S2, R4-S1/S2, R5-S1). Rebuild's single prose line "Last comparable … 42.5 kg × 10 @ 2 RIR, 42.5 kg × 9 @ 2 RIR" wraps and has to be parsed (`B/dark-workout-rest.png`; worse at 200% in `B/dark-entry-200.png`).
2. **Give done, current and upcoming sets distinct states, not by colour alone** (R2, R3-S2, R4-S2, R1-S1). In Rebuild, the logged sets start below the fold under the rest bar.
3. **Keep the accent colour for actions and state** (R2 light, R1). R4 shows the dilution that follows when the accent also colours titles. Rebuild's History detail has seven amber text actions on one screen (`B/dark-history-detail.png`).
4. **Keep rest status close to the task and shown against its target** (R2 inline bar, R4 toolbar Rest, R6 remaining/target). Rebuild's docked bar already does this well.
5. **Use a plain sans with aligned numerals for data** (R1, R2, R5; R3's italic is the counterexample).

## 4. Rebuild direction

**Rebuild baseline observations.** Today has a single clear primary action ("Start Lower B"), labelled alternatives and plain phase text. The workout screen has large labelled kg/Reps/RIR fields, a full-width "Log set", honest "Prefilled from last time" copy, exercise chips with 1/2 counters and a docked rest bar with +time and skip. History detail is read-first with a pencil per set.

Weak points:
- Text buttons ("Change phase", "Add workout note", "Add missed set") are visibly inset from the content edge (about 26 px in the 900-px-wide rendered view), so they look misaligned.
- At 200%, the docked rest bar covers the lower half of "Log set" in the captured scroll position (`B/dark-entry-200.png`, `B/light-entry-200.png`).

**Dark theme:** preserve it. Token pairs are strong: primary on surface 10.8:1, onPrimary on primary 7.7:1. Change only layout and emphasis, not the palette.

**Light theme:** redirect it. The problem isn't contrast (primary on surface is 6.0:1). It's character: olive-brown #7A5A12 filled blocks on beige #FBF8F3 with beige containers read muddy. It should read as the dark identity in daylight:
- Surfaces: near-neutral with only a faint warm tint (for example ≈#FAF9F7, containers stepping to ≈#F3F1EE).
- Primary filled action: charcoal ink (≈#2E2924, 13.7:1 on that surface) with a cream label (#F6E3BE, 11.4:1).
- Amber #E6C27A: used only as a fill behind dark ink (7.7:1), for the selected chip, rest progress and current-set marker. Never as text or as a lone boundary, because it measures 1.6:1 on a light surface.
- Text buttons: ink or a deeper bronze (≈#6B4E0E, 7.3:1).

All of these are illustrative starting values to verify in the app.

**Primary action:** there should be one filled button per screen. Everything else is tonal or text, and fewer of those should be amber.

**Typography:** keep Roboto/system. Use tabular figures for set values. Keep the current type sizes, because the 200% capture reflows acceptably.

**Density:** keep the big entry fields. Don't adopt the table-sized inputs seen in R1, R2 and R4.

**Current and prior sets:** swap the prose line for a compact aligned "Last · Sep 26" list (set N: kg × reps @ RIR), with the row matching "Log set N" emphasised. Move the working-set list (done ✓ / current / upcoming) so it sits right after "Log set".

**Home and History:** Today is already right; just fix the text-button alignment. In History detail, keep "Exercise history" visible and group "Add missed set" and "Add exercise note" under one per-exercise overflow or a single "Edit" affordance.

**Rest controls:** keep the docked bar and its controls, and optionally show "2:57 / 3:00". Reserve the bar's height in the scroll content padding so it never covers "Log set".

**Avoid:** estimated 1RM, strength scores, PR badges, volume totals, exercise media, streaks and pure black.

## 5. Where reasonable reviewers could disagree

- Whether Strong's action clarity earns a 4, given the composite keypad.
- Whether the light theme should use a charcoal primary or just a cleaner bronze. The charcoal route risks making the light UI feel stark or grey.
- Whether moving the logged sets above the target details pushes too much content down on a Galaxy A71.
- RB's scores may be anchored by the richer native evidence behind them, compared with the marketing composites.

## 6. Top changes, ordered by impact

1. **Show last comparable as per-set aligned rows next to the entry.** Risk: comparable-set logic changes, replacement-exercise mapping, missing sets. Check: fixtures with 0, 1 or 2 prior sets and the lat-pulldown replacement; 200% font; TalkBack reads each row.
2. **Redirect the light palette as described in section 4 and leave dark unchanged.** Risk: contrast regressions in unlisted Material roles, and the checkbox or outline visibility. Check: a contrast test over every used role pair, paired screenshots, system theme switching, no forced dark.
3. **Reserve the docked rest bar's height in the scroll content and never cover Log set; optionally show remaining/target.** Risk: IME and landscape insets. Check: 200% font, keyboard open, landscape, the timer surviving process death.
4. **Place set states (done ✓ / current / upcoming) directly after Log set.** Risk: edit-in-place and warm-up flags being lost. Check: editing a logged set, warm-up exclusion, recovery after restart.
5. **Reduce accent noise: align text buttons to the content edge and group History detail's secondary actions.** Risk: less discoverable note and missed-set actions. Check: every action stays reachable with a visible label and a 48 dp target, and the semantics tests still pass.
