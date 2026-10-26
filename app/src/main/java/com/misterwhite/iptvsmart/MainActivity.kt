package com.misterwhite.iptvsmart

import android.graphics.Color
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.misterwhite.iptvsmart.data.FavoriteEntry
import com.misterwhite.iptvsmart.data.LibraryRepository
import com.misterwhite.iptvsmart.data.MediaKind
import com.misterwhite.iptvsmart.player.Playback
import com.misterwhite.iptvsmart.cast.CastSupport
import com.google.android.gms.cast.framework.CastState
import com.google.android.gms.cast.framework.CastStateListener
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.misterwhite.iptvsmart.ui.profile.ProfileViewModel
import com.misterwhite.iptvsmart.ui.detail.MovieDetailScreen
import com.misterwhite.iptvsmart.ui.detail.MovieDetailViewModel
import com.misterwhite.iptvsmart.ui.detail.SeriesDetailScreen
import com.misterwhite.iptvsmart.ui.detail.SeriesDetailViewModel
import com.misterwhite.iptvsmart.ui.account.AccountScreen
import com.misterwhite.iptvsmart.ui.account.AccountViewModel
import com.misterwhite.iptvsmart.ui.login.LoginScreen
import com.misterwhite.iptvsmart.ui.login.LoginViewModel
import com.misterwhite.iptvsmart.ui.main.MainActions
import com.misterwhite.iptvsmart.ui.main.MainScreen
import com.misterwhite.iptvsmart.ui.main.MainViewModel
import com.misterwhite.iptvsmart.ui.premium.LocalPremium
import com.misterwhite.iptvsmart.ui.theme.IptvTheme
import kotlinx.coroutines.launch

// FragmentActivity : le choix de la Chromecast s'ouvre dans une boîte de dialogue à fragments.
class MainActivity : FragmentActivity() {
    private var lastCastState = CastState.NO_DEVICES_AVAILABLE

    // Connecté depuis l'accueil, la télé n'a encore rien à lire : on l'explique.
    private val castStateListener = CastStateListener { state ->
        if (state == CastState.CONNECTED && lastCastState != CastState.CONNECTED) {
            Toast.makeText(this, "Chromecast connectée : choisis un film, un épisode ou une chaîne.", Toast.LENGTH_LONG).show()
        }
        lastCastState = state
    }

    override fun onResume() {
        super.onResume()
        // Un achat ou une résiliation a pu avoir lieu hors de l'appli.
        (application as IptvApp).container.premium.billing.refresh()
        CastSupport.castContext(this)?.let {
            lastCastState = it.castState
            it.addCastStateListener(castStateListener)
        }
    }

    override fun onPause() {
        CastSupport.castContext(this)?.removeCastStateListener(castStateListener)
        super.onPause()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        val container = (application as IptvApp).container
        setContent {
            CompositionLocalProvider(LocalPremium provides container.premium) {
                IptvTheme {
                    AppNavigation(container)
                    DisclaimerDialog()
                    CrashDialog()
                }
            }
        }
    }
}

/** Rappelle au premier lancement que Zynora n'est qu'un lecteur (exigé pour le Play Store). */
@Composable
private fun DisclaimerDialog() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("app", android.content.Context.MODE_PRIVATE) }
    var show by remember { mutableStateOf(!prefs.getBoolean(KEY_DISCLAIMER_ACCEPTED, false)) }
    if (!show) return
    AlertDialog(
        onDismissRequest = {},
        title = { Text("Bienvenue sur Zynora") },
        text = {
            Text(
                "Zynora est un lecteur : il ne fournit aucune chaîne, aucun film ni aucune série. " +
                    "Tu te connectes avec ton propre service, et tu dois l'utiliser uniquement " +
                    "s'il te donne légalement accès à ces contenus."
            )
        },
        confirmButton = {
            TextButton(onClick = {
                prefs.edit().putBoolean(KEY_DISCLAIMER_ACCEPTED, true).apply()
                show = false
            }) { Text("J'ai compris") }
        },
    )
}

private const val KEY_DISCLAIMER_ACCEPTED = "disclaimer_accepted_v1"

/** Montre la cause du dernier plantage, avec un bouton pour la copier. */
@Composable
private fun CrashDialog() {
    val context = LocalContext.current
    var crash by remember { mutableStateOf(CrashReporter.lastCrash(context)) }
    val report = crash ?: return
    val clipboard = LocalClipboardManager.current
    val dismiss = {
        CrashReporter.clear(context)
        crash = null
    }
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text("L'appli s'est fermée") },
        text = {
            Column {
                Text("Copie ce rapport et envoie-le pour qu'on corrige le problème.")
                Spacer(Modifier.height(12.dp))
                Text(
                    report,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .heightIn(max = 280.dp)
                        .verticalScroll(rememberScrollState()),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                clipboard.setText(AnnotatedString(report))
                Toast.makeText(context, "Rapport copié", Toast.LENGTH_SHORT).show()
                dismiss()
            }) { Text("Copier") }
        },
        dismissButton = { TextButton(onClick = dismiss) { Text("Fermer") } },
    )
}

private object Routes {
    const val ACCOUNT = "account"
    const val LOGIN = "login"
    const val MAIN = "main"
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
    val scope = rememberCoroutineScope()
    val library = container.library
    val account = container.account
    // Après le compte Zynora : l'écran du fournisseur, ou l'accueil s'il est déjà enregistré.
    val afterAccount = { if (container.credentialsStore.load() != null) Routes.MAIN else Routes.LOGIN }
    val start = remember { if (account.needsSignIn) Routes.ACCOUNT else afterAccount() }

    NavHost(navController = navController, startDestination = start) {
        composable(Routes.ACCOUNT) {
            val vm: AccountViewModel = viewModel(
                factory = viewModelFactory { initializer { AccountViewModel(account) } }
            )
            val user by account.user.collectAsStateWithLifecycle()
            LaunchedEffect(user) {
                if (user != null && !account.needsSignIn) navController.logoutTo(afterAccount())
            }
            AccountScreen(vm)
        }
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
                factory = viewModelFactory { initializer { MainViewModel(container.catalog, library) } }
            )
            val profileVm: ProfileViewModel = viewModel(
                factory = viewModelFactory {
                    initializer {
                        ProfileViewModel(
                            container.xtreamClient,
                            container.credentialsStore,
                            container.catalog,
                            library,
                            account,
                            container.favoritesSync,
                        )
                    }
                }
            )
            MainScreen(
                vm,
                profileVm,
                library,
                MainActions(
                    openMovie = { navController.navigate(Routes.movie(it)) },
                    openSeries = { navController.navigate(Routes.series(it)) },
                    playChannel = playback::playChannel,
                    playMovie = playback::playMovie,
                    resume = { entry ->
                        when (entry.kind) {
                            MediaKind.MOVIE -> playback.resumeMovie(entry)
                            MediaKind.EPISODE -> scope.launch { playback.resumeEpisode(entry) }
                            MediaKind.LIVE -> playback.playChannel(entry.contentId)
                            MediaKind.SERIES -> navController.navigate(Routes.series(entry.contentId))
                        }
                    },
                    forget = { entry -> library.removeFromHistory(entry.key) },
                ),
                onLoggedOut = { navController.logoutTo(if (account.needsSignIn) Routes.ACCOUNT else Routes.LOGIN) },
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
    }
}

private fun NavHostController.logoutTo(route: String) {
    navigate(route) {
        popUpTo(graph.id) { inclusive = true }
    }
}
