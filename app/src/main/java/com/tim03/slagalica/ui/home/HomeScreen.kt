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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tim03.slagalica.ui.theme.*

@Composable
fun HomeScreen(
    onKorakPoKorakClick: () -> Unit,
    onMojBrojClick: () -> Unit,
    onKoZnaZnaClick: () -> Unit = {},
    onSpojniceClick: () -> Unit = {},
    onProfileClick: () -> Unit = {}
) {
    Scaffold(
        containerColor = Navy,
        topBar = {
            TopBar()
        },
        bottomBar = {
            BottomNavBar(onProfileClick = onProfileClick)
        }
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
            Text(
                "Igre",
                style = MaterialTheme.typography.titleLarge,
                color = White,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                GameCard(
                    title = "Korak po korak",
                    icon = Icons.Default.Stairs,
                    maxPoints = "40 bod.",
                    duration = "2×70s",
                    available = true,
                    modifier = Modifier.weight(1f),
                    onClick = onKorakPoKorakClick
                )
                GameCard(
                    title = "Moj broj",
                    icon = Icons.Default.Calculate,
                    maxPoints = "20 bod.",
                    duration = "2×60s",
                    available = true,
                    modifier = Modifier.weight(1f),
                    onClick = onMojBrojClick
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                GameCard(
                    title = "Ko zna zna",
                    icon = Icons.Default.Quiz,
                    maxPoints = "50 bod.",
                    duration = "25s",
                    available = true,
                    modifier = Modifier.weight(1f),
                    onClick = onKoZnaZnaClick
                )
                GameCard(
                    title = "Spojnice",
                    icon = Icons.Default.Link,
                    maxPoints = "20 bod.",
                    duration = "2×30s",
                    available = true,
                    modifier = Modifier.weight(1f),
                    onClick = onSpojniceClick
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                GameCard(
                    title = "Asocijacije",
                    icon = Icons.Default.GridView,
                    maxPoints = "60 bod.",
                    duration = "2×2min",
                    available = false,
                    modifier = Modifier.weight(1f),
                    onClick = {}
                )
                GameCard(
                    title = "Skočko",
                    icon = Icons.Default.Casino,
                    maxPoints = "40 bod.",
                    duration = "2×30s",
                    available = false,
                    modifier = Modifier.weight(1f),
                    onClick = {}
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopBar() {
    TopAppBar(
        title = {
            Text(
                "SLAGALICA",
                fontWeight = FontWeight.ExtraBold,
                color = Gold,
                letterSpacing = 2.sp
            )
        },
        actions = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(end = 12.dp)
            ) {
                Icon(Icons.Default.Token, null, tint = GoldLight, modifier = Modifier.size(18.dp))
                Text("5", color = White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Icon(Icons.Default.Star, null, tint = Gold, modifier = Modifier.size(18.dp))
                Text("0", color = White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = NavyCard
                ) {
                    Text(
                        "Liga 0",
                        color = LightGray,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = NavyLight)
    )
}

@Composable
private fun WelcomeCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = NavyCard)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(PrimaryBlue),
                contentAlignment = Alignment.Center
            ) {
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
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = PrimaryBlue)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.horizontalGradient(listOf(PrimaryBlue, PrimaryBlueBright)))
                .padding(20.dp)
        ) {
            Column {
                Text("Spremi se za igru!", color = White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Text("Nađi protivnika i počni partiju", color = OffWhite, style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = {},
                    colors = ButtonDefaults.buttonColors(containerColor = Gold),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, null, tint = Navy)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("POČNI PARTIJU", color = Navy, fontWeight = FontWeight.Bold)
                }
            }
            Box(modifier = Modifier.align(Alignment.CenterEnd)) {
                Icon(
                    Icons.Default.EmojiEvents,
                    null,
                    tint = GoldLight.copy(alpha = 0.3f),
                    modifier = Modifier.size(80.dp)
                )
            }
        }
    }
}

@Composable
private fun GameCard(
    title: String,
    icon: ImageVector,
    maxPoints: String,
    duration: String,
    available: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val alpha = if (available) 1f else 0.4f
    Card(
        modifier = modifier
            .clickable(enabled = available, onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = NavyCard)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (available) PrimaryBlue else DarkGray),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = if (available) White else MediumGray, modifier = Modifier.size(26.dp))
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                title,
                color = if (available) White else MediumGray,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(maxPoints, color = if (available) Gold else MediumGray, style = MaterialTheme.typography.labelSmall)
                Text(duration, color = LightGray.copy(alpha = alpha), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun BottomNavBar(onProfileClick: () -> Unit = {}) {
    NavigationBar(containerColor = NavyLight) {
        NavigationBarItem(
            selected = true,
            onClick = {},
            icon = { Icon(Icons.Default.Home, null) },
            label = { Text("Početna") },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = PrimaryBlueBright,
                selectedTextColor = PrimaryBlueBright,
                indicatorColor = NavyCard,
                unselectedIconColor = MediumGray,
                unselectedTextColor = MediumGray
            )
        )
        NavigationBarItem(
            selected = false,
            onClick = onProfileClick,
            icon = { Icon(Icons.Default.Person, null) },
            label = { Text("Profil") },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = PrimaryBlueBright,
                selectedTextColor = PrimaryBlueBright,
                indicatorColor = NavyCard,
                unselectedIconColor = MediumGray,
                unselectedTextColor = MediumGray
            )
        )
        NavigationBarItem(
            selected = false,
            onClick = {},
            icon = { Icon(Icons.Default.Leaderboard, null) },
            label = { Text("Rang") },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = PrimaryBlueBright,
                selectedTextColor = PrimaryBlueBright,
                indicatorColor = NavyCard,
                unselectedIconColor = MediumGray,
                unselectedTextColor = MediumGray
            )
        )
        NavigationBarItem(
            selected = false,
            onClick = {},
            icon = { Icon(Icons.Default.Group, null) },
            label = { Text("Prijatelji") },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = PrimaryBlueBright,
                selectedTextColor = PrimaryBlueBright,
                indicatorColor = NavyCard,
                unselectedIconColor = MediumGray,
                unselectedTextColor = MediumGray
            )
        )
        NavigationBarItem(
            selected = false,
            onClick = {},
            icon = { Icon(Icons.Default.Chat, null) },
            label = { Text("Čet") },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = PrimaryBlueBright,
                selectedTextColor = PrimaryBlueBright,
                indicatorColor = NavyCard,
                unselectedIconColor = MediumGray,
                unselectedTextColor = MediumGray
            )
        )
    }
}
