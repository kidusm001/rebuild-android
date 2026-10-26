# Rebuild design council: independent Astra assessment

**Reviewer:** gpt-6-astra, high effort. **Date:** 2026-09-28.

## Basis and verdict

Strong is the most useful **reference for Rebuild**, principally for comparative set data and entry-state differentiation. Rebuild already has a better-fitting product scope than the competitors' dashboards. Improve its presentation, not its purpose.

I individually inspected all 18 reference images and all 10 native baseline images. Scores concern app UI inside the artwork, excluding marketing headlines, frames, enlarged callouts and illustrated fingers. I exercised no interactions. This is expert judgment, not a user study, accessibility certification or market ranking.

References were collected September 27; depicted versions and creation dates are unknown. R1/R4/R5 are Android-facing listings with unverified depicted OS; R2/R3 have Android-style chrome; R6 is iOS style evidence only. Light appears in R1/R2, dark in R2–R6; missing themes are not missing product capabilities.

## Ratings

H = hierarchy; L = legibility; A = action clarity; O = orientation; T = theming; F = Rebuild fit. Each axis is 0–4; 3 means strong, 4 exceptional.

| ID / app | H | L | A | O | T | F | Total | Coverage | Confidence |
|---|---:|---:|---:|---:|---:|---:|---:|---|---|
| R1 Hevy | 3 | 3 | 3 | 3 | 3 | 3 | 18/24 | 6/6 | Medium |
| R2 Strong | 3 | 3 | 3 | 3 | 3 | 4 | 19/24 | 6/6 | Medium |
| R3 Fitbod | 3 | 3 | 2 | 3 | 3 | 2 | 16/24 | 6/6 | Medium |
| R4 Boostcamp | 2 | 2 | 3 | 3 | 3 | 3 | 16/24 | 6/6 | Medium |
| R5 Alpha Progression | 3 | 2 | 3 | 3 | 3 | 3 | 17/24 | 6/6 | Medium |
| R6 Liftin’ | 3 | 3 | 2 | 3 | 3 | 2 | 16/24 | 6/6 | Low |
| RB Rebuild 0.1.3 | 3 | 3 | 3 | 4 | 2 | 4 | 19/24 | 6/6 | High, visual only |

All six *visual* axes are evidenced in every row. Complete coverage does not imply complete state coverage. Missing behavioral evidence remains unknown, not zero.

## Individual assessments

### R1 Hevy

**Strengths:** The in-phone logger separates Previous, KG and Reps, with a plainly named exercise and rest setting. The routine page distinguishes creation/exploration from named routines, using quieter utility controls and filled Start actions.

**Tradeoffs:** The unfinished row is noticeably faint, making planned values resemble unavailable controls. Several blue elements compete, including Finish, exercise naming and rest configuration. **Borrow:** labeled comparison and restrained light surfaces. **Avoid:** promotion-heavy entry points and the assumption that a record metric belongs on Today. The enlarged green rows, chart and routine card do not establish native sizing; the in-phone metric selectors are observable, but their alternate results are not.

