# Rebuild native design

A lifter uses the phone under bright gym lighting, then in a dim room at home:
respect system light/dark mode rather than imposing either.

Use restrained Material 3 surfaces, a muted warm amber primary accent, system
Roboto typography and standard native navigation. Colors are implemented as
Compose sRGB equivalents of a low-chroma warm-neutral palette; no web-only
color machinery. Accent indicates actions and selection, not decoration.

Workout is the primary destination. A compact exercise picker sits above one
focused exercise's target, previous comparable results and numeric entry, before
the growing completed-set table. Baseline, advisory upper and rest settings live
under Target details; they remain distinct from today's chosen target. After the
target is met, Next is primary and logging an extra set is explicit but unrestricted.
Rest remains visible without preventing logging. History, plan,
backup and concise help are secondary. Use open lists and dividers instead of
nested cards. Destructive replacement/discard requires explicit confirmation.

Minimum 48 dp touch targets, scalable labels, scrollable content with IME insets,
clear errors and bottom-reachable primary actions. Charts depict logged kg and
reps only, with textual equivalents and separate exercise/loading identities.

Respect user font scale. Headers wrap outside fixed toolbar heights; confirmation
bodies scroll with independently stacked actions. The compact rest row consumes
safe horizontal/navigation insets and stays above the IME. During landscape
numeric entry, secondary chrome and the exercise picker temporarily give way to
exercise/loading context, side-by-side fields and Log, and rest controls.
Android Back dismisses the keyboard and restores navigation without logging.
Portrait entry scrolls its complete field/action group into view as the IME opens.

Validation identifies and focuses the invalid field without clearing input.
Saving and target completion have explicit feedback; the countdown does not
announce every second. History and saved notes are read-first, with explicit
correction/Add/Edit actions. Unsaved notes still use the durable navigation guard.
Native emulator geometry and semantics checks are evidence, not a claim of
physical-device or TalkBack speech certification.

Icons use the curated Lucide rounded-outline family: a 24 dp optical box,
2-unit stroke and round caps/joins. Workout, history, plan and settings use
dumbbell, history-clock, clipboard-list and sliders glyphs. Keep the visible tab
labels and selected pill/color; selection never depends on an icon alone.
Utility icons share the same family and keep their existing accessible names
and 48 dp interactive parents (distinct from Material's 40 dp visual button).
The launcher uses the same dumbbell in Android's adaptive-icon safe zone.
See `docs/icons.md` for pinned provenance and packaged licensing.
