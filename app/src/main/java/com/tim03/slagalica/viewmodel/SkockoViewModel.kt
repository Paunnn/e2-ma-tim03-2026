package com.tim03.slagalica.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.ListenerRegistration
import com.tim03.slagalica.data.repository.MultiplayerGameRepository
import com.tim03.slagalica.data.repository.PartijaSessionRepository
import com.tim03.slagalica.data.repository.UserRepository
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
    val message: String? = null,
    val showSolution: Boolean = false,
    val revealedSolution: List<Int> = emptyList(),
    val bonusAttemptResult: SkockoAttemptResult? = null
)

class SkockoViewModel(
    private val sessionId: String = "",
    private val isPlayer1: Boolean = true,
    private val gameIdx: Int = -1,
    private val izazovMode: Boolean = false,
    private val userRepo: UserRepository = UserRepository(),
    private val mpRepo: MultiplayerGameRepository = MultiplayerGameRepository(),
    private val sessionRepo: PartijaSessionRepository = PartijaSessionRepository()
) : ViewModel() {

    val isMultiplayer = sessionId.isNotEmpty()

    private val _uiState = MutableStateFlow(SkockoUiState())
    val uiState: StateFlow<SkockoUiState> = _uiState.asStateFlow()

    private val _username = MutableStateFlow("Igrač")
    val username: StateFlow<String> = _username.asStateFlow()

    private var timerJob: Job? = null
    private var opponentJob: Job? = null
    private var mpListener: ListenerRegistration? = null
    private var forfeitListener: ListenerRegistration? = null
    private var opponentForfeited = false
    // Firestore phases already force-written on the absent opponent's behalf.
    private val forcedWrites = mutableSetOf<String>()

    private var solvedAtAttemptNum: Int? = null
    private var resultSaved = false
    private var lastHandledPhase = ""
    // In multiplayer: sol1=P1's R1 solution, sol2=P2's R2 solution
    private var mp_sol1: List<Int> = emptyList()
    private var mp_sol2: List<Int> = emptyList()

    init {
        if (isMultiplayer) startMultiplayerGame() else startGame()
        viewModelScope.launch {
            runCatching { userRepo.getCurrentUser()?.username?.also { _username.value = it } }
        }
        if (isMultiplayer) {
            forfeitListener = sessionRepo.listenToForfeit(sessionId) { forfeitedByPlayer1 ->
                if (forfeitedByPlayer1 != isPlayer1) {
                    opponentForfeited = true
                    forceOpponentTurnEndDueToForfeit()
                }
            }
        }
    }

    // ─── Single-player ───

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

    // ─── Multiplayer ───

    // Normally P1 always sets up each mini-game. But if P1 forfeited the partija before
    // this game was ever reached, nobody else would - so P2 must take over as initializer.
    private suspend fun isFallbackInitializer(): Boolean {
        if (isPlayer1) return false
        val session = sessionRepo.getSession(sessionId) ?: return false
        return session.status == "forfeited" && session.forfeitedBy == "player1"
    }

    private fun startMultiplayerGame() {
        viewModelScope.launch {
            if (isPlayer1) {
                val sol1 = generateSolution()
                val sol2 = generateSolution()
                mp_sol1 = sol1; mp_sol2 = sol2
                mpRepo.initGame(
                    sessionId, gameIdx, "sk_r1",
                    mapOf(
                        "type" to "sk",
                        "sol1" to sol1.map { it.toLong() },
                        "sol2" to sol2.map { it.toLong() },
                        "r1Score" to 0L, "r1Bonus" to 0L,
                        "r2Score" to 0L, "r2Bonus" to 0L,
                        "r1Attempts" to emptyList<Any>(),
                        "r2Attempts" to emptyList<Any>(),
                        "showSolution" to false,
                        "solutionSymbols" to emptyList<Long>()
                    )
                )
                _uiState.value = SkockoUiState(
                    phase = SkockoPhase.MY_TURN,
                    mySolution = sol1,
                    opponentSolution = sol2,
                    currentRound = 1
                )
                startTimer(30)
                listenForMultiplayerPhase()
            } else if (isFallbackInitializer()) {
                // P1 is gone - I set up round 1 myself, then immediately treat their
                // never-going-to-happen turn as forfeited (0 points) so I can play.
                val sol1 = generateSolution()
                val sol2 = generateSolution()
                mp_sol1 = sol1; mp_sol2 = sol2
                mpRepo.initGame(
                    sessionId, gameIdx, "sk_r1",
                    mapOf(
                        "type" to "sk",
                        "sol1" to sol1.map { it.toLong() },
                        "sol2" to sol2.map { it.toLong() },
                        "r1Score" to 0L, "r1Bonus" to 0L,
                        "r2Score" to 0L, "r2Bonus" to 0L,
                        "r1Attempts" to emptyList<Any>(),
                        "r2Attempts" to emptyList<Any>(),
                        "showSolution" to false,
                        "solutionSymbols" to emptyList<Long>()
                    )
                )
                _uiState.value = SkockoUiState(
                    phase = SkockoPhase.WAITING_OPPONENT,
                    mySolution = sol2,
                    opponentSolution = sol1,
                    currentRound = 1,
                    timeLeft = 30
                )
                listenForMultiplayerPhase()
                forceOpponentTurnEndDueToForfeit()
            } else {
                listenForMultiplayerPhase()
            }
        }
    }

    private fun listenForMultiplayerPhase() {
        mpListener?.remove()
        mpListener = mpRepo.listenToGameState(sessionId) { gIdx, phase, data ->
            if (gIdx != gameIdx) return@listenToGameState
            if (phase != lastHandledPhase) {
                lastHandledPhase = phase
                handleMpPhase(phase, data)
            } else {
                handleMpLiveUpdate(phase, data)
            }
            // Re-check after every state change: a forced transition lands me in the NEXT
            // waiting phase, which must also be skipped - the absent opponent never writes.
            if (opponentForfeited) forceOpponentTurnEndDueToForfeit()
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun handleMpPhase(phase: String, data: Map<String, Any>) {
        fun parseList(key: String) = (data[key] as? List<*>)?.map { (it as? Long)?.toInt() ?: 0 } ?: emptyList()

        when (phase) {
            "sk_r1" -> {
                if (!isPlayer1) {
                    val sol1 = parseList("sol1")
                    val sol2 = parseList("sol2")
                    mp_sol1 = sol1; mp_sol2 = sol2
                    _uiState.value = SkockoUiState(
                        phase = SkockoPhase.WAITING_OPPONENT,
                        mySolution = sol2,
                        opponentSolution = sol1,
                        currentRound = 1,
                        timeLeft = 30,
                        message = "Protivnik igra rundu 1..."
                    )
                    startWaitingTimer(30)
                }
            }
            "sk_r1bonus" -> {
                timerJob?.cancel(); opponentJob?.cancel()
                if (!isPlayer1) {
                    val sol1 = if (mp_sol1.isNotEmpty()) mp_sol1 else parseList("sol1")
                    val existingOppAttempts = _uiState.value.opponentAttempts
                    _uiState.value = _uiState.value.copy(
                        phase = SkockoPhase.MY_BONUS,
                        opponentSolution = sol1,
                        opponentAttempts = existingOppAttempts,
                        currentInput = emptyList(),
                        timeLeft = 10,
                        message = "Protivnik nije pogodio! Tvoj bonus (1 pokušaj)!"
                    )
                    startTimer(10)
                } else {
                    _uiState.value = _uiState.value.copy(
                        phase = SkockoPhase.OPPONENT_BONUS_R1,
                        currentInput = emptyList(),
                        timeLeft = 12,
                        message = "Protivnik ima bonus pokušaj!"
                    )
                    startWaitingTimer(12)
                }
            }
            "sk_r2" -> {
                timerJob?.cancel(); opponentJob?.cancel()
                val r1Score = (data["r1Score"] as? Long)?.toInt() ?: 0
                val r1Bonus = (data["r1Bonus"] as? Long)?.toInt() ?: 0
                val myR1 = if (isPlayer1) r1Score else r1Bonus
                val oppR1 = if (isPlayer1) r1Bonus else r1Score
                if (!isPlayer1) {
                    val sol2 = if (mp_sol2.isNotEmpty()) mp_sol2 else parseList("sol2")
                    _uiState.value = _uiState.value.copy(
                        phase = SkockoPhase.MY_TURN,
                        mySolution = sol2,
                        currentRound = 2,
                        myAttempts = emptyList(),
                        opponentAttempts = emptyList(),
                        currentInput = emptyList(),
                        timeLeft = 30,
                        myScore = myR1,
                        opponentScore = oppR1,
                        showSolution = false,
                        bonusAttemptResult = null,
                        message = "Runda 2: Tvoj red!"
                    )
                    startTimer(30)
                } else {
                    val sol2 = if (mp_sol2.isNotEmpty()) mp_sol2 else parseList("sol2")
                    _uiState.value = _uiState.value.copy(
                        phase = SkockoPhase.WAITING_OPPONENT,
                        opponentSolution = sol2,
                        currentRound = 2,
                        myAttempts = emptyList(),
                        opponentAttempts = emptyList(),
                        currentInput = emptyList(),
                        timeLeft = 30,
                        myScore = myR1,
                        opponentScore = oppR1,
                        showSolution = false,
                        bonusAttemptResult = null,
                        message = "Protivnik igra rundu 2..."
                    )
                    startWaitingTimer(30)
                }
            }
            "sk_r2bonus" -> {
                timerJob?.cancel(); opponentJob?.cancel()
                if (isPlayer1) {
                    val sol2 = if (mp_sol2.isNotEmpty()) mp_sol2 else parseList("sol2")
                    val existingOppAttempts = _uiState.value.opponentAttempts
                    _uiState.value = _uiState.value.copy(
                        phase = SkockoPhase.MY_BONUS,
                        opponentSolution = sol2,
                        opponentAttempts = existingOppAttempts,
                        currentInput = emptyList(),
                        timeLeft = 10,
                        message = "Protivnik nije pogodio! Tvoj bonus (1 pokušaj)!"
                    )
                    startTimer(10)
                } else {
                    _uiState.value = _uiState.value.copy(
                        phase = SkockoPhase.OPPONENT_BONUS_R1,
                        currentInput = emptyList(),
                        timeLeft = 12,
                        message = "Protivnik ima bonus pokušaj!"
                    )
                    startWaitingTimer(12)
                }
            }
            "sk_done" -> {
                timerJob?.cancel(); opponentJob?.cancel()
                val r1Score = (data["r1Score"] as? Long)?.toInt() ?: 0
                val r1Bonus = (data["r1Bonus"] as? Long)?.toInt() ?: 0
                val r2Score = (data["r2Score"] as? Long)?.toInt() ?: 0
                val r2Bonus = (data["r2Bonus"] as? Long)?.toInt() ?: 0
                val myFinal = if (isPlayer1) r1Score + r2Bonus else r2Score + r1Bonus
                val oppFinal = if (isPlayer1) r2Score + r1Bonus else r1Score + r2Bonus
                val sol2 = if (mp_sol2.isNotEmpty()) mp_sol2 else parseList("sol2")
                _uiState.value = _uiState.value.copy(
                    myScore = myFinal,
                    opponentScore = oppFinal,
                    revealedSolution = sol2,
                    showSolution = false
                )
                endGame()
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun handleMpLiveUpdate(phase: String, data: Map<String, Any>) {
        val showSolution = data["showSolution"] as? Boolean ?: false
        val solutionSymbols = (data["solutionSymbols"] as? List<*>)
            ?.map { (it as? Long)?.toInt() ?: 0 } ?: emptyList()

        when (phase) {
            "sk_r1" -> if (!isPlayer1) {
                val attempts = (data["r1Attempts"] as? List<*>)?.let { mapsToAttempts(it) } ?: return
                val update = _uiState.value.copy(opponentAttempts = attempts)
                _uiState.value = if (showSolution && solutionSymbols.isNotEmpty())
                    update.copy(showSolution = true, revealedSolution = solutionSymbols)
                else if (!showSolution)
                    update.copy(showSolution = false)
                else update
            }
            "sk_r1bonus", "sk_r2bonus" -> {
                val bonusMap = data["bonusAttempt"] as? Map<*, *>
                val bonusResult = bonusMap?.let { m ->
                    val s = (m["s"] as? List<*>)?.map { (it as? Long)?.toInt() ?: 0 } ?: return@let null
                    val p = (m["p"] as? Long)?.toInt() ?: return@let null
                    val c = (m["c"] as? Long)?.toInt() ?: return@let null
                    SkockoAttemptResult(s, p, c)
                }
                var next = _uiState.value
                if (bonusResult != null && next.bonusAttemptResult == null) {
                    next = next.copy(bonusAttemptResult = bonusResult)
                }
                next = if (showSolution && solutionSymbols.isNotEmpty())
                    next.copy(showSolution = true, revealedSolution = solutionSymbols)
                else if (!showSolution && next.showSolution)
                    next.copy(showSolution = false)
                else next
                _uiState.value = next
            }
            "sk_r2" -> if (isPlayer1) {
                val attempts = (data["r2Attempts"] as? List<*>)?.let { mapsToAttempts(it) } ?: return
                val update = _uiState.value.copy(opponentAttempts = attempts)
                _uiState.value = if (showSolution && solutionSymbols.isNotEmpty())
                    update.copy(showSolution = true, revealedSolution = solutionSymbols)
                else if (!showSolution)
                    update.copy(showSolution = false)
                else update
            }
        }
    }

    // ─── Timers ───

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

    private fun startWaitingTimer(seconds: Int) {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            var t = seconds
            while (t > 0) {
                delay(1000L)
                t--
                val ph = _uiState.value.phase
                if (ph != SkockoPhase.WAITING_OPPONENT && ph != SkockoPhase.OPPONENT_BONUS_R1) break
                _uiState.value = _uiState.value.copy(timeLeft = t)
            }
        }
    }

    private fun onTimerExpired() {
        when (_uiState.value.phase) {
            SkockoPhase.MY_TURN -> {
                if (isMultiplayer) {
                    val round = _uiState.value.currentRound
                    viewModelScope.launch {
                        delay(300L)
                        if (isPlayer1 && round == 1) {
                            mpRepo.setPhaseAndData(sessionId, "sk_r1bonus",
                                mapOf("r1Score" to 0L, "showSolution" to false))
                        } else if (!isPlayer1 && round == 2) {
                            mpRepo.setPhaseAndData(sessionId, "sk_r2bonus",
                                mapOf("r2Score" to 0L, "showSolution" to false))
                        }
                    }
                } else {
                    startOpponentBonusR1()
                }
            }
            SkockoPhase.MY_BONUS -> {
                if (isMultiplayer) {
                    val solution = _uiState.value.opponentSolution
                    viewModelScope.launch {
                        _uiState.value = _uiState.value.copy(showSolution = true, revealedSolution = solution)
                        mpRepo.updateGameData(sessionId, mapOf(
                            "showSolution" to true,
                            "solutionSymbols" to solution.map { it.toLong() }
                        ))
                        delay(2500L)
                        _uiState.value = _uiState.value.copy(showSolution = false)
                        submitBonusToFirestore(0)
                    }
                } else endGame()
            }
            else -> {}
        }
    }

    // ─── Opponent forfeit ───
    // While I'm waiting on the opponent's turn or their bonus attempt, they'll never write
    // the phase transition if they've left. Write it myself, crediting them 0, so I can
    // continue right away instead of sitting through the waiting countdown.
    private fun forceOpponentTurnEndDueToForfeit() {
        val state = _uiState.value
        if (state.phase == SkockoPhase.GAME_OVER) return
        if (state.phase != SkockoPhase.WAITING_OPPONENT && state.phase != SkockoPhase.OPPONENT_BONUS_R1) return
        val (targetPhase, dataMap) = when {
            state.phase == SkockoPhase.WAITING_OPPONENT && state.currentRound == 1 ->
                "sk_r1bonus" to mapOf("r1Score" to 0L, "showSolution" to false)
            state.phase == SkockoPhase.OPPONENT_BONUS_R1 && state.currentRound == 1 ->
                "sk_r2" to mapOf(
                    "r1Bonus" to 0L, "showSolution" to false,
                    "solutionSymbols" to emptyList<Long>(), "r2Attempts" to emptyList<Any>()
                )
            state.phase == SkockoPhase.WAITING_OPPONENT && state.currentRound == 2 ->
                "sk_r2bonus" to mapOf("r2Score" to 0L, "showSolution" to false)
            else ->
                "sk_done" to mapOf("r2Bonus" to 0L, "showSolution" to false, "solutionSymbols" to emptyList<Long>())
        }
        if (!forcedWrites.add(targetPhase)) return
        timerJob?.cancel(); opponentJob?.cancel()
        viewModelScope.launch {
            runCatching { mpRepo.setPhaseAndData(sessionId, targetPhase, dataMap) }
        }
    }

    // ─── Player input ───

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
        val attemptsKey = if (state.currentRound == 1) "r1Attempts" else "r2Attempts"

        if (result.correctPos == 4) {
            timerJob?.cancel()
            solvedAtAttemptNum = newAttempts.size
            val points = scoreForAttempt(newAttempts.size)
            val sol = state.mySolution
            _uiState.value = state.copy(
                myAttempts = newAttempts, currentInput = emptyList(),
                myScore = state.myScore + points,
                message = "+$points bodova! Pogodio/la si kombinaciju!"
            )
            if (isMultiplayer) {
                val round = state.currentRound
                viewModelScope.launch {
                    mpRepo.updateGameData(sessionId, mapOf(attemptsKey to attemptsToMaps(newAttempts)))
                    _uiState.value = _uiState.value.copy(showSolution = true, revealedSolution = sol)
                    mpRepo.updateGameData(sessionId, mapOf(
                        "showSolution" to true,
                        "solutionSymbols" to sol.map { it.toLong() }
                    ))
                    delay(2500L)
                    _uiState.value = _uiState.value.copy(showSolution = false)
                    if (isPlayer1 && round == 1) {
                        mpRepo.setPhaseAndData(sessionId, "sk_r2", mapOf(
                            "r1Score" to points.toLong(),
                            "showSolution" to false,
                            "solutionSymbols" to emptyList<Long>(),
                            "r2Attempts" to emptyList<Any>()
                        ))
                    } else if (!isPlayer1 && round == 2) {
                        mpRepo.setPhaseAndData(sessionId, "sk_done", mapOf(
                            "r2Score" to points.toLong(),
                            "showSolution" to false,
                            "solutionSymbols" to emptyList<Long>()
                        ))
                    }
                }
            } else {
                viewModelScope.launch {
                    if (izazovMode) {
                        // Izazov: fully solo, one round only (spec 9d - every game appears once).
                        _uiState.value = _uiState.value.copy(revealedSolution = sol)
                        delay(1500L); endGame()
                    } else {
                        startRound2()
                    }
                }
            }
        } else if (newAttempts.size >= 6) {
            timerJob?.cancel()
            _uiState.value = state.copy(
                myAttempts = newAttempts, currentInput = emptyList(),
                message = if (izazovMode) "Nisi pogodio/la." else "Nisi pogodio/la. Protivnik dobija šansu!"
            )
            if (isMultiplayer) {
                val round = state.currentRound
                viewModelScope.launch {
                    mpRepo.updateGameData(sessionId, mapOf(attemptsKey to attemptsToMaps(newAttempts)))
                    delay(1200L)
                    if (isPlayer1 && round == 1) {
                        mpRepo.setPhaseAndData(sessionId, "sk_r1bonus",
                            mapOf("r1Score" to 0L, "showSolution" to false))
                    } else if (!isPlayer1 && round == 2) {
                        mpRepo.setPhaseAndData(sessionId, "sk_r2bonus",
                            mapOf("r2Score" to 0L, "showSolution" to false))
                    }
                }
            } else {
                viewModelScope.launch { delay(1200L); startOpponentBonusR1() }
            }
        } else {
            if (isMultiplayer) {
                viewModelScope.launch {
                    mpRepo.updateGameData(sessionId, mapOf(attemptsKey to attemptsToMaps(newAttempts)))
                }
            }
            _uiState.value = state.copy(myAttempts = newAttempts, currentInput = emptyList(), message = null)
        }
    }

    private fun submitMyBonusAttempt(state: SkockoUiState) {
        val solution = state.opponentSolution
        val result = checkAttempt(state.currentInput, solution)
        timerJob?.cancel()
        val bonus = if (result.correctPos == 4) 10 else 0
        _uiState.value = state.copy(
            myScore = state.myScore + bonus,
            currentInput = emptyList(),
            bonusAttemptResult = result,
            message = if (bonus > 0) "+10 bonus bodova!" else "Netačno!"
        )
        if (isMultiplayer) {
            val bonusAttemptMap = attemptsToMaps(listOf(result))[0]
            viewModelScope.launch {
                _uiState.value = _uiState.value.copy(showSolution = true, revealedSolution = solution)
                mpRepo.updateGameData(sessionId, mapOf(
                    "showSolution" to true,
                    "solutionSymbols" to solution.map { it.toLong() },
                    "bonusAttempt" to bonusAttemptMap
                ))
                delay(2500L)
                _uiState.value = _uiState.value.copy(showSolution = false)
                submitBonusToFirestore(bonus)
            }
        } else {
            viewModelScope.launch { delay(800L); endGame() }
        }
    }

    private fun submitBonusToFirestore(bonusScore: Int) {
        val round = _uiState.value.currentRound
        viewModelScope.launch {
            if (!isPlayer1 && round == 1) {
                mpRepo.setPhaseAndData(sessionId, "sk_r2", mapOf(
                    "r1Bonus" to bonusScore.toLong(),
                    "showSolution" to false,
                    "solutionSymbols" to emptyList<Long>(),
                    "r2Attempts" to emptyList<Any>()
                ))
            } else if (isPlayer1 && round == 2) {
                mpRepo.setPhaseAndData(sessionId, "sk_done", mapOf(
                    "r2Bonus" to bonusScore.toLong(),
                    "showSolution" to false,
                    "solutionSymbols" to emptyList<Long>()
                ))
            }
        }
    }

    // ─── Single-player simulation ───

    private fun scoreForAttempt(n: Int): Int = when (n) { 1, 2 -> 20; 3, 4 -> 15; else -> 10 }

    private fun startOpponentBonusR1() {
        timerJob?.cancel(); opponentJob?.cancel()
        // Izazov: fully solo, one round only - no bonus phase, reveal and finish.
        if (izazovMode) {
            _uiState.value = _uiState.value.copy(
                revealedSolution = _uiState.value.mySolution, showSolution = true
            )
            viewModelScope.launch { delay(2000L); endGame() }
            return
        }
        _uiState.value = _uiState.value.copy(
            phase = SkockoPhase.OPPONENT_BONUS_R1, timeLeft = 10, currentInput = emptyList(),
            message = "Protivnik ima 10 sekundi za bonus!"
        )
        timerJob = viewModelScope.launch {
            var time = 10; val guessAfter = (3..8).random(); var guessed = false
            while (time > 0) {
                delay(1000L); time--
                _uiState.value = _uiState.value.copy(timeLeft = time)
                if (!guessed && (10 - time) >= guessAfter) {
                    guessed = true
                    val correct = (0..1).random() == 1
                    _uiState.value = _uiState.value.copy(
                        opponentScore = if (correct) _uiState.value.opponentScore + 10 else _uiState.value.opponentScore,
                        message = if (correct) "Protivnik pogodio! +10" else "Protivnik nije pogodio."
                    )
                }
            }
            startRound2()
        }
    }

    private suspend fun startRound2() {
        val r1Sol = _uiState.value.mySolution
        _uiState.value = _uiState.value.copy(showSolution = true, revealedSolution = r1Sol)
        delay(2500L)
        _uiState.value = _uiState.value.copy(
            currentRound = 2, phase = SkockoPhase.WAITING_OPPONENT,
            opponentAttempts = emptyList(), currentInput = emptyList(),
            timeLeft = 30, showSolution = false, message = "Runda 2: Protivnik igra!"
        )
        simulateOpponentRound2()
    }

    private fun simulateOpponentRound2() {
        opponentJob?.cancel()
        opponentJob = viewModelScope.launch {
            val solution = _uiState.value.opponentSolution
            val successAttempt = if ((0..1).random() == 1) (1..6).random() else -1
            for (attempt in 1..6) {
                delay(1200L)
                val guess = if (attempt == successAttempt) solution else generateSolution()
                val result = checkAttempt(guess, solution)
                _uiState.value = _uiState.value.copy(opponentAttempts = _uiState.value.opponentAttempts + result)
                if (result.correctPos == 4) {
                    val points = scoreForAttempt(attempt)
                    _uiState.value = _uiState.value.copy(
                        opponentScore = _uiState.value.opponentScore + points,
                        message = "Protivnik pogodio u $attempt. pokušaju! +$points"
                    )
                    _uiState.value = _uiState.value.copy(showSolution = true, revealedSolution = solution)
                    delay(2500L)
                    _uiState.value = _uiState.value.copy(showSolution = false)
                    endGame(); return@launch
                }
            }
            _uiState.value = _uiState.value.copy(
                message = "Protivnik nije pogodio! Tvoj bonus!",
                phase = SkockoPhase.MY_BONUS, currentInput = emptyList()
            )
            startTimer(10)
        }
    }

    private fun endGame() {
        timerJob?.cancel(); opponentJob?.cancel()
        val state = _uiState.value
        val finalReveal = if (state.revealedSolution.isEmpty()) state.opponentSolution else state.revealedSolution
        _uiState.value = state.copy(
            phase = SkockoPhase.GAME_OVER,
            currentInput = emptyList(),
            revealedSolution = finalReveal
        )
        if (!resultSaved) {
            resultSaved = true
            val attempt = solvedAtAttemptNum
            val myScore = _uiState.value.myScore
            val oppScore = _uiState.value.opponentScore
            viewModelScope.launch {
                runCatching {
                    userRepo.saveSkockoResult(attempt, rounds = 1)
                    userRepo.saveGameResult(won = myScore > oppScore)
                }
            }
        }
    }

    private fun checkAttempt(guess: List<Int>, solution: List<Int>): SkockoAttemptResult {
        val solCount = IntArray(6); val guessCount = IntArray(6); var correctPos = 0
        for (i in 0..3) {
            if (guess[i] == solution[i]) correctPos++
            else { solCount[solution[i]]++; guessCount[guess[i]]++ }
        }
        var correctSym = 0
        for (s in 0..5) correctSym += minOf(solCount[s], guessCount[s])
        return SkockoAttemptResult(guess, correctPos, correctSym)
    }

    private fun attemptsToMaps(attempts: List<SkockoAttemptResult>): List<Map<String, Any>> =
        attempts.map { mapOf("s" to it.symbols.map { s -> s.toLong() }, "p" to it.correctPos.toLong(), "c" to it.correctSym.toLong()) }

    @Suppress("UNCHECKED_CAST")
    private fun mapsToAttempts(list: List<*>): List<SkockoAttemptResult> =
        list.mapNotNull { item ->
            val map = item as? Map<String, Any> ?: return@mapNotNull null
            val symbols = (map["s"] as? List<*>)?.map { (it as? Long)?.toInt() ?: 0 } ?: return@mapNotNull null
            val correctPos = (map["p"] as? Long)?.toInt() ?: return@mapNotNull null
            val correctSym = (map["c"] as? Long)?.toInt() ?: return@mapNotNull null
            SkockoAttemptResult(symbols, correctPos, correctSym)
        }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel(); opponentJob?.cancel(); mpListener?.remove(); forfeitListener?.remove()
    }
}

class SkockoViewModelFactory(
    private val sessionId: String,
    private val isPlayer1: Boolean,
    private val gameIdx: Int,
    private val izazovMode: Boolean = false
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        SkockoViewModel(sessionId = sessionId, isPlayer1 = isPlayer1, gameIdx = gameIdx, izazovMode = izazovMode) as T
}
