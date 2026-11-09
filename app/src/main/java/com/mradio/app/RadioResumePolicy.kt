package com.mradio.app

/**
 * Only suppress Huawei/Honor System UI playback restoration, not all external
 * controller requests. Bluetooth headsets, cars and other external controllers
 * can legitimately send an explicit Play after playback has stopped.
 *
 * This does not distinguish a genuine System UI Resume tap from an OEM-generated
 * phantom command; Huawei device logs are required before narrowing further.
 */
internal object RadioResumePolicy {
    fun isVolumeKey(code: Int): Boolean = code == 24 || code == 25 || code == 164

    fun isHuaweiFamily(manufacturer: String): Boolean =
        manufacturer.equals("huawei", ignoreCase = true) ||
            manufacturer.equals("honor", ignoreCase = true)

    fun allowBackgroundResumption(
        manufacturer: String,
        controllerPackage: String,
        appPackage: String,
        autoPackage: String,
        autoPlayEnabled: Boolean,
    ): Boolean {
        if (controllerPackage == autoPackage) return autoPlayEnabled
        if (controllerPackage == appPackage) return true
        val huaweiFamily = isHuaweiFamily(manufacturer)
        val isSystemUi = controllerPackage.equals("com.android.systemui", ignoreCase = true) ||
            controllerPackage.equals("com.huawei.systemui", ignoreCase = true) ||
            controllerPackage.equals("com.hihonor.systemui", ignoreCase = true)
        return !(huaweiFamily && isSystemUi)
    }
}
