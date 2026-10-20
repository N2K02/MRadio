package com.mradio.app

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * Equalizer and 3D sound. Opened from the gear menu on the main screen.
 * Every change is heard right away (see [SoundProcessor]) and saved.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EqualizerSheet(onDismiss: () -> Unit) {
    val s by SoundFx.settings.collectAsState()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Equalizer",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { SoundFx.reset() }) { Text("Reset") }
                Switch(
                    checked = s.enabled,
                    onCheckedChange = { on -> SoundFx.update { it.copy(enabled = on) } },
                    modifier = Modifier.semantics { contentDescription = "Sound effects" },
                )
            }
            Text(
                if (s.enabled) "Applies to every station." else "Sound effects are off.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp),
            )

            // ---- 3D sound
            SectionTitle("3D sound")
            ChipRow {
                SpaceMode.entries.forEach { mode ->
                    FilterChip(
                        selected = s.mode == mode,
                        onClick = { SoundFx.update { it.copy(mode = mode) } },
                        label = { Text(mode.label) },
                    )
                }
            }
            Text(
                s.mode.blurb,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
            if (s.mode != SpaceMode.OFF) {
                LabelledSlider(
                    label = "Strength",
                    value = s.intensity,
                    range = 0f..1f,
                    valueText = "${(s.intensity * 100).roundToInt()}%",
                    onChange = { v -> SoundFx.update { it.copy(intensity = v) } },
                )
            }

            Spacer(Modifier.size(16.dp))

            // ---- presets
            SectionTitle("Presets")
            ChipRow {
                SoundFx.PRESETS.forEach { (name, _) ->
                    FilterChip(
                        selected = s.preset == name,
                        onClick = { SoundFx.applyPreset(name) },
                        label = { Text(name) },
                    )
                }
            }

            Spacer(Modifier.size(8.dp))

            // ---- bands
            SoundFx.BAND_HZ.forEachIndexed { i, hz ->
                val db = s.bands.getOrElse(i) { 0f }
                LabelledSlider(
                    label = if (hz >= 1000) "${hz / 1000f}".removeSuffix(".0") + " kHz" else "$hz Hz",
                    value = db,
                    range = -12f..12f,
                    steps = 23,
                    valueText = signedDb(db),
                    onChange = { v -> SoundFx.setBand(i, v) },
                )
            }
            LabelledSlider(
                label = "Bass boost",
                value = s.bass,
                range = 0f..1f,
                valueText = "${(s.bass * 100).roundToInt()}%",
                onChange = { v -> SoundFx.update { it.copy(bass = v, preset = "Custom") } },
            )

            if (s.enabled && s.mode != SpaceMode.OFF) {
                Text(
                    "3D sound works best with headphones.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }
    }
}

private fun signedDb(db: Float): String {
    val v = db.roundToInt()
    return if (v > 0) "+$v dB" else "$v dB"
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

/** Chips in one row that scrolls sideways. */
@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.horizontalScroll(rememberScrollState()),
    ) { content() }
}

@Composable
private fun LabelledSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    valueText: String,
    onChange: (Float) -> Unit,
    steps: Int = 0,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(76.dp))
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            steps = steps,
            modifier = Modifier
                .weight(1f)
                .semantics {
                    contentDescription = label
                    stateDescription = valueText
                },
        )
        Text(
            valueText,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(60.dp),
            maxLines = 1,
        )
    }
}
