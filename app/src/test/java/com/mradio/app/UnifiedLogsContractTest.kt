package com.mradio.app
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
class UnifiedLogsContractTest {
    @Test fun aaYouTubeLogActionsAndPagingArePresent() {
        val path = "app/src/main/java/com/mradio/app/UnifiedLogsPage.kt"
        val source = listOf(File(path), File("../$path")).firstOrNull { it.isFile }
            ?.readText() ?: error("Missing $path")
        listOf("followLatest", "onSizeChanged { viewport = it }", "Text(\"Prev\",",
            "Text(\"Next\",", "Text(\"Copy\",", "Text(\"Clear all\",",
            "AppLog.clear()", "Switch(checked = enabled").forEach { assertTrue(it, source.contains(it)) }
        assertTrue(source.contains("Icons.Filled.ContentCopy"))
        assertTrue(source.contains("Icons.Filled.DeleteSweep"))
        assertTrue(source.contains("Icons.AutoMirrored.Filled.KeyboardArrowLeft"))
        assertTrue(source.contains("Icons.AutoMirrored.Filled.KeyboardArrowRight"))
        assertTrue(source.contains("horizontalArrangement = Arrangement.SpaceBetween"))
        assertFalse(source.contains("Text(\"Back\")"))
        assertFalse(source.contains("onBack: () -> Unit"))
    }
    @Test fun savedAndLiveEventsFollowTimeWithoutLosingLegacyDates() {
        val previous = "2026-10-28 23:59:59.000 [UI] old"
        val current = "2026-10-29 11:00:02.000 [LOG] new"
        val call = "[activity] 11:00:01 call"
        assertEquals(listOf(previous, call, current),
            LogEventOrdering.merge(listOf(previous, current), listOf(call), "2026-10-29"))
        assertEquals(listOf("untimed A", "untimed B"),
            LogEventOrdering.merge(listOf("untimed A", "untimed B"), emptyList(), "2026-10-29"))
    }

}
