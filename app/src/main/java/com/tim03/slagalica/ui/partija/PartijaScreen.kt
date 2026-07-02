package com.tim03.slagalica.ui.partija

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tim03.slagalica.ui.games.*
import com.tim03.slagalica.ui.theme.*
import com.tim03.slagalica.viewmodel.PartijaGame
import com.tim03.slagalica.viewmodel.PartijaViewModel
import com.tim03.slagalica.viewmodel.PartijaViewModelFactory

@Composable
fun PartijaScreen(
    sessionId: String = "",
    isPlayer1: Boolean = true,
    izazovId: String = "",
    onExit: () -> Unit,
    viewModel: PartijaViewModel = viewModel(factory = PartijaViewModelFactory(sessionId, isPlayer1, izazovId = izazovId))
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showForfeitDialog by remember { mutableStateOf(false) }
    val izazovMode = izazovId.isNotEmpty()

    if (showForfeitDialog) {
        ForfeitDialog(
            onConfirm = {
                showForfeitDialog = false
                viewModel.forfeit()
            },
            onDismiss = { showForfeitDialog = false }
        )
    }

    if (state.isComplete) {
        LaunchedEffect(Unit) { viewModel.saveResult() }
        Box(modifier = Modifier.fillMaxSize().background(Navy)) {
            if (izazovMode) {
                IzazovPartijaOverContent(total = state.myTotal, onFinish = onExit)
            } else {
                val forfeitMessage = when {
                    state.forfeitLoss -> "Odustali ste od partije"
                    state.forfeitWon -> "Protivnik je odustao od partije"
                    else -> null
                }
                GameOverContent(
                    myScore = state.myTotal,
                    opponentScore = state.oppTotal,
                    onFinish = onExit,
                    forfeitMessage = forfeitMessage,
                    wonOverride = when {
                        state.forfeitWon -> true
                        state.forfeitLoss -> false
                        else -> null
                    }
                )
            }
        }
        return
    }

    if (state.waitingForOpponent) {
        WaitingForOpponentScreen(
            myTotal = state.myTotal,
            oppTotal = state.oppTotal,
            opponentName = state.opponentName,
            gamesComplete = state.currentGameIndex,
            onForfeit = { showForfeitDialog = true }
        )
        return
    }

    val onForfeitClick = { showForfeitDialog = true }
    val onComplete = { my: Int, opp: Int -> viewModel.gameCompleted(my, opp) }
    val myOffset = state.myTotal
    val oppOffset = state.oppTotal

    val gameIdx = state.currentGameIndex
    val oppName = state.opponentName
    val mpSessionId = viewModel.sessionId
    val mpIsPlayer1 = viewModel.isPlayer1

    when (PartijaViewModel.GAME_ORDER[gameIdx]) {
        PartijaGame.KO_ZNA_ZNA -> KoZnaZnaScreen(
            onExitClick = onForfeitClick,
            isPartijaMode = true,
            onPartijaGameComplete = onComplete,
            myScoreOffset = myOffset,
            oppScoreOffset = oppOffset,
            sessionId = mpSessionId,
            isPlayer1 = mpIsPlayer1,
            gameIdx = gameIdx,
            izazovMode = izazovMode,
            oppName = oppName
        )
        PartijaGame.SPOJNICE -> SpojniceScreen(
            onExitClick = onForfeitClick,
            isPartijaMode = true,
            onPartijaGameComplete = onComplete,
            myScoreOffset = myOffset,
            oppScoreOffset = oppOffset,
            sessionId = mpSessionId,
            isPlayer1 = mpIsPlayer1,
            gameIdx = gameIdx,
            izazovMode = izazovMode,
            oppName = oppName
        )
        PartijaGame.MOJ_BROJ -> MojBrojScreen(
            onExitClick = onForfeitClick,
            isPartijaMode = true,
            onPartijaGameComplete = onComplete,
            myScoreOffset = myOffset,
            oppScoreOffset = oppOffset,
            sessionId = mpSessionId,
            isPlayer1 = mpIsPlayer1,
            gameIdx = gameIdx,
            izazovMode = izazovMode,
            oppName = oppName
        )
        PartijaGame.KORAK_PO_KORAK -> KorakPoKorakScreen(
            onExitClick = onForfeitClick,
            isPartijaMode = true,
            onPartijaGameComplete = onComplete,
            myScoreOffset = myOffset,
            oppScoreOffset = oppOffset,
            sessionId = mpSessionId,
            isPlayer1 = mpIsPlayer1,
            gameIdx = gameIdx,
            izazovMode = izazovMode,
            oppName = oppName
        )
        PartijaGame.ASOCIJACIJE -> AsocijacijeScreen(
            onExitClick = onForfeitClick,
            isPartijaMode = true,
            onPartijaGameComplete = onComplete,
            myScoreOffset = myOffset,
            oppScoreOffset = oppOffset,
            sessionId = mpSessionId,
            isPlayer1 = mpIsPlayer1,
            gameIdx = gameIdx,
            izazovMode = izazovMode,
            oppName = oppName
        )
        PartijaGame.SKOCKO -> SkockoScreen(
            onExitClick = onForfeitClick,
            isPartijaMode = true,
            onPartijaGameComplete = onComplete,
            myScoreOffset = myOffset,
            oppScoreOffset = oppOffset,
            sessionId = mpSessionId,
            isPlayer1 = mpIsPlayer1,
            gameIdx = gameIdx,
            izazovMode = izazovMode,
            oppName = oppName
        )
    }
}

