package com.tim03.slagalica.ui.profile

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tim03.slagalica.data.model.User
import com.tim03.slagalica.ui.components.MozaikBottomNav
import com.tim03.slagalica.ui.theme.*
import com.tim03.slagalica.util.QrCodeUtils
import com.tim03.slagalica.viewmodel.ProfileViewModel

private val avatarIcons = listOf(
    Icons.Default.Person,
    Icons.Default.Face,
    Icons.Default.SportsEsports,
    Icons.Default.Star,
    Icons.Default.EmojiEvents,
    Icons.Default.Psychology
)

private fun leagueName(league: Int) = when (league) {
    0 -> "Bronza"
    1 -> "Srebro"
    2 -> "Zlato"
    3 -> "Dijamant"
    4 -> "Majstor"
    else -> "Liga $league"
}

private fun leagueColor(league: Int) = when (league) {
    0 -> Color(0xFFCD7F32)
    1 -> Color(0xFFC0C0C0)
    2 -> Color(0xFFFFc857)
    3 -> Color(0xFF4F7CFF)
    4 -> Color(0xFFC599FF)
    else -> Color(0xFFFFc857)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onNavigateHome: () -> Unit = {},
    onLogout: () -> Unit = {},
    onChangePassword: () -> Unit = {},
    viewModel: ProfileViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showAvatarDialog by remember { mutableStateOf(false) }

    if (showLogoutDialog) {
        LogoutDialog(
            onConfirm = {
                showLogoutDialog = false
                viewModel.logout()
                onLogout()
            },
            onDismiss = { showLogoutDialog = false }
        )
    }
    if (showAvatarDialog) {
        AvatarPickerDialog(
            currentIndex = state.user?.avatarIndex ?: 0,
            onSelect = { index ->
                viewModel.updateAvatar(index)
                showAvatarDialog = false
            },
            onDismiss = { showAvatarDialog = false }
        )
    }

    Scaffold(
        containerColor = Navy,
        topBar = {
            TopAppBar(
                title = {
                    Text("PROFIL", fontWeight = FontWeight.ExtraBold, color = White, letterSpacing = 2.sp)
                },
                actions = {
                    if (!state.isLoading && state.user != null) {
                        val user = state.user!!
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(end = 12.dp)
                        ) {
                            Icon(Icons.Default.Token, null, tint = GoldLight, modifier = Modifier.size(18.dp))
                            Text("${user.tokens}", color = White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(Icons.Default.Star, null, tint = Gold, modifier = Modifier.size(18.dp))
                            Text("${user.stars}", color = White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(shape = RoundedCornerShape(8.dp), color = NavyCard) {
                                Text(
                                    leagueName(user.league),
                                    color = leagueColor(user.league),
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = NavyLight)
            )
        },
        bottomBar = {
            MozaikBottomNav(selectedIndex = 4, onHomeClick = onNavigateHome)
        }
    ) { padding ->
        when {
            state.isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator(color = PrimaryBlueBright)
                        Text("Učitavanje profila...", color = LightGray)
                    }
                }
            }
            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                ) {
                    ProfileHeaderSection(
                        user = state.user,
                        onEditAvatarClick = { showAvatarDialog = true }
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    StatisticsSection(user = state.user)
                    Spacer(modifier = Modifier.height(16.dp))
                    ChangePasswordButton(onClick = onChangePassword)
                    Spacer(modifier = Modifier.height(8.dp))
                    LogoutButton(onClick = { showLogoutDialog = true })
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun ProfileHeaderSection(user: User?, onEditAvatarClick: () -> Unit) {
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
                    val iconIndex = user?.avatarIndex?.coerceIn(0, avatarIcons.size - 1) ?: 0
                    Icon(avatarIcons[iconIndex], null, tint = White, modifier = Modifier.size(56.dp))
                }
                IconButton(
                    onClick = onEditAvatarClick,
                    modifier = Modifier.size(30.dp).clip(CircleShape).background(NavyCard).border(1.dp, MediumGray, CircleShape)
                ) {
                    Icon(Icons.Default.Edit, null, tint = LightGray, modifier = Modifier.size(16.dp))
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text(user?.username ?: "Gost", color = White, fontWeight = FontWeight.Bold, fontSize = 22.sp)
            Text(user?.email ?: "", color = LightGray, style = MaterialTheme.typography.bodySmall)

            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!user?.region.isNullOrBlank()) {
                    InfoChip(icon = Icons.Default.LocationOn, label = user!!.region, color = PrimaryBlueLight)
                }
                InfoChip(
                    icon = Icons.Default.EmojiEvents,
                    label = leagueName(user?.league ?: 0),
                    color = leagueColor(user?.league ?: 0)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                StatSummaryItem(value = "${user?.tokens ?: 0}", label = "Tokeni", icon = Icons.Default.Token, color = GoldLight)
                StatSummaryItem(value = "${user?.stars ?: 0}", label = "Zvezde", icon = Icons.Default.Star, color = Gold)
                val totalRounds = (user?.koZnaZnaRounds ?: 0) + (user?.spojniceRounds ?: 0) +
                (user?.mojBrojRounds ?: 0) + (user?.korakRounds ?: 0) + (user?.skockoRounds ?: 0)
                StatSummaryItem(value = "$totalRounds", label = "Rundi", icon = Icons.Default.Games, color = PrimaryBlueLight)
            }

            if (user != null) {
                Spacer(modifier = Modifier.height(16.dp))
                RealQrCodeCard(uid = user.uid)
            }
        }
    }
}

@Composable
private fun RealQrCodeCard(uid: String) {
    val qrBitmap = remember(uid) {
        if (uid.isNotBlank()) QrCodeUtils.generateQrCode(uid).asImageBitmap() else null
    }
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
                modifier = Modifier.size(72.dp).clip(RoundedCornerShape(8.dp)).background(White),
                contentAlignment = Alignment.Center
            ) {
                if (qrBitmap != null) {
                    Image(
                        bitmap = qrBitmap,
                        contentDescription = "QR kod",
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(Icons.Default.QrCode2, null, tint = DarkGray, modifier = Modifier.size(48.dp))
                }
            }
            Column {
                Text("Moj QR kod", color = White, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    "Prijatelji skeniraju ovaj kod da vas dodaju u listu prijatelja.",
                    color = LightGray, style = MaterialTheme.typography.bodySmall
                )
            }
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
private fun StatisticsSection(user: User?) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Text("Statistika", color = White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(12.dp))

        Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = NavyCard)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OverallStatsRow(user = user)
                HorizontalDivider(color = MediumGray.copy(alpha = 0.3f))
                GameStatItem(title = "Ko zna zna", icon = Icons.Default.Quiz, iconColor = GameKoZnaZna) {
                    KoZnaZnaStats(user = user)
                }
                HorizontalDivider(color = MediumGray.copy(alpha = 0.3f))
                GameStatItem(title = "Spojnice", icon = Icons.Default.Link, iconColor = GameSpojnice) {
                    SpojniceStats(user = user)
                }
                HorizontalDivider(color = MediumGray.copy(alpha = 0.3f))
                GameStatItem(title = "Moj broj", icon = Icons.Default.Calculate, iconColor = GameMojBroj) {
                    MojBrojStats(user = user)
                }
                HorizontalDivider(color = MediumGray.copy(alpha = 0.3f))
                GameStatItem(title = "Korak po korak", icon = Icons.Default.Stairs, iconColor = GameKorak) {
                    KorakPoKorakStats(user = user)
                }
                HorizontalDivider(color = MediumGray.copy(alpha = 0.3f))
                GameStatItem(title = "Asocijacije", icon = Icons.Default.GridView, iconColor = GameAsocijacije) {
                    AsocijacijeStats(user = user)
                }
                HorizontalDivider(color = MediumGray.copy(alpha = 0.3f))
                GameStatItem(title = "Skočko", icon = Icons.Default.Casino, iconColor = GameSkocko) {
                    SkockoStats(user = user)
                }
            }
        }
    }
}

