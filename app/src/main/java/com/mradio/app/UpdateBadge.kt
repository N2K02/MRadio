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

/** Pending indicator for the live shared updater state. */
internal fun dev.n2k02.updater.UpdateStatus.hasSharedPendingUpdate(): Boolean = when (this) {
    is dev.n2k02.updater.UpdateStatus.Available,
    is dev.n2k02.updater.UpdateStatus.Downloading,
    is dev.n2k02.updater.UpdateStatus.Ready,
    is dev.n2k02.updater.UpdateStatus.NeedsInstallPermission -> true
    else -> false
}
