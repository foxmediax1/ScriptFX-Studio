package net.foxmediax.app

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import net.foxmediax.app.ui.StudioHome

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "ScriptFX Studio",
        state = rememberWindowState(width = 1100.dp, height = 720.dp)
    ) {
        StudioHome()
    }
}