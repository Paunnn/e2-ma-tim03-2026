package com.tim03.slagalica.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tim03.slagalica.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onNavigateHome: () -> Unit = {},
    onNavigateRanking: () -> Unit = {},
    onNavigateFriends: () -> Unit = {},
    onNavigateChat: () -> Unit = {},
    onLogout: () -> Unit = {}
) {
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showAvatarDialog by remember { mutableStateOf(false) }

    if (showLogoutDialog) {
        LogoutDialog(
            onConfirm = {
                showLogoutDialog = false
                onLogout()
            },
            onDismiss = { showLogoutDialog = false }
        )
    }
    if (showAvatarDialog) {
        AvatarPickerDialog(onDismiss = { showAvatarDialog = false })
    }

    Scaffold(
        containerColor = Navy,
        topBar = {
            TopAppBar(
                title = {
                    Text("PROFIL", fontWeight = FontWeight.ExtraBold, color = White, letterSpacing = 2.sp)
                },
                actions = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(end = 12.dp)
                    ) {
                        Icon(Icons.Default.Token, null, tint = GoldLight, modifier = Modifier.size(18.dp))
                        Text("5", color = White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(Icons.Default.Star, null, tint = Gold, modifier = Modifier.size(18.dp))
                        Text("142", color = White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(shape = RoundedCornerShape(8.dp), color = NavyCard) {
                            Text(
                                "Liga 2",
                                color = GoldLight,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = NavyLight)
            )
        },
        bottomBar = {
            BottomNavBar(
                onHomeClick = onNavigateHome,
                onRankingClick = onNavigateRanking,
                onFriendsClick = onNavigateFriends,
                onChatClick = onNavigateChat
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            ProfileHeaderSection(onEditAvatarClick = { showAvatarDialog = true })
            Spacer(modifier = Modifier.height(16.dp))
            StatisticsSection()
            Spacer(modifier = Modifier.height(16.dp))
            LogoutButton(onClick = { showLogoutDialog = true })
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ProfileHeaderSection(onEditAvatarClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(NavyLight, Navy)))
            .padding(20.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Box(contentAlignment = Alignment.BottomEnd) {
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .clip(CircleShape)
                        .border(3.dp, Gold, CircleShape)
                        .background(PrimaryBlue),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Person, null, tint = White, modifier = Modifier.size(56.dp))
                }
                IconButton(
                    onClick = onEditAvatarClick,
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(NavyCard)
                        .border(1.dp, MediumGray, CircleShape)
                ) {
                    Icon(Icons.Default.Edit, null, tint = LightGray, modifier = Modifier.size(16.dp))
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text("Igrač1", color = White, fontWeight = FontWeight.Bold, fontSize = 22.sp)
            Text("igrac1@email.com", color = LightGray, style = MaterialTheme.typography.bodySmall)

            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InfoChip(icon = Icons.Default.LocationOn, label = "Vojvodina", color = PrimaryBlueLight)
                InfoChip(icon = Icons.Default.EmojiEvents, label = "Liga 2", color = Gold)
            }

            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatSummaryItem(value = "5", label = "Tokeni", icon = Icons.Default.Token, color = GoldLight)
                StatSummaryItem(value = "142", label = "Zvezde", icon = Icons.Default.Star, color = Gold)
                StatSummaryItem(value = "38", label = "Partija", icon = Icons.Default.Games, color = PrimaryBlueLight)
            }

            Spacer(modifier = Modifier.height(16.dp))
            QrCodeCard()
        }
    }
}

@Composable
private fun InfoChip(icon: ImageVector, label: String, color: Color) {
    Surface(shape = RoundedCornerShape(20.dp), color = NavyCard) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(icon, null, tint = color, modifier = Modifier.size(14.dp))
            Text(label, color = color, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun StatSummaryItem(value: String, label: String, icon: ImageVector, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.height(2.dp))
        Text(value, color = White, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
        Text(label, color = LightGray, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun QrCodeCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = NavyCard)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(White)
                    .padding(6.dp),
                contentAlignment = Alignment.Center
            ) {
                QrCodePlaceholder()
            }
            Column {
                Text("Moj QR kod", color = White, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    "Prijatelji skeniraju ovaj kod da vas dodaju u listu prijatelja.",
                    color = LightGray,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun QrCodePlaceholder() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            repeat(5) { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    repeat(5) { col ->
                        val filled = (row == 0 || row == 4 || col == 0 || col == 4 ||
                                (row == 1 && col == 1) || (row == 2 && col == 2) || (row == 3 && col == 3))
                        Box(
                            modifier = Modifier
                                .size(9.dp)
                                .background(if (filled) Color.Black else Color.Transparent)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatisticsSection() {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Text("Statistika", color = White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(12.dp))

        Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = NavyCard)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OverallStatsRow()
                HorizontalDivider(color = MediumGray.copy(alpha = 0.3f))
                GameStatItem(
                    title = "Ko zna zna",
                    icon = Icons.Default.Quiz,
                    iconColor = SuccessGreen,
                    content = { KoZnaZnaStats() }
                )
                HorizontalDivider(color = MediumGray.copy(alpha = 0.3f))
                GameStatItem(
                    title = "Spojnice",
                    icon = Icons.Default.Link,
                    iconColor = PrimaryBlueLight,
                    content = { SpojniceStats() }
                )
                HorizontalDivider(color = MediumGray.copy(alpha = 0.3f))
                GameStatItem(
                    title = "Moj broj",
                    icon = Icons.Default.Calculate,
                    iconColor = Gold,
                    content = { MojBrojStats() }
                )
                HorizontalDivider(color = MediumGray.copy(alpha = 0.3f))
                GameStatItem(
                    title = "Korak po korak",
                    icon = Icons.Default.Stairs,
                    iconColor = WarningOrange,
                    content = { KorakPoKorakStats() }
                )
                HorizontalDivider(color = MediumGray.copy(alpha = 0.3f))
                GameStatItem(
                    title = "Asocijacije",
                    icon = Icons.Default.GridView,
                    iconColor = GoldLight,
                    content = { AsocijacijeStats() }
                )
                HorizontalDivider(color = MediumGray.copy(alpha = 0.3f))
                GameStatItem(
                    title = "Skočko",
                    icon = Icons.Default.Casino,
                    iconColor = ErrorRed,
                    content = { SkockoStats() }
                )
            }
        }
    }
}

@Composable
private fun OverallStatsRow() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Opšta statistika", color = LightGray, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            MiniStatBox("38", "Ukupno partija")
            MiniStatBox("62%", "Pobede")
            MiniStatBox("38%", "Porazi")
        }
        LabeledProgressBar(label = "Pobede", value = 0.62f, color = SuccessGreen)
    }
}

@Composable
private fun MiniStatBox(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = White, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
        Text(label, color = LightGray, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
    }
}

@Composable
private fun GameStatItem(title: String, icon: ImageVector, iconColor: Color, content: @Composable () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(iconColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = iconColor, modifier = Modifier.size(18.dp))
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text(title, color = White, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = { expanded = !expanded }, modifier = Modifier.size(24.dp)) {
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    null,
                    tint = LightGray,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        if (expanded) {
            Spacer(modifier = Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun KoZnaZnaStats() {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Prosečni bodovi: 15–35 bod./rundi", color = LightGray, style = MaterialTheme.typography.bodySmall)
        LabeledProgressBar(label = "Tačni odgovori", value = 0.68f, color = SuccessGreen)
        LabeledProgressBar(label = "Netačni odgovori", value = 0.22f, color = ErrorRed)
        Text("Bez odgovora: 10%", color = MediumGray, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun SpojniceStats() {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Prosečni bodovi: 6–14 bod./rundi", color = LightGray, style = MaterialTheme.typography.bodySmall)
        LabeledProgressBar(label = "Uspešno povezani pojmovi", value = 0.74f, color = PrimaryBlueLight)
    }
}

@Composable
private fun MojBrojStats() {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Prosečni bodovi: 5–10 bod./rundi", color = LightGray, style = MaterialTheme.typography.bodySmall)
        LabeledProgressBar(label = "Pronađen tačan broj", value = 0.55f, color = Gold)
    }
}

@Composable
private fun KorakPoKorakStats() {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Prosečni bodovi: 8–16 bod./rundi", color = LightGray, style = MaterialTheme.typography.bodySmall)
        val stepLabels = listOf("Korak 1", "Korak 2", "Korak 3", "Korak 4", "Korak 5", "Korak 6", "Korak 7")
        val stepValues = listOf(0.05f, 0.12f, 0.20f, 0.35f, 0.50f, 0.65f, 0.80f)
        stepLabels.zip(stepValues).forEach { (label, value) ->
            LabeledProgressBar(label = label, value = value, color = WarningOrange)
        }
    }
}

@Composable
private fun AsocijacijeStats() {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Prosečni bodovi: 18–40 bod./rundi", color = LightGray, style = MaterialTheme.typography.bodySmall)
        LabeledProgressBar(label = "Rešene asocijacije", value = 0.48f, color = GoldLight)
        LabeledProgressBar(label = "Nerešene asocijacije", value = 0.52f, color = DarkGray)
    }
}

@Composable
private fun SkockoStats() {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Prosečni bodovi: 10–18 bod./rundi", color = LightGray, style = MaterialTheme.typography.bodySmall)
        val attemptLabels = listOf("1-2. pokušaj", "3-4. pokušaj", "5-6. pokušaj", "Nije pogođeno")
        val attemptValues = listOf(0.15f, 0.35f, 0.30f, 0.20f)
        val attemptColors = listOf(SuccessGreen, Gold, WarningOrange, ErrorRed)
        attemptLabels.zip(attemptValues).zip(attemptColors).forEach { (pair, color) ->
            LabeledProgressBar(label = pair.first, value = pair.second, color = color)
        }
    }
}

@Composable
private fun LabeledProgressBar(label: String, value: Float, color: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = LightGray, style = MaterialTheme.typography.labelSmall)
            Text("${(value * 100).toInt()}%", color = color, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        }
        LinearProgressIndicator(
            progress = value,
            modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(4.dp)),
            color = color,
            trackColor = DarkGray
        )
    }
}

