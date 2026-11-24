# Motion critic, build round 3 (feature/motion, feb89f6..81e0828)

## Evidence for this round

All of it is new and recorded on the emulator running on the host GPU: the renderer is the NVIDIA RTX 3070 Ti through
the Android Emulator OpenGL ES translator, as `emu.sh doctor` reports. The recordings are in the scratch directory
`motion/critic-build3/` and are not kept in the repo. The tools are from `design-council/motion/tools/`.

| Take | Build | What it covers |
|---|---|---|
| `r2` | debug HEAD | `drive-r2.sh`: Start, then a Back-key pop to Today (the hero card), resume, a slow back swipe held and committed, resume, a log and a Skip, then Finish → receipt → Done. Includes vibrator timestamps. |
| `rel` | release HEAD, fresh process | Start, log, log (the first Target done), Next, a pill tap back, Finish dialog opened and cancelled, Back key to Today, then all four tabs |
| `land` | release HEAD, landscape | Tab switches History → Plan → More → Today → History |
| `hb` | release HEAD | Two logs recorded with `screenrecord --bugreport`. Every frame is stamped with device time, so each tick can be read against the frame it should land with, with no offset to estimate. |
| `perf/` | release main (42edf76) and HEAD | `perfrun.sh`, two fresh processes each (`main-r1`, `main-r2`, `head-r1`, `head-r2`). Both APKs were built for this round: main in a temporary worktree, which has since been removed. |

`blankframes.py` finds 0 blank frames in every take.

**State left behind:**
- release HEAD is installed;
- the workout "Upper" is active (Machine chest press 2/2, Seated cable row 2/2);
- the app is on Today;
- light theme, font 1.0, scale 1, portrait (`user_rotation` 0, auto-rotate off as before).

## Release worst frame per moment (ms), on the GPU

From `framestats_cmp.py perf/`:

| Moment | main r1 | main r2 | HEAD r1 | HEAD r2 |
|---|---|---|---|---|
| First push | 101 | 67 | 34 | 34 |
| First log | 34 | 57 | 34 | 17 |
| **First Target done** | 33 | 17 | 33 | 33 |
| **First Next** | 33 | 17 | 50 | 33 |
| Log 3 | 17 | 34 | 33 | 17 |
| Log 4 (second Target done) | 18 | 17 | 17 | 33 |
| Second Next | 34 | 17 | 33 | 33 |
| Repeat push | 17 | 34 | 17 | 33 |

- Every moment on HEAD is within one or two vsyncs of main, and none is more than 17 ms over it. The first push is
  now better than main (34 against 67–101).
- `framestats.py` shows the same medians while the transitions run:

  | Moment | main median (ms) | HEAD median (ms) |
  |---|---|---|
  | First Target done (log2) | 22.4 | 23.5 |
  | Second Next (next2) | 23.1 | 24.5 |
  | Repeat log (log3) | 17.0 | 16.8 |

- The 24–40 ms medians the fixer noted happen on main as well, so they come from this emulator's GPU translation,
  not from the build.
- The one outlier is HEAD r1's first Next, with a 50 ms frame and a 39.7 ms median. It did not repeat in r2 (33 ms
  worst, 23.2 ms median).

## Build-round-2 findings, re-checked

### 1. P1: Committing a predictive back on the workout showed both screens at full strength. **Fixed.**

`r2` 19.9–21.86:
- **While the swipe is held**, the workout shrinks into a rounded card with a shadow. Today shows beside its edge.
  No nav glyph is drawn inside the card; the bar stays beneath it.
- **On release** (21.525–21.592), the opaque card fades over Today for about 4 frames, a true crossfade, and
  21.624 is clean Today.
- No frame shows Today's text at full strength through the workout. Presence over the commit is 1.00.
- **The plain Back-key pop** (`r2` 10.53–10.61): the same crossfade with no doubling, presence 0.73.

### 2. P2: The first Target done cost about 200 ms over main and showed mixed frames. **Fixed on the GPU.**

- **Perf:** the first Target done is 33/33 ms against main's 33/17 (table above), within the +50 ms bar.
- **The frames** (`rel` 15.55–15.74):
  - The save lands at 15.571: row 2's slot empties and the rest restarts.
  - At 15.604 the pill reads 2/2.
  - At 15.624 the Target done panel starts fading in over the entry, which stays opaque underneath, so Log set's
    amber never dims.
  - At 15.708 Next is fully opaque, and the panel is at rest by 15.74.
- What remains are two frames (about 33 ms) where the slot and pill have changed but the dock hasn't started yet;
  P3, item 3 below.
- The SwiftShader result (271 ms in every process) came from the emulator's software renderer. On a GPU it does not
  reproduce, which agrees with the fixer's diagnosis.

### 3. P2: On Finish, the dialog woke up and then faded out over the rising receipt. **Fixed.**

