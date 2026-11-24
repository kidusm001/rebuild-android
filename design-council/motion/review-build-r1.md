# Motion critic, build round 1 (feature/motion, 42edf76..cdff189)

## What I looked at

**The builders' recordings.** I looked at `after1`, `after2` and `after3`. I re-ran `blankframes.py` on all 29
recordings, `presence.py` on the landscape recording, and `vib.py` on the haptic takes.

**My own recordings**, in `motion/critic-build1/`, all on rebuild_api34 at scale 1, with seeded history:

| Recording | Build | What it covers |
|---|---|---|
| `sessA` | build | Cold process. Start Lower A, log, log (Target done), Next, log, log, Next. With vibrator timestamps. |
| `mainlog` | main (`perf/main-42edf76.apk`, `install -r`, data kept) | The same first log and Target done, as a baseline. |
| `sessB` | build, reinstalled | First process after install. Two logs. |
| `sessC` | build | Second process. Two logs. |
| `sessD` | build | Edit set, rest end, workout predictive back (commit). |
| `sessE` | build | Set delete, a log, the hold mark (`holdmenu.png`). |

**The app afterwards.** The build is reinstalled (`app/build/outputs/apk/debug/app-debug.apk`, same build as before).
The workout "Lower A" is active with every exercise at target. The app is at the launcher. Light theme, font 1.0,
scale 1. No app code was changed.

**Claims I confirmed:**
- 0 blank frames everywhere, apart from 3 flat-amber takeover frames (intended; pixel `#FEB224`).
- The takeover numeral stays inside its sheet, and its tick lands as the sheet does (20.08 → 20.18).
- The splash icon disc shows on the canvas.
- The hold mark is visible: the held pill keeps an amber border while its menu is open.
- Log set doesn't move during push and pop.

## Findings

### 1. P1: The first time each change happens in a process, it freezes on a half-made screen, and it is much slower than main

**The moments:** the first log, the first Target done, the first Next and the first push in a process.

**First log** (`sessA` 12.6–14.8; compare `mainlog` 10.2–11.75):
- After the save, a 630 ms stall.
- Then 14.246–14.404, an inconsistent screen: the dock says "Set 2 of 2", the pill says 1/2, but the ledger says
  "Today 0 of 2" with **both rows reading "Up next"** and no rest bar.
- Then 14.427–14.670, a frozen half-animation: the value at about 40 %, the count digit mid-roll, the rest bar half
  risen.
- At rest at 14.80.
- Main (`mainlog-r-10.95.png`) lands everything in one consistent frame, about 125 ms after the save.

**First Target done** (`sessA` 19.5–20.6):
- The change becomes visible 430 ms after the save. Main takes about 40 ms.
- The 220 ms dock glide and Rise are never seen: the panel cuts in at 20.616.
- Repeats cut too (`sessE` 22.414 → 22.439).

**First Next** (`sessA` 25.30 → 25.63; also `after2/next.mp4` 4.046–4.298):
- A 330 ms stall.
- Then a frozen frame of two docks superimposed: "Next: Bulgarian split squat" ghosted over the field tiles, and "Log
  set" over "Extra set".
- The body has no visible travel. The second Next in the same process stalls only 116 ms (`sessA` 42.83 → 42.95).

**First push:** the builders' perf numbers give 362–384 ms against 204–233 ms on main.

**Is it per process or per install?**
- In a later process (`sessC` 19.1–20.1) the first log still stalls about 120 ms, then shows the same "two Up next"
  state for about 190 ms.
- So it is not a one-time, per-install shader cost. Every gym session starts a new process, and every session pays
  for it on the logging path.
- "A phone caches shaders" may well remove part of it, but that has not been shown. The half-made frames are a
  design problem regardless of speed: whatever is slow, a stall must leave the old, consistent state on screen.

**What would satisfy me:**
- **(a) No half-made state.** Every part of one change switches in the same frame: ledger value, slot, count, dock
  kind, rest bar and pill. Any transition that waits for its first drawn frame holds every part of that change on the
  old state until then, never only some parts.
- **(b) The first-time cost is removed or hidden.** For example, warm the new layer and transition paths once in
  idle time after launch (as the tabs are warmed today), or use non-offscreen alpha wherever content doesn't overlap.
- **(c) Measured on a release build** (R8 plus the profile installer that is already a dependency), cold process,
  first log, first Target done, first Next and first push. Each must be within +50 ms of main's slowest frame, and no
  frame may show a mixed state.

### 2. P1: Finish → receipt: the finished workout comes back, then the sheet lands on it

- **Evidence:** `after1/receipt.mp4`, every frame, in `critic-build1/receipt-a1-r-3.88.png`.
- **Sequence:**
  - The finish dialog fades out (3.90–4.00).
  - For 4.000–4.127 (5 frames, about 130 ms) the **workout is back, undimmed and live-looking**, with an amber Log
    set.
  - At 4.149 the sheet appears already about 93 % up, over the *workout*, not over Today.
  - Today crossfades in underneath only at 4.338–4.417.
  - The frames come every 90 ms (4.15 → 4.24 → 4.34 → 4.42), so the sheet's travel is barely seen.
