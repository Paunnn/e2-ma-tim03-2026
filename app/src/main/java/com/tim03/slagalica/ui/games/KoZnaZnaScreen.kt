package com.tim03.slagalica.ui.games

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tim03.slagalica.data.model.KoZnaZnaQuestion
import com.tim03.slagalica.ui.theme.*
import com.tim03.slagalica.viewmodel.KoZnaZnaViewModel
import com.tim03.slagalica.viewmodel.KoZnaZnaViewModelFactory
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KoZnaZnaScreen(
    onExitClick: () -> Unit,
    isPartijaMode: Boolean = false,
    onPartijaGameComplete: (Int, Int) -> Unit = { _, _ -> },
    myScoreOffset: Int = 0,
    oppScoreOffset: Int = 0,
    sessionId: String = "",
    isPlayer1: Boolean = true,
    gameIdx: Int = -1,
    izazovMode: Boolean = false,
    oppName: String = "Protivnik"
) {
    val viewModel: KoZnaZnaViewModel = viewModel(
        factory = KoZnaZnaViewModelFactory(sessionId, isPlayer1, gameIdx, izazovMode)
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val username by viewModel.username.collectAsStateWithLifecycle()

    val currentQuestion = state.questions.getOrNull(state.currentQuestionIndex)

    LaunchedEffect(state.gameOver) {
        if (state.gameOver && isPartijaMode) {
            delay(3000L)
            onPartijaGameComplete(state.myScore, state.opponentScore)
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Navy)) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                GameHud(
                    gameName = "Ko zna zna",
                    gameColor = GameKoZnaZna,
                    gameIcon = "?",
                    round = "${state.currentQuestionIndex + 1}/${state.questions.size.coerceAtLeast(1)} pit.",
                    timeLeft = state.timeLeft,
                    totalTime = 5,
                    myScore = state.myScore + myScoreOffset,
                    oppScore = state.opponentScore + oppScoreOffset,
                    myName = username,
                    oppName = oppName,
                    showOpponent = !izazovMode,
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
                                CircularProgressIndicator(color = GameKoZnaZna)
                                Text("Učitavanje pitanja...", color = LightGray)
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
                                Text("Greška pri učitavanju pitanja", color = ErrorRed, fontWeight = FontWeight.Bold)
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
                            modifier = Modifier
                                .weight(1f)
                                .verticalScroll(rememberScrollState())
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            KoZnaZnaQuestionProgressRow(
                                total = state.questions.size,
                                outcomes = state.questionOutcomes,
                                current = state.currentQuestionIndex
                            )

                            if (currentQuestion != null) {
                                Text(
                                    "PITANJE ${state.currentQuestionIndex + 1}",
                                    color = MediumGray, fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold, letterSpacing = 1.5.sp,
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    currentQuestion.question,
                                    color = White, fontWeight = FontWeight.Bold,
                                    fontSize = 20.sp, lineHeight = 26.sp,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp)
                                )

                                currentQuestion.answers.forEachIndexed { index, answer ->
                                    val btnState = if (state.revealPhase) {
                                        when {
                                            index == currentQuestion.correctIndex -> KzzAnswerState.CORRECT
                                            state.playerAnswerIndex == index -> KzzAnswerState.PLAYER_WRONG
                                            state.opponentAnswerIndex == index -> KzzAnswerState.OPP_WRONG
                                            else -> KzzAnswerState.IDLE
                                        }
                                    } else KzzAnswerState.IDLE

                                    KoZnaZnaAnswerButton(
                                        label = ('A' + index).toString(),
                                        text = answer,
                                        state = btnState,
                                        playerBadge = state.revealPhase && state.playerAnswerIndex == index,
                                        opponentBadge = state.revealPhase && state.opponentAnswerIndex == index,
                                        enabled = !state.revealPhase && state.playerAnswerIndex == null && !state.gameOver,
                                        onClick = { viewModel.selectAnswer(index) }
                                    )
                                }

                                AnimatedVisibility(visible = state.playerAnswerIndex != null && !state.revealPhase && !izazovMode) {
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = CardDefaults.cardColors(containerColor = NavyCard),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, PrimaryBlueBright.copy(alpha = 0.4f))
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(14.dp),
                                                strokeWidth = 2.dp,
                                                color = PrimaryBlueBright
                                            )
                                            Text(
                                                "Čekamo protivnikov odgovor...",
                                                color = PrimaryBlueBright,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                }

                                AnimatedVisibility(visible = state.revealPhase) {
                                    KoZnaZnaRevealPanel(
                                        question = currentQuestion,
                                        playerAnswerIndex = state.playerAnswerIndex,
                                        opponentAnswerIndex = state.opponentAnswerIndex,
                                        playerAnswerTimeMs = state.playerAnswerTimeMs,
                                        opponentAnswerTimeMs = state.opponentAnswerTimeMs,
                                        oppName = oppName,
                                        soloMode = izazovMode
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

private enum class KzzAnswerState { IDLE, CORRECT, PLAYER_WRONG, OPP_WRONG }

@Composable
private fun KoZnaZnaQuestionProgressRow(total: Int, outcomes: Map<Int, Int>, current: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(total) { index ->
            val outcome = outcomes[index]
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(
                        when {
                            index == current -> PrimaryBlueBright
                            outcome == 1 -> SuccessGreen
                            outcome == -1 -> ErrorRed
                            outcome == 0 -> MediumGray
                            else -> DarkGray
                        }
                    )
            )
        }
    }
}

@Composable
private fun KoZnaZnaAnswerButton(
    label: String,
    text: String,
    state: KzzAnswerState,
    playerBadge: Boolean = false,
    opponentBadge: Boolean = false,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val bgColor = when (state) {
        KzzAnswerState.CORRECT -> SuccessGreen
        KzzAnswerState.PLAYER_WRONG -> ErrorRed
        KzzAnswerState.OPP_WRONG -> Color(0xFFB45309)
        KzzAnswerState.IDLE -> NavyCard
    }
    val borderColor = when (state) {
        KzzAnswerState.CORRECT -> SuccessGreen
        KzzAnswerState.PLAYER_WRONG -> ErrorRed
        KzzAnswerState.OPP_WRONG -> Color(0xFFF59E0B)
        KzzAnswerState.IDLE -> LineColor
    }
    val labelBg = when (state) {
        KzzAnswerState.CORRECT -> Navy
        KzzAnswerState.PLAYER_WRONG -> Color(0xFF7A0020)
        KzzAnswerState.OPP_WRONG -> Color(0xFF78350F)
        KzzAnswerState.IDLE -> NavyCardLight
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        border = androidx.compose.foundation.BorderStroke(
            if (state != KzzAnswerState.IDLE) 2.dp else 1.5.dp, borderColor
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier.size(26.dp).clip(RoundedCornerShape(6.dp)).background(labelBg),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    color = if (state == KzzAnswerState.CORRECT) SuccessGreen else LightGray,
                    fontWeight = FontWeight.ExtraBold, fontSize = 12.sp
                )
            }
            Text(
                text,
                color = if (state == KzzAnswerState.CORRECT) Navy else White,
                fontWeight = FontWeight.Bold, fontSize = 15.sp,
                modifier = Modifier.weight(1f)
            )
            if (playerBadge || opponentBadge) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (playerBadge) {
                        Box(
                            modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(PrimaryBlueBright).padding(horizontal = 5.dp, vertical = 2.dp)
                        ) { Text("TI", color = White, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold) }
                    }
                    if (opponentBadge) {
                        Box(
                            modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(Accent2).padding(horizontal = 5.dp, vertical = 2.dp)
                        ) { Text("P", color = White, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold) }
                    }
                }
            }
        }
    }
}

