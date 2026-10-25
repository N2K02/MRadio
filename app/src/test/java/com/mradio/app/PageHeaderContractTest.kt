package com.mradio.app

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Regression guard: one host-owned header aligned identically on all screens. */
class PageHeaderContractTest {
    private fun source(filename: String): String {
        val relative = "com/mradio/app/$filename"
        return listOf(File("src/main/java", relative), File("app/src/main/java", relative))
            .firstOrNull { it.isFile }?.readText()
            ?: error("Missing app UI source: $relative")
    }

    @Test fun pageHeaderHasLeadingIconAndRightSideAppName() {
        val host = source("MRadioScreen.kt")
        val header = host.substringAfter("topBar = {").substringBefore("bottomBar = {")
        assertTrue("Missing Settings page icon in the shared header", header.contains("Icons.Rounded.Settings"))
        assertTrue("Missing Settings subpage icon", header.contains("SettingsDestination.UPDATES -> Icons.Rounded.SystemUpdate"))
        assertTrue("Missing page label", header.contains("settingsPage.title"))
        assertTrue("Missing launcher icon", header.contains("getApplicationIcon(ctx.packageName)"))
        assertTrue("Missing mRadio name", header.contains("\"mRadio\""))
    }

    @Test fun settingsDoNotProvideASecondAppBar() {
        val settings = source("RadioSettings.kt")
        assertFalse(settings.contains("topBar = {"))
        assertFalse(settings.contains("Scaffold("))
        assertFalse(settings.contains("N2K02UpdateTitle("))
    }
}
