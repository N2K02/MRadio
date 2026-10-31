package com.mradio.app
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
class SettingsConsistencyContractTest {
    @Test fun updatesStayBelowDividerWithoutLogViewer() {
        val relative = "app/src/main/java/com/mradio/app/RadioSettings.kt"
        val menu = listOf(File(relative), File("../$relative")).first { it.isFile }.readText()
            .substringAfter("if (current == SettingsDestination.MAIN) {")
            .substringBefore("} else if (current == SettingsDestination.PLAYBACK)")
        assertTrue(menu.contains("Make listening your own"))
        assertTrue(menu.contains("headlineContent = { Text(\"Playback & audio\") }"))
        assertTrue(menu.contains("headlineContent = { Text(\"About & updates\") }"))
        assertFalse(menu.contains("headlineContent = { Text(\"Logs\") }"))
        assertTrue(menu.indexOf("HorizontalDivider()") < menu.indexOf("headlineContent = { Text(\"About & updates\") }"))
    }
}
