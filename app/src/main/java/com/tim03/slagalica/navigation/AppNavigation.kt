package com.tim03.slagalica.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.tim03.slagalica.ui.auth.ChangePasswordScreen
import com.tim03.slagalica.ui.auth.LoginScreen
import com.tim03.slagalica.ui.auth.RegisterScreen
import com.tim03.slagalica.ui.chat.ChatScreen
import com.tim03.slagalica.ui.friends.FriendsScreen
import com.tim03.slagalica.ui.games.AsocijacijeScreen
import com.tim03.slagalica.ui.izazov.IzazovScreen
import com.tim03.slagalica.ui.leaderboard.LeaderboardScreen
import com.tim03.slagalica.ui.partija.FriendlyMatchmakingScreen
import com.tim03.slagalica.ui.partija.MatchmakingScreen
import com.tim03.slagalica.ui.partija.PartijaScreen
import com.tim03.slagalica.ui.games.KorakPoKorakScreen
import com.tim03.slagalica.ui.games.KoZnaZnaScreen
import com.tim03.slagalica.ui.games.MojBrojScreen
import com.tim03.slagalica.ui.games.SkockoScreen
import com.tim03.slagalica.ui.games.SpojniceScreen
import com.tim03.slagalica.ui.home.HomeScreen
import com.tim03.slagalica.ui.notifications.NotificationsScreen
import com.tim03.slagalica.ui.profile.ProfileScreen
import com.tim03.slagalica.ui.region.RegionScreen
import com.tim03.slagalica.ui.turnir.TurnirScreen
import com.tim03.slagalica.viewmodel.HomeViewModel
import com.tim03.slagalica.viewmodel.NotificationsViewModel
import com.tim03.slagalica.viewmodel.TurnirViewModel

