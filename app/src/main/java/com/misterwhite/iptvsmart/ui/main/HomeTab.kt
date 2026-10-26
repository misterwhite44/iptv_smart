package com.misterwhite.iptvsmart.ui.main

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.misterwhite.iptvsmart.data.CatalogRepository
import com.misterwhite.iptvsmart.data.LibraryRepository
import com.misterwhite.iptvsmart.data.MediaKind
import com.misterwhite.iptvsmart.data.WatchEntry
import com.misterwhite.iptvsmart.data.xtream.Category
import com.misterwhite.iptvsmart.data.xtream.MovieDetails
import com.misterwhite.iptvsmart.ui.components.ChannelCard
import com.misterwhite.iptvsmart.ui.components.ContentRow
import com.misterwhite.iptvsmart.ui.components.PosterCard
import com.misterwhite.iptvsmart.ui.components.PressableCard
import com.misterwhite.iptvsmart.ui.components.ProgressBar
import com.misterwhite.iptvsmart.ui.components.RemoteImage
import com.misterwhite.iptvsmart.ui.components.WideCard
import com.misterwhite.iptvsmart.ui.detail.formatRating
import com.misterwhite.iptvsmart.ui.premium.PremiumCastButton
import com.misterwhite.iptvsmart.ui.theme.IptvColors
import kotlinx.coroutines.delay

/** Les onglets en pilule en haut de l'accueil. */
private enum class HomeSection(val label: String) {
    FOR_YOU("Pour toi"),
    MOVIES("Films"),
    SERIES("Séries"),
    LIVE("Direct"),
}

/** Élément mis en avant dans la grande affiche du haut. */
private data class Featured(
    val key: String,
    val badge: String,
    val title: String,
    val meta: String?,
    val rating: String?,
    val description: String?,
    val image: String?,
    val favoriteKey: String,
    val play: () -> Unit,
    val info: () -> Unit,
)

