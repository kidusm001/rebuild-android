# Motion critic, build round 2 (feature/motion, cdff189..7a91f88)

## Evidence for this round

All new. Recordings are in the scratch directory `motion/critic-build2/` and are not kept in the repo. Tools are
from `design-council/motion/tools/`.

| Take | Build | What |
|---|---|---|
| `s1` | debug HEAD, cold process after the warm-up | Start, log, log (first Target done), Next, log, Edit set → Delete, with vibrator timestamps |
| `rcpt` | debug HEAD (includes 1f7ed45) | Finish → receipt → Done |
| `rel1` | `fix1/apk/fix-release.apk`, fresh process | Start, log, log (first Target done), Next, with vibrator timestamps |
| `back` | debug HEAD, no rest running | Predictive back on the workout, held and committed |
| `perf/relP1`–`relP3` | release | `fix1/perfrun.sh`: run 1 installs and compiles speed-profile; runs 2 and 3 are fresh processes with no reinstall (`perfrun.sh` without the install and compile lines). Worst frame per moment from `gfxinfo framestats`. |
| `cal2` | — | Calibrates the recorder's start. The status-bar minute flips at pts 5.126 against 5.181 by the wall clock read, so frame 0 is about 65 ms after the read. Accuracy is about ±50 ms, depending on how late SystemUI ticks. |

`blankframes.py`: 0 blank frames in every take.

**State left behind:**
- debug HEAD is reinstalled (`app/build/outputs/apk/debug/app-debug.apk`, built after 1f7ed45);
- "Lower A" is active (Leg press 2/2, on Bulgarian split squat);
- the app is on Today;
- light theme, font 1.0, scale 1, portrait.

## Release worst frame per moment, ms

Main's numbers are the fixer's (`fix1/perf`); "fix" is mine, three runs in three fresh processes.

| Moment | main | fix, run 1 (after install) | run 2 | run 3 |
|---|---|---|---|---|
| First push | 537 | 359 | 394 | 365 |
| First log | 233 | 82 | 86 | 71 |
| **First Target done** | 67 | **271** | **267** | **269** |
| **First Next** | 36 | **132** | **132** | **133** |
| Log 3 | 66 | 82 | 72 | 84 |
| Second Target done | 67 | 92 | 100 | 100 |
| Second Next | 67 | 71 | 69 | 70 |
| Repeat push | 67 | 112 | 117 | 102 |

The first-time costs come back in every process on this emulator, and they don't fall with a second or third
process. `run-as com.rebuild ls code_cache` shows no `com.android.skia.shaders`, so HWUI keeps no program cache here,
consistent with the fixer's account. Whether a phone's cache makes this a once-per-install cost is still unproven.

## Round-1 findings, re-checked

### 1. First-time stalls and half-made frames: **partly fixed**

- **First push and first log:** now better than main (359–394 vs 537; 71–86 vs 233).
- **Half-made frames:** the long frozen half-states are gone. The worst was 580 ms of "two Up next" plus a
  half-risen rest bar.
- **First Target done is still about 200 ms over main, in every process.** Seen in release (`rel1`):
  - Tap at about 16.50, then a 180 ms stall (16.698 → 16.878).
  - Then 16.878–16.956: about 80 ms of **mixed frames**. The rest bar has restarted at 3:01, and by 16.933 the pill
    reads 2/2 and row 2's slot has emptied, but the dock still shows the entry with an amber Log set.
  - Then 16.980 cuts to Target done; the Rise isn't seen.
  - Debug `s1` shows the same pattern at 25.979–26.061.
- The first log in debug (`s1` 13.809–13.892) still has two frames where the dock says "Set 2 of 2" and the pill 1/2,
  while the ledger shows an empty slot and "Today 0 of 2".
- What remains is new finding 2.

### 2. Finish → receipt: **fixed** (one new problem, new finding 3)

