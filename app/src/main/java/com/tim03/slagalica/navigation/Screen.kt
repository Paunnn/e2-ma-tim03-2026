package com.tim03.slagalica.navigation

sealed class Screen(val route: String) {
    object Login : Screen("login")
    object Register : Screen("register")
    object Home : Screen("home")
    object KorakPoKorak : Screen("game/korak_po_korak")
    object MojBroj : Screen("game/moj_broj")
}
