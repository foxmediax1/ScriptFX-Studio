
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.zIndex
import net.foxmediax.app.project.ExportService
import net.foxmediax.app.project.ProjectManifest
import net.foxmediax.utils.nodes.GraphNode
import net.foxmediax.utils.nodes.GraphIssueSeverity
import net.foxmediax.utils.nodes.NodeGraph
import net.foxmediax.utils.nodes.ScriptNodeSync
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.foxmediax.app.project.ExportPreviewService

enum class CanvasMode {
    NODES,
    TEXT
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

// =====================================================
// PROJECT WORKSPACE
// =====================================================

@Composable
fun ProjectWorkspace(
    project: ProjectManifest,
    projectRoot: File,
    onBackToHome: () -> Unit
) {
    var tab by remember {
        mutableStateOf(WorkspaceTab.SCENARIOS)
    }

    // Команда перехода из диагностики.
    var navigationFileName by remember {
        mutableStateOf<String?>(null)
    }

    var navigationNodeId by remember {
        mutableStateOf<String?>(null)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(StudioColors.Background)
    ) {
        StudioTopBar(
            projectName = projectRoot.name,
            projectPath = projectRoot.absolutePath,
            onBackToHome = onBackToHome
        )

        StudioModuleBar(
            selected = tab,
            onSelected = {
                tab = it
            }
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(StudioColors.Background)
        ) {
            when (tab) {

                WorkspaceTab.SCENARIOS -> {
                    ScenariosPane(
                        projectRoot = projectRoot,
                        navigationFileName = navigationFileName,
                        navigationNodeId = navigationNodeId,
                        onNavigationHandled = {
                            navigationFileName = null
                            navigationNodeId = null
                        }
                    )
                }

                WorkspaceTab.SETTINGS -> {
                    SettingsPane(
                        projectRoot = projectRoot,
                        onOpenScenarioIssue = { fileName, nodeId ->
                            navigationFileName = fileName
                            navigationNodeId = nodeId

                            // Переключаемся в редактор сценариев.
                            tab = WorkspaceTab.SCENARIOS
                        }
                    )
                }

                else -> {
                    PlaceholderPane(tab.label)
                }
            }
        }

        StudioStatusBar(
            projectName = projectRoot.name,
            activeModule = tab.label
        )
    }
}

// =====================================================
// TOP BAR
// =====================================================

@Composable
private fun StudioTopBar(
    projectName: String,
    projectPath: String,
    onBackToHome: () -> Unit
) {
    Column {
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

                Column(
                    verticalArrangement = Arrangement.spacedBy(1.dp)
                ) {
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

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Box(
                    Modifier
                        .size(7.dp)
                        .background(
                            StudioColors.Success,
                            RoundedCornerShape(50)
                        )
                )

                Text(
                    "Project Loaded",
                    color = StudioColors.TextMuted,
                    style = MaterialTheme.typography.labelMedium
                )
            }

            Spacer(Modifier.width(16.dp))

            TextButton(
                onClick = onBackToHome
            ) {
                Text(
                    "← На главную",
                    color = StudioColors.Text,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }

        HorizontalDivider(
            color = StudioColors.BorderSoft
        )
    }
}

// =====================================================
// MODULE BAR
// =====================================================

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
                    .padding(
                        horizontal = 3.dp,
                        vertical = 5.dp
                    )
                    .background(
                        if (active) {
                            StudioColors.AccentSoft
                        } else {
                            Color.Transparent
                        },
                        RoundedCornerShape(6.dp)
                    )
                    .clickable {
                        onSelected(item)
                    }
                    .padding(
                        horizontal = 14.dp,
                        vertical = 9.dp
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = item.label,
                    color = if (active) {
                        StudioColors.AccentHover
                    } else {
                        StudioColors.TextMuted
                    },
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }

    HorizontalDivider(
        color = StudioColors.Border
    )
}

// =====================================================
// STATUS BAR
// =====================================================

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

// =====================================================
// PLACEHOLDER
// =====================================================

@Composable
fun PlaceholderPane(name: String) {
    Box(
        Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "$name — в разработке",
            color = Color(0xFF9A9AB0)
        )
    }
}

// =====================================================
// SCENARIOS PANE
// =====================================================

