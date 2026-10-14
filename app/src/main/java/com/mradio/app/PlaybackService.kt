package com.mradio.app

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Plays the radio and serves the station browser for Android Auto, Bluetooth and the
 * media notification.
 *
 * Fallback: each station has an ordered list of streams. When one fails, the next is tried.
 * When all have failed, backup streams for the same station are looked up on Radio Browser.
 * The stream that plays is remembered and tried first next time.
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

    override fun onCreate() {
        super.onCreate()
        Library.init(this)

        val http = DefaultHttpDataSource.Factory()
            .setUserAgent("MRadio/1.0 (Android)")
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(10_000)
            .setReadTimeoutMs(20_000)
        player = ExoPlayer.Builder(this)
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
                onStreamFailed()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    val station = current ?: return
                    queue.getOrNull(attempt)?.let { Library.markGood(station.id, it) }
                    wasPlaying = true
                }
            }
        })

        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        session = MediaLibrarySession.Builder(this, player, LibraryCallback())
            .setSessionActivity(openApp)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!player.playWhenReady || player.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        session.release()
        player.release()
        super.onDestroy()
    }

    // ---------------------------------------------------------------- playback + fallback

    /** Sets up the fallback queue for [station] and returns the item for its best stream. */
    private fun startStation(station: Station): MediaItem {
        current = station
        queue = Library.orderedStreams(station)
        attempt = 0
        lookedUpOnline = false
        wasPlaying = false
        Library.lastPlayed = station.id
        return playableItem(station, queue.firstOrNull().orEmpty())
    }

    private fun onStreamFailed() {
        val station = current ?: return
        if (player.currentMediaItem?.mediaId != station.id) return

        // A stream that was playing dropped (tunnel, network switch): retry it once first.
        if (wasPlaying) {
            wasPlaying = false
            val url = queue.getOrNull(attempt) ?: return
            scope.launch {
                delay(2000)
                if (current?.id == station.id) play(station, url)
            }
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
                val backups = RadioBrowser.alternates(station).filterNot { it in queue }
                if (current?.id != station.id || backups.isEmpty()) return@launch
                queue = queue + backups
                play(station, queue[attempt])
            }
        }
        // Otherwise every stream has failed; the error stays visible in the app and on Auto.
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
        folder(FAVOURITES, "Favourites"),
        folder(ALL, "All stations"),
        folder(DISCOVER, "More from Mauritius"),
    )

    /** Finds a station by id, asking Radio Browser for ones that were never added. */
    private suspend fun resolve(id: String): Station? {
        Library.find(id)?.let { return it }
        if (!id.startsWith("rb:")) return null
        return runCatching { RadioBrowser.byUuid(id.removePrefix("rb:")) }.getOrNull()
            ?.also { Library.remember(listOf(it)) }
    }

    /** Your stations first, then online Mauritius matches, then worldwide if nothing found. */
    private suspend fun searchStations(query: String): List<Station> {
        val local = Library.searchLocal(query)
        val online = runCatching { RadioBrowser.search(query, mauritiusOnly = true) }
            .getOrDefault(emptyList()).filterNot { Library.contains(it) }
        val world = if (local.isEmpty() && online.isEmpty()) {
            runCatching { RadioBrowser.search(query, mauritiusOnly = false) }.getOrDefault(emptyList())
        } else {
            emptyList()
        }
        return (local + online + world).also { Library.remember(it) }
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

    private fun pageOf(items: List<MediaItem>, page: Int, pageSize: Int): ImmutableList<MediaItem> {
        if (pageSize <= 0 || pageSize == Int.MAX_VALUE) return ImmutableList.copyOf(items)
        val from = (page.toLong() * pageSize).coerceIn(0L, items.size.toLong()).toInt()
        val to = minOf(items.size, from + pageSize)
        return ImmutableList.copyOf(items.subList(from, to))
    }

    private inner class LibraryCallback : MediaLibrarySession.Callback {

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
                FAVOURITES -> {
                    val favs = Library.favourites.value
                    val items = Library.stations.value.filter { it.id in favs }.map { browseItem(it, artwork = true) }
                    Futures.immediateFuture(result(items))
                }
                ALL -> Futures.immediateFuture(
                    result(Library.favouritesFirst().map { browseItem(it, artwork = true) }),
                )
                DISCOVER -> future {
                    val found = runCatching { RadioBrowser.mauritius() }.getOrDefault(emptyList())
                        .filterNot { Library.contains(it) }
                    Library.remember(found)
                    result(found.map { browseItem(it, artwork = false) })
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
            LibraryResult.ofItemList(pageOf(found.map { browseItem(it, artwork = false) }, page, pageSize), params)
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
                        Library.lastPlayed?.let { resolve(it) } ?: Library.favouritesFirst().firstOrNull()
                    } else {
                        searchStations(query).firstOrNull()
                    }
                }
            }
            if (station == null) mutableListOf() else mutableListOf(startStation(station))
        }

        /** Android Auto / Bluetooth "play" with nothing queued: resume the last station. */
        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> = future {
            val station = Library.lastPlayed?.let { resolve(it) } ?: Library.favouritesFirst().first()
            MediaSession.MediaItemsWithStartPosition(listOf(startStation(station)), 0, C.TIME_UNSET)
        }
    }

    private companion object {
        const val ROOT = "root"
        const val FAVOURITES = "favourites"
        const val ALL = "all"
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
