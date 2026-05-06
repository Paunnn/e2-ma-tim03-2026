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

private data class SpojniceRound(
    val criterion: String,
    val leftItems: List<String>,
    val rightItems: List<String>,
    val correctMapping: Map<Int, Int>
)

private val mockRounds = listOf(
    SpojniceRound(
        criterion = "Povežite izvođača sa pesmom",
        leftItems = listOf("Zdravko Čolić", "Bijelo Dugme", "Riblja Čorba", "Oliver Dragojević", "Tose Proeski"),
        rightItems = listOf("Kad bi bio bijelo dugme", "Kurvini sinovi", "Galeb i ja", "Ako me pitaš zašto", "Tvoja"),
        correctMapping = mapOf(0 to 0, 1 to 1, 2 to 2, 3 to 3, 4 to 4)
    ),
    SpojniceRound(
        criterion = "Povežite prestonicu sa državom",
        leftItems = listOf("Pariz", "Tokio", "Kairo", "Ottawa", "Brasilia"),
        rightItems = listOf("Kanada", "Egipat", "Francuska", "Japan", "Brazil"),
        correctMapping = mapOf(0 to 2, 1 to 3, 2 to 1, 3 to 0, 4 to 4)
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpojniceScreen(onExitClick: () -> Unit) {
    var currentRound by remember { mutableStateOf(0) }
    var isMyTurn by remember { mutableStateOf(true) }
    var timeLeft by remember { mutableStateOf(30) }
    var myScore by remember { mutableStateOf(0) }
    var opponentScore by remember { mutableStateOf(0) }

    var selectedLeft by remember { mutableStateOf<Int?>(null) }
    var connections by remember { mutableStateOf(mapOf<Int, Int>()) }
    var wrongConnections by remember { mutableStateOf(mapOf<Int, Int>()) }
    var showOpponentPhase by remember { mutableStateOf(false) }
    var gameOver by remember { mutableStateOf(false) }

    LaunchedEffect(currentRound, showOpponentPhase, gameOver) {
        if (gameOver) return@LaunchedEffect
        timeLeft = 30
        while (timeLeft > 0 && !gameOver) {
            delay(1000L)
            timeLeft--
        }
        if (!gameOver) {
            if (!showOpponentPhase) {
                showOpponentPhase = true
            } else {
                if (currentRound < mockRounds.size - 1) {
                    currentRound++
                    isMyTurn = false
                    showOpponentPhase = false
                    connections = emptyMap()
                    wrongConnections = emptyMap()
                    selectedLeft = null
                } else {
                    gameOver = true
                }
            }
        }
    }

    val round = mockRounds.getOrNull(currentRound)
    val connectedLeft = connections.keys
    val connectedRight = connections.values.toSet()
    val wrongLeft = wrongConnections.keys
    val usedLeft = connectedLeft + wrongLeft
    val usedRight = connectedRight  // wrong right items stay available
    val unconnectedCount = 5 - connections.size - wrongConnections.size
    val timerColor = when {
        timeLeft > 20 -> TimerGreen
        timeLeft > 10 -> TimerYellow
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
                            "SPOJNICE",
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
                Column(modifier = Modifier.background(NavyLight)) {
                    LinearProgressIndicator(
                        progress = timeLeft.toFloat() / 30f,
                        modifier = Modifier.fillMaxWidth().height(6.dp),
                        color = timerColor,
                        trackColor = DarkGray
                    )
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Default.Timer, null, tint = timerColor, modifier = Modifier.size(14.dp))
                            Text("$timeLeft s", color = timerColor, fontWeight = FontWeight.Bold, fontSize = 13.sp)
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
                    Text(
                        "Runda ${currentRound + 1} / 2",
                        color = Gold,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isMyTurn && !showOpponentPhase) PrimaryBlue else DarkGray
                    ) {
                        Text(
                            when {
                                showOpponentPhase -> "  Tvoj red (bonus)  "
                                isMyTurn -> "  Tvoj red  "
                                else -> "  Protivnikov red  "
                            },
                            color = if (isMyTurn || showOpponentPhase) White else LightGray,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
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
                    if (round != null) {
                        CriterionCard(criterion = round.criterion)

                        ScoreChips(
                            connected = connections.size,
                            wrong = wrongConnections.size,
                            total = 5,
                            roundScore = connections.size * 2
                        )

                        if (selectedLeft != null) {
                            SelectionHintCard(leftItem = round.leftItems[selectedLeft!!])
                        }

                        ConnectingGrid(
                            leftItems = round.leftItems,
                            rightItems = round.rightItems,
                            connections = connections,
                            wrongConnections = wrongConnections,
                            selectedLeft = selectedLeft,
                            usedLeft = usedLeft,
                            usedRight = usedRight,
                            enabled = isMyTurn || showOpponentPhase,
                            onLeftClick = { index ->
                                if (index !in usedLeft) {
                                    selectedLeft = if (selectedLeft == index) null else index
                                }
                            },
                            onRightClick = { rightIndex ->
                                val left = selectedLeft
                                if (left != null && rightIndex !in usedRight) {
                                    val isCorrect = round.correctMapping[left] == rightIndex
                                    if (isCorrect) {
                                        connections = connections + (left to rightIndex)
                                        myScore += 2
                                    } else {
                                        wrongConnections = wrongConnections + (left to rightIndex)
                                    }
                                    selectedLeft = null
                                }
                            }
                        )

                        if (showOpponentPhase) {
                            OpponentPhaseBanner(unconnectedCount = unconnectedCount)
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Button(
                            onClick = {
                                if (!showOpponentPhase && isMyTurn) {
                                    showOpponentPhase = true
                                    timeLeft = 30
                                } else {
                                    if (currentRound < mockRounds.size - 1) {
                                        currentRound++
                                        isMyTurn = false
                                        showOpponentPhase = false
                                        connections = emptyMap()
                                        wrongConnections = emptyMap()
                                        selectedLeft = null
                                        timeLeft = 30
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlueBright)
                        ) {
                            val label = when {
                                !showOpponentPhase && connections.size < 5 -> "PREDAJ — protivnik pokušava"
                                showOpponentPhase || connections.size == 5 -> if (currentRound < mockRounds.size - 1) "SLEDEĆA RUNDA" else "ZAVRŠI"
                                else -> "DALJE"
                            }
                            Text(label, fontWeight = FontWeight.Bold, color = White)
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

@Composable
private fun CriterionCard(criterion: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = PrimaryBlue.copy(alpha = 0.15f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, PrimaryBlue.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(Icons.Default.Link, null, tint = PrimaryBlueLight, modifier = Modifier.size(20.dp))
            Text(
                criterion,
                color = White,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun ScoreChips(connected: Int, wrong: Int, total: Int, roundScore: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(shape = RoundedCornerShape(20.dp), color = SuccessGreen.copy(alpha = 0.15f), modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Default.CheckCircle, null, tint = SuccessGreen, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("$connected / $total tačno", color = SuccessGreen, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            }
        }
        if (wrong > 0) {
            Surface(shape = RoundedCornerShape(20.dp), color = ErrorRed.copy(alpha = 0.15f), modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Default.Cancel, null, tint = ErrorRed, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("$wrong promašeno", color = ErrorRed, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
            }
        } else {
            Surface(shape = RoundedCornerShape(20.dp), color = Gold.copy(alpha = 0.15f), modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Default.Star, null, tint = Gold, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("+$roundScore bodova", color = Gold, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun SelectionHintCard(leftItem: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Gold.copy(alpha = 0.12f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Gold.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.Default.TouchApp, null, tint = Gold, modifier = Modifier.size(16.dp))
            Text(
                "Odabrano: \"$leftItem\" — tapnite pojam sa desne strane",
                color = Gold,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}


@Composable
private fun ConnectingGrid(
    leftItems: List<String>,
    rightItems: List<String>,
    connections: Map<Int, Int>,
    wrongConnections: Map<Int, Int>,
    selectedLeft: Int?,
    usedLeft: Set<Int>,
    usedRight: Set<Int>,
    enabled: Boolean,
    onLeftClick: (Int) -> Unit,
    onRightClick: (Int) -> Unit
) {
    val indexLetters = listOf("A", "B", "C", "D", "E")
    val wrongLeft = wrongConnections.keys

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            leftItems.forEachIndexed { index, item ->
                val isCorrect = index in connections.keys
                val isWrong = index in wrongLeft
                val isSelected = selectedLeft == index

                ConnectItemCard(
                    text = item,
                    badge = "${index + 1}",
                    isCorrect = isCorrect,
                    isWrong = isWrong,
                    isSelected = isSelected,
                    enabled = enabled && index !in usedLeft,
                    side = CardSide.LEFT,
                    onClick = { onLeftClick(index) }
                )
            }
        }

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            rightItems.forEachIndexed { index, item ->
                val isCorrect = index in connections.values

                ConnectItemCard(
                    text = item,
                    badge = indexLetters[index],
                    isCorrect = isCorrect,
                    isWrong = false,
                    isSelected = false,
                    enabled = enabled && index !in usedRight && selectedLeft != null,
                    side = CardSide.RIGHT,
                    onClick = { onRightClick(index) }
                )
            }
        }
    }
}

private enum class CardSide { LEFT, RIGHT }

@Composable
private fun ConnectItemCard(
    text: String,
    badge: String,
    isCorrect: Boolean,
    isWrong: Boolean,
    isSelected: Boolean,
    enabled: Boolean,
    side: CardSide,
    onClick: () -> Unit
) {
    val bgColor = when {
        isCorrect -> SuccessGreen.copy(alpha = 0.15f)
        isWrong -> ErrorRed.copy(alpha = 0.15f)
        isSelected -> Gold.copy(alpha = 0.15f)
        else -> NavyCard
    }
    val borderColor = when {
        isCorrect -> SuccessGreen.copy(alpha = 0.7f)
        isWrong -> ErrorRed.copy(alpha = 0.6f)
        isSelected -> Gold
        else -> MediumGray.copy(alpha = 0.3f)
    }
    val badgeBg = when {
        isCorrect -> SuccessGreen
        isWrong -> ErrorRed
        isSelected -> Gold
        else -> if (side == CardSide.LEFT) PrimaryBlue else NavyCardLight
    }
    val textColor = when {
        isCorrect -> SuccessGreen
        isWrong -> ErrorRed
        isSelected -> Gold
        !enabled -> MediumGray
        else -> White
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, borderColor)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(badgeBg),
                    contentAlignment = Alignment.Center
                ) {
                    if (isWrong) {
                        Icon(Icons.Default.Close, null, tint = White, modifier = Modifier.size(12.dp))
                    } else {
                        Text(badge, color = White, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                    }
                }
                Text(
                    text,
                    color = textColor,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = if (isSelected || isCorrect || isWrong) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 2
                )
            }
        }
    }
}

@Composable
private fun OpponentPhaseBanner(unconnectedCount: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = WarningOrange.copy(alpha = 0.12f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, WarningOrange.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.Default.Info, null, tint = WarningOrange, modifier = Modifier.size(18.dp))
            Column {
                Text(
                    "Protivnik nije završio!",
                    color = WarningOrange,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    "Imate 30s da povežete preostalih $unconnectedCount pojmova.",
                    color = WarningOrange.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}
