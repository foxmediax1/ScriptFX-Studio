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
import net.foxmediax.app.project.ExportService
import net.foxmediax.app.project.ProjectManifest
import net.foxmediax.utils.nodes.GraphNode
import net.foxmediax.utils.nodes.NodeGraph
import net.foxmediax.utils.nodes.ScriptNodeSync
import java.io.File
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.BorderStroke

enum class CanvasMode { NODES, TEXT }

@Composable
fun ProjectWorkspace(
    project: ProjectManifest,
    projectRoot: File
) {
    var tab by remember { mutableStateOf(WorkspaceTab.SCENARIOS) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(StudioColors.Background)
    ) {
        // TOP BAR
        StudioTopBar(
            projectName = projectRoot.name,
            projectPath = projectRoot.absolutePath
        )

        // MODULE NAVIGATION
        StudioModuleBar(
            selected = tab,
            onSelected = { tab = it }
        )

        // EDITOR
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(StudioColors.Background)
        ) {
            when (tab) {
                WorkspaceTab.SCENARIOS ->
                    ScenariosPane(projectRoot)

                WorkspaceTab.SETTINGS ->
                    SettingsPane(projectRoot)

                else ->
                    PlaceholderPane(tab.label)
            }
        }

        // BOTTOM STATUS BAR
        StudioStatusBar(
            projectName = projectRoot.name,
            activeModule = tab.label
        )
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
private fun StudioTopBar(
    projectName: String,
    projectPath: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(62.dp)
            .background(StudioColors.Surface)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // BRAND
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .background(
                        StudioColors.AccentSoft,
                        RoundedCornerShape(9.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "S",
                    color = StudioColors.AccentHover,
                    style = MaterialTheme.typography.titleMedium
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    "ScriptFX",
                    color = StudioColors.Text,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    "STUDIO",
                    color = StudioColors.Accent,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }

        Spacer(Modifier.width(24.dp))

        Box(
            modifier = Modifier
                .width(1.dp)
                .height(28.dp)
                .background(StudioColors.Border)
        )

        Spacer(Modifier.width(20.dp))

        // PROJECT
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                projectName,
                color = StudioColors.Text,
                style = MaterialTheme.typography.labelLarge
            )

            Text(
                projectPath,
                color = StudioColors.TextMuted,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1
            )
        }

        Spacer(Modifier.weight(1f))

        // PROJECT STATE
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Box(
                Modifier
                    .size(7.dp)
                    .background(StudioColors.Success, RoundedCornerShape(50))
            )

            Text(
                "Project Loaded",
                color = StudioColors.TextMuted,
                style = MaterialTheme.typography.labelMedium
            )
        }
    }

    HorizontalDivider(color = StudioColors.BorderSoft)
}