@Composable
fun HomeTab(
    content: CatalogRepository.Content,
    library: LibraryState,
    actions: MainActions,
    movieDetails: Map<String, MovieDetails>,
    loadMovieDetails: (List<String>) -> Unit,
    toggleFavorite: (key: String, kind: MediaKind, id: String, title: String, image: String?) -> Unit,
) {
    var section by rememberSaveable { mutableStateOf(HomeSection.FOR_YOU) }

    val moviesByCategory = remember(content) { content.movies.items.groupBy { it.categoryId } }
    val seriesByCategory = remember(content) { content.series.items.groupBy { it.categoryId } }
    val liveByCategory = remember(content) { content.live.items.groupBy { it.categoryId } }
    val recentMovies = remember(content) { content.movies.items.sortedByDescending { it.addedAt }.take(30) }
    val recentSeries = remember(content) { content.series.items.sortedByDescending { it.updatedAt }.take(30) }
    val featuredMovies = remember(recentMovies) { recentMovies.filter { !it.poster.isNullOrBlank() }.take(5) }
    val featuredSeries = remember(recentSeries) {
        recentSeries.filter { !it.backdrop.isNullOrBlank() || !it.cover.isNullOrBlank() }.take(5)
    }
    LaunchedEffect(featuredMovies) { loadMovieDetails(featuredMovies.map { it.id }) }

    val movieCards = featuredMovies.map { m ->
        val d = movieDetails[m.id]
        Featured(
            key = "m" + m.id,
            badge = "Nouveau film",
            title = m.name,
            meta = listOfNotNull(d?.releaseDate?.take(4), d?.duration?.let(::shortDuration), d?.genre).joinToString(" • ").ifBlank { null },
            rating = formatRating(m.rating),
            description = d?.plot,
            image = d?.backdrop ?: m.poster,
            favoriteKey = LibraryRepository.movieKey(m.id),
            play = { if (d != null) actions.playMovie(d) else actions.openMovie(m.id) },
            info = { actions.openMovie(m.id) },
        )
    }
    val seriesCards = featuredSeries.map { s ->
        Featured(
            key = "s" + s.id,
            badge = "Nouvelle série",
            title = s.name,
            meta = listOfNotNull(s.year, s.genre).joinToString(" • ").ifBlank { null },
            rating = formatRating(s.rating),
            description = s.plot,
            image = s.backdrop ?: s.cover,
            favoriteKey = LibraryRepository.seriesKey(s.id),
            play = { actions.openSeries(s.id) },
            info = { actions.openSeries(s.id) },
        )
    }
    fun featuredFor(section: HomeSection) = when (section) {
        HomeSection.FOR_YOU -> (movieCards.zip(seriesCards).flatMap { listOf(it.first, it.second) } +
            movieCards.drop(seriesCards.size) + seriesCards.drop(movieCards.size)).take(8)
        HomeSection.MOVIES -> movieCards
        HomeSection.SERIES -> seriesCards
        HomeSection.LIVE -> emptyList()
    }
    val favoriteKeys = remember(library.favorites) { library.favorites.mapTo(HashSet()) { it.key } }
    val inProgress = remember(library.history) { inProgress(library.history, System.currentTimeMillis()) }
    var forgetting by remember { mutableStateOf<WatchEntry?>(null) }
    val onForget: (WatchEntry) -> Unit = { forgetting = it }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 600.dp
        // Sur téléphone, l'affiche est un peu moins haute quand il y a des
        // lectures en cours, pour que leur rangée soit visible sans défiler.
        val heroHeight = when {
            wide -> maxHeight * 0.82f
            inProgress.isNotEmpty() -> 500.dp
            else -> 560.dp
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item(key = "hero") {
                Box {
                    AnimatedContent(
                        targetState = section,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "hero",
                    ) { shown ->
                        val cards = featuredFor(shown)
                        if (cards.isNotEmpty()) {
                            Hero(cards, heroHeight, wide, favoriteKeys) { f ->
                                val kind = if (f.key.startsWith("s")) MediaKind.SERIES else MediaKind.MOVIE
                                toggleFavorite(f.favoriteKey, kind, f.key.drop(1), f.title, f.image)
                            }
                        } else {
                            Spacer(Modifier.fillMaxWidth().statusBarsPadding().height(72.dp))
                        }
                    }
                    TopBar(section, onSection = { section = it }, Modifier.align(Alignment.TopStart))
                }
            }
            val sectionInProgress = when (section) {
                HomeSection.FOR_YOU -> inProgress
                HomeSection.MOVIES -> inProgress.filter { it.kind == MediaKind.MOVIE }
                HomeSection.SERIES -> inProgress.filter { it.kind == MediaKind.EPISODE }
                HomeSection.LIVE -> inProgress.filter { it.kind == MediaKind.LIVE }
            }
            item(key = "inProgress") {
                ContentRow("En cours", sectionInProgress, key = { it.key }) { entry ->
                    InProgressCard(entry, onClick = { actions.resume(entry) }, onLongClick = { onForget(entry) })
                }
            }
            when (section) {
                HomeSection.FOR_YOU -> forYouRows(content, moviesByCategory, library, actions, recentMovies, recentSeries)
                HomeSection.MOVIES -> categoryRows(content.movies.categories, moviesByCategory, 12, key = { it.id }) { movie ->
                    PosterCard(movie.name, movie.poster, onClick = { actions.openMovie(movie.id) })
                }
                HomeSection.SERIES -> categoryRows(content.series.categories, seriesByCategory, 12, key = { it.id }) { s ->
                    PosterCard(s.name, s.cover, onClick = { actions.openSeries(s.id) })
                }
                HomeSection.LIVE -> {
                    val favorites = library.favorites.filter { it.kind == MediaKind.LIVE }
                    item(key = "liveFav") {
                        ContentRow("Mes chaînes", favorites, key = { it.key }) { fav ->
                            ChannelCard(fav.title, fav.image, onClick = { actions.playChannel(fav.contentId) })
                        }
                    }
                    categoryRows(content.live.categories, liveByCategory, 12, key = { it.id }) { ch ->
                        ChannelCard(ch.name, ch.logo, onClick = { actions.playChannel(ch.id) })
                    }
                }
            }
        }
    }

    forgetting?.let { entry ->
        AlertDialog(
            onDismissRequest = { forgetting = null },
            title = { Text("Retirer de « En cours » ?") },
            text = { Text(entry.title + (entry.subtitle?.let { "\n$it" } ?: "")) },
            confirmButton = {
                TextButton(onClick = {
                    actions.forget(entry)
                    forgetting = null
                }) { Text("Retirer") }
            },
            dismissButton = { TextButton(onClick = { forgetting = null }) { Text("Annuler") } },
            containerColor = IptvColors.Surface,
        )
    }
}

