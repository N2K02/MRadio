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
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Restore
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
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
                    val data = stream.readNBytes(1_000_001)
                    require(data.size <= 1_000_000) { "Backup is too large" }
                    String(data, Charsets.UTF_8)
                } ?: error("Could not read backup file")
                val count = Library.restoreBackup(json)
                "Restored $count favorite stations and settings"
            }.getOrElse { "Restore failed: ${it.message ?: "Invalid backup"}" }
        }
    }
    var page by rememberSaveable { mutableStateOf("Settings") }
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
                                headlineContent = { Text("Listening statistics") },
                                supportingContent = { Text("Time listened and estimated Wi-Fi / mobile streaming") },
                                leadingContent = { Icon(Icons.Rounded.BarChart, null) },
                                modifier = Modifier.clickable { page = "Listening statistics" },
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
    }
}
