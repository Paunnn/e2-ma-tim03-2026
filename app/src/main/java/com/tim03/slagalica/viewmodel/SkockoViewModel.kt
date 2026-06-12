package com.tim03.slagalica.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class SkockoPhase {
    MY_TURN,
    OPPONENT_BONUS_R1,
    WAITING_OPPONENT,
    MY_BONUS,
    GAME_OVER
}

data class SkockoAttemptResult(
    val symbols: List<Int>,
    val correctPos: Int,
    val correctSym: Int
)

data class SkockoUiState(
    val phase: SkockoPhase = SkockoPhase.MY_TURN,
    val currentRound: Int = 1,
    val mySolution: List<Int> = emptyList(),
    val opponentSolution: List<Int> = emptyList(),
    val myAttempts: List<SkockoAttemptResult> = emptyList(),
    val opponentAttempts: List<SkockoAttemptResult> = emptyList(),
    val currentInput: List<Int> = emptyList(),
    val timeLeft: Int = 30,
    val myScore: Int = 0,
    val opponentScore: Int = 0,
    val message: String? = null
)

class SkockoViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(SkockoUiState())
    val uiState: StateFlow<SkockoUiState> = _uiState.asStateFlow()

    private var timerJob: Job? = null
    private var opponentJob: Job? = null

    init {
        startGame()
    }

    private fun startGame() {
        val mySol = generateSolution()
        val oppSol = generateSolution()
        _uiState.value = SkockoUiState(
            phase = SkockoPhase.MY_TURN,
            mySolution = mySol,
            opponentSolution = oppSol
        )
        startTimer(30)
    }

    private fun generateSolution(): List<Int> = (0..5).shuffled().take(4)

    private fun startTimer(seconds: Int) {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            var t = seconds
            _uiState.value = _uiState.value.copy(timeLeft = t)
            while (t > 0) {
                delay(1000L)
                t--
                _uiState.value = _uiState.value.copy(timeLeft = t)
            }
            onTimerExpired()
        }
    }

    private fun onTimerExpired() {
        when (_uiState.value.phase) {
            SkockoPhase.MY_TURN -> startOpponentBonusR1()
            SkockoPhase.MY_BONUS -> endGame()
            else -> {}
        }
    }

    fun addSymbol(symbolIndex: Int) {
        val state = _uiState.value
        if (state.phase != SkockoPhase.MY_TURN && state.phase != SkockoPhase.MY_BONUS) return
        if (state.currentInput.size >= 4) return
        _uiState.value = state.copy(currentInput = state.currentInput + symbolIndex)
    }

    fun removeLastSymbol() {
        val state = _uiState.value
        if (state.currentInput.isEmpty()) return
        _uiState.value = state.copy(currentInput = state.currentInput.dropLast(1))
    }

    fun clearInput() {
        _uiState.value = _uiState.value.copy(currentInput = emptyList())
    }

    fun submitAttempt() {
        val state = _uiState.value
        if (state.currentInput.size != 4) return

        when (state.phase) {
            SkockoPhase.MY_TURN -> submitMyTurnAttempt(state)
            SkockoPhase.MY_BONUS -> submitMyBonusAttempt(state)
            else -> {}
        }
    }

    private fun submitMyTurnAttempt(state: SkockoUiState) {
        val result = checkAttempt(state.currentInput, state.mySolution)
        val newAttempts = state.myAttempts + result

        if (result.correctPos == 4) {
            timerJob?.cancel()
            val points = scoreForAttempt(newAttempts.size)
            _uiState.value = state.copy(
                myAttempts = newAttempts,
                currentInput = emptyList(),
                myScore = state.myScore + points,
                message = "+$points bodova! Pogodio/la si kombinaciju!",
                phase = SkockoPhase.MY_TURN
            )
            // Move to round 2
            viewModelScope.launch {
                delay(1500L)
                startRound2()
            }
        } else if (newAttempts.size >= 6) {
            timerJob?.cancel()
            _uiState.value = state.copy(
                myAttempts = newAttempts,
                currentInput = emptyList(),
                message = "Nisi pogodio/la. Protivnik dobija šansu!"
            )
            viewModelScope.launch {
                delay(1200L)
                startOpponentBonusR1()
            }
        } else {
            _uiState.value = state.copy(
                myAttempts = newAttempts,
                currentInput = emptyList(),
                message = null
            )
        }
    }

    private fun scoreForAttempt(attemptNumber: Int): Int = when (attemptNumber) {
        1, 2 -> 20
        3, 4 -> 15
        else -> 10
    }

    private fun startOpponentBonusR1() {
        timerJob?.cancel()
        opponentJob?.cancel()
        _uiState.value = _uiState.value.copy(
            phase = SkockoPhase.OPPONENT_BONUS_R1,
            timeLeft = 10,
            currentInput = emptyList(),
            message = "Protivnik ima 10 sekundi za bonus!"
        )
        timerJob = viewModelScope.launch {
            var time = 10
            val guessAfterSeconds = (3..8).random()
            var hasGuessed = false
            while (time > 0) {
                delay(1000L)
                time--
                _uiState.value = _uiState.value.copy(timeLeft = time)
                if (!hasGuessed && (10 - time) >= guessAfterSeconds) {
                    hasGuessed = true
                    val correct = (0..1).random() == 1
                    _uiState.value = _uiState.value.copy(
                        opponentScore = if (correct) _uiState.value.opponentScore + 10 else _uiState.value.opponentScore,
                        message = if (correct) "Protivnik pogodio! +10 bodova" else "Protivnik nije pogodio."
                    )
                }
            }
            delay(500L)
            startRound2()
        }
    }

    private fun startRound2() {
        _uiState.value = _uiState.value.copy(
            currentRound = 2,
            phase = SkockoPhase.WAITING_OPPONENT,
            opponentAttempts = emptyList(),
            currentInput = emptyList(),
            timeLeft = 30,
            message = "Runda 2: Protivnik igra!"
        )
        simulateOpponentRound2()
    }

    private fun simulateOpponentRound2() {
        opponentJob?.cancel()
        opponentJob = viewModelScope.launch {
            val solution = _uiState.value.opponentSolution
            // Opponent always uses up to 6 attempts; randomly picks one to be the winning guess
            val successAttempt = if ((0..1).random() == 1) (1..6).random() else -1

            for (attempt in 1..6) {
                delay(1200L)
                val guess = if (attempt == successAttempt) solution else generateSolution()
                val result = checkAttempt(guess, solution)
                _uiState.value = _uiState.value.copy(
                    opponentAttempts = _uiState.value.opponentAttempts + result
                )

                if (result.correctPos == 4) {
                    val points = scoreForAttempt(attempt)
                    _uiState.value = _uiState.value.copy(
                        opponentScore = _uiState.value.opponentScore + points,
                        message = "Protivnik pogodio u $attempt. pokušaju! +$points bodova"
                    )
                    delay(1500L)
                    endGame()
                    return@launch
                }
            }

            // Opponent failed all 6 → my bonus
            _uiState.value = _uiState.value.copy(
                message = "Protivnik nije pogodio! Tvoj bonus!",
                phase = SkockoPhase.MY_BONUS,
                currentInput = emptyList()
            )
            startTimer(10)
        }
    }

    private fun submitMyBonusAttempt(state: SkockoUiState) {
        val result = checkAttempt(state.currentInput, state.opponentSolution)
        if (result.correctPos == 4) {
            timerJob?.cancel()
            _uiState.value = state.copy(
                myScore = state.myScore + 10,
                currentInput = emptyList(),
                message = "+10 bonus bodova!"
            )
        } else {
            _uiState.value = state.copy(
                currentInput = emptyList(),
                message = "Netačno!"
            )
        }
        viewModelScope.launch {
            delay(1000L)
            endGame()
        }
    }

    private fun endGame() {
        timerJob?.cancel()
        opponentJob?.cancel()
        _uiState.value = _uiState.value.copy(
            phase = SkockoPhase.GAME_OVER,
            currentInput = emptyList()
        )
    }

    private fun checkAttempt(guess: List<Int>, solution: List<Int>): SkockoAttemptResult {
        val solCount = IntArray(6)
        val guessCount = IntArray(6)
        var correctPos = 0

        for (i in 0..3) {
            if (guess[i] == solution[i]) {
                correctPos++
            } else {
                solCount[solution[i]]++
                guessCount[guess[i]]++
            }
        }

        var correctSym = 0
        for (s in 0..5) {
            correctSym += minOf(solCount[s], guessCount[s])
        }

        return SkockoAttemptResult(guess, correctPos, correctSym)
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
        opponentJob?.cancel()
    }
}
