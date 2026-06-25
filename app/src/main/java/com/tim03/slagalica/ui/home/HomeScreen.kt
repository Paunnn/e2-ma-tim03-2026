package com.tim03.slagalica.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tim03.slagalica.data.model.DailyMissions
import com.tim03.slagalica.ui.components.MozaikBottomNav
import com.tim03.slagalica.ui.theme.*
import com.tim03.slagalica.viewmodel.DailyMissionsViewModel
import com.tim03.slagalica.viewmodel.HomeViewModel

@Composable
fun HomeScreen(
    onKorakPoKorakClick: () -> Unit,
    onMojBrojClick: () -> Unit,
    onKoZnaZnaClick: () -> Unit = {},
    onSpojniceClick: () -> Unit = {},
    onAsocijacijeClick: () -> Unit = {},
    onSkockoClick: () -> Unit = {},
    onPartijaClick: () -> Unit = {},
    onFriendlyClick: () -> Unit = {},
    onProfileClick: () -> Unit = {},
    onNotificationsClick: () -> Unit = {},
    onLeaderboardClick: () -> Unit = {},
    onTurnirClick: () -> Unit = {},
    onChatClick: () -> Unit = {},
    onIzazovClick: () -> Unit = {},
    onMapClick: () -> Unit = {},
    onFriendsClick: () -> Unit = {},
    onLogout: () -> Unit = {},
    onNavigateToPartija: (sessionId: String, isPlayer1: Boolean) -> Unit = { _, _ -> },
    unreadNotifCount: Int = 0,
    homeViewModel: HomeViewModel = viewModel(),
    missionsViewModel: DailyMissionsViewModel = viewModel()
) {
    val homeState by homeViewModel.uiState.collectAsStateWithLifecycle()
    val missions by missionsViewModel.missions.collectAsStateWithLifecycle()
    val bonusCollected by missionsViewModel.bonusJustCollected.collectAsStateWithLifecycle()
    val incomingInvite = homeState.incomingInvite

    // Incoming friend invite dialog
    if (incomingInvite != null) {
        AlertDialog(
            onDismissRequest = { homeViewModel.rejectInvite() },
            title = { Text("Poziv za prijateljsku partiju", color = White, fontWeight = FontWeight.ExtraBold) },
            text = { Text("${incomingInvite.senderName} vas poziva na prijateljsku partiju.\nPoziv ističe za 10 sekundi.", color = LightGray) },
            confirmButton = {
                TextButton(onClick = {
                    homeViewModel.acceptInvite { sessionId, isPlayer1 ->
                        onNavigateToPartija(sessionId, isPlayer1)
                    }
                }) {
                    Text("Prihvati", color = SuccessGreen, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { homeViewModel.rejectInvite() }) {
                    Text("Odbij", color = ErrorRed)
                }
            },
            containerColor = NavyCard
        )
    }

    if (bonusCollected) {
        AlertDialog(
            onDismissRequest = { missionsViewModel.clearBonusFlag() },
            title = { Text("Sve misije završene!", color = White, fontWeight = FontWeight.ExtraBold) },
            text = { Text("Odlično! Dobili ste 2 tokena i 3 bonus zvezde!", color = LightGray) },
            confirmButton = {
                TextButton(onClick = { missionsViewModel.clearBonusFlag() }) {
                    Text("Super!", color = Gold, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = NavyCard
        )
    }

    if (homeState.isGuest) {
        GuestHomeScreen(
            username = homeState.username,
            onPartijaClick = onPartijaClick,
            onLogout = { homeViewModel.logout(); onLogout() }
        )
        return
    }

    Scaffold(
        containerColor = Navy,
        topBar = {
            ResourceBar(
                username = homeState.username,
                tokens = homeState.tokens,
                stars = homeState.stars,
                league = homeState.league,
                unreadCount = unreadNotifCount,
                onProfileClick = onProfileClick,
                onNotificationsClick = onNotificationsClick
            )
        },
        bottomBar = {
            MozaikBottomNav(
                selectedIndex = 0,
                onProfileClick = onProfileClick,
                onLeaderboardClick = onLeaderboardClick,
                onMapClick = onMapClick,
                onFriendsClick = onFriendsClick
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            HeroCard(
                onFriendlyClick = onFriendlyClick,
                onChatClick = onChatClick,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ModeCard(color = Accent2, icon = "⚔", title = "Izazov", sub = "Uloži zvezde", badge = null, modifier = Modifier.weight(1f), onClick = onIzazovClick)
                ModeCard(
                    color = Accent5, icon = "♛", title = "Turnir", sub = "4 igrača · 3 ●",
                    badge = "UĐI", modifier = Modifier.weight(1f), onClick = onTurnirClick
                )
            }

            // Zapocni partiju button
            Button(
                onClick = onPartijaClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .height(54.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Gold)
            ) {
                Icon(Icons.Default.PlayArrow, null, tint = Navy, modifier = Modifier.size(22.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    "ZAPOČNI PARTIJU",
                    color = Navy,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 15.sp,
                    letterSpacing = 1.sp
                )
            }

            SectionHeader(title = "Igre", modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 10.dp))

            Column(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    GameCard("Ko zna zna", Icons.Default.Quiz, GameKoZnaZna, "50 bod.", "25s", Modifier.weight(1f), onKoZnaZnaClick)
                    GameCard("Spojnice", Icons.Default.Link, GameSpojnice, "20 bod.", "2×30s", Modifier.weight(1f), onSpojniceClick)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    GameCard("Asocijacije", Icons.Default.GridView, GameAsocijacije, "60 bod.", "2×2min", Modifier.weight(1f), onAsocijacijeClick)
                    GameCard("Skočko", Icons.Default.Casino, GameSkocko, "40 bod.", "2×30s", Modifier.weight(1f), onSkockoClick)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    GameCard("Korak po korak", Icons.Default.Stairs, GameKorak, "40 bod.", "2×70s", Modifier.weight(1f), onKorakPoKorakClick)
                    GameCard("Moj broj", Icons.Default.Calculate, GameMojBroj, "20 bod.", "2×60s", Modifier.weight(1f), onMojBrojClick)
                }
            }

            SectionHeader(title = "Dnevne misije", modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 10.dp))

            val missionList = listOf(
                Triple("Pobedi partiju", missions.winGame, GameSkocko),
                Triple("Pošalji poruku u čet", missions.sendMessage, GameSpojnice),
                Triple("Prijateljska partija", missions.playFriendly, GameKorak),
                Triple("Pobedi u turniru", missions.winTournament, GameAsocijacije),
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(missionList.size) { i ->
                    val (title, done, color) = missionList[i]
                    MissionCard(
                        title = title,
                        progress = if (done) "1/1" else "0/1",
                        color = color,
                        done = done
                    )
                }
            }
            if (missions.allCompleted() && !missions.bonusCollected) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Gold.copy(alpha = 0.15f))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("★", color = Gold, fontSize = 20.sp)
                        Spacer(Modifier.width(8.dp))
                        Text("Sve misije završene! Bonus: +2 tokena +3★",
                            color = Gold, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

// ── ResourceBar ──────────────────────────────────────────────────

@Composable
private fun ResourceBar(
    username: String = "",
    tokens: Int = 0,
    stars: Int = 0,
    league: Int = 0,
    unreadCount: Int = 0,
    onProfileClick: () -> Unit = {},
    onNotificationsClick: () -> Unit = {}
) {
    val leagueLabel = when (league) {
        0 -> "Početnik"; 1 -> "Bronza"; 2 -> "Srebro"
        3 -> "Zlato"; 4 -> "Platina"; 5 -> "Dijamant"; else -> "Liga $league"
    }
    val initials = username.split("_", " ").filter { it.isNotBlank() }
        .take(2).mapNotNull { it.firstOrNull()?.uppercaseChar() }.joinToString("").ifEmpty { "?" }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Navy)
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(PrimaryBlueBright, Accent5)))
                .clickable { onProfileClick() },
            contentAlignment = Alignment.Center
        ) {
            Text(initials, color = White, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
        }

        Row(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(999.dp))
                .background(NavyLight)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(Brush.radialGradient(listOf(Color(0xFFFFD97A), Color(0xFFC98A1A)))),
                contentAlignment = Alignment.Center
            ) {
                Text("T", fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF5B3A06))
            }
            Spacer(Modifier.width(4.dp))
            Text(tokens.toString(), color = White, fontWeight = FontWeight.Bold, fontSize = 13.sp)

            Spacer(Modifier.width(8.dp))
            Box(modifier = Modifier.width(1.dp).height(16.dp).background(LineColor))
            Spacer(Modifier.width(8.dp))

            Text("★", color = Gold, fontSize = 13.sp)
            Spacer(Modifier.width(4.dp))
            Text(
                stars.toString(), color = White, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                modifier = Modifier.weight(1f), textAlign = TextAlign.Center
            )

            Box(modifier = Modifier.width(1.dp).height(16.dp).background(LineColor))
            Spacer(Modifier.width(8.dp))

            Text("◆", color = Accent2, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
            Spacer(Modifier.width(4.dp))
            Text(leagueLabel, color = LightGray, fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
        }

        BadgedBox(badge = { if (unreadCount > 0) Badge { Text(unreadCount.toString()) } }) {
            IconButton(onClick = onNotificationsClick) {
                Icon(Icons.Default.Notifications, contentDescription = null, tint = LightGray, modifier = Modifier.size(22.dp))
            }
        }
    }
}

// ── Hero card ─────────────────────────────────────────────────────

@Composable
private fun HeroCard(
    onFriendlyClick: () -> Unit = {},
    onChatClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val tileColors = listOf(GameKoZnaZna, GameSpojnice, GameAsocijacije, GameSkocko, GameKorak, GameMojBroj)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF1A2150), Color(0xFF3A1D4A))))
    ) {
        // Decorative tiles top-right
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 12.dp, end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                tileColors.take(3).forEach { c ->
                    Box(modifier = Modifier.size(22.dp).clip(RoundedCornerShape(6.dp)).background(c.copy(alpha = 0.45f)))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                tileColors.drop(3).forEach { c ->
                    Box(modifier = Modifier.size(22.dp).clip(RoundedCornerShape(6.dp)).background(c.copy(alpha = 0.45f)))
                }
            }
        }

        Column(modifier = Modifier.padding(18.dp)) {
            Text("NOVA PARTIJA", color = Gold, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.sp)
            Text(
                "Šest igara,\njedan pobednik.",
                color = White, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 28.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
            Row(modifier = Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onFriendlyClick,
                    modifier = Modifier.height(40.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Gold),
                    contentPadding = PaddingValues(horizontal = 14.dp)
                ) {
                    Text("Drugar", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF3A2A05))
                }
                Button(
                    onClick = onChatClick,
                    modifier = Modifier.height(40.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Accent4),
                    contentPadding = PaddingValues(horizontal = 14.dp)
                ) {
                    Text("Čet", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Navy, maxLines = 1)
                }
            }
        }
    }
}

