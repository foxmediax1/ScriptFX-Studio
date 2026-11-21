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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import net.foxmediax.app.project.ProjectManifest
import net.foxmediax.app.project.ProjectService
import net.foxmediax.app.ui.theme.StudioColors
import net.foxmediax.app.ui.theme.StudioDarkScheme
import java.io.File

private enum class StudioTab(val title: String) {
    SCENARIOS("Сценарии"),
    NPC("NPC"),
    QUESTS("Квесты"),
    TRIGGERS("Триггеры"),
    CUTSCENES("Катсцены"),
    HUD("HUD"),
    PARTICLES("Частицы"),
    SETTINGS("Настройки")
}

@Composable
fun ProjectWorkspace(
    projectRoot: File,
    onBack: () -> Unit
) {
    val service = remember { ProjectService() }
    var manifest by remember { mutableStateOf<ProjectManifest?>(null) }
    var tab by remember { mutableIntStateOf(0) }
    var status by remember { mutableStateOf("") }
    var statusOk by remember { mutableStateOf(true) }

    // scenarios
    var scenarioFiles by remember { mutableStateOf<List<File>>(emptyList()) }
    var selectedFile by remember { mutableStateOf<File?>(null) }
    var editorText by remember { mutableStateOf("") }
    var originalText by remember { mutableStateOf("") }
    var dirty by remember { mutableStateOf(false) }

    // export path in settings
    var exportPath by remember {
        mutableStateOf(service.defaultMinecraftProjectsRoot().absolutePath)
    }

    fun loadManifest() {
        service.openProject(projectRoot).onSuccess {
            manifest = it.manifest
        }.onFailure {
            statusOk = false
            status = it.message ?: "Не удалось загрузить манифест"
        }
    }

    fun reloadScenarios() {
        val dir = File(projectRoot, "scenarios")
        scenarioFiles = if (dir.isDirectory) {
            dir.listFiles()
                ?.filter { it.isFile && (it.extension.equals("sfxs", true) || it.extension.equals("scn", true) || it.extension.equals("txt", true)) }
                ?.sortedBy { it.name.lowercase() }
                ?: emptyList()
        } else emptyList()
    }

    fun openFile(f: File) {
        if (dirty) {
            statusOk = false
            status = "Есть несохранённые изменения — сначала Сохранить или Отменить"
            return
        }
        runCatching {
            val text = f.readText(Charsets.UTF_8)
            selectedFile = f
            editorText = text
            originalText = text
            dirty = false
            statusOk = true
            status = "Открыт: ${f.name}"
        }.onFailure {
            statusOk = false
            status = it.message ?: "Ошибка чтения"
        }
    }

    fun saveFile() {
        val f = selectedFile ?: return
        runCatching {
            f.writeText(editorText, Charsets.UTF_8)
            originalText = editorText
            dirty = false
            statusOk = true
            status = "Сохранено: ${f.name}"
        }.onFailure {
            statusOk = false
            status = it.message ?: "Ошибка записи"
        }
    }

    fun discardChanges() {
        editorText = originalText
        dirty = false
        statusOk = true
        status = "Изменения отменены"
    }

    fun exportProject() {
        val targetRoot = File(exportPath.trim())
        val r = service.exportToMinecraft(projectRoot, targetRoot)
        r.onSuccess {
            statusOk = true
            status = "Экспорт: ${it.absolutePath}"
        }.onFailure {
            statusOk = false
            status = it.message ?: "Ошибка экспорта"
        }
    }

    fun createScenario(rawName: String) {
        val name = rawName.trim()
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .removeSuffix(".sfxs")
            .removeSuffix(".scn")
        if (name.isEmpty()) {
            statusOk = false
            status = "Введите имя сценария"
            return
        }
        if (dirty) {
            statusOk = false
            status = "Сохраните или отмените изменения перед созданием файла"
            return
        }
        val dir = File(projectRoot, "scenarios").also { it.mkdirs() }
        val file = File(dir, "$name.sfxs")
        if (file.exists()) {
            statusOk = false
            status = "Уже есть: ${file.name}"
            return
        }
        runCatching {
            file.writeText(
                """
                |# $name
                |startscript
                |print "Сценарий $name"
                |stopscript
                """.trimMargin(),
                Charsets.UTF_8
            )
            // обновить манифест (опционально)
            manifest?.let { m ->
                val refs = m.files.scenarios + net.foxmediax.app.project.FileRef(
                    id = name,
                    path = "scenarios/${file.name}",
                    kind = "script"
                )
                val updated = m.copy(files = m.files.copy(scenarios = refs))
                service.writeManifest(projectRoot, updated)
                manifest = updated
            }
            reloadScenarios()
            openFile(file)
            statusOk = true
            status = "Создан: ${file.name}"
        }.onFailure {
            statusOk = false
            status = it.message ?: "Ошибка создания"
        }
    }

    LaunchedEffect(projectRoot) {
        loadManifest()
        reloadScenarios()
        val intro = File(projectRoot, "scenarios/intro.sfxs")
        if (intro.isFile) openFile(intro)
        else scenarioFiles.firstOrNull()?.let { openFile(it) }
    }

    MaterialTheme(colorScheme = StudioDarkScheme) {
        Column(
            Modifier
                .fillMaxSize()
                .background(StudioColors.Bg)
        ) {
            // Top bar
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(StudioColors.Panel)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TextButton(onClick = onBack) { Text("← Проекты") }
                Text(
                    "▶ ${manifest?.displayName ?: projectRoot.name}",
                    color = StudioColors.AccentSoft,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    projectRoot.absolutePath,
                    color = StudioColors.Muted,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f)
                )
                if (dirty) {
                    Text("● изменено", color = StudioColors.Warning)
                }
                Text(
                    status,
                    color = if (statusOk) StudioColors.Success else StudioColors.Danger,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            ScrollableTabRow(
                selectedTabIndex = tab,
                containerColor = StudioColors.Panel,
                contentColor = StudioColors.AccentSoft,
                edgePadding = 8.dp
            ) {
                StudioTab.entries.forEachIndexed { i, t ->
                    Tab(
                        selected = tab == i,
                        onClick = { tab = i },
                        text = { Text(t.title) }
                    )
                }
            }

            when (StudioTab.entries[tab]) {
                StudioTab.SCENARIOS -> ScenariosPane(
                    files = scenarioFiles,
                    selected = selectedFile,
                    editorText = editorText,
                    dirty = dirty,
                    onSelect = { openFile(it) },
                    onTextChange = {
                        editorText = it
                        dirty = it != originalText
                    },
                    onSave = { saveFile() },
                    onDiscard = { discardChanges() },
                    onReloadList = { reloadScenarios() }
                )

                StudioTab.SETTINGS -> SettingsPane(
                    projectRoot = projectRoot,
                    exportPath = exportPath,
                    onExportPathChange = { exportPath = it },
                    onExport = { exportProject() },
                    defaultMcPath = service.defaultMinecraftProjectsRoot().absolutePath
                )

                else -> PlaceholderPane(StudioTab.entries[tab].title)
            }
        }
    }
}