/**
 * Ce qu'on regarde en ce moment, le plus récent d'abord : films et épisodes
 * entamés (un seul épisode par série) et chaînes ouvertes ces dernières heures.
 */
private fun inProgress(history: List<WatchEntry>, now: Long): List<WatchEntry> {
    val vod = history.filter { it.isResumable }.distinctBy { it.seriesId ?: it.key }
    val live = history.filter { it.kind == MediaKind.LIVE && now - it.updatedAt < RECENT_LIVE_MS }.take(MAX_RECENT_LIVE)
    return (vod + live).sortedByDescending { it.updatedAt }.take(MAX_IN_PROGRESS)
}

private const val RECENT_LIVE_MS = 24 * 60 * 60 * 1000L
private const val MAX_RECENT_LIVE = 3
private const val MAX_IN_PROGRESS = 20

/** Carte 16:9 de « En cours » : progression et temps restant, ou badge DIRECT. */
@Composable
private fun InProgressCard(entry: WatchEntry, onClick: () -> Unit, onLongClick: () -> Unit) {
    val live = entry.kind == MediaKind.LIVE
    Column(Modifier.width(220.dp)) {
        PressableCard(
            onClick = onClick,
            onLongClick = onLongClick,
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
        ) {
            if (live) {
                RemoteImage(
                    entry.image,
                    entry.title,
                    Modifier.fillMaxSize().padding(18.dp),
                    contentScale = ContentScale.Fit,
                )
            } else {
                RemoteImage(entry.image, entry.title, Modifier.fillMaxSize())
            }
            Box(
                Modifier
                    .align(Alignment.Center)
                    .size(40.dp)
                    .background(Color.Black.copy(alpha = 0.55f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = "Reprendre", tint = Color.White)
            }
            if (live) {
                Text(
                    "DIRECT",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .background(IptvColors.Error, RoundedCornerShape(3.dp))
                        .padding(horizontal = 5.dp, vertical = 1.dp),
                )
            }
            entry.progress?.let { ProgressBar(it, Modifier.align(Alignment.BottomCenter)) }
        }
        Spacer(Modifier.height(6.dp))
        Text(entry.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        val detail = when {
            live -> entry.subtitle ?: "En direct"
            entry.kind == MediaKind.EPISODE -> entry.subtitle
            else -> remaining(entry) ?: entry.subtitle
        }
        if (detail != null) {
            Text(
                detail,
                style = MaterialTheme.typography.bodyMedium,
                color = IptvColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** « Reste 1 h 12 », « Reste 8 min ». */
private fun remaining(entry: WatchEntry): String? {
    if (entry.durationMs <= 0) return null
    val minutes = ((entry.durationMs - entry.positionMs).coerceAtLeast(0) / 60_000).toInt().coerceAtLeast(1)
    return if (minutes >= 60) "Reste ${minutes / 60} h ${"%02d".format(minutes % 60)}" else "Reste $minutes min"
}

private fun androidx.compose.foundation.lazy.LazyListScope.forYouRows(
    content: CatalogRepository.Content,
    moviesByCategory: Map<String?, List<com.misterwhite.iptvsmart.data.xtream.Movie>>,
    library: LibraryState,
    actions: MainActions,
    recentMovies: List<com.misterwhite.iptvsmart.data.xtream.Movie>,
    recentSeries: List<com.misterwhite.iptvsmart.data.xtream.Series>,
) {
    item(key = "myChannels") {
        ContentRow("Mes chaînes", library.favorites.filter { it.kind == MediaKind.LIVE }, key = { it.key }) { fav ->
            ChannelCard(fav.title, fav.image, onClick = { actions.playChannel(fav.contentId) })
        }
    }
    item(key = "myList") {
        ContentRow("Ma liste", library.favorites.filter { it.kind != MediaKind.LIVE }, key = { it.key }) { fav ->
            PosterCard(fav.title, fav.image, onClick = {
                if (fav.kind == MediaKind.SERIES) actions.openSeries(fav.contentId) else actions.openMovie(fav.contentId)
            })
        }
    }
    item(key = "recommended") {
        // Séries avec une image large en cartes 16:9, comme « Recommended For You ».
        val wideSeries = recentSeries.filter { !it.backdrop.isNullOrBlank() }
        if (wideSeries.size >= 4) {
            ContentRow("Recommandé pour toi", wideSeries, key = { it.id }) { s ->
                WideCard(s.name, s.genre, s.backdrop, onClick = { actions.openSeries(s.id) }, progress = null)
            }
        } else {
            ContentRow("Recommandé pour toi", recentSeries, key = { it.id }) { s ->
                PosterCard(s.name, s.cover, onClick = { actions.openSeries(s.id) })
            }
        }
    }
    item(key = "recentMovies") {
        ContentRow("Nouveaux films", recentMovies, key = { it.id }) { movie ->
            PosterCard(movie.name, movie.poster, onClick = { actions.openMovie(movie.id) })
        }
    }
    item(key = "recentSeries") {
        ContentRow("Séries à la une", recentSeries, key = { it.id }) { s ->
            PosterCard(s.name, s.cover, onClick = { actions.openSeries(s.id) })
        }
    }
    val recentChannels = library.history.filter { it.kind == MediaKind.LIVE }.take(20)
    item(key = "recentChannels") {
        ContentRow("Chaînes récentes", recentChannels, key = { it.key }) { entry ->
            ChannelCard(entry.title, entry.image, onClick = { actions.playChannel(entry.contentId) })
        }
    }
    categoryRows(content.movies.categories, moviesByCategory, 4, "fm", key = { it.id }) { movie ->
        PosterCard(movie.name, movie.poster, onClick = { actions.openMovie(movie.id) })
    }
}

private fun <T : Any> androidx.compose.foundation.lazy.LazyListScope.categoryRows(
    categories: List<Category>,
    byCategory: Map<String?, List<T>>,
    max: Int,
    prefix: String = "c",
    key: (T) -> Any,
    card: @Composable (T) -> Unit,
) {
    categories.asSequence()
        .mapNotNull { cat -> byCategory[cat.id]?.takeIf { it.isNotEmpty() }?.let { cat to it.take(30) } }
        .take(max)
        .forEach { (cat, list) ->
            item(key = prefix + cat.id) {
                ContentRow(cat.name, list, key = key) { card(it) }
            }
        }
}

@Composable
private fun TopBar(section: HomeSection, onSection: (HomeSection) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.65f), Color.Transparent)))
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "ZYNORA",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 3.sp,
                color = IptvColors.TextPrimary,
                modifier = Modifier.padding(start = 4.dp).weight(1f),
            )
            PremiumCastButton(Modifier.size(48.dp))
        }
        Spacer(Modifier.height(4.dp))
        // Pilule semi-transparente avec les sections.
        Row(
            Modifier
                .align(Alignment.CenterHorizontally)
                .clip(RoundedCornerShape(24.dp))
                .background(Color.White.copy(alpha = 0.12f))
                .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(24.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            HomeSection.entries.forEach { item ->
                val selected = item == section
                Text(
                    item.label,
                    color = if (selected) IptvColors.TextPrimary else IptvColors.TextSecondary,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (selected) Color.White.copy(alpha = 0.18f) else Color.Transparent)
                        .clickable { onSection(item) }
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                )
            }
        }
    }
}

