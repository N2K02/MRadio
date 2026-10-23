# MRadio shared updater migration (staged)

## Baseline
This branch starts from `ui-rounded-menus`, preserving the existing working updater and its settings UI. Do not remove `AppUpdater.kt`, `UpdateUi.kt`, `UpdateFileProvider.kt`, or `UpdateBadge.kt` until the replacement is feature-complete and tested.

## Shared library
Source: private `N2K02/n2k02updater`, draft PR #1, module `updater`.

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

## Current status
Preparation only. No runtime behavior changed, and no dependency to the private repository has been introduced.
