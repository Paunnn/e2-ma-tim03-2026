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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tim03.slagalica.ui.theme.*
import com.tim03.slagalica.viewmodel.MojBrojPhase
import com.tim03.slagalica.viewmodel.MojBrojViewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MojBrojScreen(
    onExitClick: () -> Unit,
    isPartijaMode: Boolean = false,
    onPartijaGameComplete: (Int, Int) -> Unit = { _, _ -> },
    myScoreOffset: Int = 0,
    oppScoreOffset: Int = 0,
    viewModel: MojBrojViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val username by viewModel.username.collectAsStateWithLifecycle()

    val timerColor = when {
        state.timeLeft > 30 -> TimerGreen
        state.timeLeft > 15 -> TimerYellow
        else -> TimerRed
    }

    Box(modifier = Modifier.fillMaxSize().background(Navy)) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                GameHud(
                    gameName = "Moj broj",
                    gameColor = GameMojBroj,
                    gameIcon = "#",
                    round = "${state.currentRound}/2 runda",
                    timeLeft = state.timeLeft,
                    totalTime = 60,
                    myScore = state.myScore + myScoreOffset,
                    oppScore = state.opponentScore + oppScoreOffset,
                    myName = username,
                    onExit = onExitClick
                )
            }
        ) { padding ->
            Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                LaunchedEffect(state.gameOver) {
                    if (state.gameOver && isPartijaMode) {
                        delay(3000L)
                        onPartijaGameComplete(state.myScore, state.opponentScore)
                    }
                }
                if (state.gameOver && !isPartijaMode) {
                    GameOverContent(myScore = state.myScore, opponentScore = state.opponentScore, onFinish = onExitClick)
                } else {
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
                        val isWaiting = state.phase == MojBrojPhase.WAITING_FIRST_STOP || state.phase == MojBrojPhase.WAITING_SECOND_STOP
                        val isRevealed = !isWaiting
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isRevealed) PrimaryBlue.copy(alpha = 0.2f) else NavyCard
                            ),
                            border = if (isRevealed)
                                androidx.compose.foundation.BorderStroke(1.5.dp, GameMojBroj.copy(alpha = 0.6f))
                            else
                                androidx.compose.foundation.BorderStroke(1.dp, NavyCardLight)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp, horizontal = 16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    "TRAŽENI BROJ",
                                    color = MediumGray, fontSize = 9.sp,
                                    fontWeight = FontWeight.ExtraBold, letterSpacing = 1.5.sp
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                AnimatedContent(targetState = isRevealed) { revealed ->
                                    if (revealed) {
                                        Text(
                                            "${state.targetNumber}",
                                            color = GameMojBroj,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 56.sp,
                                            modifier = Modifier.drawBehind {
                                                drawIntoCanvas { canvas ->
                                                    val paint = Paint()
                                                    paint.asFrameworkPaint().apply {
                                                        isAntiAlias = true
                                                        color = android.graphics.Color.TRANSPARENT
                                                        setShadowLayer(40f, 0f, 0f, GameMojBroj.copy(alpha = 0.55f).toArgb())
                                                    }
                                                    canvas.drawRect(-12f, -12f, size.width + 12f, size.height + 12f, paint)
                                                }
                                            }
                                        )
                                    } else {
                                        Text("???", color = MediumGray, fontWeight = FontWeight.ExtraBold, fontSize = 56.sp)
                                    }
                                }
                            }
                        }

                        // STOP button — separate, prominent, below the card
                        if (isWaiting) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Button(
                                    onClick = { viewModel.pressStop() },
                                    modifier = Modifier.fillMaxWidth().height(56.dp),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = GameMojBroj)
                                ) {
                                    Icon(Icons.Default.Stop, null, tint = Navy, modifier = Modifier.size(22.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("STOP", color = Navy, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                                }
                                Text(
                                    text = if (state.phase == MojBrojPhase.WAITING_SECOND_STOP)
                                        "${state.waitingForStopTimeLeft}s do automatskog otkrivanja"
                                    else
                                        "Pritisni STOP ili tresni uređaj",
                                    color = MediumGray,
                                    style = MaterialTheme.typography.labelSmall
                                )
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
            .background(if (isUsed) NavyLight else NavyCard)
            .border(1.5.dp, if (isUsed) DarkGray else PrimaryBlue.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
            .then(if (isUsed) Modifier.alpha(0.5f) else Modifier)
            .clickable(enabled = !isUsed, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "$number",
            color = if (isUsed) MediumGray else White,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            style = LocalTextStyle.current.copy(
                textDecoration = if (isUsed) TextDecoration.LineThrough else TextDecoration.None
            )
        )
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
