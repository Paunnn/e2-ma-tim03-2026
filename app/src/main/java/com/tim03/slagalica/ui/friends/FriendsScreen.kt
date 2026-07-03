package com.tim03.slagalica.ui.friends

import android.app.Activity
import android.content.ContextWrapper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.zxing.integration.android.IntentIntegrator
import com.tim03.slagalica.data.model.FriendInfo
import com.tim03.slagalica.data.model.Friendship
import com.tim03.slagalica.ui.components.MozaikBottomNav
import com.tim03.slagalica.ui.components.avatarIcons
import com.tim03.slagalica.ui.theme.*
import com.tim03.slagalica.util.leagueIcon
import com.tim03.slagalica.util.leagueName
import com.tim03.slagalica.viewmodel.FriendsTab
import com.tim03.slagalica.viewmodel.FriendsViewModel

@Composable
fun FriendsScreen(
    onHomeClick: () -> Unit = {},
    onLeaderboardClick: () -> Unit = {},
    onMapClick: () -> Unit = {},
    onProfileClick: () -> Unit = {},
    onStartFriendlyMatch: (friendUid: String, friendName: String) -> Unit = { _, _ -> },
    vm: FriendsViewModel = viewModel()
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val qrLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val intentResult = IntentIntegrator.parseActivityResult(result.resultCode, result.data)
            val scanned = intentResult?.contents
            if (!scanned.isNullOrBlank()) {
                vm.handleQrScanResult(scanned)
            }
        }
    }

    Scaffold(
        containerColor = Navy,
        bottomBar = {
            MozaikBottomNav(
                selectedIndex = 3,
                onHomeClick = onHomeClick,
                onLeaderboardClick = onLeaderboardClick,
                onMapClick = onMapClick,
                onFriendsClick = {},
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
                Icon(Icons.Default.Group, contentDescription = null, tint = PrimaryBlueBright, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(8.dp))
                Text("Prijatelji", color = White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                if (state.pendingRequests.isNotEmpty()) {
                    Spacer(Modifier.width(8.dp))
                    Badge { Text("${state.pendingRequests.size}") }
                }
            }
            Spacer(Modifier.height(12.dp))

            // Tabs
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FriendsTab.entries.forEach { tab ->
                    val label = when (tab) {
                        FriendsTab.LIST -> "Lista"
                        FriendsTab.SEARCH -> "Pretraga"
                        FriendsTab.REQUESTS -> "Zahtevi (${state.pendingRequests.size})"
                    }
                    val selected = state.tab == tab
                    FilterChip(
                        selected = selected,
                        onClick = { vm.setTab(tab) },
                        label = { Text(label, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = PrimaryBlueBright.copy(alpha = 0.2f),
                            selectedLabelColor = PrimaryBlueBright,
                            containerColor = NavyCard,
                            labelColor = LightGray
                        )
                    )
                }
            }
            Spacer(Modifier.height(8.dp))

            when (state.tab) {
                FriendsTab.LIST -> FriendsList(
                    friends = state.friends,
                    onRemove = { vm.removeFriend(it) },
                    onInvite = { uid, name -> onStartFriendlyMatch(uid, name) }
                )
                FriendsTab.SEARCH -> SearchTab(
                    query = state.searchQuery,
                    onQueryChange = { vm.setSearchQuery(it) },
                    onSearch = { vm.searchUser() },
                    onQrScan = {
                        var ctx = context
                        while (ctx is ContextWrapper && ctx !is Activity) ctx = ctx.baseContext
                        val integrator = IntentIntegrator(ctx as Activity)
                        integrator.setPrompt("Skeniraj QR kod prijatelja")
                        integrator.setBeepEnabled(false)
                        integrator.setOrientationLocked(true)
                        qrLauncher.launch(integrator.createScanIntent())
                    },
                    searchResult = state.searchResult,
                    searchDone = state.searchDone,
                    alreadyFriend = state.friends.any { it.uid == state.searchResult?.uid },
                    requestSent = state.requestSent,
                    isLoading = state.isLoading,
                    errorMsg = state.errorMsg,
                    onSendRequest = { vm.sendRequest(it) }
                )
                FriendsTab.REQUESTS -> RequestsList(
                    requests = state.pendingRequests,
                    onAccept = { vm.acceptRequest(it) },
                    onReject = { vm.rejectRequest(it.id) }
                )
            }
        }
    }
}

