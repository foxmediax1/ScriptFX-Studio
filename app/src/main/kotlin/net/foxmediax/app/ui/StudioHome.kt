package net.foxmediax.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.foxmediax.app.project.ProjectService
import net.foxmediax.app.ui.theme.StudioColors
import net.foxmediax.app.ui.theme.StudioDarkScheme
import java.io.File

@Composable
fun StudioHome(
    onOpenProject: ((File) -> Unit)? = null
) {
    val service = remember { ProjectService() }
    var projectName by remember { mutableStateOf("example_project") }
    var status by remember { mutableStateOf("Создайте или откройте проект") }
    var statusOk by remember { mutableStateOf(true) }
    var openedPath by remember { mutableStateOf<String?>(null) }
    var projects by remember { mutableStateOf(service.listProjects()) }

    fun refreshList() {
        projects = service.listProjects()
    }

    fun setStatus(ok: Boolean, message: String) {
        statusOk = ok
        status = message
    }

    MaterialTheme(colorScheme = StudioDarkScheme) {
        Row(
            Modifier
                .fillMaxSize()
                .background(StudioColors.Bg)
                .padding(24.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // ——— Левая колонка: создание / открытие ———
            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    "▶ ScriptFX Studio",
                    style = MaterialTheme.typography.headlineMedium,
                    color = StudioColors.AccentSoft
                )
                Text(
                    "Story Engine · проекты студии",
                    color = StudioColors.Muted
                )

                Card(
                    colors = CardDefaults.cardColors(containerColor = StudioColors.Card),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("Новый проект", color = StudioColors.Accent)

                        OutlinedTextField(
                            value = projectName,
                            onValueChange = { projectName = it },
                            label = { Text("Имя папки") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Text(
                            "Студия: ${service.defaultProjectsRoot().absolutePath}",
                            color = StudioColors.Muted,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            "В игру — через экспорт (настройки проекта)",
                            color = StudioColors.Muted,
                            style = MaterialTheme.typography.bodySmall
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    val r = service.createProject(
                                        parentDir = service.defaultProjectsRoot(),
                                        rawName = projectName
                                    )
                                    r.onSuccess {
                                        openedPath = it.root.absolutePath
                                        setStatus(true, "Создан: ${it.manifest.name}")
                                        refreshList()
                                        onOpenProject?.invoke(it.root)
                                    }.onFailure {
                                        setStatus(false, it.message ?: "Ошибка создания")
                                    }
                                }
                            ) {
                                Text("Создать")
                            }

                            OutlinedButton(
                                onClick = {
                                    val dir = File(service.defaultProjectsRoot(), projectName.trim())
                                    val r = service.openProject(dir)
                                    r.onSuccess {
                                        openedPath = it.root.absolutePath
                                        setStatus(true, "Открыт: ${it.manifest.name}")
                                        onOpenProject?.invoke(it.root)
                                    }.onFailure {
                                        setStatus(false, it.message ?: "Ошибка открытия")
                                    }
                                }
                            ) {
                                Text("Открыть по имени")
                            }
                        }
                    }
                }

                Text(
                    status,
                    color = if (statusOk) StudioColors.Success else StudioColors.Danger
                )

                openedPath?.let { path ->
                    Text("Корень: $path", color = StudioColors.Muted)
                    Text(
                        "Экспорт в мод: ${service.defaultMinecraftProjectsRoot().absolutePath}",
                        color = StudioColors.Muted,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            // ——— Правая колонка: список проектов ———
            Card(
                colors = CardDefaults.cardColors(containerColor = StudioColors.Card),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                Column(
                    Modifier
                        .padding(16.dp)
                        .fillMaxSize()
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Проекты", color = StudioColors.Accent)
                        TextButton(onClick = { refreshList() }) {
                            Text("Обновить")
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    if (projects.isEmpty()) {
                        Text(
                            "Пока нет проектов\n%APPDATA%\\ScriptFX Studio\\Projects\\",
                            color = StudioColors.Muted
                        )
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(projects, key = { it.absolutePath }) { dir ->
                                TextButton(
                                    onClick = {
                                        projectName = dir.name
                                        service.openProject(dir)
                                            .onSuccess {
                                                openedPath = it.root.absolutePath
                                                setStatus(true, "Открыт: ${it.manifest.name}")
                                                onOpenProject?.invoke(it.root)
                                            }
                                            .onFailure {
                                                setStatus(false, it.message ?: "Ошибка")
                                            }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        "📁  ${dir.name}",
                                        color = StudioColors.Text,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}