package com.tim03.slagalica.ui.partija

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tim03.slagalica.ui.theme.*
import com.tim03.slagalica.viewmodel.MatchmakingStatus
import com.tim03.slagalica.viewmodel.MatchmakingViewModel
import kotlinx.coroutines.delay

@Composable
fun MatchmakingScreen(
    onMatchFound: (sessionId: String, isPlayer1: Boolean) -> Unit,
    onCancel: () -> Unit,
    viewModel: MatchmakingViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.status) {
        when (state.status) {
            MatchmakingStatus.MATCHED -> {
                delay(1500L)
                onMatchFound(state.sessionId, state.isPlayer1)
            }
            MatchmakingStatus.CANCELLED -> onCancel()
            else -> {}
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Navy),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            when (state.status) {
                MatchmakingStatus.SEARCHING -> SearchingContent()
                MatchmakingStatus.MATCHED -> MatchFoundContent(state.opponentName)
                MatchmakingStatus.ERROR -> ErrorContent(state.errorMessage ?: "Greška")
                MatchmakingStatus.CANCELLED -> {}
            }

            if (state.status == MatchmakingStatus.SEARCHING || state.status == MatchmakingStatus.ERROR) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { viewModel.cancel(); onCancel() },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = LightGray),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MediumGray)
                ) {
                    Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (state.status == MatchmakingStatus.ERROR) "Nazad" else "Otkaži")
                }
            }
        }
    }
}

@Composable
private fun SearchingContent() {
    val transition = rememberInfiniteTransition(label = "pulse")
    val scale by transition.animateFloat(
        initialValue = 0.85f, targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Box(
        modifier = Modifier
            .size(120.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(
                Brush.radialGradient(listOf(PrimaryBlueBright.copy(alpha = 0.25f), Color.Transparent))
            ),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            color = PrimaryBlueBright,
            modifier = Modifier.size(80.dp),
            strokeWidth = 4.dp
        )
    }

    Text(
        "Tražimo protivnika...",
        color = White,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 20.sp,
        textAlign = TextAlign.Center
    )
    Text(
        "Čekamo drugog igrača",
        color = MediumGray,
        fontSize = 14.sp,
        textAlign = TextAlign.Center
    )
}

@Composable
private fun MatchFoundContent(opponentName: String) {
    Text("✓", color = TimerGreen, fontSize = 52.sp, fontWeight = FontWeight.ExtraBold)
    Text(
        "Protivnik pronađen!",
        color = White,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 22.sp,
        textAlign = TextAlign.Center
    )
    if (opponentName.isNotEmpty()) {
        Text(
            "vs.  $opponentName",
            color = Gold,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
    Text("Partija počinje...", color = MediumGray, fontSize = 13.sp)
}

@Composable
private fun ErrorContent(message: String) {
    Text("Greška", color = TimerRed, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    Text(message, color = MediumGray, fontSize = 13.sp, textAlign = TextAlign.Center)
}