`r2` 49.31–50.41:
- After the tap, the dialog's Finish and Cancel turn grey (49.526) and stay busy until it leaves.
- At 50.125 the dialog is gone with no exit, and the receipt's scrim continues the dialog's dim with no step.
- The sheet is seen rising at 50.125, 50.143, 50.161, 50.177, 50.193 and 50.214, over a dimmed workout that
  crossfades to Today underneath (50.125–50.214).
- It is at rest by about 50.29.
- No frame shows dialog text over the sheet, an enabled Finish after the save, or an undimmed workout.

**The dialog's dim lag (the cost the fixer named):** not visible on the GPU. When the dialog opens, its card and its
dim arrive within one frame of each other in debug (45.332 and 45.341) and in release (31.721 and 31.744), and both
are complete by about 100–130 ms.

### 4. P3: First Next and the repeat push in release. **Fixed on the GPU.**

- **First Next:** 50/33 ms against main's 33/17, at most one vsync over.
- **The Next frames** (`rel` 21.72–21.84):
  - The body crossfades in its own slot; the dock's Target done panel fades out over the new entry.
  - Log set and the dock never travel sideways (`regiondiff` on the dock: a single change at 21.758).
  - Everything is at rest by 21.84.
- **Repeat push:** 17/33 ms against main's 17/34.

## Other items re-checked on the GPU

| Item | Evidence | Status |
|---|---|---|
| Set logged (P1-1) | `r2` 35.02–35.31; `rel` 9.4–9.65 | The slot gives way, the value fades in place, the band steps down and the count rolls. The rest bar rises from the bottom edge with the dock in step. There is never more than one "Up next". |
| Pop back to Today, hero card (4d3f52c) | `r2` 10.53–10.84; `rel` 38.30–38.50 | Today comes back already showing "In progress · Upper". The card never swaps during or after the pop. Fixed. |
| Pill tap back to a finished exercise | `rel` 26.70–26.99 | The body crossfades, the entry dock gives way to the Target done panel by Fade in over, and Log set's amber never dims. |
| Portrait tabs | `rel` 43.4, 47.9, 52.4, 56.9 | Presence 0.88, 0.88, 0.76 and 0.92, with no blank frames. |
| Landscape tabs | `land`, five switches | Presence 0.87, 0.83, 0.97, 0.97 and 0.85, with no blank frames. Fixed. |
| Set-logged haptic | `hb` | Read from the `--bugreport` device-time overlay against the vibrator's start times. **Log 1:** the tick lands at 16:30:30.036; the slot empties in the frame stamped 30.048 and the value first draws at 30.088, so the tick leads by 12–52 ms. **Log 2:** the tick lands at 41.295 and the first changed frame is 41.318, a 23 ms lead. That is within about three frames at 60 Hz, which I accept. |

## Remaining items (P3, not blocking)

1. **The push crossfade dips just under the presence bar.**
   - **Evidence:** Today → workout reads presence 0.36 in release (`rel` 3.492) and 0.35 in debug (`r2` 4.758–4.775),
     where the spec's bar is 0.40. That is two frames, about 33 ms, in which Today fades out while the workout fades
     in and both sit near 50 %.
   - **Why it isn't blocking:** it is the spec's own Crossfade rule, and it reads as a crossfade, not a flash.
   - **If it's wanted:** since the incoming top layer is now canvas-backed for the whole move, let it fade in over an
     outgoing layer held at 1 (Fade in over) to remove the dip. Or record the 0.35 as accepted for push.
2. **The `hapt.py` offset method is unreliable for sub-frame claims.** On this boot the recorder start offset was at
   least 120 ms (measured from the minute flip), not the 65 ms assumed before. Measure ticks with
   `BUGREPORT=1 hrec.sh` and read the per-frame device time, as in `hb`. I did not re-measure the receipt's tick that
   way this round.
3. **About two frames lead the first Target done.** For about 33 ms the slot, pill and rest bar have changed while the
   dock hasn't yet started its Fade in over (`rel` 15.571–15.604). It is barely perceptible on the GPU. Starting the
   dock in the same frame would remove it.
4. **Not re-checked this round:**
   - the drag;
   - the keyboard;
   - strip add, remove and skip;
   - the error line;
   - the snackbar;
   - the spinner fade-in on a slow launch;
   - the bars' shift on pop with a rest running;
   - font 2.0.

   None of them was changed by this fix round.

## Summary of the verification approach

The two SwiftShader-only costs (the first Target done and the first Next) are gone on a real GPU path. Every round-2
finding is fixed, with fresh frame and perf evidence. The remaining items are polish. A check on a physical phone
before release would still be worthwhile, because the emulator's GPU translation adds its own frame noise to the
smoothness judgements.

## What is good

- The back preview is now right in every phase: held, cancelled and committed. The nav bar stays beneath the card,
  and the commit is a clean crossfade.
- The finish → receipt handoff reads as one continuous dim and one rising sheet, with no stray dialog text.
- First push and first log are faster than main.
- First Target done and first Next are at parity with main on the GPU.
- Logging a set, Next, pill taps and tabs (portrait and landscape) all behave as the spec describes, with 0 blank
  frames anywhere.
- Ticks land within a few frames of what they confirm.

VERDICT: SATISFIED
