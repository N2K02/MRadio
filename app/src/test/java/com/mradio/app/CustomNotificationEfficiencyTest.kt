package com.mradio.app

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CustomNotificationEfficiencyTest {
    @Test fun controlNotificationOnlyRefreshesWhenShownStateChanges() {
        val file = listOf(
            File("src/main/java/com/mradio/app/PlaybackService.kt"),
            File("app/src/main/java/com/mradio/app/PlaybackService.kt")
        ).first { it.exists() }
        val code = file.readText()
        assertTrue(code.contains("val key = Triple(current?.id, current?.name, player.isPlaying)"))
        assertTrue(code.contains("if (key == lastCustomControls) return"))
        assertTrue(code.contains("lastCustomControls = key"))
        assertTrue(code.contains("lastCustomControls = null"))
        assertTrue(code.contains("updateListening(isPlaying)"))
    }
}
