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

private data class QuizQuestion(
    val question: String,
    val answers: List<String>,
    val correctIndex: Int
)

private val mockQuestions = listOf(
    QuizQuestion(
        "Koji grad je prestonica Australije?",
        listOf("Sidnej", "Melburn", "Kanbera", "Brizben"),
        correctIndex = 2
    ),
    QuizQuestion(
        "Ko je napisao 'Hamlet'?",
        listOf("Čarls Dikens", "Vilijam Šekspir", "Džon Milton", "Džejn Ostin"),
        correctIndex = 1
    ),
    QuizQuestion(
        "Koliko strana ima kocka?",
        listOf("4", "6", "8", "12"),
        correctIndex = 1
    ),
    QuizQuestion(
        "Koji element ima hemijski simbol 'Au'?",
        listOf("Srebro", "Aluminijum", "Zlato", "Bakar"),
        correctIndex = 2
    ),
    QuizQuestion(
        "Koja planeta je najbliža Suncu?",
        listOf("Venera", "Mars", "Merkur", "Zemlja"),
        correctIndex = 2
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KoZnaZnaScreen(onExitClick: () -> Unit) {
    var currentQuestionIndex by remember { mutableStateOf(0) }
    var selectedAnswerIndex by remember { mutableStateOf<Int?>(null) }
    var answeredQuestions by remember { mutableStateOf(setOf<Int>()) }
    var myScore by remember { mutableStateOf(0) }
    var opponentScore by remember { mutableStateOf(0) }
    var questionTimeLeft by remember { mutableStateOf(5) }
    var totalTimeLeft by remember { mutableStateOf(25) }
    var showResult by remember { mutableStateOf(false) }
    var lastResultCorrect by remember { mutableStateOf<Boolean?>(null) }
    var gameOver by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (totalTimeLeft > 0 && !gameOver) {
            delay(1000L)
            totalTimeLeft--
        }
        gameOver = true
    }

    LaunchedEffect(currentQuestionIndex, gameOver) {
        if (gameOver) return@LaunchedEffect
        questionTimeLeft = 5
        while (questionTimeLeft > 0 && currentQuestionIndex !in answeredQuestions && !gameOver) {
            delay(1000L)
            questionTimeLeft--
        }
        if (!gameOver && currentQuestionIndex !in answeredQuestions) {
            // Time's up — skip question (no penalty per spec)
            if (currentQuestionIndex < mockQuestions.size - 1) {
                currentQuestionIndex++
                selectedAnswerIndex = null
                showResult = false
            } else {
                gameOver = true
            }
        }
    }

    LaunchedEffect(answeredQuestions.size) {
        if (answeredQuestions.size == mockQuestions.size) {
            delay(1200L)
            gameOver = true
        }
    }

    val timerColor = when {
        questionTimeLeft > 3 -> TimerGreen
        questionTimeLeft > 1 -> TimerYellow
        else -> TimerRed
    }
    val totalTimerColor = when {
        totalTimeLeft > 15 -> TimerGreen
        totalTimeLeft > 8 -> TimerYellow
        else -> TimerRed
    }

    val currentQuestion = mockQuestions.getOrNull(currentQuestionIndex)

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
                            "KO ZNA ZNA",
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
                            Text("142", color = White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
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
                    TotalTimerBar(timeLeft = totalTimeLeft, totalTime = 25, timerColor = totalTimerColor)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(NavyCard)
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Pitanje ${currentQuestionIndex + 1} / ${mockQuestions.size}",
                            color = Gold,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        QuestionTimerChip(timeLeft = questionTimeLeft, timerColor = timerColor)
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.horizontalGradient(
                                    listOf(PrimaryBlue.copy(alpha = 0.3f), Navy, WarningOrange.copy(alpha = 0.15f))
                                )
                            )
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
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        QuestionProgressRow(
                            total = mockQuestions.size,
                            answered = answeredQuestions,
                            current = currentQuestionIndex
                        )

                        if (currentQuestion != null) {
                            QuestionCard(question = currentQuestion.question)

                            Spacer(modifier = Modifier.height(4.dp))

                            currentQuestion.answers.forEachIndexed { index, answer ->
                                val isSelected = selectedAnswerIndex == index
                                val isAnswered = currentQuestionIndex in answeredQuestions
                                val isCorrect = index == currentQuestion.correctIndex
                                val answerState = when {
                                    !isAnswered -> AnswerState.IDLE
                                    isCorrect -> AnswerState.CORRECT
                                    isSelected && !isCorrect -> AnswerState.WRONG
                                    else -> AnswerState.IDLE
                                }
                                AnswerButton(
                                    label = ('A' + index).toString(),
                                    text = answer,
                                    state = answerState,
                                    enabled = !isAnswered,
                                    onClick = {
                                        selectedAnswerIndex = index
                                        answeredQuestions = answeredQuestions + currentQuestionIndex
                                        lastResultCorrect = (index == currentQuestion.correctIndex)
                                        if (index == currentQuestion.correctIndex) {
                                            myScore += 10
                                        } else {
                                            myScore -= 5
                                        }
                                        showResult = true
                                    }
                                )
                            }
                        }

                        AnimatedVisibility(visible = showResult && currentQuestionIndex in answeredQuestions) {
                            ResultBanner(correct = lastResultCorrect == true)
                        }

                        if (currentQuestionIndex in answeredQuestions) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Button(
                                onClick = {
                                    if (currentQuestionIndex < mockQuestions.size - 1) {
                                        currentQuestionIndex++
                                        selectedAnswerIndex = null
                                        showResult = false
                                    } else {
                                        gameOver = true
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlueBright)
                            ) {
                                Text(
                                    if (currentQuestionIndex < mockQuestions.size - 1) "SLEDEĆE PITANJE" else "ZAVRŠI",
                                    fontWeight = FontWeight.Bold,
                                    color = White
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(Icons.Default.ArrowForward, null, tint = White, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

private enum class AnswerState { IDLE, CORRECT, WRONG }

@Composable
private fun TotalTimerBar(timeLeft: Int, totalTime: Int, timerColor: Color) {
    Column(modifier = Modifier.background(NavyLight)) {
        LinearProgressIndicator(
            progress = timeLeft.toFloat() / totalTime.toFloat(),
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp),
            color = timerColor,
            trackColor = DarkGray
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 5.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(Icons.Default.Timer, null, tint = timerColor, modifier = Modifier.size(14.dp))
                Text("$timeLeft s ukupno", color = timerColor, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun QuestionTimerChip(timeLeft: Int, timerColor: Color) {
    Surface(shape = RoundedCornerShape(20.dp), color = timerColor.copy(alpha = 0.15f)) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(Icons.Default.HourglassBottom, null, tint = timerColor, modifier = Modifier.size(14.dp))
            Text("$timeLeft s", color = timerColor, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun QuestionProgressRow(total: Int, answered: Set<Int>, current: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(total) { index ->
            val isAnswered = index in answered
            val isCurrent = index == current
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(
                        when {
                            isAnswered -> SuccessGreen
                            isCurrent -> PrimaryBlueBright
                            else -> DarkGray
                        }
                    )
            )
        }
    }
}

@Composable
private fun QuestionCard(question: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = NavyCard),
        border = androidx.compose.foundation.BorderStroke(1.dp, PrimaryBlue.copy(alpha = 0.4f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(PrimaryBlue.copy(alpha = 0.12f), Color.Transparent)))
                .padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Quiz, null, tint = Gold, modifier = Modifier.size(28.dp))
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    question,
                    color = White,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun AnswerButton(
    label: String,
    text: String,
    state: AnswerState,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val bgColor = when (state) {
        AnswerState.CORRECT -> SuccessGreen.copy(alpha = 0.2f)
        AnswerState.WRONG -> ErrorRed.copy(alpha = 0.2f)
        AnswerState.IDLE -> NavyCard
    }
    val borderColor = when (state) {
        AnswerState.CORRECT -> SuccessGreen
        AnswerState.WRONG -> ErrorRed
        AnswerState.IDLE -> MediumGray.copy(alpha = 0.4f)
    }
    val labelBgColor = when (state) {
        AnswerState.CORRECT -> SuccessGreen
        AnswerState.WRONG -> ErrorRed
        AnswerState.IDLE -> PrimaryBlue
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, borderColor)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(labelBgColor),
                contentAlignment = Alignment.Center
            ) {
                Text(label, color = White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
            Text(
                text,
                color = when (state) {
                    AnswerState.CORRECT -> SuccessGreen
                    AnswerState.WRONG -> ErrorRed
                    AnswerState.IDLE -> White
                },
                fontWeight = FontWeight.Medium,
                style = MaterialTheme.typography.bodyMedium
            )
            if (state == AnswerState.CORRECT) {
                Spacer(modifier = Modifier.weight(1f))
                Icon(Icons.Default.CheckCircle, null, tint = SuccessGreen, modifier = Modifier.size(20.dp))
            } else if (state == AnswerState.WRONG) {
                Spacer(modifier = Modifier.weight(1f))
                Icon(Icons.Default.Cancel, null, tint = ErrorRed, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun ResultBanner(correct: Boolean) {
    val bgColor = if (correct) SuccessGreen.copy(alpha = 0.15f) else ErrorRed.copy(alpha = 0.15f)
    val tint = if (correct) SuccessGreen else ErrorRed
    val icon = if (correct) Icons.Default.CheckCircle else Icons.Default.Cancel
    val text = if (correct) "+10 bodova! Tačno!" else "-5 bodova. Netačno!"
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        border = androidx.compose.foundation.BorderStroke(1.dp, tint.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
            Text(text, color = tint, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
