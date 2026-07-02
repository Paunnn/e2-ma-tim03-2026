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
    val status: String = "open", // "open", "finalizing", "completed"
    val winnerId: String = "",
    val runnerId: String = "",
    val createdAt: Long = 0L,
    // Join/play deadline: after this the challenge finalizes with whoever played.
    val expiresAt: Long = 0L
) {
    fun hasPlayed(uid: String) = scores.containsKey(uid)
    fun isFull() = participants.size >= 4
    fun isExpired() = expiresAt in 1..System.currentTimeMillis()
    fun allPlayed() = participants.all { scores.containsKey(it) }
    fun canJoin(uid: String) = status == "open" && !isExpired() && !participants.contains(uid) && !isFull()
}
