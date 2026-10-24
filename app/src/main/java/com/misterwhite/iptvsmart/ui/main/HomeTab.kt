package com.misterwhite.iptvsmart.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.misterwhite.iptvsmart.cast.CastButton
import com.misterwhite.iptvsmart.data.CatalogRepository
import com.misterwhite.iptvsmart.ui.components.ChannelCard
import com.misterwhite.iptvsmart.ui.components.ContentRow
import com.misterwhite.iptvsmart.ui.components.PosterCard
import com.misterwhite.iptvsmart.ui.components.RemoteImage
import com.misterwhite.iptvsmart.ui.components.WideCard
import com.misterwhite.iptvsmart.data.MediaKind
import com.misterwhite.iptvsmart.ui.theme.IptvColors
import kotlinx.coroutines.delay

/** Élément mis en avant dans le carrousel du haut. */
private data class Featured(
    val key: String,
    val title: String,
    val subtitle: String?,
    val image: String?,
    val open: () -> Unit,
)

@Composable
fun HomeTab(
    content: CatalogRepository.Content,
    library: LibraryState,
    actions: MainActions,
) {
    // Un seul élément par série : le dernier épisode regardé.
    val continueWatching = remember(library.history) {
        library.history
            .filter { it.isResumable }
            .distinctBy { it.seriesId ?: it.key }
            .take(20)
    }
    val recentChannels = remember(library.history) {
        library.history.filter { it.kind == MediaKind.LIVE }.take(20)
    }
    val recentMovies = remember(content) {
        content.movies.items.sortedByDescending { it.addedAt }.take(30)
    }
    val recentSeries = remember(content) {
        content.series.items.sortedByDescending { it.updatedAt }.take(30)
    }
    val featured = remember(content) {
        val series = recentSeries.filter { !it.backdrop.isNullOrBlank() || !it.cover.isNullOrBlank() }.take(4)
            .map { Featured("s" + it.id, it.name, listOfNotNull("Série", it.genre, it.year).joinToString(" • "), it.backdrop ?: it.cover) { actions.openSeries(it.id) } }
        val movies = recentMovies.filter { !it.poster.isNullOrBlank() }.take(4)
            .map { Featured("m" + it.id, it.name, "Film", it.poster) { actions.openMovie(it.id) } }
        (series.zip(movies).flatMap { listOf(it.first, it.second) } +
            series.drop(movies.size) + movies.drop(series.size)).take(8)
    }
    // Quelques catégories de films et séries, avec leurs éléments.
    val movieRows = remember(content) {
        val byCategory = content.movies.items.groupBy { it.categoryId }
        content.movies.categories.mapNotNull { cat ->
            byCategory[cat.id]?.takeIf { it.isNotEmpty() }?.let { cat to it.take(30) }
        }.take(6)
    }
    val seriesRows = remember(content) {
        val byCategory = content.series.items.groupBy { it.categoryId }
        content.series.categories.mapNotNull { cat ->
            byCategory[cat.id]?.takeIf { it.isNotEmpty() }?.let { cat to it.take(30) }
        }.take(4)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(key = "hero") {
            Box {
                if (featured.isNotEmpty()) HeroCarousel(featured)
                else Spacer(Modifier.statusBarsPadding().height(64.dp))
                TopBar(Modifier.align(Alignment.TopStart))
            }
        }
        item(key = "continue") {
            ContentRow("Reprendre la lecture", continueWatching, key = { it.key }) { entry ->
                WideCard(
                    title = entry.title,
                    subtitle = entry.subtitle,
                    imageUrl = entry.image,
                    progress = entry.progress,
                    onClick = { actions.resume(entry) },
                )
            }
        }
        item(key = "favorites") {
            ContentRow("Ma liste", library.favorites.filter { it.kind != MediaKind.LIVE }, key = { it.key }) { fav ->
                PosterCard(fav.title, fav.image, onClick = {
                    if (fav.kind == MediaKind.SERIES) actions.openSeries(fav.contentId) else actions.openMovie(fav.contentId)
                })
            }
        }
        item(key = "recentChannels") {
            ContentRow("Chaînes récentes", recentChannels, key = { it.key }) { entry ->
                ChannelCard(entry.title, entry.image, onClick = { actions.playChannel(entry.contentId) })
            }
        }
        item(key = "recentMovies") {
            ContentRow("Films récemment ajoutés", recentMovies, key = { it.id }) { movie ->
                PosterCard(movie.name, movie.poster, onClick = { actions.openMovie(movie.id) })
            }
        }
        item(key = "recentSeries") {
            ContentRow("Séries à la une", recentSeries, key = { it.id }) { series ->
                PosterCard(series.name, series.cover, onClick = { actions.openSeries(series.id) })
            }
        }
        item(key = "live") {
            ContentRow("En direct", content.live.items.take(20), key = { it.id }) { channel ->
                ChannelCard(channel.name, channel.logo, onClick = { actions.playChannel(channel.id) })
            }
        }
        movieRows.forEach { (category, movies) ->
            item(key = "mc" + category.id) {
                ContentRow(category.name, movies, key = { it.id }) { movie ->
                    PosterCard(movie.name, movie.poster, onClick = { actions.openMovie(movie.id) })
                }
            }
        }
        seriesRows.forEach { (category, series) ->
            item(key = "sc" + category.id) {
                ContentRow(category.name, series, key = { it.id }) { s ->
                    PosterCard(s.name, s.cover, onClick = { actions.openSeries(s.id) })
                }
            }
        }
    }
}

@Composable
private fun TopBar(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent)))
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text("Zynora", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
        CastButton(Modifier.size(48.dp))
    }
}

@Composable
private fun HeroCarousel(items: List<Featured>) {
    val pagerState = rememberPagerState(pageCount = { items.size })
    LaunchedEffect(pagerState, items.size) {
        while (items.size > 1) {
            delay(6_000)
            pagerState.animateScrollToPage((pagerState.currentPage + 1) % items.size)
        }
    }
    Column {
        HorizontalPager(state = pagerState, key = { items[it].key }) { page ->
            val item = items[page]
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(440.dp),
            ) {
                RemoteImage(item.image, item.title, Modifier.fillMaxSize())
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Transparent,
                                0.45f to Color.Transparent,
                                1f to IptvColors.Background,
                            )
                        ),
                )
                Column(
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                ) {
                    Text(
                        item.title,
                        style = MaterialTheme.typography.headlineLarge,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (item.subtitle != null) {
                        Text(item.subtitle, color = IptvColors.TextSecondary)
                    }
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = item.open,
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = IptvColors.TextPrimary,
                            contentColor = IptvColors.Background,
                        ),
                    ) {
                        Icon(Icons.Filled.Info, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(8.dp))
                        Text("DÉCOUVRIR", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        if (items.size > 1) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                repeat(items.size) { index ->
                    Box(
                        Modifier
                            .padding(3.dp)
                            .size(if (index == pagerState.currentPage) 8.dp else 6.dp)
                            .background(
                                if (index == pagerState.currentPage) IptvColors.TextPrimary else IptvColors.TextSecondary.copy(alpha = 0.5f),
                                CircleShape,
                            ),
                    )
                }
            }
        }
    }
}
