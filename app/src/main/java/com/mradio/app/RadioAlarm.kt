package com.mradio.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Calendar

/**
 * One-time radio wake-up alarm. AlarmClockInfo exposes the upcoming alarm to Android and
 * requires exact-alarm access on devices that enforce the special permission.
 */
object RadioAlarm {
    private const val PREFS = "mradio_alarm"
    const val ACTION_PLAY_ALARM = "com.mradio.app.PLAY_RADIO_ALARM"
    private const val REQUEST_CODE = 9301
    private val _alarmAt = MutableStateFlow<Long?>(null)
    val alarmAt: StateFlow<Long?> = _alarmAt

    private fun pending(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context, REQUEST_CODE,
            Intent(context, RadioAlarmReceiver::class.java).setAction(ACTION_PLAY_ALARM),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    fun init(context: Context) {
        _alarmAt.value = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong("at", 0L).takeIf { it > System.currentTimeMillis() }
    }

    /** Re-register a pending alarm after reboot or when exact-alarm access is granted. */
    fun restoreScheduled(context: Context) {
        val store = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val whenMillis = store.getLong("at", 0L)
        val station = store.getString("station", null)
        if (whenMillis <= System.currentTimeMillis() || station.isNullOrBlank()) {
            store.edit().clear().apply()
            _alarmAt.value = null
            return
        }
        _alarmAt.value = whenMillis
        if (canSchedule(context) && Library.find(station) != null) {
            runCatching { schedule(context, whenMillis, station) }
                .onFailure { Library.logPlayback("Radio alarm reschedule failed: ${it.javaClass.simpleName}") }
        }
    }

    fun scheduledStation(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("station", null)

    fun canSchedule(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return manager.canScheduleExactAlarms()
    }

    fun requestPermission(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.startActivity(
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                    .setData(Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    fun schedule(context: Context, atMillis: Long, stationId: String) {
        require(atMillis > System.currentTimeMillis()) { "Choose a future alarm time" }
        require(Library.find(stationId) != null) { "Choose a saved station" }
        check(canSchedule(context)) { "Allow exact alarms in Android settings first" }
        val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val show = PendingIntent.getActivity(
            context, REQUEST_CODE,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        manager.setAlarmClock(AlarmManager.AlarmClockInfo(atMillis, show), pending(context))
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong("at", atMillis).putString("station", stationId).apply()
        _alarmAt.value = atMillis
    }

    fun cancel(context: Context) {
        (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(pending(context))
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
        _alarmAt.value = null
    }

    fun consume(context: Context): String? {
        val station = scheduledStation(context)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
        _alarmAt.value = null
        return station
    }
}

/** Explicit, non-exported alarm broadcast. No arbitrary URLs or station IDs accepted from intents. */
class RadioAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != RadioAlarm.ACTION_PLAY_ALARM) return
        val station = RadioAlarm.consume(context) ?: return
        if (Library.find(station) == null) return
        runCatching {
            ContextCompat.startForegroundService(
                context,
                Intent(context, PlaybackService::class.java)
                    .setAction(RadioAlarm.ACTION_PLAY_ALARM)
                    .putExtra("stationId", station),
            )
        }.onFailure {
            Library.logPlayback("Alarm couldn't start playback: ${it.javaClass.simpleName}")
        }
    }
}

/** Restores the single scheduled alarm after boot or newly granted alarm access. */
class RadioAlarmBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED
        ) return
        Library.init(context)
        RadioAlarm.restoreScheduled(context)
    }
}
