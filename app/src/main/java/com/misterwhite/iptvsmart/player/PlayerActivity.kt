package com.misterwhite.iptvsmart.player

import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.cast.CastPlayer
import androidx.media3.cast.SessionAvailabilityListener
import androidx.compose.material.icons.filled.Cast
import com.misterwhite.iptvsmart.cast.CastButton
import com.misterwhite.iptvsmart.cast.CastSupport
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.misterwhite.iptvsmart.IptvApp
import com.misterwhite.iptvsmart.data.FavoriteEntry
import com.misterwhite.iptvsmart.data.MediaKind
import com.misterwhite.iptvsmart.data.WatchEntry
import com.misterwhite.iptvsmart.ui.theme.IptvTheme

/** Lecteur plein écran pour le direct, les films et les épisodes. */
@OptIn(UnstableApi::class)
class PlayerActivity : FragmentActivity() {

    private val container get() = (application as IptvApp).container
    private val library get() = container.library

    private var player: ExoPlayer? = null
    private var castPlayer: CastPlayer? = null
    private val isCasting = mutableStateOf(false)
    private var castAvailable = false

    /** Le lecteur qui joue vraiment : la Chromecast pendant un cast, sinon le téléphone. */
    private val activePlayer: Player? get() = if (isCasting.value) castPlayer else player
    private var playerView: PlayerView? = null
    private var items: List<PlayItem> = emptyList()

    private var currentIndex = 0
    private var resumePositionMs = C.TIME_UNSET
    /** Index des chaînes passées en secours sur le flux .ts. */
    private val usedFallback = mutableSetOf<Int>()
    private var trackedIndex = -1

