package com.mradio.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateBadgeTest {
    private val release = AppUpdater.Release(
        tag = "v2.0",
        versionName = "2.0",
        versionCode = 20L,
        notes = emptyList(),
        apkUrl = "https://example.org/radio.apk",
        apkName = "radio.apk",
        apkSize = 1L,
        sha256 = null,
        pageUrl = "https://example.org",
    )

    @Test fun availableAndInstallStatesShowBadge() {
        assertTrue(AppUpdater.State.Available(release).hasPendingUpdate())
        assertTrue(AppUpdater.State.Downloading(release, 0.5f).hasPendingUpdate())
        assertTrue(AppUpdater.State.Ready(release).hasPendingUpdate())
        assertTrue(AppUpdater.State.NeedsPermission(release).hasPendingUpdate())
    }

    @Test fun idleAndCompletedChecksDoNotShowBadge() {
        assertFalse(AppUpdater.State.Idle.hasPendingUpdate())
        assertFalse(AppUpdater.State.Checking.hasPendingUpdate())
        assertFalse(AppUpdater.State.UpToDate(manual = true).hasPendingUpdate())
        assertFalse(AppUpdater.State.Failed("Offline", null).hasPendingUpdate())
    }
}
