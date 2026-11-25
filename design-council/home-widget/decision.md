# Home screen widget: design council decision

## Council seats

Three independent seats evaluated the codebase (`PRODUCT.md`, `DESIGN.md`,
`Theme.kt`, `HomeScreen.kt`, `Week.kt`, `Body.kt`, `RestContext.kt`,
`RebuildApplication.kt`, `MainActivity.kt`, `RestAlarmScheduler.kt`,
`AndroidManifest.xml`, `build.gradle.kts`):

1. **Product & Lifter UX** (Claude Opus 5.5, high effort) — widget job, state
   machine, weekly honesty, tap safety, what stays off, accessibility copy.
2. **Visual & "Ink + Ember" Design** (Gemini 4 Neon, research critic) — Ink +
   Ember surfaces over wallpapers, Signal Amber restraint, `RemoteViews`
   typography, shared Rebuild motifs, WCAG AA contrast table.
3. **Android Platform & Engineering** (Claude Sonnet 5.5, high effort) —
   `RemoteViews` XML vs Jetpack Glance, update lifecycle (commits, midnight
   rollover, rest expiry), responsive sizing API 26–35, deep-linking and blast
   radius.

---

## Unanimous decisions

1. **One adaptive widget ("Today")** (`widgetCategory="home_screen"`). No
   separate rest-timer, body, or quick-start widgets.
2. **Native `AppWidgetProvider` + XML `RemoteViews`** — no Jetpack Glance. Zero
   new dependencies, zero WorkManager/DataStore/secondary SQLite database,
   native `<Chronometer>` support, consistent with `notification_rest_*.xml`.
3. **Pure JVM core (`domain/Widget.kt`).** Every state, label, midnight
   boundary, size bucket, and TalkBack summary comes from pure functions over
   `(AppState, nowMillis, zoneId)` reusing `weekChecklist`, `todayPlan`,
   `opensAt`, and `restContext`. Equality-deduped in `RebuildApplication` so
   unrelated edits (notes, weigh-ins) never push redundant binder updates.
4. **Never start a workout from the home screen.** Tapping an idle scheduled
   widget opens **Today** (never mutates state, prevents pocket-tap phantom
   workouts, preserves twin/extra choosers). Tapping during an active workout
   opens **Workout** (`Routes.WORKOUT`), ignoring the link if an unsaved editor
   is open.
