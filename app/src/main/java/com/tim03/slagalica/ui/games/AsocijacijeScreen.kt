package com.tim03.slagalica.ui.games

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tim03.slagalica.ui.theme.*
import com.tim03.slagalica.viewmodel.AsocijacijePhase
import com.tim03.slagalica.viewmodel.AsocijacijeViewModel
import com.tim03.slagalica.viewmodel.AsocijacijeViewModelFactory

private val columnColors = listOf(
    PrimaryBlueBright,
    GameSpojnice,
    GameAsocijacije,
    GameMojBroj
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AsocijacijeScreen(
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
    val viewModel: AsocijacijeViewModel = viewModel(
        factory = AsocijacijeViewModelFactory(sessionId, isPlayer1, gameIdx)
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.phase) {
        if (state.phase == AsocijacijePhase.GAME_OVER && isPartijaMode) {
            kotlinx.coroutines.delay(3000L)
            onPartijaGameComplete(state.myScore, state.opponentScore)
        }
    }
    var answerText by remember { mutableStateOf("") }
    var guessMode by remember { mutableStateOf<GuessMode>(GuessMode.None) }

    val timerColor = when {
        state.timeLeft > 80 -> TimerGreen
        state.timeLeft > 40 -> TimerYellow
        else -> TimerRed
    }

    if (state.phase == AsocijacijePhase.LOADING) {
        Box(modifier = Modifier.fillMaxSize().background(Navy), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = PrimaryBlueBright)
        }
        return
    }

    Scaffold(
        containerColor = Navy,
        topBar = {
            GameHud(
                gameName = "Asocijacije",
                gameColor = GameAsocijacije,
                gameIcon = "✦",
                round = "${state.currentRound}/2 runda",
                timeLeft = state.timeLeft,
                totalTime = 120,
                myScore = state.myScore + myScoreOffset,
                oppScore = state.opponentScore + oppScoreOffset,
                oppName = oppName,
                onExit = onExitClick
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {

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
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val question = state.currentQuestion

                if (question == null && state.waitingMessage != null) {
                    Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        androidx.compose.foundation.layout.Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(color = GameAsocijacije)
                            Text(state.waitingMessage ?: "", color = LightGray, textAlign = TextAlign.Center)
                        }
                    }
                }

                if (question != null) {
                    // Column headers
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        (0..3).forEach { col ->
                            Text(
                                text = "${('A' + col)}",
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.Center,
                                color = columnColors[col],
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 14.sp
                            )
                        }
                    }

                    // Clue grid
                    (0..3).forEach { row ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            (0..3).forEach { col ->
                                val isRevealed = state.revealed[col][row] || state.revealAll
                                val isSolved = state.columnSolved[col] || state.revealAll
                                val clue = question.columns()[col].clues.getOrNull(row) ?: ""
                                val canReveal = !isRevealed && !isSolved &&
                                    state.isMyTurn &&
                                    state.phase == AsocijacijePhase.MY_TURN &&
                                    !state.hasRevealedThisTurn &&
                                    !state.revealAll
                                val cellLabel = "${'A' + col}${row + 1}"
                                AsocijacijeClueCell(
                                    text = if (isRevealed || isSolved) clue else cellLabel,
                                    isRevealed = isRevealed || isSolved,
                                    isClickable = canReveal,
                                    color = columnColors[col],
                                    modifier = Modifier.weight(1f),
                                    onClick = { if (canReveal) viewModel.revealField(col, row) }
                                )
                            }
                        }
                    }

                    // Column solution cells
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        (0..3).forEach { col ->
                            val isSolved = state.columnSolved[col] || state.revealAll
                            AsocijacijeSolutionCell(
                                text = if (isSolved) question.columns()[col].solution else "Reši ${('A' + col)}",
                                isSolved = isSolved,
                                color = columnColors[col],
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    if (!isSolved && state.isMyTurn && state.phase == AsocijacijePhase.MY_TURN && !state.revealAll) {
                                        guessMode = GuessMode.Column(col)
                                        answerText = ""
                                    }
                                }
                            )
                        }
                    }

                    // Final solution card
                    val effectiveFinalSolved = state.finalSolved || state.revealAll
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                if (effectiveFinalSolved)
                                    Brush.linearGradient(listOf(GameAsocijacije, PrimaryBlueBright))
                                else
                                    Brush.linearGradient(listOf(NavyCard, NavyCard))
                            )
                            .border(
                                if (effectiveFinalSolved) 2.dp else 1.dp,
                                if (effectiveFinalSolved) GameAsocijacije else MediumGray,
                                RoundedCornerShape(14.dp)
                            )
                            .clickable(enabled = !effectiveFinalSolved && state.isMyTurn && state.phase == AsocijacijePhase.MY_TURN && !state.revealAll) {
                                guessMode = GuessMode.Final
                                answerText = ""
                            }
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "KONAČNO REŠENJE",
                                color = if (effectiveFinalSolved) Navy else MediumGray,
                                fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.5.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (effectiveFinalSolved) question.finalSolution else "???",
                                color = if (effectiveFinalSolved) Navy else LightGray,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = if (effectiveFinalSolved) 22.sp else 18.sp
                            )
                            if (!effectiveFinalSolved && state.isMyTurn && state.phase == AsocijacijePhase.MY_TURN) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Tapni za pogađanje", color = MediumGray, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }

                    if (state.phase == AsocijacijePhase.GAME_OVER && !isPartijaMode) {
                        Spacer(modifier = Modifier.height(8.dp))
                        AsocijacijeResultCard(state.myScore, state.opponentScore)
                    }
                }
            }

            // Opponent-turn banner (multiplayer only)
            if (state.phase == AsocijacijePhase.OPPONENT_TURN && sessionId.isNotEmpty() && !state.revealAll) {
                Surface(color = NavyLight, shadowElevation = 4.dp) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                            .navigationBarsPadding(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Default.SmartToy, null, tint = Accent2, modifier = Modifier.size(18.dp))
                        Text(
                            "$oppName igra, čekaj na red",
                            color = Accent2,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // Input area
            if (state.phase == AsocijacijePhase.MY_TURN && state.isMyTurn && !state.revealAll) {
                when (val mode = guessMode) {
                    is GuessMode.Column -> {
                        GuessInputBar(
                            hint = "Rešenje kolone ${('A' + mode.col)}...",
                            value = answerText,
                            onValueChange = { answerText = it },
                            onSubmit = {
                                viewModel.guessColumn(mode.col, answerText)
                                answerText = ""
                                guessMode = GuessMode.None
                            },
                            onCancel = {
                                guessMode = GuessMode.None
                                answerText = ""
                            }
                        )
                    }
                    is GuessMode.Final -> {
                        GuessInputBar(
                            hint = "Konačno rešenje...",
                            value = answerText,
                            onValueChange = { answerText = it },
                            onSubmit = {
                                viewModel.guessFinal(answerText)
                                answerText = ""
                                guessMode = GuessMode.None
                            },
                            onCancel = {
                                guessMode = GuessMode.None
                                answerText = ""
                            }
                        )
                    }
                    GuessMode.None -> {
                        Surface(color = NavyLight, shadowElevation = 4.dp) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp)
                                    .navigationBarsPadding(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = if (state.hasRevealedThisTurn)
                                        "Pogodi rešenje kolone ili konačno rešenje, ili preskoči."
                                    else
                                        "Tapni polje da otvoriš, ili tapni rešenje kolone/konačno.",
                                    color = MediumGray,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f)
                                )
                                // "Preskoči" shown only after player has revealed a field this turn
                                if (state.hasRevealedThisTurn) {
                                    OutlinedButton(
                                        onClick = {
                                            guessMode = GuessMode.None
                                            answerText = ""
                                            viewModel.passTurn()
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        border = androidx.compose.foundation.BorderStroke(
                                            1.dp, WarningOrange.copy(alpha = 0.6f)
                                        )
                                    ) {
                                        Text("PRESKOČI", color = WarningOrange, fontSize = 11.sp)
                                    }
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
private fun GuessInputBar(
    hint: String,
    value: String,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onCancel: () -> Unit
) {
    Surface(color = NavyLight, shadowElevation = 8.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp).navigationBarsPadding(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(onClick = onCancel) {
                Icon(Icons.Default.Close, null, tint = MediumGray)
            }
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                placeholder = { Text(hint, color = MediumGray) },
                singleLine = true,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PrimaryBlueBright,
                    unfocusedBorderColor = MediumGray,
                    cursorColor = PrimaryBlueBright,
                    focusedTextColor = White,
                    unfocusedTextColor = White
                )
            )
            Button(
                onClick = onSubmit,
                enabled = value.isNotBlank(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlueBright),
                modifier = Modifier.height(56.dp)
            ) {
                Text("POGODI", fontWeight = FontWeight.Bold, color = White)
            }
        }
    }
}

@Composable
private fun AsocijacijeClueCell(
    text: String,
    isRevealed: Boolean,
    isClickable: Boolean = false,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    // Closed: solid game color bg + label (A1/B2..) in dark text (tappable to reveal)
    // Revealed: NavyCard bg + white text
    val bgColor = when {
        isRevealed -> NavyCard
        isClickable -> color
        else -> color.copy(alpha = 0.4f)
    }
    val borderColor = when {
        isRevealed -> color.copy(alpha = 0.5f)
        isClickable -> color
        else -> color.copy(alpha = 0.25f)
    }
    Box(
        modifier = modifier
            .height(64.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(bgColor)
            .border(1.5.dp, borderColor, RoundedCornerShape(10.dp))
            .clickable(enabled = isClickable || isRevealed, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (isRevealed) White else if (isClickable) Navy else Navy.copy(alpha = 0.7f),
            fontWeight = if (isRevealed) FontWeight.SemiBold else FontWeight.ExtraBold,
            fontSize = if (isRevealed) 10.sp else 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(4.dp)
        )
    }
}

@Composable
private fun AsocijacijeSolutionCell(
    text: String,
    isSolved: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    // Solved column solution = Accent4 (mint) bg + Navy text
    Box(
        modifier = modifier
            .height(40.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSolved) Accent4 else NavyCard)
            .border(1.5.dp, if (isSolved) Accent4 else color.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
            .clickable(enabled = !isSolved, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (isSolved) Navy else color.copy(alpha = 0.8f),
            fontWeight = if (isSolved) FontWeight.ExtraBold else FontWeight.SemiBold,
            fontSize = 10.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }
}

@Composable
private fun AsocijacijeResultCard(myScore: Int, opponentScore: Int) {
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

private sealed class GuessMode {
    object None : GuessMode()
    object Final : GuessMode()
    data class Column(val col: Int) : GuessMode()
}
