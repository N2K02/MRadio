package com.mradio.app

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/** mRadio is the reference layout for the other Android apps' Settings menus. */
class SettingsConsistencyContractTest {
    @Test fun diagnosticAndUpdateRowsRemainAtTheBottom() {
        val relative = "app/src/main/java/com/mradio/app/RadioSettings.kt"
        val source = listOf(File(relative), File("../$relative"))
            .firstOrNull { it.isFile }?.readText() ?: error("Missing $relative")
        val menu = source.substringAfter("if (current == SettingsDestination.MAIN) {", "")
            .substringBefore("} else if (current == SettingsDestination.PLAYBACK)")
        assertTrue(menu.contains("Make listening your own"))
        assertTrue(menu.contains("headlineContent = { Text(\"Playback & audio\") }"))
        assertTrue(menu.contains("headlineContent = { Text(\"Connection diagnostics\") }"))
        assertTrue(menu.contains("headlineContent = { Text(\"About & updates\") }"))
        val divider = menu.indexOf("HorizontalDivider()")
        val logs = menu.indexOf("headlineContent = { Text(\"Connection diagnostics\") }")
        val updates = menu.indexOf("headlineContent = { Text(\"About & updates\") }")
        assertTrue("Settings links must come before the separated diagnostic/update footer",
            divider >= 0 && logs > divider && updates > logs)
    }
}
