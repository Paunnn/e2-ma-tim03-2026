package com.tim03.slagalica.ui.games

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tim03.slagalica.ui.theme.*
import com.tim03.slagalica.viewmodel.SkockoAttemptResult
import com.tim03.slagalica.viewmodel.SkockoPhase
import com.tim03.slagalica.viewmodel.SkockoViewModel
import kotlinx.coroutines.delay

private data class SkockoSymbolDef(val kind: String, val color: Color)

private val skockoSymbols = listOf(
    SkockoSymbolDef("skocko",  GameKoZnaZna),
    SkockoSymbolDef("kvadrat", GameSpojnice),
    SkockoSymbolDef("krug",    GameKorak),
    SkockoSymbolDef("srce",    GameSkocko),
    SkockoSymbolDef("trougao", GameAsocijacije),
    SkockoSymbolDef("zvezda",  GameMojBroj)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkockoScreen(
    onExitClick: () -> Unit,
    isPartijaMode: Boolean = false,
    onPartijaGameComplete: (Int, Int) -> Unit = { _, _ -> },
    myScoreOffset: Int = 0,
    oppScoreOffset: Int = 0,
    viewModel: SkockoViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val isInteractive = state.phase == SkockoPhase.MY_TURN || state.phase == SkockoPhase.MY_BONUS

    val timerColor = when {
        state.timeLeft > 20 -> TimerGreen
        state.timeLeft > 10 -> TimerYellow
        else -> TimerRed
    }

    val maxTimer = when (state.phase) {
        SkockoPhase.MY_BONUS -> 10f
        else -> 30f
    }

    Scaffold(
        containerColor = Navy,
        topBar = {
            GameHud(
                gameName = "Skočko",
                gameColor = GameSkocko,
                gameIcon = "S",
                round = "Pokušaj ${state.myAttempts.size + 1}/6",
                timeLeft = state.timeLeft,
                totalTime = maxTimer.toInt(),
                myScore = state.myScore + myScoreOffset,
                oppScore = state.opponentScore + oppScoreOffset,
                onExit = onExitClick
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {

            LaunchedEffect(state.phase == SkockoPhase.GAME_OVER) {
                if (state.phase == SkockoPhase.GAME_OVER && isPartijaMode) {
                    delay(3000L)
                    onPartijaGameComplete(state.myScore, state.opponentScore)
                }
            }
            if (state.phase == SkockoPhase.GAME_OVER && !isPartijaMode) {
                GameOverContent(
                    myScore = state.myScore,
                    opponentScore = state.opponentScore,
                    onFinish = onExitClick
                )
            } else {
            // Message banner
            state.message?.let { msg ->
                Surface(color = NavyCard) {
                    Text(
                        msg,
                        color = Gold,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Scoring legend
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = NavyCard)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        SkockoScoreBadge("1-2. pokušaj", "20 bod.", SuccessGreen)
                        SkockoScoreBadge("3-4. pokušaj", "15 bod.", TimerYellow)
                        SkockoScoreBadge("5-6. pokušaj", "10 bod.", TimerRed)
                    }
                }

                // Attempts display
                val showingOpponentAttempts = state.phase == SkockoPhase.WAITING_OPPONENT ||
                    state.phase == SkockoPhase.MY_BONUS ||
                    state.phase == SkockoPhase.GAME_OVER
                val attemptsToShow = if (showingOpponentAttempts) state.opponentAttempts else state.myAttempts

                val currentInputForDisplay = when {
                    isInteractive && attemptsToShow.size < 6 -> state.currentInput
                    else -> null
                }

                Text(
                    text = if (showingOpponentAttempts) "Pokušaji protivnika (Runda 2)" else "Pokušaji (Runda 1)",
                    color = LightGray,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )

                (0..5).forEach { rowIndex ->
                    val attempt = attemptsToShow.getOrNull(rowIndex)
                    val isCurrent = rowIndex == attemptsToShow.size && currentInputForDisplay != null
                    SkockoAttemptRow(
                        rowIndex = rowIndex,
                        attempt = attempt,
                        isCurrent = isCurrent,
                        currentSymbols = if (isCurrent) currentInputForDisplay else null
                    )
                }

                // MY_BONUS: dedicated 7th row for player's one attempt
                if (state.phase == SkockoPhase.MY_BONUS) {
                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(color = GameSkocko.copy(alpha = 0.4f))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "TVOJ BONUS POKUŠAJ",
                        color = GameSkocko,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    SkockoAttemptRow(
                        rowIndex = 6,
                        attempt = null,
                        isCurrent = true,
                        currentSymbols = state.currentInput
                    )
                }

                // R1 solution shown briefly after round 1 ends, before round 2 starts
                if (state.showRound1Solution) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Gold.copy(alpha = 0.08f)),
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, Gold.copy(alpha = 0.4f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                "REŠENJE RUNDE 1",
                                color = Gold, fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp
                            )
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                state.mySolution.forEach { symIdx -> SkockoSlot(symbolIndex = symIdx, isCurrent = false) }
                            }
                        }
                    }
                }

                // GAME_OVER: reveal R2 solution only (R1 was already shown after round 1)
                if (state.phase == SkockoPhase.GAME_OVER) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Gold.copy(alpha = 0.08f)),
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, Gold.copy(alpha = 0.4f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                "REŠENJE RUNDE 2",
                                color = Gold, fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp
                            )
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                state.opponentSolution.forEach { symIdx -> SkockoSlot(symbolIndex = symIdx, isCurrent = false) }
                            }
                        }
                    }
                }

                if (isInteractive) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Odaberite simbole",
                        color = LightGray,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        skockoSymbols.forEachIndexed { index, sym ->
                            SkockoSymbolButton(
                                sym = sym,
                                modifier = Modifier.weight(1f),
                                onClick = { viewModel.addSymbol(index) }
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.removeLastSymbol() },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MediumGray)
                        ) {
                            Icon(Icons.Default.Backspace, null, tint = LightGray, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Briši", color = LightGray)
                        }
                        OutlinedButton(
                            onClick = { viewModel.clearInput() },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, ErrorRed.copy(alpha = 0.5f))
                        ) {
                            Icon(Icons.Default.Clear, null, tint = ErrorRed, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Poništi", color = ErrorRed)
                        }
                    }
                }
            }

            if (isInteractive) {
                Surface(color = NavyLight, shadowElevation = 8.dp) {
                    Button(
                        onClick = { viewModel.submitAttempt() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                            .navigationBarsPadding()
                            .height(52.dp),
                        enabled = state.currentInput.size == 4,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = GameSkocko)
                    ) {
                        Text("POTVRDI POKUŠAJ", fontWeight = FontWeight.Bold, color = White)
                    }
                }
            }
            } // end else (not GAME_OVER)
        }
    }
}

