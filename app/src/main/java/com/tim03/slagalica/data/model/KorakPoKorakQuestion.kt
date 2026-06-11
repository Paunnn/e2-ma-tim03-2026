package com.tim03.slagalica.data.model

data class KorakPoKorakQuestion(
    val id: String = "",
    val answer: String = "",
    val steps: List<String> = emptyList()
)
