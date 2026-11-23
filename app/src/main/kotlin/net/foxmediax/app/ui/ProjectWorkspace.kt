
package net.foxmediax.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import net.foxmediax.app.project.ExportPreviewService
import net.foxmediax.app.project.ExportService
import net.foxmediax.app.project.GraphHistoryStore
import net.foxmediax.app.project.GraphRecoveryStore
import net.foxmediax.app.project.ProjectManifest

import net.foxmediax.utils.nodes.GraphIssueSeverity
import net.foxmediax.utils.nodes.GraphNode
import net.foxmediax.utils.nodes.NodeGraph
import net.foxmediax.utils.nodes.NodeGraphHistory
import net.foxmediax.utils.nodes.ScriptNodeSync

import java.io.File
import java.util.Date

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

    // =====================================================
    // SCENARIO DIRECTORY
    // =====================================================

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

    // =====================================================
    // INITIAL SCENARIO
    // =====================================================

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

    // =====================================================
    // STATE
    // =====================================================

    var fileList by remember {
        mutableStateOf(getScenarioFiles())
    }

    var currentFile by remember {
        mutableStateOf<File?>(initialFile)
    }

    var textFieldValue by remember {
        mutableStateOf(TextFieldValue(""))
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

    var pendingFocusNodeId by remember {
        mutableStateOf<String?>(null)
    }

    val canvasController = remember {
        NodeCanvasController()
    }

    val textHistory = remember {
        TextEditorHistory(300)
    }

    val textHistoryStore = remember(projectRoot) {
        TextEditorHistoryStore()
    }

    // =====================================================
    // GRAPH RECOVERY
    // =====================================================

    val graphRecoveryStore = remember(projectRoot) {
        GraphRecoveryStore()
    }

    var graphBaselineFingerprint by remember(projectRoot) {
        mutableStateOf("")
    }

    var pendingGraphRecovery by remember {
        mutableStateOf<GraphRecoveryStore.Recovery?>(null)
    }

    var pendingRecoveryFile by remember {
        mutableStateOf<File?>(null)
    }

    var showGraphRecoveryDialog by remember {
        mutableStateOf(false)
    }

    // =====================================================
    // PERSISTENT GRAPH HISTORY
    // =====================================================

    val graphHistoryStore = remember(projectRoot) {
        GraphHistoryStore()
    }

    var pendingGraphHistory by remember {
        mutableStateOf<NodeGraphHistory.Snapshot?>(null)
    }

    // =====================================================
    // SHARED HISTORY MANAGER
    // =====================================================

    val historyManager = remember(
        textHistory,
        canvasController
    ) {
        ProjectHistoryManager(
            activeMode = {
                mode
            },
            textHistory = textHistory,
            graphController = canvasController,
            currentText = {
                textFieldValue
            },
            onTextRestored = { restored ->
                textFieldValue = restored
                dirty = restored.text != textModeBaseline
            }
        )
    }

    val canUndoText by historyManager.canUndo
    val canRedoText by historyManager.canRedo

    // =====================================================
    // GRAPH CALLBACKS
    // =====================================================

    fun notifyGraphChanged(newGraph: NodeGraph) {
        graph = newGraph
        graphRevision++
        dirty = true
    }

    fun reloadFiles() {
        fileList = getScenarioFiles()
    }

    // =====================================================
    // HISTORY ACTIONS
    // =====================================================

    fun undoText() {
        historyManager.undo()
    }

    fun redoText() {
        historyManager.redo()
    }

    // =====================================================
    // SAVE SCENARIO
    // =====================================================

    fun persist(): Boolean {

        val file = currentFile ?: return false

        return try {

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

                    textFieldValue = TextFieldValue(
                        ScriptNodeSync.stripMeta(output)
                    )
                }

                CanvasMode.TEXT -> {

                    file.writeText(
                        textFieldValue.text
                    )

                    textModeBaseline = textFieldValue.text

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

            // Обновляем контрольную сумму сохранённого состояния.
            graphBaselineFingerprint = graphRecoveryStore.fingerprint(
                projectRoot,
                file
            )

            // Удаляем устаревшую аварийную копию.
            runCatching {
                graphRecoveryStore.clear(
                    projectRoot,
                    file
                )
            }.onFailure {
                println(
                    "ScriptFX Studio: не удалось очистить резервную копию: " +
                            it.message
                )
            }

            // Сохраняем постоянную историю графа.
            if (mode == CanvasMode.NODES) {

                runCatching {
                    graphHistoryStore.save(
                        projectRoot = projectRoot,
                        scenarioFile = file,
                        baselineFingerprint = graphBaselineFingerprint,
                        currentGraphJson = graph.toJson(),
                        snapshot = canvasController.historySnapshot()
                    )
                }.onFailure {
                    println(
                        "ScriptFX Studio: не удалось сохранить историю графа: " +
                                it.message
                    )
                }

            } else {

                runCatching {
                    graphHistoryStore.clear(
                        projectRoot,
                        file
                    )
                }.onFailure {
                    println(
                        "ScriptFX Studio: не удалось очистить историю графа: " +
                                it.message
                    )
                }
            }

            dirty = false
            reloadFiles()

            true

        } catch (error: Exception) {

            println(
                "ScriptFX Studio: не удалось сохранить сценарий: " +
                        error.message
            )

            false
        }
    }

    // =====================================================
    // OPEN SCENARIO
    // =====================================================

    fun openFile(file: File) {

        // Не переключаем сценарий, если предыдущий не удалось сохранить.
        if (dirty && !persist()) {
            return
        }

        showGraphRecoveryDialog = false
        pendingGraphRecovery = null
        pendingRecoveryFile = null
        pendingGraphHistory = null

        val previousFile = currentFile

        // Сохраняем историю предыдущего текстового сценария.
        if (
            previousFile != null &&
            mode == CanvasMode.TEXT
        ) {
            runCatching {
                textHistoryStore.save(
                    projectRoot = projectRoot,
                    scenarioFile = previousFile,
                    baselineText = textModeBaseline,
                    snapshot = textHistory.snapshot(textFieldValue)
                )
            }.onFailure {
                println(
                    "ScriptFX Studio: не удалось сохранить историю: " +
                            it.message
                )
            }
        }

        runCatching {

            val fileText = file.readText()

            val strippedText = ScriptNodeSync.stripMeta(fileText)

            val loadedFingerprint = graphRecoveryStore.fingerprint(
                projectRoot,
                file
            )

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

            val restoredTextHistory = textHistoryStore.load(
                projectRoot = projectRoot,
                scenarioFile = file,
                expectedBaselineText = strippedText
            )

            currentFile = file
            graphBaselineFingerprint = loadedFingerprint

            val loadedGraph = savedGraph
                ?: ScriptNodeSync.textToGraph(fileText)

            graph = loadedGraph

            canvasController.resetHistory(loadedGraph)

            graphRevision++

            if (restoredTextHistory != null) {

                textFieldValue = textHistory.restore(
                    restoredTextHistory.snapshot
                )

                textModeBaseline = restoredTextHistory.baselineText

                mode = CanvasMode.TEXT

                dirty = textFieldValue.text != textModeBaseline

            } else {

                textFieldValue = TextFieldValue(
                    strippedText
                )

                textModeBaseline = strippedText

                textHistory.reset()

                mode = if (
                    savedGraph != null ||
                    ScriptNodeSync.hasMeta(fileText)
                ) {
                    CanvasMode.NODES
                } else {
                    CanvasMode.TEXT
                }

                dirty = false
            }

            // Восстанавливаем историю графа только в нодовом режиме.
            if (mode == CanvasMode.NODES) {

                val recovery = graphRecoveryStore.load(
                    projectRoot = projectRoot,
                    scenarioFile = file,
                    expectedBaselineFingerprint = loadedFingerprint
                )

                if (recovery != null) {

                    pendingGraphRecovery = recovery

                    pendingGraphHistory = graphHistoryStore.load(
                        projectRoot = projectRoot,
                        scenarioFile = file,
                        expectedBaselineFingerprint = loadedFingerprint,
                        expectedGraphJson = recovery.graph.toJson()
                    )

                    pendingRecoveryFile = file
                    showGraphRecoveryDialog = true

                } else {

                    val savedHistory = graphHistoryStore.load(
                        projectRoot = projectRoot,
                        scenarioFile = file,
                        expectedBaselineFingerprint = loadedFingerprint,
                        expectedGraphJson = loadedGraph.toJson()
                    )

                    if (savedHistory != null) {

                        val restored = canvasController.restoreHistory(
                            savedHistory
                        )

                        if (!restored) {
                            canvasController.resetHistory(loadedGraph)
                        }
                    }
                }
            }

        }.onFailure { error ->

            println(
                "ScriptFX Studio: не удалось открыть ${file.name}: " +
                        error.message
            )
        }
    }

    // =====================================================
    // INITIAL SCENARIO
    // =====================================================

    LaunchedEffect(initialFile) {
        if (navigationFileName == null) {
            openFile(initialFile)
        }
    }

    // =====================================================
    // NAVIGATION FROM VALIDATION
    // =====================================================

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

    // =====================================================
    // FOCUS PROBLEM NODE
    // =====================================================

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

    // =====================================================
    // AUTOMATIC GRAPH RECOVERY AND HISTORY
    // =====================================================

    LaunchedEffect(
        currentFile?.absolutePath,
        mode,
        graphRevision,
        dirty,
        graphBaselineFingerprint
    ) {

        val file = currentFile
            ?: return@LaunchedEffect

        if (mode != CanvasMode.NODES) {
            return@LaunchedEffect
        }

        if (!dirty || graphBaselineFingerprint.isBlank()) {
            return@LaunchedEffect
        }

        val graphSnapshot = graph.toJson()
        val historySnapshot = canvasController.historySnapshot()
        val baseline = graphBaselineFingerprint

        delay(700)

        runCatching {

            withContext(Dispatchers.IO) {

                graphRecoveryStore.save(
                    projectRoot = projectRoot,
                    scenarioFile = file,
                    baselineFingerprint = baseline,
                    graphJson = graphSnapshot
                )

                graphHistoryStore.save(
                    projectRoot = projectRoot,
                    scenarioFile = file,
                    baselineFingerprint = baseline,
                    currentGraphJson = graphSnapshot,
                    snapshot = historySnapshot
                )
            }

        }.onFailure { error ->

            println(
                "ScriptFX Studio: не удалось сохранить граф и его историю: " +
                        error.message
            )
        }
    }

    // =====================================================
    // PERSIST TEXT EDITOR HISTORY
    // =====================================================

    LaunchedEffect(
        currentFile?.absolutePath,
        mode,
        textFieldValue,
        textModeBaseline,
        canUndoText,
        canRedoText
    ) {

        val file = currentFile
            ?: return@LaunchedEffect

        if (mode != CanvasMode.TEXT) {
            return@LaunchedEffect
        }

        val baseline = textModeBaseline
        val snapshot = textHistory.snapshot(textFieldValue)

        delay(350)

        runCatching {

            withContext(Dispatchers.IO) {

                textHistoryStore.save(
                    projectRoot = projectRoot,
                    scenarioFile = file,
                    baselineText = baseline,
                    snapshot = snapshot
                )
            }

        }.onFailure { error ->

            println(
                "ScriptFX Studio: ошибка сохранения истории: " +
                        error.message
            )
        }
    }

    // =====================================================
    // USER INTERFACE
    // =====================================================

    Column(
        modifier = Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->

                if (
                    event.type != KeyEventType.KeyDown ||
                    !event.isCtrlPressed
                ) {
                    return@onPreviewKeyEvent false
                }

                when {

                    event.key == Key.Z && event.isShiftPressed -> {
                        historyManager.redo()
                        true
                    }

                    event.key == Key.Z -> {
                        historyManager.undo()
                        true
                    }

                    event.key == Key.Y -> {
                        historyManager.redo()
                        true
                    }

                    else -> false
                }
            }
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

                // -----------------------------------------
                // LAYER 0: NODE EDITOR
                // -----------------------------------------

                NodeCanvas(
                    graph = graph,
                    onGraphChanged = ::notifyGraphChanged,
                    controller = canvasController,
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(0f)
                )

                // -----------------------------------------
                // LAYER 1: TEXT EDITOR
                // -----------------------------------------

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
                            value = textFieldValue,

                            onValueChange = { newValue ->

                                if (
                                    newValue.text != textFieldValue.text
                                ) {

                                    textHistory.checkpoint(
                                        textFieldValue
                                    )

                                    dirty = true
                                }

                                textFieldValue = newValue
                            },

                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp),

                            textStyle = MaterialTheme.typography.bodyMedium
                        )

                        // ---------------------------------
                        // TEXT EDITOR TOOLBAR
                        // ---------------------------------

                        Surface(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(24.dp)
                                .zIndex(2f),
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
                                        modifier = Modifier.padding(
                                            horizontal = 6.dp
                                        )
                                    )
                                }

                                TextButton(
                                    onClick = {
                                        persist()
                                    }
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
                                    enabled = canUndoText,
                                    onClick = {
                                        undoText()
                                    }
                                ) {
                                    Text(
                                        "↶ Undo",
                                        color = if (canUndoText) {
                                            StudioColors.Text
                                        } else {
                                            StudioColors.TextMuted
                                        }
                                    )
                                }

                                TextButton(
                                    enabled = canRedoText,
                                    onClick = {
                                        redoText()
                                    }
                                ) {
                                    Text(
                                        "↷ Redo",
                                        color = if (canRedoText) {
                                            StudioColors.Text
                                        } else {
                                            StudioColors.TextMuted
                                        }
                                    )
                                }
                            }
                        }
                    }

                } else {

                    // -------------------------------------
                    // NODE TOOLBAR
                    // -------------------------------------

                    CanvasToolbar(
                        controller = canvasController,
                        historyManager = historyManager,
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

                // -----------------------------------------
                // LAYER 2: MODE SWITCH
                // -----------------------------------------

                FloatingActionButton(
                    onClick = {

                        when (mode) {

                            CanvasMode.NODES -> {

                                if (persist()) {

                                    textModeBaseline = textFieldValue.text

                                    textHistory.reset()

                                    mode = CanvasMode.TEXT
                                }
                            }

                            CanvasMode.TEXT -> {

                                if (
                                    textFieldValue.text != textModeBaseline
                                ) {

                                    val parsedGraph =
                                        ScriptNodeSync.textToGraph(
                                            textFieldValue.text
                                        )

                                    graph = parsedGraph

                                    canvasController.resetHistory(
                                        parsedGraph
                                    )

                                    graphRevision++
                                    dirty = true

                                } else {

                                    currentFile?.let { file ->

                                        val savedHistory = graphHistoryStore.load(
                                            projectRoot = projectRoot,
                                            scenarioFile = file,
                                            expectedBaselineFingerprint = graphBaselineFingerprint,
                                            expectedGraphJson = graph.toJson()
                                        )

                                        if (savedHistory != null) {

                                            val restored = canvasController.restoreHistory(
                                                savedHistory
                                            )

                                            if (!restored) {
                                                canvasController.resetHistory(graph)
                                            }
                                        }
                                    }
                                }

                                textHistory.reset()

                                currentFile?.let { file ->

                                    runCatching {
                                        textHistoryStore.clear(
                                            projectRoot,
                                            file
                                        )
                                    }.onFailure {
                                        println(
                                            "ScriptFX Studio: не удалось очистить историю: " +
                                                    it.message
                                        )
                                    }
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

    // =====================================================
    // SAVE WHEN EDITOR CLOSES
    // =====================================================

    val latestCurrentFile by rememberUpdatedState(currentFile)
    val latestMode by rememberUpdatedState(mode)
    val latestTextValue by rememberUpdatedState(textFieldValue)
    val latestBaseline by rememberUpdatedState(textModeBaseline)
    val latestDirty by rememberUpdatedState(dirty)

    val latestSnapshot by rememberUpdatedState(
        textHistory.snapshot(textFieldValue)
    )

    val latestPersist by rememberUpdatedState(
        newValue = { persist() }
    )

    DisposableEffect(projectRoot) {

        onDispose {

            val file = latestCurrentFile
            val wasDirty = latestDirty

            val saved = if (wasDirty) {
                latestPersist()
            } else {
                true
            }

            if (
                file != null &&
                latestMode == CanvasMode.TEXT
            ) {

                val baseline = if (wasDirty && saved) {
                    latestTextValue.text
                } else {
                    latestBaseline
                }

                runCatching {

                    textHistoryStore.save(
                        projectRoot = projectRoot,
                        scenarioFile = file,
                        baselineText = baseline,
                        snapshot = latestSnapshot
                    )

                }.onFailure { error ->

                    println(
                        "ScriptFX Studio: не удалось сохранить историю " +
                                "при закрытии редактора: ${error.message}"
                    )
                }
            }
        }
    }

    // =====================================================
    // GRAPH RECOVERY DIALOG
    // =====================================================

    if (showGraphRecoveryDialog) {

        val recovery = pendingGraphRecovery
        val recoveryFile = pendingRecoveryFile

        if (
            recovery != null &&
            recoveryFile != null
        ) {

            AlertDialog(
                onDismissRequest = {

                    runCatching {
                        graphRecoveryStore.clear(
                            projectRoot,
                            recoveryFile
                        )
                    }

                    runCatching {
                        graphHistoryStore.clear(
                            projectRoot,
                            recoveryFile
                        )
                    }

                    pendingGraphRecovery = null
                    pendingGraphHistory = null
                    pendingRecoveryFile = null
                    showGraphRecoveryDialog = false
                },

                title = {
                    Text("Найдена резервная копия")
                },

                text = {

                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {

                        Text(
                            "Для сценария ${recoveryFile.name} найдена " +
                                    "автоматически сохранённая копия графа."
                        )

                        Text(
                            "Время сохранения: ${Date(recovery.savedAtMillis)}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )

                        Text(
                            "Восстановление не перезапишет исходный файл. " +
                                    "После восстановления изменения можно будет " +
                                    "проверить и сохранить обычным способом."
                        )
                    }
                },

                confirmButton = {

                    Button(
                        onClick = {

                            graph = recovery.graph

                            canvasController.resetHistory(
                                recovery.graph
                            )

                            pendingGraphHistory?.let { history ->

                                val restored = canvasController.restoreHistory(
                                    history
                                )

                                if (!restored) {
                                    canvasController.resetHistory(
                                        recovery.graph
                                    )
                                }
                            }

                            graphRevision++

                            mode = CanvasMode.NODES
                            dirty = true

                            pendingGraphRecovery = null
                            pendingGraphHistory = null
                            pendingRecoveryFile = null
                            showGraphRecoveryDialog = false
                        }
                    ) {
                        Text("Восстановить")
                    }
                },

                dismissButton = {

                    TextButton(
                        onClick = {

                            runCatching {
                                graphRecoveryStore.clear(
                                    projectRoot,
                                    recoveryFile
                                )
                            }.onFailure {
                                println(
                                    "ScriptFX Studio: не удалось удалить резервную копию: " +
                                            it.message
                                )
                            }

                            runCatching {
                                graphHistoryStore.clear(
                                    projectRoot,
                                    recoveryFile
                                )
                            }.onFailure {
                                println(
                                    "ScriptFX Studio: не удалось удалить историю графа: " +
                                            it.message
                                )
                            }

                            pendingGraphRecovery = null
                            pendingGraphHistory = null
                            pendingRecoveryFile = null
                            showGraphRecoveryDialog = false
                        }
                    ) {
                        Text("Отбросить")
                    }
                }
            )
        }
    }
}

// =====================================================
// CANVAS TOOLBAR
// =====================================================

@Composable
private fun CanvasToolbar(
    controller: NodeCanvasController,
    historyManager: ProjectHistoryManager,
    dirty: Boolean,
    onSave: () -> Unit,
    modifier: Modifier = Modifier
) {

    val canUndo by historyManager.canUndo
    val canRedo by historyManager.canRedo

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
                    historyManager.undo()
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
                    historyManager.redo()
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

// =====================================================
// PROJECT VALIDATION DIALOG
// =====================================================

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
                Text(
                    "Проверено сценариев: ${report.scenariosChecked}"
                )

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
            TextButton(
                onClick = onDismiss
            ) {
                Text("Закрыть")
            }
        }
    )
}
