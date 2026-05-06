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
import com.tim03.slagalica.ui.theme.*
import kotlinx.coroutines.delay

private val mockSteps = listOf(
    "Osnivač kompanije Apple i Pixar",
    "Poznat po revolucionarnim prezentacijama",
    "Vratio se u Apple 1997. godine",
    "Predstavio prvi iPhone 2007. godine",
    "Autor knjige 'The Journey is the Reward'",
    "Studirao na Reed Collegeu",
    "Preminuo 2011. godine"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KorakPoKorakScreen(onExitClick: () -> Unit) {
    var answer by remember { mutableStateOf("") }
    var revealedSteps by remember { mutableStateOf(1) }
    var currentRound by remember { mutableStateOf(1) }
    var myScore by remember { mutableStateOf(0) }
    var opponentScore by remember { mutableStateOf(0) }
    var timeLeft by remember { mutableStateOf(70) }
    var isMyTurn by remember { mutableStateOf(true) }
    var gameOver by remember { mutableStateOf(false) }

    LaunchedEffect(currentRound, isMyTurn, gameOver) {
        if (gameOver) return@LaunchedEffect
        val totalTime = if (isMyTurn) 70 else 10
        timeLeft = totalTime
        while (timeLeft > 0 && !gameOver) {
            delay(1000L)
            timeLeft--
            if (isMyTurn) {
                val elapsed = totalTime - timeLeft
                val expected = (elapsed / 10) + 1
                if (expected > revealedSteps && revealedSteps < mockSteps.size) {
                    revealedSteps = expected.coerceAtMost(mockSteps.size)
                }
            }
        }
        if (!gameOver) {
            if (isMyTurn) {
                isMyTurn = false
            } else {
                if (currentRound < 2) {
                    currentRound++
                    isMyTurn = true
                    revealedSteps = 1
                    answer = ""
                } else {
                    gameOver = true
                }
            }
        }
    }

    val timerColor = when {
        timeLeft > 30 -> TimerGreen
        timeLeft > 15 -> TimerYellow
        else -> TimerRed
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Navy)
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            "KORAK PO KORAK",
                            fontWeight = FontWeight.ExtraBold,
                            color = White,
                            letterSpacing = 1.sp,
                            fontSize = 16.sp
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onExitClick) {
                            Icon(Icons.Default.Close, null, tint = LightGray)
                        }
                    },
                    actions = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(end = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.Token, null, tint = GoldLight, modifier = Modifier.size(16.dp))
                            Text("5", color = White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Icon(Icons.Default.Star, null, tint = Gold, modifier = Modifier.size(16.dp))
                            Text("0", color = White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = NavyLight)
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                if (gameOver) {
                    GameOverContent(
                        myScore = myScore,
                        opponentScore = opponentScore,
                        onFinish = onExitClick
                    )
                } else {
                TimerSection(timeLeft = timeLeft, totalTime = if (isMyTurn) 70 else 10, timerColor = timerColor)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(NavyCard)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Runda $currentRound / 2",
                        color = Gold,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isMyTurn) PrimaryBlue else DarkGray
                    ) {
                        Text(
                            if (isMyTurn) "  Tvoj red  " else "  Čekaš  ",
                            color = if (isMyTurn) White else LightGray,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                PlayerScoreRow(myScore = myScore, opponentScore = opponentScore)

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = NavyCard)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.HelpOutline, null, tint = MediumGray, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Traženi pojam", color = MediumGray, style = MaterialTheme.typography.bodySmall)
                            Spacer(modifier = Modifier.weight(1f))
                            Text("???", color = LightGray, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        "Koraci",
                        color = LightGray,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    mockSteps.forEachIndexed { index, clue ->
                        val isRevealed = index < revealedSteps
                        val isCurrent = index == revealedSteps - 1 && isRevealed

                        AnimatedVisibility(
                            visible = true,
                            enter = fadeIn() + slideInVertically()
                        ) {
                            StepCard(
                                stepNumber = index + 1,
                                clueText = clue,
                                isRevealed = isRevealed,
                                isCurrent = isCurrent
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (!isMyTurn) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = WarningOrange.copy(alpha = 0.15f)),
                            border = androidx.compose.foundation.BorderStroke(1.dp, WarningOrange.copy(alpha = 0.4f))
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Info, null, tint = WarningOrange, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "Protivnik nije pogodio! Imate 10s da osvoje 5 bodova.",
                                    color = WarningOrange,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }

                AnswerSection(
                    answer = answer,
                    onAnswerChange = { answer = it },
                    onSubmit = {
                        val pointsEarned = (20 - (revealedSteps - 1) * 2).coerceAtLeast(0)
                        if (isMyTurn) {
                            myScore += pointsEarned
                            isMyTurn = false
                        } else {
                            myScore += 5
                            if (currentRound < 2) {
                                currentRound++
                                isMyTurn = true
                                revealedSteps = 1
                            } else {
                                gameOver = true
                            }
                        }
                        answer = ""
                    },
                    enabled = isMyTurn || (!isMyTurn && timeLeft > 0)
                )
                }
            }
        }
    }
}

