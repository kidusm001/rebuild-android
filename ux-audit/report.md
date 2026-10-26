# Rebuild native Android UI/UX critique

Audit date: 27 September 2026. Scope: the unchanged, installed Android MVP, not a proposed redesign.

## Verdict

**Rebuild is a functional, trustworthy MVP, but it is not yet a polished one-handed gym logger.** The visual system is clean and appropriately native. The biggest problems are not a need for a new palette or fashionable components: large-text layout breaks, keyboard/system-inset occlusion, and too much reference material on the repeated logging path. It would be misleading to call the current UI universally "best."

**Anti-patterns verdict: pass on the obvious AI-template tells.** There are no decorative gradients, glass panels, hero metrics, marketing dashboards, or arbitrary gamification. Restrained warm neutrals, amber actions, native typography and Material controls suit a person checking sets between exercises. Repeated explanation and action-colored headings still make the interface feel like a careful form rather than a refined tool.

No app code was changed, no new APK was built, and no design change was applied. Recommendations below require separate implementation approval.

## Scope, evidence, and independence

Source: `app/src/main/java/com/rebuild/ui`, plus the hosting activity and manifest.

Baseline:

- Branch: `kidusm001-rebuild-android-mvp`
- Commit: `ea996eb5a23c4de9134b102d76ebd101b2bec29f`
- Installed/delivery APK SHA256: `afa20b6820ef9a2d09a263552b838a56ba7b21b6bc2825ae8e08413a0bead872`
- Native API 33 x86_64 emulator `emulator-5590`, private AVD, two cores and 2 GiB, offline, synthetic data only.
- 62 new PNG captures, each paired with a UIAutomator XML tree, density/font/theme JSON and an input-method dump. `evidence-manifest.json` records actual dimensions and image hashes. `interactions.jsonl` records performed actions.

Assessment A was an independent design review by agent `7752da33-ab1f-420e-9c0c-02cad289c7ca`, **claude-opus-5.5 / high**, using source and neutral previous delivery screenshots. It received neither detector findings nor this native evidence. Its preliminary Nielsen score was 28/40. The parent exclusively controlled the emulator for Assessment B. A finished before the detector attempt and synthesis.

The real, single detector invocation was:

```text
node ~/.agents/skills/impeccable/scripts/detect.mjs \
  --json app/src/main/java/com/rebuild/ui
```

It exited 1 with **`Error: bundled detector not found.`** Therefore there are no usable detector counts, no confirmed detector findings and no clean-scan claim. It did not analyze Kotlin. Native Compose has no Web DOM; no web mock, browser overlay, script injection or visualization server was used. Actual Android screenshots, platform IME dumps, native semantics and source inspection replace that web-only portion.

The synthesis corrects several preliminary A assumptions: kg entry really uses a decimal keyboard; an old letter-keyboard image was from note entry; IME Done saves and dismisses the keyboard; a visible prefilled Log set already permits a one-tap repeat. Minimum touch size is not claimed universally from component intentions.

### Skills applied

Impeccable was invoked. Its context loader, PRODUCT/DESIGN, product register, critique, audit, heuristics, cognitive-load and persona references informed the assessment.

Two centrally installed skills were incorporated into native Assessment B only:

| Skill | Provenance supplied by installer | Applied criteria |
|---|---|---|
| `edge-to-edge` | Google LLC, `android/skills` commit `42dc2270e96032bd860bb94511e440aa00a43125`, Apache 2.0 | Activity setup, Scaffold inset consumption, IME visibility, horizontal navigation-bar protection, system-bar icon legibility |
| `mobile-android-design` | `wshobson/agents` commit `9b15b34b0bfc13a815cbfc2366e14ea549e09422`, MIT | Native Material theming, 48 dp targets, adaptive layout, field errors, progress, navigation/back behavior |

The runtime's advertised skill list did not include these new registrations, so their installed `SKILL.md` files and relevant `details.md`, `android-navigation.md`, `compose-components.md`, and `material3-theming.md` sections were read directly. No tool invocation for either new skill is claimed. No duplicate discovery/installation occurred. Generic sample dependencies, newer ruler APIs, dynamic color, skeletons, cards and tablet navigation were not treated as mandatory changes to this approved phone product.

