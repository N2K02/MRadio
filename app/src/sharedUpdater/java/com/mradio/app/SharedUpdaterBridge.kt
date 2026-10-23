package com.mradio.app

import dev.n2k02.updater.PublishedUpdateSource
import dev.n2k02.updater.UpdateConfig
import dev.n2k02.updater.UpdaterController
import android.content.Context

/**
 * Opt-in build-only integration probe. Not used by the live updater yet.
 * Keeps existing MRadio update behavior unchanged during the migration.
 */
internal object SharedUpdaterBridge {
    private const val manifestUrl = "https://n2k02.github.io/mradio/updates"

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
        )
    }
}
