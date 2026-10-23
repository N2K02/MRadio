package com.mradio.app

import androidx.compose.runtime.Composable
import dev.n2k02.theme.N2K02Theme
import dev.n2k02.theme.N2K02SettingsRow
import dev.n2k02.theme.N2K02StatusCard

/**
 * Compile-time and device-test probe for the shared Material 3 Expressive theme.
 * Not wired into production navigation. Host remains responsible for UI rollout.
 */
@Composable
internal fun SharedThemeTestScreen() {
    N2K02Theme {
        N2K02StatusCard(
            title = "MRadio theme integration",
            detail = "Shared Material 3 Expressive theme",
        )
        N2K02SettingsRow(
            title = "Appearance",
            description = "Shared design-system component",
        )
    }
}
