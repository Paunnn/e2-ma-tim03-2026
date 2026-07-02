package com.tim03.slagalica.viewmodel

import android.app.Application
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.ListenerRegistration
import com.tim03.slagalica.data.repository.MultiplayerGameRepository
import com.tim03.slagalica.data.repository.PartijaSessionRepository
import com.tim03.slagalica.data.repository.UserRepository
import com.tim03.slagalica.util.evalExpression
import com.tim03.slagalica.util.removeLastToken
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sqrt

// Multiplayer phases: mb_r1_s1 → mb_r1_s2 → mb_r1_play → mb_r2_s1 → mb_r2_s2 → mb_r2_play → mb_done
// Both players play each round simultaneously. P1 controls STOP in R1; P2 controls STOP in R2.
enum class MojBrojPhase {
    WAITING_STOP1,   // before first STOP click (target hidden)
    WAITING_STOP2,   // after first STOP click (target visible, 5s countdown)
    PLAYING,
    SUBMITTED,       // submitted, waiting for opponent; or showing round result
    GAME_OVER
}

data class MojBrojUiState(
    val currentRound: Int = 1,
    val targetNumber: Int = 0,
    val availableNumbers: List<Int> = emptyList(),
    val expression: String = "",
    val usedNumberIndices: Set<Int> = emptySet(),
    val expressionIndices: List<Int> = emptyList(),
    val expressionResult: Int? = null,
    val timeLeft: Int = 60,
    val waitingForStopTimeLeft: Int = 5,
    val myScore: Int = 0,
    val opponentScore: Int = 0,
    val phase: MojBrojPhase = MojBrojPhase.WAITING_STOP1,
    val myRoundResult: Int? = null,
    val opponentRoundResult: Int? = null,
    val roundResultMessage: String? = null,
    val gameOver: Boolean = false,
    val waitingMessage: String? = null,
)

