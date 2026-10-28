package com.misterwhite.iptvsmart.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import com.misterwhite.iptvsmart.cast.MiniCastBar
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.IconButton
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.misterwhite.iptvsmart.data.FavoriteEntry
import com.misterwhite.iptvsmart.data.LibraryRepository
import com.misterwhite.iptvsmart.data.MediaKind
import com.misterwhite.iptvsmart.data.WatchEntry
import com.misterwhite.iptvsmart.data.xtream.MovieDetails
import com.misterwhite.iptvsmart.ui.components.ErrorBox
import com.misterwhite.iptvsmart.ui.profile.ProfileTab
import com.misterwhite.iptvsmart.ui.profile.ProfileViewModel
import com.misterwhite.iptvsmart.ui.components.LoadingBox
import com.misterwhite.iptvsmart.ui.theme.IptvColors

/** Actions de navigation partagées par les onglets. */
class MainActions(
    val openMovie: (String) -> Unit,
    val openSeries: (String) -> Unit,
    val playChannel: (String) -> Unit,
    val playMovie: (MovieDetails) -> Unit,
    /** Reprend une lecture depuis « En cours » ou l'historique. */
    val resume: (WatchEntry) -> Unit,
    /** Retire une lecture de « En cours » (appui long). */
    val forget: (WatchEntry) -> Unit = {},
)

/** Historique et favoris, partagés par les onglets. */
data class LibraryState(
    val history: List<WatchEntry>,
    val favorites: List<FavoriteEntry>,
) {
    fun favoriteIds(kind: MediaKind): Set<String> =
        favorites.filter { it.kind == kind }.mapTo(HashSet()) { it.contentId }
}

private enum class Tab(val label: String, val icon: ImageVector) {
    HOME("Accueil", Icons.Filled.Home),
    LIVE("Direct", Icons.Filled.LiveTv),
    MOVIES("Films", Icons.Filled.Movie),
    SERIES("Séries", Icons.Filled.Tv),
    SEARCH("Recherche", Icons.Filled.Search),
    PROFILE("Profil", Icons.Filled.Person),
}

@Composable
fun MainScreen(
    viewModel: MainViewModel,
    profileViewModel: ProfileViewModel,
    library: LibraryRepository,
    actions: MainActions,
    onLoggedOut: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val history by library.history.collectAsStateWithLifecycle()
    val favorites by library.favorites.collectAsStateWithLifecycle()
    val movieDetails by viewModel.movieDetails.collectAsStateWithLifecycle()
    val libraryState = LibraryState(history, favorites)
    var tabIndex by rememberSaveable { mutableIntStateOf(Tab.HOME.ordinal) }
    val tab = Tab.entries[tabIndex]

    val body: @Composable () -> Unit = {
        when (val s = state) {
            CatalogState.Loading -> LoadingBox(message = "Chargement du catalogue…")
            is CatalogState.Error -> ErrorBox(s.message, onRetry = { viewModel.load(force = true) })
            is CatalogState.Ready -> when (tab) {
                Tab.HOME -> HomeTab(
                    s.content,
                    libraryState,
                    actions,
                    movieDetails = movieDetails,
                    loadMovieDetails = viewModel::loadMovieDetails,
                    toggleFavorite = { key, kind, id, title, image ->
                        library.toggleFavorite(FavoriteEntry(key, kind, id, title, image, System.currentTimeMillis()))
                    },
                )
                Tab.LIVE -> {
                    // Les chaînes favorites en tête de liste, dans l'ordre des favoris.
                    val favoriteIds = libraryState.favoriteIds(MediaKind.LIVE)
                    val live = remember(s.content, favoriteIds) {
                        val byId = s.content.live.items.associateBy { it.id }
                        val first = favorites.filter { it.kind == MediaKind.LIVE }.mapNotNull { byId[it.contentId] }
                        s.content.live.copy(items = first + s.content.live.items.filter { it.id !in favoriteIds })
                    }
                    LiveTab(live, favoriteIds, actions)
                }
                Tab.MOVIES -> MoviesTab(s.content.movies, libraryState.favoriteIds(MediaKind.MOVIE), actions)
                Tab.SERIES -> SeriesTab(s.content.series, libraryState.favoriteIds(MediaKind.SERIES), actions)
                Tab.SEARCH -> SearchTab(s.content, actions)
                Tab.PROFILE -> ProfileTab(
                    profileViewModel,
                    s.content,
                    libraryState,
                    actions,
                    onRefreshCatalog = { viewModel.load(force = true) },
                    onLoggedOut = onLoggedOut,
                )
            }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(IptvColors.Background)) {
        if (maxWidth >= 600.dp) {
            // Écran large (paysage, tablette) : menu d'icônes à gauche.
            Row(Modifier.fillMaxSize()) {
                SideRail(tab, profileViewModel.username, onSelect = { tabIndex = it.ordinal })
                Box(Modifier.weight(1f).fillMaxSize()) {
                    body()
                    MiniCastBar(Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
                }
            }
        } else {
            Scaffold(
                containerColor = IptvColors.Background,
                bottomBar = {
                    Column {
                        MiniCastBar()
                        BottomBar(tab, onSelect = { tabIndex = it.ordinal })
                    }
                },
            ) { padding ->
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(bottom = padding.calculateBottomPadding()),
                ) { body() }
            }
        }
    }
}

@Composable
private fun BottomBar(tab: Tab, onSelect: (Tab) -> Unit) {
    NavigationBar(containerColor = IptvColors.Background.copy(alpha = 0.97f)) {
        Tab.entries.forEach { item ->
            NavigationBarItem(
                selected = item == tab,
                onClick = { onSelect(item) },
                icon = { Icon(item.icon, contentDescription = item.label) },
                label = { Text(item.label, maxLines = 1) },
                alwaysShowLabel = false,
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = IptvColors.TextPrimary,
                    selectedTextColor = IptvColors.TextPrimary,
                    unselectedIconColor = IptvColors.TextSecondary,
                    unselectedTextColor = IptvColors.TextSecondary,
                    indicatorColor = IptvColors.Accent.copy(alpha = 0.35f),
                ),
            )
        }
    }
}

@Composable
private fun SideRail(tab: Tab, username: String, onSelect: (Tab) -> Unit) {
    Column(
        Modifier
            .fillMaxHeight()
            .width(72.dp)
            .background(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent)))
            .systemBarsPadding()
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Avatar du profil en haut, comme sur les plateformes de streaming.
        Box(
            Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(IptvColors.Accent, IptvColors.AccentBright)))
                .clickable { onSelect(Tab.PROFILE) },
            contentAlignment = Alignment.Center,
        ) {
            Text(username.firstOrNull()?.uppercase() ?: "Z", fontWeight = FontWeight.Bold, color = Color.White)
        }
        Spacer(Modifier.height(12.dp))
        listOf(Tab.SEARCH, Tab.HOME, Tab.LIVE, Tab.MOVIES, Tab.SERIES, Tab.PROFILE).forEach { item ->
            val selected = item == tab
            IconButton(onClick = { onSelect(item) }) {
                Icon(
                    item.icon,
                    contentDescription = item.label,
                    tint = if (selected) IptvColors.TextPrimary else IptvColors.TextSecondary.copy(alpha = 0.7f),
                    modifier = Modifier.size(if (selected) 26.dp else 22.dp),
                )
            }
        }
    }
}
