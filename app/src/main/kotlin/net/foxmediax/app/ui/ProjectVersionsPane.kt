
package net.foxmediax.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.foxmediax.app.project.ProjectVersionRestoreService
import net.foxmediax.app.project.ProjectVersionStore
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ProjectVersionsPane(
    projectRoot: File
) {
    val store = remember(projectRoot) {
        ProjectVersionStore()
    }

    val restoreService = remember(store) {
        ProjectVersionRestoreService(store)
    }

    val scope = rememberCoroutineScope()

    var versions by remember(projectRoot) {
        mutableStateOf<List<ProjectVersionStore.VersionInfo>>(emptyList())
    }

    var loading by remember {
        mutableStateOf(true)
    }

    var busy by remember {
        mutableStateOf(false)
    }

    var refreshKey by remember {
        mutableIntStateOf(0)
    }

    var errorMessage by remember {
        mutableStateOf<String?>(null)
    }

    var statusMessage by remember {
        mutableStateOf<String?>(null)
    }

    var showCreateDialog by remember {
        mutableStateOf(false)
    }

    var pendingRestore by remember {
        mutableStateOf<ProjectVersionStore.VersionInfo?>(null)
    }

    var pendingDelete by remember {
        mutableStateOf<ProjectVersionStore.VersionInfo?>(null)
    }

    var versionName by remember {
        mutableStateOf("")
    }

    var versionComment by remember {
        mutableStateOf("")
    }

    // =====================================================
    // LOAD VERSIONS
    // =====================================================

    LaunchedEffect(projectRoot, refreshKey) {
        loading = true
        errorMessage = null

        try {
            versions = withContext(Dispatchers.IO) {
                store.listVersions(projectRoot)
            }
        } catch (error: Exception) {
            errorMessage = error.message
                ?: "Не удалось загрузить версии проекта."
        } finally {
            loading = false
        }
    }

    // =====================================================
    // CREATE VERSION
    // =====================================================

    fun createVersion() {
        val name = versionName.trim()

        if (name.isBlank()) {
            errorMessage = "Укажи название версии."
            return
        }

        scope.launch {
            busy = true
            errorMessage = null
            statusMessage = null

            try {
                val created = withContext(Dispatchers.IO) {
                    store.createVersion(
                        projectRoot = projectRoot,
                        name = name,
                        comment = versionComment.trim()
                    )
                }

                statusMessage =
                    "Версия «${created.name}» успешно создана."

                showCreateDialog = false
                versionName = ""
                versionComment = ""
                refreshKey++

            } catch (error: Exception) {
                errorMessage = error.message
                    ?: "Не удалось создать версию."
            } finally {
                busy = false
            }
        }
    }

    // =====================================================
    // VERIFY VERSION
    // =====================================================

    fun verifyVersion(version: ProjectVersionStore.VersionInfo) {
        scope.launch {
            busy = true
            errorMessage = null
            statusMessage = null

            try {
                val valid = withContext(Dispatchers.IO) {
                    store.verifyVersion(
                        projectRoot,
                        version.id
                    )
                }

                statusMessage = if (valid) {
                    "Версия «${version.name}»: архив цел, проверка пройдена."
                } else {
                    "Версия «${version.name}»: проверка не пройдена. " +
                            "Архив повреждён или недоступен."
                }

            } catch (error: Exception) {
                errorMessage = error.message
                    ?: "Не удалось проверить версию."
            } finally {
                busy = false
            }
        }
    }

    // =====================================================
    // RESTORE VERSION
    // =====================================================

    fun restoreVersion(version: ProjectVersionStore.VersionInfo) {
        scope.launch {
            busy = true
            errorMessage = null
            statusMessage = null

            try {
                val result = withContext(Dispatchers.IO) {
                    restoreService.restoreVersion(
                        projectRoot = projectRoot,
                        versionId = version.id
                    )
                }

                pendingRestore = null

                statusMessage =
                    "Восстановлено файлов: ${result.restoredFiles}. " +
                            "Создана резервная версия: " +
                            "«${result.backupVersion.name}»."

                refreshKey++

            } catch (error: Exception) {
                pendingRestore = null
                errorMessage = error.message
                    ?: "Не удалось восстановить проект."
            } finally {
                busy = false
            }
        }
    }

    // =====================================================
    // DELETE VERSION
    // =====================================================

    fun deleteVersion(version: ProjectVersionStore.VersionInfo) {
        scope.launch {
            busy = true
            errorMessage = null
            statusMessage = null

            try {
                val deleted = withContext(Dispatchers.IO) {
                    store.deleteVersion(
                        projectRoot,
                        version.id
                    )
                }

                pendingDelete = null

                if (deleted) {
                    statusMessage =
                        "Версия «${version.name}» удалена."
                    refreshKey++
                } else {
                    errorMessage =
                        "Версия больше не существует."
                    refreshKey++
                }

            } catch (error: Exception) {
                pendingDelete = null
                errorMessage = error.message
                    ?: "Не удалось удалить версию."
            } finally {
                busy = false
            }
        }
    }

    // =====================================================
    // UI
    // =====================================================

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    "Версии проекта",
                    style = MaterialTheme.typography.headlineSmall
                )

                Text(
                    "Сохранённые состояния и восстановление проекта",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Button(
                enabled = !busy && !loading,
                onClick = {
                    versionName = "Версия ${
                        SimpleDateFormat(
                            "dd.MM.yyyy HH:mm",
                            Locale.getDefault()
                        ).format(Date())
                    }"

                    versionComment = ""
                    errorMessage = null
                    showCreateDialog = true
                }
            ) {
                Text("+ Создать версию")
            }
        }

        HorizontalDivider()

        Text(
            "Перед созданием версии сохрани открытые сценарии. " +
                    "В снимок попадают файлы, записанные на диск. " +
                    "Каталоги .git, .idea, .gradle, build и out не включаются.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall
        )

        errorMessage?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        statusMessage?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        when {
            loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            versions.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "Сохранённых версий пока нет",
                            style = MaterialTheme.typography.titleMedium
                        )

                        Text(
                            "Создай первую версию, чтобы иметь возможность " +
                                    "вернуться к предыдущему состоянию проекта.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(
                        items = versions,
                        key = { it.id }
                    ) { version ->

                        ProjectVersionCard(
                            version = version,
                            enabled = !busy,
                            onVerify = {
                                verifyVersion(version)
                            },
                            onRestore = {
                                pendingRestore = version
                            },
                            onDelete = {
                                pendingDelete = version
                            }
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Всего версий: ${versions.size}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium
            )

            TextButton(
                enabled = !busy && !loading,
                onClick = {
                    refreshKey++
                    statusMessage = null
                    errorMessage = null
                }
            ) {
                Text("Обновить список")
            }
        }
    }

    // =====================================================
    // CREATE DIALOG
    // =====================================================

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!busy) showCreateDialog = false
            },
            title = {
                Text("Создание версии")
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = versionName,
                        onValueChange = {
                            versionName = it
                        },
                        label = {
                            Text("Название версии")
                        },
                        singleLine = true,
                        enabled = !busy
                    )

                    OutlinedTextField(
                        value = versionComment,
                        onValueChange = {
                            versionComment = it
                        },
                        label = {
                            Text("Описание (необязательно)")
                        },
                        minLines = 3,
                        maxLines = 5,
                        enabled = !busy
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = !busy && versionName.isNotBlank(),
                    onClick = {
                        createVersion()
                    }
                ) {
                    Text(
                        if (busy) "Создание…" else "Создать"
                    )
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !busy,
                    onClick = {
                        showCreateDialog = false
                    }
                ) {
                    Text("Отмена")
                }
            }
        )
    }

    // =====================================================
    // RESTORE CONFIRMATION
    // =====================================================

    pendingRestore?.let { version ->
        AlertDialog(
            onDismissRequest = {
                if (!busy) pendingRestore = null
            },
            title = {
                Text("Восстановить версию?")
            },
            text = {
                Text(
                    "Проект будет возвращён к состоянию «${version.name}». " +
                            "Файлы, появившиеся после создания версии, будут удалены. " +
                            "Перед восстановлением автоматически создаётся " +
                            "резервная версия текущего состояния."
                )
            },
            confirmButton = {
                Button(
                    enabled = !busy,
                    onClick = {
                        restoreVersion(version)
                    }
                ) {
                    Text(
                        if (busy) "Восстановление…" else "Восстановить"
                    )
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !busy,
                    onClick = {
                        pendingRestore = null
                    }
                ) {
                    Text("Отмена")
                }
            }
        )
    }

    // =====================================================
    // DELETE CONFIRMATION
    // =====================================================

    pendingDelete?.let { version ->
        AlertDialog(
            onDismissRequest = {
                if (!busy) pendingDelete = null
            },
            title = {
                Text("Удалить версию?")
            },
            text = {
                Text(
                    "Версия «${version.name}» будет удалена без возможности " +
                            "восстановления из этого снимка."
                )
            },
            confirmButton = {
                Button(
                    enabled = !busy,
                    onClick = {
                        deleteVersion(version)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Удалить")
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !busy,
                    onClick = {
                        pendingDelete = null
                    }
                ) {
                    Text("Отмена")
                }
            }
        )
    }
}

