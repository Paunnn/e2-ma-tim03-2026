package com.tim03.slagalica.ui.games

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import com.tim03.slagalica.viewmodel.MojBrojPhase
import com.tim03.slagalica.viewmodel.MojBrojViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MojBrojScreen(
    onExitClick: () -> Unit,
    viewModel: MojBrojViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val timerColor = when {
        state.timeLeft > 30 -> TimerGreen
        state.timeLeft > 15 -> TimerYellow
        else -> TimerRed
    }

    Box(modifier = Modifier.fillMaxSize().background(Navy)) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text("MOJ BROJ", fontWeight = FontWeight.ExtraBold, color = White, letterSpacing = 1.sp, fontSize = 16.sp) },
                    navigationIcon = { IconButton(onClick = onExitClick) { Icon(Icons.Default.Close, null, tint = LightGray) } },
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
                if (state.gameOver) {
                    GameOverContent(myScore = state.myScore, opponentScore = state.opponentScore, onFinish = onExitClick)
                } else {
                    // Timer bar
                    Column(modifier = Modifier.background(NavyLight)) {
                        LinearProgressIndicator(
                            progress = state.timeLeft.toFloat() / 60f,
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

                    // Round + shake hint
                    Row(
                        modifier = Modifier.fillMaxWidth().background(NavyCard).padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Runda ${state.currentRound} / 2", color = Gold, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.PhoneAndroid, null, tint = LightGray, modifier = Modifier.size(14.dp))
                            Text("Tresite za stop", color = LightGray, style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    // Scores
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .background(Brush.horizontalGradient(listOf(PrimaryBlue.copy(alpha = 0.3f), Navy, WarningOrange.copy(alpha = 0.15f))))
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
                    ) {
                        PlayerChip(name = "Ti", score = state.myScore, isActive = true)
                        Text("VS", color = MediumGray, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        PlayerChip(name = "Protivnik", score = state.opponentScore, isActive = false)
                    }

                    // Round result message
                    AnimatedVisibility(visible = state.roundResultMessage != null && state.phase == MojBrojPhase.SUBMITTED) {
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(containerColor = Gold.copy(alpha = 0.1f)),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Gold.copy(alpha = 0.3f))
                        ) {
                            Text(
                                state.roundResultMessage ?: "",
                                color = Gold, style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(12.dp).fillMaxWidth(),
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    Column(
                        modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Target number card
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (state.phase != MojBrojPhase.WAITING_FIRST_STOP && state.phase != MojBrojPhase.WAITING_SECOND_STOP)
                                    PrimaryBlue.copy(alpha = 0.2f) else NavyCard
                            ),
                            border = if (state.phase != MojBrojPhase.WAITING_FIRST_STOP && state.phase != MojBrojPhase.WAITING_SECOND_STOP)
                                androidx.compose.foundation.BorderStroke(1.5.dp, PrimaryBlueBright) else null
                        ) {
                            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                Column {
                                    Text("Traženi broj", color = LightGray, style = MaterialTheme.typography.labelSmall)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    AnimatedContent(targetState = state.phase != MojBrojPhase.WAITING_FIRST_STOP && state.phase != MojBrojPhase.WAITING_SECOND_STOP) { revealed ->
                                        if (revealed) {
                                            Text("${state.targetNumber}", color = Gold, fontWeight = FontWeight.ExtraBold, fontSize = 48.sp)
                                        } else {
                                            Text("???", color = MediumGray, fontWeight = FontWeight.ExtraBold, fontSize = 48.sp)
                                        }
                                    }
                                }
                                if (state.phase == MojBrojPhase.WAITING_FIRST_STOP || state.phase == MojBrojPhase.WAITING_SECOND_STOP) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Button(
                                            onClick = { viewModel.pressStop() },
                                            shape = RoundedCornerShape(12.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = Gold)
                                        ) {
                                            Icon(Icons.Default.Stop, null, tint = Navy, modifier = Modifier.size(20.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("STOP", color = Navy, fontWeight = FontWeight.ExtraBold)
                                        }
                                        if (state.phase == MojBrojPhase.WAITING_SECOND_STOP) {
                                            Text("${state.waitingForStopTimeLeft}s do auto-otkrivanja", color = LightGray, style = MaterialTheme.typography.labelSmall)
                                        } else {
                                            Text("ili tresite uređaj", color = MediumGray, style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            }
                        }

                        // Available numbers
                        AnimatedVisibility(visible = state.phase == MojBrojPhase.PLAYING || state.phase == MojBrojPhase.SUBMITTED) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Vaši brojevi", color = LightGray, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    state.availableNumbers.forEachIndexed { index, num ->
                                        val isUsed = index in state.usedNumberIndices
                                        MojBrojNumberButton(
                                            number = num, isUsed = isUsed,
                                            modifier = Modifier.weight(1f),
                                            onClick = { viewModel.appendNumber(index) }
                                        )
                                    }
                                }
                            }
                        }

                        // Expression builder
                        AnimatedVisibility(visible = state.phase == MojBrojPhase.PLAYING || state.phase == MojBrojPhase.SUBMITTED) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Card(
                                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = NavyCard)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("Vaš izraz", color = LightGray, style = MaterialTheme.typography.labelSmall)
                                            state.expressionResult?.let { res ->
                                                val resColor = if (res == state.targetNumber) SuccessGreen else LightGray
                                                Text("= $res", color = resColor, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = state.expression.ifEmpty { "Unesite izraz..." },
                                            color = if (state.expression.isEmpty()) MediumGray else White,
                                            style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, minLines = 2
                                        )
                                    }
                                }

                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf("+", "-", "×", "÷", "(", ")").forEach { op ->
                                        MojBrojOperatorButton(operator = op, modifier = Modifier.weight(1f)) {
                                            val actualOp = when (op) { "×" -> "*"; "÷" -> "/"; else -> op }
                                            viewModel.appendOperator(actualOp)
                                        }
                                    }
                                }

                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(
                                        onClick = { viewModel.backspace() },
                                        modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, MediumGray),
                                        enabled = state.phase == MojBrojPhase.PLAYING
                                    ) {
                                        Icon(Icons.Default.Backspace, null, tint = LightGray, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Briši", color = LightGray, style = MaterialTheme.typography.labelMedium)
                                    }
                                    OutlinedButton(
                                        onClick = { viewModel.clearExpression() },
                                        modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, ErrorRed.copy(alpha = 0.5f)),
                                        enabled = state.phase == MojBrojPhase.PLAYING
                                    ) {
                                        Icon(Icons.Default.Clear, null, tint = ErrorRed, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Poništi", color = ErrorRed, style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }
                        }
                    }

                    // Submit button
                    Surface(color = NavyLight, shadowElevation = 8.dp) {
                        val isCorrect = state.expressionResult == state.targetNumber
                        Button(
                            onClick = { viewModel.submitRound() },
                            modifier = Modifier.fillMaxWidth().padding(12.dp).navigationBarsPadding().height(52.dp),
                            enabled = state.phase == MojBrojPhase.PLAYING,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isCorrect) SuccessGreen else PrimaryBlueBright,
                                disabledContainerColor = DarkGray
                            )
                        ) {
                            Icon(
                                if (isCorrect) Icons.Default.CheckCircle else Icons.Default.Send,
                                null, tint = White, modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                if (isCorrect) "TAČNO! POTVRDI" else "POTVRDI",
                                fontWeight = FontWeight.Bold, color = White
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MojBrojNumberButton(number: Int, isUsed: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier.height(52.dp).clip(RoundedCornerShape(10.dp))
            .background(if (isUsed) DarkGray else NavyCard)
            .border(1.5.dp, if (isUsed) DarkGray else PrimaryBlue.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
            .clickable(enabled = !isUsed, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text("$number", color = if (isUsed) MediumGray else White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }
}

@Composable
private fun MojBrojOperatorButton(operator: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier.height(44.dp).clip(RoundedCornerShape(10.dp))
            .background(NavyCardLight)
            .border(1.dp, MediumGray, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(operator, color = Gold, fontWeight = FontWeight.Bold, fontSize = 18.sp)
    }
}
