package com.tim03.slagalica.navigation

sealed class Screen(val route: String) {
    object Login : Screen("login")
    object Register : Screen("register")
    object Home : Screen("home")
    object Profile : Screen("profile")
    object ChangePassword : Screen("change_password")
    object KorakPoKorak : Screen("game/korak_po_korak")
    object MojBroj : Screen("game/moj_broj")
    object KoZnaZna : Screen("game/ko_zna_zna")
    object Spojnice : Screen("game/spojnice")
    object Asocijacije : Screen("game/asocijacije")
    object Skocko : Screen("game/skocko")
    object Matchmaking : Screen("matchmaking")
    object Partija : Screen("partija/{sessionId}/{isPlayer1}") {
        fun createRoute(sessionId: String, isPlayer1: Boolean) = "partija/$sessionId/$isPlayer1"
    }
    object Notifications : Screen("notifications")
    object Leaderboard : Screen("leaderboard")
    object Turnir : Screen("turnir")
    object Chat : Screen("chat")
    object Izazov : Screen("izazov")
    object IzazovPartija : Screen("izazov_partija/{izazovId}") {
        fun createRoute(izazovId: String) = "izazov_partija/$izazovId"
    }
    // Optional args: opened from the friends list they preselect the friend and the
    // invite is sent immediately; opened without them (from Home) the user searches.
    object FriendlyMatchmaking : Screen("friendly_matchmaking?friendUid={friendUid}&friendName={friendName}") {
        fun createRoute(friendUid: String, friendName: String) =
            "friendly_matchmaking?friendUid=$friendUid&friendName=${android.net.Uri.encode(friendName)}"
    }
    object Region : Screen("region")
    object Friends : Screen("friends")
}