@Composable
private fun StudioModuleBar(
    selected: WorkspaceTab,
    onSelected: (WorkspaceTab) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(StudioColors.Panel)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        WorkspaceTab.entries.forEach { item ->
            val active = selected == item

            Box(
                modifier = Modifier
                    .padding(horizontal = 3.dp, vertical = 5.dp)
                    .background(
                        if (active) StudioColors.AccentSoft
                        else Color.Transparent,
                        RoundedCornerShape(6.dp)
                    )
                    .clickable { onSelected(item) }
                    .padding(horizontal = 14.dp, vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = item.label,
                    color = if (active)
                        StudioColors.AccentHover
                    else
                        StudioColors.TextMuted,
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }

    HorizontalDivider(color = StudioColors.Border)
}

@Composable
private fun StudioStatusBar(
    projectName: String,
    activeModule: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(27.dp)
            .background(StudioColors.Panel)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "SCRIPTFX",
            color = StudioColors.Accent,
            style = MaterialTheme.typography.labelSmall
        )

        Spacer(Modifier.width(12.dp))

        Text(
            projectName,
            color = StudioColors.TextMuted,
            style = MaterialTheme.typography.labelSmall
        )

        Spacer(Modifier.weight(1f))

        Text(
            activeModule,
            color = StudioColors.TextMuted,
            style = MaterialTheme.typography.labelSmall
        )

        Spacer(Modifier.width(14.dp))

        Text(
            "UTF-8",
            color = StudioColors.TextMuted,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
fun PlaceholderPane(name: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("$name — в разработке", color = Color(0xFF9A9AB0))
    }
}

@Composable
fun ScenariosPane(projectRoot: File) {
    val scenariosDir = remember(projectRoot) {
        File(projectRoot, "scenarios").apply {
            mkdirs()
        }
    }

    fun getScenarioFiles(): List<File> {
        return scenariosDir.listFiles()
            ?.filter {
                it.isFile &&
                        it.extension.equals("sfxs", ignoreCase = true)
            }
            ?.sortedBy { it.name }
            ?: emptyList()
    }

    // Если сценариев ещё нет, создаём начальный.
    val initialFile = remember(scenariosDir) {
        val existing = getScenarioFiles().firstOrNull()

        existing ?: run {
            val file = File(scenariosDir, "main.sfxs")
            val initialGraph = NodeGraph.defaultTemplate()

            file.writeText(
                ScriptNodeSync.graphToText(
                    initialGraph,
                    includeMeta = true
                )
            )

            File(
                scenariosDir,
                "main.graph.json"
            ).writeText(initialGraph.toJson())

            file
        }
    }

    var fileList by remember {
        mutableStateOf(getScenarioFiles())
    }

    var currentFile by remember {
        mutableStateOf<File?>(initialFile)
    }

    var text by remember {
        mutableStateOf("")
    }

    var graph by remember {
        mutableStateOf(NodeGraph.defaultTemplate())
    }

    var mode by remember {
        mutableStateOf(CanvasMode.NODES)
    }

    var dirty by remember {
        mutableStateOf(false)
    }

    var graphRevision by remember {
        mutableStateOf(0)
    }

    val canvasController = remember {
        NodeCanvasController()
    }

    fun notifyGraphChanged(newGraph: NodeGraph) {
        graph = newGraph
        graphRevision++
        dirty = true
    }

    fun reloadFiles() {
        fileList = getScenarioFiles()
    }

    fun persist() {
        val file = currentFile ?: return

        runCatching {
            when (mode) {
                CanvasMode.NODES -> {
                    val output = ScriptNodeSync.graphToText(
                        graph = graph,
                        includeMeta = true
                    )

                    file.writeText(output)

                    val sidecar = File(
                        file.parentFile,
                        "${file.nameWithoutExtension}.graph.json"
                    )

                    sidecar.writeText(graph.toJson())

                    text = ScriptNodeSync.stripMeta(output)
                }

                CanvasMode.TEXT -> {
                    file.writeText(text)

                    // В текстовом режиме обновляем вспомогательный граф.
                    val parsedGraph = ScriptNodeSync.textToGraph(text)

                    val sidecar = File(
                        file.parentFile,
                        "${file.nameWithoutExtension}.graph.json"
                    )

                    sidecar.writeText(parsedGraph.toJson())
                }
            }

            dirty = false
            reloadFiles()
        }.onFailure { error ->
            println(
                "ScriptFX Studio: не удалось сохранить сценарий: " +
                        error.message
            )
        }
    }

    fun openFile(file: File) {
        if (dirty) {
            persist()
        }

        runCatching {
            val fileText = file.readText()

            val sidecar = File(
                file.parentFile,
                "${file.nameWithoutExtension}.graph.json"
            )

            val savedGraph = runCatching {
                if (sidecar.isFile) {
                    NodeGraph.fromJson(sidecar.readText())
                } else {
                    null
                }
            }.getOrNull()

            currentFile = file
            text = ScriptNodeSync.stripMeta(fileText)

            graph = savedGraph
                ?: ScriptNodeSync.textToGraph(fileText)

            canvasController.resetHistory(graph)

            graphRevision++

            mode = if (
                savedGraph != null ||
                ScriptNodeSync.hasMeta(fileText)
            ) {
                CanvasMode.NODES
            } else {
                CanvasMode.TEXT
            }

            dirty = false
        }.onFailure { error ->
            println(
                "ScriptFX Studio: не удалось открыть ${file.name}: " +
                        error.message
            )
        }
    }

    LaunchedEffect(initialFile) {
        openFile(initialFile)
    }

    Column(
        Modifier.fillMaxSize()
    ) {
        Row(
            Modifier.fillMaxSize()
        ) {
            // -----------------------------------------------------
            // LEFT: FILE EXPLORER + NODE PALETTE
            // -----------------------------------------------------

            Column(
                Modifier
                    .width(250.dp)
                    .fillMaxHeight()
                    .background(StudioColors.Panel)
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = 12.dp,
                            vertical = 8.dp
                        ),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Сценарии",
                        color = StudioColors.Text,
                        style = MaterialTheme.typography.titleMedium
                    )

                    IconButton(
                        onClick = {
                            val file = File(
                                scenariosDir,
                                "script_${System.currentTimeMillis()}.sfxs"
                            )

                            val newGraph = NodeGraph.defaultTemplate()

                            runCatching {
                                file.writeText(
                                    ScriptNodeSync.graphToText(
                                        newGraph,
                                        includeMeta = true
                                    )
                                )

                                File(
                                    scenariosDir,
                                    "${file.nameWithoutExtension}.graph.json"
                                ).writeText(newGraph.toJson())

                                reloadFiles()
                                openFile(file)
                            }
                        }
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "Создать сценарий",
                            tint = StudioColors.Text
                        )
                    }
                }

                HorizontalDivider(
                    color = StudioColors.Border
                )

                LazyColumn(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    items(
                        items = fileList,
                        key = { it.absolutePath }
                    ) { file ->
                        val selected = currentFile == file

                        Row(
                            Modifier
                                .fillMaxWidth()
                                .background(
                                    if (selected) {
                                        StudioColors.AccentSoft
                                    } else {
                                        Color.Transparent
                                    }
                                )
                                .clickable {
                                    if (currentFile != file) {
                                        openFile(file)
                                    }
                                }
                                .padding(
                                    horizontal = 12.dp,
                                    vertical = 11.dp
                                ),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = file.name,
                                color = if (selected) {
                                    StudioColors.Text
                                } else {
                                    StudioColors.TextMuted
                                },
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }

                if (mode == CanvasMode.NODES) {
                    HorizontalDivider(
                        color = StudioColors.Border
                    )

                    NodePalette(
                        onAdd = { entry ->
                            val newNode = GraphNode(
                                id = "${entry.kind.name.lowercase()}_" +
                                        System.currentTimeMillis(),
                                kind = entry.kind,
                                title = entry.title,
                                params = entry.defaultParams
                            )

                            canvasController.addNode(newNode)
                            dirty = true
                        },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    )
                }
            }

            // -----------------------------------------------------
            // CENTER: CANVAS / TEXT EDITOR
            // -----------------------------------------------------

            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(StudioColors.Background)
            ) {
                when (mode) {
                    CanvasMode.NODES -> {
                        NodeCanvas(
                            graph = graph,
                            onGraphChanged = ::notifyGraphChanged,
                            controller = canvasController,
                            modifier = Modifier.fillMaxSize()
                        )

                        CanvasToolbar(
                            controller = canvasController,
                            dirty = dirty,
                            onSave = {
                                persist()
                            },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(12.dp)
                        )
                    }

                    CanvasMode.TEXT -> {
                        OutlinedTextField(
                            value = text,
                            onValueChange = {
                                text = it
                                dirty = true
                            },
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp),
                            textStyle = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                FloatingActionButton(
                    onClick = {
                        when (mode) {
                            CanvasMode.NODES -> {
                                persist()
                                mode = CanvasMode.TEXT
                            }

                            CanvasMode.TEXT -> {
                                graph = ScriptNodeSync.textToGraph(text)
                                graphRevision++
                                mode = CanvasMode.NODES
                                dirty = true
                            }
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp),
                    containerColor = StudioColors.Accent,
                    contentColor = Color.White
                ) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Переключить режим"
                    )
                }
            }

            // -----------------------------------------------------
            // RIGHT: NODE INSPECTOR
            // -----------------------------------------------------

            if (mode == CanvasMode.NODES) {
                NodeInspector(
                    graph = graph,
                    selectedNodeId = canvasController.selectedNodeId.value,
                    revision = graphRevision,
                    onGraphChanged = ::notifyGraphChanged,
                    controller = canvasController,
                    modifier = Modifier
                )
            }
        }
    }

    // Сохраняем текущий сценарий при закрытии редактора.
    DisposableEffect(Unit) {
        onDispose {
            if (dirty) {
                persist()
            }
        }
    }
}


