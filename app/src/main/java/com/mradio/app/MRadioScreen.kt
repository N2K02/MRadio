package com.mradio.app

import android.os.Bundle
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/** What the player is doing, mirrored from the MediaController for Compose. */
@Stable
class PlayerUi {
    var mediaId by mutableStateOf<String?>(null)
    var isPlaying by mutableStateOf(false)
    var playWhenReady by mutableStateOf(false)
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
                ui.playWhenReady = c.playWhenReady
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

/** "MU" -> "Mauritius", "WW" -> "Worldwide". */
private fun placeName(place: String) = if (place == Directory.WORLDWIDE) "Worldwide" else countryName(place)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MRadioScreen(controller: MediaController?, onExit: () -> Unit) {
    val stations by Library.stations.collectAsState()
    val mine by Library.mine.collectAsState()
    val pinned by Library.pinned.collectAsState()
    val broken by Library.broken.collectAsState()
    val hidden by Library.hidden.collectAsState()
    val player = rememberPlayerUi(controller)
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    val context = LocalContext.current

    var query by rememberSaveable { mutableStateOf("") }
    var homeTab by rememberSaveable { mutableStateOf("All stations") }
    var filtersOpen by rememberSaveable { mutableStateOf(false) }
    var selectedGenre by rememberSaveable { mutableStateOf("All genres") }
    var stationSort by rememberSaveable { mutableStateOf("Default") }
    var workingOnly by rememberSaveable { mutableStateOf(false) }
    var place by rememberSaveable { mutableStateOf(Library.place) }
    var pickCountry by remember { mutableStateOf(false) }
    var playerOpen by rememberSaveable { mutableStateOf(false) }
    var infoFor by remember { mutableStateOf<Station?>(null) }
    var confirmHideStation by remember { mutableStateOf<Station?>(null) }
    var showHidden by remember { mutableStateOf(false) }
    var settingsMenu by remember { mutableStateOf(false) }
    val updateState by AppUpdater.state.collectAsState()
    var equalizerOpen by rememberSaveable { mutableStateOf(false) }

    BackHandler(enabled = homeTab != "All stations" && !settingsMenu && !playerOpen && !equalizerOpen && infoFor == null && !showHidden && !pickCountry && confirmHideStation == null) {
        homeTab = "All stations"
        query = ""
    }

    // Tap a section title to fold its list away. Kept across rotation and process restarts.
    // Reorder mode for Favourite Radios: up/down arrows replace the remove button.
    var arranging by rememberSaveable { mutableStateOf(false) }
    var moreOpen by rememberSaveable { mutableStateOf(true) }

    fun choosePlace(p: String) {
        place = p
        Library.place = p
    }

    // Long lists show a page at a time, with "Show more" at the end.
    var moreShown by remember(place) { mutableIntStateOf(PAGE) }
    var onlineShown by remember(query, place) { mutableIntStateOf(PAGE) }

    // "More from …": popular stations in the chosen place, from every directory, merged.
    var discovered by remember(place) { mutableStateOf<List<Station>?>(null) }
    var discoverFailed by remember { mutableStateOf(false) }
    var discoverKey by remember { mutableIntStateOf(0) }
    LaunchedEffect(place, discoverKey) {
        discoverFailed = false
        runCatching {
            Directory.popular(place).collect {
                discovered = it
                if (place == Directory.MAURITIUS) Library.setDiscovered(it) else Library.remember(it)
            }
        }.onFailure { if (discovered == null) discoverFailed = true }
    }

    // Online search in the chosen place, after a short pause in typing. Grows as Streema stations are checked.
    var online by remember { mutableStateOf<List<Station>?>(null) }
    var searching by remember { mutableStateOf(false) }
    var searchFailed by remember { mutableStateOf(false) }
    LaunchedEffect(query, place) {
        online = null
        searchFailed = false
        if (query.isBlank()) return@LaunchedEffect
        delay(450)
        searching = true
        runCatching {
            Directory.search(query, place).collect {
                online = it
                Library.remember(it)
            }
        }.onFailure { if (online == null) searchFailed = true }
        searching = false
    }

    val availableGenres = remember(stations, discovered, online) {
        (stations + discovered.orEmpty() + online.orEmpty())
            .flatMap { it.tags.split(',') }
            .map { it.trim().lowercase(Locale.ROOT) }
            .filter { it.isNotBlank() }
            .groupingBy { it }.eachCount()
            .entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .take(18).map { it.key }
    }
    fun filtered(items: List<Station>): List<Station> {
        val eligible = items.filter {
            (selectedGenre == "All genres" ||
                it.tags.split(',').any { tag -> tag.trim().equals(selectedGenre, ignoreCase = true) }) &&
                (!workingOnly || it.id !in broken)
        }
        return if (stationSort == "Name") eligible.sortedBy { it.name.lowercase(Locale.ROOT) } else eligible
    }
    val myList = remember(stations, mine, pinned, hidden) { Library.myStations() }
    val moreList = remember(stations, mine, discovered, place, hidden) {
        Library.moreStations(discovered.orEmpty(), mauritius = place == Directory.MAURITIUS)
    }
    val visibleMine = filtered(myList)
    val visibleMore = filtered(moreList)
    val localResults = filtered(Library.searchLocal(query))
    val onlineResults = online?.let { filtered(it.filterNot { station -> Library.known(station) || Library.isHidden(station) }) }

    val current = player.mediaId?.let { id -> Library.find(id) }
    val currentBroken = current != null && current.id in broken

    fun playOrPause(station: Station, from: List<Station>) {
        val c = controller ?: return
        if (player.mediaId == station.id && !player.failed) {
            if (c.playWhenReady) c.pause() else c.play()
        } else {
            Library.setBrowsing(from)
            Library.remember(listOf(station))
            c.setMediaItem(MediaItem.Builder().setMediaId(station.id).build())
            c.prepare()
            c.play()
        }
    }

    fun retry(station: Station) {
        val c = controller ?: return
        c.setMediaItem(MediaItem.Builder().setMediaId(station.id).build())
        c.prepare()
        c.play()
    }

    fun announce(text: String, undo: (() -> Unit)?) {
        snackbar.currentSnackbarData?.dismiss()
        scope.launch {
            val result = snackbar.showSnackbar(
                text,
                actionLabel = if (undo != null) "Undo" else null,
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) undo?.invoke()
        }
    }

    fun addToMine(station: Station) {
        val id = Library.addToMine(station)
        announce("${station.name} added to Favourite Radios") { Library.removeFromMine(id) }
    }

    fun removeFromMine(station: Station) {
        Library.removeFromMine(station.id)
        announce("${station.name} removed from Favourite Radios") { Library.addToMine(station) }
    }

    fun removeCompletely(station: Station) {
        val wasMine = Library.inMine(station)
        Library.hide(station)
        if (player.mediaId == station.id) controller?.stop()
        announce("${station.name} removed") {
            Library.unhide(normalizeName(station.name))
            if (wasMine) Library.addToMine(station)
        }
    }

    // A clear message when the playing radio can't be reached.
    LaunchedEffect(player.failed, currentBroken, current?.id) {
        val station = current ?: return@LaunchedEffect
        if (player.failed && currentBroken) {
            snackbar.currentSnackbarData?.dismiss()
            val result = snackbar.showSnackbar(
                "${station.name} isn't available right now",
                actionLabel = "Details",
                duration = SnackbarDuration.Long,
            )
            if (result == SnackbarResult.ActionPerformed) infoFor = station
        }
    }

    @Composable
    fun StationItem(station: Station, inMineSection: Boolean, list: List<Station>, modifier: Modifier) {
        val isMine = station.id in mine || Library.inMine(station)
        StationRow(
            station = station,
            isCurrent = player.mediaId == station.id,
            isPlaying = player.mediaId == station.id && player.isPlaying,
            isPlayRequested = player.mediaId == station.id && player.playWhenReady,
            inMineSection = inMineSection,
            isMine = isMine,
            isBroken = station.id in broken,
            fromDirectory = !Library.known(station),
            onClick = { focus.clearFocus(); playOrPause(station, list) },
            onHeart = { if (isMine) removeFromMine(station) else addToMine(station) },
            onRemove = { removeFromMine(station) },
            isPinned = station.id in pinned,
            onPin = if (inMineSection) ({ Library.togglePinned(station.id) }) else null,
            onInfo = { infoFor = station },
            onShare = {
                val stream = Library.orderedStreams(station).firstOrNull {
                    it.startsWith("https://", ignoreCase = true) ||
                        it.startsWith("http://", ignoreCase = true)
                }
                val description = buildString {
                    append(station.name)
                    if (station.tags.isNotBlank()) append(" · ").append(station.subtitle)
                    if (stream != null) append("\n").append(stream)
                }
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, station.name)
                    putExtra(Intent.EXTRA_TEXT, description)
                }
                runCatching {
                    context.startActivity(Intent.createChooser(intent, "Share station"))
                }.onFailure {
                    announce("No app available for sharing", null)
                }
            },
            onCopyInfo = {
                val details = buildString {
                    appendLine(station.name)
                    appendLine("Country: ${countryName(station.country)}")
                    if (station.tags.isNotBlank()) appendLine("Genres: ${station.tags}")
                    if (station.location.isNotBlank()) appendLine("Location: ${station.location}")
                    appendLine("Stream links: ${station.streams.size}")
                    val url = Library.orderedStreams(station).firstOrNull()
                    if (url != null) append("Preferred stream: ").append(url)
                }.trim()
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                clipboard?.setPrimaryClip(ClipData.newPlainText("Station information", details))
                announce("Station information copied", null)
            },
            onCopy = {
                val url = Library.orderedStreams(station).firstOrNull()
                if (url != null) {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    clipboard?.setPrimaryClip(ClipData.newPlainText("Station stream URL", url))
                    announce("Stream URL copied", null)
                } else announce("No stream URL is available yet", null)
            },
            onMoveUp = if (inMineSection && arranging) ({ Library.moveInMine(station.id, -1) }) else null,
            onMoveDown = if (inMineSection && arranging) ({ Library.moveInMine(station.id, +1) }) else null,
            modifier = modifier,
        )
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                // Keep the bar and gesture-navigation inset opaque, so station cards never
                // show through the controls on edge-to-edge Android layouts.
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 4.dp,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                Column(Modifier.navigationBarsPadding()) {
                    if (homeTab == "Search" && current != null) {
                        NowPlayingBar(
                            station = current,
                            player = player,
                            isMine = Library.inMine(current),
                            onOpen = { playerOpen = true },
                            onHeart = { addToMine(current) },
                            onPlayPause = { if (player.failed) retry(current) else playOrPause(current, emptyList()) },
                            onPrevious = { controller?.sendCustomCommand(StationCommands.PREVIOUS, Bundle.EMPTY) },
                            onNext = { controller?.sendCustomCommand(StationCommands.NEXT, Bundle.EMPTY) },
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                    NavigationBar(
                        modifier = Modifier.fillMaxWidth(),
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 0.dp,
                    ) {
                        NavigationBarItem(
                            selected = homeTab == "All stations" && !settingsMenu,
                            onClick = { homeTab = "All stations"; query = ""; settingsMenu = false },
                            icon = { Icon(Icons.Rounded.Home, contentDescription = null) },
                            label = { Text("Home") },
                            alwaysShowLabel = true,
                        )
                        NavigationBarItem(
                            selected = homeTab == "Search" && !settingsMenu,
                            onClick = { homeTab = "Search"; settingsMenu = false },
                            icon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                            label = { Text("Search") },
                            alwaysShowLabel = true,
                        )
                        NavigationBarItem(
                            selected = settingsMenu,
                            onClick = { settingsMenu = true },
                            icon = { Icon(Icons.Rounded.Settings, contentDescription = null) },
                            label = { Text("Settings") },
                            alwaysShowLabel = true,
                        )
                    }
                }
                }
            },
        ) { padding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = padding,
            ) {
                item(key = "home-head") {
                    Column(modifier = Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // Use the same branding as the installed launcher icon.
                            AndroidView(
                                factory = { ctx ->
                                    android.widget.ImageView(ctx).apply {
                                        setImageDrawable(ctx.packageManager.getApplicationIcon(ctx.packageName))
                                        scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
                                        contentDescription = "mRadio"
                                    }
                                },
                                modifier = Modifier.size(52.dp),
                            )
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
                if (homeTab == "All stations") {
                    item(key = "home-player") {
                        Surface(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            onClick = { if (current != null) playerOpen = true },
                        ) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (current != null) StationBadge(current, spinning = player.isPlaying, size = 52)
                                    else Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) {
                                        Icon(Icons.Rounded.GraphicEq, contentDescription = null)
                                    }
                                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                        Text(if (player.isPlaying) "● LIVE RADIO" else "NOW PLAYING",
                                            color = MaterialTheme.colorScheme.primary,
                                            style = MaterialTheme.typography.labelSmall)
                                        Text(current?.name ?: "Choose a station",
                                            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(
                                            if (current == null) "Select a radio below" else statusText(current, player, currentBroken),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    if (current != null) IconButton(onClick = {
                                        if (Library.inMine(current)) removeFromMine(current) else addToMine(current)
                                    }) {
                                        Icon(
                                            if (Library.inMine(current)) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                            contentDescription = "Toggle favorite",
                                        )
                                    }
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                                    horizontalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterHorizontally),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    IconButton(onClick = { controller?.sendCustomCommand(StationCommands.PREVIOUS, Bundle.EMPTY) },
                                        enabled = current != null) {
                                        Icon(Icons.Rounded.SkipPrevious, contentDescription = "Previous station")
                                    }
                                    FilledIconButton(
                                        onClick = {
                                            if (current != null) {
                                                if (player.failed) retry(current) else playOrPause(current, emptyList())
                                            } else myList.firstOrNull()?.let { playOrPause(it, myList) }
                                        },
                                        modifier = Modifier.size(48.dp),
                                        enabled = controller != null,
                                    ) {
                                        Icon(
                                            if (player.playWhenReady) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                            contentDescription = if (player.playWhenReady) "Pause radio" else "Play radio",
                                        )
                                    }
                                    IconButton(onClick = { controller?.sendCustomCommand(StationCommands.NEXT, Bundle.EMPTY) },
                                        enabled = current != null) {
                                        Icon(Icons.Rounded.SkipNext, contentDescription = "Next station")
                                    }
                                }
                            }
                        }
                    }
                }

                item(key = "station-controls") {
                    Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                        if (homeTab == "Search") {
                            TextField(
                                value = query,
                                onValueChange = { query = it },
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                                placeholder = { Text("Find a station") },
                                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                                trailingIcon = {
                                    if (query.isNotEmpty()) IconButton(onClick = { query = "" }) {
                                        Icon(Icons.Rounded.Close, contentDescription = "Clear search")
                                    }
                                },
                                singleLine = true,
                                shape = CircleShape,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
                            )
                        }
                        if (homeTab == "Search") PlaceChips(
                            place = place,
                            onPlace = ::choosePlace,
                            onPickCountry = { pickCountry = true },
                        )
                        if (homeTab == "Search") {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.End,
                            ) {
                                TextButton(onClick = { filtersOpen = !filtersOpen }) {
                                    Icon(Icons.Rounded.FilterList, contentDescription = null)
                                    Spacer(Modifier.width(6.dp))
                                    Text(if (filtersOpen) "Hide filters" else "Filter stations")
                                }
                            }
                            AnimatedVisibility(filtersOpen) {
                                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                                    Text("Genre", style = MaterialTheme.typography.labelMedium)
                                    Row(
                                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        (listOf("All genres") + availableGenres).forEach { genre ->
                                            FilterChip(
                                                selected = selectedGenre == genre,
                                                onClick = { selectedGenre = genre; moreShown = PAGE; onlineShown = PAGE },
                                                label = { Text(genre.replaceFirstChar { it.titlecase(Locale.ROOT) }) },
                                            )
                                        }
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        FilterChip(
                                            selected = stationSort == "Name",
                                            onClick = { stationSort = if (stationSort == "Name") "Default" else "Name" },
                                            label = { Text("Sort A–Z", style = MaterialTheme.typography.labelMedium) },
                                        )
                                        FilterChip(
                                            selected = workingOnly,
                                            onClick = { workingOnly = !workingOnly },
                                            label = { Text("Hide unavailable", style = MaterialTheme.typography.labelMedium) },
                                        )
                                        Spacer(Modifier.weight(1f))
                                        TextButton(onClick = {
                                            selectedGenre = "All genres"
                                            stationSort = "Default"
                                            workingOnly = false
                                        }) { Text("Reset") }
                                    }
                                }
                            }
                        }
                    }
                }

                if (homeTab == "All stations") {
                    header(
                        "Favourite Radios",
                        action = if (myList.size > 1) {
                            { TextButton(onClick = { arranging = !arranging }) {
                                Text(if (arranging) "Done" else "Reorder")
                            } }
                        } else null,
                    )
                    if (visibleMine.isEmpty()) message("No favorites yet. Find stations in Search and add them here.")
                    items(visibleMine, key = { "favorites-" + it.id }) {
                        StationItem(it, true, visibleMine, Modifier.animateItem())
                    }
                } else if (query.isBlank()) {
                    header(
                        if (place == Directory.WORLDWIDE) "Popular worldwide" else "More from ${placeName(place)}",
                        expanded = moreOpen,
                    ) { moreOpen = !moreOpen }
                    if (moreOpen) {
                        items(visibleMore.take(moreShown), key = { "more-" + it.id }) { StationItem(it, false, visibleMore, Modifier.animateItem()) }
                        showMore(shown = moreShown, total = visibleMore.size) { moreShown += PAGE }
                        when {
                            discovered != null -> if (visibleMore.isEmpty()) message("No matching stations for these filters.")
                            discoverFailed -> item {
                                RetryMessage("Couldn't reach the station directories for more stations.") { discoverKey++ }
                            }
                            else -> loading()
                        }
                    }
                    if (!moreOpen) {
                        item(key = "stations-collapsed") {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text("Station suggestions are collapsed",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                TextButton(onClick = { moreOpen = true }) { Text("Show stations") }
                            }
                        }
                    }
                    if (hidden.isNotEmpty()) {
                        item(key = "hidden") {
                            TextButton(
                                onClick = { showHidden = true },
                                modifier = Modifier.padding(start = 12.dp, top = 8.dp),
                            ) {
                                Icon(Icons.Rounded.VisibilityOff, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Removed stations (${hidden.size})")
                            }
                        }
                    }
                } else {
                    if (localResults.isNotEmpty()) {
                        header("Your stations")
                        items(localResults, key = { "q-" + it.id }) { StationItem(it, false, localResults, Modifier.animateItem()) }
                    }
                    header("Online · ${placeName(place)}")
                    val list = onlineResults.orEmpty()
                    items(list.take(onlineShown), key = { "q-new-" + it.id }) { StationItem(it, false, list, Modifier.animateItem()) }
                    showMore(shown = onlineShown, total = list.size) { onlineShown += PAGE }
                    when {
                        searching && (online == null || list.isNotEmpty()) -> loading()
                        onlineResults != null && list.isEmpty() && !searching -> message(
                            if (place == Directory.WORLDWIDE) "No other working stations found."
                            else "No other working stations found in ${placeName(place)}. Try Worldwide.",
                        )
                        searchFailed -> message("Couldn't reach the station directories. Check your connection.")
                        online == null -> loading()
                    }
                }
                item { Spacer(Modifier.size(24.dp)) }
            }
        }

        // The full player, opened from the bar at the bottom.
        AnimatedVisibility(
            visible = playerOpen && current != null,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
        ) {
            current?.let { station ->
                BackHandler { playerOpen = false }
                FullPlayer(
                    station = station,
                    player = player,
                    isMine = station.id in mine || Library.inMine(station),
                    isBroken = station.id in broken,
                    onCollapse = { playerOpen = false },
                    onHeart = { addToMine(station) },
                    onInfo = { infoFor = station },
                    onPlayPause = { if (player.failed) retry(station) else playOrPause(station, emptyList()) },
                    onPrevious = { controller?.sendCustomCommand(StationCommands.PREVIOUS, Bundle.EMPTY) },
                    onNext = { controller?.sendCustomCommand(StationCommands.NEXT, Bundle.EMPTY) },
                    onSleep = { minutes ->
                        controller?.sendCustomCommand(
                            StationCommands.SLEEP,
                            Bundle().apply { putInt(StationCommands.MINUTES, minutes) },
                        )
                    },
                )
            }
        }
    }

    if (settingsMenu) {
        RadioSettings(
            versionDescription = when (val u = updateState) {
                is AppUpdater.State.Available -> "Version ${u.release.versionName} available"
                is AppUpdater.State.Checking -> "Checking…"
                is AppUpdater.State.Downloading -> "Downloading…"
                is AppUpdater.State.Ready, is AppUpdater.State.NeedsPermission -> "Ready to install"
                is AppUpdater.State.UpToDate -> "Up to date · v${AppUpdater.installedVersionName}"
                else -> "Installed v${AppUpdater.installedVersionName}"
            },
            onEqualizer = {
                settingsMenu = false
                equalizerOpen = true
            },
            onCheckUpdates = {
                if (updateState is AppUpdater.State.Idle ||
                    updateState is AppUpdater.State.UpToDate ||
                    updateState is AppUpdater.State.Failed
                ) AppUpdater.check(manual = true)
            },
            onDismiss = { settingsMenu = false },
        )
    }

    if (equalizerOpen) EqualizerSheet(onDismiss = { equalizerOpen = false })

    if (pickCountry) {
        CountryPicker(
            selected = place,
            onPick = { choosePlace(it); pickCountry = false },
            onDismiss = { pickCountry = false },
        )
    }

    infoFor?.let { station ->
        StationInfoDialog(
            station = station,
            isMine = Library.inMine(station),
            failedAt = broken[station.id],
            onRemoveFromMine = { infoFor = null; removeFromMine(station) },
            onRemoveCompletely = { infoFor = null; confirmHideStation = station },
            onTryAgain = { infoFor = null; retry(station) },
            onSelectStream = { url ->
                Library.setPreferredStream(station, url)
                if (player.mediaId == station.id) {
                    val resume = player.isPlaying
                    controller?.setMediaItem(MediaItem.Builder().setMediaId(station.id).build())
                    controller?.prepare()
                    if (resume) controller?.play()
                }
                announce(if (url == null) "Automatic stream selection" else "Preferred stream saved", null)
            },
            onDismiss = { infoFor = null },
        )
    }

    confirmHideStation?.let { station ->
        AlertDialog(
            onDismissRequest = { confirmHideStation = null },
            title = { Text("Remove ${station.name}?") },
            text = {
                Text("This hides the station from lists and search. You can restore it later in Removed stations.")
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmHideStation = null
                    removeCompletely(station)
                }) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { confirmHideStation = null }) { Text("Cancel") }
            },
        )
    }

    if (showHidden) {
        HiddenStationsDialog(
            hidden = hidden,
            onRestore = { Library.unhide(it) },
            onDismiss = { showHidden = false },
        )
    }
}

