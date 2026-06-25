package com.tim03.slagalica.data.model

data class Friendship(
    val id: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val receiverId: String = "",
    val receiverName: String = "",
    val status: String = "pending", // "pending", "accepted"
    val createdAt: Long = 0L
)

data class FriendInfo(
    val uid: String = "",
    val username: String = "",
    val stars: Int = 0,
    val league: Int = 0,
    val avatarIndex: Int = 0,
    val monthlyStars: Int = 0,
    val region: String = ""
)
