# Native icon assets

Rebuild uses a curated subset of **Lucide 0.468.0**, pinned to official repository
commit `f12b0de177fbc2a6795e99be065887e72b237123`:
https://github.com/lucide-icons/lucide/tree/f12b0de177fbc2a6795e99be065887e72b237123/icons

The SVG paths are converted to Android VectorDrawable without redrawing their
geometry: 24-unit viewport, 2-unit stroke, round caps and joins, transparent fill.
SVG lines, circles, polygons and rounded rectangles are equivalent path geometry.
Compose supplies the current Material content tint. Back, Next, Undo skip and the
row chevron mirror in RTL. Arrow-right, play, search, archive-restore, book-open and
both chevrons (added in 0.1.6), arrow-up, arrow-down and undo-2 (added in
0.1.8), library, calendar, calendar-plus and refresh-cw (added in 0.1.9), and pause
and square (added in 0.1.10), were compared path-for-path with the pinned upstream
SVGs. Calendar's, pause's and square's rounded rectangles are written as their
equivalent paths.

| Purpose | Upstream SVG |
| --- | --- |
| Today/workout | dumbbell |
| History | history |
| Plan / target details | clipboard-list |
| More/settings | sliders-horizontal |
| Back | arrow-left |
| Next exercise | arrow-right |
| Correction/plan edit | pencil |
| Workout note | notebook-pen |
| Rest extension | alarm-clock-plus |
| Skip rest / dismiss expiry | skip-forward / x |
| Options | ellipsis-vertical |
| Completed target | check |
| Set target adjustment | minus / plus |
| Android rest notification | timer |
| Start / resume workout | play |
| Library search / Browse exercises | search |
| Backup and restore | archive-restore |
| Training guidance | book-open |
| Grouped More rows | chevron-right |
| Library filter menus | chevron-down |
| Move exercise up / down (this workout only) | arrow-up / arrow-down |
| Undo skip | undo-2 |
| Add exercise / Start empty workout | plus |
| Skip today | skip-forward |
| Remove added exercise / dismiss notice | x |
| Save to plan | clipboard-list |
| Your routines / Save as routine | library |
| Add a routine to a weekly plan | calendar-plus |
| Change a routine's day | calendar |
| Update a copy from the library | refresh-cw |
| Stopwatch / cardio Start rest / stopwatch strip | timer |
| Pause stopwatch | pause |
| Stop stopwatch | square |
| Resume stopwatch | play |
| Discard a stopwatch effort | x |

The existing labeled tabs, selected pill/color and 48 dp interactive parents are
unchanged. Symbols render at 24 dp regardless of font scaling. No new icon font,
network access or runtime icon dependency is used; the unused extended Material
icon dependency was removed.

The adaptive launcher uses the same dumbbell paths centered within Android's safe
zone on the existing warm-amber background. Its foreground is separate from the
background; the rest notification remains a monochrome transparent small icon.
The pre-26 fallback is also a vector, although the app's minimum SDK is 26.

Lucide is ISC licensed, with portions derived from Feather (MIT). The upstream
copyright and permission notice is included verbatim in
`app/src/main/res/raw/lucide_license.txt`, packaged in the APK. Only the listed
assets are included, not an unreviewed icon bundle.