## Design health score

These are **subjective expert judgments**, not a usability study, an accessibility certification, or a percentage of users who will succeed. Scale: 0 poor/absent through 4 excellent.

| # | Nielsen heuristic | Score / 4 | Main evidence |
|---|---|---:|---|
| 1 | Visibility of system status | 2 | Counts and saved rows are clear, but the running rest panel is covered by the IME. Saving is mainly communicated through disabled controls. |
| 2 | Match with the real world | 3 | Working sets, reps and kg are natural. RIR and advisory limits need context, which the app supplies rather verbosely. |
| 3 | User control and freedom | 2 | Notes/back/cancel safeguards work, but the large-text restore buttons overlap and partial-finish reassurance is clipped. |
| 4 | Consistency and standards | 3 | Coherent native components and navigation; amber headings resemble actions and notes have different presentations in workout/history. |
| 5 | Error prevention | 3 | Explicit logging, safe prefills, truthful partial finish, today-only substitution and mandatory pre-restore backup are strong. |
| 6 | Recognition rather than recall | 3 | Comparable history, prefilled values and progress chips reduce memory load. Long chips and the note icon are less discoverable. |
| 7 | Flexibility and efficiency | 2 | One-tap prefilled logging and real Next/Done work; scrolling and keyboard switching remain configuration-dependent costs. |
| 8 | Aesthetic and minimalist design | 2 | Calm styling, but repeated explanation and always-editable history compete with the actual task. |
| 9 | Error recognition and recovery | 3 | Specific recovery text preserves entries; it is not attached to the invalid field. |
| 10 | Help and documentation | 3 | Good phase/loading/training guidance, but too much remains permanently on the repeat path. |
| | **Total** | **26/40** | **Acceptable: significant usability improvements needed** |

The two-point change from A's preliminary 28 is driven by measured status occlusion and large-text control failures, not a second aesthetic opinion.

## Technical audit health

Web criteria were translated to native Android semantics, dp targets, IME and window insets. They were not applied as HTML/ARIA requirements.

| Dimension | Score / 4 | Evidence and limit |
|---|---:|---|
| Accessibility | 2 | Strong sampled contrast and many labeled/merged semantics, but large-text failures and incomplete field-specific error semantics. TalkBack speech was not tested. |
| Performance | 2 | Provisional: no frame benchmark or physical-device profiling. History uses eager collections; the already-measured near-cap cold read is slow but visibly loading. |
| Responsive design | 1 | Confirmed 200% font reflow, overlapping restore controls, IME occlusion and landscape navigation-inset problems. |
| Theming | 3 | Light/dark look coherent and sampled token pairs are strong; not every disabled/composited/system state was exhaustively checked. |
| Anti-patterns | 3 | No dashboard/glass/gradient template, but repeated defensive copy and competing emphasis remain. |
| **Total** | **11/20** | **Acceptable: significant work needed in weak dimensions** |

The performance score is a bounded assessment of known risks, not a measured frame-rate result. The five dimensions are not equally precise.

## Priority findings

Five main findings: **0 P0, 2 P1, 3 P2**. Three P3 observations appear separately below. Related manifestations are grouped rather than counted as separate defects.

### 1. P1: Large text breaks the rest panel and important dialogs

**Location:** `ui/RestBar.kt:56-88`; `ui/Components.kt:54-91,123-154`; `MainActivity.kt:98-129`.

**Evidence:** At 360 dp width and font scale 2.0, the nonwrapping rest-action row compresses "Battery limits · fix" into roughly seven short lines. The rest-colored region occupies about 1,338 of 2,400 vertical pixels, over half the screen, leaving very little workout body. The app subtitle also clips in the fixed-height top bar. At approximately 411 dp/2.0, the rest region is smaller but still consumes roughly 29% of the screen.

The restore preview has a separate measured collision at 360 dp/2.0: "Save current backup" clickable bounds are `[132,1716][948,1988]`; Cancel is `[632,1912][948,2072]`. They overlap by **76 px, approximately 25.3 dp vertically**, confirmed visually. This is not just an oversized screenshot or a preference about whitespace.

