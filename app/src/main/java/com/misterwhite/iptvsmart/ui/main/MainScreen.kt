package com.misterwhite.iptvsmart.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
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
import com.misterwhite.iptvsmart.ui.components.ErrorBox
import com.misterwhite.iptvsmart.ui.components.LoadingBox
import com.misterwhite.iptvsmart.ui.theme.IptvColors

/** Actions de navigation partagées par les onglets. */
class MainActions(
    val openMovie: (String) -> Unit,
    val openSeries: (String) -> Unit,
    val playChannel: (String) -> Unit,
    val openAccount: () -> Unit,
)

private enum class Tab(val label: String, val icon: ImageVector) {
    HOME("Accueil", Icons.Filled.Home),
    LIVE("Direct", Icons.Filled.LiveTv),
    MOVIES("Films", Icons.Filled.Movie),
    SERIES("Séries", Icons.Filled.Tv),
    SEARCH("Recherche", Icons.Filled.Search),
}

@Composable
fun MainScreen(viewModel: MainViewModel, actions: MainActions) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var tabIndex by rememberSaveable { mutableIntStateOf(0) }
    val tab = Tab.entries[tabIndex]

    Scaffold(
        containerColor = IptvColors.Background,
        bottomBar = {
            NavigationBar(containerColor = IptvColors.Background.copy(alpha = 0.97f)) {
                Tab.entries.forEachIndexed { index, item ->
                    NavigationBarItem(
                        selected = index == tabIndex,
                        onClick = { tabIndex = index },
                        icon = { Icon(item.icon, contentDescription = item.label) },
                        label = { Text(item.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = IptvColors.TextPrimary,
                            selectedTextColor = IptvColors.TextPrimary,
                            unselectedIconColor = IptvColors.TextSecondary,
                            unselectedTextColor = IptvColors.TextSecondary,
                            indicatorColor = IptvColors.SurfaceHigh,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .background(IptvColors.Background)
                .padding(bottom = padding.calculateBottomPadding()),
        ) {
            when (val s = state) {
                CatalogState.Loading -> LoadingBox(message = "Chargement du catalogue…")
                is CatalogState.Error -> ErrorBox(s.message, onRetry = { viewModel.load(force = true) })
                is CatalogState.Ready -> when (tab) {
                    Tab.HOME -> HomeTab(s.content, actions)
                    Tab.LIVE -> LiveTab(s.content.live, actions)
                    Tab.MOVIES -> MoviesTab(s.content.movies, actions)
                    Tab.SERIES -> SeriesTab(s.content.series, actions)
                    Tab.SEARCH -> SearchTab(s.content, actions)
                }
            }
        }
    }
}
