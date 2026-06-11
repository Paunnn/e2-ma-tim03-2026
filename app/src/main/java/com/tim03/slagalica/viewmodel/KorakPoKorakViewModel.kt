package com.tim03.slagalica.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tim03.slagalica.data.model.KorakPoKorakQuestion
import com.tim03.slagalica.data.repository.KorakPoKorakRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class KorakPoKorakPhase {
    MY_TURN,
    OPPONENT_BONUS,
    WAITING_OPPONENT,
    MY_BONUS,
    GAME_OVER
}

data class KorakPoKorakUiState(
    val isLoading: Boolean = true,
    val question: KorakPoKorakQuestion? = null,
    val currentRound: Int = 1,
    val revealedSteps: Int = 1,
    val timeLeft: Int = 70,
    val phase: KorakPoKorakPhase = KorakPoKorakPhase.MY_TURN,
    val myScore: Int = 0,
    val opponentScore: Int = 0,
    val lastResultMessage: String? = null,
    val error: String? = null
)

class KorakPoKorakViewModel(
    private val repo: KorakPoKorakRepository = KorakPoKorakRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(KorakPoKorakUiState())
    val uiState: StateFlow<KorakPoKorakUiState> = _uiState.asStateFlow()

    private var timerJob: Job? = null
    private var opponentJob: Job? = null
    private var currentQuestion: KorakPoKorakQuestion? = null
    private var round2Question: KorakPoKorakQuestion? = null

    init {
        loadGame()
    }

    private fun loadGame() {
        viewModelScope.launch {
            _uiState.value = KorakPoKorakUiState(isLoading = true)
            currentQuestion = repo.getRandomQuestion()
            round2Question = repo.getRandomQuestion()
            _uiState.value = KorakPoKorakUiState(
                isLoading = false,
                question = currentQuestion,
                phase = KorakPoKorakPhase.MY_TURN
            )
            startMyTurnTimer()
        }
    }

    private fun startMyTurnTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            val state = _uiState.value
            var time = 70
            _uiState.value = state.copy(timeLeft = time, revealedSteps = 1)

            while (time > 0) {
                delay(1000L)
                time--
                val elapsed = 70 - time
                val newRevealed = ((elapsed / 10) + 1).coerceAtMost(
                    currentQuestion?.steps?.size ?: 7
                )
                _uiState.value = _uiState.value.copy(timeLeft = time, revealedSteps = newRevealed)
            }
            // Time ran out without guessing → opponent bonus
            startOpponentBonusPhase()
        }
    }

    fun submitAnswer(answer: String) {
        if (_uiState.value.phase != KorakPoKorakPhase.MY_TURN) return
        timerJob?.cancel()
        val correct = answer.trim().equals(currentQuestion?.answer?.trim(), ignoreCase = true)
        if (correct) {
            val points = (20 - (_uiState.value.revealedSteps - 1) * 2).coerceAtLeast(2)
            _uiState.value = _uiState.value.copy(
                myScore = _uiState.value.myScore + points,
                lastResultMessage = "+$points bodova!"
            )
            proceedAfterMyRound()
        } else {
            _uiState.value = _uiState.value.copy(lastResultMessage = "Netačno!")
            startOpponentBonusPhase()
        }
    }

    private fun startOpponentBonusPhase() {
        _uiState.value = _uiState.value.copy(
            phase = KorakPoKorakPhase.OPPONENT_BONUS,
            timeLeft = 10
        )
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            var time = 10
            while (time > 0) {
                delay(1000L)
                time--
                _uiState.value = _uiState.value.copy(timeLeft = time)
            }
            proceedAfterMyRound()
        }
    }

    fun submitOpponentBonusAnswer(answer: String) {
        if (_uiState.value.phase != KorakPoKorakPhase.OPPONENT_BONUS) return
        timerJob?.cancel()
        val correct = answer.trim().equals(currentQuestion?.answer?.trim(), ignoreCase = true)
        if (correct) {
            _uiState.value = _uiState.value.copy(
                opponentScore = _uiState.value.opponentScore + 5,
                lastResultMessage = "Protivnik: +5 bodova"
            )
        }
        proceedAfterMyRound()
    }

    private fun proceedAfterMyRound() {
        if (_uiState.value.currentRound == 1) {
            startRound2()
        } else {
            endGame()
        }
    }

    private fun startRound2() {
        val q = round2Question ?: currentQuestion ?: return
        currentQuestion = q
        _uiState.value = _uiState.value.copy(
            currentRound = 2,
            question = q,
            revealedSteps = 1,
            phase = KorakPoKorakPhase.WAITING_OPPONENT,
            timeLeft = 70,
            lastResultMessage = null
        )
        simulateOpponentRound()
    }

    private fun simulateOpponentRound() {
        opponentJob?.cancel()
        opponentJob = viewModelScope.launch {
            // Opponent reveals 2-5 steps before attempting or failing
            val opponentSteps = (2..5).random()
            var time = 70
            _uiState.value = _uiState.value.copy(revealedSteps = 1)

            for (elapsed in 1..70) {
                delay(1000L)
                time--
                val revealed = ((elapsed / 10) + 1).coerceAtMost(
                    currentQuestion?.steps?.size ?: 7
                )
                _uiState.value = _uiState.value.copy(timeLeft = time, revealedSteps = revealed)

                if (revealed >= opponentSteps) {
                    // Opponent either guesses correctly (50%) or fails
                    val opponentGuesses = (0..1).random() == 1
                    if (opponentGuesses) {
                        val points = (20 - (opponentSteps - 1) * 2).coerceAtLeast(2)
                        _uiState.value = _uiState.value.copy(
                            opponentScore = _uiState.value.opponentScore + points,
                            lastResultMessage = "Protivnik pogodio!"
                        )
                        endGame()
                    } else {
                        // Opponent failed → I get bonus
                        _uiState.value = _uiState.value.copy(
                            phase = KorakPoKorakPhase.MY_BONUS,
                            timeLeft = 10,
                            lastResultMessage = "Protivnik nije pogodio! Tvoj red."
                        )
                        startMyBonusTimer()
                    }
                    return@launch
                }
            }
            // Opponent time ran out
            _uiState.value = _uiState.value.copy(
                phase = KorakPoKorakPhase.MY_BONUS,
                timeLeft = 10,
                lastResultMessage = "Protivniku je isteklo vreme! Tvoj red."
            )
            startMyBonusTimer()
        }
    }

    private fun startMyBonusTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            var time = 10
            _uiState.value = _uiState.value.copy(timeLeft = time)
            while (time > 0) {
                delay(1000L)
                time--
                _uiState.value = _uiState.value.copy(timeLeft = time)
            }
            endGame()
        }
    }

    fun submitMyBonusAnswer(answer: String) {
        if (_uiState.value.phase != KorakPoKorakPhase.MY_BONUS) return
        timerJob?.cancel()
        val correct = answer.trim().equals(currentQuestion?.answer?.trim(), ignoreCase = true)
        if (correct) {
            _uiState.value = _uiState.value.copy(
                myScore = _uiState.value.myScore + 5,
                lastResultMessage = "+5 bodova!"
            )
        }
        endGame()
    }

    private fun endGame() {
        timerJob?.cancel()
        opponentJob?.cancel()
        _uiState.value = _uiState.value.copy(phase = KorakPoKorakPhase.GAME_OVER)
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
        opponentJob?.cancel()
    }
}
