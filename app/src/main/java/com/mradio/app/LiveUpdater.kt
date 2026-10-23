package com.mradio.app

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import dev.n2k02.updater.PublishedUpdateSource
import dev.n2k02.updater.UpdateConfig
import dev.n2k02.updater.UpdateInstallGuard
import dev.n2k02.updater.UpdaterController
import kotlinx.coroutines.flow.MutableStateFlow

/** Live shared updater for MRadio's stable and experimental channels. */
internal object LiveUpdater {
    private lateinit var app: Context
    private var controllerInstance: UpdaterController? = null
    val experimentalEnabled = MutableStateFlow(false)
    val installGuard = UpdateInstallGuard { !PlaybackService.playbackActiveForUpdate }

    fun init(context: Context) {
        app = context.applicationContext
        experimentalEnabled.value = prefs().getBoolean("experimentalEnabled", false)
        controllerInstance = createController()
    }

    private fun prefs() = app.getSharedPreferences("app_updater", Context.MODE_PRIVATE)

    private fun versionCode(): Long {
        val info = app.packageManager.getPackageInfo(app.packageName, 0)
        return if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else @Suppress("DEPRECATION") info.versionCode.toLong()
    }

    private fun createController() = UpdaterController(
        context = app,
        config = UpdateConfig(
            repository = "N2K02/MRadio",
            packageName = app.packageName,
            installedVersionCode = versionCode(),
            experimentalEnabled = experimentalEnabled.value,
        ),
        publishedSource = PublishedUpdateSource("https://n2k02.github.io/mradio/updates"),
        offerPreferences = prefs(),
    )

    val controller: UpdaterController get() = requireNotNull(controllerInstance) { "LiveUpdater.init must be called first" }

    fun setExperimentalEnabled(enabled: Boolean) {
        if (experimentalEnabled.value == enabled) return
        controller.cancel()
        prefs().edit().putBoolean("experimentalEnabled", enabled).apply()
        experimentalEnabled.value = enabled
        controllerInstance = createController()
        controller.check(manual = true)
    }

    fun onResume(activity: Activity) {
        when (controller.status.value) {
            is dev.n2k02.updater.UpdateStatus.NeedsInstallPermission -> controller.install(activity, installGuard)
            dev.n2k02.updater.UpdateStatus.Idle -> controller.check(manual = false)
            else -> Unit
        }
    }
}
