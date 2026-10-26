---
target: Rebuild Android UI (0.1.11)
total_score: 28
p0_count: 0
p1_count: 4
timestamp: 2026-10-22T19-58-36Z
slug: app-src-main-java-com-rebuild-ui
---
# Rebuild native Android UI critique (0.1.11)

Method: dual-agent (A: independent design review · B: deterministic evidence). Target: app/src/main/java/com/rebuild/ui. Evidence: 73 native 0.1.10 captures (API 34, 1080×2400) + 6 from 0.1.9, uiautomator trees, Theme.kt contrast maths, source audit, lint. 0.1.11 changes judged from source.

## Design health: 28/40 (Good)
| # | Heuristic | Score | Key issue |
|---|---|---|---|
| 1 | Visibility of system status | 3 | Finishing a workout gives no confirmation; snackbar is errors-only |
| 2 | Match system / real world | 3 | "Effort", "advisory upper", "load from this workout" are app-speak |
| 3 | User control and freedom | 3 | No undo after Log |
| 4 | Consistency and standards | 2 | "Finish workout" in 4 button types; headings in 6 type roles; 3 raw AlertDialogs; 3 chip shapes; two "Drop" concepts |
| 5 | Error prevention | 4 | Stale-draft guard, cooldowns, backup-before-restore |
| 6 | Recognition vs recall | 3 | Rail pills truncate; Drop chip meaning must be remembered |
| 7 | Flexibility and efficiency | 2 | No ± steppers; add exercise is 3 steps; reorder one row per tap |
| 8 | Aesthetic and minimalist | 2 | 99 on-screen strings > 70 chars; inverted hierarchy; light strip under dark nav bar |
| 9 | Error recovery | 3 | Field-level validation now |
| 10 | Help and documentation | 3 | Over-explained inline |

## Anti-patterns verdict
No visual slop (no gradients, glass, hero metrics, nested cards). The tell is spec-like copy and an unclaimed design system: no Typography, Shapes or motion in Theme.kt; zero animations or haptics. Detector: exit 0, `[]`, but it reads only web files, so 0 Kotlin files were analysed (not a clean scan).

## Priority issues
1. [P1] Dark theme shows a 24 dp light strip behind the gesture bar in 43 of 65 dark captures (Scaffold-level inset padding over a light windowBackground, no values-night theme). RebuildApp.kt:179, res/values/themes.xml. → polish
2. [P1] Finishing a workout has no ending; partial-finish dialog leads with a list of shortfalls. → delight, clarify
3. [P1] Two "Drop" concepts on the logging dock (0.1.11 Drop chip vs Drop set type) plus a third Drop button on Target done. → clarify
4. [P1] Inconsistent component vocabulary: Finish workout ×4 variants, 6 heading roles, dialog buttons 4 dp apart, raw AlertDialogs, 3 chip shapes, accent-tinted More icons, sliders icon with 4 meanings. → polish, audit
5. [P2] No type/shape system; hierarchy inverted (rest countdown bodyLarge, target line titleMedium). → typeset
6. [P2] Copy density: duplicated stopwatch notices, paragraph-first dialogs. → distill
7. [P2] Today has no shape (full exercise list, trailing Backup/Alerts/Phase sections). → layout
8. [P2] Landscape/wide: bottom bar kept, 38–57% of height scrolls, 155-char lines; no predictive back opt-in. → adapt
9. [P3] No motion or haptics; rest progress track invisible (1.00:1). → animate

## Persona red flags
- Casey (one-handed): Finish/note/overflow top-right; rest −30 s inside a dialog; rail cuts previous exercise.
- Sam (a11y): semantics strong; paused strip truncates at 200%; chip meaning depends on neighbour; History rows' bounds overlap Back.
- Alex (power): no ± steppers; 3-step add exercise; 6 taps to move #8 to #2.
- Mid-set lifter: rest countdown is 16 sp while the name is 24 sp; Warm-up/Drop chips flush against Log; three equal buttons after target.

## Minor
Rest field in seconds vs m:ss elsewhere; "(optional) (km)"; +30 s icon ambiguous; two-line "Up next" placeholder; charts dot-only and tall; hardcoded Pull note on Today; Reset filters shown for search only; History detail 3 action rows per exercise; dialog button gaps 4 dp; header actions abut (0 dp).

## Questions
1. What is the honest celebration for "completed means actually performed"? A factual receipt?
2. Does the dock need five set concepts, or Log plus one "Set options"?
3. Is "+1 rep vs last time" a forbidden metric or the most motivating true fact?