@Composable
private fun LogoutButton(onClick: () -> Unit) {
    Box(modifier = Modifier.padding(horizontal = 16.dp)) {
        OutlinedButton(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, ErrorRed.copy(alpha = 0.6f))
        ) {
            Icon(Icons.Default.Logout, null, tint = ErrorRed, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("ODJAVI SE", color = ErrorRed, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun LogoutDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NavyCard,
        title = { Text("Odjava", color = White, fontWeight = FontWeight.Bold) },
        text = { Text("Da li ste sigurni da se želite odjaviti?", color = LightGray) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("ODJAVI SE", color = ErrorRed, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Otkaži", color = LightGray)
            }
        }
    )
}

@Composable
private fun AvatarPickerDialog(onDismiss: () -> Unit) {
    val avatarIcons = listOf(
        Icons.Default.Person, Icons.Default.Face, Icons.Default.SportsEsports,
        Icons.Default.Star, Icons.Default.EmojiEvents, Icons.Default.Psychology
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NavyCard,
        title = { Text("Odaberi avatar", color = White, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text("Izaberite jednu od ikonica:", color = LightGray, style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    avatarIcons.take(3).forEach { icon ->
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .background(PrimaryBlue)
                                .border(2.dp, MediumGray, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(icon, null, tint = White, modifier = Modifier.size(28.dp))
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    avatarIcons.drop(3).forEach { icon ->
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .background(PrimaryBlue)
                                .border(2.dp, MediumGray, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(icon, null, tint = White, modifier = Modifier.size(28.dp))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Potvrdi", color = PrimaryBlueBright, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Otkaži", color = LightGray)
            }
        }
    )
}

@Composable
private fun BottomNavBar(
    onHomeClick: () -> Unit,
    onRankingClick: () -> Unit,
    onFriendsClick: () -> Unit,
    onChatClick: () -> Unit
) {
    NavigationBar(containerColor = NavyLight) {
        NavigationBarItem(
            selected = false,
            onClick = onHomeClick,
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
            selected = true,
            onClick = {},
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
            onClick = onRankingClick,
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
            onClick = onFriendsClick,
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
            onClick = onChatClick,
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
