# Native icon assets

Rebuild uses a curated subset of **Phosphor Icons 2.1.1, Bold weight**, taken from
the published `@phosphor-icons/core@2.1.1` package (`assets/bold/*-bold.svg`):
https://github.com/phosphor-icons/core/tree/v2.1.1/assets/bold

The SVG paths are converted to Android VectorDrawable without redrawing their
geometry: 256-unit viewport rendered in a 24 dp box, one filled path per glyph
(Phosphor draws its outlines as filled shapes, not strokes). Bold's outline is
24 units on the 256 grid, about 2.25 dp at 24 dp, so the icons sit at the weight
of the app's semibold labels. Compose supplies the current Material content tint.
Back, Next, Undo skip and the row chevron mirror in RTL (`autoMirrored`).

Phosphor replaced Lucide 0.468 after 0.4.1. Resource names were kept so no call
site changed; the table maps each resource to its upstream Phosphor icon.

| Resource | Purpose | Phosphor icon |
| --- | --- | --- |
| `ic_dumbbell` | Today/workout, Exercises | barbell |
| `ic_history` | History | clock-counter-clockwise |
| `ic_clipboard_list` | Plan / target details / Save to plan | clipboard-text |
| `ic_sliders_horizontal` | More/settings, training phase | sliders-horizontal |
| `ic_arrow_left` | Back | arrow-left |
| `ic_arrow_right` | Next exercise | arrow-right |
| `ic_arrow_up` / `ic_arrow_down` | Move exercise up / down | arrow-up / arrow-down |
| `ic_pencil` | Correction/plan edit | pencil-simple |
| `ic_notebook_pen` | Workout and exercise note | note-pencil |
| `ic_alarm_clock_plus` | (unused) rest extension | alarm |
| `ic_skip_forward` | Skip rest / skip today | skip-forward |
| `ic_x` | Dismiss, remove, discard | x |
| `ic_ellipsis_vertical` | Options | dots-three-vertical |
| `ic_check` | Completed target | check |
| `ic_minus` / `ic_plus` | Set target adjustment, add exercise | minus / plus |
| `ic_rest` | Rest notification, stopwatch | timer |
| `ic_play` | Start / resume workout or stopwatch | play |
| `ic_pause` | Pause stopwatch | pause |
| `ic_square` | Stop stopwatch | stop |
| `ic_search` | Library search / Browse exercises | magnifying-glass |
| `ic_archive_restore` | Backup and restore | box-arrow-up |
| `ic_book_open` | Training guidance | book-open |
| `ic_chevron_right` | Grouped More rows | caret-right |
| `ic_chevron_down` / `ic_chevron_up` | Filter menus, expanders | caret-down / caret-up |
| `ic_undo_2` | Undo skip | arrow-u-up-left |
| `ic_library` | Your routines / Save as routine | books |
| `ic_calendar` | Change a routine's day | calendar-blank |
| `ic_calendar_plus` | Add a routine to a weekly plan | calendar-plus |
| `ic_refresh_cw` | Update a copy from the library | arrows-clockwise |
| `ic_building` | Setup: gym | building |
| `ic_person_standing` | Setup: bodyweight | person-simple |
| `ic_smartphone` | Setup: stays on this phone | device-mobile |
| `ic_lock` | Setup: no pressure | lock |
| `ic_bell` | Setup: rest alerts | bell |

`calendar-blank` is used instead of Phosphor's `calendar`, which prints a fixed
"12" inside the grid.

The existing labeled tabs, selected pill/color and 48 dp interactive parents are
unchanged. Symbols render at 24 dp regardless of font scaling. No icon font,
network access or runtime icon dependency is used.

The adaptive launcher and splash icon use the same barbell path, scaled from the
256 grid into a 54.4 dp square centered on the 108 dp canvas (the glyph itself is
about 51 x 34 dp, inside Android's 66 dp safe circle) on the existing warm-amber
background. The foreground is separate from the background;
the rest notification remains a monochrome transparent small icon. The pre-26
fallback is also a vector, although the app's minimum SDK is 26.

Phosphor Icons is MIT licensed. Its copyright and permission notice is included
verbatim in `app/src/main/assets/licenses/Phosphor-MIT.txt`, packaged in the APK
(assets are not removed by `shrinkResources`, unlike unreferenced `res/raw` files).
Only the listed assets are included, not an unreviewed icon bundle.
