package com.mradio.app

import android.content.ComponentName
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private val controller = mutableStateOf<MediaController?>(null)
    private var autoPlayedThisLaunch = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppUpdater.init(this, "N2K02/MRadio", "MRadio")
        enableEdgeToEdge()
        Library.init(this)
        // Exit pressed in the notification while the app is open.
        lifecycleScope.launch { AppExit.requests.collect { finishAndRemoveTask() } }
        setContent {
            MRadioTheme {
                MRadioScreen(controller = controller.value, onExit = ::exit)
            }
        }
    }

    /** Stop the radio and close the app. */
    private fun exit() {
        controller.value?.sendCustomCommand(StationCommands.EXIT, Bundle.EMPTY)
        finishAndRemoveTask()
    }

    override fun onResume() {
        super.onResume()
        AppUpdater.onResume(this)
    }

    override fun onStart() {
        super.onStart()
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        val future = MediaController.Builder(this, token).buildAsync()
        controllerFuture = future
        future.addListener(
            {
                val connected = runCatching { future.get() }.getOrNull()
                controller.value = connected
                if (!autoPlayedThisLaunch && Library.autoPlayPhone.value && connected != null &&
                    connected.playbackState == androidx.media3.common.Player.STATE_IDLE &&
                    connected.mediaItemCount == 0
                ) {
                    val station = Library.lastPlayed?.let(Library::find)
                    if (station != null) {
                        autoPlayedThisLaunch = true
                        connected.setMediaItem(MediaItem.Builder().setMediaId(station.id).build())
                        connected.prepare()
                        connected.play()
                    }
                }
            },
            ContextCompat.getMainExecutor(this),
        )
    }

    override fun onStop() {
        controller.value = null
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        super.onStop()
    }
}
