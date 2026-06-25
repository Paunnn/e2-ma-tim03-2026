package com.tim03.slagalica.data.model

data class PartijaSession(
    val id: String = "",
    val player1Uid: String = "",
    val player2Uid: String = "",
    val player1Name: String = "",
    val player2Name: String = "",
    val playerUids: List<String> = emptyList(),
    val status: String = "active",
    val forfeitedBy: String = "",
    val player1GameScores: Map<String, Long> = emptyMap(),
    val player2GameScores: Map<String, Long> = emptyMap(),
    val isTournament: Boolean = false,
    val isFinal: Boolean = false,
    val isFriendly: Boolean = false,
    val turnirId: String = ""
)
