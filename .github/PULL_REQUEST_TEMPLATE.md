## Summary

<!-- What does this change do, and why? One or two sentences a reviewer can read first. -->

Closes #

## Type of change

- [ ] Bug fix
- [ ] New feature
- [ ] UI / design change
- [ ] Refactor (no behaviour change)
- [ ] Performance
- [ ] Tests, CI or tooling
- [ ] Documentation

## Changes

<!-- The notable changes, one line each. Say what was deliberately left out and why. -->

-

## Screenshots

<!--
Required for any change to what's on screen. Use the same device, theme and state for Before and After.
Add rows for dark mode, 200% text or landscape when the change affects them. Delete this section only if
nothing visible changed.
-->

| Screen | Before | After |
| --- | --- | --- |
|  |  |  |

## Data safety

<!-- Rebuild holds real training history, and every release installs over the last one. -->

- [ ] No change to stored data, the backup codec or migrations
- [ ] Changes stored data: migrations only add, and older snapshots still open and compare record by record

## Testing

- [ ] `./scripts/check.sh` passes (unit tests, Robolectric screenshots, lint, builds)
- [ ] Device tests pass (`./gradlew :app:connectedDebugAndroidTest`), or the affected classes are named below
- [ ] Checked by hand on a device or emulator

<!-- What you ran, on which API level, and anything you couldn't verify. -->

## Accessibility

- [ ] Touch targets stay at least 48 dp
- [ ] Works at 200% text and in landscape
- [ ] TalkBack labels are correct for new or changed controls
- [ ] Contrast meets WCAG AA in light and dark themes

## Release notes

<!-- One line for CHANGELOG.md, or "None". Bump versionName only to publish a release: CI on main publishes it. -->