// ---------------------------------------------------------------- country filter

@Composable
private fun PlaceChips(place: String, onPlace: (String) -> Unit, onPickCountry: () -> Unit) {
    val other = place != Directory.MAURITIUS && place != Directory.WORLDWIDE
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilterChip(
            selected = place == Directory.MAURITIUS,
            onClick = { onPlace(Directory.MAURITIUS) },
            modifier = Modifier.weight(1f),
            label = { Text("Mauritius", maxLines = 1, style = MaterialTheme.typography.labelMedium) },
        )
        FilterChip(
            selected = place == Directory.WORLDWIDE,
            onClick = { onPlace(Directory.WORLDWIDE) },
            modifier = Modifier.weight(1f),
            label = { Text("Worldwide", maxLines = 1, style = MaterialTheme.typography.labelMedium) },
        )
        FilterChip(
            selected = other,
            onClick = onPickCountry,
            modifier = Modifier.weight(1f),
            label = {
                Text(if (other) countryName(place) else "Country",
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelMedium)
            },
            trailingIcon = { Icon(Icons.Rounded.ArrowDropDown, null, Modifier.size(16.dp)) },
        )
    }
}

/** Every country, searchable, in the phone's language. */
@Composable
private fun CountryPicker(selected: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    var filter by remember { mutableStateOf("") }
    val all = remember {
        Locale.getISOCountries().map { it to countryName(it) }.sortedBy { it.second.lowercase() }
    }
    val shown = remember(filter) {
        val f = filter.trim()
        if (f.isEmpty()) all else all.filter { it.second.contains(f, ignoreCase = true) || it.first.equals(f, ignoreCase = true) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose a country") },
        text = {
            Column {
                TextField(
                    value = filter,
                    onValueChange = { filter = it },
                    placeholder = { Text("Search countries") },
                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    singleLine = true,
                    shape = CircleShape,
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.size(8.dp))
                LazyColumn(Modifier.heightIn(max = 380.dp)) {
                    items(shown, key = { it.first }) { (code, name) ->
                        ListItem(
                            headlineContent = { Text(name) },
                            leadingContent = { Text(flagEmoji(code), style = MaterialTheme.typography.titleLarge) },
                            colors = ListItemDefaults.colors(
                                containerColor = if (code == selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                            ),
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { onPick(code) },
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        dismissButton = {
            TextButton(onClick = { onPick(Directory.MAURITIUS) }) {
                Text("Reset to Mauritius")
            }
        },
    )
}

// ---------------------------------------------------------------- dialogs

/** A station that stopped working: what happened, and what to do about it. */
@Composable
private fun StationInfoDialog(
    station: Station,
    isMine: Boolean,
    failedAt: Long?,
    onRemoveFromMine: () -> Unit,
    onRemoveCompletely: () -> Unit,
    onTryAgain: () -> Unit,
    onSelectStream: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val lastWorked = Library.lastWorked(station.id)
    var selectedStream by remember(station.id) { mutableStateOf(Library.preferredStream(station.id)) }
    val streams = remember(station) { Library.orderedStreams(station) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                if (failedAt == null) Icons.Rounded.Info else Icons.Rounded.ErrorOutline,
                null,
                tint = if (failedAt == null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
        },
        title = { Text(if (failedAt == null) station.name else "${station.name} isn't available") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (failedAt != null) {
                    Text("All known streams failed. The station may be off air or have changed its address.")
                } else {
                    Text("Country: ${countryName(station.country)}")
                    if (station.tags.isNotBlank()) Text("Genres: ${station.tags}")
                    if (station.location.isNotBlank()) Text("Location: ${station.location}")
                    Text("Available stream links: ${station.streams.size}")
                }
                failedAt?.let { Text("Failed: ${DateUtils.getRelativeTimeSpanString(it)}", style = MaterialTheme.typography.bodySmall) }
                lastWorked?.let { Text("Last worked: ${DateUtils.getRelativeTimeSpanString(it)}", style = MaterialTheme.typography.bodySmall) }
                Spacer(Modifier.size(4.dp))
                if (streams.size > 1) {
                    Text("Stream preference", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Choose which known URL to try first. Automatic fallback remains enabled; different URLs may not provide different audio quality.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    var streamMenu by remember { mutableStateOf(false) }
                    Box {
                        OutlinedButton(onClick = { streamMenu = true }) {
                            Text(
                                selectedStream?.let { chosen ->
                                    "Preferred · " + (android.net.Uri.parse(chosen).host ?: "Stream")
                                } ?: "Automatic (last working)",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        DropdownMenu(expanded = streamMenu, onDismissRequest = { streamMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("Automatic (last working)") },
                                onClick = {
                                    streamMenu = false
                                    selectedStream = null
                                    onSelectStream(null)
                                },
                            )
                            streams.forEachIndexed { index, url ->
                                DropdownMenuItem(
                                    text = {
                                        Text("Stream ${index + 1} · " +
                                            (android.net.Uri.parse(url).host ?: "Unknown host"),
                                            maxLines = 1)
                                    },
                                    onClick = {
                                        streamMenu = false
                                        selectedStream = url
                                        onSelectStream(url)
                                    },
                                )
                            }
                        }
                    }
                }
                if (failedAt != null) {
                    OutlinedButton(onClick = onTryAgain, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.Refresh, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Try again")
                    }
                }
                if (isMine) {
                    OutlinedButton(onClick = onRemoveFromMine, modifier = Modifier.fillMaxWidth()) {
                        Text("Remove from Favourite Radios")
                    }
                }
                OutlinedButton(onClick = onRemoveCompletely, modifier = Modifier.fillMaxWidth()) {
                    Text("Remove completely")
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

@Composable
private fun HiddenStationsDialog(hidden: Map<String, String>, onRestore: (String) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Removed stations") },
        text = {
            LazyColumn(Modifier.heightIn(max = 380.dp)) {
                items(hidden.entries.sortedBy { it.value.lowercase() }, key = { it.key }) { (key, name) ->
                    ListItem(
                        headlineContent = { Text(name) },
                        trailingContent = { TextButton(onClick = { onRestore(key) }) { Text("Restore") } },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

// ---------------------------------------------------------------- list parts

/**
 * A section title. Pass [expanded] and [onToggle] to make it collapsible: tapping the whole row
 * folds or unfolds the list under it, and the arrow turns to show which.
 */
private fun LazyListScope.header(
    text: String,
    expanded: Boolean? = null,
    action: (@Composable () -> Unit)? = null,
    onToggle: () -> Unit = {},
) = item(key = "h-$text") {
    val collapsible = expanded != null
    val arrow by animateFloatAsState(if (expanded == false) -90f else 0f, label = "header arrow")
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (collapsible) Modifier.clickable(onClickLabel = if (expanded == true) "Collapse $text" else "Expand $text", onClick = onToggle) else Modifier)
            .padding(start = 20.dp, end = 16.dp, top = 10.dp, bottom = 4.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f).padding(vertical = 5.dp),
        )
        action?.invoke()
        if (collapsible) {
            Icon(
                Icons.Rounded.KeyboardArrowDown,
                contentDescription = if (expanded == true) "Collapse $text" else "Expand $text",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.rotate(arrow),
            )
        }
    }
}

/** Stations shown per page in long lists. */
private const val PAGE = 20

/** "Show 20 more · 57 left" button at the end of a long list. */
private fun LazyListScope.showMore(shown: Int, total: Int, onMore: () -> Unit) {
    if (total <= shown) return
    item(key = "more-button-$shown-$total") {
        Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
            OutlinedButton(onClick = onMore, modifier = Modifier.fillMaxWidth()) {
                Text("Show ${minOf(PAGE, total - shown)} more  ·  ${total - shown} left")
            }
        }
    }
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

/**
 * A station: tap to play. On the right:
 * - an info icon when it stopped working,
 * - in Favourite Radios: − to take it out,
 * - elsewhere: the heart (empty = tap to add to Favourite Radios, filled = already there).
 */
@Composable
private fun StationRow(
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
                Text(station.name, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Medium,
                    modifier = Modifier.weight(1f, fill = false))
                if (isCurrent && isPlayRequested) {
                    Text("LIVE", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary)
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
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (isBroken) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        },
        leadingContent = { StationBadge(station, spinning = isPlaying, size = 46) },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClick, modifier = Modifier.size(44.dp)) {
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
            .clickable(onClick = onClick),
    )
}

/**
 * The station's logo on an expressive Material shape. The shape turns slowly behind the
 * logo while the station plays. Without a logo (or while it loads) the initials show instead.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun StationBadge(station: Station, spinning: Boolean, size: Int = 52) {
    val scheme = MaterialTheme.colorScheme
    var logo by remember(station.id) { mutableStateOf(Library.logo(station)) }
    LaunchedEffect(station.id) {
        if (logo == null && Library.shouldLookUpLogo(station)) {
            RadioBrowser.logo(station)?.let {
                Library.saveLogo(station.id, it)
                logo = it
            }
        }
    }
    Box(
        Modifier.size(size.dp).clip(RoundedCornerShape((size / 4).dp))
            .background(Color.White),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            initials(station.name),
            style = if (size >= 100) MaterialTheme.typography.displayMedium
                else MaterialTheme.typography.titleMedium,
            color = Color(0xFF235A91),
            fontWeight = FontWeight.Bold,
        )
        logo?.let { url ->
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding((size / 14).dp),
            )
        }
    }
}

private fun statusText(station: Station, player: PlayerUi, broken: Boolean): String = when {
    player.failed && broken -> "Not available right now"
    player.failed -> "Can't reach this station right now"
    player.buffering -> "Connecting…"
    player.isPlaying -> player.title?.takeIf { it.isNotBlank() && it != station.name } ?: "Live"
    else -> "Paused"
}

/** The bar at the bottom: tap it to open the full player. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun NowPlayingBar(
    station: Station,
    player: PlayerUi,
    isMine: Boolean,
    onOpen: () -> Unit,
    onHeart: () -> Unit,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    val broken by Library.broken.collectAsState()
    val barColor by animateColorAsState(
        targetValue = if (player.failed) MaterialTheme.colorScheme.errorContainer
            else if (player.isPlaying) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceContainer,
        animationSpec = tween(240),
        label = "Mini-player background",
    )
    Surface(
        color = barColor,
        shape = RoundedCornerShape(20.dp),
        tonalElevation = 0.dp,
        onClick = onOpen,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
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
                    androidx.compose.animation.AnimatedContent(
                        targetState = statusText(station, player, station.id in broken),
                        transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
                        label = "Playback status",
                        modifier = Modifier.weight(1f),
                    ) { status ->
                        Text(
                            status,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (player.failed) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            if (!isMine) {
                IconButton(onClick = onHeart) {
                    Icon(Icons.Rounded.FavoriteBorder, contentDescription = "Add to Favourite Radios")
                }
            } else {
                IconButton(onClick = onPrevious) {
                    Icon(Icons.Rounded.SkipPrevious, contentDescription = "Previous station")
                }
            }
            Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) {
                FilledIconButton(onClick = onPlayPause, modifier = Modifier.size(52.dp)) {
                    Icon(
                        when {
                            player.failed -> Icons.Rounded.Refresh
                            player.playWhenReady -> Icons.Rounded.Pause
                            else -> Icons.Rounded.PlayArrow
                        },
                        contentDescription = when {
                            player.failed -> "Try again"
                            player.playWhenReady -> "Pause radio"
                            else -> "Play radio"
                        },
                    )
                }
                if (player.buffering && player.playWhenReady) {
                    LoadingIndicator(
                        Modifier.size(16.dp).align(Alignment.TopEnd),
                    )
                }
            }
            IconButton(onClick = onNext) {
                Icon(Icons.Rounded.SkipNext, contentDescription = "Next station")
            }
        }
    }
}

// ---------------------------------------------------------------- full player

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FullPlayer(
    station: Station,
    player: PlayerUi,
    isMine: Boolean,
    isBroken: Boolean,
    onCollapse: () -> Unit,
    onHeart: () -> Unit,
    onInfo: () -> Unit,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSleep: (Int) -> Unit,
) {
    val audio by Library.audio.collectAsState()
    val sleepAt by Library.sleepAt.collectAsState()
    val info by produceState<Directory.Info?>(null, station.id) { value = Directory.info(station) }
    val since = remember(station.id) { System.currentTimeMillis() }
    // Tick only while a sleep countdown exists, avoiding idle UI wake-ups.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(sleepAt) {
        if (sleepAt != null) {
            while (true) {
                now = System.currentTimeMillis()
                delay(15_000)
            }
        }
    }
    val uri = LocalUriHandler.current

    // Swipe down to minimise: pulling down while the page is at its top drags the whole card.
    // Let go past 140 dp, or flick it down, and it closes; otherwise it springs back.
    val closeAt = with(LocalDensity.current) { 140.dp.toPx() }
    var drag by remember { mutableFloatStateOf(0f) }
    val swipeToClose = remember(closeAt) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // Pushing the card back up shrinks the pull first, before the page scrolls.
                if (drag > 0f && available.y < 0f) {
                    val used = maxOf(available.y, -drag)
                    drag += used
                    return Offset(0f, used)
                }
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                // What the page couldn't scroll (already at the top) pulls the card down.
                if (source == NestedScrollSource.UserInput && available.y > 0f) {
                    drag += available.y
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (drag <= 0f) return Velocity.Zero
                if (drag > closeAt || available.y > 1800f) {
                    onCollapse()
                } else {
                    animate(drag, 0f) { value, _ -> drag = value }
                }
                return available
            }
        }
    }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(swipeToClose)
            .graphicsLayer { translationY = drag },
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState()),
        ) {
            // Top: collapse back to the small bar.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                IconButton(onClick = onCollapse) {
                    Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Make smaller")
                }
                Text(
                    "Now playing",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                )
                if (!isMine) {
                    IconButton(onClick = onHeart) {
                        Icon(Icons.Rounded.FavoriteBorder, contentDescription = "Add to Favourite Radios")
                    }
                } else {
                    Spacer(Modifier.size(48.dp))
                }
            }

            // Keep the artwork visually grouped, without adding animation work when paused.
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
            ) {
                Box(
                    Modifier.fillMaxWidth().padding(vertical = 28.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    StationBadge(station, spinning = player.isPlaying, size = 220)
                }
            }
            Text(
                station.name,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp).padding(top = 16.dp),
            )
            Text(
                statusText(station, player, isBroken),
                style = MaterialTheme.typography.bodyLarge,
                color = if (player.failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 2,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            )
            if (player.failed) {
                TextButton(onClick = onInfo, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Icon(Icons.Rounded.Info, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("What can I do?")
                }
            }

            // Controls
            Row(
                horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
            ) {
                FilledTonalIconButton(onClick = onPrevious, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Rounded.SkipPrevious, contentDescription = "Previous station")
                }
                Box(Modifier.size(84.dp), contentAlignment = Alignment.Center) {
                    FilledIconButton(onClick = onPlayPause, modifier = Modifier.size(84.dp)) {
                        Icon(
                            when {
                                player.failed -> Icons.Rounded.Refresh
                                player.playWhenReady -> Icons.Rounded.Pause
                                else -> Icons.Rounded.PlayArrow
                            },
                            contentDescription = when {
                                player.failed -> "Try again"
                                player.playWhenReady -> "Pause radio"
                                else -> "Play radio"
                            },
                            modifier = Modifier.size(40.dp),
                        )
                    }
                    if (player.buffering && player.playWhenReady) {
                        LoadingIndicator(Modifier.size(24.dp).align(Alignment.TopEnd))
                    }
                }
                FilledTonalIconButton(onClick = onNext, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Rounded.SkipNext, contentDescription = "Next station")
                }
            }

            // Volume
            VolumeRow(Modifier.padding(bottom = 12.dp))

            // Sleep timer: lined up with the volume row (icon in the same column as "−", buttons
            // between), and the time left on its own line underneath.
            var chosen by rememberSaveable { mutableIntStateOf(0) }
            LaunchedEffect(sleepAt) { if (sleepAt == null) chosen = 0 }
            val left = sleepAt?.let { ((it - now) / 60_000L + 1).coerceAtLeast(1) }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Rounded.Bedtime,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp),
                            tint = if (left != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    BoxWithConstraints(Modifier.weight(1f)) {
                        val short = maxWidth < 290.dp
                        val options = listOf(15, 30, 60, 90)
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                            options.forEachIndexed { i, m ->
                                SegmentedButton(
                                    selected = left != null && chosen == m,
                                    onClick = {
                                        if (left != null && chosen == m) {
                                            onSleep(0)
                                            chosen = 0
                                        } else {
                                            onSleep(m)
                                            chosen = m
                                        }
                                    },
                                    shape = SegmentedButtonDefaults.itemShape(index = i, count = options.size),
                                    icon = {},
                                    label = { Text(if (short) "$m" else "$m min", maxLines = 1, softWrap = false) },
                                )
                            }
                        }
                    }
                    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                        if (left != null) {
                            IconButton(onClick = { onSleep(0); chosen = 0 }) {
                                Icon(Icons.Rounded.Close, contentDescription = "Cancel the sleep timer")
                            }
                        }
                    }
                }
                Text(
                    if (left != null) "Radio stops in $left min" else "Sleep timer",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (left != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }

            // Details
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            ) {
                Column(Modifier.padding(vertical = 8.dp)) {
                    val i = info
                    val country = i?.country?.takeIf { it.length == 2 } ?: station.country.takeIf { it.length == 2 }
                    country?.let { DetailRow(Icons.Rounded.Public, "Country", "${flagEmoji(it)}  ${countryName(it)}") }
                    (i?.location?.ifBlank { null } ?: station.location.ifBlank { null })
                        ?.let { DetailRow(Icons.Rounded.LocationOn, "Location", it) }
                    i?.frequency?.ifBlank { null }?.let { DetailRow(null, "Frequency", it) }
                    DetailRow(null, "Sound quality", soundQuality(audio, i))
                    audio?.host?.let { DetailRow(null, "Stream from", it) }
                    i?.genres?.ifEmpty { null }?.let { DetailRow(null, "Genres", it.take(6).joinToString(", ")) }
                    i?.language?.ifBlank { null }?.let { DetailRow(Icons.Rounded.Language, "Language", it) }
                    DetailRow(null, "On air", "Live stream · the directories don't list programme times")
                    if (player.isPlaying) {
                        DetailRow(null, "Listening since", DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(since)))
                    }
                    Library.lastWorked(station.id)?.let {
                        DetailRow(null, "Last worked", DateUtils.getRelativeTimeSpanString(it, now, DateUtils.MINUTE_IN_MILLIS).toString())
                    }
                    i?.slogan?.ifBlank { null }?.let { DetailRow(null, "Slogan", it) }
                    if (i == null) {
                        Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                            LoadingIndicator(Modifier.size(36.dp))
                        }
                    }
                    i?.website?.ifBlank { null }?.let { site ->
                        HorizontalDivider(Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                        TextButton(
                            onClick = { runCatching { uri.openUri(site) } },
                            modifier = Modifier.padding(horizontal = 8.dp),
                        ) { Text("Open the station's website") }
                    }
                }
            }
            info?.description?.ifBlank { null }?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
            }
            Spacer(Modifier.size(24.dp))
        }
    }
}

/** "AAC · 128 kbps · 44.1 kHz · stereo", from the stream itself, else from the directory. */
private fun soundQuality(a: AudioInfo?, i: Directory.Info?): String {
    val parts = mutableListOf<String>()
    (a?.codec ?: i?.listedCodec?.ifBlank { null })?.let { parts += it }
    (a?.bitrateKbps ?: i?.listedBitrate)?.let { parts += "$it kbps" }
    a?.sampleRateHz?.let { parts += String.format(Locale.getDefault(), "%.1f kHz", it / 1000.0).replace(".0 ", " ") }
    a?.channels?.let { parts += if (it == 1) "mono" else if (it == 2) "stereo" else "$it channels" }
    val quality = when (val kbps = a?.bitrateKbps ?: i?.listedBitrate) {
        null -> null
        in 0..63 -> "basic"
        in 64..127 -> "good"
        in 128..191 -> "very good"
        else -> "excellent"
    }
    if (parts.isEmpty()) return "Shown once the stream plays"
    return parts.joinToString(" · ") + (quality?.let { " ($it)" } ?: "")
}

@Composable
private fun DetailRow(icon: ImageVector?, label: String, value: String) {
    ListItem(
        overlineContent = { Text(label) },
        headlineContent = { Text(value) },
        leadingContent = icon?.let { { Icon(it, null, tint = MaterialTheme.colorScheme.primary) } },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}
