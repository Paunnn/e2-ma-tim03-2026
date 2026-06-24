package com.tim03.slagalica.data.model

data class LeaderboardEntry(
    val uid: String = "",
    val username: String = "",
    val stars: Int = 0,
    val weeklyStars: Int = 0,
    val monthlyStars: Int = 0,
    val league: Int = 0,
    val avatarIndex: Int = 0,
    val region: String = "",
    val rank: Int = 0
)
