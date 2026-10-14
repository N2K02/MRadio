package com.mradio.app

import android.os.Bundle
import androidx.media3.session.SessionCommand

/**
 * Next/previous station requests from the app's player bar to PlaybackService.
 * (The notification, lock screen and Android Auto use the standard next/previous buttons.)
 */
object StationCommands {
    val NEXT = SessionCommand("com.mradio.app.NEXT_STATION", Bundle.EMPTY)
    val PREVIOUS = SessionCommand("com.mradio.app.PREVIOUS_STATION", Bundle.EMPTY)
}