5. **What stays off the widget:**
   - Streaks, progress rings/donuts, motivational copy, PRs, calories, scores.
   - Bodyweight and body measurements (home-screen privacy + single readings
     undermine Rebuild's honest weekly average).
   - Backup reminders and rest-alert permission warnings (actionable only in the
     app; permanent warnings become wallpaper).
   - Set logging / exercise entry.
6. **Ink + Ember opaque surfaces, no borders, no Material You tinting:**
   - Warm Light outer canvas (`#F3EFE9`) with `#FFFFFF` inner tray; Dark raised
     outer card (`#1D1B19`) with `#2A2724` inner tray so it never vanishes on
     pure black or pure white wallpapers.
   - Concentric system widget radii on API 31+ with 20 dp / 12 dp fallback on
     API 26–30.
   - System `sans-serif` + `sans-serif-condensed` with `fontFeatureSettings="tnum"`
     (launcher processes cannot load bundled `@font/archivo` resources from XML
     `RemoteViews`, matching `design-council/app-fonts/decision.md`).
   - Rest day and Week complete stay calm and neutral — zero Signal Amber fills
     or celebration chrome.
7. **Honest update lifecycle & error safety:**
   - `updatePeriodMillis="0"` — no polling.
   - Updates on state commits in `RebuildApplication`, an inexact non-wakeup
     midnight `AlarmManager.set(RTC, ...)` rollover, system time/timezone/boot/
     package/locale broadcasts, `RestAlarmReceiver` expiry, and
     `MainActivity.onResume` self-heal.
   - On snapshot read failure or timeout: render a neutral `"Open Rebuild"`
     fallback and never write or invent defaults.

---

## Adjudicated council tensions

### 1. Direct rest buttons (`+30 s` / `Skip`) & idle `"Start"` label vs read-only widget
- **Tension:** Visual and Platform seats proposed `+30 s` / `Skip` buttons on
  the rest band (reusing `RestAlarmReceiver`'s timer-ID-scoped broadcasts) and
  Visual proposed a `"Start"` pill on idle scheduled days; Product/UX argued the
  widget must be strictly read-only.
- **Decision: Strictly read-only widget (Product/UX wins).**
  - Labeling an idle tap target `"Start"` when tapping opens Today instead of
    starting is dishonest. Idle scheduled days use an emphasized `DayBlock`
    (`secondaryContainer`) with zero fake CTA pill.
  - During rest, the lifter already has `+30 s` and `Skip` on the ongoing
    countdown notification and in-app rest bar, plus PiP. Putting an undo-less
    `Skip` button on the home screen invites accidental taps and adds cold-start
    broadcast lag.
  - Signal Amber fills are reserved for **honest live states**: a `Resume` pill
    during an active workout (pure navigation to `Routes.WORKOUT`) and a docked
    Signal Amber **Rest Band** (full amber takeover on 2×2) during an active
    rest countdown — maximum 3-foot gym legibility with zero accidental writes.

### 2. Weekly context: Mon–Sun 7-day strip vs checklist rows and text tally
- **Tension:** Visual and Platform seats suggested a 7-day `M T W T F S S` chip
  strip; Product/UX rejected it as a habit-streak chain that cannot represent
  multiple routines on one day and makes rest days look like gaps.
- **Decision: Text tally (`Done 2 of 4`) + plain checklist rows at Expanded 4×3
  (Product/UX wins).**
  - Mirrors `DESIGN.md` ("This week is a plain Monday–Sunday checklist...
    without a progress ring or metric hero").
  - Standard (4×2) shows `"Done 2 of 4"` (hidden when `routines` is empty, never
    `0 of 0`).
  - Expanded (4×3) idle shows up to 4 scheduled routine rows in plan order
    (`MON Push · Done · 23 Nov`, `THU Legs · Partial · not counted`, `SAT Push
    (2 of 2) · Not done`, with `✓` only beside `"Done"`) plus a quiet `"Also
    logged this week"` line for extras/other if present.
  - Expanded (4×3) active workout shows `SessionTrack` + a 4-exercise window
    centered on the current exercise (`(✓)` done, amber `(2)` current, neutral
    `(3)` pending, plus `+N more` and `Opens ...`) so a mid-workout glance shows
    real session progress.

### 3. Rest countdown when exact alarms are denied (`-0:01` drift)
- **Tension:** Platform noted a `Chronometer` counts negative (`-0:01`) if
  best-effort alarm delivery lags; Product/UX required honest fallback when
  exact alarms are off.
- **Decision: Honest precision gating.**
  - When `canUseExactAlarms()` is true (all API 26–30, or granted on API 31+),
    render a live counting-down `<Chronometer>` and refresh the widget in
    `RestAlarmReceiver`'s `ACTION_EXPIRE` `finally` block.
  - When `canUseExactAlarms()` is false on API 31+, show static `"Until
    6:42 PM"` instead of a countdown that could drift negative.
  - If `deadline <= now` before the timer clears, never show a clock — show
    `"Rest over"` + `RestContext.next`.

### 4. Outer surface color (Light `#F3EFE9` vs `#FFFFFF`)
- **Decision:** Light outer `#F3EFE9` (`surface`) + inner tray `#FFFFFF`
  (`surfaceContainerLowest`); Dark outer `#1D1B19` (`surfaceContainerLowest`) +
  inner tray `#2A2724` (`surfaceContainerHigh`) + tonal chips `#34302C`
  (`surfaceContainerHighest`). Separates cleanly from white and AMOLED black
  wallpapers without borders, preserving Ink + Ember's tonal card hierarchy.
  Android's out-of-process `Chronometer` uses zero-padded minutes (`02:55`) and
  is accepted as a `RemoteViews` platform exception matching the rest
  notification.

### 5. `MainActivity` launch mode
- **Decision:** Set `android:launchMode="singleTop"` on `MainActivity` with
  `onNewIntent` delivering a semantic `WidgetLink` (`TODAY` / `WORKOUT`) that
  is consumed once, ignored on `FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY`, ignored
  while `Routes.isEditor(nav.current)` or setup/receipt is open, and falls back
  to Today if the workout is no longer active.

---

## Responsive size tiers

Default size: `targetCellWidth="4"`, `targetCellHeight="2"`, resizable both
ways (`minResizeWidth="110dp"`, `minResizeHeight="40dp"`). API 31+ uses
`RemoteViews(Map<SizeF, RemoteViews>)`; API 26–30 picks via `widgetSizeFor`.

| Tier | Breakpoint | Content |
| --- | --- | --- |
| **Strip** (2×1–4×1) | `heightDp < 100` | Single-line summary (`Push · Today`, live clock + `Next: set 2 of 3`, `Rest day`). |
| **Compact** (2×2) | `widthDp < 220` | Eyebrow + headline + 1 fact; `SessionTrack` + `Resume` pill when active; full amber rest takeover when resting. |
| **Standard** (4×2, default) | `widthDp >= 220`, `heightDp < 255` (or `fontScale > 1.3f`) | `DayBlock` + headline + summary + `"Done X of N"`; active adds `SessionTrack` + `Resume`; resting shows calm header + docked Signal Amber Rest Band. |
| **Expanded** (4×3+) | `widthDp >= 220`, `heightDp >= 255`, `fontScale <= 1.3f` | Standard header + inner tonal tray: weekly checklist rows when idle, 4-exercise progress window (`+N more`) when active, or tight Up-next tray + docked Rest Band when resting. |

---

## Redesign (25 Nov 2026)

The first build passed its critics but not its owner: the 4×2 was a thin band
of text floating in a tall card, rest turned the whole widget into an amber
slab, and copy contradicted itself ("Rest day" beside "Done 0 of 2"). Three
directions were mocked up (week board, big numeral, session list); the owner
chose a hybrid:

- **Idle and active use the session list.** The widget mirrors Today's card:
  name, one quiet line, numbered rows. Scheduled days list today's exercises
  with plan targets; other days list the week's routines by weekday. Active
  workouts list exercises with counts around the current one (one row of
  context above it), with Resume beside the name.
- **Rest uses the big numeral.** A 64 sp condensed countdown beside the next
  exercise, its set, and last time's same-unit set at that position (the
  pairing the set ledger uses). Amber is reduced to the label, badge and pill;
  there is no full amber fill.
- **The list fills the real cell.** Launchers give 4×2 very different heights
  (this emulator: about 222 dp), so `widgetListRows` picks one to seven 30 dp
  rows for the height the launcher reports.
- **Dropped:** the DayBlock, the progress track, the separate 4×3 layout, the
  "Also logged this week" note and the inner tray colours. Light now uses a
  white card, which reads more cleanly against wallpapers than the warm canvas.

### App fonts (25 Nov 2026)

The owner asked for the widget to use the app's fonts. A launcher inflates widget
layouts in a restricted context that ignores `@font/` resources (tested on the
API 34 emulator: an `@font/archivo` title still drew in Roboto, as the
notification shade does in `design-council/app-fonts`). So:

- `WidgetTextPainter` sets each string in Archivo (or Archivo Numerals for the
  static rest time) as an `ALPHA_8` mask, wrapped and ellipsized to the width it
  will occupy; the layouts are ImageViews tinted with the widget colour
  resources, so the launcher still resolves light or dark itself.
- Exact widths need the exact cell, so the widget ships one layout for the size
  the launcher reports in the current orientation and re-renders on every resize
  (`onAppWidgetOptionsChanged`, now on all API levels) and rotation. A size map
  misbehaved on Pixel Launcher: it applied a landscape or a 110 dp floor layout
  to a portrait cell. Orientation is read from the physical display, because
  while Rebuild is in picture-in-picture even its application configuration
  reports landscape.
- The live rest countdown and stopwatch stay platform Chronometers in
  `sans-serif-condensed`: the launcher ticks them, so they can't be drawn by
  the app.

### Idle fill (25 Nov 2026)

The owner found the idle 4×2 too empty on rest days. Three directions (up next,
last-workout recap, week tiles) went to two Opus critics (product/lifter UX and
visual craft). Both rejected the week tiles (an amber chain reads as a streak;
outlines and "done" amber break the system) and a "best set" recap (reads as a
score). Built: an **Up next** preview under the week list (heading naming the
routine and its exercise count, then its planned exercises with targets), and for
a complete week a quiet **Last workout** recap (date, routine, logged sets,
minutes only when believable). Whole or nothing: the heading plus at least two
exercises must fit, past days give way first, else the subtitle says what's
next. Future days show no status; idle has no amber; content is top-anchored.
Rows are 28 dp so the preview fits a ~223 dp 4×2 cell at normal text.
