package com.tim03.slagalica.ui.games

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tim03.slagalica.ui.theme.*

@Composable
fun GameHud(
    gameName: String,
    gameColor: Color,
    gameIcon: String,
    round: String,
    timeLeft: Int,
    totalTime: Int,
    myScore: Int,
    oppScore: Int,
    myName: String = "Igrač",
    oppName: String = "Protivnik",
    // False in izazov mode - the challenge partija is played completely solo.
    showOpponent: Boolean = true,
    onExit: () -> Unit = {}
) {
    val myInitials = myName.split("_", " ").filter { it.isNotBlank() }
        .take(2).mapNotNull { it.firstOrNull()?.uppercaseChar() }.joinToString("").ifEmpty { "?" }
    val oppInitials = oppName.split("_", " ").filter { it.isNotBlank() }
        .take(2).mapNotNull { it.firstOrNull()?.uppercaseChar() }.joinToString("").ifEmpty { "?" }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(NavyLight)
            .statusBarsPadding()
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
            // Row 1: game icon + name/round label + timer ring
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(onClick = onExit, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Izađi", tint = MediumGray)
                }
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(gameColor),
                    contentAlignment = Alignment.Center
                ) {
                    Text(gameIcon, color = Navy, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "IGRA · $round",
                        color = MediumGray, fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold, letterSpacing = 1.8.sp
                    )
                    Text(
                        gameName, color = White,
                        fontWeight = FontWeight.ExtraBold, fontSize = 16.sp
                    )
                }
                MozaikTimerRing(current = timeLeft, total = totalTime, size = 48.dp, color = gameColor)
            }

            Spacer(Modifier.height(10.dp))

            // Row 2: me (left) vs opponent (right)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Me avatar left, text right
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(listOf(PrimaryBlueBright, Color(0xFF1A2150)))
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(myInitials, color = White, fontWeight = FontWeight.ExtraBold, fontSize = 8.sp)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "$myName (ti)", color = MediumGray, fontSize = 11.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "$myScore", color = PrimaryBlueBright,
                            fontWeight = FontWeight.ExtraBold, fontSize = 16.sp
                        )
                    }
                }

                if (showOpponent) {
                    Text("vs", color = MediumGray, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)

                    // Opponent — text left (right-aligned), avatar right
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.End
                        ) {
                            Text(
                                oppName, color = MediumGray, fontSize = 11.sp,
                                maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                "$oppScore", color = Accent2,
                                fontWeight = FontWeight.ExtraBold, fontSize = 16.sp
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(listOf(Accent2, Color(0xFF1A2150)))
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(oppInitials, color = White, fontWeight = FontWeight.ExtraBold, fontSize = 8.sp)
                        }
                    }
                }
            }
        }
        HorizontalDivider(thickness = 1.dp, color = LineSoft)
    }
}

@Composable
fun MozaikTimerRing(
    current: Int,
    total: Int,
    size: Dp = 48.dp,
    color: Color
) {
    Box(modifier = Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeW = 4.dp.toPx()
            val inset = strokeW / 2f
            val arcW = this.size.width - strokeW
            val arcH = this.size.height - strokeW
            drawArc(
                color = Color.White.copy(alpha = 0.08f),
                startAngle = -90f, sweepAngle = 360f, useCenter = false,
                style = Stroke(strokeW, cap = StrokeCap.Round),
                topLeft = Offset(inset, inset),
                size = Size(arcW, arcH)
            )
            val fraction = if (total > 0) current.toFloat() / total.toFloat() else 0f
            if (fraction > 0f) {
                drawArc(
                    color = color,
                    startAngle = -90f, sweepAngle = 360f * fraction, useCenter = false,
                    style = Stroke(strokeW, cap = StrokeCap.Round),
                    topLeft = Offset(inset, inset),
                    size = Size(arcW, arcH)
                )
            }
        }
        Text(
            text = current.toString(),
            color = color,
            fontWeight = FontWeight.ExtraBold,
            fontSize = (size.value * 0.35f).sp
        )
    }
}

// Shared shape renderer for Skočko — used in SkockoScreen
@Composable
fun SkockoShape(kind: String, size: Dp = 20.dp, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(size)) {
        val s = this.size.width / 20f
        when (kind) {
            "kvadrat" -> drawRoundRect(
                color = color,
                topLeft = Offset(3 * s, 3 * s),
                size = Size(14 * s, 14 * s),
                cornerRadius = CornerRadius(2 * s)
            )
            "krug" -> drawCircle(color, radius = 7 * s)
            "trougao" -> drawPath(
                color = color,
                path = Path().apply {
                    moveTo(10 * s, 3 * s)
                    lineTo(17.5f * s, 16 * s)
                    lineTo(2.5f * s, 16 * s)
                    close()
                }
            )
            "zvezda" -> drawPath(
                color = color,
                path = Path().apply {
                    listOf(
                        10f to 1.5f, 12.4f to 7f, 18.5f to 7.5f, 13.9f to 11.5f,
                        15.3f to 17.5f, 10f to 14.4f, 4.7f to 17.5f, 6.1f to 11.5f,
                        1.5f to 7.5f, 7.6f to 7f
                    ).forEachIndexed { i, (x, y) ->
                        if (i == 0) moveTo(x * s, y * s) else lineTo(x * s, y * s)
                    }
                    close()
                }
            )
            "srce" -> drawPath(
                color = color,
                path = Path().apply {
                    moveTo(10 * s, 17 * s)
                    cubicTo(4 * s, 14 * s, 0f, 9 * s, 3.5f * s, 5.5f * s)
                    cubicTo(5.5f * s, 3.5f * s, 8.5f * s, 4 * s, 10 * s, 5.7f * s)
                    cubicTo(11.5f * s, 4 * s, 14.5f * s, 3.5f * s, 16.5f * s, 5.5f * s)
                    cubicTo(20 * s, 9 * s, 16 * s, 14 * s, 10 * s, 17 * s)
                    close()
                }
            )
            "skocko" -> {
                drawPath(
                    color = color,
                    alpha = 0.95f,
                    path = Path().apply {
                        listOf(
                            10f to 2f, 12.5f to 6f, 17f to 7.5f, 13.7f to 11.2f,
                            14.5f to 16f, 10f to 13.6f, 5.5f to 16f, 6.3f to 11.2f,
                            3f to 7.5f, 7.5f to 6f
                        ).forEachIndexed { i, (x, y) ->
                            if (i == 0) moveTo(x * s, y * s) else lineTo(x * s, y * s)
                        }
                        close()
                    }
                )
                drawCircle(Color(0xFF0A0E1A), radius = 2.2f * s)
            }
        }
    }
}
