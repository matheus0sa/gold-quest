package com.goldquest.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = Gold,
    onPrimary = Ink,
    primaryContainer = GoldSoft,
    onPrimaryContainer = Ink,
    secondary = Ok,
    onSecondary = Color.White,
    secondaryContainer = GoldSoft,
    onSecondaryContainer = Ink,
    tertiary = GoldDeep,
    onTertiary = Color.White,
    background = Paper,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    surfaceVariant = Color(0xFFF3F6F4),
    onSurfaceVariant = Muted,
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color.White,
    outline = Line,
    outlineVariant = Line,
    error = Danger,
    onError = Color.White,
)

private val DarkColorScheme = darkColorScheme(
    primary = NightGold,
    onPrimary = NightBg,
    primaryContainer = NightGoldSoft,
    onPrimaryContainer = NightInk,
    secondary = NightOk,
    onSecondary = NightBg,
    secondaryContainer = NightGoldSoft,
    onSecondaryContainer = NightInk,
    tertiary = NightGold,
    onTertiary = NightBg,
    background = NightBg,
    onBackground = NightInk,
    surface = NightSurface,
    onSurface = NightInk,
    surfaceVariant = NightLine,
    onSurfaceVariant = NightMuted,
    surfaceContainer = NightSurface,
    surfaceContainerHigh = NightSurface,
    outline = NightLine,
    outlineVariant = NightLine,
    error = NightDanger,
    onError = NightBg,
)

@Composable
fun GoldQuestTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography,
        content = content,
    )
}
