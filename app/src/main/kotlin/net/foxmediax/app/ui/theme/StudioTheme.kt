package net.foxmediax.app.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color

object StudioColors {
    val Bg = Color(0xFF0B0812)
    val Panel = Color(0xFF14101C)
    val Card = Color(0xFF1A1426)
    val Accent = Color(0xFFB026FF)
    val AccentSoft = Color(0xFFD4A5FF)
    val Text = Color(0xFFF2ECFA)
    val Muted = Color(0xFFA89BB8)
    val Success = Color(0xFF55FF88)
    val Danger = Color(0xFFFF5555)
    val Warning = Color(0xFFFFAA55)
}

val StudioDarkScheme = darkColorScheme(
    primary = StudioColors.Accent,
    onPrimary = StudioColors.Text,
    background = StudioColors.Bg,
    surface = StudioColors.Panel,
    onBackground = StudioColors.Text,
    onSurface = StudioColors.Text,
    secondary = StudioColors.AccentSoft
)