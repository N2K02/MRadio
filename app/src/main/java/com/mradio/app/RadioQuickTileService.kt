package com.mradio.app

import android.content.ComponentName
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture

/**
 * User-initiated Android Quick Settings play / pause.
 * Routes actions through Media3 rather than starting a new background service directly.
 */
@RequiresApi(Build.VERSION_CODES.N)
class RadioQuickTileService : TileService() {
    private var controllerFuture: ListenableFuture<MediaController>? = null

    override fun onStartListening() {
        super.onStartListening()
        refreshPlaybackState()
    }

    override fun onClick() {
        super.onClick()
        withController { controller ->
            if (controller.isPlaying) {
                controller.pause()
            } else {
                if (controller.mediaItemCount == 0) {
                    val station = Library.lastPlayed?.let(Library::find)
                        ?: Library.myStations().firstOrNull()
                    if (station != null) {
                        controller.setMediaItem(MediaItem.Builder().setMediaId(station.id).build())
                        controller.prepare()
                        controller.play()
                    } else {
                        launchApp()
                    }
                } else {
                    if (controller.playbackState == Player.STATE_IDLE) controller.prepare()
                    controller.play()
                }
            }
            render(controller.isPlaying)
        }
    }

    private fun launchApp() {
        val activity = android.app.PendingIntent.getActivity(
            this, 0, android.content.Intent(this, MainActivity::class.java),
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
        )
        if (Build.VERSION.SDK_INT >= 34) startActivityAndCollapse(activity)
        else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(android.content.Intent(this, MainActivity::class.java)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    private fun refreshPlaybackState() {
        withController { render(it.isPlaying) }
    }

    private fun render(playing: Boolean) {
        qsTile?.let { tile ->
            tile.state = if (playing) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            tile.label = "mRadio"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tile.subtitle = if (playing) "Playing" else "Tap to play"
            }
            tile.updateTile()
        }
    }

    private fun withController(action: (MediaController) -> Unit) {
        controllerFuture?.let(MediaController::releaseFuture)
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        val future = MediaController.Builder(this, token).buildAsync()
        controllerFuture = future
        future.addListener({
            try {
                if (controllerFuture === future) {
                    val controller = future.get()
                    action(controller)
                }
            } catch (error: Exception) {
                Library.logPlayback("Quick Settings controller unavailable: ${error.javaClass.simpleName}")
                render(false)
            } finally {
                MediaController.releaseFuture(future)
                if (controllerFuture === future) controllerFuture = null
            }
        }, ContextCompat.getMainExecutor(this))
    }

    override fun onDestroy() {
        controllerFuture?.let(MediaController::releaseFuture)
        controllerFuture = null
        super.onDestroy()
    }
}
