package com.tim03.slagalica.ui.izazov

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tim03.slagalica.data.model.IzazovSession
import com.tim03.slagalica.ui.theme.*
import com.tim03.slagalica.viewmodel.IzazovPhase
import com.tim03.slagalica.viewmodel.IzazovViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IzazovScreen(
    onBack: () -> Unit,
    onPlayIzazov: (String) -> Unit = {},
    vm: IzazovViewModel = viewModel()
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    var showCreateDialog by remember { mutableStateOf(false) }

    // Refresh balance/list when (re-)entering, e.g. coming back from the izazov partija.
    LaunchedEffect(Unit) { vm.refresh() }

    // One-shot navigation into the solo partija for this izazov.
    LaunchedEffect(state.playIzazovId) {
        state.playIzazovId?.let { id ->
            vm.consumePlayEvent()
            onPlayIzazov(id)
        }
    }

    if (showCreateDialog) {
        CreateIzazovDialog(
            bidStars = state.createBidStars,
            bidTokens = state.createBidTokens,
            myStars = state.myStars,
            myTokens = state.myTokens,
            onBidStarsChange = vm::setBidStars,
            onBidTokensChange = vm::setBidTokens,
            onConfirm = { showCreateDialog = false; vm.createIzazov() },
            onDismiss = { showCreateDialog = false }
        )
    }

    when (state.phase) {
        IzazovPhase.LIST -> {
            Scaffold(
                containerColor = Navy,
                topBar = {
                    TopAppBar(
                        title = { Text("Izazov", color = White, fontWeight = FontWeight.Bold) },
                        navigationIcon = {
                            IconButton(onClick = onBack) {
                                Icon(Icons.Default.ArrowBack, null, tint = White)
                            }
                        },
                        actions = {
                            IconButton(onClick = { showCreateDialog = true }) {
                                Icon(Icons.Default.Add, null, tint = Gold)
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Navy)
                    )
                }
            ) { padding ->
                if (state.openSessions.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(padding),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("⚔", fontSize = 48.sp)
                            Spacer(Modifier.height(12.dp))
                            Text("Nema aktivnih izazova", color = MediumGray, fontWeight = FontWeight.SemiBold)
                            Text("Klikni + da kreirate novi", color = MediumGray, fontSize = 12.sp)
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(padding),
                        contentPadding = PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(state.openSessions, key = { it.id }) { session ->
                            IzazovCard(
                                session = session,
                                myUid = state.myUid,
                                onJoin = { vm.joinIzazov(session) },
                                onPlay = { vm.playExisting(session) },
                                onViewResults = { vm.viewResults(session) },
                                onFinalizeEarly = { vm.finalizeEarly(session) }
                            )
                        }
                    }
                }
            }
        }

        IzazovPhase.CREATING -> {
            Box(Modifier.fillMaxSize().background(Navy), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Gold)
            }
        }

        IzazovPhase.RESULTS -> {
            IzazovResultsScreen(
                session = state.activeSession,
                myUid = state.myUid,
                onBack = { vm.backToList() }
            )
        }
    }
}

