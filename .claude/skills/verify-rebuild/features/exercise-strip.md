# Exercise strip

The pills across the top of a workout show each exercise's order, name and progress. Tapping one opens it. Holding one and dragging moves it to a new place for today only. Holding one and letting go opens a menu: move it earlier or later, or remove it from today (a planned exercise is skipped and stays in the plan; one added today is removed, with a confirmation when sets are logged).

## Sub-features

- `strip-select` tapping a pill opens that exercise.
- `strip-drag` hold and drag reorders today's workout once, on drop; the plan's order doesn't change.
- `strip-menu` hold and release opens the menu on that pill, without switching the open exercise.
- `strip-remove` "Remove from today" skips a planned exercise; the menu then offers "Bring back to today".
- `strip-blocked` removal is disabled with a reason while a set or the stopwatch is open on it, or once its target is done.

## How to get to it (user POV)

- During any workout, the row of pills under the workout's title.
- The same actions are TalkBack custom actions on each pill, and the ⋮ menu's Reorder dialog.

## Driving it with ui.sh

Preconditions:

- A workout is in progress (`$S/flows.sh start-workout`).

- **Select.** Run `$S/ui.sh tap exercise-chip-1`. The second exercise opens.
- **Menu.** Run `$S/ui.sh tap exercise-chip-0`, then `$S/ui.sh hold exercise-chip-1` and `$S/ui.sh wait "Move earlier" 5`. The menu opens on the second pill with its name, "Move earlier", "Move later" and "Remove from today". The first exercise is still the open one.
- **Move from the menu.** Run `$S/ui.sh tap "Move earlier"`. The two pills swap numbers.
- **Remove and bring back.** Run `$S/ui.sh hold exercise-chip-1` then `$S/ui.sh tap "Remove from today"`. The pill reads "Skipped". Hold it again: the menu offers "Bring back to today"; tap it and its progress returns.
- **Drag.** Run `$S/ui.sh drag exercise-chip-0 700`. Mid-drag the pill lifts with an amber edge; after the drop it is exercise 2, and the strip scrolls with it at the edge.
- **Order is saved.** Run `adb shell am force-stop com.rebuild`, reopen, resume. The new order is still there.
- **Proof.** `$S/evidence.sh record strip-drag 6` while dragging (in a second shell), shots of the menu and the strip after the drop.

## Gotchas

- The menu is a popup window, so its test tags (`exercise-hold-menu`, `hold-move-earlier`, ...) aren't visible to `ui.sh`; use the item texts.
- `drag` needs to pass the neighbour's centre: about 700 px for the first pill on this screen. A shorter drag correctly drops it back.
- With fewer exercises than fit on screen, the strip can't scroll, so edge scrolling can't be shown.
- The device tests in `HoldToReorderDeviceTest` cover the same paths with a fake repository; this map proves them on the real app.