@Composable
private fun OverallStatsRow(user: User?) {
    val partijaWon = user?.partijaWon ?: 0
    val partijaLost = user?.partijaLost ?: 0
    val partijaDrawn = user?.partijaDrawn ?: 0
    val totalPartija = partijaWon + partijaLost + partijaDrawn
    val winRatio = if (totalPartija > 0) partijaWon.toFloat() / totalPartija else 0f
    val lossRatio = if (totalPartija > 0) partijaLost.toFloat() / totalPartija else 0f

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Opšta statistika", color = LightGray, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            MiniStatBox("$totalPartija", "Partija")
            MiniStatBox("$partijaWon", "Pobede")
            MiniStatBox("$partijaLost", "Porazi")
            if (partijaDrawn > 0) MiniStatBox("$partijaDrawn", "Nerešeno")
        }
        if (totalPartija > 0) {
            LabeledProgressBar(label = "Pobede", value = winRatio, color = SuccessGreen)
            LabeledProgressBar(label = "Porazi", value = lossRatio, color = ErrorRed)
        } else {
            Text("Nema odigranih partija. Kliknite 'Započni partiju' na početnoj strani.", color = MediumGray, style = MaterialTheme.typography.bodySmall)
        }
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
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(iconColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = iconColor, modifier = Modifier.size(18.dp))
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text(title, color = White, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = { expanded = !expanded }, modifier = Modifier.size(24.dp)) {
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    null, tint = LightGray, modifier = Modifier.size(20.dp)
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
private fun KoZnaZnaStats(user: User?) {
    val correct = user?.koZnaZnaCorrect ?: 0
    val incorrect = user?.koZnaZnaIncorrect ?: 0
    val rounds = user?.koZnaZnaRounds ?: 0
    val total = correct + incorrect
    val correctRatio = if (total > 0) correct.toFloat() / total else 0f
    val incorrectRatio = if (total > 0) incorrect.toFloat() / total else 0f

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Odigrane runde: $rounds  ·  Tačno: $correct  ·  Netačno: $incorrect", color = LightGray, style = MaterialTheme.typography.bodySmall)
        LabeledProgressBar(label = "Tačni odgovori", value = correctRatio, color = SuccessGreen)
        LabeledProgressBar(label = "Netačni odgovori", value = incorrectRatio, color = ErrorRed)
    }
}

