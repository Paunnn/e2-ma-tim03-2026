package com.tim03.slagalica.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Star

// Shared avatar set - user.avatarIndex indexes into this list. Kept in one place so
// the profile, friends list and any other screen render the same picture for a player.
val avatarIcons = listOf(
    Icons.Default.Person,
    Icons.Default.Face,
    Icons.Default.SportsEsports,
    Icons.Default.Star,
    Icons.Default.EmojiEvents,
    Icons.Default.Psychology
)
