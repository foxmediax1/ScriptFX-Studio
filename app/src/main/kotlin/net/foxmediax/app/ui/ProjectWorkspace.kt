package net.foxmediax.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.Json
import net.foxmediax.app.project.ExportService
import net.foxmediax.app.project.ProjectManifest
import net.foxmediax.utils.nodes.GraphNode
import net.foxmediax.utils.nodes.NodeGraph
import net.foxmediax.utils.nodes.ScriptNodeSync
import java.io.File

enum class CanvasMode { NODES, TEXT }

@Composable
fun ProjectWorkspace(project: ProjectManifest, projectRoot: File) {
    var tab by remember { mutableStateOf(WorkspaceTab.SCENARIOS) }

    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = tab.ordinal) {
            WorkspaceTab.entries.forEach { t ->
                Tab(
                    selected = tab == t,
                    onClick = { tab = t },
                    text = { Text(t.label) }
                )
            }
        }
        Box(Modifier.fillMaxSize()) {
            when (tab) {
                WorkspaceTab.SCENARIOS -> ScenariosPane(projectRoot)
                WorkspaceTab.SETTINGS -> SettingsPane(projectRoot)
                else -> PlaceholderPane(tab.label)
            }
        }
    }
}

enum class WorkspaceTab(val label: String) {
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
fun PlaceholderPane(name: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("$name — в разработке", color = Color(0xFF9A9AB0))
    }
}

@Composable
fun ScenariosPane(projectRoot: File) {
    val scenariosDir = File(projectRoot, "scenarios").apply { mkdirs() }
    var fileList by remember { mutableStateOf(scenariosDir.listFiles()?.toList() ?: emptyList()) }
    var currentFile by remember { mutableStateOf<File?>(null) }
    var text by remember { mutableStateOf("") }
    var graph by remember { mutableStateOf(NodeGraph()) }
    var mode by remember { mutableStateOf(CanvasMode.NODES) }
    var dirty by remember { mutableStateOf(false) }

    val canvasController = remember { NodeCanvasController() }

    fun reloadFiles() {
        fileList = scenariosDir.listFiles()?.sortedBy { it.name }?.toList() ?: emptyList()
    }

    fun openFile(f: File) {
        currentFile = f
        val txt = f.readText()
        text = ScriptNodeSync.stripMeta(txt)
        graph = if (ScriptNodeSync.hasMeta(txt)) ScriptNodeSync.textToGraph(txt)
        else NodeGraph.defaultTemplate()
        mode = if (ScriptNodeSync.hasMeta(txt)) CanvasMode.NODES else CanvasMode.TEXT
        dirty = false
    }

    fun persist() {
        val f = currentFile ?: return
        when (mode) {
            CanvasMode.NODES -> {
                val t = ScriptNodeSync.graphToText(graph, includeMeta = true)
                f.writeText(t)
                text = ScriptNodeSync.stripMeta(t)
                File(f.parentFile, "${f.nameWithoutExtension}.graph.json")
                    .writeText(graph.toJson())
            }
            CanvasMode.TEXT -> f.writeText(text)
        }
        dirty = false
    }

    Row(Modifier.fillMaxSize()) {
        Column(Modifier.width(280.dp).fillMaxHeight().background(Color(0xFF181820))) {
            Row(
                Modifier.fillMaxWidth().padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Сценарии", color = Color(0xFFE8E8F0))
                IconButton(onClick = {
                    val name = "script_${System.currentTimeMillis()}.sfxs"
                    val f = File(scenariosDir, name)
                    val g = NodeGraph.defaultTemplate()
                    f.writeText(ScriptNodeSync.graphToText(g, includeMeta = true))
                    File(scenariosDir, "${f.nameWithoutExtension}.graph.json").writeText(g.toJson())
                    reloadFiles()
                    openFile(f)
                }) { Icon(Icons.Default.Add, "Создать") }
            }
            LazyColumn(Modifier.weight(1f)) {
                items(fileList) { f ->
                    ListItem(
                        headlineContent = { Text(f.name, color = Color(0xFFE8E8F0)) },
                        modifier = Modifier.clickable { openFile(f) }
                    )
                }
            }
            if (mode == CanvasMode.NODES) {
                HorizontalDivider()
                NodePalette(
                    onAdd = { entry ->
                        val newNode = GraphNode(
                            id = entry.kind.name.lowercase() + "_" + System.currentTimeMillis(),
                            kind = entry.kind,
                            title = entry.title,
                            params = entry.defaultParams
                        )
                        canvasController.addNode(newNode)
                        dirty = true
                    },
                    modifier = Modifier.weight(1f).fillMaxWidth()
                )
            }
        }

        Box(Modifier.weight(1f).fillMaxHeight()) {
            when (mode) {
                CanvasMode.NODES -> NodeCanvas(
                    graph = graph,
                    onGraphChanged = { newGraph -> graph = newGraph; dirty = true },
                    controller = canvasController
                )
                CanvasMode.TEXT -> OutlinedTextField(
                    value = text,
                    onValueChange = { text = it; dirty = true },
                    modifier = Modifier.fillMaxSize().padding(12.dp)
                )
            }

            FloatingActionButton(
                onClick = {
                    if (mode == CanvasMode.NODES) {
                        persist()
                        mode = CanvasMode.TEXT
                    } else {
                        graph = ScriptNodeSync.textToGraph(text)
                        mode = CanvasMode.NODES
                    }
                },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
            ) {
                Icon(Icons.Default.Edit, "Режим нод/текст")
            }
        }
    }

    DisposableEffect(Unit) { onDispose { if (dirty) persist() } }
}

