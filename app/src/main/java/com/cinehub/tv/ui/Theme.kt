package com.cinehub.tv.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme
import androidx.compose.material3.MaterialTheme as M3Theme
import androidx.compose.material3.darkColorScheme as m3DarkColorScheme

object Palette {
    val Bg = Color(0xFF0E1116)
    val Surface = Color(0xFF181D26)
    val SurfaceHi = Color(0xFF232A36)
    val Accent = Color(0xFFE5A00D)
    val OnAccent = Color(0xFF14110A)
    val Text = Color(0xFFF2F4F7)
    val TextDim = Color(0xFFA3ABB8)
    val Error = Color(0xFFFF6B6B)
    val Good = Color(0xFF4CC38A)
    val ProfileColors = listOf(
        0xFFE5A00D, 0xFF4C9AFF, 0xFF4CC38A, 0xFFE5534B, 0xFFB57EDC, 0xFF3FC1C9, 0xFFF08C4A,
    )
}

@Composable
fun CineHubTheme(content: @Composable () -> Unit) {
    val tvColors = darkColorScheme(
        primary = Palette.Accent,
        onPrimary = Palette.OnAccent,
        background = Palette.Bg,
        onBackground = Palette.Text,
        surface = Palette.Surface,
        onSurface = Palette.Text,
        surfaceVariant = Palette.SurfaceHi,
        onSurfaceVariant = Palette.TextDim,
        error = Palette.Error,
    )
    val m3Colors = m3DarkColorScheme(
        primary = Palette.Accent,
        onPrimary = Palette.OnAccent,
        background = Palette.Bg,
        onBackground = Palette.Text,
        surface = Palette.Surface,
        onSurface = Palette.Text,
        onSurfaceVariant = Palette.TextDim,
        error = Palette.Error,
    )
    M3Theme(colorScheme = m3Colors) {
        MaterialTheme(colorScheme = tvColors, content = content)
    }
}
