package com.mradio.app
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
class NoAppLogsContractTest {
    private fun src(p: String) = listOf(File(p), File("../$p")).first { it.isFile }.readText()
    @Test fun noLogsDestination() {
        assertFalse(src("app/src/main/java/com/mradio/app/RadioDestination.kt").contains("LOGS"))
        assertFalse(src("app/src/main/java/com/mradio/app/RadioSettings.kt").contains("MRadioLogsPage"))
    }
    @Test fun noPrivateLogJournal() {
        assertTrue(src("app/src/main/java/com/mradio/app/AppLog.kt").contains("Log.d("))
        assertFalse(src("app/src/main/java/com/mradio/app/AppLog.kt").contains("appendText("))
        assertFalse(src("app/src/main/AndroidManifest.xml").contains("CrashJournalProvider"))
    }
}
