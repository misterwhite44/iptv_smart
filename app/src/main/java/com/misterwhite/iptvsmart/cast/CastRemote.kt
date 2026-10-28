package com.misterwhite.iptvsmart.cast

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.MediaSeekOptions
import com.google.android.gms.cast.framework.CastSession
import com.misterwhite.iptvsmart.player.PlayQueue
import com.misterwhite.iptvsmart.player.PlayerActivity
import com.misterwhite.iptvsmart.ui.theme.IptvColors
import kotlinx.coroutines.delay

/** Ce que la Chromecast est en train de faire, relu régulièrement pour la télécommande. */
data class CastSnapshot(
    val deviceName: String?,
    val title: String?,
    val subtitle: String?,
    val image: String?,
    val contentUrl: String?,
    val isLive: Boolean,
    val isPlaying: Boolean,
    val isBuffering: Boolean,
    val positionMs: Long,
    val durationMs: Long,
    val volume: Float,
    val hasMedia: Boolean,
)

/** Commandes envoyées directement à la Chromecast connectée. */
object CastRemote {
    private fun session(context: Context): CastSession? =
        CastSupport.castContext(context)?.sessionManager?.currentCastSession?.takeIf { it.isConnected }

    fun snapshot(context: Context): CastSnapshot? {
        val session = session(context) ?: return null
        val client = session.remoteMediaClient
        val info = client?.mediaInfo
        val meta = info?.metadata
        fun text(key: String) = runCatching { meta?.getString(key) }.getOrNull()?.takeIf { it.isNotBlank() }
        val duration = client?.streamDuration ?: 0L
        return CastSnapshot(
            deviceName = session.castDevice?.friendlyName,
            title = text(MediaMetadata.KEY_TITLE),
            subtitle = text(MediaMetadata.KEY_ARTIST) ?: text(MediaMetadata.KEY_SUBTITLE),
            image = meta?.images?.firstOrNull()?.url?.toString(),
            contentUrl = info?.contentUrl ?: info?.contentId,
            isLive = info?.streamType == MediaInfo.STREAM_TYPE_LIVE,
            isPlaying = client?.isPlaying == true,
            isBuffering = client?.isBuffering == true || client?.isLoadingNextItem == true,
            positionMs = client?.approximateStreamPosition?.coerceAtLeast(0L) ?: 0L,
            durationMs = if (duration > 0) duration else 0L,
            volume = runCatching { session.volume.toFloat() }.getOrDefault(0f),
            hasMedia = client != null && info != null && (client.isPlaying || client.isPaused || client.isBuffering),
        )
    }

    fun togglePlayback(context: Context) {
        session(context)?.remoteMediaClient?.togglePlayback()
    }

    fun seekTo(context: Context, positionMs: Long) {
        session(context)?.remoteMediaClient?.seek(
            MediaSeekOptions.Builder().setPosition(positionMs.coerceAtLeast(0L)).build()
        )
    }

    fun seekBy(context: Context, deltaMs: Long) {
        val client = session(context)?.remoteMediaClient ?: return
        seekTo(context, client.approximateStreamPosition + deltaMs)
    }

    fun setVolume(context: Context, volume: Float) {
        runCatching { session(context)?.volume = volume.coerceIn(0f, 1f).toDouble() }
    }

    /** Coupe la diffusion : la lecture revient sur le téléphone si le lecteur est ouvert. */
    fun stop(context: Context) {
        CastSupport.castContext(context)?.sessionManager?.endCurrentSession(true)
    }

    /** Vrai si la Chromecast lit déjà ce flux : inutile de le relancer. */
    fun isPlayingUrl(context: Context, url: String): Boolean {
        val client = session(context)?.remoteMediaClient ?: return false
        val info = client.mediaInfo ?: return false
        val active = client.isPlaying || client.isPaused || client.isBuffering
        return active && (info.contentUrl == url || info.contentId == url)
    }
}

/** État de la Chromecast, rafraîchi deux fois par seconde (null sans Chromecast connectée). */
@Composable
fun rememberCastSnapshot(): State<CastSnapshot?> {
    val context = LocalContext.current
    return produceState<CastSnapshot?>(initialValue = CastRemote.snapshot(context)) {
        if (CastSupport.castContext(context) == null) return@produceState
        while (true) {
            value = CastRemote.snapshot(context)
            delay(500)
        }
    }
}

/**
 * Télécommande plein écran pendant un cast, en portrait, façon Disney+ :
 * grande image, titre, barre de lecture, gros bouton lecture/pause, volume
 * et bouton pour arrêter la diffusion.
 */
