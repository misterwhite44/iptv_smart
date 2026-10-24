package com.misterwhite.iptvsmart

import android.graphics.Color
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.misterwhite.iptvsmart.data.FavoriteEntry
import com.misterwhite.iptvsmart.data.LibraryRepository
import com.misterwhite.iptvsmart.data.MediaKind
import com.misterwhite.iptvsmart.player.Playback
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.misterwhite.iptvsmart.ui.account.AccountScreen
import com.misterwhite.iptvsmart.ui.account.AccountViewModel
import com.misterwhite.iptvsmart.ui.detail.MovieDetailScreen
import com.misterwhite.iptvsmart.ui.detail.MovieDetailViewModel
import com.misterwhite.iptvsmart.ui.detail.SeriesDetailScreen
import com.misterwhite.iptvsmart.ui.detail.SeriesDetailViewModel
import com.misterwhite.iptvsmart.ui.login.LoginScreen
import com.misterwhite.iptvsmart.ui.login.LoginViewModel
import com.misterwhite.iptvsmart.ui.main.MainActions
import com.misterwhite.iptvsmart.ui.main.MainScreen
import com.misterwhite.iptvsmart.ui.main.MainViewModel
import com.misterwhite.iptvsmart.ui.theme.IptvTheme

// FragmentActivity : le choix de la Chromecast s'ouvre dans une boîte de dialogue à fragments.
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        val container = (application as IptvApp).container
        setContent {
            IptvTheme {
                AppNavigation(container)
            }
        }
    }
}

private object Routes {
    const val LOGIN = "login"
    const val MAIN = "main"
    const val ACCOUNT = "account"
    const val MOVIE = "movie/{id}"
    const val SERIES = "series/{id}"

    fun movie(id: String) = "movie/$id"
    fun series(id: String) = "series/$id"
}

@Composable
private fun AppNavigation(container: AppContainer) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val playback = remember { Playback(context, container) }
    val library = container.library
    val start = remember { if (container.credentialsStore.load() != null) Routes.MAIN else Routes.LOGIN }

    NavHost(navController = navController, startDestination = start) {
        composable(Routes.LOGIN) {
            val vm: LoginViewModel = viewModel(
                factory = viewModelFactory {
                    initializer { LoginViewModel(container.xtreamClient, container.credentialsStore) }
                }
            )
            LoginScreen(vm, onLoggedIn = {
                navController.navigate(Routes.MAIN) {
                    popUpTo(Routes.LOGIN) { inclusive = true }
                }
            })
        }
        composable(Routes.MAIN) {
            val vm: MainViewModel = viewModel(
                factory = viewModelFactory { initializer { MainViewModel(container.catalog) } }
            )
            MainScreen(
                vm,
                library,
                MainActions(
                    openMovie = { navController.navigate(Routes.movie(it)) },
                    openSeries = { navController.navigate(Routes.series(it)) },
                    playChannel = playback::playChannel,
                    openAccount = { navController.navigate(Routes.ACCOUNT) },
                    resume = { entry ->
                        when (entry.kind) {
                            MediaKind.MOVIE -> playback.resumeMovie(entry)
                            MediaKind.EPISODE -> entry.seriesId?.let { navController.navigate(Routes.series(it)) }
                            MediaKind.LIVE -> playback.playChannel(entry.contentId)
                            MediaKind.SERIES -> navController.navigate(Routes.series(entry.contentId))
                        }
                    },
                ),
            )
        }
        composable(Routes.MOVIE) { entry ->
            val id = entry.arguments?.getString("id").orEmpty()
            val vm: MovieDetailViewModel = viewModel(
                factory = viewModelFactory { initializer { MovieDetailViewModel(container.catalog, id) } }
            )
            val history by library.history.collectAsStateWithLifecycle()
            val favorites by library.favorites.collectAsStateWithLifecycle()
            val key = LibraryRepository.movieKey(id)
            val watched = history.firstOrNull { it.key == key }?.takeIf { it.isResumable }
            MovieDetailScreen(
                vm,
                onBack = { navController.popBackStack() },
                onPlay = playback::playMovie,
                progress = watched?.progress,
                isFavorite = favorites.any { it.key == key },
                onToggleFavorite = { d ->
                    library.toggleFavorite(
                        FavoriteEntry(key, MediaKind.MOVIE, d.movie.id, d.movie.name, d.movie.poster, System.currentTimeMillis())
                    )
                },
            )
        }
        composable(Routes.SERIES) { entry ->
            val id = entry.arguments?.getString("id").orEmpty()
            val vm: SeriesDetailViewModel = viewModel(
                factory = viewModelFactory { initializer { SeriesDetailViewModel(container.catalog, id) } }
            )
            val history by library.history.collectAsStateWithLifecycle()
            val favorites by library.favorites.collectAsStateWithLifecycle()
            val key = LibraryRepository.seriesKey(id)
            val seriesHistory = history.filter { it.kind == MediaKind.EPISODE && it.seriesId == id }
            SeriesDetailScreen(
                vm,
                onBack = { navController.popBackStack() },
                onPlayEpisode = playback::playEpisode,
                episodeProgress = seriesHistory.mapNotNull { h -> h.progress?.let { h.contentId to it } }.toMap(),
                resumeEpisodeId = seriesHistory.firstOrNull { it.isResumable }?.contentId,
                isFavorite = favorites.any { it.key == key },
                onToggleFavorite = { d ->
                    library.toggleFavorite(
                        FavoriteEntry(key, MediaKind.SERIES, d.series.id, d.series.name, d.series.cover, System.currentTimeMillis())
                    )
                },
            )
        }
        composable(Routes.ACCOUNT) {
            val vm: AccountViewModel = viewModel(
                factory = viewModelFactory {
                    initializer {
                        AccountViewModel(container.xtreamClient, container.credentialsStore, container.catalog, library)
                    }
                }
            )
            AccountScreen(
                vm,
                onBack = { navController.popBackStack() },
                onLoggedOut = { navController.logoutTo(Routes.LOGIN) },
            )
        }
    }
}

private fun NavHostController.logoutTo(route: String) {
    navigate(route) {
        popUpTo(graph.id) { inclusive = true }
    }
}
