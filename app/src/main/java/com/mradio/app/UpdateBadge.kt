package com.mradio.app

import androidx.compose.material3.Badge
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

/** Stable and experimental updates share the same pending-update indicator. */
internal fun AppUpdater.State.hasPendingUpdate(): Boolean = when (this) {
    is AppUpdater.State.Available,
    is AppUpdater.State.Downloading,
    is AppUpdater.State.Ready,
    is AppUpdater.State.NeedsPermission -> true
    else -> false
}

/** Visible text makes the state clear for TalkBack and does not rely on colour alone. */
@Composable
internal fun UpdateAvailableBadge() {
    Badge { Text("New") }
}
