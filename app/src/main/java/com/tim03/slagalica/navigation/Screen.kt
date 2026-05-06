package com.tim03.slagalica.navigation

sealed class Screen(val route: String) {
    object Login : Screen("login")
    object Register : Screen("register")
    object Home : Screen("home")
    object Profile : Screen("profile")
    object KorakPoKorak : Screen("game/korak_po_korak")
    object MojBroj : Screen("game/moj_broj")
    object KoZnaZna : Screen("game/ko_zna_zna")
    object Spojnice : Screen("game/spojnice")
}
