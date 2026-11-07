package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = NeonGreen,
    onPrimary = PureBlack,
    primaryContainer = DarkBorder,
    onPrimaryContainer = MintWhite,
    secondary = BrightMintGreen,
    onSecondary = PureBlack,
    background = PureBlack,
    onBackground = MintWhite,
    surface = DarkSurface,
    onSurface = MintWhite,
    surfaceVariant = DarkCard,
    onSurfaceVariant = MintSubtext,
    outline = DarkBorder
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
