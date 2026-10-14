package com.mradio.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** What the player is doing, mirrored from the MediaController for Compose. */
@Stable
class PlayerUi {
    var mediaId by mutableStateOf<String?>(null)
    var isPlaying by mutableStateOf(false)
    var buffering by mutableStateOf(false)
    var failed by mutableStateOf(false)
    var title by mutableStateOf<String?>(null)
}

@Composable
private fun rememberPlayerUi(controller: MediaController?): PlayerUi {
    val ui = remember { PlayerUi() }
    DisposableEffect(controller) {
        val c = controller
        if (c == null) {
            onDispose { }
        } else {
            fun sync() {
                ui.mediaId = c.currentMediaItem?.mediaId
                ui.isPlaying = c.isPlaying
                ui.buffering = c.playbackState == Player.STATE_BUFFERING
                ui.failed = c.playerError != null
                ui.title = c.mediaMetadata.title?.toString()
            }
            sync()
            val listener = object : Player.Listener {
                override fun onEvents(player: Player, events: Player.Events) = sync()
            }
            c.addListener(listener)
            onDispose { c.removeListener(listener) }
        }
    }
    return ui
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MRadioScreen(controller: MediaController?) {
    val stations by Library.stations.collectAsState()
    val favourites by Library.favourites.collectAsState()
    val player = rememberPlayerUi(controller)
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current

    var query by rememberSaveable { mutableStateOf("") }
    var worldwide by rememberSaveable { mutableStateOf(false) }
    var removing by remember { mutableStateOf<Station?>(null) }

    // "More from Mauritius": stations in the Radio Browser directory that aren't in the list yet.
    var discovered by remember { mutableStateOf<List<Station>?>(null) }
    var discoverFailed by remember { mutableStateOf(false) }
    var discoverKey by remember { mutableIntStateOf(0) }
    LaunchedEffect(discoverKey) {
        discoverFailed = false
        runCatching { RadioBrowser.mauritius() }
            .onSuccess { discovered = it; Library.remember(it) }
            .onFailure { discoverFailed = true }
    }

    // Online search, after a short pause in typing.
    var online by remember { mutableStateOf<List<Station>?>(null) }
    var searching by remember { mutableStateOf(false) }
    var searchFailed by remember { mutableStateOf(false) }
    LaunchedEffect(query, worldwide) {
        online = null
        searchFailed = false
        if (query.isBlank()) return@LaunchedEffect
        delay(450)
        searching = true
        runCatching { RadioBrowser.search(query, mauritiusOnly = !worldwide) }
            .onSuccess { online = it; Library.remember(it) }
            .onFailure { searchFailed = true }
        searching = false
    }

    val current = player.mediaId?.let { id -> Library.find(id) }

    fun playOrPause(station: Station) {
        val c = controller ?: return
        if (player.mediaId == station.id && !player.failed) {
            if (c.isPlaying) c.pause() else c.play()
        } else {
            Library.remember(listOf(station))
            c.setMediaItem(MediaItem.Builder().setMediaId(station.id).build())
            c.prepare()
            c.play()
        }
    }

    fun add(station: Station) {
        Library.add(station)
        scope.launch { snackbar.showSnackbar("${station.name} added to your stations") }
    }

    @Composable
    fun StationItem(station: Station, inLibrary: Boolean) {
        StationRow(
            station = station,
            isCurrent = player.mediaId == station.id,
            isPlaying = player.mediaId == station.id && player.isPlaying,
            isFavourite = station.id in favourites,
            inLibrary = inLibrary,
            onClick = { focus.clearFocus(); playOrPause(station) },
            onLongClick = if (inLibrary && !station.builtIn) ({ removing = station }) else null,
            onFavourite = { Library.toggleFavourite(station.id) },
            onAdd = { add(station) },
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            AnimatedVisibility(
                visible = current != null,
                enter = slideInVertically { it },
                exit = slideOutVertically { it },
            ) {
                current?.let { station ->
                    NowPlayingBar(station = station, player = player, onPlayPause = { playOrPause(station) })
                }
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = padding,
        ) {
            item {
                Text(
                    "MRadio",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp),
                )
                Text(
                    "Radio from all around Mauritius",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 16.dp),
                )
                TextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    placeholder = { Text("Search your stations or find new ones") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Rounded.Close, contentDescription = "Clear search")
                            }
                        }
                    },
                    singleLine = true,
                    shape = CircleShape,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent,
                    ),
                )
                Spacer(Modifier.size(8.dp))
            }

            if (query.isBlank()) {
                val favs = stations.filter { it.id in favourites }
                if (favs.isNotEmpty()) {
                    header("Favourites")
                    items(favs, key = { "fav-" + it.id }) { StationItem(it, inLibrary = true) }
                }
                header("Your stations")
                items(stations.filter { it.id !in favourites }, key = { "lib-" + it.id }) { StationItem(it, inLibrary = true) }

                header("More from Mauritius")
                val fresh = discovered?.filterNot { s -> stations.any { normalizeName(it.name) == normalizeName(s.name) || it.id == s.id } }
                when {
                    fresh != null && fresh.isNotEmpty() ->
                        items(fresh, key = { "new-" + it.id }) { StationItem(it, inLibrary = false) }
                    fresh != null -> message("You already have every Mauritius station in the directory.")
                    discoverFailed -> item {
                        RetryMessage("Couldn't reach the station directory.") { discoverKey++ }
                    }
                    else -> loading()
                }
            } else {
                item {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(horizontal = 16.dp),
                    ) {
                        FilterChip(
                            selected = !worldwide,
                            onClick = { worldwide = false },
                            label = { Text("Mauritius") },
                            leadingIcon = { Icon(Icons.Rounded.LocationOn, null, Modifier.size(18.dp)) },
                        )
                        FilterChip(
                            selected = worldwide,
                            onClick = { worldwide = true },
                            label = { Text("Worldwide") },
                            leadingIcon = { Icon(Icons.Rounded.Public, null, Modifier.size(18.dp)) },
                        )
                    }
                }
                val local = Library.searchLocal(query)
                if (local.isNotEmpty()) {
                    header("Your stations")
                    items(local, key = { "q-lib-" + it.id }) { StationItem(it, inLibrary = true) }
                }
                header("Online")
                val results = online?.filterNot { s -> stations.any { normalizeName(it.name) == normalizeName(s.name) || it.id == s.id } }
                when {
                    results != null && results.isNotEmpty() ->
                        items(results, key = { "q-new-" + it.id }) { StationItem(it, inLibrary = false) }
                    results != null -> message(
                        if (worldwide) "No other stations found." else "No other Mauritius stations found. Try Worldwide.",
                    )
                    searchFailed -> message("Couldn't reach the station directory. Check your connection.")
                    searching || online == null -> loading()
                }
            }
            item { Spacer(Modifier.size(24.dp)) }
        }
    }

    removing?.let { station ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text("Remove ${station.name}?") },
            text = { Text("You can find it again with search.") },
            confirmButton = {
                TextButton(onClick = { Library.remove(station.id); removing = null }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { removing = null }) { Text("Cancel") } },
        )
    }
}

