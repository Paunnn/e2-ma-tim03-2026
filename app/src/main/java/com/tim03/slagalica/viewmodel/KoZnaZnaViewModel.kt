package com.tim03.slagalica.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.ListenerRegistration
import com.tim03.slagalica.data.model.KoZnaZnaQuestion
import com.tim03.slagalica.data.repository.KoZnaZnaRepository
import com.tim03.slagalica.data.repository.MultiplayerGameRepository
import com.tim03.slagalica.data.repository.UserRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class KoZnaZnaUiState(
    val isLoading: Boolean = true,
    val questions: List<KoZnaZnaQuestion> = emptyList(),
    val currentQuestionIndex: Int = 0,
    val myScore: Int = 0,
    val opponentScore: Int = 0,
    val timeLeft: Int = 5,
    val playerAnswerIndex: Int? = null,
    val playerAnswerTimeMs: Long? = null,
    val opponentAnswerIndex: Int? = null,
    val opponentAnswerTimeMs: Long? = null,
    val revealPhase: Boolean = false,
    val questionOutcomes: Map<Int, Int> = emptyMap(),
    val gameOver: Boolean = false,
    val error: String? = null
)

class KoZnaZnaViewModel(
    private val sessionId: String = "",
    private val isPlayer1: Boolean = true,
    private val gameIdx: Int = -1,
    private val questionRepo: KoZnaZnaRepository = KoZnaZnaRepository(),
    private val userRepo: UserRepository = UserRepository(),
    private val mpRepo: MultiplayerGameRepository = MultiplayerGameRepository()
) : ViewModel() {

    val isMultiplayer = sessionId.isNotEmpty()

    private val _uiState = MutableStateFlow(KoZnaZnaUiState())
    val uiState: StateFlow<KoZnaZnaUiState> = _uiState.asStateFlow()

    private val _username = MutableStateFlow("Igrač")
    val username: StateFlow<String> = _username.asStateFlow()

    private var timerJob: Job? = null
    private var opponentJob: Job? = null
    private var mpListener: ListenerRegistration? = null

    private var correctCount = 0
    private var incorrectCount = 0
    private var resultSaved = false
    private var questionStartTime = 0L

    init {
        loadQuestions()
        viewModelScope.launch {
            runCatching { userRepo.getCurrentUser()?.username?.also { _username.value = it } }
        }
    }

    private fun loadQuestions() {
        viewModelScope.launch {
            try {
                if (isMultiplayer && isPlayer1) {
                    val questions = questionRepo.getQuestions(5)
                    _uiState.value = KoZnaZnaUiState(isLoading = false, questions = questions)
                    val ids = questions.map { it.id }
                    val dataMap = mutableMapOf<String, Any>(
                        "type" to "kzz",
                        "questionIds" to ids
                    )
                    for (i in ids.indices) {
                        dataMap["q${i}p1"] = -1L
                        dataMap["q${i}p2"] = -1L
                        dataMap["q${i}p1ms"] = 0L
                        dataMap["q${i}p2ms"] = 0L
                    }
                    mpRepo.initGame(sessionId, gameIdx, "kzz_playing", dataMap)
                    listenForMpAnswers()
                    startQuestion(0)
                } else if (isMultiplayer && !isPlayer1) {
                    _uiState.value = KoZnaZnaUiState(isLoading = true)
                    listenForMpAnswers()
                } else {
                    // Single-player: load questions locally
                    val questions = questionRepo.getQuestions(5)
                    _uiState.value = KoZnaZnaUiState(isLoading = false, questions = questions)
                    startQuestion(0)
                }
            } catch (e: Exception) {
                _uiState.value = KoZnaZnaUiState(isLoading = false, error = e.message)
            }
        }
    }

    // ─── Multiplayer Firestore listener ───
    // Both players write q{N}p{1|2} and q{N}p{1|2}ms when they answer.
    // The listener picks up the opponent's answer for the current question.

    private fun listenForMpAnswers() {
        mpListener?.remove()
        mpListener = mpRepo.listenToGameState(sessionId) { gIdx, phase, data ->
            if (gIdx != gameIdx) return@listenToGameState
            if (phase == "kzz_done") {
                handleMpDone(data); return@listenToGameState
            }
            // Load questions for P2 from question IDs on first snapshot
            val state = _uiState.value
            if (!isPlayer1 && state.isLoading) {
                @Suppress("UNCHECKED_CAST")
                val ids = data["questionIds"] as? List<String> ?: return@listenToGameState
                viewModelScope.launch {
                    try {
                        val questions = questionRepo.getQuestionsByIds(ids)
                        _uiState.value = _uiState.value.copy(isLoading = false, questions = questions)
                        startQuestion(0)
                    } catch (e: Exception) {
                        _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
                    }
                }
                return@listenToGameState
            }
            // Check opponent's answer for current question
            val qIdx = _uiState.value.currentQuestionIndex
            val oppKey = if (isPlayer1) "q${qIdx}p2" else "q${qIdx}p1"
            val oppMsKey = if (isPlayer1) "q${qIdx}p2ms" else "q${qIdx}p1ms"
            val oppAnswer = (data[oppKey] as? Long)?.toInt() ?: -1
            val oppMs = (data[oppMsKey] as? Long) ?: 0L
            // -1 = initial/not answered yet; -2 = timeout no-answer; 0-3 = valid answer
            if (_uiState.value.opponentAnswerIndex == null) {
                when {
                    oppAnswer >= 0 -> handleOpponentAnswered(oppAnswer, oppMs)
                    oppAnswer == -2 -> handleOpponentAnswered(null, 0L)
                }
            }
        }
    }

    private fun handleOpponentAnswered(answerIdx: Int?, timeMs: Long) {
        val state = _uiState.value
        if (state.revealPhase || state.gameOver) return
        _uiState.value = state.copy(
            opponentAnswerIndex = answerIdx ?: -2,
            opponentAnswerTimeMs = if (timeMs > 0L) timeMs else null
        )
        // If player has already answered → trigger reveal immediately
        if (state.playerAnswerIndex != null) {
            triggerReveal()
        }
    }

    private fun handleMpDone(data: Map<String, Any>) {
        timerJob?.cancel(); opponentJob?.cancel()
        // Final scores are computed locally since both have all data via listener
    }

    // ─── Question flow ───

    private fun startQuestion(idx: Int) {
        timerJob?.cancel(); opponentJob?.cancel()
        val state = _uiState.value
        if (state.isLoading || state.questions.isEmpty()) return
        questionStartTime = System.currentTimeMillis()
        _uiState.value = state.copy(
            currentQuestionIndex = idx,
            timeLeft = 5,
            playerAnswerIndex = null,
            playerAnswerTimeMs = null,
            opponentAnswerIndex = null,
            opponentAnswerTimeMs = null,
            revealPhase = false
        )
        if (!isMultiplayer) startOpponentTimer()
        startQuestionTimer()
    }

    private fun startOpponentTimer() {
        opponentJob?.cancel()
        opponentJob = viewModelScope.launch {
            val oppDelay = (800L..4800L).random()
            delay(oppDelay)
            val state = _uiState.value
            if (!state.revealPhase && state.opponentAnswerIndex == null) {
                val oppAnswerIdx = (0..3).random()
                _uiState.value = state.copy(
                    opponentAnswerIndex = oppAnswerIdx,
                    opponentAnswerTimeMs = oppDelay
                )
                if (_uiState.value.playerAnswerIndex != null) triggerReveal()
            }
        }
    }

    private fun startQuestionTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            for (t in 4 downTo 0) {
                delay(1000L)
                _uiState.value = _uiState.value.copy(timeLeft = t)
                if (_uiState.value.revealPhase) return@launch
            }
            // Timer expired
            onQuestionTimerExpired()
        }
    }

    private fun onQuestionTimerExpired() {
        val state = _uiState.value
        if (state.revealPhase) return
        // Write "no answer" for this player in multiplayer
        if (isMultiplayer) {
            val qIdx = state.currentQuestionIndex
            val myKey = if (isPlayer1) "q${qIdx}p1" else "q${qIdx}p2"
            val myMsKey = if (isPlayer1) "q${qIdx}p1ms" else "q${qIdx}p2ms"
            viewModelScope.launch {
                mpRepo.setPhaseAndData(sessionId, "kzz_playing",
                    mapOf(myKey to -2L, myMsKey to 5000L))
            }
        }
        triggerReveal()
    }

    fun selectAnswer(answerIndex: Int) {
        val state = _uiState.value
        if (state.revealPhase || state.playerAnswerIndex != null || state.gameOver) return
        val elapsed = System.currentTimeMillis() - questionStartTime
        _uiState.value = state.copy(
            playerAnswerIndex = answerIndex,
            playerAnswerTimeMs = elapsed
        )
        if (isMultiplayer) {
            val qIdx = state.currentQuestionIndex
            val myKey = if (isPlayer1) "q${qIdx}p1" else "q${qIdx}p2"
            val myMsKey = if (isPlayer1) "q${qIdx}p1ms" else "q${qIdx}p2ms"
            viewModelScope.launch {
                mpRepo.setPhaseAndData(sessionId, "kzz_playing",
                    mapOf(myKey to answerIndex.toLong(), myMsKey to elapsed))
            }
        }
        // Check if opponent has already answered
        if (_uiState.value.opponentAnswerIndex != null) triggerReveal()
    }

    private fun triggerReveal() {
        val state = _uiState.value
        if (state.revealPhase) return
        timerJob?.cancel(); opponentJob?.cancel()
        val oppAnswerIdx = if (state.opponentAnswerIndex == -2) null else state.opponentAnswerIndex

        _uiState.value = state.copy(
            revealPhase = true,
            opponentAnswerIndex = oppAnswerIdx,
            opponentAnswerTimeMs = if (oppAnswerIdx == null) null else state.opponentAnswerTimeMs
        )
        scoreQuestion()
    }

    private fun scoreQuestion() {
        val state = _uiState.value
        val q = state.questions.getOrNull(state.currentQuestionIndex) ?: return
        val pAnswered = state.playerAnswerIndex != null
        val oAnswerIdx = state.opponentAnswerIndex
        val oAnswered = oAnswerIdx != null && oAnswerIdx >= 0
        val pCorrect = pAnswered && state.playerAnswerIndex == q.correctIndex
        val oCorrect = oAnswered && oAnswerIdx == q.correctIndex
        val pTime = state.playerAnswerTimeMs ?: Long.MAX_VALUE
        val oTime = state.opponentAnswerTimeMs ?: Long.MAX_VALUE

        var myScoreDelta = 0; var oppScoreDelta = 0
        val playerGetsPoints = pCorrect && (!oCorrect || pTime <= oTime)
        when {
            playerGetsPoints -> { myScoreDelta += 10; correctCount++ }
            pCorrect -> correctCount++
            pAnswered -> { myScoreDelta -= 5; incorrectCount++ }
        }
        val opponentGetsPoints = oCorrect && (!pCorrect || oTime < pTime)
        when {
            opponentGetsPoints -> oppScoreDelta += 10
            oAnswered && !oCorrect -> oppScoreDelta -= 5
        }

        val outcome = when {
            playerGetsPoints -> 1
            pAnswered && !pCorrect -> -1
            else -> 0
        }

        _uiState.value = _uiState.value.copy(
            myScore = state.myScore + myScoreDelta,
            opponentScore = state.opponentScore + oppScoreDelta,
            questionOutcomes = state.questionOutcomes + (state.currentQuestionIndex to outcome)
        )

        viewModelScope.launch {
            delay(2500L)
            val newIdx = state.currentQuestionIndex + 1
            val newState = _uiState.value
            if (newIdx < newState.questions.size) {
                startQuestion(newIdx)
            } else {
                if (isMultiplayer && isPlayer1) {
                    mpRepo.setPhaseAndData(sessionId, "kzz_done",
                        mapOf("p1Score" to newState.myScore.toLong(),
                              "p2Score" to newState.opponentScore.toLong()))
                }
                finishGame()
            }
        }
    }

    private fun finishGame() {
        timerJob?.cancel(); opponentJob?.cancel()
        val state = _uiState.value
        _uiState.value = state.copy(gameOver = true)
        if (!resultSaved) {
            resultSaved = true
            viewModelScope.launch {
                runCatching {
                    userRepo.saveKoZnaZnaResult(correctCount, incorrectCount)
                    userRepo.saveGameResult(state.myScore > state.opponentScore)
                }
            }
        }
    }

    // Called from screen for single-player compat (keeps backward compat)
    fun saveResult(correct: Int, incorrect: Int, won: Boolean) {
        if (!isMultiplayer && !resultSaved) {
            resultSaved = true
            viewModelScope.launch {
                runCatching {
                    userRepo.saveKoZnaZnaResult(correct, incorrect)
                    userRepo.saveGameResult(won)
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel(); opponentJob?.cancel(); mpListener?.remove()
    }
}

class KoZnaZnaViewModelFactory(
    private val sessionId: String,
    private val isPlayer1: Boolean,
    private val gameIdx: Int
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        KoZnaZnaViewModel(sessionId = sessionId, isPlayer1 = isPlayer1, gameIdx = gameIdx) as T
}
