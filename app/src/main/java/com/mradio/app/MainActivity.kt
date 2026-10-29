package com.mradio.app

import android.content.ComponentName
import android.content.Intent
import androidx.compose.foundation.layout.Column
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.media3.common.Player
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private val controller = mutableStateOf<MediaController?>(null)
    private var autoPlayedThisLaunch = false
    private var pendingSharedStream: String? = null
    private var pendingSharedName: String? = null
    private var previewUrl: String? = null
    private val previewReady = mutableStateOf<String?>(null)
    private val previewError = mutableStateOf(false)
    private val previewSaveError = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LiveUpdater.init(this)
        AppLog.init(applicationContext)
        enableEdgeToEdge()
        Library.init(this)
        receiveSharedStream(intent)
        // Exit pressed in the notification while the app is open.
        lifecycleScope.launch { AppExit.requests.collect { finishAndRemoveTask() } }
        setContent {
            MRadioTheme {
                MRadioScreen(
                    controller = controller.value,
                    onExit = ::exit,
                    onSaveSharedStreamRequested = { url ->
                        previewSaveError.value = null
                        previewReady.value = url
                    },
                )
                PreviewDialogs()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        receiveSharedStream(intent)
    }

    private fun receiveSharedStream(intent: Intent?) {
        val incoming = intent ?: return
        val raw = when (incoming.action) {
            Intent.ACTION_VIEW -> incoming.dataString
            Intent.ACTION_SEND -> incoming.getStringExtra(Intent.EXTRA_TEXT)
            else -> null
        } ?: return

        val deepLink = when (incoming.action) {
            Intent.ACTION_VIEW -> incoming.data
            else -> StreamLinkParser.deepLinkFromSharedText(raw)?.let(Uri::parse)
        }
        val webLink = when (incoming.action) {
            Intent.ACTION_VIEW -> incoming.data
            else -> StreamLinkParser.httpUrlFromText(raw)?.let(Uri::parse)
        }

        val streamText = when {
            deepLink?.scheme.equals("mradio", true) && deepLink?.host.equals("open", true) ->
                MRadioShareLinks.decodeToken(deepLink?.getQueryParameter("d"))
                    ?: deepLink?.getQueryParameter("url") ?: return
            MRadioShareLinks.isSharePage(webLink?.toString()) ->
                MRadioShareLinks.decodeToken(webLink?.getQueryParameter("d"))
                    ?: webLink?.getQueryParameter("u") ?: return
            else -> raw
        }
        val url = StreamLinkParser.httpUrlFromText(streamText) ?: return
        val uri = Uri.parse(url)
        if (uri.host.isNullOrBlank() || uri.userInfo != null) return
        pendingSharedStream = url
        // Shared mRadio messages include the station name before the listening link.
        // Do not treat arbitrary chat messages or URL-only shares as station names.
        pendingSharedName = if (incoming.action == Intent.ACTION_SEND &&
            raw.contains("Listen in MRadio:", ignoreCase = true)
        ) raw.lineSequence().firstOrNull()
            ?.substringBefore(" · ")?.trim()?.takeIf {
                it.length in 1..100 && !it.contains("://") && it.none(Char::isISOControl)
            }
        else null
        playSharedStreamIfReady()
    }

    private fun playSharedStreamIfReady() {
        val player = controller.value ?: return
        val url = pendingSharedStream ?: return
        pendingSharedStream = null
        val sharedName = pendingSharedName
        pendingSharedName = null
        previewUrl = url
        previewReady.value = null
        previewSaveError.value = null
        previewError.value = false
        autoPlayedThisLaunch = true
        player.setMediaItem(
            MediaItem.Builder()
                .setMediaId("external:$url")
                .apply {
                    if (sharedName != null) setMediaMetadata(
                        MediaMetadata.Builder().setTitle(sharedName).build()
                    )
                }
                .build()
        )
        player.prepare()
        player.play()
    }

    @Composable
    private fun PreviewDialogs() {
        if (previewError.value) {
            AlertDialog(
                onDismissRequest = { previewError.value = false },
                title = { Text("Stream unavailable") },
                text = { Text("This link could not be played and was not saved.") },
                confirmButton = {
                    TextButton(onClick = { previewError.value = false }) { Text("OK") }
                },
            )
        }
        val url = previewReady.value ?: return
        // Recheck at display time AND when saving: the favourites list can change
        // while the stream is buffering or the dialog is open.
        val favourite = Library.favouriteForStream(url)
        val known = Library.favouriteForStream(url) ?: Library.findByStreamUrl(url)
        val proposed = known?.name ?: Uri.parse(url).host ?: "Shared stream"
        val name = remember(url) { mutableStateOf(proposed) }
        AlertDialog(
            onDismissRequest = { previewReady.value = null; previewSaveError.value = null },
            title = {
                Text(if (favourite != null) "Already in Favourite Radios" else "Stream is playing")
            },
            text = {
                Column {
                    Text(
                        when {
                            favourite != null -> "${favourite.name} is already saved. No duplicate will be created."
                            known != null -> "Add ${known.name} to Favourite Radios?"
                            else -> "Give this stream a name before saving it to Favourite Radios."
                        }
                    )
                    if (favourite == null && known == null) {
                        OutlinedTextField(
                            value = name.value,
                            onValueChange = { name.value = it.take(100) },
                            label = { Text("Station name") },
                            singleLine = true,
                        )
                    }
                    previewSaveError.value?.let { Text(it, color = androidx.compose.material3.MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                if (favourite != null) {
                    TextButton(onClick = { previewReady.value = null }) { Text("OK") }
                } else {
                    TextButton(
                        enabled = name.value.isNotBlank(),
                        onClick = {
                            runCatching { Library.saveSharedStream(url, name.value) }
                                .onSuccess {
                                    previewReady.value = null
                                    previewSaveError.value = null
                                }
                                .onFailure { previewSaveError.value = it.message ?: "Unable to save this stream" }
                        },
                    ) { Text("Save") }
                }
            },
            dismissButton = if (favourite == null) {
                { TextButton(onClick = { previewReady.value = null; previewSaveError.value = null }) { Text("Not now") } }
            } else null,
        )
    }

    /** Stop the radio and close the app. */
    private fun exit() {
        controller.value?.sendCustomCommand(StationCommands.EXIT, Bundle.EMPTY)
        finishAndRemoveTask()
    }

    override fun onResume() {
        super.onResume()
        LiveUpdater.onResume(this)
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
                connected?.addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        val url = previewUrl ?: return
                        if (connected.currentMediaItem?.mediaId != "external:$url") return
                        if (playbackState == Player.STATE_READY) {
                            previewReady.value = url
                            previewUrl = null
                        } else if (playbackState == Player.STATE_IDLE) {
                            previewError.value = true
                            previewUrl = null
                        }
                    }
                })
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
