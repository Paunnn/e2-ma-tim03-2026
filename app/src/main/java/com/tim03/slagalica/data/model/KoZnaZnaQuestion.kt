package com.tim03.slagalica.data.model

data class KoZnaZnaQuestion(
    val id: String = "",
    val question: String = "",
    val answers: List<String> = emptyList(),
    val correctIndex: Int = 0
)
