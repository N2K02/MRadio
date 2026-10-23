package com.mradio.app

import dev.n2k02.updater.PublishedUpdateSource
import dev.n2k02.updater.UpdateConfig
import dev.n2k02.updater.UpdaterController
import android.app.Activity
import android.content.Context
import dev.n2k02.updater.UpdateInstallGuard
import dev.n2k02.updater.UpdateRelease
import dev.n2k02.updater.UpdateStatus
import kotlinx.coroutines.flow.StateFlow

/**
 * Opt-in build-only integration probe. Not used by the live updater yet.
 * Keeps existing MRadio update behavior unchanged during the migration.
 */
internal object SharedUpdaterBridge {
    private const val manifestUrl = "https://n2k02.github.io/mradio/updates"

    /**
     * Creates an isolated controller for opt-in testing; callers retain ownership
     * of its lifecycle. This does not modify AppUpdater or its active UI state.
     */
    fun create(context: Context, experimentalEnabled: Boolean, versionCode: Long): UpdaterController {
        val config = UpdateConfig(
            repository = "N2K02/MRadio",
            packageName = context.packageName,
            installedVersionCode = versionCode,
            experimentalEnabled = experimentalEnabled,
        )
        return UpdaterController(
            context = context,
            config = config,
            publishedSource = PublishedUpdateSource(manifestUrl),
            offerPreferences = context.getSharedPreferences("app_updater", Context.MODE_PRIVATE),
        )
    }
    /** Refuse to open the installer while radio playback is active. */
    val playbackInstallGuard = UpdateInstallGuard { !PlaybackService.playbackActiveForUpdate }

    /** Explicit testing entry points, never invoked from the production updater. */
    fun status(controller: UpdaterController): StateFlow<UpdateStatus> = controller.status

    fun check(controller: UpdaterController, manual: Boolean) = controller.check(manual)

    fun skip(controller: UpdaterController, release: UpdateRelease) = controller.skip(release)

    fun download(controller: UpdaterController, release: UpdateRelease) = controller.download(release)

    /**
     * Always require an explicit host safety guard before launching Android's installer.
     * Never bypass host playback/recording protections.
     */
    fun install(controller: UpdaterController, activity: Activity, guard: UpdateInstallGuard) =
        controller.install(activity, guard)

    fun openInstallPermission(controller: UpdaterController, activity: Activity) =
        controller.openInstallPermission(activity)

    fun onResume(controller: UpdaterController, activity: Activity, guard: UpdateInstallGuard) {
        when (controller.status.value) {
            is UpdateStatus.NeedsInstallPermission -> controller.install(activity, guard)
            UpdateStatus.Idle -> controller.check(manual = false)
            else -> Unit
        }
    }

    fun cancel(controller: UpdaterController) = controller.cancel()
}