@Composable
fun AppNavigation(navController: NavHostController) {
    val notifViewModel: NotificationsViewModel = viewModel()
    val unreadCount by notifViewModel.unreadCount.collectAsState()
    val homeViewModel: HomeViewModel = viewModel()

    NavHost(navController = navController, startDestination = Screen.Login.route) {

        composable(Screen.Login.route) {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                },
                onRegisterClick = { navController.navigate(Screen.Register.route) }
            )
        }

        composable(Screen.Register.route) {
            RegisterScreen(
                onRegisterSuccess = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.Register.route) { inclusive = true }
                    }
                },
                onBackClick = { navController.popBackStack() }
            )
        }

        composable(Screen.Home.route) {
            HomeScreen(
                onKorakPoKorakClick = { navController.navigate(Screen.KorakPoKorak.route) },
                onMojBrojClick = { navController.navigate(Screen.MojBroj.route) },
                onKoZnaZnaClick = { navController.navigate(Screen.KoZnaZna.route) },
                onSpojniceClick = { navController.navigate(Screen.Spojnice.route) },
                onAsocijacijeClick = { navController.navigate(Screen.Asocijacije.route) },
                onSkockoClick = { navController.navigate(Screen.Skocko.route) },
                onPartijaClick = { navController.navigate(Screen.Matchmaking.route) },
                onFriendlyClick = { navController.navigate(Screen.FriendlyMatchmaking.route) },
                onProfileClick = { navController.navigate(Screen.Profile.route) },
                onNotificationsClick = { navController.navigate(Screen.Notifications.route) },
                onLeaderboardClick = { navController.navigate(Screen.Leaderboard.route) },
                onTurnirClick = { navController.navigate(Screen.Turnir.route) },
                onChatClick = { navController.navigate(Screen.Chat.route) },
                onIzazovClick = { navController.navigate(Screen.Izazov.route) },
                onMapClick = { navController.navigate(Screen.Region.route) },
                onFriendsClick = { navController.navigate(Screen.Friends.route) },
                onLogout = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                unreadNotifCount = unreadCount,
                homeViewModel = homeViewModel,
                onNavigateToPartija = { sessionId, isPlayer1 ->
                    navController.navigate(Screen.Partija.createRoute(sessionId, isPlayer1))
                }
            )
        }

        composable(Screen.Matchmaking.route) {
            MatchmakingScreen(
                onMatchFound = { sessionId, isPlayer1 ->
                    navController.navigate(Screen.Partija.createRoute(sessionId, isPlayer1)) {
                        popUpTo(Screen.Matchmaking.route) { inclusive = true }
                    }
                },
                onCancel = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.Partija.route,
            arguments = listOf(
                navArgument("sessionId") { type = NavType.StringType },
                navArgument("isPlayer1") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val sessionId = backStackEntry.arguments?.getString("sessionId") ?: ""
            val isPlayer1 = backStackEntry.arguments?.getString("isPlayer1") == "true"
            PartijaScreen(
                sessionId = sessionId,
                isPlayer1 = isPlayer1,
                onExit = { navController.popBackStack() }
            )
        }

        composable(Screen.Profile.route) {
            ProfileScreen(
                onNavigateHome = { navController.navigate(Screen.Home.route) { popUpTo(Screen.Home.route) { inclusive = true } } },
                onLeaderboardClick = { navController.navigate(Screen.Leaderboard.route) },
                onMapClick = { navController.navigate(Screen.Region.route) },
                onFriendsClick = { navController.navigate(Screen.Friends.route) },
                onLogout = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onChangePassword = { navController.navigate(Screen.ChangePassword.route) }
            )
        }

        composable(Screen.ChangePassword.route) {
            ChangePasswordScreen(onBack = { navController.popBackStack() })
        }

        composable(Screen.Notifications.route) {
            NotificationsScreen(onBackClick = { navController.popBackStack() })
        }

        composable(Screen.Leaderboard.route) {
            LeaderboardScreen(onBack = { navController.popBackStack() })
        }

        composable(Screen.Turnir.route) {
            val turnirVm: TurnirViewModel = viewModel()
            TurnirScreen(
                vm = turnirVm,
                onNavigateToPartija = { sessionId, isPlayer1, isTournament, isFinal ->
                    navController.navigate(Screen.Partija.createRoute(sessionId, isPlayer1))
                },
                onExit = { navController.popBackStack() }
            )
        }

        composable(Screen.Chat.route) {
            ChatScreen(onBack = { navController.popBackStack() })
        }

        composable(Screen.Izazov.route) {
            IzazovScreen(
                onBack = { navController.popBackStack() },
                onPlayIzazov = { izazovId ->
                    navController.navigate(Screen.IzazovPartija.createRoute(izazovId))
                }
            )
        }

        composable(
            route = Screen.IzazovPartija.route,
            arguments = listOf(navArgument("izazovId") { type = NavType.StringType })
        ) { backStackEntry ->
            val izazovId = backStackEntry.arguments?.getString("izazovId") ?: ""
            // Solo partija (no sessionId): every game plays once; total goes to the izazov.
            PartijaScreen(
                sessionId = "",
                izazovId = izazovId,
                onExit = { navController.popBackStack() }
            )
        }

        composable(Screen.FriendlyMatchmaking.route) {
            FriendlyMatchmakingScreen(
                onMatchReady = { sessionId, isPlayer1 ->
                    navController.navigate(Screen.Partija.createRoute(sessionId, isPlayer1)) {
                        popUpTo(Screen.FriendlyMatchmaking.route) { inclusive = true }
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.KorakPoKorak.route) {
            KorakPoKorakScreen(onExitClick = { navController.popBackStack() })
        }

        composable(Screen.MojBroj.route) {
            MojBrojScreen(onExitClick = { navController.popBackStack() })
        }

        composable(Screen.KoZnaZna.route) {
            KoZnaZnaScreen(onExitClick = { navController.popBackStack() })
        }

        composable(Screen.Spojnice.route) {
            SpojniceScreen(onExitClick = { navController.popBackStack() })
        }

        composable(Screen.Asocijacije.route) {
            AsocijacijeScreen(onExitClick = { navController.popBackStack() })
        }

        composable(Screen.Skocko.route) {
            SkockoScreen(onExitClick = { navController.popBackStack() })
        }

        composable(Screen.Region.route) {
            RegionScreen(
                onHomeClick = { navController.navigate(Screen.Home.route) { launchSingleTop = true } },
                onLeaderboardClick = { navController.navigate(Screen.Leaderboard.route) },
                onFriendsClick = { navController.navigate(Screen.Friends.route) { launchSingleTop = true } },
                onProfileClick = { navController.navigate(Screen.Profile.route) },
                onPlayIzazov = { izazovId ->
                    navController.navigate(Screen.IzazovPartija.createRoute(izazovId))
                }
            )
        }

        composable(Screen.Friends.route) {
            FriendsScreen(
                onHomeClick = { navController.navigate(Screen.Home.route) { launchSingleTop = true } },
                onLeaderboardClick = { navController.navigate(Screen.Leaderboard.route) },
                onMapClick = { navController.navigate(Screen.Region.route) { launchSingleTop = true } },
                onProfileClick = { navController.navigate(Screen.Profile.route) },
                onStartFriendlyMatch = { _, _ ->
                    navController.navigate(Screen.FriendlyMatchmaking.route)
                }
            )
        }
    }
}
