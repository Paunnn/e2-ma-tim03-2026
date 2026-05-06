package com.tim03.slagalica.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val SlagalicaColorScheme = darkColorScheme(
    primary = PrimaryBlueBright,
    onPrimary = White,
    primaryContainer = PrimaryBlue,
    onPrimaryContainer = PrimaryBlueLight,
    secondary = Gold,
    onSecondary = Navy,
    secondaryContainer = GoldDark,
    onSecondaryContainer = Navy,
    tertiary = SuccessGreen,
    onTertiary = White,
    background = Navy,
    onBackground = White,
    surface = NavyLight,
    onSurface = White,
    surfaceVariant = NavyCard,
    onSurfaceVariant = LightGray,
    error = ErrorRed,
    onError = White,
    outline = MediumGray,
    outlineVariant = DarkGray
)

@Composable
fun SlagalicaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SlagalicaColorScheme,
        typography = Typography,
        content = content
    )
}
