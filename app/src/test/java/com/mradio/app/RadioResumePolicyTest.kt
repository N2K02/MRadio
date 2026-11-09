package com.mradio.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RadioResumePolicyTest {
    @Test fun hardwareVolumeKeysAreNotPlayback() {
        assertTrue(RadioResumePolicy.isVolumeKey(24))
        assertTrue(RadioResumePolicy.isVolumeKey(25))
        assertTrue(RadioResumePolicy.isVolumeKey(164))
        assertFalse(RadioResumePolicy.isVolumeKey(85))
        assertFalse(RadioResumePolicy.isVolumeKey(126))
    }

    private fun allowed(manufacturer: String, controller: String, auto: Boolean = false) =
        RadioResumePolicy.allowBackgroundResumption(
            manufacturer, controller, "com.mradio.app",
            "com.google.android.projection.gearhead", auto
        )

    @Test fun huaweiSystemUiCannotResurrectStoppedRadio() {
        assertFalse(allowed("HUAWEI", "com.android.systemui"))
        assertFalse(allowed("Huawei", "com.huawei.systemui"))
        assertFalse(allowed("HONOR", "com.hihonor.systemui"))
    }

    @Test fun huaweiBluetoothAndExternalPlayRemainAvailable() {
        assertTrue(allowed("HUAWEI", "com.android.bluetooth"))
        assertTrue(allowed("HONOR", "com.google.android.bluetooth"))
        assertTrue(allowed("Huawei", "com.android.car.media"))
        assertTrue(allowed("Huawei", "com.example.musiccontroller"))
    }

    @Test fun explicitAppAndAutoPlaybackStillAllowed() {
        assertTrue(allowed("Huawei", "com.mradio.app"))
        assertTrue(allowed("Huawei", "com.google.android.projection.gearhead", true))
        assertFalse(allowed("Huawei", "com.google.android.projection.gearhead", false))
    }

    @Test fun otherPhonesRetainStandardResumption() {
        assertTrue(allowed("Google", "com.android.systemui"))
        assertTrue(allowed("Samsung", "com.bluetooth.player"))
    }
}
