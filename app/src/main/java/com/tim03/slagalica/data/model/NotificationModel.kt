package com.tim03.slagalica.data.model

data class NotificationModel(
    val id: String = "",
    val userId: String = "",
    val channel: String = "OTHER",
    val title: String = "",
    val message: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val isRead: Boolean = false
)