- **Against the spec (P0-2):** "Today visible (dimmed) in every frame between the dialog's last frame and the sheet
  at rest", and "its only travel is vertical, seen at ≥ 2 positions on the emulator". Both fail.
- **The cause.** The dialog closes on `onSuccess` (`WorkoutScreen.kt:1581`), but the receipt waits for the composed
  state, which arrives about 250 ms later. The sheet's entrance also starts before its long first frame, so the
  stall eats it.
- **What would satisfy me:**
  - The finish dialog dismisses in the frame `receiptFor` becomes non-null, never before, so the dialog's own scrim
    covers the wait.
  - The sheet's travel clock starts after its first drawn frame (the P0-1 rule).
  - Verify: no frame between the dialog and the sheet at rest shows the workout undimmed, and the sheet is seen at
    ≥ 3 positions at scale 1.

### 3. P1: The workout's predictive back draws the nav bar over the workout's own dock

- **Evidence:** `sessD.mp4` 34.5–37.2, with frames in `sessD-r-34.4.png`. The before/during crops are
  `pback-dock.png` and `pback-top.png`.
- **What happens.** During a slow edge swipe on the workout:
  - The preview card does shrink and round, as specified.
  - But the nav bar fades in **on top of the card**. "Today, History, Plan, More" and their icons are drawn across
    "Add drop to set 2" and "Extra set" at about 50 % from 35.25 until the release.
- **The cause.** `navAlpha` follows the layer beneath (`RebuildApp.kt:783`), but the nav overlay sits above every
  layer in z-order, including the previewed top card.
- **Scope.** This affects every pushed screen that has no nav bar: the workout and a past workout's detail. The
  builders' take (Exercise history) has the bar on both screens, so it couldn't show this.
- **Ordinary pops.** Without a rest bar covering it, the plain pop has the same overlap for its first 100 ms.
- **What would satisfy me.** While a top layer without the bar is previewed or popping, the nav overlay is drawn
  beneath it: beneath layer, then nav, then top card. Alternatively, clip the nav overlay to outside the card. Verify
  with a recording of a slow swipe on the workout, commit and cancel, with no rest running: no nav glyph inside the
  card in any frame.

### 4. P2: The haptic still leads the visible set by 120–520 ms, also on repeat logs

**Evidence:**

| Moment | Tick | Set first visible | Lead |
|---|---|---|---|
| `sessA` log1 | 13.994 | 14.246 (ledger only at 14.427) | 250–430 ms |
| `sessA` log2 | 20.182 | 20.616 | 430 ms |
| `sessA` log3, a repeat | 31.451 | 31.577 (value at 31.683) | 126–232 ms |
| `sessC`, second process | 19.505 | 19.627 (value 19.816) | 122–311 ms |

The spec allowed ≤ 50 ms on the emulator.

**Why.** The tick fires in `onSaved`, but the state frame then stalls, and the ledger value waits on its own
transition.

**Would satisfy me.** Tie the tick to the frame where the new value is first drawn, as the takeover now does with
`cover ≥ 0.98`: for example, from the incoming cell after its first drawn frame. Or fix finding 1 so that the gap
disappears. Either way, re-measure with `vib.py`: ≤ 50 ms on the emulator for first and repeat logs.

### 5. P2: Logging or deleting a set shows contradictory frames even when nothing is slow

- **Repeat log** (`sessA` 31.577–31.649): row 1 still shows its dashed "Up next" at full alpha, row 2 already says
  "Up next", and "Today 0 of 2" shows while the dock reads "Set 2 of 2". Two "Up next" rows for about 100 ms.
- **The cause.** The old slot is held at alpha 1 by "Fade in over" while the next row's label flips at once.
- **Font 2.0** (`after3/font2-setlogged-30fps.png` 0.267–0.433): the dock says "Set 2 of 2" while "Today 0 of 2".
- **Set delete** (`sessE` 9.0–9.97):
  - The dialog closes; at 9.12 the pill's count already reads 1/2.
  - But the ledger, the badge and the Target done dock keep showing the deleted set and "2 of 2" for **about
    850 ms**.
  - Then they change in one frame. No count roll is visible.
- **Would satisfy me:**
  - A replaced slot's text never coexists at full alpha with the same text in another row. The old slot's label
    leaves with Fade out, as in the spec's P1-1 table ("dashed slot: Fade out"); only its opaque card uses "Fade in
    over".
  - Every part of one change starts in one frame (as in finding 1a).
  - Verify: an every-frame sheet of a repeat log and a delete shows no frame with two "Up next" rows, and no frame
    where pill, ledger count and dock disagree for more than one frame.

### 6. P2: Landscape tab switches dip; this is not a limit of the presence metric

- **Evidence:** `after3/land.mp4` History → Plan, 7.384 and 7.412 (presence 0.23 and 0.20), and Today → History,
  3.466 (0.36). Frames in `critic-build1/land-r-7.24.png`.
- **What is wrong.** Both screens are faint and superimposed, and the frames read as a washed-out flash. White cards
  differ from the canvas by about 15 grey levels, above the metric's threshold of 10, so they do count. The low
  numbers are real.
