package com.tim03.slagalica.viewmodel

import android.app.Application
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tim03.slagalica.util.evalExpression
import com.tim03.slagalica.util.recalcUsedIndices
import com.tim03.slagalica.util.removeLastToken
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.math.sqrt

enum class MojBrojPhase {
    WAITING_FIRST_STOP,
    WAITING_SECOND_STOP,
    PLAYING,
    SUBMITTED,
    OPPONENT_TURN,
    GAME_OVER
}

data class MojBrojUiState(
    val currentRound: Int = 1,
    val targetNumber: Int = 0,
    val availableNumbers: List<Int> = emptyList(),
    val expression: String = "",
    val usedNumberIndices: Set<Int> = emptySet(),
    val expressionResult: Int? = null,
    val timeLeft: Int = 60,
    val waitingForStopTimeLeft: Int = 5,
    val myScore: Int = 0,
    val opponentScore: Int = 0,
    val phase: MojBrojPhase = MojBrojPhase.WAITING_FIRST_STOP,
    val myRoundExpression: String = "",
    val myRoundResult: Int? = null,
    val opponentRoundResult: Int? = null,
    val roundResultMessage: String? = null,
    val gameOver: Boolean = false
)

class MojBrojViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(MojBrojUiState())
    val uiState: StateFlow<MojBrojUiState> = _uiState.asStateFlow()

    private var timerJob: Job? = null
    private var stopWindowJob: Job? = null
    private var sensorManager: SensorManager =
        application.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private var accelerometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private var lastShakeTime = 0L

    private val shakeListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val x = event.values[0]; val y = event.values[1]; val z = event.values[2]
            val acceleration = sqrt(x * x + y * y + z * z) - SensorManager.GRAVITY_EARTH
            if (acceleration > SHAKE_THRESHOLD) {
                val now = System.currentTimeMillis()
                if (now - lastShakeTime > 1000L) {
                    lastShakeTime = now
                    onShake()
                }
            }
        }
        override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
    }

    init {
        accelerometer?.let {
            sensorManager.registerListener(shakeListener, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        startRound(1)
    }

    private fun startRound(round: Int) {
        timerJob?.cancel()
        stopWindowJob?.cancel()
        val target = generateTarget()
        val numbers = generateNumbers()
        _uiState.value = MojBrojUiState(
            currentRound = round,
            targetNumber = target,
            availableNumbers = numbers,
            myScore = _uiState.value.myScore,
            opponentScore = _uiState.value.opponentScore,
            phase = MojBrojPhase.WAITING_FIRST_STOP,
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

                // Auto-reveal after 5s without first STOP
                if (newTime == 55 && _uiState.value.phase == MojBrojPhase.WAITING_FIRST_STOP) {
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
        when (_uiState.value.phase) {
            MojBrojPhase.WAITING_FIRST_STOP -> revealTarget()
            MojBrojPhase.WAITING_SECOND_STOP -> revealNumbers()
            else -> {}
        }
    }

    private fun onShake() {
        pressStop()
    }

    private fun revealTarget() {
        stopWindowJob?.cancel()
        _uiState.value = _uiState.value.copy(
            phase = MojBrojPhase.WAITING_SECOND_STOP,
            waitingForStopTimeLeft = 5
        )
        stopWindowJob = viewModelScope.launch {
            var t = 5
            while (t > 0) {
                delay(1000L)
                t--
                _uiState.value = _uiState.value.copy(waitingForStopTimeLeft = t)
            }
            if (_uiState.value.phase == MojBrojPhase.WAITING_SECOND_STOP) {
                revealNumbers()
            }
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

    fun appendNumber(index: Int) {
        val state = _uiState.value
        if (state.phase != MojBrojPhase.PLAYING) return
        if (index in state.usedNumberIndices) return
        val num = state.availableNumbers[index]
        val newExpr = if (state.expression.isEmpty() ||
            state.expression.last().let { it == '+' || it == '-' || it == '*' || it == '/' || it == '(' }
        ) {
            state.expression + num
        } else {
            "${state.expression} $num"
        }
        val newUsed = state.usedNumberIndices + index
        _uiState.value = state.copy(
            expression = newExpr,
            usedNumberIndices = newUsed,
            expressionResult = evalExpression(newExpr)
        )
    }

    fun appendOperator(op: String) {
        val state = _uiState.value
        if (state.phase != MojBrojPhase.PLAYING) return
        val newExpr = state.expression + op
        _uiState.value = state.copy(
            expression = newExpr,
            expressionResult = evalExpression(newExpr)
        )
    }

    fun backspace() {
        val state = _uiState.value
        if (state.phase != MojBrojPhase.PLAYING) return
        val newExpr = removeLastToken(state.expression)
        val newUsed = recalcUsedIndices(newExpr, state.availableNumbers)
        _uiState.value = state.copy(
            expression = newExpr,
            usedNumberIndices = newUsed,
            expressionResult = evalExpression(newExpr)
        )
    }

    fun clearExpression() {
        val state = _uiState.value
        if (state.phase != MojBrojPhase.PLAYING) return
        _uiState.value = state.copy(
            expression = "",
            usedNumberIndices = emptySet(),
            expressionResult = null
        )
    }

    fun submitRound() {
        val state = _uiState.value
        if (state.phase == MojBrojPhase.SUBMITTED || state.phase == MojBrojPhase.GAME_OVER) return
        timerJob?.cancel()
        stopWindowJob?.cancel()

        val myResult = state.expressionResult
        val target = state.targetNumber

        // Simulate opponent result (opponent also plays simultaneously in each round)
        val opponentResult = simulateOpponentResult(target, state.availableNumbers)
        // In round 1, user is the round starter; in round 2, opponent is the round starter
        val userIsRoundStarter = state.currentRound == 1

        val (myPoints, opponentPoints, message) = scoreRound(target, myResult, opponentResult, userIsRoundStarter)

        _uiState.value = state.copy(
            phase = MojBrojPhase.SUBMITTED,
            myScore = state.myScore + myPoints,
            opponentScore = state.opponentScore + opponentPoints,
            myRoundResult = myResult,
            opponentRoundResult = opponentResult,
            roundResultMessage = message
        )

        if (state.currentRound < 2) {
            viewModelScope.launch {
                delay(2500L)
                startRound(2)
            }
        } else {
            viewModelScope.launch {
                delay(2500L)
                _uiState.value = _uiState.value.copy(phase = MojBrojPhase.GAME_OVER, gameOver = true)
            }
        }
    }

    private fun scoreRound(
        target: Int,
        myResult: Int?,
        opponentResult: Int?,
        userIsRoundStarter: Boolean
    ): Triple<Int, Int, String> {
        val myHit = myResult == target
        val oppHit = opponentResult == target

        return when {
            myHit && !oppHit -> Triple(10, 0, "Pogodio si traženi broj! +10")
            oppHit && !myHit -> Triple(0, 10, "Protivnik je pogodio traženi broj")
            myHit && oppHit -> Triple(10, 10, "Oba igrača su pogodila!")
            else -> {
                val myDiff = if (myResult != null && myResult != 0) abs(myResult - target) else Int.MAX_VALUE
                val oppDiff = if (opponentResult != null && opponentResult != 0) abs(opponentResult - target) else Int.MAX_VALUE
                when {
                    myDiff < oppDiff -> Triple(5, 0, "Bliže si cilju! +5")
                    oppDiff < myDiff -> Triple(0, 5, "Protivnik je bliže cilju")
                    myResult != null && myResult != 0 && myResult == opponentResult -> {
                        // Same non-zero, non-target result: round starter gets 5 pts
                        if (userIsRoundStarter) Triple(5, 0, "Isti rezultat – poeni za tebe (+5)")
                        else Triple(0, 5, "Isti rezultat – poeni za protivnika (+5)")
                    }
                    else -> Triple(0, 0, "Niko nije pogodio")
                }
            }
        }
    }

    private fun simulateOpponentResult(target: Int, numbers: List<Int>): Int? {
        val chance = (0..2).random()
        return when (chance) {
            0 -> target // opponent hits
            1 -> {
                val offset = (-30..30).random()
                val result = target + offset
                if (result > 0) result else null
            }
            else -> null // opponent doesn't answer
        }
    }

    private fun generateTarget(): Int = (100..999).random()

    private fun generateNumbers(): List<Int> {
        val single = (1..4).map { (1..9).random() }
        val medium = listOf(10, 15, 20).random()
        val large = listOf(25, 50, 75, 100).random()
        return single + medium + large
    }

    override fun onCleared() {
        super.onCleared()
        sensorManager.unregisterListener(shakeListener)
        timerJob?.cancel()
        stopWindowJob?.cancel()
    }

    companion object {
        private const val SHAKE_THRESHOLD = 12f
    }
}
