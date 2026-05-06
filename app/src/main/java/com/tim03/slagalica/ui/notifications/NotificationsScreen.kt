package com.tim03.slagalica.ui.notifications

import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tim03.slagalica.ui.theme.*

enum class NotifChannel { CHAT, RANKING, REWARD, OTHER }
enum class NotifFilter { ALL, UNREAD, CHAT, RANKING, REWARD, OTHER }

data class NotificationItem(
    val id: Int,
    val channel: NotifChannel,
    val title: String,
    val message: String,
    val time: String,
    val isRead: Boolean
)

private val mockNotifications = listOf(
    NotificationItem(1, NotifChannel.CHAT, "Poruka od Marko_BG", "Zdravo, hoćeš da odigramo partiju?", "pre 2 min", false),
    NotificationItem(2, NotifChannel.RANKING, "Nedeljni rezultati!", "Zauzeli ste 3. mesto na nedeljnoj rang listi.", "pre 1 sat", false),
    NotificationItem(3, NotifChannel.REWARD, "Nagrada!", "Dobili ste 2 tokena za 3. mesto na rang listi.", "pre 1 sat", false),
    NotificationItem(4, NotifChannel.OTHER, "Poziv za partiju", "Marko_BG vas je pozvao na prijateljsku partiju.", "pre 3 sata", true),
    NotificationItem(5, NotifChannel.RANKING, "Mesečni rezultati", "Zauzeli ste 5. mesto na mesečnoj rang listi.", "pre 2 dana", true),
    NotificationItem(6, NotifChannel.REWARD, "Nova liga!", "Prešli ste u 1. ligu! Čestitamo!", "pre 3 dana", true),
    NotificationItem(7, NotifChannel.CHAT, "Poruka od Ana_NS", "Gde si? Igramo?", "pre 4 dana", true),
    NotificationItem(8, NotifChannel.OTHER, "Dnevne misije", "Završili ste sve dnevne misije. Dobijate bonus!", "pre 5 dana", true)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(onBackClick: () -> Unit) {
    var activeFilter by remember { mutableStateOf(NotifFilter.ALL) }
    var notifications by remember { mutableStateOf(mockNotifications) }

    val filtered = when (activeFilter) {
        NotifFilter.ALL -> notifications
        NotifFilter.UNREAD -> notifications.filter { !it.isRead }
        NotifFilter.CHAT -> notifications.filter { it.channel == NotifChannel.CHAT }
        NotifFilter.RANKING -> notifications.filter { it.channel == NotifChannel.RANKING }
        NotifFilter.REWARD -> notifications.filter { it.channel == NotifChannel.REWARD }
        NotifFilter.OTHER -> notifications.filter { it.channel == NotifChannel.OTHER }
    }

    val unreadCount = notifications.count { !it.isRead }

    Scaffold(
        containerColor = Navy,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Notifikacije", fontWeight = FontWeight.Bold, color = White)
                        if (unreadCount > 0) Text("$unreadCount nepročitanih", style = MaterialTheme.typography.labelSmall, color = LightGray)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, null, tint = White)
                    }
                },
                actions = {
                    TextButton(onClick = {
                        notifications = notifications.map { it.copy(isRead = true) }
                    }) {
                        Text("Označi sve", color = PrimaryBlueLight, fontSize = 12.sp)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = NavyLight)
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            LazyRow(
                modifier = Modifier.fillMaxWidth().background(NavyLight).padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(NotifFilter.values()) { filter ->
                    FilterChip(
                        selected = activeFilter == filter,
                        onClick = { activeFilter = filter },
                        label = {
                            Text(
                                when (filter) {
                                    NotifFilter.ALL -> "Sve"
                                    NotifFilter.UNREAD -> "Nepročitane"
                                    NotifFilter.CHAT -> "Čet"
                                    NotifFilter.RANKING -> "Rangiranje"
                                    NotifFilter.REWARD -> "Nagrade"
                                    NotifFilter.OTHER -> "Ostalo"
                                },
                                fontSize = 12.sp
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = PrimaryBlue,
                            selectedLabelColor = White,
                            containerColor = NavyCard,
                            labelColor = LightGray
                        )
                    )
                }
            }

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
                        NotificationCard(
                            notif = notif,
                            onMarkRead = {
                                notifications = notifications.map { n ->
                                    if (n.id == notif.id) n.copy(isRead = true) else n
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationCard(notif: NotificationItem, onMarkRead: () -> Unit) {
    val channelIcon: ImageVector = when (notif.channel) {
        NotifChannel.CHAT -> Icons.Default.Chat
        NotifChannel.RANKING -> Icons.Default.Leaderboard
        NotifChannel.REWARD -> Icons.Default.EmojiEvents
        NotifChannel.OTHER -> Icons.Default.Info
    }
    val channelColor = when (notif.channel) {
        NotifChannel.CHAT -> PrimaryBlueBright
        NotifChannel.RANKING -> Gold
        NotifChannel.REWARD -> SuccessGreen
        NotifChannel.OTHER -> MediumGray
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (!notif.isRead) NavyCard else NavyLight
        ),
        border = if (!notif.isRead) androidx.compose.foundation.BorderStroke(1.dp, PrimaryBlue.copy(alpha = 0.4f)) else null
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier.size(42.dp).clip(CircleShape).background(channelColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(channelIcon, null, tint = channelColor, modifier = Modifier.size(22.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text(notif.title, color = White, fontWeight = if (!notif.isRead) FontWeight.Bold else FontWeight.Normal, style = MaterialTheme.typography.bodyMedium)
                    if (!notif.isRead) {
                        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(PrimaryBlueBright))
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(notif.message, color = LightGray, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AccessTime, null, tint = MediumGray, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(notif.time, color = MediumGray, style = MaterialTheme.typography.labelSmall)
                    }
                    if (!notif.isRead) {
                        TextButton(onClick = onMarkRead, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)) {
                            Text("Označi pročitano", color = PrimaryBlueLight, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}
