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
./gradlew :app:assembleDebug -PuseSharedUpdater=true 
```

The token must have read access to the private package/repository. Grant access to the MRadio Actions repository explicitly if required by GitHub Packages. Never put the token in `gradle.properties` committed to Git or in the app manifest. The manually released stable version `0.1.0` is not verified as published. The confirmed automatic development release is `0.1.0-main.2.1`; use that version for initial CI integration.

## Current status
The private library now publishes development packages automatically from `main`. The publishing workflow for `dev.n2k02:updater:0.1.0-main.2.1` succeeded in [run 39930837084](https://github.com/N2K02/n2k02updater/actions/runs/39930837084). MRadio's runtime remains unchanged; ordinary builds still do not resolve the package. Private-package consumption is not yet verified, pending CI access credentials.

## Integration CI validation (2026-10-23)

The feature-branch workflow `Shared updater integration checks` passed its baseline job in run [39921856850](https://github.com/N2K02/MRadio/actions/runs/39921856850). Its private-dependency job was **skipped as designed**: no published version was configured. The baseline proves default builds remain intact, **not** that the private AAR can resolve or that the new updater is wired into runtime UI.

To activate the separate dependency check using the published development package:
1. Under the private updater package's **Package settings → Manage Actions access**, grant `N2K02/MRadio` read access (this authorizes the built-in `GITHUB_TOKEN` from its workflow). GitHub may instead show **Manage Actions access** under package settings depending on the package UI.
2. Set the `N2K02/MRadio` repository Actions variable `SHARED_UPDATER_CI_ENABLED` to `0.1.0-main.2.1`.
3. Push the integration branch or run its workflow and confirm `private-dependency` succeeds. The job now requests `packages: read` and uses `github.actor` / `github.token` by default.
4. Only if repository-to-package Actions access cannot be granted, configure `N2K02_PACKAGES_USER` (repository variable) and `N2K02_PACKAGES_TOKEN` (Actions secret) with read-only package access. Never put this token in source or in the APK.

Do not store credentials in source or distribute them with APKs. Do not remove the legacy updater until release/experimental parity and install safety are validated on devices.

## Compile-time adapter (opt-in only)

The feature branch now contains `app/src/sharedUpdater/java/com/mradio/app/SharedUpdaterBridge.kt`. Gradle compiles this source only when `-PuseSharedUpdater=true` is set. It constructs the shared updater controller with MRadio's release repository, package identity, installed version code, and stable/experimental manifest settings. This is a **dependency compatibility probe**, not a production feature switch: no settings UI, update scheduling, or installer call has been redirected to it. CI must resolve the private package successfully before calling this integration verified.

## Authentication hardening (2026-10-23)

The opt-in `private-dependency` CI job supports GitHub's short-lived `GITHUB_TOKEN` with `packages: read`, provided the private package explicitly grants this repository Actions access. An optional custom read-only package token remains supported as a fallback. This avoids requiring a persistent PAT for the normal case. CI never runs the private-dependency job on pull-request events; it runs only on branch pushes or manual dispatch once `SHARED_UPDATER_CI_ENABLED` is set. The package has not yet been resolved in MRadio CI, so runtime migration remains blocked on that verification.

## Single release channel status (2026-10-23)

Both shared libraries now use a single numeric release channel. The automatic `main` publishing workflows successfully published `dev.n2k02:theme:0.1.3` and `dev.n2k02:updater:0.1.3` ([theme run](https://github.com/N2K02/n2k02theme/actions/runs/39933666981), [updater run](https://github.com/N2K02/n2k02updater/actions/runs/39933678716)). Existing `0.1.0-main.*` packages are historical and no longer the desired integration target.

**Updater blocker:** a request-connection lifecycle issue was found in the shipped `0.1.3` manifest downloader. The fix is under review in [updater PR #4](https://github.com/N2K02/n2k02updater/pull/4). Do not switch MRadio's runtime to the shared manifest loader until a corrected numeric package has been published and compiled by the private-dependency job. The next automatic release is expected to use the next workflow run number; verify the actual published version instead of assuming it.

**Still required:** grant `N2K02/MRadio` read access to the private updater GitHub Package, then set the repository Actions variable `SHARED_UPDATER_CI_ENABLED` to the verified corrected release version. The default MRadio build and its current updater remain unchanged.

## Manifest fix merged; corrected release in progress

The HTTP connection lifecycle fix and manifest parser regression tests were merged in [n2k02updater PR #4](https://github.com/N2K02/n2k02updater/pull/4) (commit `947fc50a6bc60ad1cceb92ddbe35c9781969d63d`). The automatic publishing workflow started in [run 39934998112](https://github.com/N2K02/n2k02updater/actions/runs/39934998112), expected to produce `0.1.4` if successful. Do not set `SHARED_UPDATER_VERSION=0.1.4` until that publication succeeds. Once it does, grant MRadio read access to the private package and run the opt-in dependency CI job. Keep the legacy updater active until dependency and runtime parity are verified.

## Automatic dependency update PRs (configured on integration branch)

The Gradle dependency uses a **literal pinned version**, `dev.n2k02:updater:0.1.4`, instead of an Actions version variable. `.github/dependabot.yml` checks the private Maven registry daily and proposes pull requests when newer updater versions are available; updating this dependency does not silently replace a version in a shipped APK.

**Required one-time setup:**
1. In **MRadio → Settings → Secrets and variables → Dependabot → Secrets**, add `N2K02_PACKAGES_USER` (GitHub username for a package-authorized account) and `N2K02_PACKAGES_TOKEN` (a narrowly scoped package read token authorized for the private updater Maven package). Dependabot registry credentials are separate from Actions secrets, and they must not appear in source.
2. Grant the MRadio Actions repository read access to the updater GitHub Package. For the existing optional integration CI check, set the MRadio Actions variable `SHARED_UPDATER_CI_ENABLED=true` **once**. The job uses the dependency version declared in Gradle, with no version variable.
3. Dependabot processes its config from the repository's **default branch**; these changes are on the integration feature branch and **will not become active until the configuration and pinned Gradle dependency reach MRadio's default branch**. If MRadio's default branch differs from the PR base `ui-rounded-menus`, plan that transition intentionally before expecting PRs.
4. Review the first Dependabot PR and require a successful private-dependency build before merging the update. Current CI excludes ordinary pull request events from accessing private credentials; its opt-in job on trusted branch pushes must be enabled, and the policy for Dependabot PR validation must be completed before updates are adopted automatically.

No stable/experimental runtime migration is enabled here. The pinned `0.1.4` value is known published from successful updater publishing run 39934998112; this says nothing about the MRadio private dependency resolving until access is configured.

## One-secret classic PAT setup

The package username is configured as `N2K02` (public information). **Do not concatenate username and PAT in a secret**: Dependabot's Maven registry config expects separate fields, and the username itself is not sensitive.

Create one secret named `N2K02_PACKAGES_TOKEN` containing only the classic PAT with `read:packages` and permission to read the private `N2K02/n2k02updater` package:
- **Dependabot secrets**: used to authenticate Dependabot to the private Maven registry and propose dependency-update PRs.
- **Actions secrets**: used by the opt-in shared updater integration workflow on trusted pushes. If both automatic updates and integration CI are needed, create the same named secret in **both** GitHub secret stores (GitHub does not automatically share Dependabot secrets with ordinary Actions workflows). The username secret and Actions username variable are no longer required.

No secret values are committed. The integration remains opt-in with `SHARED_UPDATER_CI_ENABLED=true`, and normal builds remain unchanged.

## Private updater dependency verified (2026-10-23)

The trusted branch workflow [run 39939270208, attempt 3](https://github.com/N2K02/MRadio/actions/runs/39939270208) completed successfully: both `baseline` and `private-dependency` passed. The private job built `:app:assembleDebug -PuseSharedUpdater=true` against the published `dev.n2k02:updater:0.1.4` package, with the classic PAT supplied through Actions secrets. This verifies private Maven resolution and Kotlin/Android compilation, **not** runtime behavior on a device. Future private-dependency builds additionally run `:app:testDebugUnitTest`, and Dependabot PR checks are limited to PRs originating in this repository.

The next migration gate is feature parity: when experimental is enabled MRadio's legacy updater compares stable and experimental candidates; the shared source currently selects only one channel. Skip-version, 12-hour throttling, GitHub fallback, diagnostics, and installation permission flows also require matching integration before enabling the new implementation. Keep PR #10 in draft until those behaviors are tested. Dependabot will become active when its configuration reaches the default branch.

## Runtime parity in shared updater (2026-10-23)

The stable-versus-experimental newest-release behavior passed tests and merged via [updater PR #5](https://github.com/N2K02/n2k02updater/pull/5); automatic publishing for the next package is [run 39942026952](https://github.com/N2K02/n2k02updater/actions/runs/39942026952), whose result must be checked before selecting the version. The next shared-library work, [updater PR #6](https://github.com/N2K02/n2k02updater/pull/6), adds optional persisted 12-hour automatic-check throttling and skipped-release support, preserving manual checks. It is not merged or published until CI is green. Once released, the MRadio bridge can pass its existing `app_updater` preferences and switch behaviors incrementally. The existing runtime updater remains the authority until feature parity and device tests are completed.

## Update-offer policy integrated (2026-10-23)

The shared updater's `0.1.6` package was published successfully in [run 39943513298](https://github.com/N2K02/n2k02updater/actions/runs/39943513298). The optional adapter now injects MRadio's existing `app_updater` SharedPreferences into `UpdaterController`. This supports the library's `check(manual = false)` 12-hour policy and `skip(release)` behavior using the same preference keys as the old implementation. The private dependency is pinned to `dev.n2k02:updater:0.1.6` until Dependabot proposes and CI validates a newer release.

The adapter remains compile-only and is not wired to the production UI or lifecycle. Do not switch runtime behavior before validating installation permissions, GitHub fallback, retry/error handling, and device-level updater flows.

## Installer and APK safety audit (2026-10-23)

- The shared updater library declares its own non-exported `FileProvider` at `${applicationId}.n2k02updater.files` with cache path `n2k02-updates/`. This is separate from MRadio's legacy `${applicationId}.updates` provider and requires no manual provider duplication.
- `VerifiedApkDownloader` enforces HTTPS redirects, SHA-256, package identity, a newer APK version, and signing-certificate compatibility before caching a `verified-*.apk` file. `UpdaterInstaller` only opens a verified file from the expected cache directory and invokes the host install guard before starting Android's installer.
- The APK downloader connection lifecycle was fixed and merged in [updater PR #7](https://github.com/N2K02/n2k02updater/pull/7). The publish run is [39945233949](https://github.com/N2K02/n2k02updater/actions/runs/39945233949); wait for its successful conclusion before pinning the next release.
- Remaining device validation: normal and experimental manifest availability, checksum rejection, signature mismatch, Android unknown-apps permission return, interrupted downloads, repeated checks, and no regression to mRadio playback while installing. GitHub fallback still needs a documented decision because the shared controller currently prioritizes the published manifest and does not fall back to GitHub on errors.

## Updater 0.1.7 and GitHub fallback follow-up

Updater `0.1.7` was published successfully in [run 39945233949](https://github.com/N2K02/n2k02updater/actions/runs/39945233949) and pinned for authenticated MRadio CI verification. A separate [draft updater PR #8](https://github.com/N2K02/n2k02updater/pull/8) proposes falling back to public GitHub Releases only when the published manifest fails with an I/O error. It is deliberately not merged: the fallback needs tests and a policy decision about releases lacking SHA-256 digests, because the verified downloader rejects missing hashes. No fallback changes are active in MRadio yet.

## Published-manifest fallback delivered (2026-10-23)

[Updater PR #8](https://github.com/N2K02/n2k02updater/pull/8) passed its regression tests and merged to main. The [0.1.8 package publishing run](https://github.com/N2K02/n2k02updater/actions/runs/39946560947) passed. The opt-in MRadio dependency is now pinned to `dev.n2k02:updater:0.1.8` and should pass both baseline and authenticated private-dependency CI before being considered validated.

Fallback policy: use the GitHub Releases public API only when the published manifest throws an I/O failure, not when the published manifest returns no newer release. A GitHub fallback release without a valid SHA-256 digest is rejected rather than shown as installable. The live legacy updater still controls production update checks and installation. Device-level validation and UI migration remain outstanding.

## Opt-in runtime test adapter (2026-10-23)

`SharedUpdaterBridge` now exposes the shared controller's `status` flow, `check(manual)`, and `skip(release)` methods. It still compiles only when `-PuseSharedUpdater=true` and has no calls from the live UI. The legacy `AppUpdater` remains the only production updater. This prepares controlled checks without adding a second updater to ordinary builds.

For a future device test harness, retain the controller across activity lifecycle changes; verify automatic and manual checks, permission return, interrupted download and recovery, SHA-256 rejection, and installer launch. Do not activate a second concurrent updater or replace the legacy UI until those checks pass. Experimental legacy Actions ZIP fallback is not provided by the shared controller and remains a known parity difference.

## Safe opt-in lifecycle hooks (2026-10-23)

The compile-only `SharedUpdaterBridge` now exposes `download`, `install`, `openInstallPermission`, `onResume`, and `cancel` alongside check/skip/status. Installing always requires a host-supplied `UpdateInstallGuard`; no default allow-all guard is provided. `onResume` retries a pending install permission flow or triggers a throttled automatic check when idle. The bridge is still absent from ordinary builds and has no production UI callers. Real-device validation remains mandatory.

The shared updater's malformed-manifest recovery and quiet automatic-check failure changes merged in [PR #9](https://github.com/N2K02/n2k02updater/pull/9) and [PR #10](https://github.com/N2K02/n2k02updater/pull/10). Wait for the newest package to publish successfully, then bump the opt-in dependency and verify authenticated CI before any runtime rollout.