// ── Mode card ─────────────────────────────────────────────────────

@Composable
private fun ModeCard(
    color: Color,
    icon: String,
    title: String,
    sub: String,
    badge: String? = null,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(NavyLight)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    ) {
        if (badge != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 10.dp, end = 10.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(color)
                    .padding(horizontal = 5.dp, vertical = 2.dp)
            ) {
                Text(badge, color = Navy, fontSize = 8.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.8.sp)
            }
        }
        Column(modifier = Modifier.padding(14.dp)) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(color),
                contentAlignment = Alignment.Center
            ) {
                Text(icon, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = Navy)
            }
            Text(title, color = White, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, modifier = Modifier.padding(top = 10.dp, bottom = 2.dp))
            Text(sub, color = LightGray, fontSize = 11.sp)
            Spacer(Modifier.height(4.dp))
        }
    }
}

// ── Section header ────────────────────────────────────────────────

@Composable
private fun SectionHeader(title: String, sub: String? = null, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(title, color = White, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, letterSpacing = (-0.3).sp)
        if (sub != null) Text(sub, color = MediumGray, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
    }
}

// ── Game card ─────────────────────────────────────────────────────

@Composable
private fun GameCard(
    title: String,
    icon: ImageVector,
    gameColor: Color,
    maxPoints: String,
    duration: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = NavyLight),
        border = BorderStroke(1.dp, LineSoft)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(36.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(gameColor)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = White, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 3.dp)) {
                    Text(maxPoints, color = gameColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Text(duration, color = MediumGray, fontSize = 10.sp)
                }
            }
            Icon(icon, contentDescription = null, tint = gameColor.copy(alpha = 0.8f), modifier = Modifier.size(20.dp))
        }
    }
}