@Composable
private fun ScenariosPane(
    files: List<File>,
    selected: File?,
    editorText: String,
    dirty: Boolean,
    onSelect: (File) -> Unit,
    onTextChange: (String) -> Unit,
    onSave: () -> Unit,
    onDiscard: () -> Unit,
    onReloadList: () -> Unit
) {
    Row(
        Modifier
            .fillMaxSize()
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = StudioColors.Card),
            modifier = Modifier.width(240.dp).fillMaxHeight()
        ) {
            Column(Modifier.padding(12.dp).fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("scenarios/", color = StudioColors.Accent)
                    TextButton(onClick = onReloadList) { Text("↻") }
                }
                HorizontalDivider(color = StudioColors.Muted.copy(alpha = 0.3f))
                Spacer(Modifier.height(8.dp))
                if (files.isEmpty()) {
                    Text("Нет файлов", color = StudioColors.Muted)
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        items(files, key = { it.absolutePath }) { f ->
                            val active = selected?.absolutePath == f.absolutePath
                            TextButton(
                                onClick = { onSelect(f) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    f.name,
                                    color = if (active) StudioColors.AccentSoft else StudioColors.Text,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = StudioColors.Card),
            modifier = Modifier.weight(1f).fillMaxHeight()
        ) {
            Column(Modifier.padding(12.dp).fillMaxSize()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        selected?.name ?: "Файл не выбран",
                        color = StudioColors.Text,
                        modifier = Modifier.weight(1f)
                    )
                    Button(onClick = onSave, enabled = selected != null && dirty) {
                        Text("Сохранить")
                    }
                    OutlinedButton(onClick = onDiscard, enabled = dirty) {
                        Text("Отменить")
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = editorText,
                    onValueChange = onTextChange,
                    modifier = Modifier.fillMaxSize(),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    enabled = selected != null
                )
            }
        }
    }
}

@Composable
private fun SettingsPane(
    projectRoot: File,
    exportPath: String,
    onExportPathChange: (String) -> Unit,
    onExport: () -> Unit,
    defaultMcPath: String
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Настройки проекта", color = StudioColors.Accent, style = MaterialTheme.typography.titleLarge)
        Text("Корень студии: ${projectRoot.absolutePath}", color = StudioColors.Muted)

        Card(colors = CardDefaults.cardColors(containerColor = StudioColors.Card)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Экспорт в Minecraft (мод)", color = StudioColors.AccentSoft)
                Text(
                    "Копирует файлы проекта в папку, которую читает Fabric-мод.",
                    color = StudioColors.Muted,
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = exportPath,
                    onValueChange = onExportPathChange,
                    label = { Text("Папка projects мода") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Text(
                    "По умолчанию: $defaultMcPath",
                    color = StudioColors.Muted,
                    style = MaterialTheme.typography.bodySmall
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onExport) { Text("Экспорт в игру") }
                    OutlinedButton(onClick = { onExportPathChange(defaultMcPath) }) {
                        Text("Сбросить путь")
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaceholderPane(title: String) {
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Text("Раздел «$title»", color = StudioColors.Accent, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text("Контент раздела будет добавлен позже.", color = StudioColors.Muted)
    }
}