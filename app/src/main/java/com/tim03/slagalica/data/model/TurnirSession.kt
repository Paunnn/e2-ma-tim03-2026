package com.tim03.slagalica.data.model

data class TurnirSession(
    val id: String = "",
    val playerUids: List<String> = emptyList(),
    val playerNames: List<String> = emptyList(),
    val playerLeagues: List<Int> = emptyList(),
    val playerAvatarIndices: List<Int> = emptyList(),
    val status: String = "waiting",  // waiting | semifinal | final | completed
    val semi1SessionId: String = "",
    val semi2SessionId: String = "",
    val finalSessionId: String = "",
    val semi1Winner: String = "",
    val semi2Winner: String = "",
    val tournamentWinner: String = "",
    val createdAt: Long = 0L
) {
    fun semi1Player1Uid() = playerUids.getOrElse(0) { "" }
    fun semi1Player2Uid() = playerUids.getOrElse(1) { "" }
    fun semi2Player1Uid() = playerUids.getOrElse(2) { "" }
    fun semi2Player2Uid() = playerUids.getOrElse(3) { "" }
}