@Composable
fun CastRemoteScreen(
    title: String,
    subtitle: String?,
    image: String?,
    isLive: Boolean,
    /** Affiche du film en hauteur (2:3) ; sinon image en 16:9. */
    tallArtwork: Boolean,
    hasPrevious: Boolean,
    hasNext: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit,
    castButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val snapshot by rememberCastSnapshot()
    val s = snapshot

    Box(modifier.fillMaxSize().background(IptvColors.Background)) {
        // Fond : l'image floutée, assombrie, comme sur Disney+.
        if (image != null) {
            AsyncImage(
                model = image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Modifier.blur(40.dp) else Modifier),
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            IptvColors.BackgroundTop.copy(alpha = 0.85f),
                            IptvColors.Background.copy(alpha = 0.92f),
                            IptvColors.Background,
                        )
                    )
                )
        )

        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Barre du haut : réduire, appareil, bouton Cast.
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Réduire", tint = Color.White, modifier = Modifier.size(32.dp))
                }
                Spacer(Modifier.weight(1f))
                castButton()
            }

            Spacer(Modifier.height(16.dp))

            // Grande vignette.
            Box(
                Modifier
                    .widthIn(max = if (tallArtwork) 220.dp else 420.dp)
                    .fillMaxWidth()
                    .aspectRatio(if (tallArtwork) 2f / 3f else 16f / 9f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(IptvColors.Surface),
                contentAlignment = Alignment.Center,
            ) {
                if (image != null) {
                    AsyncImage(
                        model = image,
                        contentDescription = null,
                        contentScale = if (isLive) ContentScale.Fit else ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().padding(if (isLive) 24.dp else 0.dp),
                    )
                } else {
                    Icon(Icons.Filled.CastConnected, contentDescription = null, tint = IptvColors.TextSecondary, modifier = Modifier.size(72.dp))
                }
                if (s?.isBuffering == true) {
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color.White)
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            if (isLive) {
                Text(
                    "EN DIRECT",
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFFE53935))
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                )
                Spacer(Modifier.height(10.dp))
            }
            Text(
                title,
                color = Color.White,
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            subtitle?.let {
                Spacer(Modifier.height(4.dp))
                Text(it, color = IptvColors.TextSecondary, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.CastConnected, contentDescription = null, tint = IptvColors.AccentBright, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    "Lecture sur ${s?.deviceName ?: "ta Chromecast"}",
                    color = IptvColors.AccentBright,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.height(24.dp))

            // Barre de lecture (films et épisodes).
            if (!isLive) {
                SeekBar(
                    positionMs = s?.positionMs ?: 0L,
                    durationMs = s?.durationMs ?: 0L,
                    onSeek = { CastRemote.seekTo(context, it) },
                )
                Spacer(Modifier.height(12.dp))
            }

            // Commandes principales.
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RemoteButton(
                    Icons.Filled.SkipPrevious,
                    if (isLive) "Chaîne précédente" else "Précédent",
                    enabled = hasPrevious,
                    onClick = onPrevious,
                )
                if (!isLive) RemoteButton(Icons.Filled.Replay10, "Reculer de 10 secondes") { CastRemote.seekBy(context, -10_000) }
                Box(
                    Modifier
                        .size(76.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .clickable { CastRemote.togglePlayback(context) },
                    contentAlignment = Alignment.Center,
                ) {
                    val playing = s?.isPlaying == true || s?.isBuffering == true
                    Icon(
                        if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (playing) "Pause" else "Lecture",
                        tint = IptvColors.Background,
                        modifier = Modifier.size(44.dp),
                    )
                }
                if (!isLive) RemoteButton(Icons.Filled.Forward10, "Avancer de 10 secondes") { CastRemote.seekBy(context, 10_000) }
                RemoteButton(
                    Icons.Filled.SkipNext,
                    if (isLive) "Chaîne suivante" else "Suivant",
                    enabled = hasNext,
                    onClick = onNext,
                )
            }

            Spacer(Modifier.height(28.dp))

            // Volume de la télé.
            VolumeBar(volume = s?.volume ?: 0f, onVolume = { CastRemote.setVolume(context, it) })

            Spacer(Modifier.height(28.dp))

            OutlinedButton(onClick = { CastRemote.stop(context) }) {
                Icon(Icons.Filled.Close, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Arrêter la diffusion", color = Color.White)
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun RemoteButton(icon: ImageVector, label: String, enabled: Boolean = true, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(56.dp)) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (enabled) Color.White else Color.White.copy(alpha = 0.3f),
            modifier = Modifier.size(36.dp),
        )
    }
}

@Composable
private fun SeekBar(positionMs: Long, durationMs: Long, onSeek: (Long) -> Unit) {
    // Pendant qu'on fait glisser, on suit le doigt et non la Chromecast.
    var dragging by remember { mutableStateOf<Float?>(null) }
    val fraction = dragging ?: if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val shown = if (dragging != null) (fraction * durationMs).toLong() else positionMs
    Column(Modifier.fillMaxWidth()) {
        Slider(
            value = fraction,
            onValueChange = { dragging = it },
            onValueChangeFinished = {
                dragging?.let { onSeek((it * durationMs).toLong()) }
                dragging = null
            },
            enabled = durationMs > 0,
            colors = sliderColors(),
        )
        Row(Modifier.fillMaxWidth()) {
            Text(formatTime(shown), color = IptvColors.TextSecondary, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.weight(1f))
            Text(
                if (durationMs > 0) "-" + formatTime((durationMs - shown).coerceAtLeast(0L)) else "--:--",
                color = IptvColors.TextSecondary,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun VolumeBar(volume: Float, onVolume: (Float) -> Unit) {
    var dragging by remember { mutableStateOf<Float?>(null) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onVolume(volume - 0.05f) }) {
            Icon(Icons.AutoMirrored.Filled.VolumeDown, contentDescription = "Baisser le volume", tint = Color.White)
        }
        Slider(
            value = dragging ?: volume,
            onValueChange = {
                dragging = it
                onVolume(it)
            },
            onValueChangeFinished = { dragging = null },
            colors = sliderColors(),
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = { onVolume(volume + 0.05f) }) {
            Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Monter le volume", tint = Color.White)
        }
    }
}

@Composable
private fun sliderColors() = SliderDefaults.colors(
    thumbColor = Color.White,
    activeTrackColor = IptvColors.Accent,
    inactiveTrackColor = Color.White.copy(alpha = 0.25f),
)

private fun formatTime(ms: Long): String {
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val sec = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}

/**
 * Mini-lecteur affiché en bas de l'appli quand la Chromecast lit quelque
 * chose : image, titre, lecture/pause. Un appui rouvre la télécommande.
 */
@Composable
fun MiniCastBar(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val snapshot by rememberCastSnapshot()
    val s = snapshot
    AnimatedVisibility(
        visible = s != null && s.hasMedia,
        enter = expandVertically(),
        exit = shrinkVertically(),
        modifier = modifier,
    ) {
        val current = s ?: return@AnimatedVisibility
        Column(
            Modifier
                .fillMaxWidth()
                .background(IptvColors.SurfaceHigh)
                .clickable { openRemote(context, current) }
        ) {
            if (!current.isLive && current.durationMs > 0) {
                LinearProgressIndicator(
                    progress = { (current.positionMs.toFloat() / current.durationMs).coerceIn(0f, 1f) },
                    color = IptvColors.Accent,
                    trackColor = Color.White.copy(alpha = 0.15f),
                    modifier = Modifier.fillMaxWidth().height(2.dp),
                )
            }
            Row(
                Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(width = 64.dp, height = 40.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(IptvColors.Surface),
                    contentAlignment = Alignment.Center,
                ) {
                    if (current.image != null) {
                        AsyncImage(model = current.image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    } else {
                        Icon(Icons.Filled.Cast, contentDescription = null, tint = IptvColors.TextSecondary)
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        current.title ?: "Chromecast",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "Lecture sur ${current.deviceName ?: "ta Chromecast"}",
                        color = IptvColors.AccentBright,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = { CastRemote.togglePlayback(context) }) {
                    val playing = current.isPlaying || current.isBuffering
                    Icon(
                        if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (playing) "Pause" else "Lecture",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp),
                    )
                }
                IconButton(onClick = { CastRemote.stop(context) }) {
                    Icon(Icons.Filled.Close, contentDescription = "Arrêter la diffusion", tint = IptvColors.TextSecondary)
                }
            }
        }
    }
}

/** Rouvre la télécommande sur ce que lit la Chromecast, si la file est encore en mémoire. */
private fun openRemote(context: Context, snapshot: CastSnapshot) {
    val url = snapshot.contentUrl ?: return
    val items = PlayQueue.items
    val index = items.indexOfFirst { it.url == url }
    if (index < 0) return
    PlayQueue.set(items, index)
    context.startActivity(Intent(context, PlayerActivity::class.java))
}
