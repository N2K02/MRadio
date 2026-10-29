package com.mradio.app

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MRadioLogsMergeContractTest {
    private fun read(path: String): String = listOf(File(path), File("../$path"))
        .firstOrNull { it.isFile }?.readText() ?: error("Missing $path")

    @Test fun connectionDiagnosticsMovedInsideThemedLogsSubpage() {
        val settings = read("app/src/main/java/com/mradio/app/RadioSettings.kt")
        val destinations = read("app/src/main/java/com/mradio/app/RadioDestination.kt")
        val logs = read("app/src/main/java/com/mradio/app/LogsActivity.kt")
        val screen = read("app/src/main/java/com/mradio/app/MRadioScreen.kt")
        val manifest = read("app/src/main/AndroidManifest.xml")
        assertTrue(destinations.contains("LOGS -> \"Logs\""))
        assertFalse(destinations.contains("DIAGNOSTICS"))
        assertTrue(settings.contains("onPageChange(SettingsDestination.LOGS)"))
        assertTrue(settings.contains("MRadioLogsPage("))
        assertTrue(screen.contains("SettingsDestination.LOGS -> Icons.Rounded.BugReport"))
        assertTrue(logs.contains("Library.events.collectAsState()"))
        assertTrue(logs.contains("Library::clearPlaybackLog"))
        assertTrue(logs.contains("AppLog.version.collectAsState()"))
        assertTrue(logs.contains("Switch(checked = enabled"))
        assertTrue(logs.contains("maxHeight < 530.dp"))
        assertTrue(logs.contains("onClick = onBack"))
        assertFalse(logs.contains("class LogsActivity"))
        assertFalse(logs.contains("MaterialTheme {"))
        assertFalse(manifest.contains("LogsActivity"))
    }
}
