package com.mradio.app

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/** Protect Quit access without changing this app's established navigation style. */
class QuitNavigationContractTest {
    @Test fun quitRemainsAvailableAndStopsTheApp() {
        val relative = "app/src/main/java/com/mradio/app/RadioBottomNavigation.kt"
        val source = listOf(File(relative), File("../$relative"))
            .firstOrNull { it.isFile }?.readText() ?: error("Missing $relative")
        assertTrue(source.contains("label = { Text(\"Settings\") }"))
        assertTrue(source.contains("label = { Text(\"Quit\") }"))
        assertTrue(source.contains("onClick = onExit"))
        assertTrue(source.indexOf("label = { Text(\"Quit\") }") > source.indexOf("label = { Text(\"Settings\") }"))
    }
}
