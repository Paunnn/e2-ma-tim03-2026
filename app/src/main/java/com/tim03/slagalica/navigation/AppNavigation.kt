package com.tim03.slagalica.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.tim03.slagalica.ui.auth.LoginScreen
import com.tim03.slagalica.ui.auth.RegisterScreen
import com.tim03.slagalica.ui.games.KorakPoKorakScreen
import com.tim03.slagalica.ui.games.MojBrojScreen
import com.tim03.slagalica.ui.home.HomeScreen

@Composable
fun AppNavigation(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = Screen.Login.route
    ) {
        composable(Screen.Login.route) {
            LoginScreen(
                onLoginClick = { navController.navigate(Screen.Home.route) {
                    popUpTo(Screen.Login.route) { inclusive = true }
                }},
                onRegisterClick = { navController.navigate(Screen.Register.route) },
                onGuestClick = { navController.navigate(Screen.Home.route) {
                    popUpTo(Screen.Login.route) { inclusive = true }
                }}
            )
        }
        composable(Screen.Register.route) {
            RegisterScreen(
                onRegisterSuccess = { navController.navigate(Screen.Login.route) {
                    popUpTo(Screen.Register.route) { inclusive = true }
                }},
                onBackClick = { navController.popBackStack() }
            )
        }
        composable(Screen.Home.route) {
            HomeScreen(
                onKorakPoKorakClick = { navController.navigate(Screen.KorakPoKorak.route) },
                onMojBrojClick = { navController.navigate(Screen.MojBroj.route) }
            )
        }
        composable(Screen.KorakPoKorak.route) {
            KorakPoKorakScreen(
                onExitClick = { navController.popBackStack() }
            )
        }
        composable(Screen.MojBroj.route) {
            MojBrojScreen(
                onExitClick = { navController.popBackStack() }
            )
        }
    }
}
