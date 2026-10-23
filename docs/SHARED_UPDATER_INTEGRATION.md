# MRadio shared updater migration (staged)

## Baseline
This branch starts from `ui-rounded-menus`, preserving the existing working updater and its settings UI. Do not remove `AppUpdater.kt`, `UpdateUi.kt`, `UpdateFileProvider.kt`, or `UpdateBadge.kt` until the replacement is feature-complete and tested.

## Shared library
Source: private `N2K02/n2k02updater`, merged to `main`, module `updater`.

**Important:** MRadio is public and the library is private. Do not add an unauthenticated GitHub Packages dependency or commit a GitHub token to the app. Until a private dependency delivery strategy is configured (or source is deliberately made public), this branch does not enable a build-breaking dependency.

## Required compatibility before switching
- Preserve stable and experimental manifest channels from `https://n2k02.github.io/mradio/updates`.
- Keep stable GitHub Releases fallback only where MRadio currently allows it; never silently replace experimental builds with stable.
- Preserve manual checks, skip-version, update badge, 12-hour check throttling, release notes, progress, retry, and install permission handling.
- Verify SHA-256, package identity, strictly newer version, and signing certificate before installation.
- Never install during playback-critical operations if MRadio introduces a guard.
- Keep existing Material 3 Expressive settings layout and existing accessibility strings until UI parity is reviewed.

## Private dependency delivery decision

The shared updater is a **private Android library** while MRadio is public. The safest immediate approach is to publish an authenticated Maven artifact from the private library CI and give MRadio CI a narrowly scoped read-only package credential. Configure Gradle credentials from environment variables in CI only, never in app source or APK. Do not add the dependency until the private package exists and a credential has been configured. For public fork PRs, avoid private dependency resolution or run a restricted build without access to secrets. No credential is currently configured by this document.

## Safe rollout
1. Finish and test the shared updater library independently.
2. Choose a secure delivery method for the private library (e.g. authenticated GitHub Packages CI access).
3. Add the dependency to the feature branch only, with CI secrets scoped to GitHub Actions; no token in the Android APK.
4. Introduce an adapter from MRadio release channel preferences and state to the shared controller.
5. Compare stable/experimental behavior with current `AppUpdater`, including signing and failed downloads.
6. Switch the settings screen behind a feature flag; keep the legacy implementation as rollback.
7. Require successful CI and manual device tests before requesting approval to merge.

## Opt-in Gradle dependency (now configured)
Default CI and local builds **do not** resolve the private library. To test after the package is published, set `N2K02_PACKAGES_USER` and `N2K02_PACKAGES_TOKEN` in the build environment, then run:

```bash
./gradlew :app:assembleDebug -PuseSharedUpdater=true -PsharedUpdaterVersion=0.1.0-main.2.1
```

The token must have read access to the private package/repository. Grant access to the MRadio Actions repository explicitly if required by GitHub Packages. Never put the token in `gradle.properties` committed to Git or in the app manifest. The manually released stable version `0.1.0` is not verified as published. The confirmed automatic development release is `0.1.0-main.2.1`; use that version for initial CI integration.

## Current status
The private library now publishes development packages automatically from `main`. The publishing workflow for `dev.n2k02:updater:0.1.0-main.2.1` succeeded in [run 39930837084](https://github.com/N2K02/n2k02updater/actions/runs/39930837084). MRadio's runtime remains unchanged; ordinary builds still do not resolve the package. Private-package consumption is not yet verified, pending CI access credentials.

## Integration CI validation (2026-10-23)

The feature-branch workflow `Shared updater integration checks` passed its baseline job in run [39921856850](https://github.com/N2K02/MRadio/actions/runs/39921856850). Its private-dependency job was **skipped as designed**: no published version was configured. The baseline proves default builds remain intact, **not** that the private AAR can resolve or that the new updater is wired into runtime UI.

To activate the separate dependency check using the published development package:
1. Under the private updater package's **Package settings → Manage Actions access**, grant `N2K02/MRadio` read access (this authorizes the built-in `GITHUB_TOKEN` from its workflow). GitHub may instead show **Manage Actions access** under package settings depending on the package UI.
2. Set the `N2K02/MRadio` repository Actions variable `SHARED_UPDATER_VERSION` to `0.1.0-main.2.1`.
3. Push the integration branch or run its workflow and confirm `private-dependency` succeeds. The job now requests `packages: read` and uses `github.actor` / `github.token` by default.
4. Only if repository-to-package Actions access cannot be granted, configure `N2K02_PACKAGES_USER` (repository variable) and `N2K02_PACKAGES_TOKEN` (Actions secret) with read-only package access. Never put this token in source or in the APK.

Do not store credentials in source or distribute them with APKs. Do not remove the legacy updater until release/experimental parity and install safety are validated on devices.

## Compile-time adapter (opt-in only)

The feature branch now contains `app/src/sharedUpdater/java/com/mradio/app/SharedUpdaterBridge.kt`. Gradle compiles this source only when `-PuseSharedUpdater=true` is set. It constructs the shared updater controller with MRadio's release repository, package identity, installed version code, and stable/experimental manifest settings. This is a **dependency compatibility probe**, not a production feature switch: no settings UI, update scheduling, or installer call has been redirected to it. CI must resolve the private package successfully before calling this integration verified.

## Authentication hardening (2026-10-23)

The opt-in `private-dependency` CI job supports GitHub's short-lived `GITHUB_TOKEN` with `packages: read`, provided the private package explicitly grants this repository Actions access. An optional custom read-only package token remains supported as a fallback. This avoids requiring a persistent PAT for the normal case. CI never runs the private-dependency job on pull-request events; it runs only on branch pushes or manual dispatch once `SHARED_UPDATER_VERSION` is set. The package has not yet been resolved in MRadio CI, so runtime migration remains blocked on that verification.