@Composable
private fun SkockoAttemptRow(
    rowIndex: Int,
    attempt: SkockoAttemptResult?,
    isCurrent: Boolean,
    currentSymbols: List<Int>?
) {
    val bgColor = when {
        attempt != null && attempt.correctPos == 4 -> SuccessGreen.copy(alpha = 0.15f)
        isCurrent -> PrimaryBlue.copy(alpha = 0.1f)
        else -> NavyCard
    }
    val borderColor = when {
        attempt != null && attempt.correctPos == 4 -> SuccessGreen
        isCurrent -> PrimaryBlueBright
        attempt != null -> NavyCardLight
        else -> DarkGray
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .border(1.5.dp, borderColor, RoundedCornerShape(12.dp))
            .padding(10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (0..3).forEach { col ->
                val symIndex = attempt?.symbols?.getOrNull(col) ?: currentSymbols?.getOrNull(col)
                SkockoSlot(symbolIndex = symIndex, isCurrent = isCurrent)
            }
        }

        if (attempt != null) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // White = correct position, coral = correct symbol wrong pos, dark = wrong
                repeat(attempt.correctPos) {
                    Box(modifier = Modifier.size(14.dp).clip(CircleShape).background(White))
                }
                repeat(attempt.correctSym) {
                    Box(modifier = Modifier.size(14.dp).clip(CircleShape).background(GameSkocko))
                }
                repeat(4 - attempt.correctPos - attempt.correctSym) {
                    Box(modifier = Modifier.size(14.dp).clip(CircleShape).background(DarkGray))
                }
            }
        } else {
            Text("${rowIndex + 1}.", color = MediumGray, fontSize = 12.sp)
        }
    }
}

@Composable
private fun SkockoSlot(symbolIndex: Int?, isCurrent: Boolean) {
    val sym = symbolIndex?.let { skockoSymbols.getOrNull(it) }
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(sym?.color?.copy(alpha = 0.18f) ?: if (isCurrent) NavyCardLight else NavyLight)
            .border(
                1.5.dp,
                sym?.color?.copy(alpha = 0.6f) ?: if (isCurrent) MediumGray else DarkGray,
                RoundedCornerShape(8.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        if (sym != null) {
            SkockoShape(kind = sym.kind, size = 26.dp, color = sym.color)
        }
    }
}

@Composable
private fun SkockoSymbolButton(sym: SkockoSymbolDef, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .height(52.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(sym.color.copy(alpha = 0.15f))
            .border(1.5.dp, sym.color.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        SkockoShape(kind = sym.kind, size = 28.dp, color = sym.color)
    }
}

@Composable
private fun SkockoScoreBadge(label: String, points: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(points, color = color, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        Text(label, color = MediumGray, fontSize = 9.sp)
    }
}

@Composable
private fun GameResultCard(myScore: Int, opponentScore: Int) {
    val winner = when {
        myScore > opponentScore -> "Pobedio/la si!"
        myScore < opponentScore -> "Protivnik je pobedio."
        else -> "Nerešeno!"
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = NavyCard),
        border = androidx.compose.foundation.BorderStroke(2.dp, Gold)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Default.EmojiEvents, null, tint = Gold, modifier = Modifier.size(40.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text(winner, color = Gold, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Ti", color = LightGray, style = MaterialTheme.typography.labelSmall)
                    Text("$myScore", color = White, fontWeight = FontWeight.ExtraBold, fontSize = 28.sp)
                    Text("bodova", color = MediumGray, style = MaterialTheme.typography.labelSmall)
                }
                Text(":", color = MediumGray, fontWeight = FontWeight.Bold, fontSize = 28.sp)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Protivnik", color = LightGray, style = MaterialTheme.typography.labelSmall)
                    Text("$opponentScore", color = White, fontWeight = FontWeight.ExtraBold, fontSize = 28.sp)
                    Text("bodova", color = MediumGray, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