The partial-finish dialog leaves its buttons visible but clips the final reassurance about preserving only logged sets. Its text container is not scrollable. The restore preview body is scrollable; that does not repair the button overlap.

**Screenshots:** `36-light-narrow360-font200-rest.png`, `28-narrow360-font200.png`, `48-restore-preview-large.png`, `49-restore-preview-large-scrolled.png`, `30-narrow360-font200-finish.png`.

**User impact:** Low-vision users lose the logging viewport and the visual separation between a safety action and Cancel. Important explanation disappears at the exact moment they need confidence.

**Minimal recommendation:** Reflow rest actions into bounded, readable rows or an adaptive compact panel with secondary settings behind an explicit action. Keep the limitation/remedy visible without forcing it into a tiny column. Stack dialog actions without overlap, allow confirmation bodies to scroll, and make header height/content adaptive. Do not shrink user-selected text or remove backup safeguards to make it fit. Suggested skill direction: `impeccable adapt`.

**Standard:** Android scalable text/adaptive layout. These observations correspond to resize/reflow concerns, but are not a claim of a completed WCAG conformance evaluation.

### 2. P1: The rest controls are obscured by the keyboard and landscape system navigation

**Location:** `ui/RebuildApp.kt:123-151`; `ui/RestBar.kt:56-88`.

**Evidence:** Only the Scaffold content receives consumed inner padding and `imePadding()`. The custom rest bottom bar does not. Opening the real numeric keyboard during a running rest covers that panel. In verified landscape, the `+30 s` label and end of the progress track extend under the right-side Android navigation region.

**Screenshots:** `34-narrow360-keyboard.png`, `27-dark-font200-keyboard.png`, `38-landscape-verified.png`, `41-landscape-keyboard.png`. `03-keyboard-existing-weight.png` also shows the entry/Log separation with IME present.

**User impact:** The user must dismiss the keyboard to inspect or adjust rest, then return to editing. This is an important repeated cost for a one-handed lifter. The durable timer still runs; this finding is about access and visibility, not lost deadlines.

**Minimal recommendation:** Give the custom rest controls an explicit IME and horizontal safe-inset strategy, preferably a compact above-IME status/action layout. Consume insets once; simply moving the entire already-tall panel above the keyboard would worsen finding 1. Preserve the current Next/Done behavior. Suggested skill direction: `impeccable adapt`, informed by `edge-to-edge`.

**Native checklist result:** `ComponentActivity.enableEdgeToEdge()` and manifest `adjustResize` are already present. Standard navigation/top bars largely handle themselves; the custom bottom bar is the gap. No SDK or Compose upgrade is required merely to diagnose it.

### 3. P2: Reference material and a growing table push the repeated action out of view

**Location:** `ui/WorkoutScreen.kt:192-245,301-380`; `ui/SetEditing.kt:90-94`.

**Evidence:** The content order puts exercise/loading/target/rest/baseline/advisory explanation, Adjust today, comparable results and completed rows ahead of the entry form. At 360 dp/normal font with two logged sets and a running rest, the top position has no visible kg/reps or Log button. One body swipe exposes them. At 411 dp/normal font, the first returning prefilled Log button can already be visible without a mandatory swipe. These cases must not be conflated.

After the chosen target is met, Log set remains the strongest action while Next follows the note area. Additional truthful logging must stay possible, but it need not visually dominate the usual next step.

**Screenshots:** `18-returning-prefill.png`, `20-after-first-returning-log.png`, `22-target-complete-rest.png`, `32-narrow360-top.png`, `33-narrow360-entry.png`.

**User impact:** The stable repeated task moves as the reference/table grows. A returning lifter pays a scanning/scrolling cost for explanations already understood.

**Minimal recommendation:** Keep comparable performance and entry together; compress the target/baseline/advisory information into a concise secondary summary with accessible detail. Use "RIR (optional)" in the field treatment rather than an extra permanent sentence. Put Next before optional notes, with a clear secondary path to log extra sets. Preserve all distinctions and never mark prefills completed automatically. Suggested skill direction: `impeccable distill`.

