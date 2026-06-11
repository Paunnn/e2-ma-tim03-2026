package com.tim03.slagalica.data.model

data class User(
    val uid: String = "",
    val email: String = "",
    val username: String = "",
    val region: String = "",
    val tokens: Int = 5,
    val stars: Int = 0,
    val league: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)