- **The cause.** The outgoing tab fades before the incoming one has drawn its first frame. In landscape the incoming
  tab's first layout at the new width takes longer. That breaks the spec's rule: "t = 0 is the incoming layer's first
  drawn frame for both".
- **Would satisfy me.** The outgoing tab's fade also waits for that frame. Landscape tab switches reach presence
  ≥ 0.40.

### 7. P2: After Start's save, Today looks idle and ignores taps for about 750 ms

- **Evidence:** `sessA` 5.39–6.14.
- **What happens:**
  - After the 1.2 s busy look ends, Today shows an enabled amber "Start Lower A" and nothing in progress, for about
    750 ms.
  - Then the push begins (6.277).
  - Tap → workout takes 2.7 s in total on the emulator with seeded data.
- **Why it's wrong.** Today is now the leaving layer, inert and frozen on the old state. A second tap is silently
  dropped, and the screen says nothing happened.
- **Would satisfy me.** The frozen leaving Today keeps the busy or pressed look on Start until the push starts, or
  the push starts as soon as the save lands, with Today staying under it while the workout composes (the spec's
  intent).

### 8. P3: Smaller items

- **Spinner to Today on a slow cold launch** (`after2/coldlaunch-light.mp4` 5.02 → 5.51) is a cut after a stall. The
  spec says nothing about it; a Fade in would match the rest.
- **The pop's chrome shift** jumps in one emulator frame (`after1/pushpop` 6.147 → 6.174). This is probably dropped
  frames; re-check at scale 1 on a device.
- **The first-log busy look**: with seeded data, saves take about 1.2–1.4 s on the emulator on **both** main and the
  branch (`mainlog`: tap 10.16, tick 12.00 minus the 500 ms cooldown). So "Saving…" showing on every log here is
  correct per spec, not a regression.

## Spec P0/P1 items against the build

| Item | Status | Notes |
|---|---|---|
| P0-1 push/pop | Partly | No blanks, and Log set is still. Still open: the first-push stall (finding 1) and the nav overlay on pop (finding 3). |
| P0-2 receipt | **Fails** | Finding 2. |
| P0-3 tabs | Partly | Portrait passes; landscape fails (finding 6). |
| P0-4 busy look | Passes by design | On this data, a save is slower than 300 ms on main too. |
| P0-5 Start | Partly | Today no longer rewrites itself, but finding 7. |
| P0-6 cold launch | Passes | The icon disc shows and the background is the canvas (P3: the spinner exit cuts). |
| P0-7 takeover | Passes | The numeral stays clipped and the tick is aligned. |
| P0-8 drag | Not re-checked | Builders' `drag.mp4` only. |
| P0-9 keyboard | Not re-checked | Builders' `keyboard.mp4` only. |
| P1-1 set logged | **Fails** | Findings 1, 4 and 5. |
| P1-2 bars | Passes | The bar rises from the edge with the dock. |
| P1-3 Target done | **Fails at scale 1** | The panel cuts in (finding 1); the motion is only seen at ×5. |
| P1-4 Next | Partly | The first time stalls on superimposed docks; repeats are close. |
| N1 on Next out of Target done | Deviation accepted | Log set lands where "Extra set" was. Those pixels aren't identical, so a true crossfade is right; check instead that no superimposed frame is held for more than one frame. |
| P1-5 hold mark | Passes | `holdmenu.png`. |
| P1-6 delete | **Fails** | Finding 5. |
| P1-7 strip add/remove/skip | Not recorded | "Remove from today" is disabled for done exercises; still needs a take. |
| P1-8 snackbar | Not recorded | — |
| P1-9 error line | Not recorded | My attempt logged a valid weight; still needs a take. |
| P1-10 font 2.0 | Partly | Same stale-ledger pattern as finding 5. |
| P2-1 predictive back | **Fails on the workout** | Finding 3. Exercise history passes. |

## Answers to the lead's questions

- **First-occurrence slowness.** Not acceptable as it is. It recurs in every new process, and it freezes on
  contradictory half-states on the most frequent path. Fix and measure it as in finding 1.
- **The haptic lead.** Not acceptable. It is 120–300 ms even on repeats. See finding 4.
- **Landscape presence.** Not a metric limit: the frames really are washed out. See finding 6.
- **The builders' other deviations:**
  - the system splash fade on API 31+;
  - one shared reveal for the bars;
  - the opaque preview card;
  - setup's last step kept inert under Today;
  - some controls left on the plain busy look.

  All are acceptable as described. The opaque card needs finding 3.

## What is already good

- Blank frames are gone everywhere: 19 → 0 on tabs, 10 → 0 on the receipt.
- The push and pop layer model works: no corner growth, Log set doesn't move, and the bars ride the nav height.
- The Up next takeover is clean, with the numeral clipped and the tick on the landing.
- The splash disc shows.
- The hold mark is clear.
- Repeat exercise switches are quick and keep Log set full strength (N1 passes on pill taps).
- The builders' evidence is thorough and honest about what wasn't recorded.

VERDICT: NOT SATISFIED
