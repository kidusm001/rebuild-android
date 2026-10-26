# Rebuild design council review — claude-fable-5.1 (effort high)

Independent assessment from the shared brief, the reference dossier + `references.json`, all 18 reference images opened individually, the frozen baseline packet (manifest, tokens, ten captures; contact sheet as overview only) and Impeccable heuristics. No other reviewer output read. Nothing was installed or exercised; reference scores judge publisher artwork containing app UI, not shipped products.

## 1. Rubric table

| ID | Reference | Hier. | Legib. | Action | Orient. | Theming | Fit | Sum/24 | Coverage | Confidence |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| R1 | Hevy (light, OS unknown) | 3 | 3 | 3 | 3 | 3 | 3 | 18 | 6/6 | medium |
| R2 | Strong (dark + light, Android chrome) | 3 | 3 | 4 | 3 | 4 | 3 | 20 | 6/6 | medium-high |
| R3 | Fitbod (dark, Android chrome) | 3 | 2 | 3 | 3 | 3 | 2 | 16 | 6/6 | medium |
| R4 | Boostcamp (dark, OS unknown) | 3 | 3 | 3 | 3 | 3 | 3 | 18 | 6/6 | medium |
| R5 | Alpha Progression (dark, iOS frame) | 3 | 3 | 2 | 3 | 3 | 3 | 17 | 6/6 | medium |
| R6 | Liftin' (dark, iOS) | 2 | 3 | 2 | 3 | 3 | 2 | 15 | 6/6 | low-medium |
| RB | Rebuild 0.1.3 baseline (dark + light, API 34) | 3 | 3 | 3 | 3 | 2 | 3 | 17 | 6/6 | high |

All rows are evidenced on the six visual axes, so totals are out of 24. Nothing evidences tap counts, 48 dp targets, dynamic text, keyboard or persistence for R1–R6; RB's 200 % captures are real but only two states.

## 2. Per-reference notes

**R1 Hevy** — `references/R1-hevy/01-workout-log.png`, `02-exercise-chart.png`, `03-routines.png`; https://play.google.com/store/apps/details?id=com.hevy
Strengths: (a) grey Previous ("62kg × 7") sits directly left of bold current KG/REPS — comparison in one glance. (b) W / 1 / 2 / 3 marker column and full-width "Add Set" give a stable rhythm; "Start Routine" cards make the home action unambiguous. Tradeoffs: green row fills and blue titles spend accent on finished work; header stats compete with logging; green rows are composite. Adopt: previous-beside-current column; "Heaviest weight" as an actual-load chart metric. Avoid: Explore/social, volume header, PR badges.

**R2 Strong** — `references/R2-strong/01-log-dark.png`, `02-log-light.png`, `03-progress.png`; https://play.google.com/store/apps/details?id=io.strongapp.strong
Strengths: (a) the only reference showing one interface in both themes with identical role mapping (blue action/timer, green done, orange warm-up, yellow note); light changes no layout or meaning. (b) Clearest input state in the set: focused field outlined with caret, pending fields as neutral pills, grey vs. filled check; inline "2:00" indicators keep rest attached to its set. Tradeoffs: two note boxes push the table down; light's pale green rows and grey previous are lower contrast; the numpad region is composite. Adopt: theme role parity, focused-field treatment, remaining-time bar. Avoid: Est. 1RM chart, profile dashboard, Health Connect framing.

**R3 Fitbod** — `references/R3-fitbod/01-workout-overview.jpg`, `02-set-entry.jpg`, `03-strength-progress.jpg`; https://play.google.com/store/apps/details?id=com.fitbod.fitbod
Strengths: (a) progressive emphasis — completed row mint, next row bright-outlined with floating Reps / Weight labels, later rows dimmed; the eye lands on one row. (b) Exercise rows with "4 Sets • 7 Reps • 100 lb" are compact and scannable. Tradeoffs: italic numerals and dimmed rows hurt legibility; the media hero takes about a third of the screen; no previous column; the hexagon check is not obviously tappable. Adopt: done/next/later emphasis ladder. Avoid: strength scores, AI plan, photography hero, italics for data.