@Composable
private fun FriendsList(
    friends: List<FriendInfo>,
    onRemove: (String) -> Unit,
    onInvite: (String, String) -> Unit
) {
    if (friends.isEmpty()) {
        Box(Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) {
            Text("Nema prijatelja. Dodajte ih pretragom ili QR kodom.", color = MediumGray, fontSize = 13.sp)
        }
        return
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(friends, key = { it.uid }) { friend ->
            FriendCard(
                friend = friend,
                onRemove = { onRemove(friend.uid) },
                onInvite = { onInvite(friend.uid, friend.username) }
            )
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun FriendCard(friend: FriendInfo, onRemove: () -> Unit, onInvite: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = NavyCard)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(44.dp).clip(CircleShape).background(NavyCardLight),
                contentAlignment = Alignment.Center
            ) {
                // Friend's actual profile picture (same avatar set as on the profile page).
                val iconIndex = friend.avatarIndex.coerceIn(0, avatarIcons.size - 1)
                Icon(avatarIcons[iconIndex], contentDescription = null, tint = LightGray, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(friend.username, color = White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Star, contentDescription = null, tint = Gold, modifier = Modifier.size(12.dp))
                        Text(" ${friend.stars}", color = Gold, fontSize = 12.sp)
                    }
                    Text("•", color = MediumGray, fontSize = 12.sp)
                    Text("${leagueIcon(friend.league)} ${leagueName(friend.league)}", color = LightGray, fontSize = 12.sp)
                    if (friend.region.isNotBlank()) {
                        Text("•", color = MediumGray, fontSize = 12.sp)
                        Text(friend.region, color = MediumGray, fontSize = 11.sp)
                    }
                }
                // Position on the current monthly leaderboard, not the star count -
                // players without stars this cycle are not ranked yet.
                Text(
                    if (friend.monthlyRank > 0) "Mesečni rang: #${friend.monthlyRank}"
                    else "Mesečni rang: nerangiran",
                    color = MediumGray, fontSize = 11.sp
                )
            }
            Spacer(Modifier.width(4.dp))
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Button(
                    onClick = onInvite,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlueBright),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text("Pozovi", fontSize = 12.sp)
                }
                Box {
                    IconButton(onClick = { expanded = true }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.MoreVert, contentDescription = null, tint = MediumGray, modifier = Modifier.size(16.dp))
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        DropdownMenuItem(
                            text = { Text("Ukloni prijatelja", color = Accent2) },
                            onClick = { expanded = false; onRemove() }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchTab(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onQrScan: () -> Unit,
    searchResult: FriendInfo?,
    searchDone: Boolean,
    alreadyFriend: Boolean,
    requestSent: Boolean,
    isLoading: Boolean,
    errorMsg: String?,
    onSendRequest: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text("Korisničko ime...", color = MediumGray) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PrimaryBlueBright,
                    unfocusedBorderColor = LineColor,
                    focusedTextColor = White,
                    unfocusedTextColor = White,
                    cursorColor = PrimaryBlueBright
                ),
                trailingIcon = {
                    if (query.isNotBlank()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(Icons.Default.Clear, contentDescription = null, tint = MediumGray)
                        }
                    }
                }
            )
            Button(
                onClick = onSearch,
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlueBright),
                enabled = query.isNotBlank() && !isLoading
            ) {
                Icon(Icons.Default.Search, contentDescription = null)
            }
        }

        OutlinedButton(
            onClick = onQrScan,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Accent4),
            border = androidx.compose.foundation.BorderStroke(1.dp, Accent4.copy(alpha = 0.5f))
        ) {
            Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Skeniraj QR kod")
        }

        if (isLoading) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = PrimaryBlueBright, modifier = Modifier.size(32.dp))
            }
        }

        errorMsg?.let {
            Text(it, color = Accent2, fontSize = 13.sp)
        }

        if (searchDone && searchResult != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = NavyCard)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier.size(44.dp).clip(CircleShape).background(NavyCardLight),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Person, contentDescription = null, tint = LightGray, modifier = Modifier.size(24.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(searchResult.username, color = White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("${searchResult.stars}★ • ${leagueIcon(searchResult.league)} ${leagueName(searchResult.league)}", color = LightGray, fontSize = 12.sp)
                    }
                    when {
                        alreadyFriend -> Text("Već ste prijatelji", color = SuccessGreen, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        requestSent -> Text("Poslato ✓", color = SuccessGreen, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        else -> Button(
                            onClick = { onSendRequest(searchResult.uid) },
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlueBright)
                        ) {
                            Text("Dodaj")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RequestsList(requests: List<Friendship>, onAccept: (Friendship) -> Unit, onReject: (Friendship) -> Unit) {
    if (requests.isEmpty()) {
        Box(Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) {
            Text("Nema zahteva za prijateljstvo.", color = MediumGray, fontSize = 13.sp)
        }
        return
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(requests, key = { it.id }) { req ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = NavyCard)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier.size(40.dp).clip(CircleShape).background(NavyCardLight),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Person, contentDescription = null, tint = LightGray, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(req.senderName, color = White, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconButton(
                            onClick = { onAccept(req) },
                            modifier = Modifier
                                .size(36.dp)
                                .background(SuccessGreen.copy(alpha = 0.15f), CircleShape)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = "Prihvati", tint = SuccessGreen, modifier = Modifier.size(20.dp))
                        }
                        IconButton(
                            onClick = { onReject(req) },
                            modifier = Modifier
                                .size(36.dp)
                                .background(Accent2.copy(alpha = 0.15f), CircleShape)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Odbij", tint = Accent2, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}
