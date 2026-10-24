package com.misterwhite.iptvsmart

import android.graphics.Color
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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

class MainActivity : ComponentActivity() {
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
    val start = remember { if (container.credentialsStore.load() != null) Routes.MAIN else Routes.LOGIN }
    val comingSoon = {
        Toast.makeText(context, "Le lecteur arrive à la prochaine étape.", Toast.LENGTH_SHORT).show()
    }

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
                MainActions(
                    openMovie = { navController.navigate(Routes.movie(it)) },
                    openSeries = { navController.navigate(Routes.series(it)) },
                    playChannel = { comingSoon() },
                    openAccount = { navController.navigate(Routes.ACCOUNT) },
                ),
            )
        }
        composable(Routes.MOVIE) { entry ->
            val id = entry.arguments?.getString("id").orEmpty()
            val vm: MovieDetailViewModel = viewModel(
                factory = viewModelFactory { initializer { MovieDetailViewModel(container.catalog, id) } }
            )
            MovieDetailScreen(vm, onBack = { navController.popBackStack() }, onPlay = { comingSoon() })
        }
        composable(Routes.SERIES) { entry ->
            val id = entry.arguments?.getString("id").orEmpty()
            val vm: SeriesDetailViewModel = viewModel(
                factory = viewModelFactory { initializer { SeriesDetailViewModel(container.catalog, id) } }
            )
            SeriesDetailScreen(vm, onBack = { navController.popBackStack() }, onPlayEpisode = { _, _ -> comingSoon() })
        }
        composable(Routes.ACCOUNT) {
            val vm: AccountViewModel = viewModel(
                factory = viewModelFactory {
                    initializer { AccountViewModel(container.xtreamClient, container.credentialsStore, container.catalog) }
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
