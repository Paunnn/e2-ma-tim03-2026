package com.tim03.slagalica.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.ListenerRegistration
import com.tim03.slagalica.data.model.SpojniceQuestion
import com.tim03.slagalica.data.repository.MultiplayerGameRepository
import com.tim03.slagalica.data.repository.PartijaSessionRepository
import com.tim03.slagalica.data.repository.SpojniceRepository
import com.tim03.slagalica.data.repository.UserRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.random.Random

enum class SpojnicePhase { R1_ME, R1_OPP, R2_OPP, R2_ME, DONE }

private fun nextSpojnicePhase(current: SpojnicePhase, allCorrect: Boolean): SpojnicePhase = when (current) {
    SpojnicePhase.R1_ME  -> if (allCorrect) SpojnicePhase.R2_OPP else SpojnicePhase.R1_OPP
    SpojnicePhase.R1_OPP -> SpojnicePhase.R2_OPP
    SpojnicePhase.R2_OPP -> if (allCorrect) SpojnicePhase.DONE   else SpojnicePhase.R2_ME
    SpojnicePhase.R2_ME  -> SpojnicePhase.DONE
    SpojnicePhase.DONE   -> SpojnicePhase.DONE
}

data class SpojniceUiState(
    val isLoading: Boolean = true,
    val rounds: List<SpojniceQuestion> = emptyList(),
    val phase: SpojnicePhase = SpojnicePhase.R1_ME,
    val isMyActiveTurn: Boolean = true,
    val connections: Map<Int, Int> = emptyMap(),
    val correctConnections: Set<Int> = emptySet(),
    val myScore: Int = 0,
    val opponentScore: Int = 0,
    val timeLeft: Int = 30,
    val currentAttemptPos: Int = 0,
    val pendingItems: List<Int> = emptyList(),
    val opponentActiveLeft: Int? = null,
    val gameOver: Boolean = false,
    val error: String? = null
)

