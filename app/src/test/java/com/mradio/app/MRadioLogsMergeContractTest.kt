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
        assertTrue(screen.contains("SettingsDestination.LOGS -> Icons.Rounded.Article"))
        assertTrue(logs.contains("Library.events.collectAsState()"))
        assertTrue(logs.contains("Library::clearPlaybackLog"))
        assertTrue(read("app/src/main/java/com/mradio/app/UnifiedLogsPage.kt").contains("AppLog.version.collectAsState()"))
        assertTrue(read("app/src/main/java/com/mradio/app/UnifiedLogsPage.kt").contains("Switch(checked = enabled"))
        assertTrue(read("app/src/main/java/com/mradio/app/UnifiedLogsPage.kt").contains("maxHeight < 430.dp"))
        assertFalse(read("app/src/main/java/com/mradio/app/UnifiedLogsPage.kt").contains("Text(\"Back\")"))
        assertFalse(logs.contains("class LogsActivity"))
        assertFalse(logs.contains("MaterialTheme {"))
        assertFalse(manifest.contains("LogsActivity"))
        assertTrue(settings.contains("leadingContent = { Icon(Icons.Rounded.Article, null) }"))
    }
}