**R4 Boostcamp** — `references/R4-boostcamp/01-logging-rpe-input.png`, `02-workout-table.png`, `03-exercise-chart.png`; https://play.google.com/store/apps/details?id=com.bpmhealth.boostcamp
Strengths: (a) closest visual cousin to Rebuild (charcoal + yellow); numbered exercises ("1 Squats (Barbell) >") with a per-exercise rest icon give orientation without a progress widget. (b) Previous/Target cells carry a second line ("RPE 7.5", "2 RIR", "+5 lb") — effort rides beside load without a separate field. Tradeoffs: yellow on titles, checks and Finish at once stops signalling; the corner RPE badge is tiny; five columns plus notes get dense. Adopt: numbered exercises; two-line previous cell with RIR; distinct W/F markers. Avoid: RPE grid, Calc, "% of 1RM" targets, accent on titles.

**R5 Alpha Progression** — `references/R5-alpha-progression/01-set-logger.png`, `02-plan-days.png`, `03-progress-charts.png`; https://play.google.com/store/apps/details?id=com.alphaprogression.alphaprogression
Strengths: (a) the compact "Yesterday · Legs" table under current sets is the best prior-session presentation here: same column order as entry, quiet contrast, three rows in the space Rebuild spends on one sentence. (b) Plan editor with day tabs and "Day 2 · 6 exercises · 19 sets" is a clean, non-gamified plan view. Tradeoffs: wrench/pencil glyphs on most headers blur what is editable; 10RM adds a derived number beside actual data; pill values read as buttons. Adopt: prior-session mini-table; plan day tabs. Avoid: 10RM/autopilot progression, thumbnail strip.

**R6 Liftin'** — `references/R6-liftin/01-set-log-rest.jpg`, `02-workout-list.jpg`, `03-progress-calendar.jpg`; https://apps.apple.com/us/app/liftin-gym-workout-tracker/id1445041669
Strengths: (a) the "0:30 / 1:30 MIN" readout shows remaining and total together — transferable even though its in-app position is unproven. (b) List rows ("150 lbs, 3 sets, 6 reps") use large plain numerals. Tradeoffs: how a set is logged is not evidenced (finger and callouts obscure it); iOS chrome and pink brand are not an Android reference; per-side plate math is irrelevant for a machine-heavy program. Adopt: remaining/total rest format; calendar dots as a light history glance. Avoid: PR cards, one-tap claims, iOS styling.

**RB Rebuild 0.1.3** — `~/.copilot/session-state/7fe3ff42-fc79-4d02-b465-e3abb277ed6a/files/design-council-baseline/{dark,light}-{today,workout-rest,history-detail,history,entry-200}.png`
Strengths: (a) the task hierarchy is already right: labelled outlined kg / Reps / RIR fields, one filled "Log set", explicit warm-up checkbox, exercise chips with 1/2 counts, "Working sets: 1 of 2", persistent rest bar with progress line; 200 % reflows without clipping the primary action. (b) Copy is honest ("Prefilled from last time; tap Log set to save"), and history keeps actual values with edit pencils. Tradeoffs: nearly every secondary action ("Next:", "Target details", "Change phase", "Alerts off · settings", "Add missed set") is an amber text button, diluting the accent meant for Log set/Finish; the prior result is a wrapping sentence, not a comparable row; surfaces are almost flat, so entry, context and logged rows are ungrouped. Theming is 2 because light is a shipped state: #FBF8F3 beige with a #7A5A12 filled button reads olive-brown, the tan rest bar merges with the page, and the primary changes character (pale amber pill in dark, brown slab in light) instead of keeping the role parity R2 shows.

## 3. Cross-reference practices