@Composable
fun SettingsPane(projectRoot: File) {
    val manifestFile = File(projectRoot, "scriptfx.project.json")

    var exportPath by remember {
        mutableStateOf(
            runCatching {
                val text = manifestFile.readText()
                Regex(""""exportPath"\s*:\s*"([^"]*)"""").find(text)?.groupValues?.get(1)
            }.getOrNull() ?: ""
        )
    }
    var lastResult by remember { mutableStateOf<ExportService.Result?>(null) }
    var busy by remember { mutableStateOf(false) }

    fun persistExportPath(path: String) {
        if (!manifestFile.isFile) return
        val updated = manifestFile.readText().replace(
            Regex(""""exportPath"\s*:\s*"[^"]*""""),
            """"exportPath":"${path.replace("\\", "\\\\")}""""
        )
        manifestFile.writeText(updated)
    }

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Настройки проекта", style = MaterialTheme.typography.headlineSmall)

        Text("Путь к папке Minecraft (.minecraft)", style = MaterialTheme.typography.titleMedium)
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = exportPath,
                onValueChange = { exportPath = it; persistExportPath(it) },
                modifier = Modifier.weight(1f),
                placeholder = { Text("C:\\Users\\...\\.minecraft") },
                singleLine = true
            )
            Button(onClick = {
                val picked = FileDialogs.pickMinecraftFolder(
                    exportPath.takeIf { it.isNotBlank() }?.let(::File)
                )
                if (picked != null) {
                    exportPath = picked.absolutePath
                    persistExportPath(exportPath)
                }
            }) { Text("Выбрать…") }
        }

        if (exportPath.isNotBlank()) {
            val dest = File(exportPath, "config/scriptfx/projects/${projectRoot.name}")
            Text("Будет экспортировано в: ${dest.absolutePath}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        HorizontalDivider()

        Button(
            enabled = !busy && exportPath.isNotBlank(),
            onClick = {
                busy = true
                lastResult = ExportService.export(projectRoot, File(exportPath))
                busy = false
            }
        ) { Text(if (busy) "Экспорт…" else "Экспортировать в игру") }

        lastResult?.let { r ->
            val color = if (r.success) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.error
            Text(r.message, color = color, style = MaterialTheme.typography.bodyMedium)
        }

        Spacer(Modifier.weight(1f))

        Text(
            "Экспорт копирует содержимое проекта в config/scriptfx/projects/<name>. " +
                    "Файлы *.graph.json нужны только Studio — мод их игнорирует.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}