package com.mradio.app

import androidx.compose.runtime.Composable
import dev.n2k02.theme.N2K02Theme

/** The live app theme now uses the shared Material 3 Expressive design system. */
@Composable
fun MRadioTheme(content: @Composable () -> Unit) {
    N2K02Theme(content = content)
}