@Composable
fun ScenariosPane(
    projectRoot: File,
    navigationFileName: String? = null,
    navigationNodeId: String? = null,
    onNavigationHandled: () -> Unit = {}
) {
    val scenariosDir = remember(projectRoot) {
        File(projectRoot, "scenarios").apply {
            mkdirs()
        }
    }

    fun getScenarioFiles(): List<File> {
        return scenariosDir.listFiles()
            ?.filter {
                it.isFile &&
                        it.extension.equals(
                            "sfxs",
                            ignoreCase = true
                        )
            }
            ?.sortedBy {
                it.name
            }
            ?: emptyList()
    }

    // Создание начального сценария.
    val initialFile = remember(scenariosDir) {
        val existing = getScenarioFiles().firstOrNull()

        existing ?: run {
            val file = File(
                scenariosDir,
                "main.sfxs"
            )

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
            ).writeText(
                initialGraph.toJson()
            )

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

    var textModeBaseline by remember {
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
        mutableIntStateOf(0)
    }

    // Узел, на который нужно перейти после загрузки сценария.
    var pendingFocusNodeId by remember {
        mutableStateOf<String?>(null)
    }

    val canvasController = remember {
        NodeCanvasController()
    }

    // -------------------------------------------------
    // GRAPH CALLBACKS
    // -------------------------------------------------

    fun notifyGraphChanged(newGraph: NodeGraph) {
        graph = newGraph
        graphRevision++
        dirty = true
    }

    fun reloadFiles() {
        fileList = getScenarioFiles()
    }

    // -------------------------------------------------
    // SAVE SCENARIO
    // -------------------------------------------------

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

                    sidecar.writeText(
                        graph.toJson()
                    )

                    text = ScriptNodeSync.stripMeta(output)
                }

                CanvasMode.TEXT -> {
                    file.writeText(text)

                    // Старый JSON графа больше не соответствует тексту.
                    val sidecar = File(
                        file.parentFile,
                        "${file.nameWithoutExtension}.graph.json"
                    )

                    if (
                        sidecar.exists() &&
                        !sidecar.delete()
                    ) {
                        throw IllegalStateException(
                            "Не удалось удалить устаревший файл графа: " +
                                    sidecar.name
                        )
                    }
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

    // -------------------------------------------------
    // OPEN SCENARIO
    // -------------------------------------------------

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
                    NodeGraph.fromJson(
                        sidecar.readText()
                    )
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

    // -------------------------------------------------
    // INITIAL SCENARIO
    // -------------------------------------------------

    LaunchedEffect(initialFile) {
        // Если пришла команда открыть конкретный сценарий,
        // первоначальный файл не должен перезаписывать выбор.
        if (navigationFileName == null) {
            openFile(initialFile)
        }
    }

    // -------------------------------------------------
    // NAVIGATION FROM VALIDATION
    // -------------------------------------------------

    LaunchedEffect(
        navigationFileName,
        navigationNodeId
    ) {
        val fileName = navigationFileName
            ?: return@LaunchedEffect

        val targetFile = File(projectRoot, fileName)

        if (targetFile.isFile) {
            openFile(targetFile)

            if (navigationNodeId != null) {
                // Проблемные узлы открываем в Node Editor.
                mode = CanvasMode.NODES

                pendingFocusNodeId = navigationNodeId
            }
        } else {
            println(
                "ScriptFX Studio: сценарий не найден: $fileName"
            )
        }

        onNavigationHandled()
    }

    // -------------------------------------------------
    // FOCUS PROBLEM NODE
    // -------------------------------------------------

    LaunchedEffect(
        graphRevision,
        pendingFocusNodeId
    ) {
        val nodeId = pendingFocusNodeId
            ?: return@LaunchedEffect

        if (graph.node(nodeId) != null) {
            canvasController.focusNode(nodeId)
            pendingFocusNodeId = null
        } else {
            println(
                "ScriptFX Studio: узел $nodeId не найден " +
                        "в сценарии ${currentFile?.name}"
            )

            pendingFocusNodeId = null
        }
    }

    // =================================================
    // USER INTERFACE
    // =================================================

    Column(
        Modifier.fillMaxSize()
    ) {
        Row(
            Modifier.fillMaxSize()
        ) {

            // =============================================
            // LEFT: EXPLORER AND NODE PALETTE
            // =============================================

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
                                ).writeText(
                                    newGraph.toJson()
                                )

                                reloadFiles()
                                openFile(file)
                            }.onFailure {
                                println(
                                    "ScriptFX Studio: не удалось создать сценарий: " +
                                            it.message
                                )
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
                        key = {
                            it.absolutePath
                        }
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

            // =============================================
            // CENTER: NODE CANVAS / TEXT EDITOR
            // =============================================

            Box(
                Modifier
                    .weight(1f, fill = true)
                    .fillMaxHeight()
                    .clipToBounds()
                    .background(StudioColors.Background)
            ) {

                // LAYER 0 — NODE EDITOR
                // Не уничтожается при переключении режима.

                NodeCanvas(
                    graph = graph,
                    onGraphChanged = ::notifyGraphChanged,
                    controller = canvasController,
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(0f)
                )

                // LAYER 1 — TEXT EDITOR

                if (mode == CanvasMode.TEXT) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .zIndex(1f)
                            .background(StudioColors.Background)
                            .clickable(
                                interactionSource = remember {
                                    MutableInteractionSource()
                                },
                                indication = null,
                                onClick = {}
                            )
                    ) {
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
                } else {
                    CanvasToolbar(
                        controller = canvasController,
                        dirty = dirty,
                        onSave = {
                            persist()
                        },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(12.dp)
                            .zIndex(1f)
                    )
                }

                // LAYER 2 — MODE SWITCH

                FloatingActionButton(
                    onClick = {
                        when (mode) {

                            CanvasMode.NODES -> {
                                persist()

                                textModeBaseline = text
                                mode = CanvasMode.TEXT
                            }

                            CanvasMode.TEXT -> {
                                if (text != textModeBaseline) {
                                    val parsedGraph =
                                        ScriptNodeSync.textToGraph(text)

                                    graph = parsedGraph

                                    canvasController.resetHistory(
                                        parsedGraph
                                    )

                                    graphRevision++
                                    dirty = true
                                }

                                mode = CanvasMode.NODES
                            }
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp)
                        .zIndex(2f),
                    containerColor = StudioColors.Accent,
                    contentColor = Color.White
                ) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Переключить режим"
                    )
                }
            }

            // =============================================
            // RIGHT: NODE INSPECTOR
            // =============================================

            Box(
                modifier = Modifier
                    .width(320.dp)
                    .fillMaxHeight()
                    .background(StudioColors.Panel)
            ) {
                if (mode == CanvasMode.NODES) {
                    NodeInspector(
                        graph = graph,
                        selectedNodeId = canvasController.selectedNodeId.value,
                        revision = graphRevision,
                        onGraphChanged = ::notifyGraphChanged,
                        controller = canvasController,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }

    // Сохранение при закрытии редактора сценариев.
    DisposableEffect(Unit) {
        onDispose {
            if (dirty) {
                persist()
            }
        }
    }
}

// =====================================================
// CANVAS TOOLBAR
// =====================================================

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
        border = BorderStroke(
            1.dp,
            StudioColors.Border
        )
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
                onClick = {
                    controller.undo()
                }
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
                onClick = {
                    controller.redo()
                }
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
                onClick = {
                    controller.zoomOut()
                }
            ) {
                Text(
                    "−",
                    color = StudioColors.Text
                )
            }

            TextButton(
                onClick = {
                    controller.zoomIn()
                }
            ) {
                Text(
                    "+",
                    color = StudioColors.Text
                )
            }

            TextButton(
                onClick = {
                    controller.fitGraph()
                }
            ) {
                Text(
                    "Вписать",
                    color = StudioColors.Text
                )
            }
        }
    }
}