@Composable
private fun TimerSection(timeLeft: Int, totalTime: Int, timerColor: Color) {
    Column(modifier = Modifier.background(NavyLight)) {
        LinearProgressIndicator(
            progress = timeLeft.toFloat() / totalTime,
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp),
            color = timerColor,
            trackColor = DarkGray
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(Icons.Default.Timer, null, tint = timerColor, modifier = Modifier.size(16.dp))
                Text(
                    "$timeLeft s",
                    color = timerColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }
    }
}

@Composable
private fun PlayerScoreRow(myScore: Int, opponentScore: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.horizontalGradient(listOf(PrimaryBlue.copy(alpha = 0.3f), Navy, WarningOrange.copy(alpha = 0.15f)))
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        PlayerChip(name = "Ti", score = myScore, isActive = true)
        Text("VS", color = MediumGray, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        PlayerChip(name = "Protivnik", score = opponentScore, isActive = false)
    }
}

@Composable
fun PlayerChip(name: String, score: Int, isActive: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (!isActive) Spacer(modifier = Modifier.weight(1f))
        Column(horizontalAlignment = if (isActive) Alignment.Start else Alignment.End) {
            Text(name, color = if (isActive) PrimaryBlueLight else LightGray, style = MaterialTheme.typography.labelSmall)
            Text(
                "$score bod.",
                color = if (isActive) White else LightGray,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium
            )
        }
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(if (isActive) PrimaryBlue else DarkGray)
                .then(if (isActive) Modifier.border(2.dp, Gold, CircleShape) else Modifier),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Person, null, tint = White, modifier = Modifier.size(20.dp))
        }
        if (isActive) Spacer(modifier = Modifier.weight(1f))
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
        modifier = Modifier
            .fillMaxWidth()
            .then(if (borderColor != Color.Transparent) Modifier.border(1.5.dp, borderColor, RoundedCornerShape(12.dp)) else Modifier),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(if (isRevealed) PrimaryBlue else MediumGray),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "$stepNumber",
                    color = White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            if (isRevealed) {
                Column {
                    if (isCurrent) {
                        Text("Korak $stepNumber", color = Gold, style = MaterialTheme.typography.labelSmall)
                    }
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
                    Text(
                        "Korak $stepNumber — skriveno",
                        color = MediumGray,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            if (isCurrent) {
                Spacer(modifier = Modifier.weight(1f))
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Gold.copy(alpha = 0.2f)
                ) {
                    Text(
                        "AKTIVAN",
                        color = Gold,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun AnswerSection(
    answer: String,
    onAnswerChange: (String) -> Unit,
    onSubmit: () -> Unit,
    enabled: Boolean
) {
    Surface(
        color = NavyLight,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .navigationBarsPadding(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = answer,
                onValueChange = onAnswerChange,
                placeholder = { Text("Unesite odgovor...", color = MediumGray) },
                enabled = enabled,
                singleLine = true,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PrimaryBlueBright,
                    unfocusedBorderColor = MediumGray,
                    cursorColor = PrimaryBlueBright,
                    focusedTextColor = White,
                    unfocusedTextColor = White,
                    disabledBorderColor = DarkGray,
                    disabledTextColor = MediumGray
                )
            )
            Button(
                onClick = onSubmit,
                enabled = enabled && answer.isNotBlank(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlueBright),
                modifier = Modifier.height(56.dp)
            ) {
                Text("POGODI", fontWeight = FontWeight.Bold, color = White)
            }
        }
    }
}
