# Delivered result: Rebuild 0.1.4

The research, independent council ratings, implementation and verification are
complete. The update is a native Android design refinement, not a copied
competitor interface.

## Delivery

- [Install Rebuild 0.1.4](../0.1.4/rebuild-0.1.4-debug.apk)
- [Install and evidence guide](../0.1.4/README.md)
- [Full verification](../0.1.4/verification.md)
- [Final before/after workout comparison](../0.1.4/evidence/comparison-workout-rest.png)
- [Final light workout](../0.1.4/evidence/release-light-workout-rest.png)
- [Final dark workout](../0.1.4/evidence/release-dark-workout-rest.png)
- [Final native image provenance](../0.1.4/evidence/visual-manifest.json)

These relative links resolve in the delivered `Documents/Rebuild/` directory.

## What was applied

1. **Preserved the dark palette.** All 29 explicit dark semantic color roles
   match the frozen 0.1.3 baseline. The charcoal and amber identity was not
   replaced.
2. **Retuned light mode.** Near-neutral, faintly warm surfaces and charcoal
   primary actions replace the broad beige/brown treatment. Warm selected
   details remain. System-aware switching and readable contrast are retained.
3. **Made prior performance scannable.** A dated previous-working-set layout
   separates set number, load, reps and optional RIR. Large text uses labeled
   groups; longer prior sessions have an explicit expansion. Existing
   comparability and prefill rules are unchanged.
4. **Clarified action priority without hiding functionality.** Secondary
   actions are quieter, alternative Start buttons have outlines, and phase,
   note and history controls retain recognizable icons and accessible labels.
   One-tap prefilled logging remains explicit.
5. **Fixed a demonstrated keyboard relocation race.** The existing
   bring-into-view operation now follows the measured scroll viewport rather
   than an earlier composition-time estimate of IME size. Text and targets
   were not shrunk and geometry checks were not weakened.

No training/PDF, phase, prescribed lat-pulldown, set-limit, timer algorithm,
storage-format, account or network changes were added. Competitor images are
not included in the APK or committed source.

## Council outcome

All three reviewers considered the implemented visual candidate an improvement.
Their initial and after-review scores remain separate, with no new score
invented for the final patch or release tests. Fable's action-affordance concern
was corrected and the same reviewer confirmed it resolved. The disagreement
about the light primary button is preserved in the synthesis.

## Independently checked release evidence

- **128 JVM + 62 Android 14 = 190 distinct passing tests.**
- **56 additional Android 13 compatibility tests passed.**
- All executed reports have zero failures, errors and skipped cases. Six
  Android-14-only accessibility cases are SDK-omitted on Android 13, not
  counted as passing there.
- Lint: zero errors, 18 documented warnings and four informational findings.
  Accessibility warning occurrences and their evidence remain in the bundle.
- The formerly intermittent keyboard case passed twelve consecutive targeted
  repetitions, followed by both complete native suites.
- An actual in-place upgrade from 0.1.3 preserved the whole SQLite database
  byte-for-byte, including existing activity, assistance/warm-up history,
  custom overrides, plan revision and a running timer.
- Actual offline explicit set logging started one new rest timer and survived
  background process recreation without losing the post-log state.

Source commit: `ab1a28e15292c8ea0805a660c1ff7ecf9b64f629`.
APK SHA-256:
`53e602e9a5e0985d353dd348292e3fceb66304f7341e9d63daa77d8c53e5d0e4`.
Version: 0.1.4, code 5, 11,274,442 bytes. Signing remains the same Android
Debug/development identity, not a production release key.

## Remaining boundaries

The council's ratings are subjective expert judgments of a bounded visual
sample. They are not a human study, a market-wide ranking or accessibility
certification. Physical Galaxy A71 behavior, actual TalkBack speech and
one-handed reach remain unverified. The existing near-cap data-load latency
and other nonblocking limitations are documented in the release verification.

The original APKs, original audit, reference dossier, three independent initial
reviews, their after-checks and the final visual correction packet are
preserved. Earlier candidate screenshots keep their original hashes and are
not silently presented as the delivered binary.
