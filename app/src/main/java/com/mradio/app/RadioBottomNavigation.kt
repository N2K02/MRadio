package com.mradio.app

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Bottom navigation is a stateless component: screen state lives with the parent. */
@Composable
internal fun RadioBottomNavigation(
    destination: RadioDestination,
    settingsSelected: Boolean,
    updateAvailable: Boolean,
    onHome: () -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    onExit: () -> Unit,
) {
    NavigationBar(
        modifier = Modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
    ) {
        NavigationBarItem(
            selected = destination == RadioDestination.HOME && !settingsSelected,
            onClick = onHome,
            icon = { Icon(Icons.Rounded.Home, contentDescription = null) },
            label = { Text("Home") },
            alwaysShowLabel = true,
        )
        NavigationBarItem(
            selected = destination == RadioDestination.SEARCH && !settingsSelected,
            onClick = onSearch,
            icon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            label = { Text("Search") },
            alwaysShowLabel = true,
        )
        NavigationBarItem(
            selected = settingsSelected,
            onClick = onSettings,
            icon = {
                BadgedBox(badge = { if (updateAvailable) UpdateAvailableBadge() }) {
                    Icon(Icons.Rounded.Settings, contentDescription = if (updateAvailable) "Settings, update available" else null)
                }
            },
            label = { Text("Settings") },
            alwaysShowLabel = true,
        )
        NavigationBarItem(
            selected = false,
            onClick = onExit,
            icon = { Icon(Icons.Rounded.PowerSettingsNew, contentDescription = "Quit mRadio") },
            label = { Text("Quit") },
            alwaysShowLabel = true,
        )
    }
}