`rcpt` 2.97–3.68:
- The finish dialog holds its busy look until the save lands.
- The sheet rises at 3.468, 3.494, 3.528, 3.559 and 3.592, over a dimmed workout that crossfades to Today underneath.
  It is at rest by about 3.68.
- No frame shows the workout undimmed, and there is no empty frame.
- The receipt's tick lands at about 3.73 (with the calibrated offset), roughly as the sheet comes to rest.

### 3. The nav bar over the workout's back preview: **fixed**

- `back` 1.5–3.2: the card shrinks and rounds, with no nav glyph inside it. This matches the fixer's `backR` and
  `back-trio.png`.
- But committing the gesture shows a new doubled frame: new finding 1.

### 4. Haptic lead: **fixed**

With the 65 ms calibrated offset, the set's tick lands after the frame where the set is first drawn:

| Build | Lead after the first drawn frame |
|---|---|
| release (`rel1`) | 13–19 ms |
| debug (`s1`) | 19–94 ms |

That is within one emulator frame or two, and it agrees with the fixer's −2…+70 ms. Ticking off the first drawn frame
is the right construction.

### 5. Contradictory frames on log and delete: **fixed** (except the first-time case under 1)

- **No more two "Up next" rows.** At `s1` 13.809 row 1 is an empty slot fading out; only row 2 says "Up next".
- **Delete** (`s1` 61.75–63.03):
  - "Delete set" holds its busy look until the save lands.
  - The dialog then fades over a ledger that has already updated ("Today 1 of 2", "Not logged").
  - The 850 ms stale ledger is gone.
  - At this frame rate I couldn't separate the pill's cut from the count's roll. I accept the fixer's account that
    the pill cuts about 100 ms before the count rolls, as the spec says.

### 6. Landscape tab switches: **fixed**

Evidence is the fixer's `landR`, re-measured by me but not re-recorded:
- `blankframes` 0, presence ≥ 0.76 on every switch I could window.
- 24.80–24.91: the incoming tab fades in over the outgoing one, with no washed-out frame.

### 7. Start: Today idle while the workout composes: **fixed**

`s1` 5.34–5.78: "Start Lower A" keeps its busy look until the push begins.

### 8. P3s

- **The spinner fading in over the loading screen:** not recorded, not judged.
- **The bars' pop shift:** not re-checked.
- **The busy look on emulator saves:** unchanged (a property of the emulator).

## New findings

### 1. P1: Committing a predictive back on the workout shows both screens at full strength

- **Moment:** releasing a back swipe on the workout after the preview.
- **Evidence:** `back` 3.396, 3.449, 3.591 and 3.635 (`critic-build2/back-r-3.38.png`).
  - For about 240 ms, Today's "Thursday", the "Resume workout" pill and its exercise list are drawn at near full
    strength through the workout. The workout is also at near full strength: "Bulgarian split squat", its ledger and
    an amber Log set.
  - At 3.635 the nav labels also sit over the leaving dock.
  - 3.778 is clean Today.
- **The cause.** On commit, `previewing` turns false (`RebuildApp.kt`, the predictive-back handler). That drops the
  card's opaque canvas backing while the card is still about alpha 1. Meanwhile the screen beneath is already at
  0.6 + 0.4p (≈ 1). Everywhere the workout has no card of its own, Today shows through at full strength. On a device
  this lasts the pop's 100 ms fade; here the stalls stretch it to 240 ms.
- **Against the spec.** P2-1's check, "commit shows no doubled text", fails.
- **Would satisfy me:**
  - The leaving card keeps its opaque backing until its alpha reaches 0. It fades as an opaque card over the screen
    beneath, as Fade in over does.
  - The nav overlay stays beneath it until then.
  - Verify: every-frame sheets of a held-then-released swipe on the workout and on Exercise history show no frame
    where the beneath screen's text appears inside the card at more than about 50 %.

### 2. P2: The first Target done in a process still costs about 200 ms over main and shows mixed frames