@Composable
internal fun IzazovCard(
    session: IzazovSession,
    myUid: String,
    onJoin: () -> Unit,
    onPlay: () -> Unit = {},
    onViewResults: () -> Unit,
    onFinalizeEarly: () -> Unit = {}
) {
    val isMine = session.participants.contains(myUid)
    val hasPlayed = session.hasPlayed(myUid)
    val isCompleted = session.status == "completed"
    val isPoster = session.posterId == myUid

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isCompleted || session.scores.isNotEmpty())
                    Modifier.clickable(onClick = onViewResults)
                else Modifier
            ),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = NavyCard)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        session.posterName,
                        color = White, fontWeight = FontWeight.Bold, fontSize = 15.sp
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        if (session.bidStars > 0) {
                            BidChip("★ ${session.bidStars}", Gold)
                        }
                        if (session.bidTokens > 0) {
                            BidChip("T ${session.bidTokens}", Accent4)
                        }
                        BidChip("${session.participants.size}/4 igrača", LightGray)
                        when {
                            isCompleted -> BidChip("Završen", MediumGray)
                            else -> {
                                val minLeft = ((session.expiresAt - System.currentTimeMillis()) / 60000L + 1)
                                    .coerceAtLeast(0)
                                BidChip("još $minLeft min", Accent2)
                            }
                        }
                    }
                }
                when {
                    isCompleted -> {
                        TextButton(onClick = onViewResults) {
                            Text("Rezultati", color = Gold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    hasPlayed -> {
                        val myScore = session.scores[myUid] ?: 0L
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("$myScore", color = Gold, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                            Text("bod.", color = MediumGray, fontSize = 10.sp)
                        }
                    }
                    isMine -> {
                        Button(
                            onClick = onPlay,
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Gold),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text("Igraj", color = Navy, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    !session.canJoin(myUid) -> {
                        Text("Puno", color = MediumGray, fontSize = 12.sp)
                    }
                    else -> {
                        Button(
                            onClick = onJoin,
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Accent2),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text("Priključi se", color = White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            if (session.scores.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Divider(color = LineColor, thickness = 1.dp)
                Spacer(Modifier.height(8.dp))
                val scoreboard = session.participants.zip(session.participantNames).map { (uid, name) ->
                    Triple(uid, name, session.scores[uid] ?: 0L)
                }.sortedByDescending { it.third }
                scoreboard.forEachIndexed { i, (uid, name, score) ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "${i + 1}.",
                                color = when (i) { 0 -> Gold; 1 -> LightGray; else -> MediumGray },
                                fontWeight = FontWeight.Bold, fontSize = 12.sp,
                                modifier = Modifier.width(20.dp)
                            )
                            Text(name, color = if (uid == myUid) Gold else White, fontSize = 12.sp)
                        }
                        Text("$score", color = LightGray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Poster controls: close early once everyone who joined has played,
            // or cancel (with refund) while still alone.
            if (session.status == "open" && isPoster) {
                val canClose = session.participants.size >= 2 && session.allPlayed()
                val canCancel = session.participants.size == 1
                if (canClose || canCancel) {
                    TextButton(
                        onClick = onFinalizeEarly,
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text(
                            if (canClose) "Završi izazov" else "Otkaži (vrati ulog)",
                            color = if (canClose) Gold else MediumGray,
                            fontSize = 12.sp, fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun BidChip(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(text, color = color, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
internal fun CreateIzazovDialog(
    bidStars: Int,
    bidTokens: Int,
    myStars: Int,
    myTokens: Int,
    onBidStarsChange: (Int) -> Unit,
    onBidTokensChange: (Int) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NavyCard,
        title = { Text("Novi izazov ⚔", color = White, fontWeight = FontWeight.ExtraBold) },
        text = {
            Column {
                Text("Postavi ulog koji svi moraju platiti:", color = LightGray, fontSize = 13.sp)
                Spacer(Modifier.height(16.dp))

                val maxBidStars = minOf(10, myStars)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("★ Zvezde (0-10):", color = White, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    Text("$myStars dostupno", color = MediumGray, fontSize = 10.sp)
                }
                if (maxBidStars > 0) {
                    Slider(
                        value = bidStars.toFloat(),
                        onValueChange = { onBidStarsChange(it.toInt()) },
                        valueRange = 0f..maxBidStars.toFloat(),
                        steps = (maxBidStars - 1).coerceAtLeast(0),
                        colors = SliderDefaults.colors(thumbColor = Gold, activeTrackColor = Gold)
                    )
                } else {
                    Text("Nemate zvezda za ulaganje.", color = MediumGray, fontSize = 11.sp)
                }
                Text("Ulog: $bidStars ★", color = Gold, fontWeight = FontWeight.Bold, fontSize = 13.sp)

                Spacer(Modifier.height(12.dp))

                val maxBidTokens = minOf(2, myTokens)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("T Tokeni (0-2):", color = White, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    Text("$myTokens dostupno", color = MediumGray, fontSize = 10.sp)
                }
                if (maxBidTokens > 0) {
                    Slider(
                        value = bidTokens.toFloat(),
                        onValueChange = { onBidTokensChange(it.toInt()) },
                        valueRange = 0f..maxBidTokens.toFloat(),
                        steps = (maxBidTokens - 1).coerceAtLeast(0),
                        colors = SliderDefaults.colors(thumbColor = Accent4, activeTrackColor = Accent4)
                    )
                } else {
                    Text("Nemate tokena za ulaganje.", color = MediumGray, fontSize = 11.sp)
                }
                Text("Ulog: $bidTokens T", color = Accent4, fontWeight = FontWeight.Bold, fontSize = 13.sp)

                if (bidStars == 0 && bidTokens == 0) {
                    Spacer(Modifier.height(8.dp))
                    Text("Mora biti postavljen barem jedan ulog.", color = ErrorRed, fontSize = 11.sp)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = bidStars > 0 || bidTokens > 0
            ) {
                Text("Kreiraj i igraj", color = Gold, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Otkaži", color = MediumGray) }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun IzazovResultsScreen(
    session: IzazovSession?,
    myUid: String,
    onBack: () -> Unit
) {
    Scaffold(
        containerColor = Navy,
        topBar = {
            TopAppBar(
                title = { Text("Rezultati izazova", color = White, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, null, tint = White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Navy)
            )
        }
    ) { padding ->
        if (session == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Gold)
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val allPlayed = session.participants.all { session.hasPlayed(it) }
            val isCompleted = session.status == "completed"

            if (!allPlayed && !isCompleted) {
                Spacer(Modifier.height(24.dp))
                CircularProgressIndicator(color = PrimaryBlueBright, strokeWidth = 2.dp, modifier = Modifier.size(32.dp))
                Spacer(Modifier.height(12.dp))
                Text("Čeka se da svi odigraju…", color = LightGray, fontWeight = FontWeight.SemiBold)
                Text(
                    "${session.scores.size}/${session.participants.size} odigralo",
                    color = MediumGray, fontSize = 12.sp
                )
                Spacer(Modifier.height(24.dp))
            }

            val sortedPlayers = session.participants.zip(session.participantNames).map { (uid, name) ->
                Triple(uid, name, session.scores[uid] ?: -1L)
            }.sortedByDescending { it.third }

            sortedPlayers.forEachIndexed { rank, (uid, name, score) ->
                val isWinner = isCompleted && session.winnerId == uid
                val isRunner = isCompleted && session.runnerId == uid && rank == 1
                val isMe = uid == myUid
                val emoji = when (rank) { 0 -> "🥇"; 1 -> "🥈"; 2 -> "🥉"; else -> "${rank + 1}." }

                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = when {
                            isWinner -> Gold.copy(alpha = 0.15f)
                            isMe -> PrimaryBlueBright.copy(alpha = 0.12f)
                            else -> NavyCard
                        }
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(emoji, fontSize = 20.sp, modifier = Modifier.width(36.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                name + if (isMe) " (ti)" else "",
                                color = if (isWinner) Gold else White,
                                fontWeight = FontWeight.Bold, fontSize = 14.sp
                            )
                            if (isCompleted) {
                                val reward = when {
                                    isWinner -> "+${(session.bidStars * session.participants.size * 0.75).toInt()}★ nagrada"
                                    isRunner -> "Ulog vraćen"
                                    else -> "Ulog izgubljen"
                                }
                                Text(reward, color = if (isWinner) Gold else MediumGray, fontSize = 11.sp)
                            }
                        }
                        if (score >= 0) {
                            Text(
                                "$score",
                                color = if (isWinner) Gold else LightGray,
                                fontWeight = FontWeight.ExtraBold, fontSize = 20.sp
                            )
                        } else {
                            Text("čeka...", color = MediumGray, fontSize = 12.sp)
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            if (isCompleted) {
                val iWon = session.winnerId == myUid
                val iRunner = session.runnerId == myUid
                Text(
                    when { iWon -> "🎉 Pobedili ste!"; iRunner -> "Drugi ste — ulog vraćen."; else -> "Izgubili ste ulog." },
                    color = if (iWon) Gold else if (iRunner) LightGray else MediumGray,
                    fontWeight = FontWeight.ExtraBold, fontSize = 16.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