@Composable
private fun SpojniceStats(user: User?) {
    val connected = user?.spojniceConnected ?: 0
    val totalPairs = user?.spojniceTotalPairs ?: 0
    val rounds = user?.spojniceRounds ?: 0
    val connectedRatio = if (totalPairs > 0) connected.toFloat() / totalPairs else 0f

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Odigrane runde: $rounds  ·  Tačno: $connected / $totalPairs parova", color = LightGray, style = MaterialTheme.typography.bodySmall)
        LabeledProgressBar(label = "Uspešno povezani pojmovi", value = connectedRatio, color = GameSpojnice)
    }
}

@Composable
private fun MojBrojStats(user: User?) {
    val hits = user?.mojBrojHits ?: 0
    val rounds = user?.mojBrojRounds ?: 0
    if (rounds == 0) {
        Text("Nema odigranih rundi.", color = MediumGray, style = MaterialTheme.typography.bodySmall)
        return
    }
    val ratio = hits.toFloat() / rounds
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Tačan pogodak: $hits / $rounds rundi", color = LightGray, style = MaterialTheme.typography.bodySmall)
        LabeledProgressBar(label = "Pronađen tačan broj", value = ratio, color = GameMojBroj)
    }
}

@Composable
private fun KorakPoKorakStats(user: User?) {
    val rounds = user?.korakRounds ?: 0
    if (rounds == 0) {
        Text("Nema odigranih rundi.", color = MediumGray, style = MaterialTheme.typography.bodySmall)
        return
    }
    val steps = listOf(
        user?.korakStep1 ?: 0, user?.korakStep2 ?: 0, user?.korakStep3 ?: 0,
        user?.korakStep4 ?: 0, user?.korakStep5 ?: 0, user?.korakStep6 ?: 0,
        user?.korakStep7 ?: 0
    )
    val totalCorrect = steps.sum()
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Tačno pogođenih: $totalCorrect / $rounds rundi", color = LightGray, style = MaterialTheme.typography.bodySmall)
        steps.forEachIndexed { i, count ->
            val pct = count.toFloat() / rounds
            LabeledProgressBar(label = "Korak ${i + 1}", value = pct, color = GameKorak)
        }
    }
}

