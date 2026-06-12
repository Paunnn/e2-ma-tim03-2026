package com.tim03.slagalica.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tim03.slagalica.data.model.AsocijacijeQuestion
import com.tim03.slagalica.data.model.ColumnData
import com.tim03.slagalica.data.repository.AsocijacijeRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class AsocijacijePhase {
    LOADING, MY_TURN, OPPONENT_TURN, GAME_OVER
}

data class AsocijacijeUiState(
    val phase: AsocijacijePhase = AsocijacijePhase.LOADING,
    val currentQuestion: AsocijacijeQuestion? = null,
    val revealed: List<List<Boolean>> = List(4) { List(4) { false } },
    val columnSolved: List<Boolean> = List(4) { false },
    val finalSolved: Boolean = false,
    val currentRound: Int = 1,
    val timeLeft: Int = 120,
    val myScore: Int = 0,
    val opponentScore: Int = 0,
    val message: String? = null,
    val isMyTurn: Boolean = true,
    // Per spec: player reveals one field then CAN guess before passing turn
    val hasRevealedThisTurn: Boolean = false
)

class AsocijacijeViewModel(
    private val repo: AsocijacijeRepository = AsocijacijeRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(AsocijacijeUiState())
    val uiState: StateFlow<AsocijacijeUiState> = _uiState.asStateFlow()

    private var timerJob: Job? = null
    private var opponentJob: Job? = null
    private var round1Question: AsocijacijeQuestion? = null
    private var round2Question: AsocijacijeQuestion? = null

    init {
        loadGame()
    }

    private fun loadGame() {
        viewModelScope.launch {
            _uiState.value = AsocijacijeUiState(phase = AsocijacijePhase.LOADING)
            val (q1, q2) = repo.getTwoRandomQuestions()
            round1Question = q1 ?: fallbackQuestion(1)
            round2Question = q2 ?: fallbackQuestion(2)
            startRound(1)
        }
    }

    private fun startRound(round: Int) {
        timerJob?.cancel()
        opponentJob?.cancel()
        val question = if (round == 1) round1Question else round2Question
        val myTurnFirst = (round == 1)
        _uiState.value = AsocijacijeUiState(
            phase = if (myTurnFirst) AsocijacijePhase.MY_TURN else AsocijacijePhase.OPPONENT_TURN,
            currentQuestion = question,
            currentRound = round,
            myScore = _uiState.value.myScore,
            opponentScore = _uiState.value.opponentScore,
            isMyTurn = myTurnFirst
        )
        startTimer(120)
        if (!myTurnFirst) {
            scheduleOpponentTurn()
        }
    }

    private fun startTimer(seconds: Int) {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            var t = seconds
            while (t > 0) {
                delay(1000L)
                t--
                _uiState.value = _uiState.value.copy(timeLeft = t)
            }
            onTimerExpired()
        }
    }

    private fun onTimerExpired() {
        if (_uiState.value.currentRound == 1) {
            timerJob?.cancel()
            opponentJob?.cancel()
            viewModelScope.launch {
                delay(500L)
                startRound(2)
            }
        } else {
            endGame()
        }
    }

    // Player reveals a field — stays in MY_TURN so they can guess afterwards (per spec)
    fun revealField(col: Int, row: Int) {
        val state = _uiState.value
        if (!state.isMyTurn || state.phase != AsocijacijePhase.MY_TURN) return
        if (state.revealed[col][row] || state.columnSolved[col]) return
        if (state.hasRevealedThisTurn) return  // one reveal per turn

        val newRevealed = state.revealed.mapIndexed { c, rows ->
            rows.mapIndexed { r, v -> if (c == col && r == row) true else v }
        }
        _uiState.value = state.copy(
            revealed = newRevealed,
            hasRevealedThisTurn = true,
            message = null
        )
        // Stay in MY_TURN — player can now guess or skip their turn
    }

    // Player explicitly passes their turn without guessing
    fun passTurn() {
        if (!_uiState.value.isMyTurn) return
        passToOpponent()
    }

    private fun passToOpponent() {
        _uiState.value = _uiState.value.copy(
            phase = AsocijacijePhase.OPPONENT_TURN,
            isMyTurn = false,
            hasRevealedThisTurn = false
        )
        scheduleOpponentTurn()
    }

    private fun scheduleOpponentTurn() {
        opponentJob?.cancel()
        opponentJob = viewModelScope.launch {
            delay((1500L..2500L).random())
            if (_uiState.value.phase == AsocijacijePhase.GAME_OVER) return@launch

            val state = _uiState.value
            val unrevealedCells = mutableListOf<Pair<Int, Int>>()
            for (c in 0..3) {
                if (!state.columnSolved[c]) {
                    for (r in 0..3) {
                        if (!state.revealed[c][r]) unrevealedCells.add(Pair(c, r))
                    }
                }
            }

            if (unrevealedCells.isNotEmpty()) {
                val (oc, or) = unrevealedCells.random()
                val newRevealed = _uiState.value.revealed.mapIndexed { c, rows ->
                    rows.mapIndexed { r, v -> if (c == oc && r == or) true else v }
                }
                _uiState.value = _uiState.value.copy(revealed = newRevealed)
                delay(1000L)
            }

            if (_uiState.value.phase == AsocijacijePhase.GAME_OVER) return@launch

            // Opponent might guess a column (30% chance, only if 2+ fields revealed in it)
            val unsolvedCols = (0..3).filter { !_uiState.value.columnSolved[it] }
            if (unsolvedCols.isNotEmpty() && Math.random() < 0.30) {
                val col = unsolvedCols.filter { _uiState.value.revealed[it].count { v -> v } >= 2 }
                    .randomOrNull()
                if (col != null) {
                    val colScore = getColumnScore(col, _uiState.value.revealed)
                    val newSolved = _uiState.value.columnSolved.toMutableList().also { it[col] = true }
                    _uiState.value = _uiState.value.copy(
                        columnSolved = newSolved,
                        opponentScore = _uiState.value.opponentScore + colScore,
                        message = "Protivnik rešio kolonu ${('A' + col)}! +$colScore bodova"
                    )
                    delay(800L)
                }
            }

            if (_uiState.value.phase == AsocijacijePhase.GAME_OVER) return@launch

            // Opponent might guess final (15% chance if 2+ columns solved)
            if (_uiState.value.columnSolved.count { it } >= 2 &&
                Math.random() < 0.15 &&
                !_uiState.value.finalSolved
            ) {
                tryOpponentGuessFinal()
                return@launch
            }

            // Pass back to player
            if (_uiState.value.phase != AsocijacijePhase.GAME_OVER) {
                _uiState.value = _uiState.value.copy(
                    phase = AsocijacijePhase.MY_TURN,
                    isMyTurn = true,
                    hasRevealedThisTurn = false
                )
            }
        }
    }

    private fun tryOpponentGuessFinal() {
        val state = _uiState.value
        val finalScore = getFinalScore(state.revealed, state.columnSolved)
        _uiState.value = state.copy(
            finalSolved = true,
            opponentScore = state.opponentScore + finalScore,
            message = "Protivnik pogodio konačno rešenje! +$finalScore bodova"
        )
        viewModelScope.launch {
            delay(1500L)
            if (state.currentRound == 1) startRound(2) else endGame()
        }
    }

    private fun normalizeDiacritics(s: String): String =
        s.trim().lowercase()
            .replace('ć', 'c').replace('č', 'c')
            .replace('š', 's')
            .replace('ž', 'z')
            .replace('đ', 'd')
            .replace("dž", "dz")

    fun guessColumn(col: Int, answer: String) {
        val state = _uiState.value
        if (!state.isMyTurn || state.columnSolved[col]) return
        if (state.currentQuestion == null) return

        val correct = normalizeDiacritics(answer) ==
            normalizeDiacritics(state.currentQuestion.columns()[col].solution)

        if (correct) {
            val score = getColumnScore(col, state.revealed)
            val newSolved = state.columnSolved.toMutableList().also { it[col] = true }
            // Player scored — can keep guessing other columns/final (spec rule e)
            _uiState.value = state.copy(
                columnSolved = newSolved,
                myScore = state.myScore + score,
                hasRevealedThisTurn = true,  // prevent another field reveal this turn
                message = "+$score bodova! Tačno rešenje kolone ${('A' + col)}! Možeš dalje pogađati."
            )
        } else {
            _uiState.value = state.copy(message = "Netačno rešenje!")
            passToOpponent()
        }
    }

    fun guessFinal(answer: String) {
        val state = _uiState.value
        if (!state.isMyTurn || state.finalSolved) return
        if (state.currentQuestion == null) return

        val correct = normalizeDiacritics(answer) ==
            normalizeDiacritics(state.currentQuestion.finalSolution)

        if (correct) {
            timerJob?.cancel()
            opponentJob?.cancel()
            val score = getFinalScore(state.revealed, state.columnSolved)
            _uiState.value = state.copy(
                finalSolved = true,
                myScore = state.myScore + score,
                message = "+$score bodova! Konačno rešenje je tačno!"
            )
            viewModelScope.launch {
                delay(1500L)
                if (state.currentRound == 1) startRound(2) else endGame()
            }
        } else {
            _uiState.value = state.copy(message = "Netačno konačno rešenje!")
            passToOpponent()
        }
    }

    private fun getColumnScore(col: Int, revealed: List<List<Boolean>>): Int {
        val unrevealed = revealed[col].count { !it }
        return 2 + unrevealed
    }

    private fun getFinalScore(revealed: List<List<Boolean>>, columnSolved: List<Boolean>): Int {
        var score = 7
        for (col in 0..3) {
            when {
                columnSolved[col] -> { /* already counted when column was solved */ }
                revealed[col].none { it } -> score += 6  // completely unopened column
                else -> score += getColumnScore(col, revealed)  // opened but unsolved
            }
        }
        return score
    }

    private fun endGame() {
        timerJob?.cancel()
        opponentJob?.cancel()
        _uiState.value = _uiState.value.copy(phase = AsocijacijePhase.GAME_OVER)
    }

    private fun fallbackQuestion(index: Int): AsocijacijeQuestion =
        if (index == 1) AsocijacijeQuestion(
            id = "fallback1",
            columnA = ColumnData(listOf("Tigar", "Lav", "Leopard", "Gepard"), "Mačke"),
            columnB = ColumnData(listOf("Ajkula", "Delfin", "Hobotnica", "Tuna"), "Morska bića"),
            columnC = ColumnData(listOf("Orao", "Sova", "Vrabac", "Labud"), "Ptice"),
            columnD = ColumnData(listOf("Pas", "Mačka", "Hamster", "Zec"), "Kućni ljubimci"),
            finalSolution = "ŽIVOTINJE"
        ) else AsocijacijeQuestion(
            id = "fallback2",
            columnA = ColumnData(listOf("Beograd", "Novi Sad", "Niš", "Kragujevac"), "Srpski gradovi"),
            columnB = ColumnData(listOf("Drina", "Sava", "Tisa", "Morava"), "Reke Srbije"),
            columnC = ColumnData(listOf("Đoković", "Ivanović", "Troicki", "Zimnjić"), "Srpski teniseri"),
            columnD = ColumnData(listOf("Kopaonik", "Tara", "Zlatibor", "Fruška Gora"), "Planine Srbije"),
            finalSolution = "SRBIJA"
        )

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
        opponentJob?.cancel()
    }
}
