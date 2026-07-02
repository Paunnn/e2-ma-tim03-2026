package com.tim03.slagalica.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.ListenerRegistration
import com.tim03.slagalica.data.model.AsocijacijeQuestion
import com.tim03.slagalica.data.model.ColumnData
import com.tim03.slagalica.data.repository.AsocijacijeRepository
import com.tim03.slagalica.data.repository.MultiplayerGameRepository
import com.tim03.slagalica.data.repository.PartijaSessionRepository
import com.tim03.slagalica.data.repository.UserRepository
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
    val hasRevealedThisTurn: Boolean = false,
    val revealAll: Boolean = false,
    val waitingMessage: String? = null
)

class AsocijacijeViewModel(
    private val sessionId: String = "",
    private val isPlayer1: Boolean = true,
    private val gameIdx: Int = -1,
    private val repo: AsocijacijeRepository = AsocijacijeRepository(),
    private val userRepo: UserRepository = UserRepository(),
    private val mpRepo: MultiplayerGameRepository = MultiplayerGameRepository(),
    private val sessionRepo: PartijaSessionRepository = PartijaSessionRepository()
) : ViewModel() {

    val isMultiplayer = sessionId.isNotEmpty()

    private val _uiState = MutableStateFlow(AsocijacijeUiState())
    val uiState: StateFlow<AsocijacijeUiState> = _uiState.asStateFlow()

    private val _username = MutableStateFlow("Igrač")
    val username: StateFlow<String> = _username.asStateFlow()

    private var timerJob: Job? = null
    private var opponentJob: Job? = null
    private var mpListener: ListenerRegistration? = null
    private var forfeitListener: ListenerRegistration? = null
    private var opponentForfeited = false
    private var lastHandledPhase = ""
    private var lastGameData: Map<String, Any> = emptyMap()

    private var round1Question: AsocijacijeQuestion? = null
    private var round2Question: AsocijacijeQuestion? = null
    private var myGuessesSolved = 0
    private var myGuessesTotal = 0
    private var resultSaved = false
    private var r1TransitionDone = false
    private var r2TransitionDone = false

    // Base scores carried from previous rounds (for live display during R2).
    private var myBaseScore = 0
    private var oppBaseScore = 0

    init {
        loadGame()
        viewModelScope.launch {
            runCatching { userRepo.getCurrentUser()?.username?.also { _username.value = it } }
        }
        if (isMultiplayer) {
            forfeitListener = sessionRepo.listenToForfeit(sessionId) { forfeitedByPlayer1 ->
                if (forfeitedByPlayer1 != isPlayer1) onOpponentForfeited()
            }
        }
    }

    // If the opponent leaves, their turns are simply skipped: whenever the turn lands on
    // them, reclaim it immediately so the remaining player keeps playing without waiting.
    // (The isWriter override in onTimerExpired separately covers the round transition,
    // since the designated writer role may have belonged to the player who left.)
    private fun onOpponentForfeited() {
        opponentForfeited = true
        reclaimTurnFromAbsentOpponent()
    }

    private fun reclaimTurnFromAbsentOpponent() {
        val state = _uiState.value
        if (state.phase != AsocijacijePhase.OPPONENT_TURN) return
        _uiState.value = state.copy(
            phase = AsocijacijePhase.MY_TURN, isMyTurn = true, hasRevealedThisTurn = false
        )
        mpRepo.updateGameData(sessionId, mapOf("activePlayer" to if (isPlayer1) 1L else 2L))
    }

    private fun loadGame() {
        viewModelScope.launch {
            _uiState.value = AsocijacijeUiState(phase = AsocijacijePhase.LOADING)
            if (isMultiplayer) loadMultiplayerGame()
            else {
                val (q1, q2) = repo.getTwoRandomQuestions()
                round1Question = q1 ?: fallbackQuestion(1)
                round2Question = q2 ?: fallbackQuestion(2)
                startSinglePlayerRound(1)
            }
        }
    }

    // ─── Multiplayer ─────────────────────────────────────────────────────────
    // Both players see the SAME board each round. They alternate opening fields.
    // P1 opens first in R1; P2 opens first in R2.
    // Whoever guesses the final answer in a round scores: 31 – openedFields.size.
    // Phases: as_r1 → as_r2 → as_done

    // Normally P1 always sets up each mini-game. But if P1 forfeited the partija before
    // this game was ever reached, nobody else would - so P2 must take over as initializer.
    private suspend fun isFallbackInitializer(): Boolean {
        if (isPlayer1) return false
        val session = sessionRepo.getSession(sessionId) ?: return false
        return session.status == "forfeited" && session.forfeitedBy == "player1"
    }

    private suspend fun loadMultiplayerGame() {
        if (isPlayer1) {
            val (q1, q2) = repo.getTwoRandomQuestions()
            round1Question = q1 ?: fallbackQuestion(1)
            round2Question = q2 ?: fallbackQuestion(2)
            mpRepo.initGame(
                sessionId, gameIdx, "as_r1",
                mapOf(
                    "type" to "as",
                    "q1Id" to (round1Question!!.id),
                    "q2Id" to (round2Question!!.id),
                    "activePlayer" to 1L,
                    "openedFields" to emptyList<Long>(),
                    "solvedColumns" to emptyList<Long>(),
                    "finalSolved" to false,
                    "revealAll" to false,
                    "r1P1Score" to 0L, "r1P2Score" to 0L,
                    "r2P1Score" to 0L, "r2P2Score" to 0L
                )
            )
            _uiState.value = AsocijacijeUiState(
                phase = AsocijacijePhase.MY_TURN,
                currentQuestion = round1Question,
                isMyTurn = true,
                timeLeft = 120
            )
            startTimer(120)
            listenForMultiplayerPhase()
        } else if (isFallbackInitializer()) {
            // P1 is gone - I set up round 1 myself, then immediately treat their
            // never-going-to-happen turn as forfeited so I can play.
            val (q1, q2) = repo.getTwoRandomQuestions()
            round1Question = q1 ?: fallbackQuestion(1)
            round2Question = q2 ?: fallbackQuestion(2)
            mpRepo.initGame(
                sessionId, gameIdx, "as_r1",
                mapOf(
                    "type" to "as",
                    "q1Id" to (round1Question!!.id),
                    "q2Id" to (round2Question!!.id),
                    "activePlayer" to 1L,
                    "openedFields" to emptyList<Long>(),
                    "solvedColumns" to emptyList<Long>(),
                    "finalSolved" to false,
                    "revealAll" to false,
                    "r1P1Score" to 0L, "r1P2Score" to 0L,
                    "r2P1Score" to 0L, "r2P2Score" to 0L
                )
            )
            _uiState.value = AsocijacijeUiState(
                phase = AsocijacijePhase.OPPONENT_TURN,
                currentQuestion = round1Question,
                isMyTurn = false,
                timeLeft = 120
            )
            listenForMultiplayerPhase()
            onOpponentForfeited()
        } else {
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
            // If the turn just landed on the absent opponent (wrong guess, pass, or a new
            // round starting on their turn), take it right back.
            if (opponentForfeited) reclaimTurnFromAbsentOpponent()
        }
    }

    private fun handleMpPhase(phase: String, data: Map<String, Any>) {
        when (phase) {
            "as_r1" -> {
                if (!isPlayer1) {
                    val q1Id = data["q1Id"] as? String ?: return
                    val q2Id = data["q2Id"] as? String ?: return
                    viewModelScope.launch {
                        round1Question = repo.getQuestionById(q1Id) ?: fallbackQuestion(1)
                        round2Question = repo.getQuestionById(q2Id) ?: fallbackQuestion(2)
                        // Apply any board state that arrived before loading finished.
                        val openedSet = openedFieldsFromData(lastGameData)
                        val solvedSet = solvedColumnsFromData(lastGameData)
                        val activePlayer = (lastGameData["activePlayer"] as? Long)?.toInt() ?: 1
                        val isMyTurnNow = activePlayer == 2
                        _uiState.value = AsocijacijeUiState(
                            phase = if (isMyTurnNow) AsocijacijePhase.MY_TURN else AsocijacijePhase.OPPONENT_TURN,
                            currentQuestion = round1Question,
                            revealed = revealedFromSet(openedSet),
                            columnSolved = solvedFromSet(solvedSet),
                            isMyTurn = isMyTurnNow,
                            timeLeft = 120
                        )
                        startTimer(120)
                    }
                }
            }
            "as_r2" -> {
                timerJob?.cancel()
                r1TransitionDone = true
                val r1P1 = (data["r1P1Score"] as? Long)?.toInt() ?: 0
                val r1P2 = (data["r1P2Score"] as? Long)?.toInt() ?: 0
                myBaseScore = if (isPlayer1) r1P1 else r1P2
                oppBaseScore = if (isPlayer1) r1P2 else r1P1
                val q2 = round2Question ?: fallbackQuestion(2)
                val isMyTurnNow = !isPlayer1  // P2 opens first in R2
                _uiState.value = AsocijacijeUiState(
                    phase = if (isMyTurnNow) AsocijacijePhase.MY_TURN else AsocijacijePhase.OPPONENT_TURN,
                    currentQuestion = q2,
                    currentRound = 2,
                    myScore = myBaseScore,
                    opponentScore = oppBaseScore,
                    isMyTurn = isMyTurnNow,
                    timeLeft = 120
                )
                startTimer(120)
            }
            "as_done" -> {
                timerJob?.cancel()
                r2TransitionDone = true
                val r1P1 = (data["r1P1Score"] as? Long)?.toInt() ?: 0
                val r1P2 = (data["r1P2Score"] as? Long)?.toInt() ?: 0
                val r2P1 = (data["r2P1Score"] as? Long)?.toInt() ?: 0
                val r2P2 = (data["r2P2Score"] as? Long)?.toInt() ?: 0
                val myFinal = if (isPlayer1) r1P1 + r2P1 else r1P2 + r2P2
                val oppFinal = if (isPlayer1) r1P2 + r2P2 else r1P1 + r2P1
                _uiState.value = _uiState.value.copy(
                    myScore = myFinal, opponentScore = oppFinal,
                    phase = AsocijacijePhase.GAME_OVER, revealAll = true
                )
                saveResult(myFinal, oppFinal)
            }
        }
    }

    // Syncs the shared board state (field opens, column solves, turn, scores) to both players.
    private fun handleMpLiveUpdate(data: Map<String, Any>) {
        if (_uiState.value.phase == AsocijacijePhase.GAME_OVER) return
        val openedSet = openedFieldsFromData(data)
        val solvedSet = solvedColumnsFromData(data)
        val activePlayer = (data["activePlayer"] as? Long)?.toInt() ?: 1
        val newIsMyTurn = if (isPlayer1) activePlayer == 1 else activePlayer == 2
        val prevIsMyTurn = _uiState.value.isMyTurn

        val finalSolved = data["finalSolved"] as? Boolean ?: false
        val revealAll = data["revealAll"] as? Boolean ?: false

        _uiState.value = _uiState.value.copy(
            revealed = revealedFromSet(openedSet),
            columnSolved = solvedFromSet(solvedSet),
            isMyTurn = newIsMyTurn,
            phase = if (newIsMyTurn) AsocijacijePhase.MY_TURN else AsocijacijePhase.OPPONENT_TURN,
            finalSolved = finalSolved || _uiState.value.finalSolved,
            revealAll = revealAll || _uiState.value.revealAll,
            // Reset hasRevealedThisTurn when the turn becomes mine.
            hasRevealedThisTurn = if (!prevIsMyTurn && newIsMyTurn) false else _uiState.value.hasRevealedThisTurn
        )
    }

    // ─── Timer ───────────────────────────────────────────────────────────────

    private fun startTimer(seconds: Int) {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            var t = seconds
            while (t > 0) {
                delay(1000L); t--
                _uiState.value = _uiState.value.copy(timeLeft = t)
            }
            onTimerExpired()
        }
    }

    private fun onTimerExpired() {
        val state = _uiState.value
        if (state.phase == AsocijacijePhase.GAME_OVER) return
        _uiState.value = state.copy(revealAll = true, message = "Vreme isteklo!")
        if (isMultiplayer) mpRepo.updateGameData(sessionId, mapOf("revealAll" to true))
        viewModelScope.launch {
            delay(2500L)
            if (_uiState.value.phase == AsocijacijePhase.GAME_OVER) return@launch
            val round = _uiState.value.currentRound
            if (isMultiplayer) {
                // Designated writer: P1 for R1, P2 for R2.
                val isWriter = (round == 1 && isPlayer1) || (round == 2 && !isPlayer1) || opponentForfeited
                val transitionDone = if (round == 1) r1TransitionDone else r2TransitionDone
                if (isWriter && !transitionDone) {
                    val myR = _uiState.value.myScore - (if (round == 2) myBaseScore else 0)
                    val oppR = _uiState.value.opponentScore - (if (round == 2) oppBaseScore else 0)
                    val p1R = if (isPlayer1) myR.toLong() else oppR.toLong()
                    val p2R = if (isPlayer1) oppR.toLong() else myR.toLong()
                    if (round == 1) {
                        r1TransitionDone = true
                        mpRepo.setPhaseAndData(sessionId, "as_r2", mapOf(
                            "r1P1Score" to p1R, "r1P2Score" to p2R,
                            "activePlayer" to 2L,
                            "openedFields" to emptyList<Long>(),
                            "solvedColumns" to emptyList<Long>(),
                            "finalSolved" to false, "revealAll" to false
                        ))
                    } else {
                        r2TransitionDone = true
                        mpRepo.setPhaseAndData(sessionId, "as_done", mapOf(
                            "r2P1Score" to p1R, "r2P2Score" to p2R
                        ))
                    }
                }
            } else {
                if (round == 1) startSinglePlayerRound(2) else endGame()
            }
        }
    }

    // ─── Player actions ──────────────────────────────────────────────────────

    fun revealField(col: Int, row: Int) {
        val state = _uiState.value
        if (!state.isMyTurn || state.phase != AsocijacijePhase.MY_TURN) return
        if (state.revealed[col][row] || state.columnSolved[col] || state.hasRevealedThisTurn) return

        val newRevealed = state.revealed.mapIndexed { c, rows ->
            rows.mapIndexed { r, v -> if (c == col && r == row) true else v }
        }
        _uiState.value = state.copy(revealed = newRevealed, hasRevealedThisTurn = true, message = null)

        if (isMultiplayer) {
            val openedFields = (0..3).flatMap { c ->
                (0..3).mapNotNull { r -> if (newRevealed[c][r]) (c * 4 + r).toLong() else null }
            }
            mpRepo.updateGameData(sessionId, mapOf("openedFields" to openedFields))
        }
    }

    fun passTurn() {
        val state = _uiState.value
        if (!state.isMyTurn) return
        if (isMultiplayer) {
            val newActive = if (isPlayer1) 2L else 1L
            _uiState.value = state.copy(
                isMyTurn = false, phase = AsocijacijePhase.OPPONENT_TURN, hasRevealedThisTurn = false
            )
            mpRepo.updateGameData(sessionId, mapOf("activePlayer" to newActive))
        } else {
            passToOpponent()
        }
    }

    fun guessColumn(col: Int, answer: String) {
        val state = _uiState.value
        if (!state.isMyTurn || state.columnSolved[col] || state.currentQuestion == null) return

        myGuessesTotal++
        val correct = normalize(answer) == normalize(state.currentQuestion.columns()[col].solution)

        if (correct) {
            myGuessesSolved++
            val newSolved = state.columnSolved.toMutableList().also { it[col] = true }
            _uiState.value = state.copy(
                columnSolved = newSolved,
                hasRevealedThisTurn = true,
                message = "Tačno! Rešenje kolone ${('A' + col)}: ${state.currentQuestion.columns()[col].solution}"
            )
            if (isMultiplayer) {
                val solvedList = newSolved.indices.filter { newSolved[it] }.map { it.toLong() }
                mpRepo.updateGameData(sessionId, mapOf("solvedColumns" to solvedList))
            } else if (!isMultiplayer) {
                // Single player: opponent doesn't guess in real time; score tracked in guessFinal only.
            }
        } else {
            _uiState.value = state.copy(message = "Netačno rešenje!")
            if (isMultiplayer) {
                val newActive = if (isPlayer1) 2L else 1L
                _uiState.value = _uiState.value.copy(
                    isMyTurn = false, phase = AsocijacijePhase.OPPONENT_TURN, hasRevealedThisTurn = false
                )
                mpRepo.updateGameData(sessionId, mapOf("activePlayer" to newActive))
            } else {
                passToOpponent()
            }
        }
    }

    fun guessFinal(answer: String) {
        val state = _uiState.value
        if (!state.isMyTurn || state.finalSolved || state.currentQuestion == null) return

        myGuessesTotal++
        val correct = normalize(answer) == normalize(state.currentQuestion.finalSolution)

        if (correct) {
            myGuessesSolved++
            timerJob?.cancel(); opponentJob?.cancel()
            val openedCount = state.revealed.sumOf { col -> col.count { it } }
            val score = maxOf(31 - openedCount, 7)
            val newMyScore = state.myScore + score

            _uiState.value = state.copy(
                finalSolved = true, myScore = newMyScore, revealAll = true,
                message = "+$score bodova! Konačno rešenje: ${state.currentQuestion.finalSolution}"
            )

            if (isMultiplayer) {
                mpRepo.updateGameData(sessionId, mapOf("finalSolved" to true, "revealAll" to true))
                val round = state.currentRound
                val myRoundScore = newMyScore - if (round == 2) myBaseScore else 0
                val oppRoundScore = state.opponentScore - if (round == 2) oppBaseScore else 0
                val p1Round = if (isPlayer1) myRoundScore.toLong() else oppRoundScore.toLong()
                val p2Round = if (isPlayer1) oppRoundScore.toLong() else myRoundScore.toLong()
                viewModelScope.launch {
                    delay(2500L)
                    if (round == 1 && !r1TransitionDone) {
                        r1TransitionDone = true
                        mpRepo.setPhaseAndData(sessionId, "as_r2", mapOf(
                            "r1P1Score" to p1Round, "r1P2Score" to p2Round,
                            "activePlayer" to 2L,
                            "openedFields" to emptyList<Long>(),
                            "solvedColumns" to emptyList<Long>(),
                            "finalSolved" to false, "revealAll" to false
                        ))
                    } else if (round == 2 && !r2TransitionDone) {
                        r2TransitionDone = true
                        mpRepo.setPhaseAndData(sessionId, "as_done", mapOf(
                            "r2P1Score" to p1Round, "r2P2Score" to p2Round
                        ))
                    }
                }
            } else {
                viewModelScope.launch {
                    delay(2500L)
                    if (state.currentRound == 1) startSinglePlayerRound(2) else endGame()
                }
            }
        } else {
            _uiState.value = state.copy(message = "Netačno konačno rešenje!")
            if (isMultiplayer) {
                val newActive = if (isPlayer1) 2L else 1L
                _uiState.value = _uiState.value.copy(
                    isMyTurn = false, phase = AsocijacijePhase.OPPONENT_TURN, hasRevealedThisTurn = false
                )
                mpRepo.updateGameData(sessionId, mapOf("activePlayer" to newActive))
            } else {
                passToOpponent()
            }
        }
    }

    // ─── Single-player helpers ────────────────────────────────────────────────

    private fun startSinglePlayerRound(round: Int) {
        timerJob?.cancel(); opponentJob?.cancel()
        val question = if (round == 1) round1Question else round2Question
        val myTurnFirst = round == 1
        _uiState.value = AsocijacijeUiState(
            phase = if (myTurnFirst) AsocijacijePhase.MY_TURN else AsocijacijePhase.OPPONENT_TURN,
            currentQuestion = question,
            currentRound = round,
            myScore = _uiState.value.myScore,
            opponentScore = _uiState.value.opponentScore,
            isMyTurn = myTurnFirst
        )
        startTimer(120)
        if (!myTurnFirst) scheduleOpponentTurn()
    }

    private fun passToOpponent() {
        _uiState.value = _uiState.value.copy(
            phase = AsocijacijePhase.OPPONENT_TURN, isMyTurn = false, hasRevealedThisTurn = false
        )
        scheduleOpponentTurn()
    }

    private fun scheduleOpponentTurn() {
        opponentJob?.cancel()
        opponentJob = viewModelScope.launch {
            delay((1500L..2500L).random())
            if (_uiState.value.phase == AsocijacijePhase.GAME_OVER) return@launch

            // Open a random unrevealed field.
            val state = _uiState.value
            val unrevealedCells = (0..3).flatMap { c ->
                if (!state.columnSolved[c]) (0..3).mapNotNull { r -> if (!state.revealed[c][r]) Pair(c, r) else null }
                else emptyList()
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

            // Occasionally guess a column solution.
            val unsolvedCols = (0..3).filter { !_uiState.value.columnSolved[it] }
            if (unsolvedCols.isNotEmpty() && Math.random() < 0.30) {
                val col = unsolvedCols.filter { _uiState.value.revealed[it].count { v -> v } >= 2 }.randomOrNull()
                if (col != null) {
                    val newSolved = _uiState.value.columnSolved.toMutableList().also { it[col] = true }
                    _uiState.value = _uiState.value.copy(
                        columnSolved = newSolved,
                        message = "Protivnik rešio kolonu ${('A' + col)}!"
                    )
                    delay(800L)
                }
            }
            if (_uiState.value.phase == AsocijacijePhase.GAME_OVER) return@launch

            // Occasionally guess the final answer.
            if (_uiState.value.columnSolved.count { it } >= 2 && Math.random() < 0.15) {
                tryOpponentGuessFinal(); return@launch
            }

            if (_uiState.value.phase != AsocijacijePhase.GAME_OVER) {
                _uiState.value = _uiState.value.copy(
                    phase = AsocijacijePhase.MY_TURN, isMyTurn = true, hasRevealedThisTurn = false
                )
            }
        }
    }

    private fun tryOpponentGuessFinal() {
        val state = _uiState.value
        val openedCount = state.revealed.sumOf { col -> col.count { it } }
        val score = maxOf(31 - openedCount, 7)
        _uiState.value = state.copy(
            finalSolved = true, revealAll = true,
            opponentScore = state.opponentScore + score,
            message = "Protivnik pogodio konačno rešenje! +$score bodova"
        )
        viewModelScope.launch {
            delay(2500L)
            if (state.currentRound == 1) startSinglePlayerRound(2) else endGame()
        }
    }

    private fun endGame() {
        timerJob?.cancel(); opponentJob?.cancel()
        _uiState.value = _uiState.value.copy(phase = AsocijacijePhase.GAME_OVER, revealAll = true)
        saveResult(_uiState.value.myScore, _uiState.value.opponentScore)
    }

    private fun saveResult(myScore: Int, oppScore: Int) {
        if (!resultSaved) {
            resultSaved = true
            val solved = myGuessesSolved; val total = myGuessesTotal
            viewModelScope.launch {
                runCatching {
                    userRepo.saveAsocijacijeResult(solved, total)
                    userRepo.saveGameResult(won = myScore > oppScore)
                }
            }
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private fun normalize(s: String): String =
        s.trim().lowercase()
            .replace('ć', 'c').replace('č', 'c')
            .replace('š', 's').replace('ž', 'z').replace('đ', 'd')
            .replace("dž", "dz")

    private fun openedFieldsFromData(data: Map<String, Any>): Set<Int> =
        (data["openedFields"] as? List<*>)?.mapNotNull { (it as? Long)?.toInt() }?.toSet() ?: emptySet()

    private fun solvedColumnsFromData(data: Map<String, Any>): Set<Int> =
        (data["solvedColumns"] as? List<*>)?.mapNotNull { (it as? Long)?.toInt() }?.toSet() ?: emptySet()

    private fun revealedFromSet(openedFields: Set<Int>): List<List<Boolean>> =
        List(4) { col -> List(4) { row -> (col * 4 + row) in openedFields } }

    private fun solvedFromSet(solved: Set<Int>): List<Boolean> =
        List(4) { col -> col in solved }

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
        timerJob?.cancel(); opponentJob?.cancel(); mpListener?.remove(); forfeitListener?.remove()
    }
}

class AsocijacijeViewModelFactory(
    private val sessionId: String,
    private val isPlayer1: Boolean,
    private val gameIdx: Int
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        AsocijacijeViewModel(sessionId = sessionId, isPlayer1 = isPlayer1, gameIdx = gameIdx) as T
}
