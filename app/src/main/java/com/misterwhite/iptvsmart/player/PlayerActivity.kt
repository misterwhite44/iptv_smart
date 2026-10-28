package com.misterwhite.iptvsmart.player

import android.content.pm.ActivityInfo
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
import androidx.compose.runtime.CompositionLocalProvider
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
import com.misterwhite.iptvsmart.ui.premium.LocalPremium
import com.misterwhite.iptvsmart.ui.premium.PremiumCastButton
import com.misterwhite.iptvsmart.cast.CastRemote
import com.misterwhite.iptvsmart.cast.CastRemoteScreen
import com.misterwhite.iptvsmart.cast.CastSupport
import com.misterwhite.iptvsmart.cast.IptvMediaItemConverter
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

        WindowCompat.setDecorFitsSystemWindows(window, false)
        // Chromecast déjà connectée : on ouvre directement en portrait.
        applyCastLayout(casting = CastRemote.snapshot(this) != null)

        CastSupport.castContext(this)?.let { context ->
            castAvailable = true
            castPlayer = CastPlayer(context, IptvMediaItemConverter()).apply {
                setSessionAvailabilityListener(castSessionListener)
                addListener(castListener)
            }
        }

        setContent {
            CompositionLocalProvider(LocalPremium provides container.premium) {
                IptvTheme { PlayerScreen() }
            }
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
        // La dernière position part tout de suite sur le compte, pour reprendre ailleurs.
        container.accountSync.flush()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        activePlayer?.let {
            outState.putInt(STATE_INDEX, currentItemIndex())
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
        // Pendant un cast, le téléphone ne se connecte pas au flux.
        if (!isCasting.value) exo.prepare()

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
        } else {
            currentIndex = castIndex
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
            .setMediaId(if (item.kind == MediaKind.LIVE) IptvMediaItemConverter.LIVE_PREFIX + index else "item:$index")
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

    /** Élément de la file lu par la Chromecast (elle ne reçoit qu'un élément à la fois). */
    private var castIndex = 0
    private var castRetries = 0
    private val castRetry = Runnable { castItem(castIndex, castPlayer?.currentPosition ?: 0L) }

    /** Si la télé n'a toujours rien lancé après un moment, on le dit au lieu d'attendre en silence. */
    private val castWatchdog = Runnable {
        if (isCasting.value && castPlayer?.playbackState != Player.STATE_READY) {
            error.value = "La Chromecast n'arrive pas à démarrer ce flux. Essaie une autre chaîne ou un film : " +
                "certains fournisseurs bloquent la lecture directe sur la télé."
        }
    }

    private val castListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            if (!isCasting.value) return
            when (playbackState) {
                Player.STATE_READY -> {
                    castRetries = 0
                    handler.removeCallbacks(castWatchdog)
                    error.value = null
                }
                // Fin d'un épisode : on envoie le suivant.
                Player.STATE_ENDED -> {
                    val item = items.getOrNull(castIndex)
                    if (item != null && item.kind != MediaKind.LIVE && castIndex + 1 < items.size) {
                        library.entry(item.key)?.let {
                            library.record(it.copy(positionMs = it.durationMs, updatedAt = System.currentTimeMillis()))
                        }
                        castItem(castIndex + 1, 0L)
                    }
                }
            }
        }

        override fun onPlayerError(e: PlaybackException) {
            if (!isCasting.value) return
            // Souvent une erreur 429 : le serveur n'a pas encore libéré la
            // connexion du téléphone. On réessaie un peu plus tard.
            if (castRetries < MAX_CAST_RETRIES) {
                castRetries++
                error.value = null
                handler.removeCallbacks(castRetry)
                handler.postDelayed(castRetry, CAST_RETRY_DELAY_MS * castRetries)
            } else {
                error.value = "La Chromecast n'arrive pas à lire ce flux. Le serveur limite peut-être le nombre " +
                    "de connexions : vérifie qu'aucun autre appareil ne regarde avec ton compte."
            }
        }
    }

    /**
     * Pendant un cast, le téléphone devient une télécommande en portrait, avec
     * ses barres système et sans garder l'écran allumé. Sinon, lecteur plein
     * écran en paysage.
     */
    private fun applyCastLayout(casting: Boolean) {
        requestedOrientation = if (casting) {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
        WindowInsetsControllerCompat(window, window.decorView).apply {
            if (casting) {
                show(WindowInsetsCompat.Type.systemBars())
            } else {
                hide(WindowInsetsCompat.Type.systemBars())
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
        if (casting) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        playerView?.keepScreenOn = !casting
    }

    /** Index réel dans la file de l'élément en cours, sur le téléphone ou la Chromecast. */
    private fun currentItemIndex(): Int =
        if (isCasting.value) castIndex else player?.currentMediaItemIndex ?: currentIndex

    /** Envoie la lecture en cours sur la Chromecast, à la même position. */
    private fun startCasting() {
        val local = player
        val index = local?.currentMediaItemIndex ?: currentIndex
        val item = items.getOrNull(index) ?: return
        val position = if (item.kind == MediaKind.LIVE) 0L else (local?.currentPosition ?: resumePositionMs.coerceAtLeast(0))
        saveProgress()
        // stop() ferme la connexion du téléphone au serveur : la plupart des
        // abonnements n'autorisent qu'une connexion à la fois.
        local?.stop()
        isCasting.value = true
        applyCastLayout(casting = true)
        error.value = null
        castRetries = 0
        playerView?.player = castPlayer
        castIndex = index
        trackedIndex = index
        onItemStarted(index)
        handler.removeCallbacks(castRetry)
        // Retour sur la télécommande depuis le mini-lecteur : la télé lit déjà
        // ce contenu, on ne le relance pas.
        if (CastRemote.isPlayingUrl(this, item.url)) return
        handler.postDelayed({ castItem(index, position) }, CAST_HANDOFF_DELAY_MS)
    }

    private fun castItem(index: Int, positionMs: Long) {
        val cast = castPlayer ?: return
        if (!isCasting.value || index !in items.indices) return
        castIndex = index
        trackedIndex = index
        onItemStarted(index)
        val item = items[index]
        cast.setMediaItem(mediaItem(index, forCast = true), if (item.kind == MediaKind.LIVE) 0L else positionMs)
        cast.playWhenReady = true
        cast.prepare()
        handler.removeCallbacks(castWatchdog)
        handler.postDelayed(castWatchdog, CAST_WATCHDOG_MS)
    }

    /** Reprend sur le téléphone là où la Chromecast s'est arrêtée. */
    private fun stopCasting() {
        if (!isCasting.value) return
        handler.removeCallbacks(castRetry)
        handler.removeCallbacks(castWatchdog)
        val index = castIndex
        val position = castPlayer?.currentPosition ?: 0L
        saveProgress()
        isCasting.value = false
        applyCastLayout(casting = false)
        error.value = null
        val local = player ?: return
        playerView?.player = local
        val item = items.getOrNull(index)
        if (item?.kind == MediaKind.LIVE) local.seekToDefaultPosition(index) else local.seekTo(index, position)
        // Laisse au serveur le temps de libérer la connexion de la Chromecast.
        handler.postDelayed({
            if (!isCasting.value && player === local) {
                local.prepare()
                local.play()
            }
        }, CAST_HANDOFF_DELAY_MS)
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
        val item = items.getOrNull(currentItemIndex()) ?: return
        if (item.kind == MediaKind.LIVE) return
        val duration = exo.duration.takeIf { it != C.TIME_UNSET && it > 0 }
        // Certains films n'annoncent pas leur durée : on garde quand même la
        // position, mais seulement une fois la lecture vraiment lancée.
        if (duration == null && (exo.playbackState != Player.STATE_READY || exo.currentPosition <= 0)) return
        record(item, exo.currentPosition, duration ?: 0)
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

    private fun currentItem(): PlayItem? = items.getOrNull(currentItemIndex())

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
        val showOverlay = (controlsVisible.value || error.value != null) && !isCasting.value
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

            if (isCasting.value) {
                val item = currentItem()
                CastRemoteScreen(
                    title = title.value,
                    subtitle = subtitle.value,
                    image = item?.image,
                    isLive = item?.kind == MediaKind.LIVE,
                    tallArtwork = item?.kind == MediaKind.MOVIE,
                    hasPrevious = castIndex > 0,
                    hasNext = castIndex + 1 < items.size,
                    onPrevious = { saveProgress(); castItem(castIndex - 1, 0L) },
                    onNext = { saveProgress(); castItem(castIndex + 1, 0L) },
                    // La Chromecast continue : le mini-lecteur de l'accueil prend le relais.
                    onClose = { finish() },
                    castButton = { PlayerExtraActions() },
                )
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
                        if (isCasting.value) {
                            castRetries = 0
                            castItem(castIndex, castPlayer?.currentPosition ?: 0L)
                        } else {
                            player?.prepare()
                            player?.play()
                        }
                    }) { Text("Réessayer") }
                }
            }
        }
    }

    /** Emplacement pour des boutons en plus dans la barre du haut (Chromecast). */
    @Composable
    private fun PlayerExtraActions() {
        if (castAvailable) PremiumCastButton(Modifier.padding(horizontal = 4.dp))
    }

    companion object {
        private const val STATE_INDEX = "index"
        private const val STATE_POSITION = "position"
        private const val SAVE_INTERVAL_MS = 5_000L
        private const val CAST_HANDOFF_DELAY_MS = 1_500L
        private const val CAST_RETRY_DELAY_MS = 3_000L
        private const val MAX_CAST_RETRIES = 3
        private const val CAST_WATCHDOG_MS = 25_000L
    }
}
