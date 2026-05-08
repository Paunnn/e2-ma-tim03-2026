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
import com.tim03.slagalica.ui.theme.*
import kotlinx.coroutines.delay

private val mockNumbers = listOf(3, 7, 5, 4, 10, 100)
private const val MOCK_TARGET = 321

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MojBrojScreen(onExitClick: () -> Unit) {
    var numbersRevealed by remember { mutableStateOf(false) }
    var targetRevealed by remember { mutableStateOf(false) }
    var expression by remember { mutableStateOf("") }
    var currentRound by remember { mutableStateOf(1) }
    var myScore by remember { mutableStateOf(0) }
    var opponentScore by remember { mutableStateOf(0) }
    var timeLeft by remember { mutableStateOf(60) }
    var usedNumberIndices by remember { mutableStateOf(setOf<Int>()) }
    var gameOver by remember { mutableStateOf(false) }
    var roundSubmitted by remember { mutableStateOf(false) }

    LaunchedEffect(currentRound, gameOver) {
        if (gameOver) return@LaunchedEffect
        timeLeft = 60
        roundSubmitted = false
        while (timeLeft > 0 && !gameOver && !roundSubmitted) {
            delay(1000L)
            timeLeft--
            if (timeLeft == 55 && !numbersRevealed) {
                targetRevealed = true
                numbersRevealed = true
            }
        }
        if (!gameOver) {
            if (currentRound < 2) {
                currentRound++
                expression = ""
                usedNumberIndices = emptySet()
                targetRevealed = false
                numbersRevealed = false
                roundSubmitted = false
            } else {
                gameOver = true
            }
        }
    }

    val timerColor = when {
        timeLeft > 30 -> TimerGreen
        timeLeft > 15 -> TimerYellow
        else -> TimerRed
    }

    val expressionResult: Int? = remember(expression) {
        if (expression.isBlank()) null
        else try {
            evalExpression(expression)
        } catch (e: Exception) {
            null
        }
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
                            "MOJ BROJ",
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
                Column(modifier = Modifier.background(NavyLight)) {
                    LinearProgressIndicator(
                        progress = timeLeft.toFloat() / 60f,
                        modifier = Modifier.fillMaxWidth().height(6.dp),
                        color = timerColor,
                        trackColor = DarkGray
                    )
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Default.Timer, null, tint = timerColor, modifier = Modifier.size(16.dp))
                            Text("$timeLeft s", color = timerColor, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(NavyCard)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Runda $currentRound / 2", color = Gold, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.PhoneAndroid, null, tint = LightGray, modifier = Modifier.size(14.dp))
                        Text("Tresite za stop", color = LightGray, style = MaterialTheme.typography.labelSmall)
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Brush.horizontalGradient(listOf(PrimaryBlue.copy(alpha = 0.3f), Navy, WarningOrange.copy(alpha = 0.15f))))
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PlayerChip(name = "Ti", score = myScore, isActive = true)
                    Text("VS", color = MediumGray, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    PlayerChip(name = "Protivnik", score = opponentScore, isActive = false)
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (targetRevealed) PrimaryBlue.copy(alpha = 0.2f) else NavyCard
                        ),
                        border = if (targetRevealed) androidx.compose.foundation.BorderStroke(1.5.dp, PrimaryBlueBright) else null
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Traženi broj", color = LightGray, style = MaterialTheme.typography.labelSmall)
                                Spacer(modifier = Modifier.height(4.dp))
                                AnimatedContent(targetState = targetRevealed) { revealed ->
                                    if (revealed) {
                                        Text(
                                            "$MOCK_TARGET",
                                            color = Gold,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 48.sp
                                        )
                                    } else {
                                        Text(
                                            "???",
                                            color = MediumGray,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 48.sp
                                        )
                                    }
                                }
                            }
                            if (!numbersRevealed) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Button(
                                        onClick = {
                                            targetRevealed = true
                                            numbersRevealed = true
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Gold)
                                    ) {
                                        Icon(Icons.Default.Stop, null, tint = Navy, modifier = Modifier.size(20.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("STOP", color = Navy, fontWeight = FontWeight.ExtraBold)
                                    }
                                    Text("ili tresite uređaj", color = MediumGray, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }

                    AnimatedVisibility(
                        visible = numbersRevealed,
                        enter = fadeIn() + expandVertically()
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Vaši brojevi", color = LightGray, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                mockNumbers.forEachIndexed { index, num ->
                                    val isUsed = index in usedNumberIndices
                                    NumberButton(
                                        number = num,
                                        isUsed = isUsed,
                                        modifier = Modifier.weight(1f),
                                        onClick = {
                                            if (!isUsed) {
                                                usedNumberIndices = usedNumberIndices + index
                                                expression += if (expression.isEmpty() || expression.last() in listOf('+', '-', '*', '/', '(')) "$num" else " $num"
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }

                    AnimatedVisibility(
                        visible = numbersRevealed,
                        enter = fadeIn() + expandVertically()
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = NavyCard)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Vaš izraz", color = LightGray, style = MaterialTheme.typography.labelSmall)
                                        if (expressionResult != null) {
                                            val resultColor = if (expressionResult == MOCK_TARGET) SuccessGreen else LightGray
                                            Text("= $expressionResult", color = resultColor, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = expression.ifEmpty { "Unesite izraz..." },
                                        color = if (expression.isEmpty()) MediumGray else White,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Medium,
                                        minLines = 2
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf("+", "-", "×", "÷", "(", ")").forEach { op ->
                                    OperatorButton(
                                        operator = op,
                                        modifier = Modifier.weight(1f),
                                        onClick = {
                                            val actualOp = when (op) {
                                                "×" -> "*"
                                                "÷" -> "/"
                                                else -> op
                                            }
                                            expression += actualOp
                                        }
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        if (expression.isNotEmpty()) {
                                            expression = expression.dropLast(1)
                                        }
                                        usedNumberIndices = recalcUsedIndices(expression, mockNumbers)
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, MediumGray)
                                ) {
                                    Icon(Icons.Default.Backspace, null, tint = LightGray, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Briši", color = LightGray, style = MaterialTheme.typography.labelMedium)
                                }
                                OutlinedButton(
                                    onClick = {
                                        expression = ""
                                        usedNumberIndices = emptySet()
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, ErrorRed.copy(alpha = 0.5f))
                                ) {
                                    Icon(Icons.Default.Clear, null, tint = ErrorRed, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Poništi", color = ErrorRed, style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }
                }

                Surface(color = NavyLight, shadowElevation = 8.dp) {
                    Button(
                        onClick = {
                            if (expressionResult == MOCK_TARGET) {
                                myScore += 10
                            }
                            roundSubmitted = true
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                            .navigationBarsPadding()
                            .height(52.dp),
                        enabled = numbersRevealed && expression.isNotBlank() && !roundSubmitted,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (expressionResult == MOCK_TARGET) SuccessGreen else PrimaryBlueBright
                        )
                    ) {
                        Icon(
                            if (expressionResult == MOCK_TARGET) Icons.Default.CheckCircle else Icons.Default.Send,
                            null,
                            tint = White,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (expressionResult == MOCK_TARGET) "TAČNO! POTVRDI" else "POTVRDI",
                            fontWeight = FontWeight.Bold,
                            color = White
                        )
                    }
                }
                }
            }
        }
    }
}

@Composable
private fun NumberButton(number: Int, isUsed: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .height(52.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (isUsed) DarkGray else NavyCard)
            .border(1.5.dp, if (isUsed) DarkGray else PrimaryBlue.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
            .clickable(enabled = !isUsed, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "$number",
            color = if (isUsed) MediumGray else White,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp
        )
    }
}

@Composable
private fun OperatorButton(operator: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(NavyCardLight)
            .border(1.dp, MediumGray, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(operator, color = Gold, fontWeight = FontWeight.Bold, fontSize = 18.sp)
    }
}

private fun evalExpression(expr: String): Int {
    return expr.trim().toIntOrNull() ?: 0
}

private fun recalcUsedIndices(expression: String, numbers: List<Int>): Set<Int> {
    return emptySet()
}
