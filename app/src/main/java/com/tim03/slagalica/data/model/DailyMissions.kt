package com.tim03.slagalica.data.model

data class DailyMissions(
    val userId: String = "",
    val date: String = "",
    val winGame: Boolean = false,
    val sendMessage: Boolean = false,
    val playFriendly: Boolean = false,
    val winTournament: Boolean = false,
    val bonusCollected: Boolean = false
) {
    fun completedCount(): Int =
        listOf(winGame, sendMessage, playFriendly, winTournament).count { it }

    fun allCompleted(): Boolean =
        winGame && sendMessage && playFriendly && winTournament
}