1. **Previous beside current, aligned, not prose** — R1-S1, R2-S1/S2, R4-S2, R5-S1. Four of six align prior load × reps with the entry columns; RB's sentence forces re-reading.
2. **Done / next / later emphasis ladder** — R3-S2, R2-S2, R5-S1. Exactly one row reads as "now".
3. **Rest attached to the set, remaining and total visible** — R2 inline bars, R4 toolbar, R6 "0:30 / 1:30". RB's bar is persistent but lacks the total.
4. **Accent scarcity** — R2 (blue only for Finish/timer/active) reads calmer than R4 (yellow everywhere). RB sits on the R4 side.
5. **Position as plain text** — R4 numbering, R5 day tabs, RB chips. Counts suffice; no progress ring needed.

## 4. Bounded Rebuild direction

**Preserve:** dark tokens (#171410 / #E6C27A), rounded-outline icons, bottom navigation, screen order, filled "Log set", outlined labelled fields, optional RIR, warm-up checkbox, persistent rest bar, editable history, all semantics/IME/landscape guards.

**Change:**
- *Light surfaces:* reduce beige chroma toward a near-white warm neutral; render the primary action as an amber container + dark text (tonal button) so "Log set" is the same object in both themes; a darker, cleaner brown only for surviving text links. Do not force dark or remove light.
- *Primary-action emphasis:* only Log set, Finish and the current exercise chip carry primary colour; other links become onSurfaceVariant text or outlined chips.
- *Typography:* keep Roboto; enable tabular numerals for kg/reps/time; logged set values one step larger than labels.
- *Density:* shrink the "Prefilled from last time" line to a caption so logged rows return above the fold at 100 %.
- *Current/prior sets:* replace the "Last comparable" sentence with a two-to-three-row mini-table (Set · kg · reps · RIR) aligned to the entry fields, and show logged sets as rows with a tertiary check.
- *Home/history:* home adequate; history rows gain a one-line exercise summary; detail unchanged apart from grouping.
- *Rest controls:* "Rest 2:57 / 3:00", digits tabular; keep icon buttons, confirm labels.

**Avoid:** 1RM/10RM/strength scores, RPE grids, PR badges, gamified calendars, photo heroes, italic data type, copying any reference's layout or brand.

## 5. Expected disagreements

- R2 theming 4 vs 3: a reviewer weighting the washed light-green rows would give 3.
- R3 hierarchy: some will score the emphasis ladder 4 and discount the hero; I penalised the hero.
- RB theming as a single row: splitting would give dark 3 / light 2; the shared row honours the user's dark preference without excusing the light theme.
- Whether a prose prior-set line is fine for a two-set program; I think the table wins once RIR is present.
- R6's low fit is partly a platform judgment, not a quality judgment.

## 6. Highest-value changes (ordered)

1. **Light token retune** (theme only): tonal primary button, lower-chroma surfaces, darker link colour. *Risk:* contrast regressions on onPrimaryContainer, error/tertiary roles left inconsistent, screenshot tests. *Check:* contrast for every text-on-container pair, paired light/dark captures of today/workout/history.
2. **Prior-set mini-table replacing the prose line.** *Risk:* 200 % wrapping, TalkBack reading a table as fragments, landscape width. *Check:* entry-200 both themes, TalkBack traversal order, landscape capture.
3. **Accent discipline for secondary links.** *Risk:* discoverability of Change phase / Target details; targets must stay ≥ 48 dp. *Check:* existing UI tests by label, semantics roles unchanged.
4. **Surface grouping + logged-set rows with done indicator.** *Risk:* nested scroll and IME inset regressions under the rest bar. *Check:* keyboard-open capture, rest bar remains visible, recovery after rotation.
5. **Rest bar "remaining / total" with tabular digits.** *Risk:* overflow at 200 %. *Check:* entry-200 both themes; timer persistence unchanged.