// =====================================================
// SETTINGS PANE
// =====================================================
@Composable
fun SettingsPane(
    projectRoot: File,
    onOpenScenarioIssue: (String, String?) -> Unit = { _, _ -> }
) {

    val manifestFile = File(
        projectRoot,
        "scriptfx.project.json"
    )

    // =====================================================
    // EXPORT PATH
    // =====================================================

    var exportPath by remember(projectRoot) {
        mutableStateOf(
            runCatching {
                val content = manifestFile.readText()

                Regex(
                    "\"exportPath\"\\s*:\\s*\"([^\"]*)\""
                ).find(content)?.groupValues?.get(1) ?: ""

            }.getOrNull() ?: ""
        )
    }

    fun persistExportPath(path: String) {

        if (!manifestFile.isFile) return

        val content = manifestFile.readText()

        val escapedPath = path
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")

        val regex = Regex(
            "\"exportPath\"\\s*:\\s*\"[^\"]*\""
        )

        val updated = regex.replace(content) {
            "\"exportPath\":\"$escapedPath\""
        }

        manifestFile.writeText(updated)
    }

    // =====================================================
    // STATE
    // =====================================================

    var busy by remember {
        mutableStateOf(false)
    }

    var validating by remember {
        mutableStateOf(false)
    }

    var previewing by remember {
        mutableStateOf(false)
    }

    var validationError by remember {
        mutableStateOf<String?>(null)
    }

    var lastResult by remember {
        mutableStateOf<ExportService.Result?>(null)
    }

    var validationReport by remember {
        mutableStateOf<ExportService.ValidationReport?>(null)
    }

    var exportPreview by remember {
        mutableStateOf<ExportPreviewService.Plan?>(null)
    }

    var showValidationDialog by remember {
        mutableStateOf(false)
    }

    var showExportPreview by remember {
        mutableStateOf(false)
    }

    val scope = rememberCoroutineScope()

    // =====================================================
    // VALIDATE PROJECT
    // =====================================================

    fun validateProject() {

        scope.launch {

            validating = true
            validationError = null

            try {

                val target = exportPath
                    .takeIf { it.isNotBlank() }
                    ?.let(::File)

                validationReport = withContext(Dispatchers.IO) {

                    ExportService.validateProject(
                        projectRoot = projectRoot,
                        targetDir = target
                    )
                }

                showValidationDialog = true

            } catch (e: Exception) {

                validationError = e.message
                    ?: "Не удалось проверить проект."

            } finally {
                validating = false
            }
        }
    }

    // =====================================================
    // PREPARE EXPORT PREVIEW
    // =====================================================

    fun prepareExportPreview() {

        if (exportPath.isBlank()) {
            validationError = "Сначала укажи папку Minecraft."
            return
        }

        scope.launch {

            previewing = true
            validationError = null

            try {

                val target = File(exportPath.trim())

                exportPreview = withContext(Dispatchers.IO) {

                    ExportPreviewService.createPlan(
                        projectRoot = projectRoot,
                        targetDir = target
                    )
                }

                showExportPreview = true

            } catch (e: Exception) {

                validationError = e.message
                    ?: "Не удалось подготовить предпросмотр."

            } finally {
                previewing = false
            }
        }
    }

    // =====================================================
    // PERFORM EXPORT
    // =====================================================

    fun performExport() {

        if (exportPath.isBlank()) {
            validationError = "Не указана папка Minecraft."
            return
        }

        scope.launch {

            busy = true
            validationError = null

            try {

                val target = File(exportPath.trim())

                val result = withContext(Dispatchers.IO) {

                    ExportService.export(
                        projectRoot = projectRoot,
                        targetDir = target
                    )
                }

                lastResult = result

            } catch (e: Exception) {

                lastResult = ExportService.Result(
                    success = false,
                    filesCopied = 0,
                    message = "Не удалось выполнить экспорт: " +
                            (e.message ?: "неизвестная ошибка")
                )

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
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

        Text(
            text = "Настройки проекта",
            style = MaterialTheme.typography.headlineSmall
        )

        // -------------------------------------------------
        // MINECRAFT PATH
        // -------------------------------------------------

        Text(
            text = "Путь к папке Minecraft (.minecraft)",
            style = MaterialTheme.typography.titleMedium
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {

            OutlinedTextField(
                value = exportPath,
                onValueChange = {

                    exportPath = it

                    runCatching {
                        persistExportPath(it)
                    }.onFailure { error ->
                        validationError = error.message
                            ?: "Не удалось сохранить путь."
                    }
                },
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text("C:\\Users\\...\\.minecraft")
                },
                singleLine = true
            )

            Button(
                enabled = !busy && !previewing && !validating,
                onClick = {

                    val picked = FileDialogs.pickMinecraftFolder(
                        exportPath
                            .takeIf { it.isNotBlank() }
                            ?.let(::File)
                    )

                    if (picked != null) {

                        exportPath = picked.absolutePath

                        runCatching {
                            persistExportPath(exportPath)
                        }.onFailure { error ->
                            validationError = error.message
                                ?: "Не удалось сохранить путь."
                        }
                    }
                }
            ) {
                Text("Выбрать…")
            }
        }

        // -------------------------------------------------
        // DESTINATION
        // -------------------------------------------------

        if (exportPath.isNotBlank()) {

            val destination = File(
                exportPath,
                "config/scriptfx/projects/${projectRoot.name}"
            )

            Text(
                text = "Папка экспорта: ${destination.absolutePath}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        HorizontalDivider()

        // -------------------------------------------------
        // VALIDATION BUTTON
        // -------------------------------------------------

        OutlinedButton(
            enabled = !busy && !validating && !previewing,
            onClick = {
                validateProject()
            }
        ) {
            Text(
                if (validating) {
                    "Проверка проекта…"
                } else {
                    "Проверить проект"
                }
            )
        }

        // -------------------------------------------------
        // PREVIEW BUTTON
        // -------------------------------------------------

        Button(
            enabled = !busy &&
                    !validating &&
                    !previewing &&
                    exportPath.isNotBlank(),
            onClick = {
                prepareExportPreview()
            }
        ) {
            Text(
                if (previewing) {
                    "Подготовка предпросмотра…"
                } else {
                    "Предпросмотр экспорта"
                }
            )
        }

        // -------------------------------------------------
        // ERRORS
        // -------------------------------------------------

        validationError?.let {

            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        // -------------------------------------------------
        // LAST EXPORT RESULT
        // -------------------------------------------------

        lastResult?.let { result ->

            val resultColor = if (result.success) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.error
            }

            HorizontalDivider()

            Text(
                text = result.message,
                color = resultColor,
                style = MaterialTheme.typography.bodyMedium
            )

            result.validation?.let { report ->

                if (report.hasIssues) {

                    TextButton(
                        onClick = {

                            validationReport = report
                            showValidationDialog = true
                        }
                    ) {
                        Text(
                            "Подробнее о проверке: " +
                                    "${report.errors} ошибок, " +
                                    "${report.warnings} предупреждений"
                        )
                    }
                }
            }
        }

        Spacer(
            modifier = Modifier.weight(1f)
        )

        // -------------------------------------------------
        // INFO
        // -------------------------------------------------

        Text(
            text = "Экспорт копирует содержимое проекта в " +
                    "config/scriptfx/projects/<name>. " +
                    "Сценарии из scenarios/ преобразуются в scripts/. " +
                    "Файлы *.graph.json остаются только в Studio.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    // =====================================================
    // EXPORT PREVIEW DIALOG
    // =====================================================

    if (showExportPreview) {

        exportPreview?.let { plan ->

            ExportPreviewDialog(
                plan = plan,

                onDismiss = {
                    showExportPreview = false
                },

                onConfirm = {

                    showExportPreview = false

                    performExport()
                }
            )
        }
    }

    // =====================================================
    // VALIDATION DIALOG
    // =====================================================

    if (showValidationDialog) {

        validationReport?.let { report ->

            ProjectValidationDialog(
                report = report,

                onDismiss = {
                    showValidationDialog = false
                },

                onOpenIssue = { fileName, nodeId ->

                    showValidationDialog = false

                    onOpenScenarioIssue(
                        fileName,
                        nodeId
                    )
                }
            )
        }
    }
}



@Composable
private fun ProjectValidationDialog(
    report: ExportService.ValidationReport,
    onDismiss: () -> Unit,
    onOpenIssue: (String, String?) -> Unit
) {
    val errors = report.issues.count {
        it.issue.severity == GraphIssueSeverity.ERROR
    }

    val warnings = report.issues.count {
        it.issue.severity == GraphIssueSeverity.WARNING
    }

    val infos = report.issues.count {
        it.issue.severity == GraphIssueSeverity.INFO
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Проверка проекта")
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Проверено сценариев: ${report.scenariosChecked}")

                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        "Ошибки: $errors",
                        color = MaterialTheme.colorScheme.error
                    )

                    Text(
                        "Предупреждения: $warnings",
                        color = MaterialTheme.colorScheme.tertiary
                    )

                    Text(
                        "Информация: $infos",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                HorizontalDivider()

                if (report.issues.isEmpty()) {
                    Text(
                        "Проблем не обнаружено. Проект прошёл проверку.",
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 380.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(report.issues) { entry ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        MaterialTheme.colorScheme.surfaceVariant,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Text(
                                    entry.scenarioFile,
                                    style = MaterialTheme.typography.labelMedium
                                )

                                val severityColor = when (entry.issue.severity) {
                                    GraphIssueSeverity.ERROR ->
                                        MaterialTheme.colorScheme.error

                                    GraphIssueSeverity.WARNING ->
                                        MaterialTheme.colorScheme.tertiary

                                    GraphIssueSeverity.INFO ->
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                }

                                Text(
                                    entry.issue.severity.name,
                                    color = severityColor,
                                    style = MaterialTheme.typography.labelSmall
                                )

                                Text(
                                    entry.issue.message,
                                    style = MaterialTheme.typography.bodyMedium
                                )

                                entry.issue.nodeId?.let { nodeId ->
                                    TextButton(
                                        onClick = {
                                            onOpenIssue(
                                                entry.scenarioFile,
                                                nodeId
                                            )
                                        }
                                    ) {
                                        Text("Перейти к узлу →")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Закрыть")
            }
        }
    )
}