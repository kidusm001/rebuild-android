# Rest alerts

How the end of a rest is announced, chosen in More › Rest alert: a sound (Buzz, Long buzz, Chime, Alarm that rings until stopped, or Silent) and, while Rebuild is open, something on screen (None, Edge light, or Up next, where the rest bar fills the screen with the next set). Silent can't be paired with nothing on screen. The rest notifications show a live countdown with the exercise and what's next, with +30 s and Skip, then "Rest over" with the next set.

## Sub-features

- `alert-settings` the two groups of choices, the Silent rule, and Try alert.
- `alert-edge-light` the screen's edges glow amber three times; touches still reach the app.
- `alert-up-next` the rest bar grows to fill the screen: "Rest over", the exercise, the set number rolling up, then it drops away. Tap dismisses it early.
- `alert-alarm` Alarm rings until stopped; the rest bar's slot shows "Rest over", the overtime and Stop. Opening Rebuild or the notification's Stop also silences it.
- `alert-countdown-notification` the countdown notification: live clock, exercise, next set, +30 s and Skip.
- `alert-rest-over-notification` "Rest over" with "Set 2 of 3" and the exercise.
- `alert-on-time` with Rebuild open, the rest ends exactly at 0:00 and the alert is sounded from the app.

## How to get to it (user POV)

- More › Rest alert (the row under "Rest alerts").
- Every rest that ends while you're resting.
- The notification shade during and after a rest.

## Driving it with ui.sh

Preconditions:

- A phase is chosen; notifications and exact alarms are granted (`emu.sh install` does both).

- **Settings.** Run `$S/flows.sh rest-alert SILENT`. "None" under On screen is disabled with "A silent alert needs something on screen", and Edge light is selected instead.
- **Try it.** Run `$S/flows.sh rest-alert CHIME SWEEP`, then open More › Rest alert again and `$S/ui.sh tap rest-alert-try`. Up next fills the screen and drops away.
- **A real rest.** Run `$S/flows.sh start-workout`, `$S/flows.sh log-set 40 10`, `$S/flows.sh set-rest 30`. Wait for "0:05" (`$S/ui.sh wait 0:05 40`), then `$S/evidence.sh record rest-end 8` and `$S/evidence.sh frames rest-end 15`. Frames show the takeover rising from the bar, holding, and leaving.
- **Countdown notification.** During a rest run `$S/ui.sh home` then `$S/evidence.sh notifications countdown`. The shade shows the big clock, the exercise and "Next: set 2 of N". Expand it for +30 s and Skip.
- **Rest over notification.** After the rest ends, `$S/evidence.sh notifications rest-over`. It shows "Set 2 of N", "Rest over" and the exercise.
- **Alarm.** Choose `ALARM`, run a real rest to its end, then `adb shell dumpsys notification --noredact | grep -A3 "com.rebuild"`. The channel is `rest-complete-alarm` with `FLAG_INSISTENT` (flags include `0x4`). `$S/ui.sh tap rest-end-stop` silences it (the notification is gone from `dumpsys`).
- **Proof.** Frames of the motion, both notification shots, and the `dumpsys` lines for the alarm.

## Gotchas

- The emulator renders in software and drops frames; judge motion from frames, smoothness only on a real phone.
- The edge light can't be rendered by the JVM screenshot harness on dark (additive blending); use the emulator.
- Short rest-pause pauses never ring the alarm; they buzz.
- `dumpsys` shows the notification only while it's posted; capture it before stopping the alarm.
