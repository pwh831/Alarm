package com.fitwake.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Accent = Color(0xFFFF8A3D)

private val colors = darkColorScheme(
    primary = Accent,
    onPrimary = Color.Black,
    background = Color(0xFF111316),
    surface = Color(0xFF1A1D21),
)

/** 어두운 방에서 눈부심을 줄이기 위해 다크 테마만 쓴다 (PRD SE-03). */
@Composable
fun FitWakeTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, content = content)
}
