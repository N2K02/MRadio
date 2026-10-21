package com.mradio.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken

/**
 * Compact launcher widget. Its buttons connect to the SAME Media3 session as the app.
 * No stream networking, timers, or long-running work lives in this receiver.
 */
class RadioWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { update(context, manager, it, null, false) }
        refreshFromPlayer(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_TOGGLE || intent.action == ACTION_NEXT) {
            val pending = goAsync()
            val finished = java.util.concurrent.atomic.AtomicBoolean(false)
            val handler = android.os.Handler(android.os.Looper.getMainLooper())
            val finish = Runnable {
                if (finished.compareAndSet(false, true)) pending.finish()
            }
            // Broadcast receivers have a short execution window. Never leave goAsync pending
            // indefinitely if the media service is slow or cannot be connected.
            handler.postDelayed(finish, 8_000L)
            runCatching {
                withController(context) { controller ->
                if (finished.get()) return@withController
                try {
                    if (controller != null) {
                        when (intent.action) {
                            ACTION_NEXT -> {
                                // StationSwitcher implements this for Android Auto and Bluetooth too.
                                // Do not construct our own queue or duplicate station selection logic.
                                controller.seekToNextMediaItem()
                            }
                            ACTION_TOGGLE -> {
                                if (controller.isPlaying) controller.pause()
                                else {
                                    if (controller.mediaItemCount == 0) {
                                        val station = Library.lastPlayed?.let(Library::find)
                                            ?: Library.myStations().firstOrNull()
                                        if (station != null) {
                                            controller.setMediaItem(MediaItem.Builder().setMediaId(station.id).build())
                                            controller.prepare()
                                            controller.play()
                                        }
                                    } else {
                                        if (controller.playbackState == Player.STATE_IDLE) controller.prepare()
                                        controller.play()
                                    }
                                }
                            }
                        }
                        val title = controller.currentMediaItem?.mediaId?.let { Library.find(it)?.name }
                            ?: controller.currentMediaItem?.mediaMetadata?.title?.toString()
                        refresh(context, title, controller.isPlaying)
                    }
                } catch (error: Exception) {
                    Library.logPlayback("Widget playback failed: ${error.javaClass.simpleName}")
                } finally {
                    finish.run()
                }
                }
            }.onFailure {
                Library.logPlayback("Widget controller setup failed: ${it.javaClass.simpleName}")
                finish.run()
            }
        } else if (intent.action == ACTION_REFRESH) {
            refreshFromPlayer(context)
        }
    }

    companion object {
        const val ACTION_TOGGLE = "com.mradio.app.WIDGET_TOGGLE"
        const val ACTION_NEXT = "com.mradio.app.WIDGET_NEXT"
        const val ACTION_REFRESH = "com.mradio.app.WIDGET_REFRESH"

        private fun widgetIntent(context: Context, action: String, requestCode: Int): PendingIntent =
            PendingIntent.getBroadcast(
                context, requestCode,
                Intent(context, RadioWidgetProvider::class.java).setAction(action),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        private fun update(
            context: Context,
            manager: AppWidgetManager,
            id: Int,
            name: String?,
            playing: Boolean,
        ) {
            val view = RemoteViews(context.packageName, R.layout.radio_widget)
            view.setTextViewText(R.id.widget_station, name ?: "Choose a station")
            view.setTextViewText(R.id.widget_status, if (playing) "LIVE · Playing" else "Tap play to listen")
            view.setImageViewResource(
                R.id.widget_toggle,
                if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
            )
            view.setOnClickPendingIntent(R.id.widget_toggle, widgetIntent(context, ACTION_TOGGLE, 21))
            view.setOnClickPendingIntent(R.id.widget_next, widgetIntent(context, ACTION_NEXT, 22))
            val open = PendingIntent.getActivity(
                context, 23, Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            view.setOnClickPendingIntent(R.id.widget_station, open)
            manager.updateAppWidget(id, view)
        }

        fun refresh(context: Context, name: String?, playing: Boolean) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, RadioWidgetProvider::class.java))
            ids.forEach { update(context, manager, it, name, playing) }
        }

        fun refreshFromPlayer(context: Context) {
            withController(context) { controller ->
                if (controller != null) {
                    val name = controller.currentMediaItem?.mediaId?.let { Library.find(it)?.name }
                        ?: controller.currentMediaItem?.mediaMetadata?.title?.toString()
                    refresh(context, name, controller.isPlaying)
                }
            }
        }

        private fun withController(context: Context, action: (MediaController?) -> Unit) {
            val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
            val future = MediaController.Builder(context, token).buildAsync()
            future.addListener({
                val controller = runCatching { future.get() }.getOrNull()
                try {
                    action(controller)
                } catch (error: Exception) {
                    Library.logPlayback("Widget Media3 command failed: ${error.javaClass.simpleName}")
                } finally {
                    MediaController.releaseFuture(future)
                }
            }, ContextCompat.getMainExecutor(context))
        }
    }
}
