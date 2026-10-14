package com.mradio.app

import android.os.Bundle
import androidx.media3.session.SessionCommand
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * Requests from the app's player bar and the notification to PlaybackService.
 * (Next/previous in the notification, lock screen and Android Auto use the standard buttons.)
 */
object StationCommands {
    val NEXT = SessionCommand("com.mradio.app.NEXT_STATION", Bundle.EMPTY)
    val PREVIOUS = SessionCommand("com.mradio.app.PREVIOUS_STATION", Bundle.EMPTY)
    val EXIT = SessionCommand("com.mradio.app.EXIT", Bundle.EMPTY)
    /** Adds the playing station to Favourite Radios (the heart in the notification). */
    val FAVOURITE = SessionCommand("com.mradio.app.FAVOURITE", Bundle.EMPTY)
    /** Sleep timer; args: [MINUTES] (0 cancels). */
    val SLEEP = SessionCommand("com.mradio.app.SLEEP", Bundle.EMPTY)
    const val MINUTES = "minutes"
}

/** Tells an open app screen to close when Exit is pressed in the notification. */
object AppExit {
    private val _requests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val requests: SharedFlow<Unit> = _requests

    fun request() {
        _requests.tryEmit(Unit)
    }
}
