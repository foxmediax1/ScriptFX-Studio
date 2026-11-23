package net.foxmediax.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.foxmediax.app.project.ProjectService
import net.foxmediax.app.project.ProjectVersionRestoreService
import net.foxmediax.app.ui.ProjectWorkspace
import net.foxmediax.app.ui.ScriptFXStudioTheme
import net.foxmediax.app.ui.StudioHome
import java.io.File

sealed interface Screen {
    data object Home : Screen

    data class Workspace(
        val openedProject: ProjectService.OpenedProject
    ) : Screen
}

fun main() = application {

    val service = remember {
        ProjectService()
    }

    val restoreService = remember {
        ProjectVersionRestoreService()
    }

    val scope = rememberCoroutineScope()

    var screen by remember {
        mutableStateOf<Screen>(Screen.Home)
    }

    var openingProject by remember {
        mutableStateOf<File?>(null)
    }

    var recoveryNotice by remember {
        mutableStateOf<String?>(null)
    }

    var openingError by remember {
        mutableStateOf<String?>(null)
    }

    var failedProject by remember {
        mutableStateOf<File?>(null)
    }

    fun openProject(root: File) {

        if (openingProject != null) {
            return
        }

        openingProject = root
        openingError = null

        scope.launch {

            try {

                val result = withContext(Dispatchers.IO) {

                    val recovery =
                        restoreService.recoverInterruptedRestore(root)

                    val opened = service.openProject(root)

                    opened to recovery
                }

                screen = Screen.Workspace(
                    result.first
                )

                recoveryNotice = result.second?.message
                failedProject = null

            } catch (error: Exception) {

                screen = Screen.Home
                failedProject = root

                openingError = error.message
                    ?: "Не удалось безопасно открыть проект."

            } finally {
                openingProject = null
            }
        }
    }

    Window(
        onCloseRequest = ::exitApplication,
        title = "ScriptFX Studio"
    ) {

        ScriptFXStudioTheme {

            when (val currentScreen = screen) {

                Screen.Home -> {

                    StudioHome(
                        service = service,
                        onOpenProject = ::openProject
                    )
                }

                is Screen.Workspace -> {

                    val opened = currentScreen.openedProject

                    ProjectWorkspace(
                        project = opened.manifest,
                        projectRoot = opened.root,
                        onBackToHome = {
                            screen = Screen.Home
                        }
                    )
                }
            }

            // =================================================
            // PROJECT OPENING
            // =================================================

            openingProject?.let { project ->

                AlertDialog(
                    onDismissRequest = {},
                    title = {
                        Text("Открытие проекта")
                    },
                    text = {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator()

                            Text(
                                "Проверяем состояние проекта:\n${project.name}"
                            )
                        }
                    },
                    confirmButton = {}
                )
            }

            // =================================================
            // RECOVERY ERROR
            // =================================================

            openingError?.let { message ->

                AlertDialog(
                    onDismissRequest = {
                        openingError = null
                        failedProject = null
                    },
                    title = {
                        Text("Не удалось открыть проект")
                    },
                    text = {
                        Text(message)
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {

                                val root = failedProject

                                openingError = null
                                failedProject = null

                                if (root != null) {
                                    openProject(root)
                                }
                            }
                        ) {
                            Text("Повторить")
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = {
                                openingError = null
                                failedProject = null
                            }
                        ) {
                            Text("Назад")
                        }
                    }
                )
            }

            // =================================================
            // SUCCESSFUL RECOVERY NOTICE
            // =================================================

            recoveryNotice?.let { message ->

                AlertDialog(
                    onDismissRequest = {
                        recoveryNotice = null
                    },
                    title = {
                        Text("Проверка восстановления")
                    },
                    text = {
                        Text(message)
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                recoveryNotice = null
                            }
                        ) {
                            Text("Понятно")
                        }
                    }
                )
            }
        }
    }
}