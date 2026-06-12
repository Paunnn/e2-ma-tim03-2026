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
import androidx.compose.runtime.Composable
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
import com.tim03.slagalica.ui.theme.*

@Composable
fun HomeScreen(
    onKorakPoKorakClick: () -> Unit,
    onMojBrojClick: () -> Unit,
    onKoZnaZnaClick: () -> Unit = {},
    onSpojniceClick: () -> Unit = {},
    onAsocijacijeClick: () -> Unit = {},
    onSkockoClick: () -> Unit = {},
    onProfileClick: () -> Unit = {},
    onNotificationsClick: () -> Unit = {},
    unreadNotifCount: Int = 0
) {
    Scaffold(
        containerColor = Navy,
        topBar = {
            ResourceBar(
                unreadCount = unreadNotifCount,
                onProfileClick = onProfileClick,
                onNotificationsClick = onNotificationsClick
            )
        },
        bottomBar = { MozaikBottomNav(onProfileClick = onProfileClick) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            HeroCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ModeCard(color = Accent2, icon = "⚔", title = "Izazov", sub = "Uloži zvezde", badge = "3 ČEKAJU", modifier = Modifier.weight(1f))
                ModeCard(color = Accent5, icon = "♛", title = "Turnir", sub = "4 igrača · 3 ●", badge = "START 18:00", modifier = Modifier.weight(1f))
            }

            SectionHeader(title = "Igre", modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 10.dp))

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

            SectionHeader(title = "Dnevne misije", sub = "Resetuje se za 04:12", modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 10.dp))

            val missions = listOf(
                Triple("Pobedi partiju", "0/1", GameSkocko),
                Triple("Pošalji poruku u čet", "1/1", GameSpojnice),
                Triple("Prijateljska partija", "0/1", GameKorak),
                Triple("Pobedi u turniru", "0/1", GameAsocijacije),
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(missions.size) { i ->
                    val (title, progress, color) = missions[i]
                    MissionCard(title = title, progress = progress, color = color, done = progress == "1/1")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

// ── ResourceBar ──────────────────────────────────────────────────

@Composable
private fun ResourceBar(
    tokens: Int = 12,
    stars: Int = 247,
    league: String = "Bronza II",
    unreadCount: Int = 0,
    onProfileClick: () -> Unit = {},
    onNotificationsClick: () -> Unit = {}
) {
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
            Text("IM", color = White, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
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
            Text(league, color = LightGray, fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
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
private fun HeroCard(modifier: Modifier = Modifier) {
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
                    onClick = {},
                    modifier = Modifier.weight(1f).height(40.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlueBright),
                    contentPadding = PaddingValues(horizontal = 12.dp)
                ) {
                    Text("Igraj nasumično", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = White, maxLines = 1)
                }
                Button(
                    onClick = {},
                    modifier = Modifier.height(40.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Gold),
                    contentPadding = PaddingValues(horizontal = 14.dp)
                ) {
                    Text("Drugar", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF3A2A05))
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
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(NavyLight)
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

// ── Mozaik bottom nav ─────────────────────────────────────────────

@Composable
private fun MozaikBottomNav(onProfileClick: () -> Unit = {}) {
    val items = listOf(
        Triple(Icons.Default.PlayArrow, "Igraj", true),
        Triple(Icons.Default.Leaderboard, "Rang", false),
        Triple(Icons.Default.Map, "Mapa", false),
        Triple(Icons.Default.Group, "Drugari", false),
        Triple(Icons.Default.Person, "Profil", false),
    )
    NavigationBar(containerColor = Navy, tonalElevation = 0.dp) {
        items.forEachIndexed { i, (icon, label, selected) ->
            NavigationBarItem(
                selected = selected,
                onClick = { if (i == 4) onProfileClick() },
                icon = {
                    Box(
                        modifier = if (selected)
                            Modifier.clip(RoundedCornerShape(8.dp)).background(PrimaryBlueBright.copy(alpha = 0.16f)).padding(horizontal = 10.dp, vertical = 4.dp)
                        else
                            Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(icon, contentDescription = label, modifier = Modifier.size(20.dp))
                    }
                },
                label = { Text(label, fontSize = 10.sp, fontWeight = FontWeight.SemiBold) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = PrimaryBlueBright,
                    selectedTextColor = PrimaryBlueBright,
                    unselectedIconColor = MediumGray,
                    unselectedTextColor = MediumGray,
                    indicatorColor = Color.Transparent
                )
            )
        }
    }
}
