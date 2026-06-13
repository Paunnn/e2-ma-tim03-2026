package com.tim03.slagalica.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tim03.slagalica.ui.theme.*

@Composable
fun MozaikBottomNav(
    selectedIndex: Int = 0,
    onHomeClick: () -> Unit = {},
    onProfileClick: () -> Unit = {}
) {
    val items = listOf(
        Pair(Icons.Default.PlayArrow, "Igraj"),
        Pair(Icons.Default.Leaderboard, "Rang"),
        Pair(Icons.Default.Map, "Mapa"),
        Pair(Icons.Default.Group, "Drugari"),
        Pair(Icons.Default.Person, "Profil"),
    )
    NavigationBar(containerColor = Navy, tonalElevation = 0.dp) {
        items.forEachIndexed { i, (icon, label) ->
            val selected = i == selectedIndex
            NavigationBarItem(
                selected = selected,
                onClick = {
                    when (i) {
                        0 -> onHomeClick()
                        4 -> onProfileClick()
                    }
                },
                icon = {
                    Box(
                        modifier = if (selected)
                            Modifier.clip(RoundedCornerShape(8.dp)).background(PrimaryBlueBright.copy(alpha = 0.16f)).padding(horizontal = 10.dp, vertical = 4.dp)
                        else
                            Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(icon, contentDescription = label)
                    }
                },
                label = { Text(label, fontSize = 10.sp) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = PrimaryBlueBright,
                    selectedTextColor = PrimaryBlueBright,
                    unselectedIconColor = MediumGray,
                    unselectedTextColor = MediumGray,
                    indicatorColor = Color.Transparent
                )
            )
        }
    }
}
