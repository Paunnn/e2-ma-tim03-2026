package com.tim03.slagalica.ui.games

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tim03.slagalica.ui.theme.*

@Composable
internal fun GameOverContent(
    myScore: Int,
    opponentScore: Int,
    onFinish: () -> Unit
) {
    val playerWon = myScore > opponentScore
    val tied = myScore == opponentScore

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Navy)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = when {
                playerWon -> Icons.Default.EmojiEvents
                tied -> Icons.Default.Balance
                else -> Icons.Default.SentimentDissatisfied
            },
            contentDescription = null,
            tint = when {
                playerWon -> Gold
                tied -> LightGray
                else -> ErrorRed
            },
            modifier = Modifier.size(80.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = when {
                playerWon -> "Pobeda!"
                tied -> "Nerešeno!"
                else -> "Poraz!"
            },
            color = when {
                playerWon -> Gold
                tied -> LightGray
                else -> ErrorRed
            },
            fontWeight = FontWeight.ExtraBold,
            fontSize = 34.sp
        )

        Spacer(modifier = Modifier.height(8.dp))
        Text("Igra je završena", color = MediumGray, style = MaterialTheme.typography.bodyMedium)

        Spacer(modifier = Modifier.height(32.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = NavyCard)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Ti", color = PrimaryBlueLight, style = MaterialTheme.typography.labelMedium)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "$myScore",
                        color = if (playerWon) Gold else White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 44.sp
                    )
                    Text("bodova", color = LightGray, style = MaterialTheme.typography.labelSmall)
                }
                Text(
                    ":",
                    color = MediumGray,
                    fontWeight = FontWeight.Bold,
                    fontSize = 28.sp
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Protivnik", color = LightGray, style = MaterialTheme.typography.labelMedium)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "$opponentScore",
                        color = if (!playerWon && !tied) Gold else White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 44.sp
                    )
                    Text("bodova", color = LightGray, style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        Spacer(modifier = Modifier.height(40.dp))

        Button(
            onClick = onFinish,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlueBright)
        ) {
            Icon(Icons.Default.Home, null, tint = White, modifier = Modifier.size(22.dp))
            Spacer(modifier = Modifier.width(10.dp))
            Text("ZAVRŠI IGRU", fontWeight = FontWeight.ExtraBold, color = White, fontSize = 16.sp)
        }
    }
}
