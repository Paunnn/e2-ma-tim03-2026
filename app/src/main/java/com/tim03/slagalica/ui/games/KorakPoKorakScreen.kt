package com.tim03.slagalica.ui.games

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tim03.slagalica.ui.theme.*
import com.tim03.slagalica.viewmodel.KorakPoKorakPhase
import com.tim03.slagalica.viewmodel.KorakPoKorakViewModel
import com.tim03.slagalica.viewmodel.KorakPoKorakViewModelFactory
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KorakPoKorakScreen(
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
    val viewModel: KorakPoKorakViewModel = viewModel(
        factory = KorakPoKorakViewModelFactory(sessionId, isPlayer1, gameIdx, izazovMode)
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val username by viewModel.username.collectAsStateWithLifecycle()
    var answer by remember { mutableStateOf("") }

    val isMainRound = state.phase == KorakPoKorakPhase.MY_TURN ||
        state.phase == KorakPoKorakPhase.WAITING_OPPONENT
    val totalTime = if (isMainRound) 70 else 10
    val timerColor = if (isMainRound) when {
        state.timeLeft > 40 -> TimerGreen
        state.timeLeft > 20 -> TimerYellow
        else -> TimerRed
    } else when {
        state.timeLeft > 6 -> TimerGreen
        state.timeLeft > 3 -> TimerYellow
        else -> TimerRed
    }

    Box(modifier = Modifier.fillMaxSize().background(Navy)) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                GameHud(
                    gameName = "Korak po korak",
                    gameColor = GameKorak,
                    gameIcon = "↦",
                    round = if (izazovMode) "1/1 runda" else "${state.currentRound}/2 runda",
                    timeLeft = state.timeLeft,
                    totalTime = totalTime,
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
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = PrimaryBlueBright)
                                Spacer(modifier = Modifier.height(12.dp))
                                Text("Učitavanje pitanja...", color = LightGray)
                            }
                        }
                    }
                    state.phase == KorakPoKorakPhase.GAME_OVER && !isPartijaMode -> {
                        GameOverContent(myScore = state.myScore, opponentScore = state.opponentScore, onFinish = onExitClick)
                    }
                    else -> {
                        LaunchedEffect(state.phase == KorakPoKorakPhase.GAME_OVER) {
                            if (state.phase == KorakPoKorakPhase.GAME_OVER && isPartijaMode) {
                                delay(3000L)
                                onPartijaGameComplete(state.myScore, state.opponentScore)
                            }
                        }
                        // Result message
                        state.lastResultMessage?.let { msg ->
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = CardDefaults.cardColors(containerColor = Gold.copy(alpha = 0.1f)),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Gold.copy(alpha = 0.3f))
                            ) {
                                Text(msg, color = Gold, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), textAlign = TextAlign.Center)
                            }
                        }

                        // Steps list
                        val steps = state.question?.steps ?: emptyList()
                        Column(
                            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp)
                        ) {
                            // Target hint card
                            val isGameOver = state.phase == KorakPoKorakPhase.GAME_OVER || state.showAnswer
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isGameOver) Gold.copy(alpha = 0.1f) else NavyCard
                                ),
                                border = if (isGameOver) androidx.compose.foundation.BorderStroke(1.5.dp, Gold.copy(alpha = 0.5f)) else null
                            ) {
                                Row(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        if (isGameOver) Icons.Default.CheckCircle else Icons.Default.HelpOutline,
                                        null,
                                        tint = if (isGameOver) Gold else MediumGray,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Traženi pojam", color = if (isGameOver) Gold.copy(alpha = 0.8f) else MediumGray, style = MaterialTheme.typography.bodySmall)
                                    Spacer(modifier = Modifier.weight(1f))
                                    Text(
                                        if (isGameOver) state.question?.answer?.uppercase() ?: "?" else "???",
                                        color = if (isGameOver) Gold else LightGray,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Koraci", color = LightGray, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(8.dp))

                            steps.forEachIndexed { index, clue ->
                                val isRevealed = index < state.revealedSteps
                                val isCurrent = index == state.revealedSteps - 1 && isRevealed
                                val stepPoints = (20 - index * 2).coerceAtLeast(8)
                                AnimatedVisibility(visible = true, enter = fadeIn() + slideInVertically()) {
                                    StepCard(
                                        stepNumber = index + 1,
                                        clueText = clue,
                                        isRevealed = isRevealed,
                                        isCurrent = isCurrent,
                                        potentialPoints = stepPoints
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                            }

                        }

                        // Phase-specific bottom bar (replaces always-visible disabled input)
                        when (state.phase) {
                            KorakPoKorakPhase.MY_TURN, KorakPoKorakPhase.MY_BONUS -> {
                                val isBonusRound = state.phase == KorakPoKorakPhase.MY_BONUS
                                Surface(
                                    color = if (isBonusRound) GameKorak.copy(alpha = 0.12f) else NavyLight,
                                    shadowElevation = 8.dp
                                ) {
                                    Column {
                                        if (isBonusRound) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Icon(Icons.Default.EmojiEvents, null, tint = GameKorak, modifier = Modifier.size(14.dp))
                                                Text(
                                                    "BONUS · Pogodi protivnikovu reč za +5 · ${state.timeLeft}s",
                                                    color = GameKorak, fontSize = 11.sp, fontWeight = FontWeight.SemiBold
                                                )
                                            }
                                        }
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(12.dp).navigationBarsPadding(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            val accentColor = if (isBonusRound) GameKorak else PrimaryBlueBright
                                            OutlinedTextField(
                                                value = answer,
                                                onValueChange = { answer = it },
                                                placeholder = { Text("Unesite odgovor...", color = MediumGray) },
                                                singleLine = true, modifier = Modifier.weight(1f),
                                                shape = RoundedCornerShape(12.dp),
                                                colors = OutlinedTextFieldDefaults.colors(
                                                    focusedBorderColor = accentColor, unfocusedBorderColor = MediumGray,
                                                    cursorColor = accentColor, focusedTextColor = White, unfocusedTextColor = White
                                                )
                                            )
                                            Button(
                                                onClick = {
                                                    if (isBonusRound) { viewModel.submitMyBonusAnswer(answer); answer = "" }
                                                    else { viewModel.submitAnswer(answer); answer = "" }
                                                },
                                                enabled = answer.isNotBlank(),
                                                shape = RoundedCornerShape(12.dp),
                                                colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                                                modifier = Modifier.height(56.dp)
                                            ) {
                                                Text("POGODI", fontWeight = FontWeight.Bold,
                                                    color = if (isBonusRound) Navy else White)
                                            }
                                        }
                                    }
                                }
                            }
                            KorakPoKorakPhase.OPPONENT_BONUS -> {
                                Surface(color = NavyLight, shadowElevation = 8.dp) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(16.dp).navigationBarsPadding(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = WarningOrange, strokeWidth = 2.5.dp)
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("Protivnik pokušava da pogodi...", color = WarningOrange, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                            Text("${state.timeLeft}s preostalo", color = MediumGray, fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                            KorakPoKorakPhase.WAITING_OPPONENT -> {
                                Surface(color = NavyLight, shadowElevation = 8.dp) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(16.dp).navigationBarsPadding(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = PrimaryBlueBright, strokeWidth = 2.5.dp)
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("Protivnik igra svoju rundu...", color = PrimaryBlueLight, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                            Text("${state.timeLeft}s preostalo", color = MediumGray, fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                            else -> {}
                        }
                    }
                }
            }
        }
    }
}

private fun phaseLabel(phase: KorakPoKorakPhase): String = when (phase) {
    KorakPoKorakPhase.MY_TURN -> "  Tvoj red  "
    KorakPoKorakPhase.OPPONENT_BONUS -> "  Protivnik (bonus)  "
    KorakPoKorakPhase.WAITING_OPPONENT -> "  Protivnik igra  "
    KorakPoKorakPhase.MY_BONUS -> "  Tvoj bonus  "
    KorakPoKorakPhase.GAME_OVER -> "  Kraj  "
}

private fun phaseColor(phase: KorakPoKorakPhase): Color = when (phase) {
    KorakPoKorakPhase.MY_TURN, KorakPoKorakPhase.MY_BONUS -> PrimaryBlueBright
    KorakPoKorakPhase.OPPONENT_BONUS, KorakPoKorakPhase.WAITING_OPPONENT -> WarningOrange
    KorakPoKorakPhase.GAME_OVER -> Gold
}

@Composable
fun PlayerChip(name: String, score: Int, isActive: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!isActive) {
            Column(horizontalAlignment = Alignment.End) {
                Text(name, color = LightGray, style = MaterialTheme.typography.labelSmall)
                Text("$score bod.", color = LightGray, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            }
        }
        Box(
            modifier = Modifier.size(36.dp).clip(CircleShape)
                .background(if (isActive) PrimaryBlue else DarkGray)
                .then(if (isActive) Modifier.border(2.dp, Gold, CircleShape) else Modifier),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Person, null, tint = White, modifier = Modifier.size(20.dp))
        }
        if (isActive) {
            Column(horizontalAlignment = Alignment.Start) {
                Text(name, color = PrimaryBlueLight, style = MaterialTheme.typography.labelSmall)
                Text("$score bod.", color = White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun StepCard(stepNumber: Int, clueText: String, isRevealed: Boolean, isCurrent: Boolean, potentialPoints: Int = 20 - (stepNumber - 1) * 2) {
    // Current step: solid GameKorak bg + Navy text
    // Revealed (not current): NavyCard bg + white text
    // Unrevealed: NavyLight, opacity 0.55, dashed-style border
    val bgColor = when {
        isCurrent  -> GameKorak
        isRevealed -> NavyCard
        else       -> NavyLight
    }
    val borderColor = when {
        isCurrent  -> GameKorak
        isRevealed -> GameKorak.copy(alpha = 0.3f)
        else       -> DarkGray
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (!isRevealed) Modifier.alpha(0.55f) else Modifier)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth().border(1.5.dp, borderColor, RoundedCornerShape(12.dp)),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = bgColor)
        ) {
            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(28.dp).clip(CircleShape)
                        .background(if (isCurrent) Navy else if (isRevealed) GameKorak.copy(alpha = 0.2f) else MediumGray),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "$stepNumber",
                        color = if (isCurrent) GameKorak else if (isRevealed) GameKorak else White,
                        fontWeight = FontWeight.ExtraBold, fontSize = 12.sp
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                if (isRevealed) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            clueText,
                            color = if (isCurrent) Navy else White,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isCurrent) FontWeight.ExtraBold else FontWeight.SemiBold
                        )
                    }
                } else {
                    Text(
                        "Korak $stepNumber",
                        color = MediumGray, style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                }
                // "+N bodova" badge
                if (isRevealed) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isCurrent) Navy.copy(alpha = 0.25f) else NavyCardLight)
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                    ) {
                        Text(
                            "+$potentialPoints",
                            color = if (isCurrent) Navy else GameKorak,
                            fontSize = 11.sp, fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
        }
    }
}
