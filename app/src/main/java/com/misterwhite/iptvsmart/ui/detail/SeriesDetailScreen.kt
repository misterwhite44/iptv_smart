package com.misterwhite.iptvsmart.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.misterwhite.iptvsmart.data.xtream.Episode
import com.misterwhite.iptvsmart.data.xtream.SeriesDetails
import com.misterwhite.iptvsmart.ui.components.EmptyBox
import com.misterwhite.iptvsmart.ui.components.ErrorBox
import com.misterwhite.iptvsmart.ui.components.LoadingBox
import com.misterwhite.iptvsmart.ui.components.ProgressBar
import com.misterwhite.iptvsmart.ui.components.RemoteImage
import com.misterwhite.iptvsmart.ui.theme.IptvColors

@Composable
fun SeriesDetailScreen(
    viewModel: SeriesDetailViewModel,
    onBack: () -> Unit,
    onPlayEpisode: (SeriesDetails, Episode) -> Unit,
    /** Avancement par identifiant d'épisode, entre 0 et 1. */
    episodeProgress: Map<String, Float> = emptyMap(),
    /** Épisode à reprendre en priorité (le dernier regardé). */
    resumeEpisodeId: String? = null,
    isFavorite: Boolean = false,
    onToggleFavorite: (SeriesDetails) -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Box(Modifier.fillMaxSize().background(IptvColors.Background)) {
        when (val s = state) {
            DetailState.Loading -> LoadingBox()
            is DetailState.Error -> ErrorBox(s.message, onRetry = viewModel::load)
            is DetailState.Ready -> SeriesContent(s.details, onBack, onPlayEpisode, episodeProgress, resumeEpisodeId, isFavorite, onToggleFavorite)
        }
    }
}

@Composable
private fun SeriesContent(
    d: SeriesDetails,
    onBack: () -> Unit,
    onPlayEpisode: (SeriesDetails, Episode) -> Unit,
    episodeProgress: Map<String, Float>,
    resumeEpisodeId: String?,
    isFavorite: Boolean,
    onToggleFavorite: (SeriesDetails) -> Unit,
) {
    val seasons = d.seasons.keys.toList()
    val allEpisodes = d.seasons.values.flatten()
    val resume = allEpisodes.firstOrNull { it.id == resumeEpisodeId }
    val initialSeason = resume?.let { seasons.indexOf(it.season) }?.takeIf { it >= 0 } ?: 0
    var seasonIndex by rememberSaveable(d.series.id) { mutableIntStateOf(initialSeason) }
    val episodes = seasons.getOrNull(seasonIndex)?.let { d.seasons[it] }.orEmpty()

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp),
    ) {
        item(key = "header") {
            DetailHeader(
                title = d.series.name,
                backdrop = d.series.backdrop,
                poster = d.series.cover,
                meta = listOfNotNull(
                    d.series.year,
                    if (seasons.size > 1) "${seasons.size} saisons" else if (seasons.size == 1) "1 saison" else null,
                    formatRating(d.series.rating),
                ).joinToString(" • "),
                onBack = onBack,
            )
        }
        item(key = "intro") {
            Column(Modifier.padding(16.dp)) {
                val first = resume ?: allEpisodes.firstOrNull()
                if (first != null) {
                    PlayButton(
                        if (resume != null) "REPRENDRE S${resume.season} É${resume.number}" else "LECTURE S${first.season} É${first.number}",
                        onClick = { onPlayEpisode(d, first) },
                    )
                    Spacer(Modifier.height(10.dp))
                }
                FavoriteButton(isFavorite, onClick = { onToggleFavorite(d) })
                Spacer(Modifier.height(16.dp))
                if (!d.series.plot.isNullOrBlank()) {
                    Text(d.series.plot, style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(10.dp))
                }
                InfoLine("Genre", d.series.genre)
                InfoLine("Avec", d.cast)
            }
        }
        if (seasons.isEmpty()) {
            item(key = "empty") { EmptyBox("Aucun épisode disponible pour cette série.", Modifier.height(160.dp)) }
        } else {
            item(key = "tabs") {
                ScrollableTabRow(
                    selectedTabIndex = seasonIndex.coerceIn(0, seasons.lastIndex),
                    containerColor = IptvColors.Background,
                    contentColor = IptvColors.TextPrimary,
                    edgePadding = 16.dp,
                    indicator = { positions ->
                        val index = seasonIndex.coerceIn(0, positions.lastIndex)
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(positions[index]),
                            color = IptvColors.Accent,
                        )
                    },
                    divider = {},
                ) {
                    seasons.forEachIndexed { index, season ->
                        Tab(
                            selected = index == seasonIndex,
                            onClick = { seasonIndex = index },
                            text = { Text("Saison $season") },
                            unselectedContentColor = IptvColors.TextSecondary,
                        )
                    }
                }
            }
            items(episodes, key = { "e" + it.id }) { episode ->
                EpisodeRow(
                    episode = episode,
                    fallbackImage = d.series.backdrop ?: d.series.cover,
                    progress = episodeProgress[episode.id],
                    onClick = { onPlayEpisode(d, episode) },
                )
            }
        }
    }
}

@Composable
private fun EpisodeRow(episode: Episode, fallbackImage: String?, progress: Float?, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .width(150.dp)
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(6.dp)),
            ) {
                RemoteImage(episode.image ?: fallbackImage, "É${episode.number}", Modifier.fillMaxSize())
                if (progress != null) ProgressBar(progress, Modifier.align(Alignment.BottomCenter))
            }
            Spacer(Modifier.width(12.dp))
            Column(verticalArrangement = Arrangement.Center) {
                Text(
                    "${episode.number}. ${episode.title}",
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                episode.durationSecs?.takeIf { it > 0 }?.let {
                    Text("${it / 60} min", color = IptvColors.TextSecondary, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        if (!episode.plot.isNullOrBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                episode.plot,
                color = IptvColors.TextSecondary,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
