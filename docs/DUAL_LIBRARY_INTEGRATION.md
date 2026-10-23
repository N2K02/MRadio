# MRadio dual-library integration

## Goal
Adopt `N2K02/n2k02theme` and `N2K02/n2k02updater` without regressing the existing Material 3 Expressive UI, playback, or updater.

## Current technical state
- Shared updater is an opt-in compile-time dependency (`-PuseSharedUpdater=true`) pinned to `dev.n2k02:updater:0.1.10` on this branch; production update flow remains legacy.
- Shared theme currently exports `N2K02Theme` based on `MaterialTheme`, whereas MRadio's `MRadioTheme` uses `MaterialExpressiveTheme` with a custom radio palette and expressive motion. Do not replace the app theme directly until this contract is reconciled.
- Shared theme README describes source-first integration; verify a published, accessible Maven artifact and its API/version before adding a binary dependency.

## Required implementation gates
1. Verify published theme coordinates and GitHub Packages authentication (or adopt a reviewed composite build).
2. Add an opt-in theme dependency and use shared components in a bounded screen without altering the app-wide theme. Align Compose, Kotlin, and Android Gradle Plugin versions.
3. Preserve existing color contrast, typography, motion, rounded menus, dark/light mode, large-font behavior, and accessibility semantics.
4. Wire shared updater into a separately switchable runtime path, retaining `AppUpdater` as fallback. Ensure release channel precedence, skip-version, throttling, manual check, badge, notes, diagnostics, download verification, signing/package identity, permissions, and playback guard parity.
5. Add CI jobs for both libraries with credentials only on trusted events. Never commit tokens or ship them in APKs.
6. Build a signed experimental APK and repeat device tests for both integrations.
7. Merge only after CI, code review, and explicit release approval. No automatic production cutover.

## Device validation
The earlier updater-only experimental APK passed user-reported install, UI, playback, isolated updater, download, cancellation/retry, and legacy-updater checks. These results do not validate a future dual-library build.
