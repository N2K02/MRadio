package com.mradio.app

import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.rounded.Settings
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
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
                        } else {
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
