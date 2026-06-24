package com.tim03.slagalica.ui.leaderboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tim03.slagalica.data.model.LeaderboardEntry
import com.tim03.slagalica.ui.theme.*
import com.tim03.slagalica.viewmodel.LeaderboardViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeaderboardScreen(
    onBack: () -> Unit,
    vm: LeaderboardViewModel = viewModel()
) {
    val state by vm.uiState.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = Navy,
        topBar = {
            TopAppBar(
                title = { Text("Rang lista", color = White, fontWeight = FontWeight.ExtraBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, null, tint = LightGray)
                    }
                },
                actions = {
                    IconButton(onClick = { vm.refresh() }) {
                        Icon(Icons.Default.Refresh, null, tint = LightGray)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Navy)
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Tabs
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(NavyLight),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                listOf("Nedeljni", "Mesečni").forEachIndexed { i, label ->
                    val selected = state.selectedTab == i
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (selected) PrimaryBlueBright else Color.Transparent)
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        TextButton(onClick = { vm.selectTab(i) }) {
                            Text(
                                label,
                                color = if (selected) White else LightGray,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }

            // Cycle date range
            val (start, end) = if (state.selectedTab == 0) state.weekRange else state.monthRange
            if (start.isNotEmpty()) {
                Text(
                    "$start — $end",
                    color = MediumGray,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }

            if (state.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = PrimaryBlueBright)
                }
                return@Scaffold
            }

            val entries = if (state.selectedTab == 0) state.weeklyEntries else state.monthlyEntries

            if (entries.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Nema podataka", color = MediumGray, fontSize = 16.sp)
                        Text(
                            "Odigrajte partiju da biste se pojavili na rang listi",
                            color = MediumGray, fontSize = 12.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
                return@Scaffold
            }

            // Top 3 podium
            if (entries.size >= 3) {
                TopThreePodium(
                    entries = entries.take(3),
                    myUid = state.myUid,
                    isWeekly = state.selectedTab == 0
                )
            }

            // Rest of the list
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val restEntries = if (entries.size > 3) entries.drop(3) else emptyList()
                itemsIndexed(restEntries) { _, entry ->
                    LeaderboardRow(
                        entry = entry,
                        isMe = entry.uid == state.myUid,
                        cycleStars = if (state.selectedTab == 0) entry.weeklyStars else entry.monthlyStars
                    )
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }
}

@Composable
private fun TopThreePodium(entries: List<LeaderboardEntry>, myUid: String, isWeekly: Boolean) {
    val podiumColors = listOf(Gold, LightGray, Color(0xFFCD7F32))
    val podiumHeights = listOf(90.dp, 70.dp, 60.dp)
    val order = listOf(1, 0, 2) // 2nd, 1st, 3rd visual order

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Bottom
    ) {
        order.forEach { idx ->
            if (idx >= entries.size) { Box(Modifier.weight(1f)) ; return@forEach }
            val entry = entries[idx]
            val color = podiumColors[idx]
            val isMe = entry.uid == myUid

            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Avatar
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(
                            if (isMe) Brush.linearGradient(listOf(PrimaryBlueBright, Accent5))
                            else Brush.linearGradient(listOf(NavyCard, NavyCardLight))
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        entry.username.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                        color = White, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp
                    )
                }
                Text(
                    entry.username, color = White, fontWeight = FontWeight.Bold, fontSize = 11.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp)
                )
                val stars = if (isWeekly) entry.weeklyStars else entry.monthlyStars
                Text("★ $stars", color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                // Podium block
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp)
                        .height(podiumHeights[idx])
                        .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                        .background(color.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "${idx + 1}",
                        color = color, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun LeaderboardRow(entry: LeaderboardEntry, isMe: Boolean, cycleStars: Int) {
    val leagueLabel = leagueName(entry.league)
    val leagueColor = leagueColor(entry.league)

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isMe) PrimaryBlueBright.copy(alpha = 0.12f) else NavyLight
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Rank
            Text(
                "#${entry.rank}",
                color = if (isMe) PrimaryBlueBright else MediumGray,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 13.sp,
                modifier = Modifier.width(32.dp)
            )
            // Avatar
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(
                        if (isMe) Brush.linearGradient(listOf(PrimaryBlueBright, Accent5))
                        else Brush.linearGradient(listOf(NavyCard, NavyCardLight))
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    entry.username.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                    color = White, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp
                )
            }
            // Name + League
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    entry.username,
                    color = if (isMe) White else OffWhite,
                    fontWeight = if (isMe) FontWeight.ExtraBold else FontWeight.SemiBold,
                    fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Text(
                    leagueLabel, color = leagueColor, fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            // Stars
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("★", color = Gold, fontSize = 14.sp)
                Text(
                    " $cycleStars",
                    color = Gold, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp
                )
            }
        }
    }
}

private fun leagueName(league: Int) = when (league) {
    0 -> "Početnik"; 1 -> "Bronza"; 2 -> "Srebro"
    3 -> "Zlato"; 4 -> "Platina"; 5 -> "Dijamant"; else -> "Liga $league"
}

private fun leagueColor(league: Int) = when (league) {
    0 -> Color(0xFF9E9E9E); 1 -> Color(0xFFCD7F32)
    2 -> Color(0xFFC0C0C0); 3 -> Gold
    4 -> Color(0xFFE5E4E2); 5 -> Color(0xFF4FC3F7); else -> MediumGray
}