// ── Guest home ───────────────────────────────────────────────────

@Composable
private fun GuestHomeScreen(
    username: String,
    onPartijaClick: () -> Unit,
    onLogout: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Navy)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MediumGray.copy(alpha = 0.25f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("G", color = LightGray, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(username, color = White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
                IconButton(onClick = onLogout) {
                    Icon(Icons.Default.Logout, contentDescription = "Odjava", tint = LightGray, modifier = Modifier.size(22.dp))
                }
            }

            Spacer(Modifier.weight(1f))

            Surface(shape = RoundedCornerShape(24.dp), color = PrimaryBlue, modifier = Modifier.size(80.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Star, null, tint = Gold, modifier = Modifier.size(44.dp))
                }
            }
            Spacer(Modifier.height(16.dp))
            Text("SLAGALICA", color = Gold, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp, letterSpacing = 4.sp)
            Spacer(Modifier.height(8.dp))
            Text(
                "Prijavili ste se kao neregistrovan korisnik",
                color = MediumGray, fontSize = 13.sp, textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(40.dp))

            Button(
                onClick = onPartijaClick,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Gold)
            ) {
                Icon(Icons.Default.PlayArrow, null, tint = Navy, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    "ZAPOČNI PARTIJU",
                    color = Navy,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 15.sp,
                    letterSpacing = 1.sp
                )
            }

            Spacer(Modifier.weight(1f))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = NavyLight),
                border = BorderStroke(1.dp, LineSoft)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "Registrujte se za više opcija",
                        color = White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp
                    )
                    Text(
                        "Pratite statistike, rang liste i dostignuća.",
                        color = MediumGray, fontSize = 11.sp, textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    Spacer(Modifier.height(10.dp))
                    TextButton(onClick = onLogout) {
                        Text("Idi na prijavu →", color = Gold, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

// ── Mission card ──────────────────────────────────────────────────

@Composable
private fun MissionCard(title: String, progress: String, color: Color, done: Boolean) {
    Box(
        modifier = Modifier
            .width(140.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (done) Brush.linearGradient(listOf(color.copy(alpha = 0.25f), NavyLight))
                else Brush.linearGradient(listOf(NavyLight, NavyLight))
            )
            .padding(12.dp)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(color.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Text(if (done) "✓" else "○", color = color, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
            }
            Text(title, color = White, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp), maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                Text("★", color = Gold, fontSize = 12.sp)
                Text("+3", color = Gold, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp, modifier = Modifier.padding(start = 2.dp))
                Spacer(Modifier.weight(1f))
                Text(progress, color = MediumGray, fontSize = 10.sp)
            }
        }
    }
}

