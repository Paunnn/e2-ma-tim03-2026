package com.tim03.slagalica.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.ListenerRegistration
import com.tim03.slagalica.data.model.KorakPoKorakQuestion
import com.tim03.slagalica.data.repository.KorakPoKorakRepository
import com.tim03.slagalica.data.repository.MultiplayerGameRepository
import com.tim03.slagalica.data.repository.UserRepository
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
    val showAnswer: Boolean = false,
    val error: String? = null
)

class KorakPoKorakViewModel(
    private val sessionId: String = "",
    private val isPlayer1: Boolean = true,
    private val gameIdx: Int = -1,
    private val repo: KorakPoKorakRepository = KorakPoKorakRepository(),
    private val userRepo: UserRepository = UserRepository(),
    private val mpRepo: MultiplayerGameRepository = MultiplayerGameRepository()
) : ViewModel() {

    val isMultiplayer = sessionId.isNotEmpty()

    private val _uiState = MutableStateFlow(KorakPoKorakUiState())
    val uiState: StateFlow<KorakPoKorakUiState> = _uiState.asStateFlow()

    private val _username = MutableStateFlow("Igrač")
    val username: StateFlow<String> = _username.asStateFlow()

    private var timerJob: Job? = null
    private var opponentJob: Job? = null
    private var mpListener: ListenerRegistration? = null

    private var currentQuestion: KorakPoKorakQuestion? = null
    private var round2Question: KorakPoKorakQuestion? = null
    private var myCorrectStep: Int? = null
    private var resultSaved = false
    private var lastHandledPhase = ""
    private var lastGameData: Map<String, Any> = emptyMap()

    init {
        loadGame()
        viewModelScope.launch {
            runCatching { userRepo.getCurrentUser()?.username?.also { _username.value = it } }
        }
    }

    private fun loadGame() {
        if (isMultiplayer) loadMultiplayerGame() else loadSinglePlayerGame()
    }

    private fun loadSinglePlayerGame() {
        viewModelScope.launch {
            _uiState.value = KorakPoKorakUiState(isLoading = true)
            val (q1, q2) = repo.getTwoQuestions()
            currentQuestion = q1
            round2Question = q2
            _uiState.value = KorakPoKorakUiState(
                isLoading = false,
                question = currentQuestion,
                phase = KorakPoKorakPhase.MY_TURN
            )
            startMyTurnTimer()
        }
    }

    private fun loadMultiplayerGame() {
        viewModelScope.launch {
            _uiState.value = KorakPoKorakUiState(isLoading = true)
            if (isPlayer1) {
                val (q1, q2) = repo.getTwoQuestions()
                currentQuestion = q1
                round2Question = q2
                mpRepo.initGame(
                    sessionId, gameIdx, "kpk_r1",
                    mapOf("type" to "kpk", "q1Id" to q1.id, "q2Id" to q2.id,
                          "r1Score" to -1L, "r1Bonus" to 0L,
                          "r2Score" to -1L, "r2Bonus" to 0L)
                )
                _uiState.value = KorakPoKorakUiState(
                    isLoading = false, question = q1, phase = KorakPoKorakPhase.MY_TURN
                )
                startMyTurnTimer()
            }
            listenForMultiplayerPhase()
        }
    }

    private fun listenForMultiplayerPhase() {
        mpListener?.remove()
        mpListener = mpRepo.listenToGameState(sessionId) { gIdx, phase, data ->
            if (gIdx != gameIdx) return@listenToGameState
            lastGameData = data
            if (phase != lastHandledPhase) {
                lastHandledPhase = phase
                handleMpPhase(phase, data)
            } else {
                handleMpLiveUpdate(data)
            }
        }
    }

    // Same-phase data updates: sync revealed steps, timer snap, and answer reveal to the watcher.
    private fun handleMpLiveUpdate(data: Map<String, Any>) {
        val curPhase = _uiState.value.phase
        val revealedSteps = (data["revealedSteps"] as? Long)?.toInt()
        val showAnswer = data["showAnswer"] as? Boolean ?: false
        val syncedTimeLeft = (data["timeLeft"] as? Long)?.toInt()

        if (curPhase == KorakPoKorakPhase.WAITING_OPPONENT) {
            _uiState.value = _uiState.value.copy(
                revealedSteps = revealedSteps ?: _uiState.value.revealedSteps,
                timeLeft = if (syncedTimeLeft != null && syncedTimeLeft < _uiState.value.timeLeft)
                    syncedTimeLeft else _uiState.value.timeLeft
            )
        }
        if (showAnswer && !_uiState.value.showAnswer &&
            (curPhase == KorakPoKorakPhase.WAITING_OPPONENT ||
             curPhase == KorakPoKorakPhase.OPPONENT_BONUS)) {
            _uiState.value = _uiState.value.copy(showAnswer = true)
        }
    }

    private fun handleMpPhase(phase: String, data: Map<String, Any>) {
        when (phase) {
            "kpk_r1" -> {
                if (!isPlayer1) {
                    val q1Id = data["q1Id"] as? String ?: ""
                    val q2Id = data["q2Id"] as? String ?: ""
                    viewModelScope.launch {
                        val q1 = if (q1Id.isNotEmpty()) repo.getQuestionById(q1Id) else repo.getRandomQuestion()
                        val q2 = if (q2Id.isNotEmpty()) repo.getQuestionById(q2Id) else repo.getRandomQuestion()
                        currentQuestion = q1
                        round2Question = q2
                        _uiState.value = KorakPoKorakUiState(
                            isLoading = false, question = q1,
                            phase = KorakPoKorakPhase.WAITING_OPPONENT, timeLeft = 70
                        )
                        startWaitingTimer(70)
                    }
                }
            }
            "kpk_r1bonus" -> {
                if (isPlayer1) {
                    timerJob?.cancel()
                    val allSteps = currentQuestion?.steps?.size ?: 7
                    _uiState.value = _uiState.value.copy(
                        phase = KorakPoKorakPhase.OPPONENT_BONUS,
                        revealedSteps = allSteps, timeLeft = 12, showAnswer = false,
                        lastResultMessage = "Nisi pogodio! Protivnik dobija šansu."
                    )
                    startWaitingTimer(12)
                } else {
                    timerJob?.cancel()
                    val allSteps = currentQuestion?.steps?.size ?: 7
                    _uiState.value = _uiState.value.copy(
                        phase = KorakPoKorakPhase.MY_BONUS,
                        revealedSteps = allSteps, timeLeft = 10, showAnswer = false,
                        lastResultMessage = "Protivnik nije pogodio! Pogodi ti za +5!"
                    )
                    startMyBonusTimer()
                }
            }
            "kpk_r2" -> {
                if (!isPlayer1) {
                    timerJob?.cancel()
                    val q = round2Question ?: return
                    currentQuestion = q
                    _uiState.value = _uiState.value.copy(
                        currentRound = 2, question = q, revealedSteps = 1,
                        phase = KorakPoKorakPhase.MY_TURN, timeLeft = 70,
                        lastResultMessage = null, showAnswer = false
                    )
                    startMyTurnTimer()
                } else {
                    timerJob?.cancel()
                    val q = round2Question
                    currentQuestion = q
                    _uiState.value = _uiState.value.copy(
                        currentRound = 2, question = q, revealedSteps = 1,
                        phase = KorakPoKorakPhase.WAITING_OPPONENT, timeLeft = 70,
                        lastResultMessage = null, showAnswer = false
                    )
                    startWaitingTimer(70)
                }
            }
            "kpk_r2bonus" -> {
                if (isPlayer1) {
                    timerJob?.cancel()
                    val allSteps = currentQuestion?.steps?.size ?: 7
                    _uiState.value = _uiState.value.copy(
                        phase = KorakPoKorakPhase.MY_BONUS,
                        revealedSteps = allSteps, timeLeft = 10, showAnswer = false,
                        lastResultMessage = "Protivnik nije pogodio! Pogodi ti za +5!"
                    )
                    startMyBonusTimer()
                } else {
                    timerJob?.cancel()
                    val allSteps = currentQuestion?.steps?.size ?: 7
                    _uiState.value = _uiState.value.copy(
                        phase = KorakPoKorakPhase.OPPONENT_BONUS,
                        revealedSteps = allSteps, timeLeft = 12, showAnswer = false,
                        lastResultMessage = "Nisi pogodio! Protivnik dobija šansu."
                    )
                    startWaitingTimer(12)
                }
            }
            "kpk_done" -> {
                timerJob?.cancel()
                opponentJob?.cancel()
                val r1Score = (data["r1Score"] as? Long)?.toInt() ?: 0
                val r1Bonus = (data["r1Bonus"] as? Long)?.toInt() ?: 0
                val r2Score = (data["r2Score"] as? Long)?.toInt() ?: 0
                val r2Bonus = (data["r2Bonus"] as? Long)?.toInt() ?: 0
                val myFinal = if (isPlayer1) r1Score + r2Bonus else r2Score + r1Bonus
                val oppFinal = if (isPlayer1) r2Score + r1Bonus else r1Score + r2Bonus
                _uiState.value = _uiState.value.copy(myScore = myFinal, opponentScore = oppFinal)
                endGame()
            }
        }
    }

    // ─── Timer helpers ───

    private fun startWaitingTimer(seconds: Int) {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(timeLeft = seconds)
            while (true) {
                delay(1000L)
                val phase = _uiState.value.phase
                if (phase != KorakPoKorakPhase.WAITING_OPPONENT &&
                    phase != KorakPoKorakPhase.OPPONENT_BONUS) break
                val cur = _uiState.value.timeLeft
                if (cur <= 0) break
                _uiState.value = _uiState.value.copy(timeLeft = cur - 1)
            }
        }
    }

    private fun startMyTurnTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            val totalSteps = currentQuestion?.steps?.size ?: 7
            var t = 70
            _uiState.value = _uiState.value.copy(revealedSteps = 1, timeLeft = t)
            while (t > 0) {
                delay(1000L)
                t--
                _uiState.value = _uiState.value.copy(timeLeft = t)
                if (t > 0 && t % 10 == 0) {
                    val newStep = ((70 - t) / 10 + 1).coerceAtMost(totalSteps)
                    val cur = _uiState.value.revealedSteps
                    if (newStep > cur) {
                        _uiState.value = _uiState.value.copy(revealedSteps = newStep)
                        if (isMultiplayer) mpRepo.updateGameData(sessionId,
                            mapOf("revealedSteps" to newStep.toLong(), "timeLeft" to t.toLong()))
                    }
                }
            }
            if (_uiState.value.phase == KorakPoKorakPhase.MY_TURN) {
                if (isMultiplayer) handleRoundFailedMultiplayer()
                else startOpponentBonusPhase()
            }
        }
    }

    private fun advanceToNextStep() {
        val totalSteps = currentQuestion?.steps?.size ?: 7
        val cur = _uiState.value.revealedSteps
        if (cur >= totalSteps) {
            if (isMultiplayer) handleRoundFailedMultiplayer()
            else startOpponentBonusPhase()
            return
        }
        val t = _uiState.value.timeLeft
        val newTime = ((t - 1) / 10) * 10
        val newStep = cur + 1
        _uiState.value = _uiState.value.copy(revealedSteps = newStep, timeLeft = newTime)
        if (isMultiplayer) mpRepo.updateGameData(sessionId,
            mapOf("revealedSteps" to newStep.toLong(), "timeLeft" to newTime.toLong()))
        if (newTime <= 0) {
            if (isMultiplayer) handleRoundFailedMultiplayer()
            else startOpponentBonusPhase()
        } else {
            resumeMainTimerFrom(newTime)
        }
    }

    private fun resumeMainTimerFrom(fromTime: Int) {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            val totalSteps = currentQuestion?.steps?.size ?: 7
            var t = fromTime
            while (t > 0) {
                delay(1000L)
                t--
                _uiState.value = _uiState.value.copy(timeLeft = t)
                if (t > 0 && t % 10 == 0) {
                    val newStep = ((70 - t) / 10 + 1).coerceAtMost(totalSteps)
                    val cur = _uiState.value.revealedSteps
                    if (newStep > cur) {
                        _uiState.value = _uiState.value.copy(revealedSteps = newStep)
                        if (isMultiplayer) mpRepo.updateGameData(sessionId,
                            mapOf("revealedSteps" to newStep.toLong(), "timeLeft" to t.toLong()))
                    }
                }
            }
            if (_uiState.value.phase == KorakPoKorakPhase.MY_TURN) {
                if (isMultiplayer) handleRoundFailedMultiplayer()
                else startOpponentBonusPhase()
            }
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
            if (_uiState.value.phase == KorakPoKorakPhase.MY_BONUS) {
                if (isMultiplayer) {
                    _uiState.value = _uiState.value.copy(showAnswer = true)
                    mpRepo.updateGameData(sessionId, mapOf("showAnswer" to true))
                    delay(2000L)
                    _uiState.value = _uiState.value.copy(showAnswer = false)
                    submitBonusToFirestore(0)
                } else {
                    endGame()
                }
            }
        }
    }

    // ─── Player actions ───

    fun submitAnswer(answer: String) {
        if (_uiState.value.phase != KorakPoKorakPhase.MY_TURN) return
        if (_uiState.value.showAnswer) return
        timerJob?.cancel()
        val correct = answer.trim().equals(currentQuestion?.answer?.trim(), ignoreCase = true)
        if (correct) {
            myCorrectStep = _uiState.value.revealedSteps
            val points = (20 - (_uiState.value.revealedSteps - 1) * 2).coerceAtLeast(8)
            _uiState.value = _uiState.value.copy(
                myScore = _uiState.value.myScore + points,
                lastResultMessage = "+$points bodova!",
                showAnswer = true
            )
            // Share the correct guess with the watcher so they also see the answer.
            if (isMultiplayer) mpRepo.updateGameData(sessionId, mapOf("showAnswer" to true))
            viewModelScope.launch {
                delay(2500L)
                _uiState.value = _uiState.value.copy(showAnswer = false)
                if (isMultiplayer) proceedAfterMyRoundMultiplayer(points)
                else proceedAfterMyRound()
            }
        } else {
            _uiState.value = _uiState.value.copy(lastResultMessage = "Netačno!")
            advanceToNextStep()
        }
    }

    fun submitMyBonusAnswer(answer: String) {
        if (_uiState.value.phase != KorakPoKorakPhase.MY_BONUS) return
        if (_uiState.value.showAnswer) return
        timerJob?.cancel()
        val correct = answer.trim().equals(currentQuestion?.answer?.trim(), ignoreCase = true)
        val bonus = if (correct) 5 else 0
        if (correct) {
            _uiState.value = _uiState.value.copy(
                myScore = _uiState.value.myScore + bonus,
                lastResultMessage = "+$bonus bodova!",
                showAnswer = true
            )
            // Share the correct guess with the watcher so they also see the answer.
            if (isMultiplayer) mpRepo.updateGameData(sessionId, mapOf("showAnswer" to true))
        }
        if (isMultiplayer) {
            viewModelScope.launch {
                if (!correct) {
                    _uiState.value = _uiState.value.copy(showAnswer = true)
                    mpRepo.updateGameData(sessionId, mapOf("showAnswer" to true))
                }
                delay(2000L)
                _uiState.value = _uiState.value.copy(showAnswer = false)
                submitBonusToFirestore(bonus)
            }
        } else {
            viewModelScope.launch {
                if (correct) {
                    delay(2500L)
                    _uiState.value = _uiState.value.copy(showAnswer = false)
                }
                endGame()
            }
        }
    }

    // ─── Multiplayer helpers ───

    private fun proceedAfterMyRoundMultiplayer(points: Int) {
        val round = _uiState.value.currentRound
        viewModelScope.launch {
            if (isPlayer1 && round == 1) {
                mpRepo.setPhaseAndData(sessionId, "kpk_r2",
                    mapOf("r1Score" to points.toLong(), "revealedSteps" to 1L, "showAnswer" to false, "timeLeft" to 70L))
            } else if (!isPlayer1 && round == 2) {
                mpRepo.setPhaseAndData(sessionId, "kpk_done", mapOf("r2Score" to points.toLong()))
            }
        }
    }

    private fun handleRoundFailedMultiplayer() {
        val round = _uiState.value.currentRound
        val allSteps = currentQuestion?.steps?.size ?: 7
        _uiState.value = _uiState.value.copy(
            phase = KorakPoKorakPhase.OPPONENT_BONUS,
            revealedSteps = allSteps,
            lastResultMessage = "Nisi pogodio! Protivnik dobija šansu."
        )
        viewModelScope.launch {
            if (isPlayer1 && round == 1) {
                mpRepo.setPhaseAndData(sessionId, "kpk_r1bonus", mapOf("r1Score" to 0L))
            } else if (!isPlayer1 && round == 2) {
                mpRepo.setPhaseAndData(sessionId, "kpk_r2bonus", mapOf("r2Score" to 0L))
            }
        }
    }

    private fun submitBonusToFirestore(bonusScore: Int) {
        val round = _uiState.value.currentRound
        viewModelScope.launch {
            if (!isPlayer1 && round == 1) {
                mpRepo.setPhaseAndData(sessionId, "kpk_r2",
                    mapOf("r1Bonus" to bonusScore.toLong(), "revealedSteps" to 1L, "showAnswer" to false, "timeLeft" to 70L))
            } else if (isPlayer1 && round == 2) {
                mpRepo.setPhaseAndData(sessionId, "kpk_done", mapOf("r2Bonus" to bonusScore.toLong()))
            }
        }
    }

    // ─── Single-player helpers ───

    private fun startOpponentBonusPhase() {
        val allSteps = currentQuestion?.steps?.size ?: 7
        _uiState.value = _uiState.value.copy(
            phase = KorakPoKorakPhase.OPPONENT_BONUS,
            timeLeft = 10, revealedSteps = allSteps, lastResultMessage = null
        )
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            var time = 10
            val guessAfter = (3..8).random()
            var guessed = false
            while (time > 0) {
                delay(1000L)
                time--
                _uiState.value = _uiState.value.copy(timeLeft = time)
                if (!guessed && (10 - time) >= guessAfter) {
                    guessed = true
                    val correct = (0..1).random() == 1
                    _uiState.value = _uiState.value.copy(
                        opponentScore = if (correct) _uiState.value.opponentScore + 5 else _uiState.value.opponentScore,
                        lastResultMessage = if (correct) "Protivnik pogodio! +5" else "Protivnik nije pogodio."
                    )
                }
            }
            proceedAfterMyRound()
        }
    }

    private fun proceedAfterMyRound() {
        if (_uiState.value.currentRound == 1) startRound2() else endGame()
    }

    private fun startRound2() {
        val q = round2Question ?: currentQuestion ?: return
        currentQuestion = q
        _uiState.value = _uiState.value.copy(
            currentRound = 2, question = q, revealedSteps = 1,
            phase = KorakPoKorakPhase.WAITING_OPPONENT, timeLeft = 70, lastResultMessage = null
        )
        simulateOpponentRound()
    }

    private fun simulateOpponentRound() {
        opponentJob?.cancel()
        opponentJob = viewModelScope.launch {
            val opponentSteps = (2..5).random()
            var time = 70
            _uiState.value = _uiState.value.copy(revealedSteps = 1)
            for (elapsed in 1..70) {
                delay(1000L)
                time--
                val revealed = ((elapsed / 10) + 1).coerceAtMost(currentQuestion?.steps?.size ?: 7)
                _uiState.value = _uiState.value.copy(timeLeft = time, revealedSteps = revealed)
                if (revealed >= opponentSteps) {
                    val guesses = (0..1).random() == 1
                    if (guesses) {
                        val points = (20 - (opponentSteps - 1) * 2).coerceAtLeast(8)
                        _uiState.value = _uiState.value.copy(
                            opponentScore = _uiState.value.opponentScore + points,
                            lastResultMessage = "Protivnik pogodio!",
                            showAnswer = true
                        )
                        delay(2500L)
                        _uiState.value = _uiState.value.copy(showAnswer = false)
                        endGame()
                    } else {
                        _uiState.value = _uiState.value.copy(
                            phase = KorakPoKorakPhase.MY_BONUS, timeLeft = 10,
                            lastResultMessage = "Protivnik nije pogodio! Tvoj red."
                        )
                        startMyBonusTimer()
                    }
                    return@launch
                }
            }
            _uiState.value = _uiState.value.copy(
                phase = KorakPoKorakPhase.MY_BONUS, timeLeft = 10,
                lastResultMessage = "Protivniku isteklo vreme! Tvoj red."
            )
            startMyBonusTimer()
        }
    }

    private fun endGame() {
        timerJob?.cancel()
        opponentJob?.cancel()
        _uiState.value = _uiState.value.copy(phase = KorakPoKorakPhase.GAME_OVER)
        if (!resultSaved) {
            resultSaved = true
            val step = myCorrectStep
            val myScore = _uiState.value.myScore
            val oppScore = _uiState.value.opponentScore
            viewModelScope.launch {
                runCatching {
                    userRepo.saveKorakPoKorakResult(step)
                    userRepo.saveGameResult(won = myScore > oppScore)
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
        opponentJob?.cancel()
        mpListener?.remove()
    }
}

class KorakPoKorakViewModelFactory(
    private val sessionId: String,
    private val isPlayer1: Boolean,
    private val gameIdx: Int
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        KorakPoKorakViewModel(sessionId = sessionId, isPlayer1 = isPlayer1, gameIdx = gameIdx) as T
}