@Composable
private fun KoZnaZnaRevealPanel(
    question: KoZnaZnaQuestion,
    playerAnswerIndex: Int?,
    opponentAnswerIndex: Int?,
    playerAnswerTimeMs: Long?,
    opponentAnswerTimeMs: Long?,
    oppName: String = "Protivnik",
    // Izazov: no opponent at all - only the player's own outcome is shown.
    soloMode: Boolean = false
) {
    val pCorrect = playerAnswerIndex != null && playerAnswerIndex == question.correctIndex
    val oCorrect = opponentAnswerIndex != null && opponentAnswerIndex >= 0 && opponentAnswerIndex == question.correctIndex
    val pTime = playerAnswerTimeMs ?: Long.MAX_VALUE
    val oTime = opponentAnswerTimeMs ?: Long.MAX_VALUE

    val outcomeText = when {
        soloMode -> when {
            playerAnswerIndex == null -> "Niste odgovorili, nema promena"
            pCorrect -> "Tačno! +10"
            else -> "Netačno (-5)"
        }
        playerAnswerIndex == null && (opponentAnswerIndex == null || opponentAnswerIndex < 0) ->
            "Niko nije odgovorio, nema promena"
        playerAnswerIndex == null ->
            if (oCorrect) "$oppName tačno (+10 protivnik)" else "$oppName netačno (-5 protivnik)"
        opponentAnswerIndex == null || opponentAnswerIndex < 0 ->
            if (pCorrect) "Tačno! Protivnik nije odgovorio (+10)" else "Netačno (-5)"
        pCorrect && oCorrect ->
            if (pTime <= oTime) "Oba tačno, Vi brži! +10 tebi" else "Oba tačno, $oppName brži! +10 protivnik"
        pCorrect -> "Tačno! Protivnik netačno, +10 tebi, -5 protivnik"
        oCorrect -> "Netačno, protivnik tačno, -5 tebi, +10 protivnik"
        else -> "Oba netačna, -5 tebi, -5 protivnik"
    }

    val outcomeColor = when {
        playerAnswerIndex != null && pCorrect &&
            (opponentAnswerIndex == null || opponentAnswerIndex < 0 || !oCorrect || pTime <= oTime) -> SuccessGreen
        playerAnswerIndex != null && !pCorrect -> ErrorRed
        else -> LightGray
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = NavyCard),
        border = androidx.compose.foundation.BorderStroke(1.dp, outcomeColor.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            KoZnaZnaRevealRow(
                label = "Ti",
                answerText = playerAnswerIndex?.let { question.answers.getOrNull(it) } ?: "Nije odgovorio",
                timeMs = playerAnswerTimeMs,
                isCorrect = playerAnswerIndex != null && pCorrect,
                isWrong = playerAnswerIndex != null && !pCorrect,
                labelColor = PrimaryBlueBright
            )
            if (!soloMode) {
                HorizontalDivider(color = LineSoft)
                KoZnaZnaRevealRow(
                    label = oppName.take(10),
                    answerText = opponentAnswerIndex?.let { if (it >= 0) question.answers.getOrNull(it) else null }
                        ?: "Nije odgovorio",
                    timeMs = opponentAnswerTimeMs,
                    isCorrect = oCorrect,
                    isWrong = opponentAnswerIndex != null && opponentAnswerIndex >= 0 && !oCorrect,
                    labelColor = Accent2
                )
            }
            HorizontalDivider(color = LineSoft)
            Text(
                outcomeText,
                color = outcomeColor,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun KoZnaZnaRevealRow(
    label: String,
    answerText: String,
    timeMs: Long?,
    isCorrect: Boolean,
    isWrong: Boolean,
    labelColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(label, color = labelColor, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp, modifier = Modifier.width(64.dp))
        Text(
            answerText,
            color = when { isCorrect -> SuccessGreen; isWrong -> ErrorRed; else -> MediumGray },
            fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.weight(1f)
        )
        if (timeMs != null) {
            Text("${timeMs / 1000}.${(timeMs % 1000) / 100}s", color = MediumGray, fontSize = 11.sp)
        }
        if (isCorrect) Icon(Icons.Default.CheckCircle, null, tint = SuccessGreen, modifier = Modifier.size(16.dp))
        else if (isWrong) Icon(Icons.Default.Cancel, null, tint = ErrorRed, modifier = Modifier.size(16.dp))
    }
}