### 4. P2: Validation identifies the problem but does not identify the field semantically or visually

**Location:** `ui/SetEditing.kt:36-94`; `ui/Components.kt:116-119,156-180`; `ui/WorkoutScreen.kt:301-380`; `ui/ActionRunner.kt:33-54`.

**Evidence:** Submitting an empty set shows "Enter the reps you completed (1 or more)." The message is specific and entries are retained. But `SetFields` does not wire `NumberField.isError` or supporting text to the failing input. `ErrorText` is plain colored Text without explicit error/live-region semantics. The save path mainly disables controls while busy, without a distinct "Saving..." state.

**Screenshot:** `09-empty-validation-error.png`. Additional evidence is source-level; no claim is made that TalkBack actually stayed silent.

**User impact:** A low-vision or screen-reader user may need to search for the error and associate it with the correct field. A brief disabled state can look like an unresponsive control rather than a save in progress.

**Minimal recommendation:** Associate each validation result with the relevant field, preserve the typed value, provide appropriate error semantics and bring/focus the invalid field into view. Add a concise save status when latency is noticeable. Announce important completion/error changes politely, not the timer every second. Suggested skill direction: `impeccable harden`.

### 5. P2: Finished-workout history is presented as an editing form

**Location:** `ui/HistoryScreens.kt:106-163,258-305`.

**Evidence:** Each exercise immediately exposes an empty note field, Add missed set and Exercise history, even when the user only wants to read what happened. These repeated controls reduce how many actual results fit on screen. Charts correctly show actual kg/reps with textual results, but 11 sp axis labels and small points are hard to scan compared with the accompanying text.

**Screenshots:** `15-workout-history.png`, `16-exercise-chart.png`, `53-two-workouts-after-cancel.png`.

**User impact:** Reviewing the completed workout feels like unfinished paperwork and requires more scrolling than the logged data itself warrants.

**Minimal recommendation:** Render notes read-first, expose an explicit Edit/Add note action, and make correction actions secondary without hiding their existence. Give chart labels more room and retain the exact textual data alternative. Do not replace actual results with an estimated score. Suggested skill direction: `impeccable distill`, then `impeccable typeset`.

## What works and should be preserved

1. **Truthful, low-pressure workout behavior.** Current targets, baseline and advisory limits are distinguishable. Prefills are not completed sets. Manual exercise choice, today-only edits and partial finish are real, visible options.
2. **Useful native interaction and a coherent theme.** Decimal kg entry, integer reps, Next/Next/Done, bottom navigation labels, check-plus-count progress and strong text colors work. The visible prefilled Log button is already an effective one-tap repeat shortcut.
3. **Calm data protection.** The unsaved-note guard really offers save/discard/keep editing. The Files restore flow previews replacement, requires a safety backup and permits cancellation. These safeguards should not be removed to simplify the screen.

## Native measurements and exercised flows

### Display matrix

This is a selected critical-state matrix, not every screen under every combination.

| Configuration | Actual exercised states |
|---|---|
| Approx. 411.43 dp, font 1.0, light | Start/resume, no comparable history, returning prefill, set completion, rest, invalid entry, notes, substitution, history/chart, Files backup/restore |
| Approx. 411.43 dp, dark | Completed target/rest at 1.0; rest at 1.3; rest, body scrolling and numeric IME at 2.0 |
| 360 dp, light | Body/entry/rest and IME at 1.0; rest at 1.3 and 2.0; partial finish and restore preview at 2.0; first-run phase choice at 2.0 |
| 360 dp, dark, font 2.0 | Rest, content gesture, finish confirmation |
| Landscape approx. 914.29 x 411.43 dp, light, font 1.0 | Set table, rest/system navigation, entry and IME |

The emulator raster was 1080 x 2400 portrait with density 420 or 480. These yield the dp widths above; this is not a real Galaxy A71 display/One UI test. API 33 also does not prove Android 14+ nonlinear font scaling behavior.

### Actual operation counts

Counts are activation taps/swipes in named states, not timings or user-study results. Numeric digit entry is separate.

