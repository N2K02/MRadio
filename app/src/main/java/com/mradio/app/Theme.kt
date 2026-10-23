package com.mradio.app

import androidx.compose.ui.unit.dp

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// A consistent radio-first palette. In particular, opaque surfaces prevent station
// rows from bleeding through the bottom navigation on edge-to-edge phones.
private val RadioDark = darkColorScheme(
    primary = Color(0xFF99C7FF),
    onPrimary = Color(0xFF0C2B48),
    primaryContainer = Color(0xFF19334D),
    onPrimaryContainer = Color(0xFFD5E8FF),
    secondary = Color(0xFFB2C9E3),
    secondaryContainer = Color(0xFF263441),
    onSecondaryContainer = Color(0xFFDCE8F4),
    tertiary = Color(0xFFB3D8D1),
    background = Color(0xFF101113),
    onBackground = Color(0xFFF1F2F4),
    surface = Color(0xFF101113),
    onSurface = Color(0xFFF1F2F4),
    surfaceVariant = Color(0xFF24272B),
    surfaceContainerLow = Color(0xFF1B1D20),
    surfaceContainer = Color(0xFF202226),
    surfaceContainerHigh = Color(0xFF292C30),
    onSurfaceVariant = Color(0xFFB8BEC7),
    outlineVariant = Color(0xFF383D43),
)
private val RadioLight = lightColorScheme(
    primary = Color(0xFF235A91),
    primaryContainer = Color(0xFFD7E9FF),
    onPrimaryContainer = Color(0xFF133756),
    secondaryContainer = Color(0xFFE5EDF5),
    background = Color(0xFFF7F8FA),
    surface = Color(0xFFF7F8FA),
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFF1F3F7),
    surfaceContainerHigh = Color(0xFFE7EBF0),
    outlineVariant = Color(0xFFD6DDE5),
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MRadioTheme(content: @Composable () -> Unit) {
    MaterialExpressiveTheme(
        colorScheme = if (isSystemInDarkTheme()) RadioDark else RadioLight,
        shapes = androidx.compose.material3.MaterialTheme.shapes.copy(extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(28.dp)),
        motionScheme = MotionScheme.expressive(),
        content = content,
    )
}
