# Tab-switch performance (0.1.13)

Switching to History or Plan stuttered: the screen appeared a beat after the
tap and the fade stuttered. This records how it was measured, what caused it
and what changed.

## How it was measured

All measurements ran on an API 33 emulator (1080×2400, 420 dpi) with the real
0.1.10 capture database (`0.1.10/evidence/capture-method/captures-end-state.db`,
about 38 workouts).

- **`design-review/perf/measure.py`** installs an APK over that data and
  compiles it the way the phone's idle maintenance does (`speed-profile`). It
  then records a Perfetto scheduler trace while it taps Today → History → Plan →
  More, with each tap time-stamped on the device. For each tap it reports how
  long the app's **main thread actually ran on a CPU** in the next 900 ms. That
  is the app's own work, and it is unaffected by the emulator's software GPU.
  `FIRST=1` measures the first visit to each tab after a fresh launch.
- **`design-review/perf/frames.py`** breaks each slow frame into recomposition,
  measure/layout, text layout, draw recording, and time blocked on the render
  thread.
- **`design-review/perf/composables.py`** attributes time to individual
  composables. It needs a trace from a build with Compose composition tracing.

The emulator renders with SwiftShader, a software GPU, so whole-frame times
(`gfxinfo`) are dominated by the render thread there: most of the 3.4 s of
`postAndWait` in a trace is the main thread waiting for it. A phone's GPU does
that part in a few milliseconds. The numbers below are main-thread CPU only.

## What it cost, and why

The trace of 0.1.12 showed the first frame after a History tap doing **143–156
ms** of app work: 48–65 ms recomposing, 63–73 ms of measure and layout,
15–20 ms of text layout and about 90 ms of draw recording (Compose measures
inside draw). Then, while the new screen faded in, frame after frame recomposed
5–25 ms with nothing changing. Three causes:

1. **Every tab tap rebuilt the screen from scratch.** Navigation was one
   `AnimatedContent` keyed on the route, so the old tab was disposed and the new
   one composed, measured and laid out in a single frame, every time.
2. **Every screen recomposed on every recomposition of the app root.**
   `AppEnv`, the object every screen takes, was constructed fresh on each
   recomposition of `RebuildApp`, which recomposes on every tab tap. A new
   instance never compares equal, so all visible screens recomposed even when
   no data had changed. That includes the outgoing screen during the fade.
3. **The phone ran a debug build:** debuggable, unminified, no baseline profile.

## What changed

- **`AppEnv` is remembered on its real inputs**: state, platform status, the
  runner, the clocks, the drafts and the calendar day. Screens recompose only
  when something they show changes.
- **Tabs are kept** (`KeepAliveTabs` in `RebuildApp.kt`). Today, History, Plan
  and More are composed once and measured, and only the active one is placed.
  Switching places an already-built screen and fades its layer in, with no
  composition or layout. A hidden tab:
  - is given the `AppEnv` it was last shown with, so it doesn't recompose while
    a set is logged elsewhere;
  - gets a Back dispatcher nothing presses;
  - has its semantics cleared, because unplaced nodes still count for semantics
    (checked with a probe test), and otherwise screen readers and tests would
    see four screens.
- **Tabs are warmed after launch**: 600 ms after the first screen settles, the
  other three are composed one per frame, so the first visit to each is fast
  too.
- **The calendar day is an input.** A kept Today doesn't rebuild on a switch,
  so the day is read again on each root recomposition and at midnight. Without
  this, a kept Today showed last week after midnight into Monday;
  `WeekChecklistDeviceTest` caught it.
- **Release build:** R8 with resource shrinking, a baseline profile for all app
  code (`app/src/main/baseline-prof.txt`) plus the profiles Compose ships,
  installed by `profileinstaller`. It is signed with the same debug key as every
  earlier build, so it installs over them and keeps their data. The APK is
  1.8 MB instead of 12 MB.

Two pitfalls found along the way, both documented in the code:

- **A modifier with a lambda inside an `if` in composable code**, such as
  `if (active) Modifier else Modifier.clearAndSetSemantics { }`. With strong
  skipping, the compiler remembers the lambda inside the branch, and flipping
  the branch discarded and rebuilt the whole tab after it. A probe test showed
  the disposal. The modifier is now built once, outside composition.
- **Choosing between two `CompositionLocalProvider` call sites** with `if`
  likewise rebuilds the content. The provider now has one call site and only its
  value changes.

## Results

Main-thread CPU per tab switch, median of runs (ms):

| Tab | 0.1.12 debug (what was installed) | 0.1.12 code, release build | 0.1.13 release |
| --- | ---: | ---: | ---: |
| History | 198 | 61 | 40 |
| Plan | 308 | 95 | 40 |
| More | 185 | 66 | 42 |
| Today | 177 | 49 | 37 |

The build type alone took the median from 193 to 67 ms. The code changes took it from 67 to 40 ms. For first visits,
warming took History from about 190 ms to 22 ms.

First visit after a fresh launch (3 cold starts each):

| Tab | 0.1.12 debug | 0.1.13 release |
| --- | ---: | ---: |
| History | 172–199 | 20–24 |
| Plan | 308–337 | 35–56 |
| More | 219–236 | 28–42 |
| Today | 189–289 | 38–45 |

In 0.1.13, no frame after a tap spends more than about 5 ms on the main thread:
recomposition ≤5.3 ms, layout 0.2 ms, draw recording ≤3.2 ms. The remaining
time per switch is spread across the fade's frames.
