package com.mnm.auseekers.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF006C4C),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF8CF8C8),
    onPrimaryContainer = Color(0xFF002116),
    secondary = Color(0xFF4D6358),
    background = Color(0xFFF4F7F5),
    surface = Color(0xFFF4F7F5),
    error = Color(0xFFBA1A1A),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF70DBAD),
    onPrimary = Color(0xFF003826),
    primaryContainer = Color(0xFF005138),
    onPrimaryContainer = Color(0xFF8CF8C8),
    secondary = Color(0xFFB4CCBF),
    background = Color(0xFF101512),
    surface = Color(0xFF101512),
)

@Composable
fun MnmAuSeekersTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