- **Evidence:** the perf table (271/267/269 against 67, in three processes) and `rel1` 16.69–16.98 as above.
- **The cause, per the fixer.** Three programs (AtlasText, FillRect, TextureOp) appear only when the real
  `TargetMetPanel` is drawn. The warm-up draws it at about 1/255 with ModulateAlpha, which evidently compiles other
  variants.
- **Why it matters.** This is the app's one reward moment, on every session's first exercise. It shows 180 ms of no
  response and then contradictory frames.
- **Would satisfy me:**
  - **Either** the warm-up draws the exact variants: the real panel (and Next's incoming dock and body) at full
    strength, under an opaque surface that already covers them, rather than at 1/255. Then, in a release build, the
    first Target done in a fresh process is within main + 50 ms on two consecutive processes.
  - **Or** a real-GPU device shows that a second process is at parity, which would make this once per install. I'd
    then take it as P3.
  - **In either case:** while that first frame stalls, all parts of the change stay on the old state together (rest
    bar, pill, slot, dock kind). No frame shows the restarted rest and the emptied slot with the old entry dock.

### 3. P2: On Finish, the dialog fades out over the rising receipt, and Finish wakes up first

- **Evidence:** `rcpt` 3.279–3.468 and 3.559–3.680.
  - After the save, the dialog's Finish and Cancel turn active again (amber) for about 190 ms before the sheet
    starts.
  - Then the dialog, a separate window that is always above the in-app sheet, fades out while the sheet rises
    beneath it. "Finish partial workout", its checkbox text and its buttons sit over "Lower A saved" and the sheet's
    rows for about 120 ms.
- **What is wrong.** The doubled text lands on the "saved" moment, and a live Finish button invites a second tap
  into a finished workout.
- **Would satisfy me:**
  - The dialog keeps its busy look until it leaves.
  - It is gone before the sheet passes under it: either dismiss it with no window exit at the sheet's first drawn
    frame (the sheet's scrim already dims), or start the sheet's travel when the dialog's exit ends.
  - Verify: no frame shows dialog text over the sheet, and no frame shows an enabled Finish after the save.

### 4. P3: First Next (+96 ms) and the repeat push (+35–50 ms) in release

- **First Next:** a 190 ms stall before anything answers the tap (`rel1` 22.639 → 22.829), then a clean crossfade.
  It happens once per process, mid-workout. This is not a blocker, but the warm-up fix in finding 2 should include
  Next's incoming dock and body.
- **Repeat push:** 102–117 against 67. It's within the +50 ms I set, and the first push is now 150–180 ms better
  than main. I accept it.

## Answers to the lead

1. **The first-Target-done cost:** not acceptable yet (finding 2). It repeats in every process on this emulator.
   Satisfy it by warming up the exact variants (measured on two consecutive release processes, within main + 50 ms),
   or with real-device proof that it is once per install. Either way, no mixed frames during the stall.
2. **First Next +97 / repeat push +50 in release:** acceptable as P3 (finding 4). Fold Next into the same warm-up fix.
3. **Haptic timing:** acceptable. The tick lands 13–19 ms after the set is first drawn in release (19–94 ms in
   debug), within the emulator's frame noise.
4. **The receipt on 1f7ed45:** re-recorded (`rcpt`). The empty and undimmed-workout frames are fixed; the dialog now
   double-exposes over the rising sheet (finding 3).

**Pre-existing, the hero card swapping during the pop back to Today:** not judged this round. It's outside the
fixes; record it next round.

## What is good

- The warm-up made first push and first log faster than main in release.
- The tick-on-first-drawn-frame construction is right, and it measures well.
- Delete, Start and the landscape tab fade-in now behave as specified.
- The receipt never shows an empty or undimmed screen any more.
- The back preview keeps the nav bar out of the card.
- 0 blank frames across every take.

VERDICT: NOT SATISFIED
