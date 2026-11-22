package net.foxmediax.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object StudioColors {
    val Background = Color(0xFF101014)
    val Surface = Color(0xFF19181F)
    val SurfaceAlt = Color(0xFF202029)
    val Panel = Color(0xFF18171D)
    val PanelRaised = Color(0xFF23222B)

    val Border = Color(0xFF32313C)
    val BorderSoft = Color(0xFF292832)

    val Accent = Color(0xFF9875F9)
    val AccentHover = Color(0xFFAC91FF)
    val AccentSoft = Color(0xFF302647)

    val Text = Color(0xFFE9E7F0)
    val TextMuted = Color(0xFF9996A8)
    val TextDisabled = Color(0xFF656372)

    val Success = Color(0xFF72D49A)
    val Warning = Color(0xFFE8BA70)
    val Error = Color(0xFFEF777F)

    val NodeGreen = Color(0xFF254331)
    val NodeRed = Color(0xFF46272B)
    val NodeBlue = Color(0xFF253A50)
    val NodeYellow = Color(0xFF41371F)
}

private val ScriptFXDarkScheme = darkColorScheme(
    primary = StudioColors.Accent,
    onPrimary = Color.White,

    primaryContainer = StudioColors.AccentSoft,
    onPrimaryContainer = Color(0xFFE0D4FF),

    secondary = Color(0xFFAAA0C9),
    onSecondary = Color(0xFF18151F),

    background = StudioColors.Background,
    onBackground = StudioColors.Text,

    surface = StudioColors.Surface,
    onSurface = StudioColors.Text,

    surfaceVariant = StudioColors.SurfaceAlt,
    onSurfaceVariant = StudioColors.TextMuted,

    outline = StudioColors.Border,
    outlineVariant = StudioColors.BorderSoft,

    error = StudioColors.Error,
    onError = Color.White
)

@Composable
fun ScriptFXStudioTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = ScriptFXDarkScheme,
        content = content
    )
}