package com.lightpod.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightOSColorScheme = darkColorScheme(
    background       = LightOSBlack,
    surface          = LightOSBlack,
    surfaceVariant   = Color(0xFF0F0F0F),
    onBackground     = LightOSWhite,
    onSurface        = LightOSWhite,
    onSurfaceVariant = LightOSDim,
    primary          = LightOSWhite,
    onPrimary        = LightOSBlack,
    secondary        = LightOSDim,
    onSecondary      = LightOSBlack,
    outline          = LightOSDivider,
    outlineVariant   = LightOSDivider
)

@Composable
fun LightMusicTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightOSColorScheme,
        typography  = LightOSTypography,
        content     = content
    )
}
