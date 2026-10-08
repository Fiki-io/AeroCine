package com.aerocine.camera.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val MatteBlack = Color(0xFF121212)
val DarkSurface = Color(0xFF1A1A1A)
val SlateGray = Color(0xFF2D3139)
val NeutralGray = Color(0xFF8E9297)
val SoftWhite = Color(0xFFF0F0F2)
val RecordCrimson = Color(0xFFD32F2F)

private val DarkColorScheme = darkColorScheme(
    primary = SoftWhite,
    secondary = SlateGray,
    background = MatteBlack,
    surface = DarkSurface,
    onPrimary = MatteBlack,
    onSecondary = SoftWhite,
    onBackground = SoftWhite,
    onSurface = SoftWhite,
    error = RecordCrimson
)

@Composable
fun AeroCineTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
