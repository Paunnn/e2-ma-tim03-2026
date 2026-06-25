package com.tim03.slagalica.ui.region

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tim03.slagalica.data.repository.RegionLeaderboardEntry
import com.tim03.slagalica.data.repository.RegionStats
import com.tim03.slagalica.ui.components.MozaikBottomNav
import com.tim03.slagalica.ui.theme.*
import com.tim03.slagalica.viewmodel.RegionViewModel

private val Silver = Color(0xFFC0C0C0)
private val Bronze = Color(0xFFCD7F32)

private fun rankColor(rank: Int) = when (rank) {
    1 -> Gold
    2 -> Silver
    3 -> Bronze
    else -> MediumGray
}

@Composable
fun RegionScreen(
    onHomeClick: () -> Unit = {},
    onLeaderboardClick: () -> Unit = {},
    onFriendsClick: () -> Unit = {},
    onProfileClick: () -> Unit = {},
    vm: RegionViewModel = viewModel()
) {
    val state by vm.uiState.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = Navy,
        bottomBar = {
            MozaikBottomNav(
                selectedIndex = 2,
                onHomeClick = onHomeClick,
                onLeaderboardClick = onLeaderboardClick,
                onMapClick = {},
                onFriendsClick = onFriendsClick,
                onProfileClick = onProfileClick
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Map, contentDescription = null, tint = PrimaryBlueBright, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(8.dp))
                Text("Mapa Srbije", color = White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { vm.loadLeaderboard() }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Osvezi", tint = MediumGray)
                }
            }
            Text("Mesečna rang lista po regionima", color = LightGray, fontSize = 13.sp)
            Spacer(Modifier.height(12.dp))

            if (state.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = PrimaryBlueBright)
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(state.leaderboard) { _, region ->
                        RegionCard(
                            stats = region,
                            isMyRegion = region.region == state.myRegion,
                            onClick = { vm.selectRegion(region) }
                        )
                    }
                    item { Spacer(Modifier.height(8.dp)) }
                }
            }
        }

        // Region detail dialog
        val selected = state.selectedRegion
        if (selected != null) {
            RegionDetailDialog(
                stats = selected,
                players = state.regionPlayers,
                isLoadingPlayers = state.isLoadingPlayers,
                onDismiss = { vm.clearSelectedRegion() }
            )
        }
    }
}

@Composable
private fun RegionCard(stats: RegionStats, isMyRegion: Boolean, onClick: () -> Unit) {
    val borderColor = if (isMyRegion) PrimaryBlueBright else Color.Transparent
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, borderColor, RoundedCornerShape(14.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = NavyCard)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Rank badge
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(rankColor(stats.rank).copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "#${stats.rank}",
                    color = rankColor(stats.rank),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 14.sp
                )
            }
            Spacer(Modifier.width(12.dp))
            // Region icon + name
            Text(stats.icon, fontSize = 28.sp)
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stats.region, color = White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    if (isMyRegion) {
                        Spacer(Modifier.width(6.dp))
                        Text("(moj)", color = PrimaryBlueBright, fontSize = 11.sp)
                    }
                }
                Text(
                    "${stats.activePlayers} aktivnih / ${stats.totalPlayers} ukupno",
                    color = MediumGray,
                    fontSize = 12.sp
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Star, contentDescription = null, tint = Gold, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(2.dp))
                    Text("${stats.totalMonthlyStars}", color = Gold, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
                Text("mesečno", color = MediumGray, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun RegionDetailDialog(
    stats: RegionStats,
    players: List<RegionLeaderboardEntry>,
    isLoadingPlayers: Boolean,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NavyCard,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stats.icon, fontSize = 24.sp)
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(stats.region, color = White, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
                    Text("Rang #${stats.rank} • ${stats.totalMonthlyStars}★ mesečno", color = LightGray, fontSize = 12.sp)
                }
            }
        },
        text = {
            if (isLoadingPlayers) {
                Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = PrimaryBlueBright)
                }
            } else if (players.isEmpty()) {
                Text("Nema aktivnih igrača u ovom regionu.", color = MediumGray, fontSize = 13.sp)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    players.take(10).forEach { player ->
                        PlayerRow(player)
                    }
                    if (players.size > 10) {
                        Text("...i još ${players.size - 10} igrača", color = MediumGray, fontSize = 11.sp)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Zatvori", color = PrimaryBlueBright)
            }
        }
    )
}

@Composable
private fun PlayerRow(player: RegionLeaderboardEntry) {
    val frameColor = when (player.rank) {
        1 -> Gold
        2 -> Silver
        3 -> Bronze
        else -> null
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "#${player.rank}",
            color = rankColor(player.rank),
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            modifier = Modifier.width(32.dp)
        )
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(NavyCardLight)
                .then(
                    if (frameColor != null)
                        Modifier.border(2.dp, frameColor, CircleShape)
                    else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Person, contentDescription = null, tint = LightGray, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(8.dp))
        Text(player.username, color = White, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Star, contentDescription = null, tint = Gold, modifier = Modifier.size(12.dp))
            Spacer(Modifier.width(2.dp))
            Text("${player.monthlyStars}", color = Gold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}
