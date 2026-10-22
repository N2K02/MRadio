package com.mradio.app

import org.junit.Assert.assertEquals
import org.junit.Test

class RadioDestinationTest {
    @Test fun legacyNavigationFallsBackToHome() {
        assertEquals(RadioDestination.HOME, RadioDestination.fromSaved("Favorites"))
        assertEquals(RadioDestination.HOME, RadioDestination.fromSaved("All stations"))
        assertEquals(RadioDestination.SEARCH, RadioDestination.fromSaved("Search"))
    }

    @Test fun settingsDestinationsHaveDisplayTitles() {
        assertEquals("About & updates", SettingsDestination.UPDATES.title)
        assertEquals("Radio alarm", SettingsDestination.ALARM.title)
    }
}
