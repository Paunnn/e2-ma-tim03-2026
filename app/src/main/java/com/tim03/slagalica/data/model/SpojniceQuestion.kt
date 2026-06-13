package com.tim03.slagalica.data.model

data class SpojniceQuestion(
    val id: String = "",
    val criterion: String = "",
    val leftItems: List<String> = emptyList(),
    val rightItems: List<String> = emptyList(),
    val correctMapping: List<Int> = emptyList()
)
