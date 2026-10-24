package com.misterwhite.iptvsmart

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.misterwhite.iptvsmart.ui.home.HomeScreen
import com.misterwhite.iptvsmart.ui.home.HomeViewModel
import com.misterwhite.iptvsmart.ui.login.LoginScreen
import com.misterwhite.iptvsmart.ui.login.LoginViewModel
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
    const val HOME = "home"
}

@Composable
private fun AppNavigation(container: AppContainer) {
    val navController = rememberNavController()
    val start = if (container.credentialsStore.load() != null) Routes.HOME else Routes.LOGIN

    NavHost(navController = navController, startDestination = start) {
        composable(Routes.LOGIN) {
            val vm: LoginViewModel = viewModel(
                factory = viewModelFactory {
                    initializer { LoginViewModel(container.xtreamClient, container.credentialsStore) }
                }
            )
            LoginScreen(vm, onLoggedIn = {
                navController.navigate(Routes.HOME) {
                    popUpTo(Routes.LOGIN) { inclusive = true }
                }
            })
        }
        composable(Routes.HOME) {
            val vm: HomeViewModel = viewModel(
                factory = viewModelFactory {
                    initializer { HomeViewModel(container.xtreamClient, container.credentialsStore) }
                }
            )
            HomeScreen(vm, onLoggedOut = {
                navController.navigate(Routes.LOGIN) {
                    popUpTo(Routes.HOME) { inclusive = true }
                }
            })
        }
    }
}
