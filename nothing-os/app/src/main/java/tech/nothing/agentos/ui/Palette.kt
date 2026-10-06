package tech.nothing.agentos.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp

object Palette {
    val Background = Color(0xFF0E0E0E)
    val Foreground = Color(0xFFF2F2F2)
    val Dim = Color(0xFF8A8A8A)
    val Rule = Color(0xFF222222)
    val Red = Color(0xFFD71921)
}

object Type {
    val Body = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, letterSpacing = 0.5.sp, color = Palette.Dim)
    val Big = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 72.sp, letterSpacing = (-1).sp, color = Palette.Foreground)
}