class SpojniceViewModel(
    private val sessionId: String = "",
    private val isPlayer1: Boolean = true,
    private val gameIdx: Int = -1,
    private val izazovMode: Boolean = false,
    private val roundRepo: SpojniceRepository = SpojniceRepository(),
    private val userRepo: UserRepository = UserRepository(),
    private val mpRepo: MultiplayerGameRepository = MultiplayerGameRepository(),
    private val sessionRepo: PartijaSessionRepository = PartijaSessionRepository()
) : ViewModel() {

    val isMultiplayer = sessionId.isNotEmpty()

    private val _uiState = MutableStateFlow(SpojniceUiState())
    val uiState: StateFlow<SpojniceUiState> = _uiState.asStateFlow()

    private val _username = MutableStateFlow("Igrač")
    val username: StateFlow<String> = _username.asStateFlow()

    private var timerJob: Job? = null
    private var opponentJob: Job? = null
    private var mpListener: ListenerRegistration? = null
    private var forfeitListener: ListenerRegistration? = null
    private var opponentForfeited = false
    // Firestore phases we've already force-written on the absent opponent's behalf,
    // so repeated snapshots can't double-write the same transition.
    private val forcedWrites = mutableSetOf<String>()
    private var lastHandledPhase = ""

    private var totalConnected = 0; private var totalPairs = 0
    private var resultSaved = false
    private var pendingItemsLocal: List<Int> = emptyList()
    private var attemptPosLocal = 0
    private var correctLocal: Set<Int> = emptySet()
    private var connectionsLocal: Map<Int, Int> = emptyMap()

    private fun mpPhaseFor(phase: SpojnicePhase) = when (phase) {
        SpojnicePhase.R1_ME  -> "sp_r1"
        SpojnicePhase.R1_OPP -> "sp_r1bonus"
        SpojnicePhase.R2_OPP -> "sp_r2"
        SpojnicePhase.R2_ME  -> "sp_r2bonus"
        SpojnicePhase.DONE   -> "sp_done"
    }

    private fun isMyActiveSpojnicePhase(phase: SpojnicePhase): Boolean = when {
        isPlayer1 -> phase == SpojnicePhase.R1_ME || phase == SpojnicePhase.R2_ME
        else      -> phase == SpojnicePhase.R1_OPP || phase == SpojnicePhase.R2_OPP
    }

    // ─── Firestore connection map helpers ───

    // Converts local connections map to Firestore-compatible format (string keys, Long values).
    private fun Map<Int, Int>.toFSConns(): Map<String, Long> =
        entries.associate { (k, v) -> k.toString() to v.toLong() }

    // Reads a connection map from gData (string keys → int indices).
    private fun parseFirestoreConns(data: Map<String, Any>, key: String): Map<Int, Int> {
        val raw = data[key] as? Map<*, *> ?: return emptyMap()
        return raw.entries.mapNotNull { (k, v) ->
            val leftIdx = (k as? String)?.toIntOrNull() ?: return@mapNotNull null
            val rightIdx = (v as? Long)?.toInt() ?: return@mapNotNull null
            leftIdx to rightIdx
        }.toMap()
    }

    // Builds the map of correct connections from a connection map + round.
    private fun correctConnsFrom(conns: Map<Int, Int>, round: SpojniceQuestion): Set<Int> =
        conns.entries.filter { (l, r) -> round.correctMapping.getOrElse(l) { -1 } == r }.map { it.key }.toSet()

    init {
        loadRounds()
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

    // Normally P1 always sets up each mini-game. But if P1 forfeited the partija before
    // this game was ever reached, nobody else would - so P2 must take over as initializer.
    private suspend fun isFallbackInitializer(): Boolean {
        if (isPlayer1) return false
        val session = sessionRepo.getSession(sessionId) ?: return false
        return session.status == "forfeited" && session.forfeitedBy == "player1"
    }

    private fun loadRounds() {
        viewModelScope.launch {
            try {
                if (isMultiplayer && isPlayer1) {
                    val rounds = roundRepo.getRounds(2)
                    mpRepo.initGame(sessionId, gameIdx, "sp_r1",
                        mapOf(
                            "type" to "sp",
                            "r1Id" to rounds[0].id,
                            "r2Id" to (rounds.getOrNull(1)?.id ?: ""),
                            "r1P1Correct" to emptyList<Long>(), "r1P1Score" to 0L,
                            "r1P2Correct" to emptyList<Long>(), "r1P2Score" to 0L,
                            "r2P2Correct" to emptyList<Long>(), "r2P2Score" to 0L,
                            "r2P1Correct" to emptyList<Long>(), "r2P1Score" to 0L,
                            // Live connection maps — written per-click so the watching player sees real-time progress
                            "r1Conns" to emptyMap<String, Long>(),
                            "r1BonusConns" to emptyMap<String, Long>(),
                            "r2Conns" to emptyMap<String, Long>(),
                            "r2BonusConns" to emptyMap<String, Long>()
                        )
                    )
                    _uiState.value = SpojniceUiState(isLoading = false, rounds = rounds)
                    startPhase(SpojnicePhase.R1_ME, rounds)
                    listenForMpPhase()
                } else if (isMultiplayer && !isPlayer1 && isFallbackInitializer()) {
                    // P1 is gone - I set up the round myself, then immediately treat their
                    // never-going-to-happen R1_ME turn as forfeited (0 points) so I can play.
                    val rounds = roundRepo.getRounds(2)
                    mpRepo.initGame(sessionId, gameIdx, "sp_r1",
                        mapOf(
                            "type" to "sp",
                            "r1Id" to rounds[0].id,
                            "r2Id" to (rounds.getOrNull(1)?.id ?: ""),
                            "r1P1Correct" to emptyList<Long>(), "r1P1Score" to 0L,
                            "r1P2Correct" to emptyList<Long>(), "r1P2Score" to 0L,
                            "r2P2Correct" to emptyList<Long>(), "r2P2Score" to 0L,
                            "r2P1Correct" to emptyList<Long>(), "r2P1Score" to 0L,
                            "r1Conns" to emptyMap<String, Long>(),
                            "r1BonusConns" to emptyMap<String, Long>(),
                            "r2Conns" to emptyMap<String, Long>(),
                            "r2BonusConns" to emptyMap<String, Long>()
                        )
                    )
                    _uiState.value = SpojniceUiState(
                        isLoading = false, rounds = rounds,
                        phase = SpojnicePhase.R1_ME, isMyActiveTurn = false, timeLeft = 30
                    )
                    listenForMpPhase()
                    forceOpponentTurnEndDueToForfeit()
                } else if (isMultiplayer && !isPlayer1) {
                    _uiState.value = SpojniceUiState(isLoading = true)
                    listenForMpPhase()
                } else {
                    val rounds = roundRepo.getRounds(2)
                    _uiState.value = SpojniceUiState(isLoading = false, rounds = rounds)
                    startPhase(SpojnicePhase.R1_ME, rounds)
                }
            } catch (e: Exception) {
                _uiState.value = SpojniceUiState(isLoading = false, error = e.message)
            }
        }
    }

    // ─── Multiplayer phase listener ───

    private fun listenForMpPhase() {
        mpListener?.remove()
        mpListener = mpRepo.listenToGameState(sessionId) { gIdx, phase, data ->
            if (gIdx != gameIdx) return@listenToGameState
            if (phase != lastHandledPhase) {
                lastHandledPhase = phase
                handleMpPhase(phase, data)
            } else {
                // Same phase: update live connection display for the watching player.
                handleMpLiveUpdate(phase, data)
            }
            // Re-check after every state change: each forced transition lands me in the
            // NEXT waiting phase, which must be skipped too - the absent opponent never
            // generates snapshots of their own, so this is the only reliable trigger.
            if (opponentForfeited) forceOpponentTurnEndDueToForfeit()
        }
    }

    // Called when the Firestore document updates but the phase hasn't changed.
    // Applies the active player's live connection writes to the watching player's display.
    private fun handleMpLiveUpdate(phase: String, data: Map<String, Any>) {
        val state = _uiState.value
        // Only the watching player needs to update their display from Firestore.
        if (state.isMyActiveTurn || state.gameOver || state.isLoading) return

        val liveKey = when {
            phase == "sp_r1"      && !isPlayer1 -> "r1Conns"
            phase == "sp_r1bonus" && isPlayer1  -> "r1BonusConns"
            phase == "sp_r2"      && isPlayer1  -> "r2Conns"
            phase == "sp_r2bonus" && !isPlayer1 -> "r2BonusConns"
            else -> null
        } ?: return

        val liveConns = parseFirestoreConns(data, liveKey)
        val roundIdx = if (phase == "sp_r1" || phase == "sp_r1bonus") 0 else 1
        val round = state.rounds.getOrNull(roundIdx) ?: return

        // Merge: state.connections holds the previous player's correct conns (set at phase start);
        // liveConns holds the active player's new connections in this phase.
        val mergedConns = state.connections + liveConns
        val newCorrect = correctConnsFrom(mergedConns, round)

        _uiState.value = state.copy(connections = mergedConns, correctConnections = newCorrect)
    }

    @Suppress("UNCHECKED_CAST")
    private fun handleMpPhase(phase: String, data: Map<String, Any>) {
        fun longList(key: String) = (data[key] as? List<*>)?.map { (it as? Long)?.toInt() ?: 0 } ?: emptyList()
        fun longVal(key: String)  = (data[key] as? Long)?.toInt() ?: 0

        when (phase) {
            "sp_r1" -> {
                if (!isPlayer1) {
                    val r1Id = data["r1Id"] as? String ?: return
                    val r2Id = data["r2Id"] as? String ?: ""
                    viewModelScope.launch {
                        val rounds = buildList {
                            roundRepo.getRoundsByIds(listOf(r1Id)).firstOrNull()?.also { add(it) }
                            if (r2Id.isNotEmpty()) roundRepo.getRoundsByIds(listOf(r2Id)).firstOrNull()?.also { add(it) }
                        }
                        if (rounds.isEmpty()) return@launch
                        // P2 waits while P1 plays R1; real-time updates arrive via handleMpLiveUpdate.
                        correctLocal = emptySet(); connectionsLocal = emptyMap()
                        _uiState.value = SpojniceUiState(
                            isLoading = false, rounds = rounds,
                            phase = SpojnicePhase.R1_ME,
                            isMyActiveTurn = false,
                            connections = emptyMap(), correctConnections = emptySet(),
                            pendingItems = (0 until rounds[0].leftItems.size).toList(),
                            timeLeft = 30
                        )
                        startWaitingTimer()
                        // The forfeit may have arrived while rounds were still loading.
                        if (opponentForfeited) forceOpponentTurnEndDueToForfeit()
                    }
                }
            }

            "sp_r1bonus" -> {
                timerJob?.cancel(); opponentJob?.cancel()
                val r1P1Correct = longList("r1P1Correct").toSet()
                val r1P1Score   = longVal("r1P1Score")
                // P1's full connection map lets us know which right-side items are already taken.
                val r1Conns = parseFirestoreConns(data, "r1Conns")
                val p1CorrectConns = r1P1Correct
                    .associateWith { leftIdx -> r1Conns[leftIdx] }
                    .filterValues { it != null }
                    .mapValues { it.value!! }
                val state = _uiState.value

                if (!isPlayer1) {
                    // P2 connects remaining items that P1 missed.
                    val round = state.rounds.getOrNull(0) ?: return
                    val remaining = (0 until round.leftItems.size).filter { it !in r1P1Correct }
                    correctLocal = r1P1Correct
                    // Initialize with P1's correct connections so takenRightIndices works correctly.
                    connectionsLocal = p1CorrectConns
                    attemptPosLocal = 0; pendingItemsLocal = remaining
                    _uiState.value = state.copy(
                        phase = SpojnicePhase.R1_OPP,
                        isMyActiveTurn = true,
                        connections = p1CorrectConns,
                        correctConnections = r1P1Correct,
                        currentAttemptPos = 0,
                        pendingItems = remaining,
                        timeLeft = 30,
                        opponentScore = r1P1Score
                    )
                    startMyTurnTimer()
                } else {
                    // P1 watches P2's bonus; keep P1's connections in state so taken items stay visible.
                    _uiState.value = state.copy(
                        phase = SpojnicePhase.R1_OPP,
                        isMyActiveTurn = false,
                        correctConnections = r1P1Correct,
                        // connections stays as-is (P1's connections from R1 are still in state)
                        myScore = r1P1Score,
                        opponentScore = 0,
                        timeLeft = 30
                    )
                    startWaitingTimer()
                }
            }

            "sp_r2" -> {
                timerJob?.cancel(); opponentJob?.cancel()
                val r1P2Correct = longList("r1P2Correct").toSet()
                val r1P2Score   = longVal("r1P2Score")
                val r1P1Score   = longVal("r1P1Score")
                val state = _uiState.value
                val myR1Score  = if (isPlayer1) r1P1Score else r1P2Score
                val oppR1Score = if (isPlayer1) r1P2Score else r1P1Score

                if (!isPlayer1) {
                    // P2 connects all items in R2 — completely fresh round, no inherited connections.
                    val round = state.rounds.getOrNull(1) ?: return
                    val items = (0 until round.leftItems.size).toList()
                    correctLocal = emptySet(); connectionsLocal = emptyMap()
                    attemptPosLocal = 0; pendingItemsLocal = items
                    _uiState.value = state.copy(
                        phase = SpojnicePhase.R2_OPP,
                        isMyActiveTurn = true,
                        connections = emptyMap(),
                        correctConnections = emptySet(),
                        currentAttemptPos = 0,
                        pendingItems = items,
                        timeLeft = 30,
                        myScore = myR1Score, opponentScore = oppR1Score
                    )
                    startMyTurnTimer()
                } else {
                    _uiState.value = state.copy(
                        phase = SpojnicePhase.R2_OPP,
                        isMyActiveTurn = false,
                        connections = emptyMap(),
                        correctConnections = emptySet(),
                        timeLeft = 30,
                        myScore = myR1Score, opponentScore = oppR1Score
                    )
                    startWaitingTimer()
                }
            }

            "sp_r2bonus" -> {
                timerJob?.cancel(); opponentJob?.cancel()
                val r2P2Correct = longList("r2P2Correct").toSet()
                val r2P2Score   = longVal("r2P2Score")
                val r2Conns = parseFirestoreConns(data, "r2Conns")
                val p2CorrectConns = r2P2Correct
                    .associateWith { leftIdx -> r2Conns[leftIdx] }
                    .filterValues { it != null }
                    .mapValues { it.value!! }
                val state = _uiState.value

                if (isPlayer1) {
                    // P1 connects remaining items that P2 missed in R2.
                    val round = state.rounds.getOrNull(1) ?: return
                    val remaining = (0 until round.leftItems.size).filter { it !in r2P2Correct }
                    correctLocal = r2P2Correct
                    connectionsLocal = p2CorrectConns
                    attemptPosLocal = 0; pendingItemsLocal = remaining
                    _uiState.value = state.copy(
                        phase = SpojnicePhase.R2_ME,
                        isMyActiveTurn = true,
                        connections = p2CorrectConns,
                        correctConnections = r2P2Correct,
                        currentAttemptPos = 0,
                        pendingItems = remaining,
                        timeLeft = 30,
                        opponentScore = state.opponentScore + r2P2Score
                    )
                    startMyTurnTimer()
                } else {
                    // P2 watches P1's bonus; keep P2's connections in state.
                    _uiState.value = state.copy(
                        phase = SpojnicePhase.R2_ME,
                        isMyActiveTurn = false,
                        correctConnections = r2P2Correct,
                        myScore = state.myScore + r2P2Score,
                        timeLeft = 30
                    )
                    startWaitingTimer()
                }
            }

            "sp_done" -> {
                timerJob?.cancel(); opponentJob?.cancel()
                val r2P1Score   = longVal("r2P1Score")
                val r2P2Score   = longVal("r2P2Score")
                val r1P1Score   = longVal("r1P1Score")
                val r1P2Score   = longVal("r1P2Score")
                val myFinal  = if (isPlayer1) r1P1Score + r2P1Score else r1P2Score + r2P2Score
                val oppFinal = if (isPlayer1) r1P2Score + r2P2Score else r1P1Score + r2P1Score
                _uiState.value = _uiState.value.copy(
                    myScore = myFinal, opponentScore = oppFinal,
                    phase = SpojnicePhase.DONE, isMyActiveTurn = false, gameOver = true
                )
                saveResultFinal(myFinal, oppFinal)
            }
        }
    }

    // ─── Single-player / active-turn phase management ───

    private fun startPhase(phase: SpojnicePhase, rounds: List<SpojniceQuestion>) {
        timerJob?.cancel(); opponentJob?.cancel()
        if (phase == SpojnicePhase.DONE) {
            val state = _uiState.value
            _uiState.value = state.copy(phase = SpojnicePhase.DONE, isMyActiveTurn = false, gameOver = true)
            if (!isMultiplayer) saveResultFinal(state.myScore, state.opponentScore)
            return
        }
        val roundIdx = if (phase == SpojnicePhase.R1_ME || phase == SpojnicePhase.R1_OPP) 0 else 1
        val round = rounds.getOrNull(roundIdx) ?: return
        val isFirst = phase == SpojnicePhase.R1_ME || phase == SpojnicePhase.R2_OPP

        if (phase == SpojnicePhase.R2_OPP) {
            correctLocal = emptySet(); connectionsLocal = emptyMap()
        }

        val items = if (isFirst) (0 until round.leftItems.size).toList()
                    else (0 until round.leftItems.size).filter { it !in correctLocal }.sorted()

        if (items.isEmpty()) {
            val nextPhase = nextSpojnicePhase(phase, true)
            startPhase(nextPhase, rounds)
            return
        }

        pendingItemsLocal = items; attemptPosLocal = 0

        val myActiveTurn = if (!isMultiplayer) (phase == SpojnicePhase.R1_ME || phase == SpojnicePhase.R2_ME)
                           else isMyActiveSpojnicePhase(phase)

        _uiState.value = _uiState.value.copy(
            phase = phase,
            isMyActiveTurn = myActiveTurn,
            connections = connectionsLocal,
            correctConnections = correctLocal,
            currentAttemptPos = 0,
            pendingItems = items,
            timeLeft = 30,
            opponentActiveLeft = null
        )

        if (!isMultiplayer && !myActiveTurn) {
            simulateOpponentTurn(round, items, phase, rounds)
        } else if (myActiveTurn) {
            startMyTurnTimer()
        }
    }

    private fun startMyTurnTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            var t = _uiState.value.timeLeft
            while (t > 0 && !_uiState.value.gameOver) {
                delay(1000L); t--
                _uiState.value = _uiState.value.copy(timeLeft = t)
                val cur = _uiState.value
                if (cur.currentAttemptPos >= cur.pendingItems.size) break
            }
            delay(600L)
            onMyTurnEnd()
        }
    }

    private fun startWaitingTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            var t = 30
            while (t > 0 && !_uiState.value.gameOver) {
                delay(1000L); t--
                _uiState.value = _uiState.value.copy(timeLeft = t)
                if (_uiState.value.isMyActiveTurn) break
            }
        }
    }

    private fun simulateOpponentTurn(
        round: SpojniceQuestion, items: List<Int>, phase: SpojnicePhase, rounds: List<SpojniceQuestion>
    ) {
        opponentJob?.cancel()
        opponentJob = viewModelScope.launch {
            val timerJob2 = launch {
                var t = 30
                while (t > 0 && !_uiState.value.gameOver) { delay(1000L); t--; _uiState.value = _uiState.value.copy(timeLeft = t) }
            }
            for (i in items.indices) {
                if (_uiState.value.gameOver || _uiState.value.timeLeft <= 0) break
                val leftIdx = items[i]
                _uiState.value = _uiState.value.copy(opponentActiveLeft = leftIdx)
                delay((700L..2600L).random())
                if (_uiState.value.gameOver || _uiState.value.timeLeft <= 0) {
                    _uiState.value = _uiState.value.copy(opponentActiveLeft = null); break
                }
                val correctRight = round.correctMapping.getOrElse(leftIdx) { 0 }
                val picks = if (Random.nextFloat() < 0.65f) correctRight
                            else (0 until round.rightItems.size).filter { it != correctRight }.randomOrNull() ?: correctRight
                val newConns   = _uiState.value.connections + (leftIdx to picks)
                val isCorrect  = picks == correctRight
                val newCorrect = if (isCorrect) _uiState.value.correctConnections + leftIdx else _uiState.value.correctConnections
                _uiState.value = _uiState.value.copy(
                    connections = newConns, correctConnections = newCorrect, currentAttemptPos = i + 1,
                    opponentScore = if (isCorrect) _uiState.value.opponentScore + 2 else _uiState.value.opponentScore,
                    opponentActiveLeft = null
                )
                if (isCorrect) totalConnected++
                totalPairs++
            }
            timerJob2.cancel()
            delay(1200L)
            correctLocal = _uiState.value.correctConnections
            val nextPhase = nextSpojnicePhase(phase, correctLocal.size >= round.leftItems.size)
            startPhase(nextPhase, rounds)
        }
    }

    private fun onMyTurnEnd() {
        if (_uiState.value.gameOver) return
        val state = _uiState.value
        val roundIdx = if (state.phase == SpojnicePhase.R1_ME || state.phase == SpojnicePhase.R1_OPP) 0 else 1
        val round = state.rounds.getOrNull(roundIdx) ?: return
        correctLocal = state.correctConnections

        if (isMultiplayer) {
            finishMyMultiplayerTurn(state, round)
        } else if (izazovMode) {
            // Izazov: fully solo, one round only (spec 9d - every game appears once).
            startPhase(SpojnicePhase.DONE, state.rounds)
        } else {
            val nextPhase = nextSpojnicePhase(state.phase, correctLocal.size >= round.leftItems.size)
            startPhase(nextPhase, state.rounds)
        }
    }

    private fun finishMyMultiplayerTurn(state: SpojniceUiState, round: SpojniceQuestion) {
        val correctList = correctLocal.map { it.toLong() }
        val score = correctLocal.size * 2
        viewModelScope.launch {
            when (state.phase) {
                SpojnicePhase.R1_ME -> {
                    // Set state before the write: Firestore listener fires on optimistic local write
                    // (before await returns), so the listener must not race with a post-await override.
                    _uiState.value = state.copy(
                        phase = SpojnicePhase.R1_OPP, isMyActiveTurn = false, timeLeft = 30)
                    mpRepo.setPhaseAndData(sessionId, "sp_r1bonus",
                        mapOf("r1P1Correct" to correctList, "r1P1Score" to score.toLong()))
                }
                SpojnicePhase.R1_OPP -> {
                    val bonusItems = state.pendingItems.toSet()
                    val bonusScore = correctLocal.count { it in bonusItems } * 2
                    _uiState.value = state.copy(
                        phase = SpojnicePhase.R2_OPP, isMyActiveTurn = false, timeLeft = 30,
                        myScore = state.myScore + bonusScore,
                        connections = emptyMap(), correctConnections = emptySet()
                    )
                    mpRepo.setPhaseAndData(sessionId, "sp_r2",
                        mapOf("r1P2Correct" to correctList, "r1P2Score" to bonusScore.toLong()))
                }
                SpojnicePhase.R2_OPP -> {
                    _uiState.value = state.copy(
                        phase = SpojnicePhase.R2_ME, isMyActiveTurn = false, timeLeft = 30,
                        myScore = state.myScore + score,
                        connections = emptyMap(), correctConnections = emptySet()
                    )
                    mpRepo.setPhaseAndData(sessionId, "sp_r2bonus",
                        mapOf("r2P2Correct" to correctList, "r2P2Score" to score.toLong()))
                }
                SpojnicePhase.R2_ME -> {
                    _uiState.value = state.copy(
                        phase = SpojnicePhase.DONE, isMyActiveTurn = false, gameOver = true,
                        myScore = state.myScore + score)
                    mpRepo.setPhaseAndData(sessionId, "sp_done",
                        mapOf("r2P1Correct" to correctList, "r2P1Score" to score.toLong()))
                }
                else -> {}
            }
        }
    }

    // ─── Opponent forfeit ───
    // If the opponent leaves the partija while I'm the one watching their turn, they'll
    // never write the phase transition themselves. Write it on their behalf, crediting
    // them 0 for the abandoned turn, so I can continue immediately instead of sitting
    // through the waiting countdown.
    private fun forceOpponentTurnEndDueToForfeit() {
        val state = _uiState.value
        if (state.gameOver || state.isLoading) return
        // R1_ME/R2_ME always belong to P1, R1_OPP/R2_OPP always belong to P2 - this is fixed
        // by phase, unlike isMyActiveTurn, which is briefly (and misleadingly) set to false
        // as a local placeholder while P2 hands off from R1_OPP straight into R2_OPP (both
        // their own turns) waiting for the Firestore round-trip to confirm it.
        val activeIsPlayer1 = state.phase == SpojnicePhase.R1_ME || state.phase == SpojnicePhase.R2_ME
        if (activeIsPlayer1 == isPlayer1) return
        val (targetPhase, dataMap) = when (state.phase) {
            SpojnicePhase.R1_ME -> "sp_r1bonus" to
                mapOf("r1P1Correct" to emptyList<Long>(), "r1P1Score" to 0L)
            SpojnicePhase.R1_OPP -> "sp_r2" to
                mapOf("r1P2Correct" to emptyList<Long>(), "r1P2Score" to 0L)
            SpojnicePhase.R2_OPP -> "sp_r2bonus" to
                mapOf("r2P2Correct" to emptyList<Long>(), "r2P2Score" to 0L)
            SpojnicePhase.R2_ME -> "sp_done" to
                mapOf("r2P1Correct" to emptyList<Long>(), "r2P1Score" to 0L)
            else -> return
        }
        if (!forcedWrites.add(targetPhase)) return
        Log.d("PartijaDbg", "Spojnice: skipping absent opponent's ${state.phase}, writing $targetPhase")
        timerJob?.cancel(); opponentJob?.cancel()
        viewModelScope.launch {
            runCatching { mpRepo.setPhaseAndData(sessionId, targetPhase, dataMap) }
                .onFailure { Log.e("PartijaDbg", "Spojnice: forced write $targetPhase FAILED", it) }
        }
    }

    // ─── Player click handler ───

    fun connectItem(leftIdx: Int, rightIdx: Int) {
        val state = _uiState.value
        if (!state.isMyActiveTurn || state.gameOver) return
        val roundIdx = if (state.phase == SpojnicePhase.R1_ME || state.phase == SpojnicePhase.R1_OPP) 0 else 1
        val round = state.rounds.getOrNull(roundIdx) ?: return
        val correctRight = round.correctMapping.getOrElse(leftIdx) { -1 }
        val isCorrect = rightIdx == correctRight
        val newConns   = state.connections + (leftIdx to rightIdx)
        val newCorrect = if (isCorrect) state.correctConnections + leftIdx else state.correctConnections
        totalPairs++
        if (isCorrect) totalConnected++
        correctLocal = newCorrect; connectionsLocal = newConns
        attemptPosLocal++
        val newPos = state.currentAttemptPos + 1

        _uiState.value = state.copy(
            connections = newConns, correctConnections = newCorrect,
            currentAttemptPos = newPos,
            myScore = if (isCorrect) state.myScore + 2 else state.myScore
        )

        // Write live connection update so the watching player can see it in real-time.
        if (isMultiplayer) {
            val liveKey = when (state.phase) {
                SpojnicePhase.R1_ME  -> "r1Conns"
                SpojnicePhase.R1_OPP -> "r1BonusConns"
                SpojnicePhase.R2_OPP -> "r2Conns"
                SpojnicePhase.R2_ME  -> "r2BonusConns"
                else -> null
            }
            if (liveKey != null) {
                mpRepo.updateGameData(sessionId, mapOf(liveKey to newConns.toFSConns()))
            }
        }

        if (newPos >= state.pendingItems.size) {
            timerJob?.cancel()
            viewModelScope.launch { delay(600L); onMyTurnEnd() }
        }
    }

    fun saveResult(connected: Int, totalPairsArg: Int, won: Boolean) {
        if (!isMultiplayer && !resultSaved) {
            resultSaved = true
            viewModelScope.launch {
                runCatching {
                    userRepo.saveSpojniceResult(connected, totalPairsArg)
                    userRepo.saveGameResult(won)
                }
            }
        }
    }

    private fun saveResultFinal(myScore: Int, oppScore: Int) {
        if (!resultSaved) {
            resultSaved = true
            viewModelScope.launch {
                runCatching {
                    userRepo.saveSpojniceResult(totalConnected, totalPairs)
                    userRepo.saveGameResult(won = myScore > oppScore)
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel(); opponentJob?.cancel(); mpListener?.remove(); forfeitListener?.remove()
    }
}

class SpojniceViewModelFactory(
    private val sessionId: String,
    private val isPlayer1: Boolean,
    private val gameIdx: Int,
    private val izazovMode: Boolean = false
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        SpojniceViewModel(sessionId = sessionId, isPlayer1 = isPlayer1, gameIdx = gameIdx, izazovMode = izazovMode) as T
}
