# Final affordance correction, 0.1.4/code 5

This packet supersedes only the interaction-cue treatment in the first candidate.
The original candidate packet is preserved unchanged. No palette, comparison,
workout, timer or navigation logic changed in this correction.

- `final-{dark,light}-today.png`: real Monday rest-day launcher, the same neutral
  synthetic fixture as the before/after packet. Alternative Start actions now have
  an outline. Change phase has the existing sliders icon.
- `final-{dark,light}-history-detail.png`: note, missed-set and exercise-history
  actions have existing decorative Lucide icons alongside their labels. They
  retain context descriptions and native interaction roles.
- `scheduled-primary-{dark,light}.png`: actual native production RebuildApp UI
  mounted by ActionAffordanceTest with its **controlled synthetic Tuesday clock
  and minimal two-exercise test template**, not an actual device date or full
  prescribed workout. Shows the filled scheduled Start versus outlined alternatives.
- Other `final-*` screenshots provide the same full-resolution native context
  and 200% initial scroll position as the first candidate. As before, large
  content scrolls; the initial image is not a whole-content screenshot.

API 34, 1080 x 2400 at 420 dpi, system light/dark, normal font 1.0 except entry-200
at 2.0. Keyboard closed in these captures. Final app APK SHA-256:
`1829d6b23e0d219887e8aaab2ce53a842f89883ee77c1483768a3631c9c64fb0`.

The correction's seven focused native role/accessibility/safety cases passed,
including the real negative control. Its scheduled/rest-day role case also ran
in dark. Full final suites are running separately. All 29 explicit dark tokens
remain exact. Physical A71, TalkBack speech and one-handed reach are unverified.
