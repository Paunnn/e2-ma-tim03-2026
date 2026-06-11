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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KorakPoKorakScreen(
    onExitClick: () -> Unit,
    viewModel: KorakPoKorakViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var answer by remember { mutableStateOf("") }

    val timerColor = when {
        state.timeLeft > 30 -> TimerGreen
        state.timeLeft > 10 -> TimerYellow
        else -> TimerRed
    }
    val totalTime = if (state.phase == KorakPoKorakPhase.OPPONENT_BONUS || state.phase == KorakPoKorakPhase.MY_BONUS) 10 else 70

    Box(modifier = Modifier.fillMaxSize().background(Navy)) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text("KORAK PO KORAK", fontWeight = FontWeight.ExtraBold, color = White, letterSpacing = 1.sp, fontSize = 16.sp) },
                    navigationIcon = {
                        IconButton(onClick = onExitClick) { Icon(Icons.Default.Close, null, tint = LightGray) }
                    },
                    actions = {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 12.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Default.Token, null, tint = GoldLight, modifier = Modifier.size(16.dp))
                            Text("5", color = White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(Icons.Default.Star, null, tint = Gold, modifier = Modifier.size(16.dp))
                            Text("0", color = White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = NavyLight)
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
                    state.phase == KorakPoKorakPhase.GAME_OVER -> {
                        GameOverContent(myScore = state.myScore, opponentScore = state.opponentScore, onFinish = onExitClick)
                    }
                    else -> {
                        // Timer bar
                        Column(modifier = Modifier.background(NavyLight)) {
                            LinearProgressIndicator(
                                progress = state.timeLeft.toFloat() / totalTime,
                                modifier = Modifier.fillMaxWidth().height(6.dp),
                                color = timerColor, trackColor = DarkGray
                            )
                            Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), contentAlignment = Alignment.Center) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(Icons.Default.Timer, null, tint = timerColor, modifier = Modifier.size(16.dp))
                                    Text("${state.timeLeft} s", color = timerColor, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                }
                            }
                        }

                        // Round + phase indicator
                        Row(
                            modifier = Modifier.fillMaxWidth().background(NavyCard).padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Runda ${state.currentRound} / 2", color = Gold, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                            Surface(shape = RoundedCornerShape(8.dp), color = phaseColor(state.phase).copy(alpha = 0.25f)) {
                                Text(
                                    phaseLabel(state.phase),
                                    color = phaseColor(state.phase),
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        // Scores
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .background(Brush.horizontalGradient(listOf(PrimaryBlue.copy(alpha = 0.3f), Navy, WarningOrange.copy(alpha = 0.15f))))
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
                        ) {
                            PlayerChip(name = "Ti", score = state.myScore, isActive = state.phase == KorakPoKorakPhase.MY_TURN || state.phase == KorakPoKorakPhase.MY_BONUS)
                            Text("VS", color = MediumGray, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            PlayerChip(name = "Protivnik", score = state.opponentScore, isActive = state.phase == KorakPoKorakPhase.WAITING_OPPONENT || state.phase == KorakPoKorakPhase.OPPONENT_BONUS)
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
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = NavyCard)
                            ) {
                                Row(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.HelpOutline, null, tint = MediumGray, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Traženi pojam", color = MediumGray, style = MaterialTheme.typography.bodySmall)
                                    Spacer(modifier = Modifier.weight(1f))
                                    Text("???", color = LightGray, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Koraci", color = LightGray, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(8.dp))

                            steps.forEachIndexed { index, clue ->
                                val isRevealed = index < state.revealedSteps
                                val isCurrent = index == state.revealedSteps - 1 && isRevealed
                                AnimatedVisibility(visible = true, enter = fadeIn() + slideInVertically()) {
                                    StepCard(stepNumber = index + 1, clueText = clue, isRevealed = isRevealed, isCurrent = isCurrent)
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                            }

                            if (state.phase == KorakPoKorakPhase.OPPONENT_BONUS) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = WarningOrange.copy(alpha = 0.1f)),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, WarningOrange.copy(alpha = 0.4f))
                                ) {
                                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Info, null, tint = WarningOrange, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Protivnik ima 10s da osvoji 5 bodova.", color = WarningOrange, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }

                            if (state.phase == KorakPoKorakPhase.WAITING_OPPONENT) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = PrimaryBlue.copy(alpha = 0.1f))
                                ) {
                                    Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = PrimaryBlueLight, strokeWidth = 2.dp)
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text("Protivnik igra svoju rundu...", color = PrimaryBlueLight, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }

                            if (state.phase == KorakPoKorakPhase.MY_BONUS) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = SuccessGreen.copy(alpha = 0.1f)),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, SuccessGreen.copy(alpha = 0.4f))
                                ) {
                                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.EmojiEvents, null, tint = SuccessGreen, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Tvoja šansa! Pogodi za 5 bodova.", color = SuccessGreen, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }

                        // Answer input
                        val canAnswer = state.phase == KorakPoKorakPhase.MY_TURN || state.phase == KorakPoKorakPhase.MY_BONUS
                        Surface(color = NavyLight, shadowElevation = 8.dp) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp).navigationBarsPadding(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = answer,
                                    onValueChange = { answer = it },
                                    placeholder = { Text("Unesite odgovor...", color = MediumGray) },
                                    enabled = canAnswer,
                                    singleLine = true, modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = PrimaryBlueBright, unfocusedBorderColor = MediumGray,
                                        cursorColor = PrimaryBlueBright, focusedTextColor = White, unfocusedTextColor = White,
                                        disabledBorderColor = DarkGray, disabledTextColor = MediumGray
                                    )
                                )
                                Button(
                                    onClick = {
                                        when (state.phase) {
                                            KorakPoKorakPhase.MY_TURN -> { viewModel.submitAnswer(answer); answer = "" }
                                            KorakPoKorakPhase.MY_BONUS -> { viewModel.submitMyBonusAnswer(answer); answer = "" }
                                            else -> {}
                                        }
                                    },
                                    enabled = canAnswer && answer.isNotBlank(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlueBright),
                                    modifier = Modifier.height(56.dp)
                                ) {
                                    Text("POGODI", fontWeight = FontWeight.Bold, color = White)
                                }
                            }
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
private fun StepCard(stepNumber: Int, clueText: String, isRevealed: Boolean, isCurrent: Boolean) {
    val borderColor = when {
        isCurrent -> Gold
        isRevealed -> PrimaryBlue.copy(alpha = 0.5f)
        else -> Color.Transparent
    }
    val bgColor = when {
        isCurrent -> NavyCard
        isRevealed -> NavyCard.copy(alpha = 0.7f)
        else -> NavyLight
    }

    Card(
        modifier = Modifier.fillMaxWidth()
            .then(if (borderColor != Color.Transparent) Modifier.border(1.5.dp, borderColor, RoundedCornerShape(12.dp)) else Modifier),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor)
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(28.dp).clip(CircleShape).background(if (isRevealed) PrimaryBlue else MediumGray),
                contentAlignment = Alignment.Center
            ) {
                Text("$stepNumber", color = White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
            Spacer(modifier = Modifier.width(12.dp))
            if (isRevealed) {
                Column {
                    if (isCurrent) Text("Korak $stepNumber", color = Gold, style = MaterialTheme.typography.labelSmall)
                    Text(
                        clueText,
                        color = if (isCurrent) White else OffWhite,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.Lock, null, tint = MediumGray, modifier = Modifier.size(14.dp))
                    Text("Korak $stepNumber je skriven", color = MediumGray, style = MaterialTheme.typography.bodySmall)
                }
            }
            if (isCurrent) {
                Spacer(modifier = Modifier.weight(1f))
                Surface(shape = RoundedCornerShape(6.dp), color = Gold.copy(alpha = 0.2f)) {
                    Text("AKTIVAN", color = Gold, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                }
            }
        }
    }
}
