package com.mradio.app

import android.app.PendingIntent
import android.content.ComponentName
import android.os.Build
import android.service.quicksettings.TileService
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.Metadata
import androidx.media3.extractor.metadata.icy.IcyHeaders
import androidx.media3.extractor.metadata.icy.IcyInfo
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.CommandButton
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Plays the radio and serves the station browser for Android Auto, Bluetooth and the
 * media notification.
 *
 * Fallback: each station has an ordered list of streams. When one fails, the next is tried.
 * When all have failed, backup streams for the same station are looked up on Radio Browser and
 * Streema. The stream that plays is remembered and tried first next time. If nothing plays,
 * the station is marked as not working (the app shows an info icon for it).
 *
 * Reconnect: when a stream fails while there is no internet, the fallbacks are not touched (they
 * would all fail too). The service waits for the connection to come back, retrying with a growing
 * delay as a safety net, then starts again from the best stream. A station that was playing fine
 * is never given up on; after every stream failed it keeps cycling with a delay in between.
 */
@OptIn(UnstableApi::class)
class PlaybackService : MediaLibraryService() {

    private lateinit var player: ExoPlayer
    private lateinit var session: MediaLibrarySession
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val searchResults = HashMap<String, List<Station>>()

    // Fallback state for the station being played.
    private var current: Station? = null
    private var queue: List<String> = emptyList()
    private var attempt = 0
    private var lookedUpOnline = false
    private var wasPlaying = false
    private var sleepJob: Job? = null
    /** The song the station last announced (ICY "StreamTitle"), shown instead of the station name. */
    private var lastSong: String? = null

    // Reconnect state.
    private var playedBefore = false          // this station has played fine since it was selected
    private var waitingForNetwork = false
    private var retryRound = 0
    private var retryJob: Job? = null
    // Listening accounting runs only during actual audio output, not while paused or idle.
    private var listeningStartMs = 0L
    private var listeningTransport = "unknown"
    private var listeningBitrateKbps: Int? = null
    private var listeningJob: Job? = null

    private fun networkTransport(): String {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        return when {
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "wifi"
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "mobile"
            else -> "unknown"
        }
    }

    private fun flushListening() {
        if (listeningStartMs == 0L) return
        val now = SystemClock.elapsedRealtime()
        val duration = (now - listeningStartMs).coerceAtLeast(0L)
        listeningStartMs = now
        // Respect the 60-second maximum accepted by Library.recordListening.
        var remaining = duration
        while (remaining > 0L) {
            val step = minOf(remaining, 60_000L)
            Library.recordListening(step, listeningBitrateKbps, listeningTransport)
            remaining -= step
        }
    }

