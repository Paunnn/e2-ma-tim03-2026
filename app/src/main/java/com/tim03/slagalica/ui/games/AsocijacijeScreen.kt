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
import kotlinx.coroutines.delay
import com.tim03.slagalica.ui.theme.*

private data class Column(val clues: List<String>, val solution: String)

private val mockColumns = listOf(
    Column(listOf("Beograd", "Pariz", "London", "Berlin"), "Prestonica"),
    Column(listOf("Fudbal", "Košarka", "Tenis", "Odbojka"), "Sport"),
    Column(listOf("Jabuka", "Banana", "Grozđe", "Narandža"), "Voće"),
    Column(listOf("Pas", "Mačka", "Zec", "Hrčak"), "Ljubimac")
)
private const val FINAL_SOLUTION = "KATEGORIJE"

private val columnColors = listOf(
    Color(0xFF1565C0),
    Color(0xFF2E7D32),
    Color(0xFF6A1B9A),
    Color(0xFFE65100)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AsocijacijeScreen(onExitClick: () -> Unit) {
    var revealed by remember { mutableStateOf(Array(4) { BooleanArray(4) { false } }) }
    var columnSolved by remember { mutableStateOf(BooleanArray(4) { false }) }
    var finalSolved by remember { mutableStateOf(false) }
    var answer by remember { mutableStateOf("") }
    var currentRound by remember { mutableStateOf(1) }
    var myScore by remember { mutableStateOf(0) }
    var opponentScore by remember { mutableStateOf(18) }
    var timeLeft by remember { mutableStateOf(90) }
    var isMyTurn by remember { mutableStateOf(true) }

    LaunchedEffect(currentRound, isMyTurn) {
        timeLeft = 90
        while (timeLeft > 0) {
            delay(1000)
            timeLeft--
        }
    }

    val timerColor = when {
        timeLeft > 60 -> TimerGreen
        timeLeft > 30 -> TimerYellow
        else -> TimerRed
    }

    Scaffold(
        containerColor = Navy,
        topBar = {
            TopAppBar(
                title = { Text("ASOCIJACIJE", fontWeight = FontWeight.ExtraBold, color = White, letterSpacing = 1.sp, fontSize = 16.sp) },
                navigationIcon = { IconButton(onClick = onExitClick) { Icon(Icons.Default.Close, null, tint = LightGray) } },
                actions = {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(modifier = Modifier.background(NavyLight)) {
                LinearProgressIndicator(
                    progress = timeLeft / 120f,
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                    color = timerColor, trackColor = DarkGray
                )
                Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), contentAlignment = Alignment.Center) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Default.Timer, null, tint = timerColor, modifier = Modifier.size(16.dp))
                        Text("$timeLeft s", color = timerColor, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().background(NavyCard).padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Runda $currentRound / 2", color = Gold, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                Surface(shape = RoundedCornerShape(8.dp), color = if (isMyTurn) PrimaryBlue else DarkGray) {
                    Text(if (isMyTurn) "  Tvoj red  " else "  Čekaš  ", color = if (isMyTurn) White else LightGray,
                        style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().background(NavyLight).padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
            ) {
                PlayerChip(name = "Ti", score = myScore, isActive = true)
                Text("VS", color = MediumGray, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                PlayerChip(name = "Protivnik", score = opponentScore, isActive = false)
            }

            Column(
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
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

                (0..3).forEach { row ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        (0..3).forEach { col ->
                            val isRevealed = revealed[col][row]
                            val isSolved = columnSolved[col]
                            ClueCell(
                                text = if (isRevealed || isSolved) mockColumns[col].clues[row] else "?",
                                isRevealed = isRevealed || isSolved,
                                color = columnColors[col],
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    if (!isRevealed && !isSolved && isMyTurn) {
                                        val newRevealed = Array(4) { c -> BooleanArray(4) { r -> revealed[c][r] } }
                                        newRevealed[col][row] = true
                                        revealed = newRevealed
                                    }
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (0..3).forEach { col ->
                        SolutionCell(
                            text = if (columnSolved[col]) mockColumns[col].solution else "Rešenje ${('A' + col)}",
                            isSolved = columnSolved[col],
                            color = columnColors[col],
                            modifier = Modifier.weight(1f),
                            onClick = {
                                if (!columnSolved[col] && isMyTurn) {
                                    val newSolved = columnSolved.copyOf()
                                    newSolved[col] = true
                                    columnSolved = newSolved
                                }
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Card(
                    modifier = Modifier.fillMaxWidth().clickable(enabled = !finalSolved && isMyTurn) {
                        if (!finalSolved) finalSolved = true
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (finalSolved) Gold.copy(alpha = 0.2f) else NavyCard
                    ),
                    border = androidx.compose.foundation.BorderStroke(if (finalSolved) 2.dp else 1.dp, if (finalSolved) Gold else MediumGray)
                ) {
                    Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Konačno rešenje", color = if (finalSolved) Gold else LightGray, style = MaterialTheme.typography.labelSmall)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (finalSolved) FINAL_SOLUTION else "???",
                                color = if (finalSolved) Gold else MediumGray,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = if (finalSolved) 22.sp else 18.sp
                            )
                        }
                    }
                }
            }

            Surface(color = NavyLight, shadowElevation = 8.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp).navigationBarsPadding(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = answer,
                        onValueChange = { answer = it },
                        placeholder = { Text("Unesite rešenje...", color = MediumGray) },
                        enabled = isMyTurn,
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
                        onClick = {},
                        enabled = isMyTurn && answer.isNotBlank(),
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

@Composable
private fun ClueCell(text: String, isRevealed: Boolean, color: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .height(64.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (isRevealed) color.copy(alpha = 0.15f) else NavyCard)
            .border(1.5.dp, if (isRevealed) color.copy(alpha = 0.6f) else DarkGray, RoundedCornerShape(10.dp))
            .clickable(enabled = !isRevealed, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (isRevealed) {
            Text(text, color = White, fontWeight = FontWeight.SemiBold, fontSize = 10.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(4.dp))
        } else {
            Icon(Icons.Default.Lock, null, tint = MediumGray, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun SolutionCell(text: String, isSolved: Boolean, color: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .height(40.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSolved) color else NavyCard)
            .border(1.5.dp, color.copy(alpha = if (isSolved) 1f else 0.4f), RoundedCornerShape(8.dp))
            .clickable(enabled = !isSolved, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (isSolved) White else color.copy(alpha = 0.5f),
            fontWeight = if (isSolved) FontWeight.Bold else FontWeight.Normal,
            fontSize = 10.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }
}