@Suppress("UNCHECKED_CAST")
class MojBrojViewModel @JvmOverloads constructor(
    application: Application,
    private val sessionId: String = "",
    private val isPlayer1: Boolean = true,
    private val gameIdx: Int = -1,
    private val izazovMode: Boolean = false,
    private val userRepo: UserRepository = UserRepository(),
    private val mpRepo: MultiplayerGameRepository = MultiplayerGameRepository(),
    private val sessionRepo: PartijaSessionRepository = PartijaSessionRepository()
) : AndroidViewModel(application) {

    val isMultiplayer = sessionId.isNotEmpty()

    private val _uiState = MutableStateFlow(MojBrojUiState())
    val uiState: StateFlow<MojBrojUiState> = _uiState.asStateFlow()

    private val _username = MutableStateFlow("Igrač")
    val username: StateFlow<String> = _username.asStateFlow()

    private var mojBrojHitsLocal = 0
    private var resultSaved = false

    private var timerJob: Job? = null
    private var stopWindowJob: Job? = null
    private var mpListener: ListenerRegistration? = null
    private var forfeitListener: ListenerRegistration? = null
    private var opponentForfeited = false
    // Firestore phases already force-written on the absent opponent's behalf.
    private val forcedWrites = mutableSetOf<String>()
    private var lastHandledPhase = ""

    // Cached game data (set when mb_r1_s1 fires)
    private var mpR1Target = 0; private var mpR1Nums = emptyList<Int>()
    private var mpR2Target = 0; private var mpR2Nums = emptyList<Int>()

    // Guards to prevent double-transition (each player independently detects "both submitted")
    private var r1TransitionDone = false
    private var r2TransitionDone = false

    private val sensorManager: SensorManager =
        application.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private var lastShakeTime = 0L

    private val shakeListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val x = event.values[0]; val y = event.values[1]; val z = event.values[2]
            val acceleration = sqrt(x * x + y * y + z * z) - SensorManager.GRAVITY_EARTH
            if (acceleration > SHAKE_THRESHOLD) {
                val now = System.currentTimeMillis()
                if (now - lastShakeTime > 1000L) { lastShakeTime = now; onShake() }
            }
        }
        override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
    }

    init {
        accelerometer?.let {
            sensorManager.registerListener(shakeListener, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        if (isMultiplayer) startMultiplayerGame() else startRound(1)
        viewModelScope.launch {
            runCatching { userRepo.getCurrentUser()?.username?.also { _username.value = it } }
        }
        if (isMultiplayer) {
            forfeitListener = sessionRepo.listenToForfeit(sessionId) { forfeitedByPlayer1 ->
                if (forfeitedByPlayer1 != isPlayer1) {
                    opponentForfeited = true
                    checkAndForceIfStuck()
                }
            }
        }
    }

    // If the opponent leaves, two things can get me permanently stuck:
    // 1) They were the round's fixed "stop controller" and never pressed STOP.
    // 2) They were the round's fixed "result writer" and never submitted, so the
    //    both-submitted transition never fires.
    // Bypass both once the opponent is confirmed gone, and shorten the wait to a beat.
    private fun checkAndForceIfStuck() {
        if (!opponentForfeited) return
        val state = _uiState.value
        if (state.phase == MojBrojPhase.GAME_OVER) return
        when (state.phase) {
            MojBrojPhase.WAITING_STOP1 -> forceStopIfNotController(state, "mb_r1_s2", "mb_r2_s2")
            MojBrojPhase.WAITING_STOP2 -> forceStopIfNotController(state, "mb_r1_play", "mb_r2_play")
            MojBrojPhase.SUBMITTED -> forceRoundTransitionDueToForfeit(state)
            else -> {}
        }
    }

    private fun forceStopIfNotController(state: MojBrojUiState, round1Next: String, round2Next: String) {
        val isController = (state.currentRound == 1 && isPlayer1) || (state.currentRound == 2 && !isPlayer1)
        if (isController) return
        val next = if (state.currentRound == 1) round1Next else round2Next
        if (!forcedWrites.add(next)) return
        stopWindowJob?.cancel()
        viewModelScope.launch { mpRepo.setPhase(sessionId, next) }
    }

    private fun forceRoundTransitionDueToForfeit(state: MojBrojUiState) {
        if (state.currentRound == 1) {
            if (r1TransitionDone) return
            r1TransitionDone = true
            val myResult = state.myRoundResult
            val p1Result = if (isPlayer1) myResult else null
            val p2Result = if (isPlayer1) null else myResult
            val score = computeMpRoundScore(mpR1Target, p1Result, p2Result, true)
            val myPts  = if (isPlayer1) score.first else score.second
            val oppPts = if (isPlayer1) score.second else score.first
            _uiState.value = _uiState.value.copy(myScore = myPts, opponentScore = oppPts, roundResultMessage = score.third)
            viewModelScope.launch { delay(500L); mpRepo.setPhase(sessionId, "mb_r2_s1") }
        } else {
            if (r2TransitionDone) return
            r2TransitionDone = true
            viewModelScope.launch { delay(500L); mpRepo.setPhase(sessionId, "mb_done") }
        }
    }

    // ─── Multiplayer flow ───

    // Normally P1 always sets up each mini-game. But if P1 forfeited the partija before
    // this game was ever reached, nobody else would - so P2 must take over as initializer.
    private suspend fun isFallbackInitializer(): Boolean {
        if (isPlayer1) return false
        val session = sessionRepo.getSession(sessionId) ?: return false
        return session.status == "forfeited" && session.forfeitedBy == "player1"
    }

    private fun startMultiplayerGame() {
        viewModelScope.launch {
            if (isPlayer1 || isFallbackInitializer()) {
                if (!isPlayer1) opponentForfeited = true
                val r1Target = generateTarget(); val r1Nums = generateNumbers()
                val r2Target = generateTarget(); val r2Nums = generateNumbers()
                mpR1Target = r1Target; mpR1Nums = r1Nums
                mpR2Target = r2Target; mpR2Nums = r2Nums
                mpRepo.initGame(
                    sessionId, gameIdx, "mb_r1_s1",
                    mapOf(
                        "r1Target" to r1Target.toLong(),
                        "r1Nums"   to r1Nums.map { it.toLong() },
                        "r2Target" to r2Target.toLong(),
                        "r2Nums"   to r2Nums.map { it.toLong() },
                        "r1P1" to -1L, "r1P2" to -1L,
                        "r2P1" to -1L, "r2P2" to -1L
                    )
                )
            }
            listenForMultiplayerPhase()
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
            if (opponentForfeited) checkAndForceIfStuck()
        }
    }

    private fun handleMpPhase(phase: String, data: Map<String, Any>) {
        fun intVal(key: String) = (data[key] as? Long)?.toInt() ?: 0
        fun listInts(key: String) = (data[key] as? List<*>)
            ?.mapNotNull { (it as? Long)?.toInt() } ?: emptyList()

        when (phase) {
            "mb_r1_s1" -> {
                mpR1Target = intVal("r1Target"); mpR1Nums = listInts("r1Nums")
                mpR2Target = intVal("r2Target"); mpR2Nums = listInts("r2Nums")
                timerJob?.cancel(); stopWindowJob?.cancel()
                _uiState.value = MojBrojUiState(
                    currentRound = 1,
                    targetNumber = mpR1Target,
                    availableNumbers = mpR1Nums,
                    phase = MojBrojPhase.WAITING_STOP1,
                    timeLeft = 60,
                    waitingMessage = if (!isPlayer1) "Protivnik pokreće igru..." else null
                )
                checkAndForceIfStuck()
            }
            "mb_r1_s2" -> {
                timerJob?.cancel(); stopWindowJob?.cancel()
                _uiState.value = _uiState.value.copy(
                    phase = MojBrojPhase.WAITING_STOP2,
                    waitingForStopTimeLeft = 5,
                    waitingMessage = if (!isPlayer1) "Protivnik bira brojeve..." else null
                )
                // Only P1 (stop controller in R1) runs the 5-second auto-reveal countdown.
                if (isPlayer1) startStopCountdown("mb_r1_play")
                checkAndForceIfStuck()
            }
            "mb_r1_play" -> {
                timerJob?.cancel(); stopWindowJob?.cancel()
                _uiState.value = _uiState.value.copy(
                    phase = MojBrojPhase.PLAYING, timeLeft = 60,
                    waitingMessage = null,
                    expression = "", usedNumberIndices = emptySet(),
                    expressionIndices = emptyList(), expressionResult = null
                )
                startMainTimerMp()
            }
            "mb_r2_s1" -> {
                timerJob?.cancel(); stopWindowJob?.cancel()
                val prev = _uiState.value
                _uiState.value = MojBrojUiState(
                    currentRound = 2,
                    targetNumber = mpR2Target,
                    availableNumbers = mpR2Nums,
                    myScore = prev.myScore,
                    opponentScore = prev.opponentScore,
                    phase = MojBrojPhase.WAITING_STOP1,
                    timeLeft = 60,
                    roundResultMessage = prev.roundResultMessage, // show R1 result while waiting
                    waitingMessage = if (isPlayer1) "Protivnik pokreće rundu 2..." else null
                )
                checkAndForceIfStuck()
            }
            "mb_r2_s2" -> {
                timerJob?.cancel(); stopWindowJob?.cancel()
                _uiState.value = _uiState.value.copy(
                    phase = MojBrojPhase.WAITING_STOP2,
                    waitingForStopTimeLeft = 5,
                    waitingMessage = if (isPlayer1) "Protivnik bira brojeve..." else null
                )
                // Only P2 (stop controller in R2) runs the 5-second auto-reveal countdown.
                if (!isPlayer1) startStopCountdown("mb_r2_play")
                checkAndForceIfStuck()
            }
            "mb_r2_play" -> {
                timerJob?.cancel(); stopWindowJob?.cancel()
                _uiState.value = _uiState.value.copy(
                    phase = MojBrojPhase.PLAYING, timeLeft = 60,
                    roundResultMessage = null, waitingMessage = null,
                    expression = "", usedNumberIndices = emptySet(),
                    expressionIndices = emptyList(), expressionResult = null
                )
                startMainTimerMp()
            }
            "mb_done" -> {
                timerJob?.cancel(); stopWindowJob?.cancel()
                // Re-compute final scores from authoritative Firestore data.
                val r1P1 = (data["r1P1"] as? Long)?.takeIf { it != -1L }
                val r1P2 = (data["r1P2"] as? Long)?.takeIf { it != -1L }
                val r2P1 = (data["r2P1"] as? Long)?.takeIf { it != -1L }
                val r2P2 = (data["r2P2"] as? Long)?.takeIf { it != -1L }
                val r1Score = computeMpRoundScore(mpR1Target,
                    r1P1?.let { if (it == -2L) null else it.toInt() },
                    r1P2?.let { if (it == -2L) null else it.toInt() }, true)
                val r2Score = computeMpRoundScore(mpR2Target,
                    r2P1?.let { if (it == -2L) null else it.toInt() },
                    r2P2?.let { if (it == -2L) null else it.toInt() }, false)
                val p1Total = r1Score.first + r2Score.first
                val p2Total = r1Score.second + r2Score.second
                val myFinal  = if (isPlayer1) p1Total else p2Total
                val oppFinal = if (isPlayer1) p2Total else p1Total
                _uiState.value = _uiState.value.copy(
                    myScore = myFinal, opponentScore = oppFinal,
                    phase = MojBrojPhase.GAME_OVER, gameOver = true
                )
                endGameSave(myFinal, oppFinal)
            }
        }
    }

    // Called for same-phase Firestore updates (results arriving while phase stays unchanged).
    private fun handleMpLiveUpdate(phase: String, data: Map<String, Any>) {
        when (phase) {
            "mb_r1_play" -> {
                val r1P1 = data["r1P1"] as? Long ?: -1L
                val r1P2 = data["r1P2"] as? Long ?: -1L
                if (r1P1 == -1L || r1P2 == -1L || r1TransitionDone) return
                r1TransitionDone = true
                timerJob?.cancel()
                val r1P1Int = if (r1P1 == -2L) null else r1P1.toInt()
                val r1P2Int = if (r1P2 == -2L) null else r1P2.toInt()
                val score   = computeMpRoundScore(mpR1Target, r1P1Int, r1P2Int, true)
                val myPts   = if (isPlayer1) score.first  else score.second
                val oppPts  = if (isPlayer1) score.second else score.first
                _uiState.value = _uiState.value.copy(
                    phase = MojBrojPhase.SUBMITTED,
                    myScore = myPts, opponentScore = oppPts,
                    myRoundResult  = if (isPlayer1) r1P1Int else r1P2Int,
                    opponentRoundResult = if (isPlayer1) r1P2Int else r1P1Int,
                    roundResultMessage = score.third,
                    waitingMessage = null
                )
                // P1 is the designated writer for the R1→R2 transition.
                if (isPlayer1) viewModelScope.launch {
                    delay(2500L)
                    mpRepo.setPhase(sessionId, "mb_r2_s1")
                }
            }
            "mb_r2_play" -> {
                val r2P1 = data["r2P1"] as? Long ?: -1L
                val r2P2 = data["r2P2"] as? Long ?: -1L
                if (r2P1 == -1L || r2P2 == -1L || r2TransitionDone) return
                r2TransitionDone = true
                timerJob?.cancel()
                val r1P1 = data["r1P1"] as? Long ?: -1L
                val r1P2 = data["r1P2"] as? Long ?: -1L
                val r1P1Int = if (r1P1 == -2L) null else r1P1.toInt()
                val r1P2Int = if (r1P2 == -2L) null else r1P2.toInt()
                val r2P1Int = if (r2P1 == -2L) null else r2P1.toInt()
                val r2P2Int = if (r2P2 == -2L) null else r2P2.toInt()
                val r1Score = computeMpRoundScore(mpR1Target, r1P1Int, r1P2Int, true)
                val r2Score = computeMpRoundScore(mpR2Target, r2P1Int, r2P2Int, false)
                val p1Total = r1Score.first + r2Score.first
                val p2Total = r1Score.second + r2Score.second
                val myFinal  = if (isPlayer1) p1Total else p2Total
                val oppFinal = if (isPlayer1) p2Total else p1Total
                _uiState.value = _uiState.value.copy(
                    phase = MojBrojPhase.SUBMITTED,
                    myScore = myFinal, opponentScore = oppFinal,
                    myRoundResult  = if (isPlayer1) r2P1Int else r2P2Int,
                    opponentRoundResult = if (isPlayer1) r2P2Int else r2P1Int,
                    roundResultMessage = r2Score.third,
                    waitingMessage = null
                )
                // P2 is the designated writer for the R2→done transition.
                if (!isPlayer1) viewModelScope.launch {
                    delay(2500L)
                    mpRepo.setPhase(sessionId, "mb_done")
                }
            }
        }
    }

    // 5-second countdown after target is revealed; auto-writes nextMpPhase when it hits 0.
    private fun startStopCountdown(nextMpPhase: String) {
        stopWindowJob?.cancel()
        stopWindowJob = viewModelScope.launch {
            var t = 5
            while (t > 0) {
                delay(1000L); t--
                _uiState.value = _uiState.value.copy(waitingForStopTimeLeft = t)
            }
            if (_uiState.value.phase == MojBrojPhase.WAITING_STOP2) {
                mpRepo.setPhase(sessionId, nextMpPhase)
            }
        }
    }

    // Multiplayer 60s play timer — starts when numbers are revealed.
    private fun startMainTimerMp() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (_uiState.value.timeLeft > 0) {
                delay(1000L)
                val s = _uiState.value
                if (s.phase == MojBrojPhase.SUBMITTED || s.phase == MojBrojPhase.GAME_OVER) break
                _uiState.value = s.copy(timeLeft = s.timeLeft - 1)
            }
            val p = _uiState.value.phase
            if (p != MojBrojPhase.SUBMITTED && p != MojBrojPhase.GAME_OVER) submitRound()
        }
    }

    // ─── Single-player game flow ───

    private fun startRound(round: Int) {
        timerJob?.cancel(); stopWindowJob?.cancel()
        val target = generateTarget()
        val numbers = generateNumbers()
        _uiState.value = MojBrojUiState(
            currentRound = round,
            targetNumber = target,
            availableNumbers = numbers,
            myScore = _uiState.value.myScore,
            opponentScore = _uiState.value.opponentScore,
            phase = MojBrojPhase.WAITING_STOP1,
            timeLeft = 60
        )
        startMainTimer()
    }

    private fun startMainTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (_uiState.value.timeLeft > 0) {
                delay(1000L)
                val phase = _uiState.value.phase
                if (phase == MojBrojPhase.SUBMITTED || phase == MojBrojPhase.GAME_OVER) break
                val newTime = _uiState.value.timeLeft - 1
                _uiState.value = _uiState.value.copy(timeLeft = newTime)
                if (newTime == 55 && _uiState.value.phase == MojBrojPhase.WAITING_STOP1) {
                    revealTargetAndNumbers()
                }
            }
            val phase = _uiState.value.phase
            if (phase != MojBrojPhase.SUBMITTED && phase != MojBrojPhase.GAME_OVER) {
                submitRound()
            }
        }
    }

    fun pressStop() {
        val state = _uiState.value
        if (isMultiplayer) {
            // Only the round's stop controller can press STOP.
            val isController = (state.currentRound == 1 && isPlayer1) ||
                               (state.currentRound == 2 && !isPlayer1)
            if (!isController || state.waitingMessage != null) return
            when (state.phase) {
                MojBrojPhase.WAITING_STOP1 -> {
                    val next = if (state.currentRound == 1) "mb_r1_s2" else "mb_r2_s2"
                    viewModelScope.launch { mpRepo.setPhase(sessionId, next) }
                }
                MojBrojPhase.WAITING_STOP2 -> {
                    stopWindowJob?.cancel()
                    val next = if (state.currentRound == 1) "mb_r1_play" else "mb_r2_play"
                    viewModelScope.launch { mpRepo.setPhase(sessionId, next) }
                }
                else -> {}
            }
        } else {
            when (state.phase) {
                MojBrojPhase.WAITING_STOP1 -> revealTarget()
                MojBrojPhase.WAITING_STOP2 -> revealNumbers()
                else -> {}
            }
        }
    }

    private fun onShake() { pressStop() }

    private fun revealTarget() {
        stopWindowJob?.cancel()
        _uiState.value = _uiState.value.copy(
            phase = MojBrojPhase.WAITING_STOP2, waitingForStopTimeLeft = 5
        )
        stopWindowJob = viewModelScope.launch {
            var t = 5
            while (t > 0) {
                delay(1000L); t--
                _uiState.value = _uiState.value.copy(waitingForStopTimeLeft = t)
            }
            if (_uiState.value.phase == MojBrojPhase.WAITING_STOP2) revealNumbers()
        }
    }

    private fun revealNumbers() {
        stopWindowJob?.cancel()
        _uiState.value = _uiState.value.copy(phase = MojBrojPhase.PLAYING)
    }

    private fun revealTargetAndNumbers() {
        stopWindowJob?.cancel()
        _uiState.value = _uiState.value.copy(phase = MojBrojPhase.PLAYING)
    }

    // ─── Expression editing ───

    fun appendNumber(index: Int) {
        val state = _uiState.value
        if (state.phase != MojBrojPhase.PLAYING) return
        if (index in state.usedNumberIndices) return
        val num = state.availableNumbers[index]
        val newExpr = if (state.expression.isEmpty() ||
            state.expression.last().let { it == '+' || it == '-' || it == '*' || it == '/' || it == '(' }
        ) state.expression + num else "${state.expression} $num"
        val newIndices = state.expressionIndices + index
        _uiState.value = state.copy(
            expression = newExpr,
            usedNumberIndices = newIndices.toSet(),
            expressionIndices = newIndices,
            expressionResult = evalExpression(newExpr)
        )
    }

    fun appendOperator(op: String) {
        val state = _uiState.value
        if (state.phase != MojBrojPhase.PLAYING) return
        _uiState.value = state.copy(
            expression = state.expression + op,
            expressionResult = evalExpression(state.expression + op)
        )
    }

    fun backspace() {
        val state = _uiState.value
        if (state.phase != MojBrojPhase.PLAYING) return
        val removedNumber = state.expression.trimEnd().lastOrNull()?.isDigit() == true
        val newExpr = removeLastToken(state.expression)
        val newIndices = if (removedNumber && state.expressionIndices.isNotEmpty())
            state.expressionIndices.dropLast(1) else state.expressionIndices
        _uiState.value = state.copy(
            expression = newExpr,
            usedNumberIndices = newIndices.toSet(),
            expressionIndices = newIndices,
            expressionResult = evalExpression(newExpr)
        )
    }

    fun clearExpression() {
        val state = _uiState.value
        if (state.phase != MojBrojPhase.PLAYING) return
        _uiState.value = state.copy(
            expression = "",
            usedNumberIndices = emptySet(),
            expressionIndices = emptyList(),
            expressionResult = null
        )
    }

    // ─── Submit ───

    fun submitRound() {
        val state = _uiState.value
        if (state.phase == MojBrojPhase.SUBMITTED || state.phase == MojBrojPhase.GAME_OVER) return
        timerJob?.cancel(); stopWindowJob?.cancel()

        val myResult = state.expressionResult
        val target = state.targetNumber
        if (myResult == target) mojBrojHitsLocal++

        if (isMultiplayer) {
            submitRoundMultiplayer(state, myResult)
        } else {
            // Izazov: fully solo - no simulated opponent, only exact hits score.
            val opponentResult = if (izazovMode) null else simulateOpponentResult(target, state.availableNumbers)
            val userIsRoundStarter = state.currentRound == 1
            val (myPoints, opponentPoints, message) = if (izazovMode) {
                if (myResult == target) Triple(10, 0, "Pogodio si traženi broj! +10")
                else Triple(0, 0, "Nisi pogodio tačan broj.")
            } else {
                scoreRound(target, myResult, opponentResult, userIsRoundStarter)
            }

            _uiState.value = state.copy(
                phase = MojBrojPhase.SUBMITTED,
                myScore = state.myScore + myPoints,
                opponentScore = state.opponentScore + opponentPoints,
                myRoundResult = myResult,
                opponentRoundResult = opponentResult,
                roundResultMessage = message
            )

            // Izazov: fully solo, one round only (spec 9d - every game appears once).
            if (state.currentRound < 2 && !izazovMode) {
                viewModelScope.launch { delay(2500L); startRound(2) }
            } else {
                val finalMyScore = _uiState.value.myScore
                val finalOppScore = _uiState.value.opponentScore
                viewModelScope.launch {
                    delay(2500L)
                    _uiState.value = _uiState.value.copy(phase = MojBrojPhase.GAME_OVER, gameOver = true)
                    endGameSave(finalMyScore, finalOppScore)
                }
            }
        }
    }

    private fun submitRoundMultiplayer(state: MojBrojUiState, myResult: Int?) {
        val resultVal = myResult?.toLong() ?: -2L
        _uiState.value = state.copy(
            phase = MojBrojPhase.SUBMITTED,
            myRoundResult = myResult,
            waitingMessage = "Čekanje na protivnika..."
        )
        // Write result only; do NOT change the Firestore phase.
        // handleMpLiveUpdate detects when both results are in and triggers the transition.
        val key = when {
            isPlayer1 && state.currentRound == 1  -> "r1P1"
            !isPlayer1 && state.currentRound == 1 -> "r1P2"
            isPlayer1 && state.currentRound == 2  -> "r2P1"
            else                                   -> "r2P2"
        }
        mpRepo.updateGameData(sessionId, mapOf(key to resultVal))
        checkAndForceIfStuck()
    }

    // ─── Scoring ───

    private fun computeMpRoundScore(
        target: Int, p1Result: Int?, p2Result: Int?,
        isP1Starter: Boolean
    ): Triple<Int, Int, String> {
        val p1Hit = p1Result == target
        val p2Hit = p2Result == target
        return when {
            p1Hit && !p2Hit -> Triple(10, 0,
                if (isPlayer1) "Pogodio si traženi broj! +10" else "Tvoj protivnik pogodio! +10 njemu")
            p2Hit && !p1Hit -> Triple(0, 10,
                if (isPlayer1) "Protivnik pogodio! +10 njemu" else "Pogodio si traženi broj! +10")
            p1Hit && p2Hit  -> Triple(10, 10, "Oba igrača su pogodila! +10 svaki")
            else -> {
                val p1Diff = if (p1Result != null && p1Result != 0) abs(p1Result - target) else Int.MAX_VALUE
                val p2Diff = if (p2Result != null && p2Result != 0) abs(p2Result - target) else Int.MAX_VALUE
                when {
                    p1Diff < p2Diff -> Triple(5, 0,
                        if (isPlayer1) "Bliže si cilju! +5" else "Protivnik bliže cilju! +5 njemu")
                    p2Diff < p1Diff -> Triple(0, 5,
                        if (isPlayer1) "Protivnik bliže cilju! +5 njemu" else "Bliže si cilju! +5")
                    p1Result != null && p1Result != 0 && p1Result == p2Result ->
                        if (isP1Starter)
                            Triple(5, 0, if (isPlayer1) "Isti rezultat – ti si prvi! +5" else "Isti – protivnik je bio prvi! +5 njemu")
                        else
                            Triple(0, 5, if (isPlayer1) "Isti – protivnik je bio prvi! +5 njemu" else "Isti rezultat – ti si prvi! +5")
                    else -> Triple(0, 0, "Niko nije pogodio")
                }
            }
        }
    }

    private fun scoreRound(
        target: Int, myResult: Int?, opponentResult: Int?,
        userIsRoundStarter: Boolean
    ): Triple<Int, Int, String> {
        val myHit  = myResult == target
        val oppHit = opponentResult == target
        return when {
            myHit && !oppHit -> Triple(10, 0, "Pogodio si traženi broj! +10")
            oppHit && !myHit -> Triple(0, 10, "Protivnik je pogodio traženi broj")
            myHit && oppHit  -> Triple(10, 10, "Oba igrača su pogodila!")
            else -> {
                val myDiff  = if (myResult != null && myResult != 0) abs(myResult - target) else Int.MAX_VALUE
                val oppDiff = if (opponentResult != null && opponentResult != 0) abs(opponentResult - target) else Int.MAX_VALUE
                when {
                    myDiff < oppDiff  -> Triple(5, 0, "Bliže si cilju! +5")
                    oppDiff < myDiff  -> Triple(0, 5, "Protivnik je bliže cilju")
                    myResult != null && myResult != 0 && myResult == opponentResult ->
                        if (userIsRoundStarter) Triple(5, 0, "Isti rezultat – poeni za tebe (+5)")
                        else Triple(0, 5, "Isti rezultat – poeni za protivnika (+5)")
                    else -> Triple(0, 0, "Niko nije pogodio")
                }
            }
        }
    }

    private fun simulateOpponentResult(target: Int, @Suppress("UNUSED_PARAMETER") numbers: List<Int>): Int? {
        return when ((0..2).random()) {
            0 -> target
            1 -> { val r = target + (-30..30).random(); if (r > 0) r else null }
            else -> null
        }
    }

    private fun endGameSave(myScore: Int, oppScore: Int) {
        if (!resultSaved) {
            resultSaved = true
            viewModelScope.launch {
                runCatching {
                    userRepo.saveMojBrojResult(mojBrojHitsLocal, 2)
                    userRepo.saveGameResult(won = myScore > oppScore)
                }
            }
        }
    }

    private fun generateTarget(): Int = (100..999).random()

    private fun generateNumbers(): List<Int> {
        val single = (1..4).map { (1..9).random() }
        val medium = listOf(10, 15, 20).random()
        val large  = listOf(25, 50, 75, 100).random()
        return single + medium + large
    }

    override fun onCleared() {
        super.onCleared()
        sensorManager.unregisterListener(shakeListener)
        timerJob?.cancel(); stopWindowJob?.cancel(); mpListener?.remove(); forfeitListener?.remove()
    }

    companion object {
        private const val SHAKE_THRESHOLD = 12f
    }
}

class MojBrojViewModelFactory(
    private val application: Application,
    private val sessionId: String,
    private val isPlayer1: Boolean,
    private val gameIdx: Int,
    private val izazovMode: Boolean = false
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        MojBrojViewModel(application, sessionId, isPlayer1, gameIdx, izazovMode) as T
}
