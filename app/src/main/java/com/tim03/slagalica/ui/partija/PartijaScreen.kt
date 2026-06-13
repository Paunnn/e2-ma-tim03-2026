package com.tim03.slagalica.ui.partija

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tim03.slagalica.ui.games.*
import com.tim03.slagalica.ui.theme.*
import com.tim03.slagalica.viewmodel.PartijaGame
import com.tim03.slagalica.viewmodel.PartijaViewModel

@Composable
fun PartijaScreen(
    onExit: () -> Unit,
    viewModel: PartijaViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    if (state.isComplete) {
        LaunchedEffect(Unit) { viewModel.saveResult() }
        Box(modifier = Modifier.fillMaxSize().background(Navy)) {
            GameOverContent(
                myScore = state.myTotal,
                opponentScore = state.oppTotal,
                onFinish = onExit
            )
        }
        return
    }

    val onComplete = { my: Int, opp: Int -> viewModel.gameCompleted(my, opp) }
    val myOffset = state.myTotal
    val oppOffset = state.oppTotal

    when (PartijaViewModel.GAME_ORDER[state.currentGameIndex]) {
        PartijaGame.KO_ZNA_ZNA -> KoZnaZnaScreen(
            onExitClick = onExit,
            isPartijaMode = true,
            onPartijaGameComplete = onComplete,
            myScoreOffset = myOffset,
            oppScoreOffset = oppOffset
        )
        PartijaGame.SPOJNICE -> SpojniceScreen(
            onExitClick = onExit,
            isPartijaMode = true,
            onPartijaGameComplete = onComplete,
            myScoreOffset = myOffset,
            oppScoreOffset = oppOffset
        )
        PartijaGame.MOJ_BROJ -> MojBrojScreen(
            onExitClick = onExit,
            isPartijaMode = true,
            onPartijaGameComplete = onComplete,
            myScoreOffset = myOffset,
            oppScoreOffset = oppOffset
        )
        PartijaGame.KORAK_PO_KORAK -> KorakPoKorakScreen(
            onExitClick = onExit,
            isPartijaMode = true,
            onPartijaGameComplete = onComplete,
            myScoreOffset = myOffset,
            oppScoreOffset = oppOffset
        )
        PartijaGame.ASOCIJACIJE -> AsocijacijeScreen(
            onExitClick = onExit,
            isPartijaMode = true,
            onPartijaGameComplete = onComplete,
            myScoreOffset = myOffset,
            oppScoreOffset = oppOffset
        )
        PartijaGame.SKOCKO -> SkockoScreen(
            onExitClick = onExit,
            isPartijaMode = true,
            onPartijaGameComplete = onComplete,
            myScoreOffset = myOffset,
            oppScoreOffset = oppOffset
        )
    }
}