| Operation and starting state | Evidence | Count / qualification |
|---|---|---|
| Repeat a prefilled set with Log already visible | `19` to `20`, then `21` to `22` | 1 Log tap; no required keyboard. Scripted swipes preceding these taps are not inherently required: `18` and `20` already show the button. |
| Enter the numeric IME from kg and complete the form | `03` to `06` | kg field tap plus Next, Next, Done = 4 activation taps, 0 swipes in this captured path. Optional RIR remained empty. Done saved and hid the IME. |
| Move to the visible next exercise during rest | `06` to `07` | 1 exercise-chip tap; rest continued. |
| Expose entry from narrow top position after two sets | `32` to `33` | 1 upward body swipe. |
| Choose first phase at 360 dp/2.0 and reach confirmation | `55`, `56-onboarding-large-scroll1/2` | Selection tap, 2 recorded swipes, Start with Intro 1 tap. Gesture distance/inertia makes this a scenario result, not a universal minimum. |
| Restore from the Backup page with an already visible file | `47` to `52`, later `54` and `58` | Choose file, select it, Save current backup, Android Files SAVE, final Replace/Cancel, success OK when replacing. Navigation and filename typing are additional. Safety steps remain justified. |

`dumpsys input_method` confirms decimal `inputType=0x2002` and Next for kg. The letter keyboard belongs to text-note editing. Rest is still in some XML trees while visually behind the keyboard, so XML presence was not treated as visibility.

The exercise-note navigation guard was exercised via Back and Finish, then Save notes and partial finish; the saved note appeared in history. A new synthetic external-load "Audit cable row" was applied with Also save to plan unchecked. The loading-choice dialog explains bodyweight, external load, assistance and per-dumbbell weight.

The initial replacement was canceled and two workouts remained (`53`). A separately generated empty synthetic backup was then restored through the same safety flow to inspect first-run and truly empty history (`54` to `57`). Finally the audit safety backup was restored and both workouts reappeared (`58`). No physical/user data was touched.

### Contrast and touch bounds

Selected opaque native token pairs, computed using sRGB relative luminance:

| Pair | Light | Dark |
|---|---:|---:|
| onSurface / surface | 16.16:1 | 14.20:1 |
| onSurfaceVariant / surface | 8.84:1 | 10.86:1 |
| primary / surface | 6.01:1 | 10.81:1 |
| onPrimary / primary | 6.12:1 | 7.73:1 |
| primary / secondaryContainer | 4.98:1 | 5.50:1 |
| error / surface | 6.26:1 | 10.80:1 |

The light outline/surface pair is 4.24:1, but it is a nontext border, not a failed 4.5:1 text label. System icons were legible in the normal light/dark captures; system-owned status-clock clipping at 2.0 is not attributed to app code. Selected ratios do not certify all disabled, translucent, chart or system combinations.

`touch-targets.json` records sampled clickable-parent bounds in dp. The main Log control is about 56 dp tall and numeric fields about 64 dp. Many parents are at least 48 dp. The note icon's 47.6 dp width is approximately one-pixel rounding. Rest +/- parents measured about 59 x 41.9 dp in one normal configuration, but XML bounds do not establish the entire Material expanded touch hit area. This needs an actual hit-region/accessibility check before asserting a hard target-size failure. Zero-size/offscreen nodes were excluded from positive conclusions.

There was no installed TalkBack service. Labeled native controls, merged set summaries, headings and textual chart data were inspected; full spoken traversal, switch access, external-keyboard focus and gesture-navigation behavior remain unverified.

## Cognitive load, emotional journey and personas

Three substantive checklist weaknesses remain: repeated reference/instruction competes with entry, secondary settings are not progressively disclosed, and the rest block groups too many action types together. Hierarchy is a partial weakness because the Log button itself is strong once visible. This does not imply a universal "four controls maximum" rule.

The rest panel has up to five action choices: -30 s, +30 s, Set time, Skip rest and a permission/battery remedy. Adjust today combines alternative selection, custom creation, target/rest edits and persistence scope. These are meaningful functions, but not equally frequent decisions. Last-performance prefills and the single-exercise focus reduce working-memory demand.

