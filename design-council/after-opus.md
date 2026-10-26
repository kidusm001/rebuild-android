# After-check for 0.1.4: claude-opus-5.5 (effort high)

**Verdict: Accept with nits. No must-fixes.**

**Scope.** I looked at the candidate screenshots only: `after-{dark,light}-workout-rest.png`, `after-{dark,light}-history-detail.png`, `after-dark-entry-200.png`, `after-{dark,light}-entry-200-scrolled.png`, `after-light-today.png` and `comparison-today.png`. I didn't run tests, TalkBack or a physical device.

## What was achieved

- **Previous-set data.** The prose line is replaced by a dated "Previous working sets · Sep 26, 2026" grid (Set / kg / Reps / RIR). It can be compared at a glance and follows the same order as the entry fields (`after-dark-workout-rest.png`). At 200% it becomes labelled groups that are still unambiguous (`after-dark-entry-200.png`).
- **Entry and single primary action.** The large labelled fields and one full-width Log set are kept. The scrolled 200% captures show the whole field row and Log set above the rest bar (`after-*-entry-200-scrolled.png`). No occlusion concern.
- **Dark theme preserved.** Amber stays on the primary, Finish and rest progress. Secondary actions are neutral, which is quieter.
- **Light theme improved.** Surfaces are near-neutral. Log set is an ink button with a cream label. Amber appears only on the selected chip, the rest container and the navigation indicator (`after-light-workout-rest.png`). Whether the rest bar should stay warm beige is taste, not a blocker.

## Nonblocking nits

1. **Text-button inset remains.** "Change phase", "Target details · rest 3:00", "Add workout note" and "Add missed set" still sit about 26 px (rendered) right of the content edge (`after-light-today.png`, `after-dark-history-detail.png`).
2. **History secondary actions look like labels.** Now neutral, they read almost like static text. A leading outline icon or tonal style would restore the affordance without the amber noise.
3. **Small alignment issues.**
   - The previous-set date is top-aligned rather than on the heading's baseline.
   - The row for the set being logged ("Log set 2") isn't emphasised.
   - At 200%, the warm-up checkbox sits next to its second label line instead of the title.
4. **Longer scroll.** "Working sets: 1 of 2" has moved further below the fold.
5. **Not evidenced.** Monday is a rest day, so the scheduled-day filled "Start <workout>" button isn't shown in the candidate.

## Rebuild scores (screenshot-bounded)

| Criterion | 0.1.3 | 0.1.4 |
|---|---|---|
| hierarchy | 3 | 3 |
| legibility | 3 | 4 |
| action_clarity | 3 | 3 |
| orientation | 3 | 3 |
| theming | 2 | 3 |
| rebuild_fit | 3 | 3 |
| **Total** | **17/24** | **19/24** |

- **Legibility** rises because previous-set data is aligned and dated.
- **Theming** rises because light is coherent and dark is preserved. It isn't 4 because of the inset text buttons.
- **Action clarity** stays at 3 because a clearer light primary is offset by quieter secondary actions.
