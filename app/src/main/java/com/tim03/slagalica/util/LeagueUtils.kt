package com.tim03.slagalica.util

// Spec 6a: league names and icons. League 0 is the starting league; each next
// league requires double the total stars of the previous one (100, 200, 400, ...).
// Single source of truth - screens must not keep their own (diverging) mappings.

fun leagueName(league: Int): String = when (league) {
    0 -> "Početnik"
    1 -> "Bronza"
    2 -> "Srebro"
    3 -> "Zlato"
    4 -> "Platina"
    5 -> "Dijamant"
    else -> "Liga $league"
}

fun leagueIcon(league: Int): String = when (league) {
    0 -> "🌱"
    1 -> "🥉"
    2 -> "🥈"
    3 -> "🥇"
    4 -> "💠"
    else -> "💎"
}