// End screen for a solo izazov partija - no opponent, just the achieved total.
@Composable
private fun IzazovPartijaOverContent(total: Int, onFinish: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("⚔", fontSize = 56.sp)
        Spacer(Modifier.height(16.dp))
        Text("Izazov završen!", color = White, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp)
        Spacer(Modifier.height(20.dp))
        Text("$total", color = Gold, fontWeight = FontWeight.ExtraBold, fontSize = 52.sp)
        Text("bodova", color = LightGray, fontSize = 14.sp)
        Spacer(Modifier.height(16.dp))
        Text(
            "Rezultat je poslat. Pobednik se određuje kada svi učesnici odigraju.",
            color = MediumGray, fontSize = 13.sp, textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(36.dp))
        Button(
            onClick = onFinish,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlueBright)
        ) {
            Text("NAZAD NA IZAZOVE", fontWeight = FontWeight.ExtraBold, color = White, fontSize = 15.sp)
        }
    }
}

@Composable
private fun ForfeitDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NavyCard,
        icon = {
            Icon(Icons.Default.ExitToApp, contentDescription = null, tint = ErrorRed, modifier = Modifier.size(28.dp))
        },
        title = {
            Text("Odustajanje od partije", color = White, fontWeight = FontWeight.Bold)
        },
        text = {
            Text(
                "Da li ste sigurni da želite da odustanete? Bićete proglašeni gubitnikom i partija će biti prekinuta.",
                color = LightGray
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("ODUSTANI", color = ErrorRed, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Nastavi igru", color = PrimaryBlueBright, fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
private fun WaitingForOpponentScreen(
    myTotal: Int,
    oppTotal: Int,
    opponentName: String,
    gamesComplete: Int,
    onForfeit: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Navy),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            CircularProgressIndicator(
                color = PrimaryBlueBright,
                modifier = Modifier.size(64.dp),
                strokeWidth = 4.dp
            )
            Text(
                "Čekanje na $opponentName...",
                color = White,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                textAlign = TextAlign.Center
            )
            Text(
                "Čekanje na prelaz u sledeću igru...",
                color = MediumGray,
                fontSize = 13.sp
            )
            Spacer(Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(32.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Ti", color = MediumGray, fontSize = 12.sp)
                    Text(
                        "$myTotal",
                        color = PrimaryBlueBright,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 28.sp
                    )
                }
                Text("vs", color = MediumGray, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(opponentName, color = MediumGray, fontSize = 12.sp)
                    Text(
                        "$oppTotal",
                        color = Accent2,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 28.sp
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onForfeit) {
                Text("Odustani od partije", color = ErrorRed.copy(alpha = 0.8f), fontSize = 13.sp)
            }
        }
    }
}
