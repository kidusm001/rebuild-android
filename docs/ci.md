# CI and releases

## CI

[`ci.yml`](../.github/workflows/ci.yml) runs on every push to `main`, on every pull request, and on demand. It has
two jobs:

- **Unit tests, screenshots, lint and release build.** It runs `testDebugUnitTest` with `REBUILD_SCREENS` set, so the
  Robolectric screenshot tour renders too, then `lintRelease` and `assembleRelease`.
  - Uploads the JUnit results, the screenshots, the lint report and the APK. The APK is signed with the runner's own
    debug key, so it is for checking the build, not for installing over the app on a phone.
- **Device tests**, on API 33 and API 34 in parallel. Each builds the app and its tests, starts a Google APIs emulator
  (Pixel 6 profile, 1080 × 2400 at 420 dpi, the same shape as the development emulator) with KVM, and runs
  `connectedDebugAndroidTest`. API 34 also runs the AccessibilityValidator checks, which need API 34 or later.
  - Uploads the instrumentation results.
  - **API 33 gates every push**: it's the phone's Android 13, and the whole suite passes there. **API 34 is advisory
    for now**: it reports on every push without blocking. Its first run (0.1.17) had 24 known failures of 146. Most
    are keyboard waits timing out on the headless emulator; one is a real accessibility finding, an unlabelled
    12 px-tall element on the plan-removal screen. Once they're fixed, API 34 gates too: remove `continue-on-error`.

Each job writes a pass/fail summary to its run page.

## Releases

[`release.yml`](../.github/workflows/release.yml) runs when a tag like `v0.1.16` is pushed:

1. The tag must equal `versionName` in `app/build.gradle.kts`.
2. The signing key is restored from a secret, then unit tests, lint and a signed release build run.
3. The APK's certificate must be `d834bba6…365b`, the key every Rebuild build has been signed with, or nothing is
   published. An APK signed with another key wouldn't install over the app on the phone.
4. The APK and its `SHA256SUMS` are published as a GitHub Release, with notes from `docs/releases/<version>.md`.

### One-time setup: the signing secrets

The key never goes into the repository. Add it as repository secrets yourself, from the machine that holds it (the
Android debug key in `~/.android/debug.keystore`, with Android's standard debug passwords):

```sh
base64 -w0 ~/.android/debug.keystore | gh secret set REBUILD_KEYSTORE_BASE64 --repo kidusm001/rebuild-android
gh secret set REBUILD_KEYSTORE_PASSWORD --repo kidusm001/rebuild-android --body android
gh secret set REBUILD_KEY_ALIAS --repo kidusm001/rebuild-android --body androiddebugkey
gh secret set REBUILD_KEY_PASSWORD --repo kidusm001/rebuild-android --body android
```

### Making a release

1. Bump `versionCode` and `versionName` in `app/build.gradle.kts`.
2. Add `docs/releases/<version>.md` and a line in `CHANGELOG.md`.
3. Push to `main` and wait for CI to pass.
4. Tag the release:

   ```sh
   git tag v0.1.16
   git push origin v0.1.16
   ```
