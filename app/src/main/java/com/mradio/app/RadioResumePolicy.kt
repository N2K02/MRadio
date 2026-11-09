package com.mradio.app

/**
 * Keep vendor volume-key dispatch from resurrecting a stopped radio.
 * Explicit playback from the app or a connected controller with an existing queue
 * is unaffected; this policy applies only when Media3 has no item and asks to resume.
 */
internal object RadioResumePolicy {
    fun isVolumeKey(code: Int): Boolean = code == 24 || code == 25 || code == 164

    fun allowBackgroundResumption(
        manufacturer: String,
        controllerPackage: String,
        appPackage: String,
        autoPackage: String,
        autoPlayEnabled: Boolean,
    ): Boolean {
        if (controllerPackage == autoPackage) return autoPlayEnabled
        if (controllerPackage == appPackage) return true
        // Huawei/EMUI sometimes converts a hardware volume interaction into a
        // system-requested media resume. Do not start a stopped radio implicitly.
        return !manufacturer.equals("huawei", ignoreCase = true) &&
            !manufacturer.equals("honor", ignoreCase = true)
    }
}
