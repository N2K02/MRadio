package com.mradio.app

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
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
    private var pendingSharedStream: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppUpdater.init(this, "N2K02/MRadio", "MRadio")
        enableEdgeToEdge()
        Library.init(this)
        receiveSharedStream(intent)
        // Exit pressed in the notification while the app is open.
        lifecycleScope.launch { AppExit.requests.collect { finishAndRemoveTask() } }
        setContent {
            MRadioTheme {
                MRadioScreen(controller = controller.value, onExit = ::exit)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        receiveSharedStream(intent)
    }

    private fun receiveSharedStream(intent: Intent?) {
        val raw = when (intent?.action) {
            Intent.ACTION_VIEW -> intent.dataString
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
            else -> null
        } ?: return
        val url = Regex("""https?://[^\\s<>\\"']+""", RegexOption.IGNORE_CASE)
            .find(raw)?.value?.trimEnd('.', ',', ')') ?: return
        val uri = Uri.parse(url)
        if (uri.host.isNullOrBlank() || uri.userInfo != null) return
        pendingSharedStream = url
        playSharedStreamIfReady()
    }

    private fun playSharedStreamIfReady() {
        val player = controller.value ?: return
        val url = pendingSharedStream ?: return
        val station = runCatching {
            Library.customStations().firstOrNull { url in it.streams }
                ?: Library.saveCustomStation(null, Uri.parse(url).host ?: "Shared stream", url, "", "MU")
        }.getOrNull() ?: return
        pendingSharedStream = null
        autoPlayedThisLaunch = true
        player.setMediaItem(MediaItem.Builder().setMediaId(station.id).build())
        player.prepare()
        player.play()
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
                playSharedStreamIfReady()
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
