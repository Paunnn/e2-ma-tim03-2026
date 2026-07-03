package com.tim03.slagalica.ui.turnir

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEvents
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
import com.tim03.slagalica.data.model.TurnirSession
import com.tim03.slagalica.ui.theme.*
import com.tim03.slagalica.viewmodel.TurnirPhase
import com.tim03.slagalica.viewmodel.TurnirUiState
import com.tim03.slagalica.viewmodel.TurnirViewModel

@Composable
fun TurnirScreen(
    vm: TurnirViewModel,
    onNavigateToPartija: (sessionId: String, isPlayer1: Boolean, isTournament: Boolean, isFinal: Boolean) -> Unit,
    onExit: () -> Unit
) {
    val state by vm.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.phase, state.mySessionId) {
        val sessionId = state.mySessionId
        val playing = state.phase == TurnirPhase.SEMI_FINAL || state.phase == TurnirPhase.FINAL
        // markNavigated keeps this from re-entering a partija that was already played
        // when the user comes back to this screen after it ends.
        if (sessionId.isNotEmpty() && playing && vm.markNavigated(sessionId)) {
            onNavigateToPartija(sessionId, state.myIsPlayer1, true, state.phase == TurnirPhase.FINAL)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Navy)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        IconButton(
            onClick = { vm.leave(); onExit() },
            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
        ) {
            Icon(Icons.Default.Close, null, tint = LightGray)
        }

        when {
            !state.joined -> TurnirEntryView(error = state.error, onJoin = { vm.joinTournament() })
            state.phase == TurnirPhase.WAITING -> WaitingView(state)
            state.phase == TurnirPhase.SEMI_FINAL -> BracketView(state, "Polufinalista")
            state.phase == TurnirPhase.WAITING_FOR_FINAL -> BracketView(state, "Čeka finale...")
            state.phase == TurnirPhase.FINAL -> BracketView(state, "Finalista!")
            state.phase == TurnirPhase.ELIMINATED -> ResultView(won = false, isRunner = false, onExit = onExit)
            state.phase == TurnirPhase.WINNER -> ResultView(won = true, isRunner = false, onExit = onExit)
            else -> ResultView(won = false, isRunner = true, onExit = onExit)
        }
    }
}

@Composable
private fun TurnirEntryView(error: String?, onJoin: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.EmojiEvents, null, tint = Gold, modifier = Modifier.size(72.dp))
        Spacer(Modifier.height(16.dp))
        Text("TURNIR", color = Gold, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 4.sp)
        Spacer(Modifier.height(8.dp))
        Text("Takmičenje 4 igrača", color = LightGray, fontSize = 14.sp)
        Spacer(Modifier.height(32.dp))
        Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = NavyLight)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Pravila", color = White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(Modifier.height(8.dp))
                listOf(
                    "• Ulaz: 3 tokena",
                    "• 4 igrača → 2 polufinala → finale",
                    "• Pobednik polufinala: +2 tokena",
                    "• Pobednik finala: +3 tokena +10★",
                    "• Odustajanjem gubite tokene"
                ).forEach { Text(it, color = LightGray, fontSize = 12.sp, modifier = Modifier.padding(top = 3.dp)) }
            }
        }
        Spacer(Modifier.height(32.dp))
        Button(
            onClick = onJoin,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Accent5)
        ) {
            Icon(Icons.Default.EmojiEvents, null, tint = Navy, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("UĐI U TURNIR (3 ●)", color = Navy, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
        }
        if (error != null) {
            Spacer(Modifier.height(12.dp))
            Text(error, color = ErrorRed, fontSize = 13.sp, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun WaitingView(state: TurnirUiState) {
    val anim = rememberInfiniteTransition(label = "pulse")
    val scale by anim.animateFloat(
        initialValue = 0.9f, targetValue = 1.1f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label = "scale"
    )

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.EmojiEvents,
            null, tint = Gold,
            modifier = Modifier.size(72.dp)
        )
        Spacer(Modifier.height(24.dp))
        Text("TURNIR", color = Gold, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 4.sp)
        Text("Čeka se 4 igrača", color = LightGray, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
        Spacer(Modifier.height(32.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(4) { idx ->
                val filled = idx < state.queueCount
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(if (filled) PrimaryBlueBright else NavyLight),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (filled) "${idx + 1}" else "?",
                        color = if (filled) White else MediumGray,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        Text(
            "${state.queueCount}/4 igrača u čekaonici",
            color = MediumGray, fontSize = 13.sp
        )
        Spacer(Modifier.height(8.dp))
        CircularProgressIndicator(color = PrimaryBlueBright, modifier = Modifier.size(32.dp))

        Spacer(Modifier.height(32.dp))
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = NavyLight)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Pravila turnira", color = White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(Modifier.height(8.dp))
                listOf(
                    "• Ulaz: 3 tokena",
                    "• Polufinalnom pobedniku: +2 tokena",
                    "• Finalnom pobedniku: +3 tokena +10★",
                    "• 4 igrača → 2 polufinala → finale"
                ).forEach {
                    Text(it, color = LightGray, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
                }
            }
        }
    }
}

@Composable
private fun BracketView(state: TurnirUiState, myStatus: String) {
    val session = state.session ?: return
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("TURNIR - BRACKET", color = Gold, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold,
            letterSpacing = 2.sp, modifier = Modifier.padding(top = 48.dp))
        Text(myStatus, color = PrimaryBlueBright, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))

        Spacer(Modifier.height(32.dp))

        // Semi-finals
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            BracketColumn("Polufinale 1", session.playerNames.getOrNull(0) ?: "?",
                session.playerNames.getOrNull(1) ?: "?", session.semi1Winner,
                session.playerUids.getOrNull(0) ?: "", session.playerUids.getOrNull(1) ?: "",
                state.myUid)
            BracketColumn("Polufinale 2", session.playerNames.getOrNull(2) ?: "?",
                session.playerNames.getOrNull(3) ?: "?", session.semi2Winner,
                session.playerUids.getOrNull(2) ?: "", session.playerUids.getOrNull(3) ?: "",
                state.myUid)
        }

        if (session.finalSessionId.isNotEmpty()) {
            Spacer(Modifier.height(24.dp))
            Divider(color = LineColor, modifier = Modifier.fillMaxWidth(0.6f))
            Spacer(Modifier.height(24.dp))

            Text("FINALE", color = Gold, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.sp)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                PlayerChip(
                    name = session.playerNames.getOrElse(session.playerUids.indexOf(session.semi1Winner)) { session.semi1Winner },
                    isMe = session.semi1Winner == state.myUid,
                    won = session.tournamentWinner == session.semi1Winner
                )
                Text("VS", color = MediumGray, fontWeight = FontWeight.ExtraBold, modifier = Modifier.align(Alignment.CenterVertically))
                PlayerChip(
                    name = session.playerNames.getOrElse(session.playerUids.indexOf(session.semi2Winner)) { session.semi2Winner },
                    isMe = session.semi2Winner == state.myUid,
                    won = session.tournamentWinner == session.semi2Winner
                )
            }
        }
    }
}