The emotional high point is honest completion without makeup pressure. The low point is fighting a disappearing entry/rest area or an overlapping safety dialog. First-run guidance is reassuring but reading-heavy. Finishing partial preserves trust; the large-text clipped reassurance undermines that otherwise strong ending.

| Persona | Specific red flags |
|---|---|
| Returning one-handed lifter | Rest disappears while editing; entry moves as rows grow; Finish/notes/overflow are high on a tall phone; Next competes with extra logging after target completion. Physical reach was not measured with a human hand. |
| First-timer | Four substantial phase descriptions precede confirmation; "Last comparable" and advisory language need interpretation; the note icon resembles a menu. The loading-type explanation and explicit nonautomatic progression are useful counterbalances. |
| Low-vision lifter | At 200% text the rest panel consumes the screen, subtitle clips, restore controls overlap and confirmation text truncates. Contrast alone does not resolve these functional accessibility problems. |

## Minor observations and bounded follow-ups

- **P3:** Amber SectionHeader text resembles nearby amber TextButton actions. Reserve the accent more consistently for action/selection; this is a hierarchy judgment, not a demonstrated mis-tap study.
- **P3:** The workout-note icon resembles a menu. It has an accessibility label, but its visual purpose could be clearer without adding permanent prose everywhere.
- **P3:** Singular copy includes "1 workouts" in restore preview and "1 working sets" in some history/home text. This is polish, not a data-counting bug.

The empty History message is honest but offers little next-action guidance. The final backup/restore result title is generic. These can be covered in the same later copy pass, not new feature work.

Do not redesign the data model, add kg steppers without considering exercise-specific increments, force dynamic wallpaper colors, add a tablet navigation framework, or remove truthful optional logging just because a generic skill example suggests it.

## Minimal implementation order, if approved

1. `impeccable adapt`: fix large-font reflow and dialog action separation first.
2. `impeccable adapt`: apply a coherent compact rest/IME/system-inset strategy, using the installed native edge-to-edge guidance and current compatible APIs.
3. `impeccable distill`: reduce repeat-path reading and make Next/history reading easier without changing training semantics.
4. `impeccable harden`: field-specific errors, focus/visibility and useful save feedback; then actual TalkBack and hit-area checks.
5. `impeccable polish`: chart labels, accent hierarchy, icon clarity and singular wording.

Acceptance for that future pass should rerun the captured 360/411 dp, 1.0/1.3/2.0, portrait/landscape and IME scenarios. It should prove nonoverlapping actionable bounds, readable confirmation content, visible compact rest status, and unchanged one-tap prefilled logging. Preserve all existing persistence/rest/backup correctness tests.

## Limitations

- **Galaxy A71 physical acceptance remains pending.** Emulator success does not establish Samsung keyboard, One UI scaling, reach comfort, lock-screen behavior or OEM notification reliability.
- Current evidence is UIAutomator/Compose semantics, native screenshots, platform dumps and source. No real-user testing, timing study, accessibility certification or frame benchmark was performed.
- The old near-cap persistence fixture was 10,484,736 bytes, mostly padding: a 7.374 s read plus 58 ms decode on this two-core API 33 environment. It is a known cold-load limitation, not representative performance for all histories/devices. Production shows "Opening your saved workouts" rather than accepting default-state edits while loading. This audit did not rerun or replace that measurement.
- Source and APK were verified unchanged before report assembly. The prior 136 passing correctness tests are not presented as proof of good UX and were not rerun for a report-only audit.
- No live web detector coverage was available. No HTML/ARIA-specific failure was imposed on Kotlin.
- `37-landscape-normal-rest` is a transitional portrait capture and is not landscape evidence. A gesture in `26-dark-font200-controls` started on the pinned bar and is not proof that the body cannot scroll.
- Display settings were returned to density 420, effective font 1.0, light mode and portrait on the disposable AVD. Its temporary UI hierarchy file was removed and only the audit emulator was stopped. Screenshots, logs and synthetic backup fixtures are preserved.

Questions were skipped because the user explicitly requested autonomous audit-only work without a new interview. No product or style approval is inferred from this report.
