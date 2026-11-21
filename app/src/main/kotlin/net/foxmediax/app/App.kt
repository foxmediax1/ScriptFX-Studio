package net.foxmediax.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import net.foxmediax.app.ui.ProjectWorkspace
import net.foxmediax.app.ui.StudioHome
import java.io.File

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "ScriptFX Studio",
        state = rememberWindowState(width = 1280.dp, height = 800.dp)
    ) {
        var openRoot by remember { mutableStateOf<File?>(null) }

        if (openRoot == null) {
            StudioHome(onOpenProject = { openRoot = it })
        } else {
            ProjectWorkspace(
                projectRoot = openRoot!!,
                onBack = { openRoot = null }
            )
        }
    }
}