package com.mradio.app

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

/** Shared mRadio Settings subpage. No standalone theme or duplicate header. */
@Composable
internal fun MRadioLogsPage(
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val enabled by AppLog.enabled.collectAsState()
    val version by AppLog.version.collectAsState()
    val logText = remember(version) { AppLog.all() }
    val playbackEvents by Library.events.collectAsState()

    BoxWithConstraints(modifier) {
        val compact = maxHeight < 530.dp
        Column(
            modifier = Modifier.fillMaxSize()
                .then(if (compact) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ListItem(
                headlineContent = { Text("Enable detailed logging") },
                supportingContent = { Text("Private diagnostics are optional; fatal crashes are always recorded.") },
                trailingContent = { Switch(checked = enabled, onCheckedChange = AppLog::setEnabled) },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            )
            Text(
                "Private storage · 7 days · fatal crashes always captured",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Surface(
                modifier = Modifier.fillMaxWidth().then(
                    if (compact) Modifier.height(120.dp) else Modifier.weight(1f)
                ),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                Text(
                    logText.ifBlank { "No saved diagnostic events." },
                    modifier = Modifier.verticalScroll(rememberScrollState()).padding(12.dp),
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        context.getSystemService(ClipboardManager::class.java)
                            ?.setPrimaryClip(ClipData.newPlainText("mRadio diagnostics", AppLog.all()))
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("Copy") }
                OutlinedButton(onClick = AppLog::clear, modifier = Modifier.weight(1f)) { Text("Clear") }
                Button(onClick = onBack, modifier = Modifier.weight(1f)) { Text("Back") }
            }
            HorizontalDivider()
            ListItem(
                headlineContent = { Text("Connection diagnostics · playback events") },
                supportingContent = { Text("Session-only playback, buffering and stream errors; stream URLs are redacted.") },
                trailingContent = {
                    TextButton(onClick = Library::clearPlaybackLog) { Text("Clear") }
                },
            )
            Surface(
                modifier = Modifier.fillMaxWidth().then(
                    if (compact) Modifier.height(160.dp) else Modifier.weight(1f)
                ),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                Text(
                    playbackEvents.ifEmpty { listOf("No playback events yet.") }.joinToString("\n"),
                    modifier = Modifier.verticalScroll(rememberScrollState()).padding(12.dp),
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}
