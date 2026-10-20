package com.mradio.app

import android.os.Bundle
import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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

/** "MU" -> "Mauritius", "WW" -> "Worldwide". */
private fun placeName(place: String) = if (place == Directory.WORLDWIDE) "Worldwide" else countryName(place)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MRadioScreen(controller: MediaController?, onExit: () -> Unit) {
    val stations by Library.stations.collectAsState()
    val mine by Library.mine.collectAsState()
    val broken by Library.broken.collectAsState()
    val hidden by Library.hidden.collectAsState()
    val player = rememberPlayerUi(controller)
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current

    var query by rememberSaveable { mutableStateOf("") }
    var place by rememberSaveable { mutableStateOf(Library.place) }
    var pickCountry by remember { mutableStateOf(false) }
    var playerOpen by rememberSaveable { mutableStateOf(false) }
    var infoFor by remember { mutableStateOf<Station?>(null) }
    var showHidden by remember { mutableStateOf(false) }
    var settingsMenu by remember { mutableStateOf(false) }
    var equalizerOpen by rememberSaveable { mutableStateOf(false) }

    // Tap a section title to fold its list away. Kept across rotation and process restarts.
    var favouritesOpen by rememberSaveable { mutableStateOf(true) }
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

    val myList = remember(stations, mine, hidden) { Library.myStations() }
    val moreList = remember(stations, mine, discovered, place, hidden) {
        Library.moreStations(discovered.orEmpty(), mauritius = place == Directory.MAURITIUS)
    }
    val localResults = remember(query, stations, mine, hidden) { Library.searchLocal(query) }
    val onlineResults = remember(online, stations, hidden) {
        online?.filterNot { Library.known(it) || Library.isHidden(it) }
    }

    val current = player.mediaId?.let { id -> Library.find(id) }
    val currentBroken = current != null && current.id in broken

    fun playOrPause(station: Station, from: List<Station>) {
        val c = controller ?: return
        if (player.mediaId == station.id && !player.failed) {
            if (c.isPlaying) c.pause() else c.play()
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
            inMineSection = inMineSection,
            isMine = isMine,
            isBroken = station.id in broken,
            fromDirectory = !Library.known(station),
            onClick = { focus.clearFocus(); playOrPause(station, list) },
            onHeart = { if (isMine) removeFromMine(station) else addToMine(station) },
            onRemove = { removeFromMine(station) },
            onInfo = { infoFor = station },
            onMoveUp = if (inMineSection && arranging) ({ Library.moveInMine(station.id, -1) }) else null,
            onMoveDown = if (inMineSection && arranging) ({ Library.moveInMine(station.id, +1) }) else null,
            modifier = modifier,
        )
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.nestedScroll(FooterScroll.connection),
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
              Column {
                AnimatedVisibility(
                    visible = current != null && !playerOpen,
                    enter = slideInVertically { it },
                    exit = slideOutVertically { it },
                ) {
                    current?.let { station ->
                        NowPlayingBar(
                            station = station,
                            player = player,
                            isMine = station.id in mine || Library.inMine(station),
                            onOpen = { playerOpen = true },
                            onHeart = { addToMine(station) },
                            onPlayPause = { if (player.failed) retry(station) else playOrPause(station, emptyList()) },
                            onPrevious = { controller?.sendCustomCommand(StationCommands.PREVIOUS, Bundle.EMPTY) },
                            onNext = { controller?.sendCustomCommand(StationCommands.NEXT, Bundle.EMPTY) },
                        )
                    }
                }
                UpdateFooterBar("MRadio")
              }
            },
        ) { padding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = padding,
            ) {
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = 24.dp, end = 16.dp, top = 16.dp),
                    ) {
                        Text(
                            "MRadio",
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f),
                        )
                        Box {
                            FilledTonalIconButton(onClick = { settingsMenu = true }) {
                                Icon(Icons.Rounded.Settings, contentDescription = "Settings")
                            }
                            DropdownMenu(expanded = settingsMenu, onDismissRequest = { settingsMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text("Equalizer") },
                                    leadingIcon = { Icon(Icons.Rounded.GraphicEq, contentDescription = null) },
                                    onClick = {
                                        settingsMenu = false
                                        equalizerOpen = true
                                    },
                                )
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        FilledTonalIconButton(onClick = onExit) {
                            Icon(Icons.Rounded.PowerSettingsNew, contentDescription = "Exit")
                        }
                    }
                    Text(
                        "Radio from Mauritius and all around the world",
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
                        placeholder = { Text("Search stations or find new ones") },
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
                    PlaceChips(
                        place = place,
                        onPlace = ::choosePlace,
                        onPickCountry = { pickCountry = true },
                    )
                }
                item(key = "update") { UpdateCard(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) }

                if (query.isBlank()) {
                    header(
                        "Favourite Radios",
                        expanded = favouritesOpen,
                        action = if (favouritesOpen && myList.size > 1) {
                            { TextButton(onClick = { arranging = !arranging }) { Text(if (arranging) "Done" else "Reorder") } }
                        } else null,
                    ) { favouritesOpen = !favouritesOpen }
                    if (favouritesOpen) {
                        if (myList.isEmpty()) message("Tap the heart on any station below to add it here.")
                        items(myList, key = { "mine-" + it.id }) { StationItem(it, true, myList, Modifier.animateItem()) }
                    }

                    header(
                        if (place == Directory.WORLDWIDE) "Popular worldwide" else "More from ${placeName(place)}",
                        expanded = moreOpen,
                    ) { moreOpen = !moreOpen }
                    if (moreOpen) {
                        items(moreList.take(moreShown), key = { "more-" + it.id }) { StationItem(it, false, moreList, Modifier.animateItem()) }
                        showMore(shown = moreShown, total = moreList.size) { moreShown += PAGE }
                        when {
                            discovered != null -> if (moreList.isEmpty()) message("No other stations found here.")
                            discoverFailed -> item {
                                RetryMessage("Couldn't reach the station directories for more stations.") { discoverKey++ }
                            }
                            else -> loading()
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
            onRemoveCompletely = { infoFor = null; removeCompletely(station) },
            onTryAgain = { infoFor = null; retry(station) },
            onDismiss = { infoFor = null },
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
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        FilterChip(
            selected = place == Directory.MAURITIUS,
            onClick = { onPlace(Directory.MAURITIUS) },
            label = { Text("Mauritius") },
            leadingIcon = { Icon(Icons.Rounded.LocationOn, null, Modifier.size(18.dp)) },
        )
        FilterChip(
            selected = place == Directory.WORLDWIDE,
            onClick = { onPlace(Directory.WORLDWIDE) },
            label = { Text("Worldwide") },
            leadingIcon = { Icon(Icons.Rounded.Public, null, Modifier.size(18.dp)) },
        )
        FilterChip(
            selected = other,
            onClick = onPickCountry,
            label = { Text(if (other) "${flagEmoji(place)}  ${countryName(place)}" else "Country") },
            trailingIcon = { Icon(Icons.Rounded.ArrowDropDown, null, Modifier.size(18.dp)) },
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
    onDismiss: () -> Unit,
) {
    val lastWorked = Library.lastWorked(station.id)
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.ErrorOutline, null, tint = MaterialTheme.colorScheme.error) },
        title = { Text("${station.name} isn't available") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "None of its stream links answered, including the backup links MRadio looked up online. " +
                        "The station may be off air, or it may have moved to a new link.",
                )
                failedAt?.let { Text("Failed: ${DateUtils.getRelativeTimeSpanString(it)}", style = MaterialTheme.typography.bodySmall) }
                lastWorked?.let { Text("Last worked: ${DateUtils.getRelativeTimeSpanString(it)}", style = MaterialTheme.typography.bodySmall) }
                Spacer(Modifier.size(4.dp))
                OutlinedButton(onClick = onTryAgain, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.Refresh, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Try again")
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
        confirmButton = { TextButton(onClick = onDismiss) { Text("Keep") } },
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
            .padding(start = 24.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f).padding(vertical = 8.dp),
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
    inMineSection: Boolean,
    isMine: Boolean,
    isBroken: Boolean,
    fromDirectory: Boolean,
    onClick: () -> Unit,
    onHeart: () -> Unit,
    onRemove: () -> Unit,
    onInfo: () -> Unit,
    onMoveUp: (() -> Unit)? = null,
    onMoveDown: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    ListItem(
        headlineContent = {
            Text(station.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
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
                color = if (isBroken) MaterialTheme.colorScheme.error else Color.Unspecified,
            )
        },
        leadingContent = { StationBadge(station, spinning = isPlaying) },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isBroken) {
                    IconButton(onClick = onInfo) {
                        Icon(Icons.Rounded.Info, contentDescription = "Why isn't it working?", tint = MaterialTheme.colorScheme.error)
                    }
                }
                if (onMoveUp != null && onMoveDown != null) {
                    IconButton(onClick = onMoveUp) {
                        Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = "Move ${station.name} up")
                    }
                    IconButton(onClick = onMoveDown) {
                        Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Move ${station.name} down")
                    }
                } else if (inMineSection) {
                    IconButton(onClick = onRemove) {
                        Icon(
                            Icons.Rounded.RemoveCircleOutline,
                            contentDescription = "Remove from Favourite Radios",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    IconButton(onClick = onHeart) {
                        Icon(
                            if (isMine) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                            contentDescription = if (isMine) "Remove from Favourite Radios" else "Add to Favourite Radios",
                            tint = if (isMine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        colors = ListItemDefaults.colors(
            containerColor = if (isCurrent) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
        ),
        modifier = modifier
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(24.dp))
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
                style = when {
                    size >= 120 -> MaterialTheme.typography.displayMedium
                    size >= 48 -> MaterialTheme.typography.titleMedium
                    else -> MaterialTheme.typography.labelLarge
                },
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
                    .padding(if (loaded) (size / 16).dp else 0.dp),
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
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(32.dp),
        tonalElevation = 3.dp,
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
                    Text(
                        statusText(station, player, station.id in broken),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (player.failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
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
                if (player.buffering) {
                    LoadingIndicator(Modifier.size(44.dp))
                } else {
                    FilledIconButton(onClick = onPlayPause, modifier = Modifier.size(52.dp)) {
                        Icon(
                            when {
                                player.failed -> Icons.Rounded.Refresh
                                player.isPlaying -> Icons.Rounded.Pause
                                else -> Icons.Rounded.PlayArrow
                            },
                            contentDescription = when {
                                player.failed -> "Try again"
                                player.isPlaying -> "Pause"
                                else -> "Play"
                            },
                        )
                    }
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
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(15_000)
        }
    }
    val uri = LocalUriHandler.current

    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxSize()) {
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

            Box(Modifier.fillMaxWidth().padding(top = 24.dp), contentAlignment = Alignment.Center) {
                StationBadge(station, spinning = player.isPlaying, size = 220)
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
                    if (player.buffering) {
                        LoadingIndicator(Modifier.size(80.dp))
                    } else {
                        FilledIconButton(onClick = onPlayPause, modifier = Modifier.size(84.dp)) {
                            Icon(
                                when {
                                    player.failed -> Icons.Rounded.Refresh
                                    player.isPlaying -> Icons.Rounded.Pause
                                    else -> Icons.Rounded.PlayArrow
                                },
                                contentDescription = when {
                                    player.failed -> "Try again"
                                    player.isPlaying -> "Pause"
                                    else -> "Play"
                                },
                                modifier = Modifier.size(40.dp),
                            )
                        }
                    }
                }
                FilledTonalIconButton(onClick = onNext, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Rounded.SkipNext, contentDescription = "Next station")
                }
            }

            // Sleep timer
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
            ) {
                Icon(Icons.Rounded.Bedtime, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                val left = sleepAt?.let { ((it - now) / 60_000L + 1).coerceAtLeast(1) }
                listOf(15, 30, 60, 90).forEach { m ->
                    FilterChip(
                        selected = false,
                        onClick = { onSleep(m) },
                        label = { Text("$m min") },
                    )
                }
                if (left != null) {
                    FilterChip(
                        selected = true,
                        onClick = { onSleep(0) },
                        label = { Text("Stops in $left min  ✕") },
                    )
                }
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