@Composable
private fun Hero(
    items: List<Featured>,
    height: Dp,
    wide: Boolean,
    favoriteKeys: Set<String>,
    onToggleFavorite: (Featured) -> Unit,
) {
    val pagerState = rememberPagerState(pageCount = { items.size })
    LaunchedEffect(pagerState, items.size) {
        while (items.size > 1) {
            delay(7_000)
            pagerState.animateScrollToPage((pagerState.currentPage + 1) % items.size)
        }
    }
    Box {
        HorizontalPager(state = pagerState, key = { items[it].key }) { page ->
            val item = items[page]
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(height),
            ) {
                RemoteImage(item.image, item.title, Modifier.fillMaxSize())
                // Assombrit la gauche et le bas pour que le texte reste lisible.
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.horizontalGradient(
                                0f to IptvColors.Background.copy(alpha = if (wide) 0.95f else 0.75f),
                                (if (wide) 0.55f else 0.8f) to Color.Transparent,
                            )
                        ),
                )
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Transparent,
                                0.5f to Color.Transparent,
                                1f to IptvColors.Background,
                            )
                        ),
                )
                Column(
                    Modifier
                        .align(Alignment.BottomStart)
                        .widthIn(max = if (wide) 420.dp else 600.dp)
                        .padding(start = 20.dp, end = 20.dp, bottom = 40.dp),
                ) {
                    Text(
                        item.badge,
                        color = Color.Black,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .background(Color.White, RoundedCornerShape(3.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        item.title.uppercase(),
                        fontSize = if (wide) 36.sp else 30.sp,
                        lineHeight = if (wide) 40.sp else 34.sp,
                        fontWeight = FontWeight.Light,
                        letterSpacing = 1.sp,
                        color = IptvColors.TextPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text("Disponible maintenant", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (item.rating != null) {
                            Text(
                                item.rating,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier
                                    .border(BorderStroke(1.dp, IptvColors.TextPrimary), RoundedCornerShape(3.dp))
                                    .padding(horizontal = 4.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        if (item.meta != null) {
                            Text(item.meta, style = MaterialTheme.typography.bodyMedium, color = IptvColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    if (!item.description.isNullOrBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            item.description,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Button(
                            onClick = item.play,
                            shape = RoundedCornerShape(4.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White.copy(alpha = 0.18f),
                                contentColor = IptvColors.TextPrimary,
                            ),
                            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("LECTURE", fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                        }
                        Spacer(Modifier.width(10.dp))
                        RoundIcon(if (item.favoriteKey in favoriteKeys) Icons.Filled.Check else Icons.Filled.Add, "Ma liste") {
                            onToggleFavorite(item)
                        }
                        Spacer(Modifier.width(8.dp))
                        RoundIcon(Icons.Filled.Info, "Plus d'infos", item.info)
                    }
                }
            }
        }
        if (items.size > 1) {
            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 14.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                repeat(items.size) { index ->
                    val selected = index == pagerState.currentPage
                    Box(
                        Modifier
                            .padding(horizontal = 4.dp)
                            .size(if (selected) 7.dp else 6.dp)
                            .background(
                                if (selected) IptvColors.TextPrimary else IptvColors.TextSecondary.copy(alpha = 0.5f),
                                CircleShape,
                            ),
                    )
                }
            }
        }
    }
}

@Composable
private fun RoundIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(42.dp)
            .border(1.5.dp, Color.White.copy(alpha = 0.8f), CircleShape)
            .background(Color.Black.copy(alpha = 0.3f), CircleShape),
    ) {
        Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(20.dp))
    }
}

/** « 01:58:00 » ou « 118 min » devient « 1 h 58 ». */
private fun shortDuration(raw: String): String? {
    val parts = raw.split(":").mapNotNull { it.trim().toIntOrNull() }
    val minutes = when {
        parts.size >= 2 -> parts[0] * 60 + parts[1]
        else -> Regex("\\d+").find(raw)?.value?.toIntOrNull()
    } ?: return null
    if (minutes <= 0) return null
    return if (minutes >= 60) "${minutes / 60} h ${"%02d".format(minutes % 60)}" else "$minutes min"
}
