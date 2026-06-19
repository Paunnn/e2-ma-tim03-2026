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
import com.tim03.slagalica.viewmodel.SpojnicePhase
import com.tim03.slagalica.viewmodel.SpojniceViewModel
import com.tim03.slagalica.viewmodel.SpojniceViewModelFactory
import kotlinx.coroutines.delay

@Composable
fun SpojniceScreen(
    onExitClick: () -> Unit,
    isPartijaMode: Boolean = false,
    onPartijaGameComplete: (Int, Int) -> Unit = { _, _ -> },
    myScoreOffset: Int = 0,
    oppScoreOffset: Int = 0,
    sessionId: String = "",
    isPlayer1: Boolean = true,
    gameIdx: Int = -1,
    oppName: String = "Protivnik"
) {
    val viewModel: SpojniceViewModel = viewModel(
        factory = SpojniceViewModelFactory(sessionId, isPlayer1, gameIdx)
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val username by viewModel.username.collectAsStateWithLifecycle()

    val rounds = state.rounds
    val roundIdx = if (state.phase == SpojnicePhase.R1_ME || state.phase == SpojnicePhase.R1_OPP) 0 else 1
    val isMyTurn = state.isMyActiveTurn
    val round = rounds.getOrNull(roundIdx)
    val activeLeftIndex: Int? = if (isMyTurn && state.currentAttemptPos < state.pendingItems.size)
        state.pendingItems[state.currentAttemptPos] else null
    val takenRightIndices = state.correctConnections.mapNotNull { state.connections[it] }.toSet()

    LaunchedEffect(state.gameOver) {
        if (state.gameOver && isPartijaMode) {
            delay(3000L)
            onPartijaGameComplete(state.myScore, state.opponentScore)
        }
    }

    val roundLabel = if (roundIdx == 0) "Runda 1/2" else "Runda 2/2"
    val oppTurnLabel = if (sessionId.isNotEmpty()) "Igra $oppName" else "Protivnikov red"
    val phaseLabel = when (state.phase) {
        SpojnicePhase.R1_ME  -> if (isMyTurn) "Vaš red, Runda 1" else "$oppTurnLabel, Runda 1"
        SpojnicePhase.R1_OPP -> if (isMyTurn) "Vaš red, preostali pojmovi" else "$oppTurnLabel, preostali pojmovi"
        SpojnicePhase.R2_OPP -> if (isMyTurn) "Vaš red, Runda 2" else "$oppTurnLabel, Runda 2"
        SpojnicePhase.R2_ME  -> if (isMyTurn) "Vaš red, preostali pojmovi" else "$oppTurnLabel, preostali pojmovi"
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
                    timeLeft = state.timeLeft,
                    totalTime = 30,
                    myScore = state.myScore + myScoreOffset,
                    oppScore = state.opponentScore + oppScoreOffset,
                    myName = username,
                    oppName = oppName,
                    onExit = onExitClick
                )
            }
        ) { padding ->
            Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                when {
                    state.isLoading -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                CircularProgressIndicator(color = GameSpojnice)
                                Text("Učitavanje spojnica...", color = LightGray)
                            }
                        }
                    }
                    state.error != null -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(Icons.Default.ErrorOutline, null, tint = ErrorRed, modifier = Modifier.size(48.dp))
                                Text("Greška pri učitavanju", color = ErrorRed, fontWeight = FontWeight.Bold)
                                Button(onClick = onExitClick, colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlueBright)) {
                                    Text("Nazad", color = White)
                                }
                            }
                        }
                    }
                    state.gameOver && !isPartijaMode -> {
                        GameOverContent(myScore = state.myScore, opponentScore = state.opponentScore, onFinish = onExitClick)
                    }
                    else -> {
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

                            if (round != null) {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = CardDefaults.cardColors(containerColor = NavyCard)
                                ) {
                                    Text(
                                        round.criterion,
                                        color = White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.fillMaxWidth().padding(12.dp, 10.dp)
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(
                                            "LEVO", color = MediumGray, fontSize = 9.sp,
                                            fontWeight = FontWeight.ExtraBold, letterSpacing = 1.5.sp,
                                            modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center
                                        )
                                        round.leftItems.forEachIndexed { idx, text ->
                                            val isActive = isMyTurn && idx == activeLeftIndex
                                            val isOppActive = !isMyTurn && idx == state.opponentActiveLeft
                                            val isCorrect = idx in state.correctConnections
                                            val wasAttempted = idx in state.connections.keys
                                            val isWrong = wasAttempted && !isCorrect
                                            SpojniceLeftItemCard(
                                                text = text, isActive = isActive, isOppActive = isOppActive,
                                                isCorrect = isCorrect, isWrong = isWrong
                                            )
                                        }
                                    }

                                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(
                                            "DESNO", color = MediumGray, fontSize = 9.sp,
                                            fontWeight = FontWeight.ExtraBold, letterSpacing = 1.5.sp,
                                            modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center
                                        )
                                        round.rightItems.forEachIndexed { idx, text ->
                                            val isTaken = idx in takenRightIndices
                                            val isClickable = isMyTurn && activeLeftIndex != null && !isTaken && !state.gameOver
                                            SpojniceRightItemCard(
                                                text = text, isTaken = isTaken, isClickable = isClickable,
                                                isMyTurn = isMyTurn,
                                                onClick = {
                                                    val leftIdx = activeLeftIndex ?: return@SpojniceRightItemCard
                                                    viewModel.connectItem(leftIdx, idx)
                                                }
                                            )
                                        }
                                    }
                                }

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
private fun SpojniceLeftItemCard(
    text: String, isActive: Boolean, isOppActive: Boolean, isCorrect: Boolean, isWrong: Boolean
) {
    val bgColor = when {
        isCorrect -> SuccessGreen.copy(alpha = 0.2f)
        isActive -> PrimaryBlueBright.copy(alpha = 0.18f)
        isOppActive -> Accent2.copy(alpha = 0.18f)
        isWrong -> ErrorRed.copy(alpha = 0.2f)
        else -> NavyCard
    }
    val borderColor = when {
        isCorrect -> SuccessGreen; isActive -> PrimaryBlueBright; isOppActive -> Accent2
        isWrong -> ErrorRed; else -> LineSoft
    }
    val textColor = when {
        isCorrect -> SuccessGreen; isActive -> White; isOppActive -> Accent2
        isWrong -> ErrorRed; else -> LightGray
    }
    val alpha = if (!isCorrect && !isWrong && !isActive && !isOppActive) 0.6f else 1f
    val borderWidth = if (isActive || isOppActive || isCorrect || isWrong) 2.dp else 1.dp

    Row(
        modifier = Modifier.fillMaxWidth().height(44.dp).alpha(alpha)
            .clip(RoundedCornerShape(10.dp)).background(bgColor)
            .border(borderWidth, borderColor, RoundedCornerShape(10.dp)).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        val icon = when {
            isCorrect -> Icons.Default.CheckCircle
            isActive || isOppActive -> Icons.AutoMirrored.Filled.ArrowForward
            isWrong -> Icons.Default.Cancel
            else -> null
        }
        if (icon != null) Icon(icon, null, tint = borderColor, modifier = Modifier.size(14.dp))
        Text(text, color = textColor, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun SpojniceRightItemCard(
    text: String, isTaken: Boolean, isClickable: Boolean, isMyTurn: Boolean, onClick: () -> Unit
) {
    val bgColor = when { isTaken -> SuccessGreen.copy(alpha = 0.15f); isClickable -> NavyCardLight; else -> NavyCard }
    val borderColor = when { isTaken -> SuccessGreen; isClickable -> GameSpojnice.copy(alpha = 0.7f); else -> LineSoft }
    val textColor = when { isTaken -> SuccessGreen; isClickable -> White; else -> MediumGray }
    val alpha = if (!isTaken && !isClickable && isMyTurn) 0.5f else 1f

    Row(
        modifier = Modifier.fillMaxWidth().height(44.dp).alpha(alpha)
            .clip(RoundedCornerShape(10.dp)).background(bgColor)
            .border(if (isTaken || isClickable) 2.dp else 1.dp, borderColor, RoundedCornerShape(10.dp))
            .then(if (isClickable) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(text, color = textColor, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, modifier = Modifier.weight(1f))
        if (isTaken) Icon(Icons.Default.CheckCircle, null, tint = SuccessGreen, modifier = Modifier.size(14.dp))
        else if (isClickable) Icon(Icons.Default.RadioButtonUnchecked, null, tint = GameSpojnice.copy(alpha = 0.6f), modifier = Modifier.size(14.dp))
    }
}