Evidence: `references/R1-hevy/{01-workout-log.png,02-exercise-chart.png,03-routines.png}`. [Publisher source](https://play.google.com/store/apps/details?id=com.hevy&hl=en_US).

### R2 Strong

**Strengths:** Aligned Previous/KG/Reps columns allow direct comparison without paragraph parsing. Both themes distinguish editable numeric cells from completed rows; the light example shows an explicit caret/focus border, and the dark example locates a time bar near the next row.

**Tradeoffs:** Completed previous values become very muted. Notes and accumulated completed rows can compete with the active set. **Borrow:** comparative alignment, localized input emphasis and comparable light/dark structure. **Avoid:** estimated-1RM dashboard semantics and copying the custom plate-entry region. Its promotional overlay does not verify IME behavior. The enlarged progress chart is excluded from native-layout scoring.

Evidence: `references/R2-strong/{01-log-dark.png,02-log-light.png,03-progress.png}`. [Publisher source](https://play.google.com/store/apps/details?id=io.strongapp.strong&hl=en_US).

### R3 Fitbod

**Strengths:** Bright outlines isolate the current pair of fields from completed mint values and subdued later rows. The workout overview consistently pairs exercise names with concise sets/reps/load summaries.

**Tradeoffs:** Large exercise media precedes the data task. Numbered markers, completed checks and outlined fields communicate state better than they communicate the exact completion action. **Borrow:** current-versus-later emphasis and compact summaries. **Avoid:** making photographs, strength scores or inferred coaching central to logging. The score dashboard has coherent grouping, but its numbers are not raw training records. History and rest chips are visible affordances, not exercised flows.

Evidence: `references/R3-fitbod/{01-workout-overview.jpg,02-set-entry.jpg,03-strength-progress.jpg}`. [Publisher source](https://play.google.com/store/apps/details?id=com.fitbod.fitbod&hl=en_US).

### R4 Boostcamp

**Strengths:** Previous, Target and actual input columns distinguish evidence from prescription. Named Rest/Calc controls and selected chart tabs make utilities and location understandable.

**Tradeoffs:** Numerous columns, effort annotations, notes and toolbar actions produce the busiest logging surface here. Small gray secondary values make the comparison harder than its structure suggests. **Borrow:** explicitly separate target from actual, without importing the entire table. **Avoid:** calculator/RPE complexity and dual-axis volume/bodyweight charts for this brief. RPE entry is not optional-RIR entry; “2 RIR” appears in previous performance. The title/illustration mismatch is illustrative-content uncertainty, not proof of a shipped defect.

Evidence: `references/R4-boostcamp/{01-logging-rpe-input.png,02-workout-table.png,03-exercise-chart.png}`. [Publisher source](https://play.google.com/store/apps/details?id=com.bpmhealth.boostcamp&hl=en_US).

### R5 Alpha Progression

**Strengths:** “Yesterday · Legs” gives previous values a useful session identity. The day editor separates day selection, exercise prescriptions and a prominent Save action.

**Tradeoffs:** Prior-session values and metadata are very muted. A blue target row and 10RM column demand interpretation beyond actual weight/reps; thumbnail-only exercise navigation also supplies less explicit identity than text. **Borrow:** a dated, compact previous-session block and a clear commit action. **Avoid:** derived-10RM emphasis, inferred automatic progression and importing the chart dashboard. Separately labeled chart metrics are conceptually honest, but do not demonstrate per-set actuals.

Evidence: `references/R5-alpha-progression/{01-set-logger.png,02-plan-days.png,03-progress-charts.png}`. [Publisher source](https://play.google.com/store/apps/details?id=com.alphaprogression.alphaprogression&hl=en_US).

### R6 Liftin’

**Strengths:** Exercise rows combine clear names with concise numerical summaries rather than nested controls. Set lines distinguish total load from per-side load, making unit meaning explicit.

**Tradeoffs:** Circular rep badges do not independently explain whether a set is planned, active or complete; the advertising claim cannot resolve that. Per-side diagrams and Weight PR cards privilege a barbell/record-oriented use case. **Borrow:** readable summaries and clear unit wording. **Avoid:** iOS-specific chrome, record-first history and ambiguous badge-only completion. The enlarged timer, Add button and calendar treatment are not native-placement evidence, so they receive no layout credit.

Evidence: `references/R6-liftin/{01-set-log-rest.jpg,02-workout-list.jpg,03-progress-calendar.jpg}`. [Publisher source](https://apps.apple.com/us/app/liftin-gym-workout-tracker/id1445041669).

### RB Rebuild

Baseline directory: `~/.copilot/session-state/7fe3ff42-fc79-4d02-b465-e3abb277ed6a/files/design-council-baseline/`. Evidence: paired `dark-`/`light-` versions of `today.png`, `workout-rest.png`, `history-detail.png`, `entry-200.png` and `history.png`; provenance and tokens come from `manifest.json` and `color-tokens.json`, commit `d93fac1`.

**Strengths:** Start and Log set have unmistakable priority. Workout, phase, selected exercise, working-set count, rest and dated actual history establish unusually explicit orientation.

**Tradeoffs:** “Last comparable” is a long sentence rather than easily scanned set data. Repeated target/help/action blocks consume height; at 200%, Log set reaches the timer boundary. This shows viewport pressure, not proof of an unreachable button.

**Dark:** Preserve `#171410` and `#E6C27A`, rounded-outline icons and the calm unboxed structure. The bright bottom gesture band visibly breaks the dark surface; its cause is unverified.

**Light:** Beige backgrounds, tan selection/rest surfaces and brown actions are coherent in hue but weakly differentiated in role and contrary to the user's preference. This drives the combined theming score, not an alleged contrast failure. Borrow Rebuild's explicit labels; avoid preserving its prose-heavy comparison merely because the data is accurate.

## Transferable patterns

1. **Context belongs beside entry:** R1/R2/R4 align previous data; R5 supplies a dated block. Preserve provenance, not just numbers.
2. **State needs structure plus color:** R2 fields/checks and R3 outlines/markers distinguish editable, current and completed states.
3. **Lists can carry substantial meaning:** R3/R6 summarize exercises without dashboard scaffolding; RB's history list already follows this principle.
4. **Metric labels are contracts:** R2 names estimated 1RM, R5 names 10RM, R4 names volume/bodyweight. For Rebuild, keep actual weight, reps and optional RIR instead.

## Bounded direction and priorities

A returning lifter reads a Galaxy A71 between sets under variable gym lighting. Keep system-aware themes, native text scaling, one-handed actions and explicit logging. Use a near-neutral light canvas with dark ink, reserving warmth for selected/action details; do not recolor dark to match. Keep one system font, role-based weight, aligned numbers, moderately denser read-only content and generous interactive targets. Today stays a launcher; History stays a record.

1. **Replace comparison prose with dated, labeled set lines adjacent to entry.** Keep current editable values visually distinct. Risk: wrong set matching or losing RIR/provenance. Check comparable/noncomparable sessions, decimals, differing set counts, warm-ups and optional-RIR absence; reflow rather than shrink at 200%.
2. **Retune light semantic colors only.** Neutralize canvas, containers and outlines; try dark-ink primary actions with restrained amber selection detail rather than brown everywhere. Risk: low contrast or theme leakage. Check actual text/control contrast, disabled/error/focus states and paired screenshots; preserve dark identity.
3. **Clarify the persistent rest strip.** Keep countdown prominent; provide visible, wrapping labels for add-time and skip, with alerts status secondary but present. Risk: IME/landscape overlap or timer reset. Check keyboard, 200% text, landscape, background/recreation and denied alerts; retain all existing guards.
4. **Tighten spacing, not fonts or targets.** Reduce repetitive help/target spacing; keep Log set dominant and current result nearby. Risk: lost warm-up distinction, misleading completion or focus order. Check one-tap prefill without auto-save, edits, recovery and baseline/current/optional-upper caps.
5. **Quiet supporting home/history controls.** Retain manual phase choice and alternative workouts; consolidate repeated note/add-missed actions without hiding set correction. Risk: confusing today's changes with future-plan edits. Check both scopes, historical accuracy, navigation semantics, TalkBack and backup/replacement safeguards.

## Uncertainty

Reasonable reviewers may prefer R5's block to R2's table, or judge Rebuild's spaciousness more valuable than density. These are tradeoffs, not consensus. No reference establishes 48dp targets, scalable text, persistence or keyboard safety. Native baseline captures establish only selected portrait states with keyboard closed, not physical-device usability. No wholesale screen copying or new analytics, social, gamification or coaching is recommended.
