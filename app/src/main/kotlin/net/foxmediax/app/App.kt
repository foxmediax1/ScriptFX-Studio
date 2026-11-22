package net.foxmediax.app

import androidx.compose.runtime.*
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import net.foxmediax.app.project.ProjectService
import net.foxmediax.app.ui.ProjectWorkspace
import net.foxmediax.app.ui.ScriptFXStudioTheme
import net.foxmediax.app.ui.StudioHome
import java.io.File

sealed interface Screen {
    data object Home : Screen
    data class Workspace(val root: File) : Screen
}

fun main() = application {
    val service = remember { ProjectService() }
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }

    Window(
        onCloseRequest = ::exitApplication,
        title = "ScriptFX Studio",
    ) {
        ScriptFXStudioTheme {
            when (val s = screen) {
                is Screen.Home -> StudioHome(
                    service = service,
                    onOpenProject = { root ->
                        screen = Screen.Workspace(root)
                    }
                )

                is Screen.Workspace -> {
                    val opened = remember(s.root) {
                        service.openProject(s.root)
                    }

                    ProjectWorkspace(
                        project = opened.manifest,
                        projectRoot = opened.root
                    )
                }
            }
        }
    }
}