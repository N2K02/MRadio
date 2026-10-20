package com.mradio.app

import android.content.Context
import android.database.ContentObserver
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * Volume: "−" slider "+" . Controls the phone's media volume, so it always matches the
 * hardware volume buttons (it follows them while the screen is open).
 */
@Composable
fun VolumeRow(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val audio = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val max = remember { audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
    var level by remember { mutableIntStateOf(audio.getStreamVolume(AudioManager.STREAM_MUSIC)) }

    // Follow the hardware buttons and the system volume panel.
    DisposableEffect(audio) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                level = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
            }
        }
        context.contentResolver.registerContentObserver(Settings.System.CONTENT_URI, true, observer)
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }

    fun setLevel(value: Int) {
        val v = value.coerceIn(0, max)
        if (v == level) return
        level = v
        // Some phones refuse a change while Do Not Disturb rules apply; the slider then snaps back.
        runCatching { audio.setStreamVolume(AudioManager.STREAM_MUSIC, v, 0) }
        level = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
    ) {
        IconButton(onClick = { setLevel(level - 1) }) {
            Icon(Icons.Rounded.Remove, contentDescription = "Volume down")
        }
        Slider(
            value = level.toFloat(),
            onValueChange = { setLevel(it.roundToInt()) },
            valueRange = 0f..max.toFloat(),
            steps = max - 1,
            modifier = Modifier
                .weight(1f)
                .semantics {
                    contentDescription = "Volume"
                    stateDescription = "${level * 100 / max}%"
                },
        )
        IconButton(onClick = { setLevel(level + 1) }) {
            Icon(Icons.Rounded.Add, contentDescription = "Volume up")
        }
    }
}
