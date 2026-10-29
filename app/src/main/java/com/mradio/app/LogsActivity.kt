package com.mradio.app

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier

/** Hosted by the main Settings page, sharing its n2k02theme header. */
@Composable
internal fun MRadioLogsPage(modifier: Modifier = Modifier, onBack: () -> Unit) {
    val playbackEvents by Library.events.collectAsState()
    N2K02UnifiedLogsPage(
        modifier = modifier, appName = "mRadio", onBack = onBack,
        extraLabel = "Connection diagnostics · playback events",
        extraLines = playbackEvents,
        clearExtra = Library::clearPlaybackLog,
    )
}
