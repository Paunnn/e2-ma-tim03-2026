package com.tim03.slagalica.data.model

data class AsocijacijeQuestion(
    val id: String = "",
    val columnA: ColumnData = ColumnData(),
    val columnB: ColumnData = ColumnData(),
    val columnC: ColumnData = ColumnData(),
    val columnD: ColumnData = ColumnData(),
    val finalSolution: String = ""
) {
    fun columns(): List<ColumnData> = listOf(columnA, columnB, columnC, columnD)
}

data class ColumnData(
    val clues: List<String> = emptyList(),
    val solution: String = ""
)