// =====================================================
// VERSION CARD
// =====================================================

@Composable
private fun ProjectVersionCard(
    version: ProjectVersionStore.VersionInfo,
    enabled: Boolean,
    onVerify: () -> Unit,
    onRestore: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        version.name,
                        style = MaterialTheme.typography.titleMedium
                    )

                    Text(
                        formatVersionDate(version.createdAtMillis),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall
                    )
                }

                Text(
                    formatArchiveSize(version.archiveSize),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium
                )
            }

            if (version.comment.isNotBlank()) {
                Text(
                    version.comment,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            HorizontalDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    enabled = enabled,
                    onClick = onVerify
                ) {
                    Text("Проверить")
                }

                TextButton(
                    enabled = enabled,
                    onClick = onDelete
                ) {
                    Text(
                        "Удалить",
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Button(
                    enabled = enabled,
                    onClick = onRestore
                ) {
                    Text("Восстановить")
                }
            }
        }
    }
}

private fun formatVersionDate(timestamp: Long): String {
    return SimpleDateFormat(
        "dd.MM.yyyy HH:mm:ss",
        Locale.getDefault()
    ).format(Date(timestamp))
}

private fun formatArchiveSize(bytes: Long): String {
    return when {
        bytes < 1024L ->
            "$bytes Б"

        bytes < 1024L * 1024L ->
            "%.1f КБ".format(Locale.getDefault(), bytes / 1024.0)

        bytes < 1024L * 1024L * 1024L ->
            "%.1f МБ".format(
                Locale.getDefault(),
                bytes / (1024.0 * 1024.0)
            )

        else ->
            "%.2f ГБ".format(
                Locale.getDefault(),
                bytes / (1024.0 * 1024.0 * 1024.0)
            )
    }
}
