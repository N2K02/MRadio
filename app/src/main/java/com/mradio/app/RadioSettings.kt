package com.mradio.app

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Dedicated settings navigation, separate from playback state.
 * Only exposes actions already supported by the application.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RadioSettings(
    versionDescription: String,
    onEqualizer: () -> Unit,
    onCheckUpdates: () -> Unit,
    onDismiss: () -> Unit,
) {
    val phoneAutoPlay by Library.autoPlayPhone.collectAsState()
    val carAutoPlay by Library.autoPlayAuto.collectAsState()
    val experimentalEnabled by AppUpdater.experimentalEnabled.collectAsState()
    val playbackEvents by Library.events.collectAsState()
    val listeningStats by Library.listeningStats.collectAsState()
    val stationList by Library.stations.collectAsState()
    val alarmAt by RadioAlarm.alarmAt.collectAsState()
    var alarmStationId by rememberSaveable { mutableStateOf("") }
    var alarmMenuOpen by remember { mutableStateOf(false) }
    var alarmError by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    var backupStatus by remember { mutableStateOf<String?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            backupStatus = runCatching {
                val json = Library.exportBackup()
                context.contentResolver.openOutputStream(uri, "wt")?.use {
                    it.write(json.toByteArray(Charsets.UTF_8))
                } ?: error("Could not open destination file")
                "Backup exported successfully"
            }.getOrElse { "Export failed: ${it.message ?: "Unknown error"}" }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            backupStatus = runCatching {
                val json = context.contentResolver.openInputStream(uri)?.use { stream ->
                    val output = java.io.ByteArrayOutputStream()
                    val chunk = ByteArray(8192)
                    while (true) {
                        val count = stream.read(chunk)
                        if (count < 0) break
                        require(output.size() + count <= 1_000_000) { "Backup is too large" }
                        output.write(chunk, 0, count)
                    }
                    output.toString(Charsets.UTF_8.name())
                } ?: error("Could not read backup file")
                val count = Library.restoreBackup(json)
                "Restored $count favorite stations and settings"
            }.getOrElse { "Restore failed: ${it.message ?: "Invalid backup"}" }
        }
    }
    var stationStatus by remember { mutableStateOf<String?>(null) }
    val playlistExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("audio/x-mpegurl")
    ) { uri ->
        if (uri != null) {
            stationStatus = runCatching {
                context.contentResolver.openOutputStream(uri, "wt")?.use { stream ->
                    stream.write(Library.exportCustomPlaylist().toByteArray(Charsets.UTF_8))
                } ?: error("Could not create playlist")
                "Playlist exported"
            }.getOrElse { "Export failed: ${it.message}" }
        }
    }
    val playlistImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            stationStatus = runCatching {
                val data = context.contentResolver.openInputStream(uri)?.use { stream ->
                    val output = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    while (true) {
                        val count = stream.read(buffer)
                        if (count < 0) break
                        require(output.size() + count <= 200_000) { "Playlist is too large" }
                        output.write(buffer, 0, count)
                    }
                    output.toString(Charsets.UTF_8.name())
                } ?: error("Could not read playlist")
                val imported = Library.importCustomPlaylist(data)
                "Imported $imported stations"
            }.getOrElse { "Import failed: ${it.message}" }
        }
    }
    var page by rememberSaveable { mutableStateOf("Settings") }
    var editingStation by remember { mutableStateOf<Station?>(null) }
    var stationEditorOpen by remember { mutableStateOf(false) }
    var stationName by remember { mutableStateOf("") }
    var stationUrl by remember { mutableStateOf("") }
    var stationTags by remember { mutableStateOf("") }
    var stationCountry by remember { mutableStateOf("MU") }
    var deleteTarget by remember { mutableStateOf<Station?>(null) }
    fun openStationEditor(station: Station?) {
        editingStation = station
        stationName = station?.name.orEmpty()
        stationUrl = station?.streams?.firstOrNull().orEmpty()
        stationTags = station?.tags.orEmpty()
        stationCountry = station?.country ?: "MU"
        stationStatus = null
        stationEditorOpen = true
    }
    fun back() {
        if (page == "Settings") onDismiss() else page = "Settings"
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        BackHandler(enabled = page != "Settings") { page = "Settings" }
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text(page) },
                        navigationIcon = {
                            IconButton(onClick = ::back) {
                                Icon(Icons.Rounded.ArrowBack, contentDescription = "Back")
                            }
                        },
                    )
                },
            ) { padding ->
                AnimatedContent(
                    targetState = page,
                    modifier = Modifier.padding(padding),
                    label = "Settings navigation",
                ) { current ->
                    Column(
                        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(androidx.compose.ui.unit.Dp(4f)),
                    ) {
                        if (current == "Settings") {
                            ListItem(
                                headlineContent = { Text("Playback & audio") },
                                supportingContent = { Text("Auto-play and equalizer") },
                                leadingContent = { Icon(Icons.Rounded.GraphicEq, null) },
                                modifier = Modifier.clickable { page = "Playback & audio" },
                            )
                            ListItem(
                                headlineContent = { Text("Radio alarm") },
                                supportingContent = { Text("Schedule a station to play at a chosen time") },
                                leadingContent = { Icon(Icons.Rounded.Alarm, null) },
                                modifier = Modifier.clickable { page = "Radio alarm" },
                            )
                            ListItem(
                                headlineContent = { Text("Listening statistics") },
                                supportingContent = { Text("Time listened and estimated Wi-Fi / mobile streaming") },
                                leadingContent = { Icon(Icons.Rounded.BarChart, null) },
                                modifier = Modifier.clickable { page = "Listening statistics" },
                            )
                            ListItem(
                                headlineContent = { Text("Custom stations") },
                                supportingContent = { Text("Add and edit your own station stream URLs") },
                                leadingContent = { Icon(Icons.Rounded.AddCircleOutline, null) },
                                modifier = Modifier.clickable { page = "Custom stations" },
                            )
                            ListItem(
                                headlineContent = { Text("Backup & restore") },
                                supportingContent = { Text("Export or restore favorites, custom stations, and preferences") },
                                leadingContent = { Icon(Icons.Rounded.Save, null) },
                                modifier = Modifier.clickable { page = "Backup & restore" },
                            )
                            HorizontalDivider()
                            ListItem(
                                headlineContent = { Text("About & updates") },
                                supportingContent = { Text("App version and update checks") },
                                leadingContent = { Icon(Icons.Rounded.Info, null) },
                                modifier = Modifier.clickable { page = "About & updates" },
                            )
                        } else if (current == "Playback & audio") {
                            ListItem(
                                headlineContent = { Text("Auto-play on phone") },
                                supportingContent = { Text("Play the last station when opening the app") },
                                trailingContent = {
                                    Switch(checked = phoneAutoPlay, onCheckedChange = Library::setAutoPlayPhone)
                                },
                                modifier = Modifier.clickable { Library.setAutoPlayPhone(!phoneAutoPlay) },
                            )
                            ListItem(
                                headlineContent = { Text("Auto-play in Android Auto") },
                                supportingContent = { Text("Allow resuming the last station when Android Auto requests playback") },
                                trailingContent = {
                                    Switch(checked = carAutoPlay, onCheckedChange = Library::setAutoPlayAuto)
                                },
                                modifier = Modifier.clickable { Library.setAutoPlayAuto(!carAutoPlay) },
                            )
                            HorizontalDivider()
                            ListItem(
                                headlineContent = { Text("Equalizer") },
                                supportingContent = { Text("Adjust sound settings") },
                                leadingContent = { Icon(Icons.Rounded.GraphicEq, null) },
                                modifier = Modifier.clickable { onEqualizer() },
                            )
                        } else if (current == "Radio alarm") {
                            val available = (Library.myStations() + stationList).distinctBy { it.id }
                            val chosen = available.firstOrNull { it.id == alarmStationId }
                                ?: available.firstOrNull { it.id == RadioAlarm.scheduledStation(context) }
                                ?: available.firstOrNull()
                            ListItem(
                                headlineContent = { Text("Alarm station") },
                                supportingContent = { Text(chosen?.name ?: "No stations available") },
                                trailingContent = {
                                    androidx.compose.foundation.layout.Box {
                                        TextButton(onClick = { alarmMenuOpen = true }) { Text("Choose") }
                                        androidx.compose.material3.DropdownMenu(
                                            expanded = alarmMenuOpen,
                                            onDismissRequest = { alarmMenuOpen = false },
                                        ) {
                                            available.take(100).forEach { station ->
                                                androidx.compose.material3.DropdownMenuItem(
                                                    text = { Text(station.name) },
                                                    onClick = {
                                                        alarmStationId = station.id
                                                        alarmMenuOpen = false
                                                    },
                                                )
                                            }
                                        }
                                    }
                                },
                            )
                            alarmAt?.let { at ->
                                ListItem(
                                    headlineContent = { Text("Upcoming alarm") },
                                    supportingContent = {
                                        Text(java.text.DateFormat.getDateTimeInstance(
                                            java.text.DateFormat.MEDIUM,
                                            java.text.DateFormat.SHORT,
                                        ).format(java.util.Date(at)))
                                    },
                                    trailingContent = {
                                        TextButton(onClick = { RadioAlarm.cancel(context) }) { Text("Cancel") }
                                    },
                                )
                            }
                            ListItem(
                                headlineContent = { Text("Set one-time alarm") },
                                supportingContent = { Text("Choose a time. If today's time has passed, the alarm is set for tomorrow.") },
                                leadingContent = { Icon(Icons.Rounded.Alarm, null) },
                                modifier = Modifier.clickable {
                                    if (chosen == null) {
                                        alarmError = "Add or select a station first"
                                    } else if (!RadioAlarm.canSchedule(context)) {
                                        alarmError = "Allow exact alarms in Android settings, then return to schedule."
                                        runCatching { RadioAlarm.requestPermission(context) }
                                    } else {
                                        val now = java.util.Calendar.getInstance()
                                        android.app.TimePickerDialog(
                                            context,
                                            { _, hour, minute ->
                                                val target = java.util.Calendar.getInstance().apply {
                                                    set(java.util.Calendar.HOUR_OF_DAY, hour)
                                                    set(java.util.Calendar.MINUTE, minute)
                                                    set(java.util.Calendar.SECOND, 0)
                                                    set(java.util.Calendar.MILLISECOND, 0)
                                                    if (timeInMillis <= System.currentTimeMillis()) add(java.util.Calendar.DAY_OF_YEAR, 1)
                                                }
                                                alarmError = runCatching {
                                                    RadioAlarm.schedule(context, target.timeInMillis, chosen.id)
                                                    null
                                                }.getOrElse { it.message ?: "Could not schedule alarm" }
                                            },
                                            now.get(java.util.Calendar.HOUR_OF_DAY),
                                            now.get(java.util.Calendar.MINUTE),
                                            true,
                                        ).show()
                                    }
                                },
                            )
                            ListItem(
                                headlineContent = { Text("Android alarm access") },
                                supportingContent = { Text("Exact-alarm permission may be required. The alarm needs network access to play, and may not survive a device restart.") },
                            )
                            alarmError?.let { message ->
                                ListItem(headlineContent = {
                                    Text(message, color = MaterialTheme.colorScheme.error)
                                })
                            }
                        } else if (current == "Custom stations") {
                            ListItem(
                                headlineContent = { Text("Add station") },
                                supportingContent = { Text("Add an HTTP or HTTPS stream to your favorites") },
                                leadingContent = { Icon(Icons.Rounded.AddCircleOutline, null) },
                                modifier = Modifier.clickable { openStationEditor(null) },
                            )
                            ListItem(
                                headlineContent = { Text("Import station playlist") },
                                supportingContent = { Text("Add stations from an M3U or M3U8 file") },
                                modifier = Modifier.clickable {
                                    stationStatus = null
                                    playlistImportLauncher.launch(
                                        arrayOf("audio/x-mpegurl", "application/x-mpegurl", "text/plain", "application/octet-stream")
                                    )
                                },
                            )
                            ListItem(
                                headlineContent = { Text("Export station playlist") },
                                supportingContent = { Text("Save your custom stations as an M3U playlist") },
                                modifier = Modifier.clickable {
                                    stationStatus = null
                                    playlistExportLauncher.launch("mradio-custom-stations.m3u")
                                },
                            )
                            stationStatus?.let { status ->
                                ListItem(
                                    headlineContent = { Text(status) },
                                    trailingContent = {
                                        TextButton(onClick = { stationStatus = null }) { Text("Dismiss") }
                                    },
                                )
                            }
                            val customs = stationList.filterNot { it.builtIn }
                            if (customs.isEmpty()) {
                                ListItem(headlineContent = { Text("No custom stations yet") })
                            } else {
                                customs.forEach { station ->
                                    ListItem(
                                        headlineContent = { Text(station.name) },
                                        supportingContent = { Text(station.streams.firstOrNull().orEmpty(), maxLines = 1) },
                                        modifier = Modifier.clickable { openStationEditor(station) },
                                        trailingContent = {
                                            IconButton(onClick = { deleteTarget = station }) {
                                                Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete ${station.name}")
                                            }
                                        },
                                    )
                                }
                            }
                        } else if (current == "Backup & restore") {
                            ListItem(
                                headlineContent = { Text("Export backup") },
                                supportingContent = { Text("Save favorites, pinned stations, custom stations, and playback preferences to a JSON file") },
                                leadingContent = { Icon(Icons.Rounded.Save, null) },
                                modifier = Modifier.clickable {
                                    backupStatus = null
                                    exportLauncher.launch("mradio-backup.json")
                                },
                            )
                            ListItem(
                                headlineContent = { Text("Restore backup") },
                                supportingContent = { Text("Replaces your current favorites and saved preferences. Export a copy first.") },
                                leadingContent = { Icon(Icons.Rounded.Restore, null) },
                                modifier = Modifier.clickable {
                                    backupStatus = null
                                    importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
                                },
                            )
                            backupStatus?.let { message ->
                                ListItem(
                                    headlineContent = { Text(message) },
                                    trailingContent = {
                                        TextButton(onClick = { backupStatus = null }) { Text("Dismiss") }
                                    },
                                )
                            }
                        } else if (current == "Listening statistics") {
                            val hours = listeningStats.listeningMs / 3_600_000L
                            val minutes = (listeningStats.listeningMs / 60_000L) % 60L
                            fun usage(bytes: Long): String = String.format(java.util.Locale.US, "%.1f MB", bytes / 1_000_000.0)
                            ListItem(
                                headlineContent = { Text("Listening time") },
                                supportingContent = { Text("${hours}h ${minutes}m") },
                            )
                            HorizontalDivider()
                            ListItem(
                                headlineContent = { Text("Wi-Fi · estimated") },
                                supportingContent = { Text(usage(listeningStats.wifiEstimatedBytes)) },
                            )
                            ListItem(
                                headlineContent = { Text("Mobile data · estimated") },
                                supportingContent = { Text(usage(listeningStats.mobileEstimatedBytes)) },
                            )
                            ListItem(
                                headlineContent = { Text("Other or unidentified network · estimated") },
                                supportingContent = { Text(usage(listeningStats.unknownNetworkEstimatedBytes)) },
                            )
                            ListItem(
                                headlineContent = { Text("How this is calculated") },
                                supportingContent = {
                                    Text("Listening time counts active playback only. Network use is estimated from the reported stream bitrate, not measured bytes. When the station does not report a bitrate, its consumption is excluded. Buffering, artwork, searches, and HTTP overhead are not counted.")
                                },
                            )
                            if (listeningStats.unestimatedMs > 0L) {
                                ListItem(
                                    headlineContent = { Text("Playback with unknown bitrate") },
                                    supportingContent = { Text("${listeningStats.unestimatedMs / 60_000L} minutes not included in data estimates") },
                                )
                            }
                            ListItem(
                                headlineContent = { Text("Reset listening statistics") },
                                supportingContent = { Text("Clear stored time and usage estimates") },
                                trailingContent = {
                                    TextButton(onClick = Library::clearListeningStats) { Text("Reset") }
                                },
                            )
                        } else if (current == "Diagnostics") {
                            ListItem(
                                headlineContent = { Text("Playback events") },
                                supportingContent = { Text("Session-only activity; no stream URLs or personal data") },
                                trailingContent = {
                                    TextButton(onClick = Library::clearPlaybackLog) { Text("Clear") }
                                },
                            )
                            if (playbackEvents.isEmpty()) {
                                ListItem(headlineContent = { Text("No events yet") })
                            } else {
                                playbackEvents.forEach { event ->
                                    ListItem(headlineContent = { Text(event) })
                                }
                            }
                        } else {
                            ListItem(
                                headlineContent = { Text("Connection diagnostics") },
                                supportingContent = { Text("Playback, buffering and stream errors") },
                                leadingContent = { Icon(Icons.Rounded.BugReport, null) },
                                modifier = Modifier.clickable { page = "Diagnostics" },
                            )
                            ListItem(
                                headlineContent = { Text("MRadio") },
                                supportingContent = { Text(versionDescription) },
                                leadingContent = { Icon(Icons.Rounded.Settings, null) },
                            )
                            ListItem(
                                headlineContent = { Text("Experimental updates") },
                                supportingContent = { Text("Offer signed APKs from successful pull request builds. Test builds may have bugs.") },
                                trailingContent = {
                                    Switch(
                                        checked = experimentalEnabled,
                                        onCheckedChange = AppUpdater::setExperimentalEnabled,
                                    )
                                },
                                modifier = Modifier.clickable {
                                    AppUpdater.setExperimentalEnabled(!experimentalEnabled)
                                },
                            )
                            HorizontalDivider()
                            ListItem(
                                headlineContent = { Text("Check for updates") },
                                supportingContent = { Text(if (experimentalEnabled) "Check latest green PR build and official releases" else "Check official GitHub releases") },
                                leadingContent = { Icon(Icons.Rounded.SystemUpdate, null) },
                                modifier = Modifier.clickable { onCheckUpdates() },
                            )
                        }
                    }
                }
            }
        }
        if (stationEditorOpen) {
            AlertDialog(
                onDismissRequest = { stationEditorOpen = false },
                title = { Text(if (editingStation == null) "Add station" else "Edit station") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = stationName, onValueChange = { stationName = it },
                            label = { Text("Station name") }, singleLine = true,
                        )
                        OutlinedTextField(
                            value = stationUrl, onValueChange = { stationUrl = it },
                            label = { Text("Stream URL (http or https)") }, singleLine = true,
                        )
                        OutlinedTextField(
                            value = stationTags, onValueChange = { stationTags = it },
                            label = { Text("Genres (comma separated)") }, singleLine = true,
                        )
                        OutlinedTextField(
                            value = stationCountry, onValueChange = { stationCountry = it },
                            label = { Text("Country code (e.g. MU)") }, singleLine = true,
                        )
                        stationStatus?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        runCatching {
                            Library.saveCustomStation(
                                editingStation?.id, stationName, stationUrl, stationTags, stationCountry,
                            )
                        }.onSuccess {
                            stationEditorOpen = false
                        }.onFailure {
                            stationStatus = it.message ?: "Could not save station"
                        }
                    }) { Text("Save") }
                },
                dismissButton = { TextButton(onClick = { stationEditorOpen = false }) { Text("Cancel") } },
            )
        }
        deleteTarget?.let { target ->
            AlertDialog(
                onDismissRequest = { deleteTarget = null },
                title = { Text("Delete ${target.name}?") },
                text = { Text("Remove this custom station and its favorite entry?") },
                confirmButton = {
                    TextButton(onClick = {
                        runCatching { Library.deleteCustomStation(target.id) }
                            .onFailure { stationStatus = it.message }
                        deleteTarget = null
                    }) { Text("Delete") }
                },
                dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } },
            )
        }
    }
}
