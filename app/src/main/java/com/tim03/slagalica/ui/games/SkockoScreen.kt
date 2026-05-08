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

private data class SkockoSymbol(val label: String, val emoji: String, val color: Color)

private val symbols = listOf(
    SkockoSymbol("SK", "⬡", Color(0xFF9C27B0)),
    SkockoSymbol("□", "□", Color(0xFF1565C0)),
    SkockoSymbol("●", "●", Color(0xFF2E7D32)),
    SkockoSymbol("♥", "♥", Color(0xFFE53935)),
    SkockoSymbol("▲", "▲", Color(0xFFF57C00)),
    SkockoSymbol("★", "★", Color(0xFFFDD835))
)

private data class AttemptRow(
    val symbols: List<Int?>,
    val correctPos: Int = 0,
    val correctSym: Int = 0
)

private val mockAttempts = listOf(
    AttemptRow(listOf(3, 5, 1, 2), correctPos = 1, correctSym = 2),
    AttemptRow(listOf(0, 3, 5, 1), correctPos = 0, correctSym = 3),
    AttemptRow(listOf(2, 0, 3, 5), correctPos = 2, correctSym = 1)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkockoScreen(onExitClick: () -> Unit) {
    var currentAttempt by remember { mutableStateOf(listOf<Int?>()) }
    var currentRound by remember { mutableStateOf(1) }
    var myScore by remember { mutableStateOf(0) }
    var opponentScore by remember { mutableStateOf(15) }
    var timeLeft by remember { mutableStateOf(22) }
    var isMyTurn by remember { mutableStateOf(true) }

    LaunchedEffect(currentRound, isMyTurn) {
        timeLeft = 22
        while (timeLeft > 0) {
            delay(1000)
            timeLeft--
        }
    }

    val timerColor = when {
        timeLeft > 20 -> TimerGreen
        timeLeft > 10 -> TimerYellow
        else -> TimerRed
    }

    Scaffold(
        containerColor = Navy,
        topBar = {
            TopAppBar(
                title = { Text("SKOČKO", fontWeight = FontWeight.ExtraBold, color = White, letterSpacing = 1.sp, fontSize = 16.sp) },
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
                    progress = timeLeft / 30f,
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
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = NavyCard)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Bodovanje", color = LightGray, style = MaterialTheme.typography.labelSmall)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ScoreBadge("1-2. pokušaj", "20", SuccessGreen)
                                ScoreBadge("3-4. pokušaj", "15", TimerYellow)
                                ScoreBadge("5-6. pokušaj", "10", TimerRed)
                            }
                        }
                    }
                }

                Text("Pokušaji", color = LightGray, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)

                (0..5).forEach { rowIndex ->
                    val attempt = mockAttempts.getOrNull(rowIndex)
                    val isCurrent = rowIndex == mockAttempts.size && isMyTurn

                    AttemptRowView(
                        rowIndex = rowIndex,
                        attempt = attempt,
                        isCurrent = isCurrent,
                        currentSymbols = if (isCurrent) currentAttempt else null
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text("Odaberite simbole", color = LightGray, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    symbols.forEachIndexed { index, symbol ->
                        SymbolButton(
                            symbol = symbol,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                if (currentAttempt.size < 4) {
                                    currentAttempt = currentAttempt + index
                                }
                            }
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { if (currentAttempt.isNotEmpty()) currentAttempt = currentAttempt.dropLast(1) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MediumGray)
                    ) {
                        Icon(Icons.Default.Backspace, null, tint = LightGray, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Briši", color = LightGray)
                    }
                    OutlinedButton(
                        onClick = { currentAttempt = emptyList() },
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

            Surface(color = NavyLight, shadowElevation = 8.dp) {
                Button(
                    onClick = { currentAttempt = emptyList() },
                    modifier = Modifier.fillMaxWidth().padding(12.dp).navigationBarsPadding().height(52.dp),
                    enabled = isMyTurn && currentAttempt.size == 4,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlueBright)
                ) {
                    Text("POTVRDI POKUŠAJ", fontWeight = FontWeight.Bold, color = White)
                }
            }
        }
    }
}

@Composable
private fun AttemptRowView(rowIndex: Int, attempt: AttemptRow?, isCurrent: Boolean, currentSymbols: List<Int?>?) {
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
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(bgColor).border(1.5.dp, borderColor, RoundedCornerShape(12.dp)).padding(10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (0..3).forEach { col ->
                val symIndex = attempt?.symbols?.getOrNull(col) ?: currentSymbols?.getOrNull(col)
                AttemptSlot(
                    symbolIndex = symIndex,
                    isEmpty = symIndex == null,
                    isCurrent = isCurrent
                )
            }
        }

        if (attempt != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                repeat(attempt.correctPos) {
                    Box(modifier = Modifier.size(14.dp).clip(CircleShape).background(SuccessGreen))
                }
                repeat(attempt.correctSym) {
                    Box(modifier = Modifier.size(14.dp).clip(CircleShape).border(2.dp, WarningOrange, CircleShape))
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
private fun AttemptSlot(symbolIndex: Int?, isEmpty: Boolean, isCurrent: Boolean) {
    val sym = symbolIndex?.let { symbols.getOrNull(it) }
    Box(
        modifier = Modifier.size(44.dp).clip(RoundedCornerShape(8.dp))
            .background(sym?.color?.copy(alpha = 0.15f) ?: if (isCurrent) NavyCardLight else NavyLight)
            .border(1.5.dp, sym?.color?.copy(alpha = 0.6f) ?: if (isCurrent) MediumGray else DarkGray, RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (sym != null) {
            Text(sym.emoji, color = sym.color, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        }
    }
}

@Composable
private fun SymbolButton(symbol: SkockoSymbol, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier.height(52.dp).clip(RoundedCornerShape(10.dp))
            .background(symbol.color.copy(alpha = 0.15f))
            .border(1.5.dp, symbol.color.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(symbol.emoji, color = symbol.color, fontWeight = FontWeight.Bold, fontSize = 22.sp)
    }
}

@Composable
private fun ScoreBadge(label: String, points: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(points + " bod.", color = color, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        Text(label, color = MediumGray, fontSize = 9.sp)
    }
}
