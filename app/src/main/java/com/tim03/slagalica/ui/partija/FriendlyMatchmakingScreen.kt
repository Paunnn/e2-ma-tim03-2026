package com.tim03.slagalica.ui.partija

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tim03.slagalica.ui.theme.*
import com.tim03.slagalica.viewmodel.FriendlyMatchViewModel
import com.tim03.slagalica.viewmodel.FriendlyPhase

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendlyMatchmakingScreen(
    // When set (entered via the friends list), the invite goes straight to this friend.
    friendUid: String = "",
    friendName: String = "",
    onMatchReady: (sessionId: String, isPlayer1: Boolean) -> Unit,
    onBack: () -> Unit,
    vm: FriendlyMatchViewModel = viewModel()
) {
    val state by vm.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.phase) {
        if (state.phase == FriendlyPhase.ACCEPTED) {
            onMatchReady(state.sessionId, state.isPlayer1)
        }
        // Preselected friend: fill the card (and auto-send the first invite) whenever
        // the screen is back in the search phase.
        if (friendUid.isNotEmpty() && state.phase == FriendlyPhase.SEARCH && state.foundUid.isEmpty()) {
            vm.preselectFriend(friendUid, friendName)
        }
    }

    Scaffold(
        containerColor = Navy,
        topBar = {
            TopAppBar(
                title = { Text("Prijateljska partija", color = White, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { vm.cancel(); onBack() }) {
                        Icon(Icons.Default.ArrowBack, null, tint = White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Navy)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(24.dp))

            when (state.phase) {
                FriendlyPhase.SEARCH, FriendlyPhase.ERROR -> {
                    Text(
                        "Pronađi prijatelja",
                        color = White, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp
                    )
                    Text(
                        "Unesi korisničko ime i pošalji poziv",
                        color = LightGray, fontSize = 13.sp,
                        modifier = Modifier.padding(top = 6.dp, bottom = 24.dp)
                    )

                    OutlinedTextField(
                        value = state.searchQuery,
                        onValueChange = vm::onSearchQueryChange,
                        placeholder = { Text("Korisničko ime", color = MediumGray) },
                        leadingIcon = { Icon(Icons.Default.Person, null, tint = LightGray) },
                        trailingIcon = {
                            IconButton(onClick = vm::searchUser) {
                                Icon(Icons.Default.Search, null, tint = PrimaryBlueBright)
                            }
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { vm.searchUser() }),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = White, unfocusedTextColor = White,
                            focusedBorderColor = PrimaryBlueBright, unfocusedBorderColor = LineColor,
                            cursorColor = PrimaryBlueBright
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    AnimatedVisibility(state.errorMessage.isNotEmpty()) {
                        Text(
                            state.errorMessage,
                            color = ErrorRed, fontSize = 12.sp,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }

                    AnimatedVisibility(state.foundUid.isNotEmpty()) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Spacer(Modifier.height(20.dp))
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = NavyCard)
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(RoundedCornerShape(22.dp))
                                            .background(PrimaryBlueBright),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            state.foundName.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                                            color = White, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp
                                        )
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(state.foundName, color = White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                        Text("Pronađen korisnik", color = SuccessGreen, fontSize = 11.sp)
                                    }
                                }
                            }
                            Spacer(Modifier.height(16.dp))
                            Button(
                                onClick = vm::sendInvite,
                                modifier = Modifier.fillMaxWidth().height(50.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlueBright)
                            ) {
                                Text("Pošalji poziv", color = White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            }
                        }
                    }
                }

                FriendlyPhase.INVITE_SENT -> {
                    Spacer(Modifier.height(32.dp))
                    CircularProgressIndicator(color = PrimaryBlueBright, strokeWidth = 3.dp)
                    Spacer(Modifier.height(24.dp))
                    Text(
                        "Poziv poslat",
                        color = White, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp
                    )
                    Text(
                        "Čeka se odgovor od ${state.foundName}…",
                        color = LightGray, fontSize = 14.sp,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    Spacer(Modifier.height(20.dp))
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(32.dp))
                            .background(NavyCard),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            state.secondsLeft.toString(),
                            color = if (state.secondsLeft <= 3) ErrorRed else Gold,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 26.sp
                        )
                    }
                    Text(
                        "Automatsko odbijanje za ${state.secondsLeft}s",
                        color = MediumGray, fontSize = 11.sp,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    Spacer(Modifier.height(32.dp))
                    OutlinedButton(
                        onClick = { vm.cancel(); onBack() },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed),
                        border = androidx.compose.foundation.BorderStroke(1.dp, ErrorRed)
                    ) {
                        Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Otkaži", fontWeight = FontWeight.Bold)
                    }
                }

                FriendlyPhase.REJECTED -> {
                    Spacer(Modifier.height(48.dp))
                    Text("❌", fontSize = 48.sp)
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Poziv odbijen",
                        color = White, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp
                    )
                    Text(
                        "Prijatelj nije prihvatio poziv.",
                        color = LightGray, fontSize = 13.sp,
                        modifier = Modifier.padding(top = 6.dp, bottom = 32.dp),
                        textAlign = TextAlign.Center
                    )
                    Button(
                        onClick = { vm.reset() },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlueBright)
                    ) {
                        Text("Pokušaj ponovo", color = White, fontWeight = FontWeight.Bold)
                    }
                }

                FriendlyPhase.ACCEPTED -> {
                    CircularProgressIndicator(color = SuccessGreen)
                    Spacer(Modifier.height(16.dp))
                    Text("Partija počinje…", color = White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
