package com.tim03.slagalica.ui.notifications

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tim03.slagalica.data.model.NotificationModel
import com.tim03.slagalica.ui.theme.*
import com.tim03.slagalica.viewmodel.NotifFilter
import com.tim03.slagalica.viewmodel.NotificationsViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    onBackClick: () -> Unit,
    viewModel: NotificationsViewModel = viewModel()
) {
    val filtered by viewModel.filteredNotifications.collectAsStateWithLifecycle()
    val unreadCount by viewModel.unreadCount.collectAsStateWithLifecycle()
    val activeFilter by viewModel.filter.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = Navy,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Notifikacije", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = White)
                        if (unreadCount > 0) Text("$unreadCount nepročitanih", fontSize = 11.sp, color = MediumGray)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, null, tint = White)
                    }
                },
                actions = {
                    TextButton(onClick = { viewModel.addTestNotifications() }) {
                        Text("+ Test", color = Gold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    TextButton(onClick = { viewModel.markAllAsRead() }) {
                        Text("Označi sve", color = PrimaryBlueBright, fontSize = 12.sp)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = NavyLight)
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {

            // ── Filter chips ──────────────────────────────────────
            LazyRow(
                modifier = Modifier.fillMaxWidth().background(NavyLight).padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(NotifFilter.values()) { filter ->
                    val selected = activeFilter == filter
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(if (selected) PrimaryBlueBright else NavyCard)
                            .clickable { viewModel.setFilter(filter) }
                            .padding(horizontal = 12.dp, vertical = 7.dp)
                    ) {
                        Text(
                            text = when (filter) {
                                NotifFilter.ALL     -> "Sve"
                                NotifFilter.UNREAD  -> "Nepročitane"
                                NotifFilter.READ    -> "Pročitane"
                                NotifFilter.CHAT    -> "Čet"
                                NotifFilter.RANKING -> "Rangiranje"
                                NotifFilter.REWARD  -> "Nagrade"
                                NotifFilter.OTHER   -> "Ostalo"
                            },
                            color = if (selected) White else LightGray,
                            fontSize = 12.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }

            // ── List ──────────────────────────────────────────────
            if (filtered.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.NotificationsNone, null, tint = MediumGray, modifier = Modifier.size(64.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Nema notifikacija", color = MediumGray, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filtered, key = { it.id }) { notif ->
                        NotificationCard(notif = notif, onMarkRead = { viewModel.markAsRead(notif.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationCard(notif: NotificationModel, onMarkRead: () -> Unit) {
    val (channelColor, channelIcon, channelLabel) = when (notif.channel) {
        "CHAT"    -> Triple(GameSpojnice,    Icons.Default.Chat,        "ČET")
        "RANKING" -> Triple(Gold,            Icons.Default.Leaderboard,  "RANGIRANJE")
        "REWARD"  -> Triple(GameAsocijacije, Icons.Default.EmojiEvents,  "NAGRADE")
        else      -> Triple(Accent5,         Icons.Default.Info,         "OSTALO")
    }

    val cardBg = Brush.horizontalGradient(
        if (!notif.isRead) listOf(channelColor.copy(alpha = 0.07f), NavyLight, NavyLight)
        else listOf(NavyLight, NavyLight)
    )
    val borderColor = if (!notif.isRead) channelColor.copy(alpha = 0.33f) else LineSoft

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { if (!notif.isRead) onMarkRead() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        border = BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().background(cardBg).padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Channel icon
            Box(
                modifier = Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background(channelColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(channelIcon, null, tint = channelColor, modifier = Modifier.size(20.dp))
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                // Channel label + unread dot
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(channelLabel, color = channelColor, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.8.sp)
                    if (!notif.isRead) {
                        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(channelColor))
                    }
                }
                // Title
                Text(
                    notif.title, color = White,
                    fontWeight = if (!notif.isRead) FontWeight.Bold else FontWeight.Normal,
                    fontSize = 13.sp, modifier = Modifier.padding(top = 1.dp)
                )
                // Message
                Text(notif.message, color = LightGray, fontSize = 11.sp, maxLines = 2, modifier = Modifier.padding(top = 2.dp))
                // Time + action
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 5.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(formatTimestamp(notif.timestamp), color = MediumGray, fontSize = 10.sp)
                    if (!notif.isRead) {
                        TextButton(
                            onClick = onMarkRead,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text("Označi pročitano", color = PrimaryBlueBright, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

private fun formatTimestamp(ts: Long): String {
    val diff = System.currentTimeMillis() - ts
    return when {
        diff < 60_000L      -> "malo pre"
        diff < 3_600_000L   -> "pre ${diff / 60_000} min"
        diff < 86_400_000L  -> "pre ${diff / 3_600_000} h"
        diff < 604_800_000L -> "pre ${diff / 86_400_000} dana"
        else -> SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date(ts))
    }
}