    private fun updateListening(active: Boolean) {
        if (!active) {
            flushListening()
            listeningStartMs = 0L
            listeningJob?.cancel()
            listeningJob = null
            return
        }
        if (listeningStartMs != 0L) return
        listeningStartMs = SystemClock.elapsedRealtime()
        listeningTransport = networkTransport()
        listeningBitrateKbps = Library.audio.value?.bitrateKbps
        listeningJob = scope.launch {
            while (true) {
                delay(60_000L)
                flushListening()
                listeningTransport = networkTransport()
                listeningBitrateKbps = Library.audio.value?.bitrateKbps
            }
        }
    }


    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        // Runs on a system thread; hop to the main thread before touching the player.
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            scope.launch {
                if (listeningStartMs != 0L) {
                    flushListening()
                    listeningTransport = networkTransport()
                    listeningBitrateKbps = Library.audio.value?.bitrateKbps
                }
            }
            if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            ) {
                scope.launch { onNetworkBack() }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Library.init(this)
        SoundFx.init(this)

        // The player's audio goes through SoundProcessor (equalizer and 3D modes).
        val renderers = object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean,
            ): AudioSink = DefaultAudioSink.Builder(context)
                .setEnableFloatOutput(enableFloatOutput)
                .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                .setAudioProcessors(arrayOf<AudioProcessor>(SoundProcessor()))
                .build()
        }

        val http = DefaultHttpDataSource.Factory()
            .setUserAgent("MRadio/1.0 (Android)")
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(10_000)
            .setReadTimeoutMs(20_000)
        player = ExoPlayer.Builder(this, renderers)
            .setMediaSourceFactory(DefaultMediaSourceFactory(DefaultDataSource.Factory(this, http)))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()

        player.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                Library.logPlayback("Stream error ${error.errorCodeName} · ${current?.name ?: "unknown"}")
                onStreamFailed()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_BUFFERING) {
                    Library.logPlayback("Buffering · ${current?.name ?: "unknown"}")
                }
                if (playbackState == Player.STATE_READY) {
                    Library.logPlayback("Playing · ${current?.name ?: "unknown"}")
                    val station = current ?: return
                    queue.getOrNull(attempt)?.let { Library.markGood(station.id, it) }
                    wasPlaying = true
                    playedBefore = true
                    waitingForNetwork = false
                    retryRound = 0
                    retryJob?.cancel()
                    updateAudioInfo()
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                updateListening(isPlaying)
                RadioWidgetProvider.refresh(this@PlaybackService, current?.name, isPlaying)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    TileService.requestListeningState(
                        this@PlaybackService,
                        ComponentName(this@PlaybackService, RadioQuickTileService::class.java),
                    )
                }
            }

            // The user paused: stop trying to reconnect.
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (!playWhenReady && reason == Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST) cancelRetry()
            }

            override fun onTracksChanged(tracks: Tracks) = updateAudioInfo()

            // Many stations announce the song in the stream ("Artist - Title").
            override fun onMetadata(metadata: Metadata) {
                for (i in 0 until metadata.length()) {
                    (metadata.get(i) as? IcyInfo)?.let { showSong(it.title) }
                }
            }
        })

        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        session = MediaLibrarySession.Builder(this, StationSwitcher(player), LibraryCallback())
            .setSessionActivity(openApp)
            .setMediaButtonPreferences(notificationButtons())
            .build()


        // The notification's heart shows only while the station isn't in Favourite Radios.
        scope.launch { Library.mine.collect { refreshButtons() } }

        runCatching {
            (getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager)
                .registerDefaultNetworkCallback(networkCallback)
        }
    }

    private fun refreshButtons() {
        if (::session.isInitialized) session.setMediaButtonPreferences(notificationButtons())
    }

    /** What the stream says about itself: codec, bitrate, sample rate (for the player screen). */
    private fun updateAudioInfo() {
        val f = player.audioFormat
        val url = queue.getOrNull(attempt)
        if (f == null && url == null) return
        var icy: IcyHeaders? = null
        f?.metadata?.let { m -> for (i in 0 until m.length()) (m.get(i) as? IcyHeaders)?.let { icy = it } }
        val mime = f?.sampleMimeType ?: f?.codecs
        val codec = when {
            mime == null -> null
            mime.contains("mpeg", true) && !mime.contains("mp4", true) -> "MP3"
            mime.contains("aac", true) || mime.contains("mp4a", true) -> "AAC"
            mime.contains("opus", true) -> "Opus"
            mime.contains("vorbis", true) -> "Ogg Vorbis"
            mime.contains("flac", true) -> "FLAC"
            else -> mime.substringAfter('/').uppercase()
        }
        val bits = listOf(icy?.bitrate ?: -1, f?.averageBitrate ?: -1, f?.bitrate ?: -1, f?.peakBitrate ?: -1)
            .firstOrNull { it > 0 }
        Library.audio.value = AudioInfo(
            codec = codec,
            bitrateKbps = bits?.let { it / 1000 },
            sampleRateHz = f?.sampleRate?.takeIf { it > 0 },
            channels = f?.channelCount?.takeIf { it > 0 },
            host = url?.let { runCatching { Uri.parse(it).host }.getOrNull() },
            stationName = icy?.name,
            genre = icy?.genre,
        )
        val newBitrate = Library.audio.value?.bitrateKbps
        if (listeningStartMs != 0L && newBitrate != listeningBitrateKbps) {
            flushListening()
            listeningBitrateKbps = newBitrate
        }
    }

    /**
     * Puts the playing song in the title (notification, lock screen, Android Auto and the app) with
     * the station as the artist line. The item is updated in place, so playback isn't interrupted.
     */
    private fun showSong(raw: String?) {
        val station = current ?: return
        val song = raw?.trim()?.takeIf { it.length > 1 && it != "-" && !it.equals(station.name, ignoreCase = true) }
        if (song == lastSong) return
        lastSong = song
        val item = player.currentMediaItem ?: return
        if (item.mediaId != station.id) return
        val meta = metadata(station, artwork = true).buildUpon()
            .apply { if (song != null) setTitle(song).setArtist(station.name) }
            .build()
        runCatching { player.replaceMediaItem(player.currentMediaItemIndex, item.buildUpon().setMediaMetadata(meta).build()) }
    }

    private fun hasNetwork(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        // VALIDATED: connected to Wi-Fi/mobile data that actually reaches the internet.
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    /** Sleep timer: stop the radio after [minutes] (0 cancels). */
    private fun setSleepTimer(minutes: Int) {
        sleepJob?.cancel()
        player.volume = 1f
        if (minutes <= 0) {
            Library.sleepAt.value = null
            return
        }
        val at = System.currentTimeMillis() + minutes * 60_000L
        Library.sleepAt.value = at
        sleepJob = scope.launch {
            // Fade out over the last 30 seconds instead of cutting off.
            val fadeMs = minOf(30_000L, minutes * 60_000L)
            delay(minutes * 60_000L - fadeMs)
            val steps = 30
            for (i in steps - 1 downTo 0) {
                player.volume = i / steps.toFloat()
                delay(fadeMs / steps)
            }
            Library.sleepAt.value = null
            player.pause()
            player.volume = 1f
        }
    }

    /**
     * Previous station, next station, the heart (only when the station isn't in Favourite Radios
     * yet) and Exit in the notification (names are read out by TalkBack).
     */
    private fun notificationButtons(): List<CommandButton> = listOfNotNull(
        CommandButton.Builder(CommandButton.ICON_PREVIOUS)
            .setPlayerCommand(Player.COMMAND_SEEK_TO_PREVIOUS)
            .setDisplayName("Previous station")
            .setSlots(CommandButton.SLOT_BACK)
            .build(),
        CommandButton.Builder(CommandButton.ICON_NEXT)
            .setPlayerCommand(Player.COMMAND_SEEK_TO_NEXT)
            .setDisplayName("Next station")
            .setSlots(CommandButton.SLOT_FORWARD)
            .build(),
        current?.takeIf { !Library.inMine(it) }?.let {
            CommandButton.Builder(CommandButton.ICON_HEART_UNFILLED)
                .setCustomIconResId(R.drawable.ic_heart)
                .setSessionCommand(StationCommands.FAVOURITE)
                .setDisplayName("Add to Favourite Radios")
                .setSlots(CommandButton.SLOT_OVERFLOW)
                .build()
        },
        CommandButton.Builder(CommandButton.ICON_UNDEFINED)
            .setCustomIconResId(R.drawable.ic_exit)
            .setSessionCommand(StationCommands.EXIT)
            .setDisplayName("Quit radio")
            .setSlots(CommandButton.SLOT_OVERFLOW)
            .build(),
    )

    /** Exit: stop the radio, remove the notification, close the app screen if it is open. */
    private fun exitApp() {
        updateListening(false)
        current = null
        cancelRetry()
        setSleepTimer(0)
        player.stop()
        player.clearMediaItems()
        RadioWidgetProvider.refresh(this, null, false)
        AppExit.request()
        stopSelf()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == RadioAlarm.ACTION_PLAY_ALARM) {
            val stationId = intent.getStringExtra("stationId")
            val station = stationId?.let { Library.find(it) }
            if (station != null) {
                // Start immediately so Media3 can create its foreground media notification.
                player.setMediaItem(startStation(station))
                player.prepare()
                player.play()
                Library.logPlayback("Radio alarm started · ${station.name}")
            } else {
                stopSelf(startId)
            }
            return START_NOT_STICKY
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!player.playWhenReady || player.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        updateListening(false)
        runCatching {
            (getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager)
                .unregisterNetworkCallback(networkCallback)
        }
        scope.cancel()
        session.release()
        player.release()
        super.onDestroy()
    }

    // ---------------------------------------------------------------- playback + fallback

    /** Sets up the fallback queue for [station] and returns the item for its best stream. */
    private fun startStation(station: Station): MediaItem {
        current = station
        Library.recordPlayed(station)
        Library.logPlayback("Selected · ${station.name}")
        queue = Library.orderedStreams(station)
        attempt = 0
        lookedUpOnline = false
        wasPlaying = false
        playedBefore = false
        cancelRetry()
        RadioWidgetProvider.refresh(this, station.name, false)
        Library.lastPlayed = station.id
        Library.audio.value = null
        lastSong = null
        refreshButtons()
        return playableItem(station, queue.firstOrNull().orEmpty())
    }

    private fun onStreamFailed() {
        val station = current ?: return
        if (player.currentMediaItem?.mediaId != station.id) return

        // The user paused: don't fight them.
        if (!player.playWhenReady) return

        // No internet: every stream would fail, so skip the fallbacks and wait for the connection.
        if (!hasNetwork()) {
            waitForNetwork(station)
            return
        }

        // A stream that was playing dropped (tunnel, network switch): retry it once first.
        if (wasPlaying) {
            wasPlaying = false
            val url = queue.getOrNull(attempt) ?: return
            scheduleRetry(station, nextBackoff()) { play(station, url) }
            return
        }

        attempt++
        if (attempt < queue.size) {
            play(station, queue[attempt])
            return
        }
        if (!lookedUpOnline) {
            lookedUpOnline = true
            scope.launch {
                val backups = Directory.alternates(station).filterNot { it in queue }
                if (current?.id != station.id) return@launch
                if (backups.isEmpty()) {
                    gaveUp(station)
                    return@launch
                }
                queue = queue + backups
                play(station, queue[attempt])
            }
            return
        }
        gaveUp(station)
    }

    /**
     * Every stream failed. Without internet that says nothing about the station, so wait for it.
     * A station that played fine earlier is not marked as broken either: it is retried from the
     * best stream after a delay (the provider may just be restarting).
     */
    private fun gaveUp(station: Station) {
        if (!hasNetwork()) {
            waitForNetwork(station)
            return
        }
        if (playedBefore) {
            scheduleRetry(station, nextBackoff()) { restartCycle(station) }
            return
        }
        Library.markBroken(station.id)
        // The error stays visible in the app and on Auto.
    }

    // ---------------------------------------------------------------- reconnect

    /** Waits for the connection to return; a timer retries anyway in case the network never reports as validated. */
    private fun waitForNetwork(station: Station) {
        waitingForNetwork = true
        // Do not wake the radio periodically while disconnected. The validated-network
        // callback (or a manual Play action) starts a fresh attempt when connectivity returns.
        retryJob?.cancel()
    }

    /** Called when a validated internet connection appears. */
    private fun onNetworkBack() {
        if (!waitingForNetwork) return
        val station = current ?: return
        if (!hasNetwork() || !player.playWhenReady) return
        waitingForNetwork = false
        // Give the connection a moment to settle before reconnecting.
        scheduleRetry(station, 1500) { restartCycle(station) }
    }

    /** Starts again from the best stream (the remembered one first, then the fallbacks). */
    private fun restartCycle(station: Station) {
        val first = queue.firstOrNull() ?: return
        attempt = 0
        wasPlaying = false
        play(station, first)
    }

    private fun scheduleRetry(station: Station, delayMs: Long, action: () -> Unit) {
        retryJob?.cancel()
        retryJob = scope.launch {
            delay(delayMs)
            if (current?.id != station.id || !player.playWhenReady) return@launch
            action()
        }
    }

    private fun cancelRetry() {
        retryJob?.cancel()
        waitingForNetwork = false
        retryRound = 0
    }

    /** Back off repeated stream failures: 5s, 10s, 20s, 40s, 60s, then 120s. */
    private fun nextBackoff(): Long {
        val steps = longArrayOf(5_000, 10_000, 20_000, 40_000, 60_000, 120_000)
        return steps[minOf(retryRound++, steps.size - 1)]
    }

    private fun play(station: Station, url: String) {
        player.setMediaItem(playableItem(station, url))
        player.prepare()
        player.play()
    }

    private fun playableItem(station: Station, url: String): MediaItem =
        MediaItem.Builder()
            .setMediaId(station.id)
            .setUri(url)
            .apply { if (url.contains(".m3u8", ignoreCase = true)) setMimeType(MimeTypes.APPLICATION_M3U8) }
            .setMediaMetadata(metadata(station, artwork = true))
            .build()

    // ---------------------------------------------------------------- next / previous station

    /**
     * Gives the notification, lock screen, Bluetooth and Android Auto "next" and "previous"
     * buttons that switch station, within the section the station is in (Favourite Radios or More from Mauritius).
     */
    private inner class StationSwitcher(player: Player) : ForwardingPlayer(player) {
        override fun getAvailableCommands(): Player.Commands =
            super.getAvailableCommands().buildUpon()
                .add(Player.COMMAND_SEEK_TO_NEXT)
                .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                .add(Player.COMMAND_SEEK_TO_PREVIOUS)
                .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                .build()

        override fun isCommandAvailable(command: Int): Boolean = availableCommands.contains(command)
        override fun hasNextMediaItem(): Boolean = true
        override fun hasPreviousMediaItem(): Boolean = true
        override fun seekToNext() = switchStation(+1)
        override fun seekToNextMediaItem() = switchStation(+1)
        override fun seekToPrevious() = switchStation(-1)
        override fun seekToPreviousMediaItem() = switchStation(-1)
    }

    private fun switchStation(step: Int) {
        val list = Library.playOrder(current?.id)
        if (list.isEmpty()) return
        val index = list.indexOfFirst { it.id == current?.id }
        val next = if (index < 0) list.first() else list[Math.floorMod(index + step, list.size)]
        player.setMediaItem(startStation(next))
        player.prepare()
        player.play()
    }

    // ---------------------------------------------------------------- browse tree

    private fun metadata(station: Station, artwork: Boolean): MediaMetadata =
        MediaMetadata.Builder()
            .setTitle(station.name)
            .setArtist(station.subtitle)
            .setStation(station.name)
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .setMediaType(MediaMetadata.MEDIA_TYPE_RADIO_STATION)
            .apply {
                val logo = Library.logo(station)
                if (logo != null) {
                    setArtworkUri(Uri.parse(logo))
                } else if (artwork) {
                    setArtworkData(Artwork.png(station), MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                }
            }
            .build()

    /** A station row in a list (no stream yet; it is resolved when played). */
    private fun browseItem(station: Station, artwork: Boolean) =
        MediaItem.Builder().setMediaId(station.id).setMediaMetadata(metadata(station, artwork)).build()

    private fun folder(id: String, title: String) = MediaItem.Builder()
        .setMediaId(id)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setIsBrowsable(true)
                .setIsPlayable(false)
                .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_RADIO_STATIONS)
                .build(),
        )
        .build()

    private fun folders() = listOf(
        folder(MINE, "Favourite Radios"),
        folder(DISCOVER, "More from Mauritius"),
    )

    /** Finds a station by id, asking the directories for ones that were never added. */
    private suspend fun resolve(id: String): Station? {
        Library.find(id)?.let { return it }
        val found = when {
            id.startsWith("rb:") -> runCatching { RadioBrowser.byUuid(id.removePrefix("rb:")) }.getOrNull()
            id.startsWith("st:") -> Streema.details(id.removePrefix("st:"))?.let { d ->
                Station(
                    id = id, name = d.name, streams = listOfNotNull(d.stream),
                    tags = d.genres.joinToString(", ").lowercase(), favicon = d.logo,
                    country = d.countryCode, streemaSlug = d.slug, location = d.location,
                )
            }
            else -> null
        }
        return found?.also { Library.remember(listOf(it)) }
    }

    /** Your stations first, then online Mauritius matches, then worldwide if nothing found. */
    private suspend fun searchStations(query: String): List<Station> {
        val local = Library.searchLocal(query)
        suspend fun online(place: String) = withTimeoutOrNull(10_000) {
            runCatching { Directory.search(query, place).last() }.getOrNull()
        }.orEmpty().filterNot { Library.known(it) || Library.isHidden(it) }
        val mu = online(Directory.MAURITIUS)
        val world = if (local.isEmpty() && mu.isEmpty()) online(Directory.WORLDWIDE) else emptyList()
        return (local + mu + world).also { Library.remember(it) }
    }

    private fun <T> future(block: suspend () -> T): ListenableFuture<T> {
        val result = SettableFuture.create<T>()
        scope.launch {
            try {
                result.set(block())
            } catch (e: Throwable) {
                result.setException(e)
            }
        }
        return result
    }

    private fun <T> pageOf(items: List<T>, page: Int, pageSize: Int): ImmutableList<T> {
        if (page < 0 || pageSize <= 0) return ImmutableList.of()
        if (pageSize == Int.MAX_VALUE) return if (page == 0) ImmutableList.copyOf(items) else ImmutableList.of()
        val from = (page.toLong() * pageSize).coerceAtMost(items.size.toLong()).toInt()
        val to = (from.toLong() + pageSize).coerceAtMost(items.size.toLong()).toInt()
        return ImmutableList.copyOf(items.subList(from, to))
    }

    private inner class LibraryCallback : MediaLibrarySession.Callback {

        /** Lets the app's own player bar ask for the next/previous station (see StationCommands). */
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            val base = super.onConnect(session, controller)
            if (!base.isAccepted) return base
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(
                    base.availableSessionCommands.buildUpon()
                        .add(StationCommands.NEXT)
                        .add(StationCommands.PREVIOUS)
                        .add(StationCommands.EXIT)
                        .add(StationCommands.FAVOURITE)
                        .add(StationCommands.SLEEP)
                        .build(),
                )
                .setAvailablePlayerCommands(base.availablePlayerCommands)
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                StationCommands.NEXT.customAction -> switchStation(+1)
                StationCommands.PREVIOUS.customAction -> switchStation(-1)
                StationCommands.EXIT.customAction -> exitApp()
                StationCommands.FAVOURITE.customAction -> current?.let {
                    Library.addToMine(it)
                    refreshButtons()
                }
                StationCommands.SLEEP.customAction -> setSleepTimer(args.getInt(StationCommands.MINUTES, 0))
                else -> return Futures.immediateFuture(SessionResult(SessionError.ERROR_NOT_SUPPORTED))
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val extras = Bundle().apply {
                putBoolean(CONTENT_STYLE_SUPPORTED, true)
                putInt(CONTENT_STYLE_BROWSABLE_HINT, CONTENT_STYLE_LIST)
                putInt(CONTENT_STYLE_PLAYABLE_HINT, CONTENT_STYLE_GRID)
                putBoolean(SEARCH_SUPPORTED, true)
            }
            val rootParams = LibraryParams.Builder().setExtras(extras).build()
            return Futures.immediateFuture(LibraryResult.ofItem(folder(ROOT, "MRadio"), rootParams))
        }

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            fun result(items: List<MediaItem>) = LibraryResult.ofItemList(pageOf(items, page, pageSize), params)
            return when (parentId) {
                ROOT -> Futures.immediateFuture(result(folders()))
                MINE -> Futures.immediateFuture(
                    result(Library.myStations().map { browseItem(it, artwork = true) }),
                )
                DISCOVER -> future {
                    runCatching { RadioBrowser.mauritius() }.onSuccess { Library.setDiscovered(it) }
                    val visible = pageOf(Library.moreStations(), page, pageSize)
                    LibraryResult.ofItemList(visible.map { browseItem(it, artwork = true) }, params)
                }
                else -> Futures.immediateFuture(
                    LibraryResult.ofError<ImmutableList<MediaItem>>(LibraryResult.RESULT_ERROR_BAD_VALUE),
                )
            }
        }

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String,
        ): ListenableFuture<LibraryResult<MediaItem>> {
            if (mediaId == ROOT) return Futures.immediateFuture(LibraryResult.ofItem(folder(ROOT, "MRadio"), null))
            folders().firstOrNull { it.mediaId == mediaId }?.let {
                return Futures.immediateFuture(LibraryResult.ofItem(it, null))
            }
            return future {
                val station = resolve(mediaId)
                if (station == null) {
                    LibraryResult.ofError<MediaItem>(LibraryResult.RESULT_ERROR_BAD_VALUE)
                } else {
                    LibraryResult.ofItem(browseItem(station, artwork = true), null)
                }
            }
        }

        override fun onSearch(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<Void>> {
            scope.launch {
                val found = searchStations(query)
                searchResults[query] = found
                session.notifySearchResultChanged(browser, query, found.size, params)
            }
            return Futures.immediateFuture(LibraryResult.ofVoid())
        }

        override fun onGetSearchResult(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = future {
            val found = searchResults[query] ?: searchStations(query).also { searchResults[query] = it }
            // Supply artwork for visible search results as well as favourites.
            // Pagination happens before artwork generation to avoid work for off-screen results.
            val visible = pageOf(found, page, pageSize)
            LibraryResult.ofItemList(visible.map { browseItem(it, artwork = true) }, params)
        }

        /** Every "play this" request (app, Auto, voice) arrives here with just a station id or a query. */
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> = future {
            val request = mediaItems.firstOrNull()
            val station = when {
                request == null -> null
                request.mediaId.isNotEmpty() -> resolve(request.mediaId)
                else -> {
                    // Voice: "Play Top FM on MRadio". An empty query means "play something".
                    val query = request.requestMetadata.searchQuery.orEmpty()
                    if (query.isBlank()) {
                        Library.lastPlayed?.let { resolve(it) } ?: Library.myStations().firstOrNull()
                    } else {
                        searchStations(query).firstOrNull()
                    }
                }
            }
            // Stations found only on Streema get their link now.
            val ready = station?.let { Directory.withStreams(it) }?.also { if (it !== station) Library.update(it) }
            if (ready == null) mutableListOf() else mutableListOf(startStation(ready))
        }

        /** Android Auto / Bluetooth "play" with nothing queued: resume the last station. */
        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> = future {
            // Android Auto may request resumption after connecting. Respect its
            // separate opt-in preference without affecting phone or Bluetooth controls.
            val isAndroidAuto = controller.packageName == "com.google.android.projection.gearhead"
            if (isAndroidAuto && !Library.autoPlayAuto.value) {
                MediaSession.MediaItemsWithStartPosition(emptyList(), 0, C.TIME_UNSET)
            } else {
                // A browser connection alone never starts audio. Only the media
                // controller's explicit resumption request can restore a station.
                val station = Library.lastPlayed?.let { resolve(it) }
                    ?: Library.myStations().firstOrNull()
                    ?: Library.stations.value.firstOrNull()
                val items = if (station != null) listOf(startStation(station)) else emptyList()
                MediaSession.MediaItemsWithStartPosition(items, 0, C.TIME_UNSET)
            }
        }
    }

    private companion object {
        const val ROOT = "root"
        const val MINE = "mine"
        const val DISCOVER = "discover"

        // Android Auto layout hints
        const val CONTENT_STYLE_SUPPORTED = "android.media.browse.CONTENT_STYLE_SUPPORTED"
        const val CONTENT_STYLE_BROWSABLE_HINT = "android.media.browse.CONTENT_STYLE_BROWSABLE_HINT"
        const val CONTENT_STYLE_PLAYABLE_HINT = "android.media.browse.CONTENT_STYLE_PLAYABLE_HINT"
        const val SEARCH_SUPPORTED = "android.media.browse.SEARCH_SUPPORTED"
        const val CONTENT_STYLE_LIST = 1
        const val CONTENT_STYLE_GRID = 2
    }
}
