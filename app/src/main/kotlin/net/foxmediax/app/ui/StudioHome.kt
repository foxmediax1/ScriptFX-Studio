package net.foxmediax.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import net.foxmediax.app.project.ProjectService
import java.io.File

@Composable
fun StudioHome(
    service: ProjectService,
    onOpenProject: (File) -> Unit
) {
    var projects by remember { mutableStateOf(service.listProjects()) }
    var showCreate by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    fun refresh() {
        projects = service.listProjects()
    }

    Column(
        Modifier.fillMaxSize().background(Color(0xFF101016)).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("ScriptFX Studio", style = MaterialTheme.typography.headlineMedium, color = Color(0xFFE8E8F0))
        Text("Редактор проектов ScriptFX", color = Color(0xFF9A9AB0))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { showCreate = true }) {
                Icon(Icons.Default.Add, null)
                Spacer(Modifier.width(6.dp))
                Text("Создать проект")
            }
            OutlinedButton(onClick = { refresh() }) {
                Icon(Icons.Default.Refresh, null)
                Spacer(Modifier.width(6.dp))
                Text("Обновить список")
            }
        }

        errorMsg?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
        }

        HorizontalDivider()

        Text("Проекты", style = MaterialTheme.typography.titleMedium, color = Color(0xFFE8E8F0))

        if (projects.isEmpty()) {
            Text("Пока нет проектов. Создайте первый.",
                color = Color(0xFF9A9AB0), modifier = Modifier.padding(16.dp))
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(projects) { dir ->
                    Card(
                        Modifier.fillMaxWidth().clickable { onOpenProject(dir) },
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E28))
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(dir.name, color = Color(0xFFE8E8F0))
                            Text(dir.absolutePath, color = Color(0xFF9A9AB0),
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }

    if (showCreate) {
        AlertDialog(
            onDismissRequest = { showCreate = false; errorMsg = null },
            title = { Text("Новый проект") },
            text = {
                Column {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("Имя проекта") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    errorMsg?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val name = newName.trim()
                    if (name.isBlank()) { errorMsg = "Введите имя"; return@TextButton }
                    runCatching { service.createProject(name) }
                        .onSuccess { opened ->
                            showCreate = false
                            newName = ""
                            errorMsg = null
                            refresh()
                            onOpenProject(opened.root)
                        }
                        .onFailure { errorMsg = it.message ?: "Не удалось создать проект" }
                }) { Text("Создать") }
            },
            dismissButton = {
                TextButton(onClick = { showCreate = false; errorMsg = null }) { Text("Отмена") }
            }
        )
    }
}