package com.mradio.app

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Independent, stateless station row rendering. Interactions are provided by callbacks. */
@Composable
internal fun StationRow(
    station: Station,
    isCurrent: Boolean,
    isPlaying: Boolean,
    isPlayRequested: Boolean,
    inMineSection: Boolean,
    isMine: Boolean,
    isBroken: Boolean,
    fromDirectory: Boolean,
    onClick: () -> Unit,
    onHeart: () -> Unit,
    onRemove: () -> Unit,
    onInfo: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onCopyInfo: () -> Unit,
    isPinned: Boolean = false,
    onPin: (() -> Unit)? = null,
    onMoveUp: (() -> Unit)? = null,
    onMoveDown: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    // Only the selected row animates; all other rows remain static and inexpensive.
    val rowColor by animateColorAsState(
        targetValue = if (isCurrent) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceContainerLow,
        animationSpec = tween(durationMillis = 240),
        label = "Station selection",
    )
    ListItem(
        headlineContent = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(station.name, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Medium,
                    modifier = Modifier.weight(1f, fill = false))
                if (isCurrent) {
                    PlaybackStatusDot(playing = isPlaying, failed = isBroken)
                }
            }
        },
        supportingContent = {
            val line = when {
                isBroken -> "Not available right now"
                fromDirectory && station.country.length == 2 && station.country != "MU" ->
                    "${flagEmoji(station.country)} ${station.subtitle}"
                else -> station.subtitle
            }
            Text(
                line,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = if (isBroken) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        },
        leadingContent = { StationBadge(station, spinning = isPlaying, size = 46) },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClick, modifier = Modifier.size(48.dp)) {
                    Icon(
                        if (isPlayRequested) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (isPlayRequested) "Pause ${station.name}" else "Play ${station.name}",
                    )
                }
                var menuOpen by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Rounded.MoreVert, contentDescription = "More options for ${station.name}")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(if (isMine) "Remove from Favorites" else "Add to Favorites") },
                            onClick = { menuOpen = false; onHeart() },
                            leadingIcon = {
                                Icon(if (isMine) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, null)
                            },
                        )
                        if (inMineSection && onPin != null) {
                            DropdownMenuItem(
                                text = { Text(if (isPinned) "Unpin" else "Pin to top") },
                                onClick = { menuOpen = false; onPin() },
                                leadingIcon = { Icon(Icons.Rounded.PushPin, null) },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Share station") },
                            onClick = { menuOpen = false; onShare() },
                            leadingIcon = { Icon(Icons.Rounded.Share, null) },
                        )
                        DropdownMenuItem(
                            text = { Text("Copy station information") },
                            onClick = { menuOpen = false; onCopyInfo() },
                        )
                        DropdownMenuItem(
                            text = { Text("Copy stream URL") },
                            onClick = { menuOpen = false; onCopy() },
                        )
                        DropdownMenuItem(
                            text = { Text("Station details") },
                            onClick = { menuOpen = false; onInfo() },
                            leadingIcon = { Icon(Icons.Rounded.Info, null) },
                        )
                        if (inMineSection && onMoveUp != null && onMoveDown != null) {
                            DropdownMenuItem(text = { Text("Move up") },
                                onClick = { menuOpen = false; onMoveUp() })
                            DropdownMenuItem(text = { Text("Move down") },
                                onClick = { menuOpen = false; onMoveDown() })
                        }
                    }
                }
            }
        },
        colors = ListItemDefaults.colors(
            containerColor = rowColor,
        ),
        modifier = modifier
            .padding(horizontal = 16.dp, vertical = 3.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(
                onClickLabel = if (isPlayRequested) "Pause ${station.name}" else "Play ${station.name}",
                onClick = onClick,
            ),
    )
}

