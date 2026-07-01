package com.tim03.slagalica.data.model

data class IzazovSession(
    val id: String = "",
    val posterId: String = "",
    val posterName: String = "",
    val region: String = "",
    val bidStars: Int = 0,
    val bidTokens: Int = 0,
    val participants: List<String> = emptyList(),
    val participantNames: List<String> = emptyList(),
    val scores: Map<String, Long> = emptyMap(),
    val status: String = "open", // "open", "completed"
    val winnerId: String = "",
    val runnerId: String = "",
    val createdAt: Long = 0L
) {
    fun hasPlayed(uid: String) = scores.containsKey(uid)
    fun isFull() = participants.size >= 4
    fun canJoin(uid: String) = status == "open" && !participants.contains(uid) && !isFull()
}