@Composable
private fun CanvasToolbar(
    controller: NodeCanvasController,
    dirty: Boolean,
    onSave: () -> Unit,
    modifier: Modifier = Modifier
) {
    val canUndo by controller.canUndo
    val canRedo by controller.canRedo

    Surface(
        modifier = modifier,
        color = StudioColors.PanelRaised,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, StudioColors.Border)
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = 6.dp,
                vertical = 2.dp
            ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (dirty) {
                Text(
                    "●",
                    color = StudioColors.Warning,
                    modifier = Modifier.padding(horizontal = 6.dp)
                )
            }

            TextButton(
                onClick = onSave
            ) {
                Text(
                    "Сохранить",
                    color = StudioColors.Text
                )
            }

            VerticalDivider(
                modifier = Modifier
                    .height(24.dp)
                    .padding(horizontal = 4.dp),
                color = StudioColors.Border
            )

            TextButton(
                enabled = canUndo,
                onClick = { controller.undo() }
            ) {
                Text(
                    "↶ Undo",
                    color = if (canUndo) {
                        StudioColors.Text
                    } else {
                        StudioColors.TextMuted
                    }
                )
            }

            TextButton(
                enabled = canRedo,
                onClick = { controller.redo() }
            ) {
                Text(
                    "↷ Redo",
                    color = if (canRedo) {
                        StudioColors.Text
                    } else {
                        StudioColors.TextMuted
                    }
                )
            }

            VerticalDivider(
                modifier = Modifier
                    .height(24.dp)
                    .padding(horizontal = 4.dp),
                color = StudioColors.Border
            )

            TextButton(
                onClick = { controller.zoomOut() }
            ) {
                Text("−", color = StudioColors.Text)
            }

            TextButton(
                onClick = { controller.zoomIn() }
            ) {
                Text("+", color = StudioColors.Text)
            }

            TextButton(
                onClick = { controller.fitGraph() }
            ) {
                Text(
                    "Вписать",
                    color = StudioColors.Text
                )
            }
        }
    }
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