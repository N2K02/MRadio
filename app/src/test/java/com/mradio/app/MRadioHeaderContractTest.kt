package com.mradio.app

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Guard against the extra top padding caused by a Settings-owned nested Scaffold. */
class MRadioHeaderContractTest {
    @Test fun everySettingsPageUsesTheHostHeader() {
        val root = File("src/main/java/com/mradio/app")
        val host = File(root, "MRadioScreen.kt").readText()
        val settings = File(root, "RadioSettings.kt").readText()

        assertTrue(host.contains("topBar = {"))
        assertTrue(host.contains("settingsPage.title"))
        assertTrue(host.contains("page = settingsPage"))
        assertTrue(host.contains("onPageChange = { settingsPage = it }"))
        assertTrue(host.contains("SettingsDestination.UPDATES -> Icons.Rounded.SystemUpdate"))
        assertTrue(host.contains("SettingsDestination.PLAYBACK -> Icons.Rounded.GraphicEq"))
        assertTrue(host.contains("getApplicationIcon(ctx.packageName)"))
        assertTrue(host.contains("Text(\n                                \"mRadio\""))

        // No nested scaffold or second TopAppBar can push Settings below the Home header.
        assertFalse(settings.contains("Scaffold("))
        assertFalse(settings.contains("TopAppBar("))
        assertTrue(settings.contains("AnimatedContent("))
    }
}