@Composable
private fun AsocijacijeStats(user: User?) {
    val solved = user?.asocijacijeSolved ?: 0
    val total = user?.asocijacijeTotal ?: 0
    if (total == 0) {
        Text("Nema odigranih igara.", color = MediumGray, style = MaterialTheme.typography.bodySmall)
        return
    }
    val ratio = solved.toFloat() / total
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Rešeno: $solved / $total pokušaja", color = LightGray, style = MaterialTheme.typography.bodySmall)
        LabeledProgressBar(label = "Tačni odgovori", value = ratio, color = GameAsocijacije)
        LabeledProgressBar(label = "Netačni odgovori", value = 1f - ratio, color = ErrorRed)
    }
}

@Composable
private fun SkockoStats(user: User?) {
    val rounds = user?.skockoRounds ?: 0
    if (rounds == 0) {
        Text("Nema odigranih rundi.", color = MediumGray, style = MaterialTheme.typography.bodySmall)
        return
    }
    val attempts = listOf(
        user?.skockoAttempt1 ?: 0, user?.skockoAttempt2 ?: 0, user?.skockoAttempt3 ?: 0,
        user?.skockoAttempt4 ?: 0, user?.skockoAttempt5 ?: 0, user?.skockoAttempt6 ?: 0
    )
    val totalSolved = attempts.sum()
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Pogođeno: $totalSolved / $rounds rundi", color = LightGray, style = MaterialTheme.typography.bodySmall)
        attempts.forEachIndexed { i, count ->
            val pct = count.toFloat() / rounds
            LabeledProgressBar(label = "Pokušaj ${i + 1}", value = pct, color = GameSkocko)
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
            progress = { value },
            modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(4.dp)),
            color = color,
            trackColor = DarkGray
        )
    }
}

@Composable
private fun ChangePasswordButton(onClick: () -> Unit) {
    Box(modifier = Modifier.padding(horizontal = 16.dp)) {
        OutlinedButton(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, MediumGray)
        ) {
            Icon(Icons.Default.Lock, null, tint = LightGray, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("PROMENA LOZINKE", color = LightGray, fontWeight = FontWeight.Bold)
        }
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
            TextButton(onClick = onConfirm) { Text("ODJAVI SE", color = ErrorRed, fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Otkaži", color = LightGray) }
        }
    )
}

@Composable
private fun AvatarPickerDialog(currentIndex: Int, onSelect: (Int) -> Unit, onDismiss: () -> Unit) {
    var selected by remember { mutableStateOf(currentIndex) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NavyCard,
        title = { Text("Odaberi avatar", color = White, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text("Izaberite jednu od ikonica:", color = LightGray, style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    avatarIcons.take(3).forEachIndexed { i, icon ->
                        val isSelected = selected == i
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .background(if (isSelected) PrimaryBlueBright else PrimaryBlue)
                                .border(2.dp, if (isSelected) Gold else MediumGray, CircleShape)
                                .clickable { selected = i },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(icon, null, tint = White, modifier = Modifier.size(28.dp))
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    avatarIcons.drop(3).forEachIndexed { i, icon ->
                        val realIndex = i + 3
                        val isSelected = selected == realIndex
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .background(if (isSelected) PrimaryBlueBright else PrimaryBlue)
                                .border(2.dp, if (isSelected) Gold else MediumGray, CircleShape)
                                .clickable { selected = realIndex },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(icon, null, tint = White, modifier = Modifier.size(28.dp))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSelect(selected) }) { Text("Potvrdi", color = PrimaryBlueBright, fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Otkaži", color = LightGray) }
        }
    )
}