@Composable
private fun BracketColumn(
    title: String,
    name1: String, name2: String,
    winnerUid: String,
    uid1: String, uid2: String,
    myUid: String
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, color = MediumGray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        PlayerChip(name1, isMe = uid1 == myUid, won = winnerUid == uid1)
        Spacer(Modifier.height(6.dp))
        Text("VS", color = MediumGray, fontSize = 10.sp)
        Spacer(Modifier.height(6.dp))
        PlayerChip(name2, isMe = uid2 == myUid, won = winnerUid == uid2)
        if (winnerUid.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text("→ Pobednik", color = SuccessGreen, fontSize = 10.sp)
        }
    }
}

@Composable
private fun PlayerChip(name: String, isMe: Boolean, won: Boolean) {
    val bg = when {
        won -> SuccessGreen.copy(alpha = 0.2f)
        isMe -> PrimaryBlueBright.copy(alpha = 0.2f)
        else -> NavyLight
    }
    val border = when {
        won -> SuccessGreen
        isMe -> PrimaryBlueBright
        else -> LineColor
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            name, color = if (isMe) White else LightGray,
            fontWeight = if (isMe) FontWeight.Bold else FontWeight.Normal,
            fontSize = 12.sp
        )
    }
}

@Composable
private fun ResultView(won: Boolean, isRunner: Boolean, onExit: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (won) {
            val anim = rememberInfiniteTransition(label = "trophy")
            val alpha by anim.animateFloat(0.5f, 1f, infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "a")
            Icon(Icons.Default.EmojiEvents, null, tint = Gold.copy(alpha = alpha), modifier = Modifier.size(96.dp))
            Spacer(Modifier.height(24.dp))
            Text("POBEDNIK!", color = Gold, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.sp)
            Text("Čestitamo! +3 tokena +10★", color = LightGray, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp), textAlign = TextAlign.Center)
        } else if (isRunner) {
            Text("Finalista", color = Silver, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
            Text("Izgubili ste finale. Dobro igrano!", color = LightGray, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp), textAlign = TextAlign.Center)
        } else {
            Text("Eliminisani", color = ErrorRed, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
            Text("Izgubili ste polufinale.", color = LightGray, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp), textAlign = TextAlign.Center)
        }

        Spacer(Modifier.height(40.dp))
        Button(
            onClick = onExit,
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlueBright),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Povratak", fontWeight = FontWeight.Bold)
        }
    }
}

private val Silver = Color(0xFFC0C0C0)
