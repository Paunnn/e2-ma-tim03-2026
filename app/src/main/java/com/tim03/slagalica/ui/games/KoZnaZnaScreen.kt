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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KoZnaZnaScreen(
    onExitClick: () -> Unit,
    isPartijaMode: Boolean = false,
    onPartijaGameComplete: (Int, Int) -> Unit = { _, _ -> },
    myScoreOffset: Int = 0,
    oppScoreOffset: Int = 0,
    viewModel: KoZnaZnaViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val username by viewModel.username.collectAsStateWithLifecycle()
    val questions = state.questions

    var currentQuestionIndex by remember { mutableStateOf(0) }
    var myScore by remember { mutableStateOf(0) }
    var opponentScore by remember { mutableStateOf(0) }
    var questionTimeLeft by remember { mutableStateOf(5) }
    var gameOver by remember { mutableStateOf(false) }
    var correctCount by remember { mutableStateOf(0) }
    var incorrectCount by remember { mutableStateOf(0) }
    var resultSaved by remember { mutableStateOf(false) }

    // Per-question state
    var playerAnswerIndex by remember { mutableStateOf<Int?>(null) }
    var playerAnswerTimeMs by remember { mutableStateOf<Long?>(null) }
    var opponentAnswerIndex by remember { mutableStateOf<Int?>(null) }
    var opponentAnswerTimeMs by remember { mutableStateOf<Long?>(null) }
    var revealPhase by remember { mutableStateOf(false) }
    var questionStartTime by remember { mutableStateOf(0L) }
    var questionOutcomes by remember { mutableStateOf(mapOf<Int, Int>()) } // 1=player+10, -1=player-5, 0=no player score

    // Per-question flow — resets on each new question
    LaunchedEffect(currentQuestionIndex, state.isLoading) {
        if (state.isLoading || questions.isEmpty() || gameOver) return@LaunchedEffect

        playerAnswerIndex = null
        playerAnswerTimeMs = null
        opponentAnswerIndex = null
        opponentAnswerTimeMs = null
        revealPhase = false
        questionTimeLeft = 5
        questionStartTime = System.currentTimeMillis()

        // Opponent picks a random answer at a random time (0.8–4.8s)
        launch {
            val oppDelay = (800L..4800L).random()
            delay(oppDelay)
            if (!revealPhase) {
                opponentAnswerIndex = (0..3).random()
                opponentAnswerTimeMs = oppDelay
                if (playerAnswerIndex != null) revealPhase = true
            }
        }

        // 5s per-question countdown
        for (tick in 5 downTo 1) {
            if (revealPhase) break
            delay(1000L)
            if (!revealPhase) questionTimeLeft = tick - 1
        }
        if (!revealPhase) revealPhase = true

        // Reveal for 2.5s
        delay(2500L)

        val q = questions.getOrNull(currentQuestionIndex) ?: return@LaunchedEffect
        val pAnswered = playerAnswerIndex != null
        val oAnswered = opponentAnswerIndex != null
        val pCorrect = pAnswered && playerAnswerIndex == q.correctIndex
        val oCorrect = oAnswered && opponentAnswerIndex == q.correctIndex
        val pTime = playerAnswerTimeMs ?: Long.MAX_VALUE
        val oTime = opponentAnswerTimeMs ?: Long.MAX_VALUE

        val playerGetsPoints = pCorrect && (!oCorrect || pTime <= oTime)
        when {
            playerGetsPoints -> { myScore += 10; correctCount++ }
            pCorrect -> correctCount++
            pAnswered -> { myScore -= 5; incorrectCount++ }
        }
        val opponentGetsPoints = oCorrect && (!pCorrect || oTime < pTime)
        when {
            opponentGetsPoints -> opponentScore += 10
            oAnswered && !oCorrect -> opponentScore -= 5
        }

        val outcome = when {
            playerGetsPoints -> 1
            pAnswered && !pCorrect -> -1
            else -> 0
        }
        questionOutcomes = questionOutcomes + (currentQuestionIndex to outcome)

        if (currentQuestionIndex < questions.size - 1) {
            currentQuestionIndex++
        } else {
            if (!resultSaved) {
                resultSaved = true
                viewModel.saveResult(correctCount, incorrectCount, won = myScore > opponentScore)
            }
            gameOver = true
        }
    }

    val currentQuestion = questions.getOrNull(currentQuestionIndex)

    Box(modifier = Modifier.fillMaxSize().background(Navy)) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                GameHud(
                    gameName = "Ko zna zna",
                    gameColor = GameKoZnaZna,
                    gameIcon = "?",
                    round = "${currentQuestionIndex + 1}/${questions.size.coerceAtLeast(1)} pit.",
                    timeLeft = questionTimeLeft,
                    totalTime = 5,
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
                                CircularProgressIndicator(color = GameKoZnaZna)
                                Text("Učitavanje pitanja...", color = LightGray)
                            }
                        }
                    }
                    state.error != null -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Icon(Icons.Default.ErrorOutline, null, tint = ErrorRed, modifier = Modifier.size(48.dp))
                                Text("Greška pri učitavanju pitanja", color = ErrorRed, fontWeight = FontWeight.Bold)
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
                            modifier = Modifier
                                .weight(1f)
                                .verticalScroll(rememberScrollState())
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            QuestionProgressRow(
                                total = questions.size,
                                outcomes = questionOutcomes,
                                current = currentQuestionIndex
                            )

                            if (currentQuestion != null) {
                                Text(
                                    "PITANJE ${currentQuestionIndex + 1}",
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
                                    val btnState = if (revealPhase) {
                                        when {
                                            index == currentQuestion.correctIndex -> AnswerState.CORRECT
                                            playerAnswerIndex == index -> AnswerState.PLAYER_WRONG
                                            opponentAnswerIndex == index -> AnswerState.OPP_WRONG
                                            else -> AnswerState.IDLE
                                        }
                                    } else {
                                        AnswerState.IDLE
                                    }
                                    AnswerButton(
                                        label = ('A' + index).toString(),
                                        text = answer,
                                        state = btnState,
                                        playerBadge = revealPhase && playerAnswerIndex == index,
                                        opponentBadge = revealPhase && opponentAnswerIndex == index,
                                        enabled = !revealPhase && playerAnswerIndex == null && !gameOver,
                                        onClick = {
                                            if (!revealPhase && playerAnswerIndex == null && !gameOver) {
                                                playerAnswerIndex = index
                                                playerAnswerTimeMs = System.currentTimeMillis() - questionStartTime
                                                if (opponentAnswerIndex != null) revealPhase = true
                                            }
                                        }
                                    )
                                }

                                // Waiting for opponent
                                AnimatedVisibility(visible = playerAnswerIndex != null && !revealPhase) {
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

                                // Reveal panel
                                AnimatedVisibility(visible = revealPhase) {
                                    RevealPanel(
                                        question = currentQuestion,
                                        playerAnswerIndex = playerAnswerIndex,
                                        opponentAnswerIndex = opponentAnswerIndex,
                                        playerAnswerTimeMs = playerAnswerTimeMs,
                                        opponentAnswerTimeMs = opponentAnswerTimeMs
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

private enum class AnswerState { IDLE, CORRECT, PLAYER_WRONG, OPP_WRONG }

@Composable
private fun QuestionProgressRow(total: Int, outcomes: Map<Int, Int>, current: Int) {
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
private fun AnswerButton(
    label: String,
    text: String,
    state: AnswerState,
    playerBadge: Boolean = false,
    opponentBadge: Boolean = false,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val bgColor = when (state) {
        AnswerState.CORRECT -> SuccessGreen
        AnswerState.PLAYER_WRONG -> ErrorRed
        AnswerState.OPP_WRONG -> Color(0xFFB45309)
        AnswerState.IDLE -> NavyCard
    }
    val borderColor = when (state) {
        AnswerState.CORRECT -> SuccessGreen
        AnswerState.PLAYER_WRONG -> ErrorRed
        AnswerState.OPP_WRONG -> Color(0xFFF59E0B)
        AnswerState.IDLE -> LineColor
    }
    val labelBg = when (state) {
        AnswerState.CORRECT -> Navy
        AnswerState.PLAYER_WRONG -> Color(0xFF7A0020)
        AnswerState.OPP_WRONG -> Color(0xFF78350F)
        AnswerState.IDLE -> NavyCardLight
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        border = androidx.compose.foundation.BorderStroke(if (state != AnswerState.IDLE) 2.dp else 1.5.dp, borderColor)
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
                    color = if (state == AnswerState.CORRECT) SuccessGreen else LightGray,
                    fontWeight = FontWeight.ExtraBold, fontSize = 12.sp
                )
            }
            Text(
                text,
                color = if (state == AnswerState.CORRECT) Navy else White,
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
private fun RevealPanel(
    question: KoZnaZnaQuestion,
    playerAnswerIndex: Int?,
    opponentAnswerIndex: Int?,
    playerAnswerTimeMs: Long?,
    opponentAnswerTimeMs: Long?
) {
    val pCorrect = playerAnswerIndex == question.correctIndex
    val oCorrect = opponentAnswerIndex == question.correctIndex
    val pTime = playerAnswerTimeMs ?: Long.MAX_VALUE
    val oTime = opponentAnswerTimeMs ?: Long.MAX_VALUE

    val outcomeText = when {
        playerAnswerIndex == null && opponentAnswerIndex == null ->
            "Niko nije odgovorio, nema promena"
        playerAnswerIndex == null ->
            if (oCorrect) "Protivnik tačno (+10 protivnik)" else "Protivnik netačno (-5 protivnik)"
        opponentAnswerIndex == null ->
            if (pCorrect) "Tačno! Nisi imao protivnika (+10)" else "Netačno (-5)"
        pCorrect && oCorrect ->
            if (pTime <= oTime) "Oba tačno, Vi brži! +10 tebi" else "Oba tačno, Protivnik brži! +10 protivnik"
        pCorrect -> "Tačno! Protivnik netačno, +10 tebi, -5 protivnik"
        oCorrect -> "Netačno, protivnik tačno, -5 tebi, +10 protivnik"
        else -> "Oba netačna, -5 tebi, -5 protivnik"
    }

    val outcomeColor = when {
        playerAnswerIndex != null && pCorrect &&
            (opponentAnswerIndex == null || !oCorrect || pTime <= oTime) -> SuccessGreen
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
            RevealRow(
                label = "Ti",
                answerText = playerAnswerIndex?.let { question.answers.getOrNull(it) } ?: "Nije odgovorio",
                timeMs = playerAnswerTimeMs,
                isCorrect = playerAnswerIndex != null && pCorrect,
                isWrong = playerAnswerIndex != null && !pCorrect,
                labelColor = PrimaryBlueBright
            )
            HorizontalDivider(color = LineSoft)
            RevealRow(
                label = "Protivnik",
                answerText = opponentAnswerIndex?.let { question.answers.getOrNull(it) } ?: "Nije odgovorio",
                timeMs = opponentAnswerTimeMs,
                isCorrect = opponentAnswerIndex != null && oCorrect,
                isWrong = opponentAnswerIndex != null && !oCorrect,
                labelColor = Accent2
            )
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
private fun RevealRow(
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
        Text(
            label,
            color = labelColor,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 11.sp,
            modifier = Modifier.width(64.dp)
        )
        Text(
            answerText,
            color = when {
                isCorrect -> SuccessGreen
                isWrong -> ErrorRed
                else -> MediumGray
            },
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            modifier = Modifier.weight(1f)
        )
        if (timeMs != null) {
            Text(
                "${timeMs / 1000}.${(timeMs % 1000) / 100}s",
                color = MediumGray,
                fontSize = 11.sp
            )
        }
        if (isCorrect) {
            Icon(Icons.Default.CheckCircle, null, tint = SuccessGreen, modifier = Modifier.size(16.dp))
        } else if (isWrong) {
            Icon(Icons.Default.Cancel, null, tint = ErrorRed, modifier = Modifier.size(16.dp))
        }
    }
}
