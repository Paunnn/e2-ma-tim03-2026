package com.tim03.slagalica.ui.games

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tim03.slagalica.ui.theme.*
import com.tim03.slagalica.viewmodel.SpojniceViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

// Phases: Round 1 me first → Round 1 opponent bonus → Round 2 opponent first → Round 2 me bonus → Done
private enum class SpojnicePhase { R1_ME, R1_OPP, R2_OPP, R2_ME, DONE }

private fun nextPhase(current: SpojnicePhase, allCorrect: Boolean): SpojnicePhase = when (current) {
    SpojnicePhase.R1_ME  -> if (allCorrect) SpojnicePhase.R2_OPP else SpojnicePhase.R1_OPP
    SpojnicePhase.R1_OPP -> SpojnicePhase.R2_OPP
    SpojnicePhase.R2_OPP -> if (allCorrect) SpojnicePhase.DONE   else SpojnicePhase.R2_ME
    SpojnicePhase.R2_ME  -> SpojnicePhase.DONE
    SpojnicePhase.DONE   -> SpojnicePhase.DONE
}

@Composable
fun SpojniceScreen(
    onExitClick: () -> Unit,
    isPartijaMode: Boolean = false,
    onPartijaGameComplete: (Int, Int) -> Unit = { _, _ -> },
    myScoreOffset: Int = 0,
    oppScoreOffset: Int = 0,
    viewModel: SpojniceViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val username by viewModel.username.collectAsStateWithLifecycle()
    val rounds = state.rounds

    var myScore by remember { mutableStateOf(0) }
    var opponentScore by remember { mutableStateOf(0) }
    var timeLeft by remember { mutableStateOf(30) }
    var gameOver by remember { mutableStateOf(false) }
    var resultSaved by remember { mutableStateOf(false) }
    var totalConnected by remember { mutableStateOf(0) }
    var totalPairs by remember { mutableStateOf(0) }
    var phase by remember { mutableStateOf(SpojnicePhase.R1_ME) }

    // Per-round connections (left index → right index); resets each round
    var connections by remember { mutableStateOf(mapOf<Int, Int>()) }
    var correctConnections by remember { mutableStateOf(setOf<Int>()) }

    // Queue of left indices to be attempted in current phase
    var pendingItems by remember { mutableStateOf(listOf<Int>()) }
    var currentAttemptPos by remember { mutableStateOf(0) }

    // Opponent's currently active left item (for UI highlight)
    var opponentActiveLeft by remember { mutableStateOf<Int?>(null) }

    // Derived
    val roundIdx = if (phase == SpojnicePhase.R1_ME || phase == SpojnicePhase.R1_OPP) 0 else 1
    val isMyTurn = phase == SpojnicePhase.R1_ME || phase == SpojnicePhase.R2_ME
    val round = rounds.getOrNull(roundIdx)
    val activeLeftIndex = if (isMyTurn && currentAttemptPos < pendingItems.size) pendingItems[currentAttemptPos] else null
    val takenRightIndices = correctConnections.mapNotNull { connections[it] }.toSet()

    // Phase flow
    LaunchedEffect(phase, rounds.size) {
        if (phase == SpojnicePhase.DONE) {
            if (!resultSaved) {
                resultSaved = true
                viewModel.saveResult(totalConnected, totalPairs, won = myScore > opponentScore)
            }
            gameOver = true
            return@LaunchedEffect
        }
        if (rounds.isEmpty()) return@LaunchedEffect

        val rIdx = if (phase == SpojnicePhase.R1_ME || phase == SpojnicePhase.R1_OPP) 0 else 1
        val r = rounds.getOrNull(rIdx) ?: return@LaunchedEffect
        val isMyPhase = phase == SpojnicePhase.R1_ME || phase == SpojnicePhase.R2_ME
        val isFirst = phase == SpojnicePhase.R1_ME || phase == SpojnicePhase.R2_OPP

        // Reset connections for round 2
        if (phase == SpojnicePhase.R2_OPP) {
            connections = mapOf()
            correctConnections = setOf()
        }

        // Items for this phase
        val items = if (isFirst) (0 until r.leftItems.size).toList()
                    else (0 until r.leftItems.size).filter { it !in correctConnections }.sorted()

        if (items.isEmpty()) {
            delay(400L)
            phase = nextPhase(phase, correctConnections.size >= r.leftItems.size)
            return@LaunchedEffect
        }

        pendingItems = items
        currentAttemptPos = 0
        timeLeft = 30

        if (!isMyPhase) {
            // --- OPPONENT TURN ---
            val timerJob = launch {
                repeat(30) {
                    if (gameOver) return@repeat
                    delay(1000L)
                    timeLeft = (timeLeft - 1).coerceAtLeast(0)
                }
            }
            for (i in items.indices) {
                if (gameOver || timeLeft <= 0) break
                val leftIdx = items[i]
                opponentActiveLeft = leftIdx
                delay((700L..2600L).random())
                if (gameOver || timeLeft <= 0) { opponentActiveLeft = null; break }

                val correctRight = r.correctMapping.getOrElse(leftIdx) { 0 }
                val picks = if (Random.nextFloat() < 0.65f) correctRight
                            else (0 until r.rightItems.size).filter { it != correctRight }.randomOrNull() ?: correctRight
                connections = connections + (leftIdx to picks)
                if (picks == correctRight) {
                    correctConnections = correctConnections + leftIdx
                    opponentScore += 2; totalConnected++
                }
                totalPairs++
                currentAttemptPos = i + 1
                opponentActiveLeft = null
            }
            timerJob.cancel()
            delay(1200L)
        } else {
            // --- MY TURN --- (clicks handled by UI; timer runs here)
            while (timeLeft > 0 && currentAttemptPos < items.size && !gameOver) {
                delay(1000L)
                if (!gameOver) timeLeft = (timeLeft - 1).coerceAtLeast(0)
            }
            delay(600L)
        }

        phase = nextPhase(phase, correctConnections.size >= r.leftItems.size)
    }

    val roundLabel = if (roundIdx == 0) "Runda 1/2" else "Runda 2/2"
    val phaseLabel = when (phase) {
        SpojnicePhase.R1_ME  -> "Vaš red, Vi ste prvi"
        SpojnicePhase.R1_OPP -> "Protivnikov red, preostali pojmovi"
        SpojnicePhase.R2_OPP -> "Protivnikov red, Protivnik je prvi"
        SpojnicePhase.R2_ME  -> "Vaš red, preostali pojmovi"
        SpojnicePhase.DONE   -> "Kraj"
    }

    Box(modifier = Modifier.fillMaxSize().background(Navy)) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                GameHud(
                    gameName = "Spojnice",
                    gameColor = GameSpojnice,
                    gameIcon = "⫶",
                    round = roundLabel,
                    timeLeft = timeLeft,
                    totalTime = 30,
                    myScore = myScore + myScoreOffset,
                    oppScore = opponentScore + oppScoreOffset,
                    myName = username,
                    onExit = onExitClick
                )
            }
        ) { padding ->
            Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                when {
                    state.isLoading -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                CircularProgressIndicator(color = GameSpojnice)
                                Text("Učitavanje spojnica...", color = LightGray)
                            }
                        }
                    }
                    state.error != null -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Icon(Icons.Default.ErrorOutline, null, tint = ErrorRed, modifier = Modifier.size(48.dp))
                                Text("Greška pri učitavanju", color = ErrorRed, fontWeight = FontWeight.Bold)
                                Button(onClick = onExitClick, colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlueBright)) {
                                    Text("Nazad", color = White)
                                }
                            }
                        }
                    }
                    gameOver && !isPartijaMode -> {
                        GameOverContent(myScore = myScore, opponentScore = opponentScore, onFinish = onExitClick)
                    }
                    else -> {
                        LaunchedEffect(gameOver) {
                            if (gameOver && isPartijaMode) {
                                delay(3000L)
                                onPartijaGameComplete(myScore, opponentScore)
                            }
                        }
                        Column(
                            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Phase indicator
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isMyTurn) PrimaryBlueBright.copy(alpha = 0.15f) else Accent2.copy(alpha = 0.15f)
                                ),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp, if (isMyTurn) PrimaryBlueBright.copy(alpha = 0.5f) else Accent2.copy(alpha = 0.5f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp, 7.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        if (isMyTurn) Icons.Default.Person else Icons.Default.SmartToy,
                                        null,
                                        tint = if (isMyTurn) PrimaryBlueBright else Accent2,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        phaseLabel,
                                        color = if (isMyTurn) PrimaryBlueBright else Accent2,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }
                            }

                            // Criterion
                            if (round != null) {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = CardDefaults.cardColors(containerColor = NavyCard)
                                ) {
                                    Text(
                                        round.criterion,
                                        color = White,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.fillMaxWidth().padding(12.dp, 10.dp)
                                    )
                                }

                                // Columns
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // Left column
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            "LEVO", color = MediumGray, fontSize = 9.sp,
                                            fontWeight = FontWeight.ExtraBold, letterSpacing = 1.5.sp,
                                            modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center
                                        )
                                        round.leftItems.forEachIndexed { idx, text ->
                                            val isActive = isMyTurn && idx == activeLeftIndex
                                            val isOppActive = !isMyTurn && idx == opponentActiveLeft
                                            val isCorrect = idx in correctConnections
                                            val wasAttempted = idx in connections.keys
                                            val isWrong = wasAttempted && !isCorrect
                                            LeftItemCard(
                                                text = text,
                                                isActive = isActive,
                                                isOppActive = isOppActive,
                                                isCorrect = isCorrect,
                                                isWrong = isWrong
                                            )
                                        }
                                    }

                                    // Right column
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            "DESNO", color = MediumGray, fontSize = 9.sp,
                                            fontWeight = FontWeight.ExtraBold, letterSpacing = 1.5.sp,
                                            modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center
                                        )
                                        round.rightItems.forEachIndexed { idx, text ->
                                            val isTaken = idx in takenRightIndices
                                            val isClickable = isMyTurn && activeLeftIndex != null && !isTaken && !gameOver
                                            RightItemCard(
                                                text = text,
                                                isTaken = isTaken,
                                                isClickable = isClickable,
                                                isMyTurn = isMyTurn,
                                                onClick = {
                                                    val leftIdx = activeLeftIndex ?: return@RightItemCard
                                                    val correct = round.correctMapping.getOrElse(leftIdx) { -1 }
                                                    connections = connections + (leftIdx to idx)
                                                    if (idx == correct) {
                                                        correctConnections = correctConnections + leftIdx
                                                        myScore += 2; totalConnected++
                                                    }
                                                    totalPairs++
                                                    currentAttemptPos++
                                                }
                                            )
                                        }
                                    }
                                }

                                // Hint
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = CardDefaults.cardColors(containerColor = NavyCard),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, LineSoft)
                                ) {
                                    Text(
                                        "2 boda za svaki tačno povezan par · max 10 po rundi",
                                        color = MediumGray, fontSize = 11.sp, textAlign = TextAlign.Center,
                                        modifier = Modifier.fillMaxWidth().padding(8.dp, 6.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LeftItemCard(
    text: String,
    isActive: Boolean,
    isOppActive: Boolean,
    isCorrect: Boolean,
    isWrong: Boolean
) {
    val bgColor = when {
        isCorrect -> SuccessGreen.copy(alpha = 0.2f)
        isActive -> PrimaryBlueBright.copy(alpha = 0.18f)
        isOppActive -> Accent2.copy(alpha = 0.18f)
        isWrong -> ErrorRed.copy(alpha = 0.2f)
        else -> NavyCard
    }
    val borderColor = when {
        isCorrect -> SuccessGreen
        isActive -> PrimaryBlueBright
        isOppActive -> Accent2
        isWrong -> ErrorRed
        else -> LineSoft
    }
    val borderWidth = if (isActive || isOppActive || isCorrect || isWrong) 2.dp else 1.dp
    val textColor = when {
        isCorrect -> SuccessGreen
        isActive -> White
        isOppActive -> Accent2
        isWrong -> ErrorRed
        else -> LightGray
    }
    val alpha = if (!isCorrect && !isWrong && !isActive && !isOppActive) 0.6f else 1f

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .alpha(alpha)
            .clip(RoundedCornerShape(10.dp))
            .background(bgColor)
            .border(borderWidth, borderColor, RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        val icon = when {
            isCorrect -> Icons.Default.CheckCircle
            isActive || isOppActive -> Icons.AutoMirrored.Filled.ArrowForward
            isWrong -> Icons.Default.Cancel
            else -> null
        }
        if (icon != null) {
            Icon(icon, null, tint = borderColor, modifier = Modifier.size(14.dp))
        }
        Text(text, color = textColor, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun RightItemCard(
    text: String,
    isTaken: Boolean,
    isClickable: Boolean,
    isMyTurn: Boolean,
    onClick: () -> Unit
) {
    val bgColor = when {
        isTaken -> SuccessGreen.copy(alpha = 0.15f)
        isClickable -> NavyCardLight
        else -> NavyCard
    }
    val borderColor = when {
        isTaken -> SuccessGreen
        isClickable -> GameSpojnice.copy(alpha = 0.7f)
        else -> LineSoft
    }
    val textColor = when {
        isTaken -> SuccessGreen
        isClickable -> White
        else -> MediumGray
    }
    val alpha = if (!isTaken && !isClickable && isMyTurn) 0.5f else 1f

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .alpha(alpha)
            .clip(RoundedCornerShape(10.dp))
            .background(bgColor)
            .border(if (isTaken || isClickable) 2.dp else 1.dp, borderColor, RoundedCornerShape(10.dp))
            .then(if (isClickable) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(text, color = textColor, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, modifier = Modifier.weight(1f))
        if (isTaken) {
            Icon(Icons.Default.CheckCircle, null, tint = SuccessGreen, modifier = Modifier.size(14.dp))
        } else if (isClickable) {
            Icon(Icons.Default.RadioButtonUnchecked, null, tint = GameSpojnice.copy(alpha = 0.6f), modifier = Modifier.size(14.dp))
        }
    }
}
