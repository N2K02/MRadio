# Shared updater release readiness

Status: **not approved for production activation**. This checklist documents reported device results and outstanding gates. It does not change the active MRadio updater.

## Evidence
- Experimental preview PR #11: https://github.com/N2K02/MRadio/pull/11
- Staged integration PR #10: https://github.com/N2K02/MRadio/pull/10
- Experimental Build APK run #232 passed: https://github.com/N2K02/MRadio/actions/runs/39950242145
- Tester reported PASS for: install/launch, rounded menus/settings, playback, isolated test screen, update availability and APK download, cancellation/errors/retry, and legacy production updater unchanged.
- These are tester-reported results, not independently captured device logs.

## Required gates before activation
- [ ] Confirm pinned `dev.n2k02:updater:0.1.10` was published, resolves in trusted CI, and has passed its own unit tests.
- [ ] Record test device, Android version, app version/build SHA, test date, and tested release channels.
- [ ] Compare stable and experimental candidate selection, skipped versions, manual and automatic checks, 12-hour throttling, GitHub fallback, release notes, progress, retries, diagnostics, and badge behavior against legacy.
- [ ] Verify APK SHA-256, package identity, strictly increasing version code, signing certificate continuity, unknown-app installation permission, and permission-return flow.
- [ ] Verify install guard blocks installer launch while host playback or recording safety conditions require it.
- [ ] Review credential boundaries: no package PAT in source, APK, logs, or untrusted PR jobs; Dependabot registry auth and Actions auth configured separately.
- [ ] Verify dependency update PRs run the required checks before merge; pin and review version changes.
- [ ] Confirm rollback path keeps `AppUpdater` and existing production UI operational.
- [ ] Run clean baseline and opt-in integration CI on the exact release candidate SHA.
- [ ] Obtain explicit approval for runtime activation and separately for merge/release.

## Rollout
1. Keep PR #10 and PR #11 draft during readiness review.
2. Make any runtime adapter or feature-flag changes on a separate reviewable PR.
3. Enable the shared updater only behind an explicit opt-in switch after parity gates pass.
4. Test stable and experimental channels on a signed device build.
5. Preserve a rollback switch to legacy updater and verify it before broad rollout.

Do not treat a passing APK build or isolated test screen as evidence that production runtime routing is complete.