    private val title = mutableStateOf("")
    private val subtitle = mutableStateOf<String?>(null)
    private val error = mutableStateOf<String?>(null)
    private val controlsVisible = mutableStateOf(true)
    private val resizeMode = mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT)

    private val handler = Handler(Looper.getMainLooper())
    private val saveTick = object : Runnable {
        override fun run() {
            saveProgress()
            handler.postDelayed(this, SAVE_INTERVAL_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        items = PlayQueue.items
        if (items.isEmpty()) {
            finish()
            return
        }
        currentIndex = savedInstanceState?.getInt(STATE_INDEX) ?: PlayQueue.startIndex
        resumePositionMs = savedInstanceState?.getLong(STATE_POSITION, C.TIME_UNSET) ?: C.TIME_UNSET

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        CastSupport.castContext(this)?.let { context ->
            castAvailable = true
            castPlayer = CastPlayer(context).apply {
                setSessionAvailabilityListener(castSessionListener)
                addListener(castListener)
            }
        }

        setContent {
            IptvTheme { PlayerScreen() }
        }
    }

    override fun onDestroy() {
        castPlayer?.let {
            it.setSessionAvailabilityListener(null)
            it.removeListener(castListener)
            it.release()
        }
        castPlayer = null
        super.onDestroy()
    }

    override fun onStart() {
        super.onStart()
        if (items.isNotEmpty()) initializePlayer()
        if (castPlayer?.isCastSessionAvailable == true && !isCasting.value) startCasting()
    }

    override fun onStop() {
        super.onStop()
        releasePlayer()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        activePlayer?.let {
            outState.putInt(STATE_INDEX, it.currentMediaItemIndex)
            outState.putLong(STATE_POSITION, it.currentPosition)
        } ?: run {
            outState.putInt(STATE_INDEX, currentIndex)
            outState.putLong(STATE_POSITION, resumePositionMs)
        }
    }

    private fun initializePlayer() {
        val exo = ExoPlayer.Builder(this)
            .setRenderersFactory(DefaultRenderersFactory(this).setEnableDecoderFallback(true))
            .setMediaSourceFactory(DefaultMediaSourceFactory(OkHttpDataSource.Factory(container.httpClient)))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .build()

        val start = items[currentIndex]
        val startPosition = when {
            resumePositionMs != C.TIME_UNSET && start.kind != MediaKind.LIVE -> resumePositionMs
            start.kind != MediaKind.LIVE && start.startPositionMs > 0 -> start.startPositionMs
            else -> C.TIME_UNSET
        }
        exo.setMediaItems(items.indices.map { mediaItem(it) }, currentIndex, startPosition)
        exo.addListener(listener)
        exo.playWhenReady = !isCasting.value
        exo.prepare()

        player = exo
        if (!isCasting.value) playerView?.player = exo
        trackedIndex = currentIndex
        onItemStarted(currentIndex)
        handler.postDelayed(saveTick, SAVE_INTERVAL_MS)
    }

    private fun releasePlayer() {
        handler.removeCallbacks(saveTick)
        val exo = player ?: return
        saveProgress()
        if (!isCasting.value) {
            currentIndex = exo.currentMediaItemIndex
            resumePositionMs = exo.currentPosition
        }
        exo.removeListener(listener)
        exo.release()
        player = null
        if (!isCasting.value) playerView?.player = null
    }

    private fun mediaItem(index: Int, forCast: Boolean = false): MediaItem {
        val item = items[index]
        // La Chromecast ne lit pas les flux .ts : elle reçoit toujours le flux HLS.
        val url = if (!forCast && index in usedFallback && item.fallbackUrl != null) item.fallbackUrl else item.url
        return MediaItem.Builder()
            .setUri(url)
            .setMediaId(index.toString())
            .setMimeType(mimeTypeOf(url))
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(item.title)
                    .setArtist(item.subtitle)
                    .setArtworkUri(item.image?.let(Uri::parse))
                    .build()
            )
            .build()
    }

    private fun mimeTypeOf(url: String): String = when (url.substringAfterLast('.').substringBefore('?').lowercase()) {
        "m3u8" -> MimeTypes.APPLICATION_M3U8
        "ts" -> MimeTypes.VIDEO_MP2T
        "mkv" -> MimeTypes.VIDEO_MATROSKA
        "webm" -> MimeTypes.VIDEO_WEBM
        "avi" -> "video/x-msvideo"
        else -> MimeTypes.VIDEO_MP4
    }

    private val castSessionListener = object : SessionAvailabilityListener {
        override fun onCastSessionAvailable() = startCasting()
        override fun onCastSessionUnavailable() = stopCasting()
    }

    private val castListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (!isCasting.value) return
            val index = castPlayer?.currentMediaItemIndex ?: return
            trackedIndex = index
            onItemStarted(index)
        }

        override fun onPlayerError(e: PlaybackException) {
            if (isCasting.value) {
                error.value = "La Chromecast n'arrive pas à lire ce flux. Certains formats ne passent qu'en lecture sur le téléphone."
            }
        }
    }

    /** Envoie la lecture en cours sur la Chromecast, à la même position. */
    private fun startCasting() {
        val cast = castPlayer ?: return
        val local = player
        val index = local?.currentMediaItemIndex ?: currentIndex
        val item = items.getOrNull(index) ?: return
        val position = if (item.kind == MediaKind.LIVE) 0L else (local?.currentPosition ?: resumePositionMs.coerceAtLeast(0))
        saveProgress()
        local?.pause()
        isCasting.value = true
        error.value = null
        cast.setMediaItems(items.indices.map { mediaItem(it, forCast = true) }, index, position)
        cast.playWhenReady = true
        cast.prepare()
        playerView?.player = cast
        trackedIndex = index
        onItemStarted(index)
    }

    /** Reprend sur le téléphone là où la Chromecast s'est arrêtée. */
    private fun stopCasting() {
        if (!isCasting.value) return
        val cast = castPlayer
        val index = cast?.currentMediaItemIndex ?: currentIndex
        val position = cast?.currentPosition ?: 0L
        saveProgress()
        isCasting.value = false
        val local = player ?: return
        playerView?.player = local
        val item = items.getOrNull(index)
        if (item?.kind == MediaKind.LIVE) local.seekToDefaultPosition(index) else local.seekTo(index, position)
        local.play()
    }

    private val listener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val previous = items.getOrNull(trackedIndex)
            if (previous != null && reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                // L'épisode précédent est allé jusqu'au bout.
                library.entry(previous.key)?.let {
                    library.record(it.copy(positionMs = it.durationMs, updatedAt = System.currentTimeMillis()))
                }
            }
            if (isCasting.value) return
            val index = player?.currentMediaItemIndex ?: return
            trackedIndex = index
            error.value = null
            onItemStarted(index)
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY) error.value = null
        }

        override fun onPlayerError(e: PlaybackException) {
            if (isCasting.value) return
            val exo = player ?: return
            val index = exo.currentMediaItemIndex
            val item = items.getOrNull(index) ?: return
            if (item.kind == MediaKind.LIVE && e.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                exo.seekToDefaultPosition()
                exo.prepare()
                return
            }
            if (item.fallbackUrl != null && index !in usedFallback) {
                usedFallback += index
                exo.replaceMediaItem(index, mediaItem(index))
                exo.prepare()
                exo.play()
                return
            }
            error.value = friendlyError(e)
        }
    }

    private fun onItemStarted(index: Int) {
        val item = items.getOrNull(index) ?: return
        title.value = item.title
        subtitle.value = item.subtitle
        // Les chaînes vont dans l'historique dès qu'on les ouvre.
        if (item.kind == MediaKind.LIVE) record(item, 0, 0)
    }

    private fun saveProgress() {
        val exo = activePlayer ?: return
        val item = items.getOrNull(exo.currentMediaItemIndex) ?: return
        if (item.kind == MediaKind.LIVE) return
        val duration = exo.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: return
        record(item, exo.currentPosition, duration)
    }

    private fun record(item: PlayItem, positionMs: Long, durationMs: Long) {
        library.record(
            WatchEntry(
                key = item.key,
                kind = item.kind,
                contentId = item.contentId,
                seriesId = item.seriesId,
                title = item.title,
                subtitle = item.subtitle,
                image = item.image,
                extension = item.extension,
                positionMs = positionMs,
                durationMs = durationMs,
                updatedAt = System.currentTimeMillis(),
            )
        )
    }

    private fun currentItem(): PlayItem? = items.getOrNull(activePlayer?.currentMediaItemIndex ?: currentIndex)

    private fun toggleFavorite() {
        val item = currentItem() ?: return
        // Pour un épisode, c'est la série qui va en favori.
        val entry = when (item.kind) {
            MediaKind.EPISODE -> item.seriesId?.let {
                FavoriteEntry(
                    key = com.misterwhite.iptvsmart.data.LibraryRepository.seriesKey(it),
                    kind = MediaKind.SERIES,
                    contentId = it,
                    title = item.title,
                    image = item.image,
                    addedAt = System.currentTimeMillis(),
                )
            }
            else -> FavoriteEntry(item.key, item.kind, item.contentId, item.title, item.image, System.currentTimeMillis())
        } ?: return
        val added = library.toggleFavorite(entry)
        Toast.makeText(this, if (added) "Ajouté aux favoris" else "Retiré des favoris", Toast.LENGTH_SHORT).show()
    }

    private fun favoriteKey(item: PlayItem?): String? = when (item?.kind) {
        null -> null
        MediaKind.EPISODE -> item.seriesId?.let { com.misterwhite.iptvsmart.data.LibraryRepository.seriesKey(it) }
        else -> item.key
    }

    private fun friendlyError(e: PlaybackException): String = when (e.errorCode) {
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ->
            "Impossible de se connecter au flux. Vérifie ta connexion."
        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ->
            "Le serveur refuse ce flux. Le nombre maximal de connexions est peut-être atteint, ou le contenu n'est plus disponible."
        PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND ->
            "Ce contenu est introuvable sur le serveur."
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
        PlaybackException.ERROR_CODE_DECODER_INIT_FAILED ->
            "Ce format vidéo n'est pas pris en charge par ton téléphone."
        else -> "Lecture impossible (${e.errorCodeName})."
    }

    @Composable
    private fun PlayerScreen() {
        val favorites by library.favorites.collectAsState()
        val showOverlay = controlsVisible.value || error.value != null
        val favKey = favoriteKey(currentItem().takeIf { title.value.isNotEmpty() })
        val isFavorite = favKey != null && favorites.any { it.key == favKey }

        Box(Modifier.fillMaxSize().background(Color.Black)) {
            AndroidView(
                factory = { context ->
                    PlayerView(context).apply {
                        keepScreenOn = true
                        setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
                        setShowNextButton(true)
                        setShowPreviousButton(true)
                        setControllerVisibilityListener(
                            PlayerView.ControllerVisibilityListener { visibility ->
                                controlsVisible.value = visibility == View.VISIBLE
                            }
                        )
                        player = this@PlayerActivity.player
                        playerView = this
                    }
                },
                update = { it.resizeMode = resizeMode.intValue },
                modifier = Modifier.fillMaxSize(),
            )

            AnimatedVisibility(
                visible = showOverlay,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopStart),
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent)))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { finish() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour", tint = Color.White)
                    }
                    Spacer(Modifier.width(4.dp))
                    Column(Modifier.weight(1f)) {
                        Text(title.value, style = MaterialTheme.typography.titleMedium, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        subtitle.value?.let {
                            Text(it, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    PlayerExtraActions()
                    if (favKey != null) {
                        IconButton(onClick = { toggleFavorite() }) {
                            Icon(
                                if (isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                                contentDescription = "Favori",
                                tint = if (isFavorite) Color(0xFFFFC107) else Color.White,
                            )
                        }
                    }
                    IconButton(onClick = {
                        resizeMode.intValue = when (resizeMode.intValue) {
                            AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                            AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                            else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                        }
                        val label = when (resizeMode.intValue) {
                            AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> "Zoom"
                            AspectRatioFrameLayout.RESIZE_MODE_FILL -> "Étiré"
                            else -> "Adapté"
                        }
                        Toast.makeText(this@PlayerActivity, "Format : $label", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Filled.AspectRatio, contentDescription = "Format de l'image", tint = Color.White)
                    }
                }
            }

            if (isCasting.value && error.value == null) {
                // Au-dessus des commandes centrales pour ne pas les cacher.
                Column(
                    Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 72.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(Icons.Filled.Cast, contentDescription = null, tint = Color.White, modifier = Modifier.width(56.dp).height(56.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("Lecture sur ta Chromecast", color = Color.White, style = MaterialTheme.typography.titleMedium)
                }
            }

            error.value?.let { message ->
                Column(
                    Modifier
                        .align(Alignment.Center)
                        .background(Color.Black.copy(alpha = 0.8f))
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(message, color = Color.White, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = {
                        error.value = null
                        activePlayer?.prepare()
                        activePlayer?.play()
                    }) { Text("Réessayer") }
                }
            }
        }
    }

    /** Emplacement pour des boutons en plus dans la barre du haut (Chromecast). */
    @Composable
    private fun PlayerExtraActions() {
        if (castAvailable) CastButton(Modifier.padding(horizontal = 4.dp))
    }

    companion object {
        private const val STATE_INDEX = "index"
        private const val STATE_POSITION = "position"
        private const val SAVE_INTERVAL_MS = 5_000L
    }
}