private fun LazyListScope.header(text: String) = item(key = "h-$text") {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 8.dp),
    )
}

private fun LazyListScope.message(text: String) = item {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun LazyListScope.loading() = item {
    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
        LoadingIndicator(Modifier.size(48.dp))
    }
}

@Composable
private fun RetryMessage(text: String, onRetry: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(start = 24.dp, end = 12.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onRetry) {
            Icon(Icons.Rounded.Refresh, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Retry")
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StationRow(
    station: Station,
    isCurrent: Boolean,
    isPlaying: Boolean,
    isFavourite: Boolean,
    inLibrary: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    onFavourite: () -> Unit,
    onAdd: () -> Unit,
) {
    ListItem(
        headlineContent = {
            Text(station.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
        },
        supportingContent = {
            val streams = station.streams.size
            Text(
                if (inLibrary) station.subtitle else "${station.subtitle} · $streams ${if (streams == 1) "stream" else "streams"}",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        leadingContent = { StationBadge(station, spinning = isPlaying) },
        trailingContent = {
            if (inLibrary) {
                IconToggleButton(checked = isFavourite, onCheckedChange = { onFavourite() }) {
                    Icon(
                        if (isFavourite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        contentDescription = if (isFavourite) "Remove from favourites" else "Add to favourites",
                        tint = if (isFavourite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                FilledTonalIconButton(onClick = onAdd) {
                    Icon(Icons.Rounded.Add, contentDescription = "Add to your stations")
                }
            }
        },
        colors = ListItemDefaults.colors(
            containerColor = if (isCurrent) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
        ),
        modifier = Modifier
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(24.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    )
}

/**
 * The station's logo on an expressive Material shape. The shape turns slowly behind the
 * logo while the station plays. Without a logo (or while it loads) the initials show instead.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun StationBadge(station: Station, spinning: Boolean, size: Int = 52) {
    val shapes = listOf(
        MaterialShapes.Cookie9Sided,
        MaterialShapes.Clover4Leaf,
        MaterialShapes.Sunny,
        MaterialShapes.Gem,
        MaterialShapes.Cookie6Sided,
        MaterialShapes.Flower,
        MaterialShapes.Pentagon,
    )
    val index = Math.floorMod(station.name.hashCode(), shapes.size)
    val scheme = MaterialTheme.colorScheme
    val (bg, fg) = when (Math.floorMod(station.name.hashCode() / 7, 3)) {
        0 -> scheme.primaryContainer to scheme.onPrimaryContainer
        1 -> scheme.tertiaryContainer to scheme.onTertiaryContainer
        else -> scheme.secondaryContainer to scheme.onSecondaryContainer
    }
    val rotation = if (spinning) {
        val t = rememberInfiniteTransition(label = "badge")
        t.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(8000, easing = LinearEasing), RepeatMode.Restart),
            label = "rotation",
        ).value
    } else {
        0f
    }
    val shape = shapes[index].toShape()

    // Logo: the station's own, one found earlier, or look one up once for stations in the list.
    var logo by remember(station.id) { mutableStateOf(Library.logo(station)) }
    LaunchedEffect(station.id) {
        if (logo == null && Library.shouldLookUpLogo(station)) {
            RadioBrowser.logo(station)?.let {
                Library.saveLogo(station.id, it)
                logo = it
            }
        }
    }
    var loaded by remember(logo) { mutableStateOf(false) }

    Box(Modifier.size(size.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { rotationZ = rotation }
                .clip(shape)
                .background(if (spinning) scheme.primary else bg),
        )
        if (!loaded) {
            Text(
                initials(station.name),
                color = if (spinning) scheme.onPrimary else fg,
                style = if (size >= 48) MaterialTheme.typography.titleMedium else MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
            )
        }
        logo?.let { url ->
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                onSuccess = { loaded = true },
                onError = { loaded = false },
                modifier = Modifier
                    .size((size * 0.7f).dp)
                    .clip(CircleShape)
                    .background(if (loaded) Color.White else Color.Transparent)
                    .padding(if (loaded) 3.dp else 0.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun NowPlayingBar(station: Station, player: PlayerUi, onPlayPause: () -> Unit) {
    val status = when {
        player.failed -> "Can't reach this station right now"
        player.buffering -> "Connecting…"
        player.isPlaying -> player.title?.takeIf { it.isNotBlank() && it != station.name } ?: "Live"
        else -> "Paused"
    }
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(32.dp),
        tonalElevation = 3.dp,
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        ) {
            StationBadge(station, spinning = player.isPlaying, size = 44)
            Column(
                Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
            ) {
                Text(
                    station.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (player.failed) {
                        Icon(
                            Icons.Rounded.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp).padding(end = 4.dp),
                        )
                    }
                    Text(
                        status,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (player.failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
                if (player.buffering) {
                    LoadingIndicator(Modifier.size(48.dp))
                } else {
                    FilledIconButton(onClick = onPlayPause, modifier = Modifier.size(56.dp)) {
                        Icon(
                            if (player.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (player.isPlaying) "Pause" else "Play",
                        )
                    }
                }
            }
        }
    }
}
