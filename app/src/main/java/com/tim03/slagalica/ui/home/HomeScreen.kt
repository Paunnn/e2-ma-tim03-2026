package com.tim03.slagalica.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
    onNotificationsClick: () -> Unit = {}
) {
    Scaffold(
        containerColor = Navy,
        topBar = { TopBar(onNotificationsClick = onNotificationsClick) },
        bottomBar = { BottomNavBar(onProfileClick = onProfileClick) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            WelcomeCard()
            Spacer(modifier = Modifier.height(20.dp))
            PlayCard()
            Spacer(modifier = Modifier.height(20.dp))
            Text("Igre", style = MaterialTheme.typography.titleLarge, color = White, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GameCard("Korak po korak", Icons.Default.Stairs, "40 bod.", "2×70s", true, Modifier.weight(1f), onKorakPoKorakClick)
                GameCard("Moj broj", Icons.Default.Calculate, "20 bod.", "2×60s", true, Modifier.weight(1f), onMojBrojClick)
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GameCard("Ko zna zna", Icons.Default.Quiz, "50 bod.", "25s", true, Modifier.weight(1f), onKoZnaZnaClick)
                GameCard("Spojnice", Icons.Default.Link, "20 bod.", "2×30s", true, Modifier.weight(1f), onSpojniceClick)
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GameCard("Asocijacije", Icons.Default.GridView, "60 bod.", "2×2min", true, Modifier.weight(1f), onAsocijacijeClick)
                GameCard("Skočko", Icons.Default.Casino, "40 bod.", "2×30s", true, Modifier.weight(1f), onSkockoClick)
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopBar(onNotificationsClick: () -> Unit = {}) {
    TopAppBar(
        title = { Text("SLAGALICA", fontWeight = FontWeight.ExtraBold, color = Gold, letterSpacing = 2.sp) },
        actions = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(end = 8.dp)
            ) {
                Icon(Icons.Default.Token, null, tint = GoldLight, modifier = Modifier.size(18.dp))
                Text("5", color = White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.width(6.dp))
                Icon(Icons.Default.Star, null, tint = Gold, modifier = Modifier.size(18.dp))
                Text("0", color = White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.width(6.dp))
                Surface(shape = RoundedCornerShape(8.dp), color = NavyCard) {
                    Text("Liga 0", color = LightGray, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                }
                Spacer(modifier = Modifier.width(4.dp))
                BadgedBox(badge = { Badge { Text("3") } }) {
                    IconButton(onClick = onNotificationsClick) {
                        Icon(Icons.Default.Notifications, null, tint = White)
                    }
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = NavyLight)
    )
}

@Composable
private fun WelcomeCard() {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = NavyCard)) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(52.dp).clip(CircleShape).background(PrimaryBlue), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Person, null, tint = White, modifier = Modifier.size(30.dp))
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text("Dobrodošao,", color = LightGray, style = MaterialTheme.typography.bodySmall)
                Text("Igrač1", color = White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Text("Vojvodina • Liga 0", color = LightGray, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun PlayCard() {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = PrimaryBlue)) {
        Box(modifier = Modifier.fillMaxWidth().background(Brush.horizontalGradient(listOf(PrimaryBlue, PrimaryBlueBright))).padding(20.dp)) {
            Column {
                Text("Spremi se za igru!", color = White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Text("Nađi protivnika i počni partiju", color = OffWhite, style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(12.dp))
                Button(onClick = {}, colors = ButtonDefaults.buttonColors(containerColor = Gold), shape = RoundedCornerShape(10.dp)) {
                    Icon(Icons.Default.PlayArrow, null, tint = Navy)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("POČNI PARTIJU", color = Navy, fontWeight = FontWeight.Bold)
                }
            }
            Box(modifier = Modifier.align(Alignment.CenterEnd)) {
                Icon(Icons.Default.EmojiEvents, null, tint = GoldLight.copy(alpha = 0.3f), modifier = Modifier.size(80.dp))
            }
        }
    }
}

@Composable
private fun GameCard(title: String, icon: ImageVector, maxPoints: String, duration: String, available: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(
        modifier = modifier.clickable(enabled = available, onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = NavyCard)
    ) {
        Column(modifier = Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(if (available) PrimaryBlue else DarkGray),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = if (available) White else MediumGray, modifier = Modifier.size(26.dp))
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(title, color = if (available) White else MediumGray, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(modifier = Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(maxPoints, color = if (available) Gold else MediumGray, style = MaterialTheme.typography.labelSmall)
                Text(duration, color = if (available) LightGray else MediumGray, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun BottomNavBar(onProfileClick: () -> Unit = {}) {
    NavigationBar(containerColor = NavyLight) {
        val itemColors = NavigationBarItemDefaults.colors(
            selectedIconColor = PrimaryBlueBright, selectedTextColor = PrimaryBlueBright,
            indicatorColor = NavyCard, unselectedIconColor = MediumGray, unselectedTextColor = MediumGray
        )
        NavigationBarItem(selected = true, onClick = {}, icon = { Icon(Icons.Default.Home, null) }, label = { Text("Početna") }, colors = itemColors)
        NavigationBarItem(selected = false, onClick = onProfileClick, icon = { Icon(Icons.Default.Person, null) }, label = { Text("Profil") }, colors = itemColors)
        NavigationBarItem(selected = false, onClick = {}, icon = { Icon(Icons.Default.Leaderboard, null) }, label = { Text("Rang") }, colors = itemColors)
        NavigationBarItem(selected = false, onClick = {}, icon = { Icon(Icons.Default.Group, null) }, label = { Text("Prijatelji") }, colors = itemColors)
        NavigationBarItem(selected = false, onClick = {}, icon = { Icon(Icons.Default.Chat, null) }, label = { Text("Čet") }, colors = itemColors)
    }
}
