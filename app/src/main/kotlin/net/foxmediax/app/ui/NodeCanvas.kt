
@file:OptIn(ExperimentalComposeUiApi::class)

package net.foxmediax.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.foundation.BorderStroke
import net.foxmediax.utils.nodes.GraphEdge
import net.foxmediax.utils.nodes.GraphNode
import net.foxmediax.utils.nodes.NodeGraph
import net.foxmediax.utils.nodes.NodeKind
import java.util.UUID
import kotlin.math.hypot
import kotlin.math.min
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import net.foxmediax.utils.nodes.GraphGroup

private const val NODE_W = 180f
private const val NODE_H = 60f
private const val PORT_R = 7f

private const val MIN_SCALE = 0.3f
private const val MAX_SCALE = 2.5f

private data class NodeClipboard(
    val nodes: List<GraphNode>,
    val edges: List<GraphEdge>,
    val groups: List<GraphGroup> = emptyList()
)

private data class CanvasContextMenu(
    val position: Offset,
    val nodeId: String?,
    val groupId: String? = null
)

private data class MarqueeSelection(
    val start: Offset,
    val end: Offset
)

private val NODE_GROUP_COLORS = listOf(
    0xFF8B72D8L,
    0xFF4C9B88L,
    0xFF547FBDL,
    0xFFBC8950L,
    0xFFB45F78L,
    0xFF7E8C9EL
)

private data class NodeGroupBounds(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val membersTop: Float
)

private fun calculateGroupBounds(
    group: GraphGroup,
    graph: NodeGraph
): NodeGroupBounds? {
    val members = group.nodeIds.mapNotNull { graph.node(it) }

    if (members.isEmpty()) return null

    val minX = members.minOf { it.x }
    val minY = members.minOf { it.y }
    val maxX = members.maxOf { it.x + NODE_W }
    val maxY = members.maxOf { it.y + NODE_H }

    return NodeGroupBounds(
        left = minX - 22f,
        top = minY - 38f,
        right = maxX + 22f,
        bottom = maxY + 20f,
        membersTop = minY
    )
}

private data class MinimapTransform(
    val minX: Float,
    val minY: Float,
    val mapScale: Float,
    val offsetX: Float,
    val offsetY: Float
) {
    fun worldToMap(point: Offset): Offset {
        return Offset(
            offsetX + point.x * mapScale,
            offsetY + point.y * mapScale
        )
    }

    fun mapToWorld(point: Offset): Offset {
        return Offset(
            (point.x - offsetX) / mapScale,
            (point.y - offsetY) / mapScale
        )
    }
}

private fun createMinimapTransform(
    nodes: List<GraphNode>,
    size: Size
): MinimapTransform? {
    if (nodes.isEmpty()) return null
    if (size.width <= 0f || size.height <= 0f) return null

    val padding = 80f

    val minX = nodes.minOf { it.x } - padding
    val minY = nodes.minOf { it.y } - padding

    val maxX = nodes.maxOf { it.x + NODE_W } + padding
    val maxY = nodes.maxOf { it.y + NODE_H } + padding

    val worldWidth = (maxX - minX).coerceAtLeast(1f)
    val worldHeight = (maxY - minY).coerceAtLeast(1f)

    val mapScale = min(
        size.width / worldWidth,
        size.height / worldHeight
    )

    return MinimapTransform(
        minX = minX,
        minY = minY,
        mapScale = mapScale,
        offsetX = (size.width - worldWidth * mapScale) / 2f
                - minX * mapScale,
        offsetY = (size.height - worldHeight * mapScale) / 2f
                - minY * mapScale
    )
}


@Composable
fun NodeCanvas(
    graph: NodeGraph,
    onGraphChanged: (NodeGraph) -> Unit,
    modifier: Modifier = Modifier,
    controller: NodeCanvasController? = null
) {
    val textMeasurer = rememberTextMeasurer()
    val focusRequester = remember { FocusRequester() }

    // Камера холста.
    var panX by remember { mutableStateOf(0f) }
    var panY by remember { mutableStateOf(0f) }
    var scale by remember { mutableStateOf(1f) }

    // Выделение.
    var selectedId by remember { mutableStateOf<String?>(null) }
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    // Буфер копирования.
    var clipboard by remember { mutableStateOf<NodeClipboard?>(null) }
    var pasteCount by remember { mutableIntStateOf(0) }

    // Контекстное меню.
    var contextMenu by remember {
        mutableStateOf<CanvasContextMenu?>(null)
    }

    // Рамочное выделение.
    var marquee by remember {
        mutableStateOf<MarqueeSelection?>(null)
    }

    // Создание связей.
    var linkingFrom by remember { mutableStateOf<String?>(null) }
    var linkingPort by remember { mutableStateOf("out") }

    var linkX by remember { mutableStateOf(0f) }
    var linkY by remember { mutableStateOf(0f) }

    var version by remember { mutableIntStateOf(0) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    var minimapCanvasSize by remember {
        mutableStateOf(IntSize.Zero)
    }

    val latestScale = rememberUpdatedState(scale)
    val latestCanvasSize = rememberUpdatedState(canvasSize)

    // Поиск узлов.
    var searchQuery by remember { mutableStateOf("") }
    var searchExpanded by remember { mutableStateOf(false) }
    var searchSelectedIndex by remember { mutableIntStateOf(0) }

    val searchFocusRequester = remember {
        FocusRequester()
    }

    // Создание и редактирование групп.
    var showGroupDialog by remember {
        mutableStateOf(false)
    }

    var editingGroupId by remember {
        mutableStateOf<String?>(null)
    }

    var groupNameInput by remember {
        mutableStateOf("")
    }

    var groupColorIndex by remember {
        mutableIntStateOf(0)
    }

    fun screenToWorld(position: Offset) = Offset(
        (position.x - panX) / scale,
        (position.y - panY) / scale
    )

    fun hitNode(world: Offset): GraphNode? =
        graph.nodes.asReversed().firstOrNull { node ->
            world.x >= node.x &&
                    world.x <= node.x + NODE_W &&
                    world.y >= node.y &&
                    world.y <= node.y + NODE_H
        }

    fun outputPortWorld(node: GraphNode, port: String): Offset {
        val localY = when {
            node.kind == NodeKind.IF && port == "true" ->
                NODE_H * 0.30f

            node.kind == NodeKind.IF && port == "next" ->
                NODE_H * 0.70f

            else -> NODE_H / 2f
        }

        return Offset(node.x + NODE_W, node.y + localY)
    }

    fun hitOutPort(world: Offset): Pair<GraphNode, String>? {
        return graph.nodes.asReversed().firstNotNullOfOrNull { node ->
            val ports = if (node.kind == NodeKind.IF) {
                listOf("true", "next")
            } else {
                listOf("out")
            }

            ports.firstNotNullOfOrNull { port ->
                val point = outputPortWorld(node, port)
                val distance = hypot(world.x - point.x, world.y - point.y)

                if (distance <= PORT_R * 2.5f) node to port else null
            }
        }
    }

    fun hitInPort(world: Offset): GraphNode? =
        graph.nodes.asReversed().firstOrNull { node ->
            val point = Offset(node.x, node.y + NODE_H / 2f)
            hypot(world.x - point.x, world.y - point.y) <= PORT_R * 2.5f
        }

    // Обновление множественного выделения.
    fun updateSelection(ids: Set<String>) {
        val validIds = ids
            .filter { graph.node(it) != null }
            .toCollection(linkedSetOf())

        selectedIds = validIds
        selectedId = validIds.lastOrNull()

        controller?.selectedNodeId?.value = selectedId

        version++
    }


    fun hitGroupHeader(world: Offset): GraphGroup? {
        return graph.groups.asReversed().firstOrNull { group ->
            val bounds = calculateGroupBounds(group, graph)
                ?: return@firstOrNull false

            world.x >= bounds.left &&
                    world.x <= bounds.right &&
                    world.y >= bounds.top &&
                    world.y <= bounds.membersTop - 6f
        }
    }

    fun openGroupDialog(group: GraphGroup? = null) {
        editingGroupId = group?.id
        groupNameInput = group?.title ?: "Новая группа"

        groupColorIndex = if (group == null) {
            0
        } else {
            NODE_GROUP_COLORS.indexOf(group.colorArgb)
                .coerceAtLeast(0)
        }

        showGroupDialog = true
    }

    fun saveGroupDialog() {
        val title = groupNameInput.trim()

        if (title.isBlank()) return

        val selectedColor = NODE_GROUP_COLORS[groupColorIndex]

        if (editingGroupId == null) {
            // Создание новой группы.
            val memberIds = graph.nodes
                .filter { it.id in selectedIds }
                .map { it.id }

            if (memberIds.size < 2) {
                showGroupDialog = false
                return
            }

            controller?.checkpoint(graph)

            val memberSet = memberIds.toSet()

            // Один узел не должен одновременно принадлежать
            // нескольким группам. Переносим его в новую группу.
            graph.groups.forEach { oldGroup ->
                oldGroup.nodeIds.removeAll { it in memberSet }
            }

            graph.groups.removeAll {
                it.nodeIds.size < 2
            }

            graph.groups.add(
                GraphGroup(
                    id = "group_${UUID.randomUUID()}",
                    title = title,
                    nodeIds = memberIds.toMutableList(),
                    colorArgb = selectedColor
                )
            )

            updateSelection(memberSet)
        } else {
            // Редактирование существующей группы.
            val group = graph.groups.firstOrNull {
                it.id == editingGroupId
            }

            if (group != null) {
                controller?.checkpoint(graph)

                group.title = title
                group.colorArgb = selectedColor
            }
        }

        showGroupDialog = false

        version++
        onGraphChanged(graph)
    }

    fun removeGroup(groupId: String) {
        val group = graph.groups.firstOrNull {
            it.id == groupId
        } ?: return

        controller?.checkpoint(graph)

        // Удаляется только группа, сами узлы остаются.
        graph.groups.remove(group)

        version++
        onGraphChanged(graph)
    }


    // Результаты поиска пересчитываются при изменении графа
// или поискового запроса.
    val searchResults = if (searchQuery.isBlank()) {
        emptyList()
    } else {
        graph.nodes.filter { node ->
            val searchableText = buildString {
                append(node.title)
                append(" ")
                append(node.kind.name)
                append(" ")
                append(node.id)

                node.params.forEach { (key, value) ->
                    append(" ")
                    append(key)
                    append(" ")
                    append(value)
                }
            }

            searchableText.contains(
                searchQuery.trim(),
                ignoreCase = true
            )
        }
    }

    // Переход к найденному узлу.
    fun focusSearchResult(node: GraphNode) {
        val centerX = canvasSize.width / 2f
        val centerY = canvasSize.height / 2f

        panX = centerX - (node.x + NODE_W / 2f) * scale
        panY = centerY - (node.y + NODE_H / 2f) * scale

        // Выделяем найденный узел.
        updateSelection(setOf(node.id))

        version++
        searchExpanded = false
    }

    // Снимок выделения для буфера.
    // START и END не копируются.
    fun captureSelection(): NodeClipboard? {
        val copiedNodes = graph.nodes
            .filter {
                it.id in selectedIds &&
                        it.kind != NodeKind.START &&
                        it.kind != NodeKind.END
            }
            .map { node ->
                node.copy(params = node.params.toMap())
            }

        if (copiedNodes.isEmpty()) return null

        val copiedIds = copiedNodes.map { it.id }.toSet()

        val copiedEdges = graph.edges.filter { edge ->
            edge.from in copiedIds && edge.to in copiedIds
        }

        val copiedGroups = graph.groups.mapNotNull { group ->
            val members = group.nodeIds.filter {
                it in copiedIds
            }

            if (members.size < 2) {
                null
            } else {
                group.copy(
                    nodeIds = members.toMutableList()
                )
            }
        }

        return NodeClipboard(
            nodes = copiedNodes,
            edges = copiedEdges,
            groups = copiedGroups
        )
    }

    fun copySelection() {
        val snapshot = captureSelection() ?: return

        clipboard = snapshot
        pasteCount = 0
    }

    // Вставка узлов с новыми ID.
    fun pasteClipboard() {
        val snapshot = clipboard ?: return
        if (snapshot.nodes.isEmpty()) return

        controller?.checkpoint(graph)

        val idMap = snapshot.nodes.associate { node ->
            node.id to "node_${UUID.randomUUID()}"
        }

        val offset = 32f * (pasteCount + 1)

        val newNodes = snapshot.nodes.map { original ->
            original.copy(
                id = idMap.getValue(original.id),
                x = original.x + offset,
                y = original.y + offset,
                params = original.params.toMap()
            )
        }

        // Копируются только связи внутри скопированной группы.
        val newEdges = snapshot.edges.mapNotNull { edge ->
            val newFrom = idMap[edge.from] ?: return@mapNotNull null
            val newTo = idMap[edge.to] ?: return@mapNotNull null

            edge.copy(
                from = newFrom,
                to = newTo
            )
        }

        val newGroups = snapshot.groups.mapNotNull { original ->
            val mappedIds = original.nodeIds.mapNotNull { oldId ->
                idMap[oldId]
            }

            if (mappedIds.size < 2) {
                null
            } else {
                GraphGroup(
                    id = "group_${UUID.randomUUID()}",
                    title = original.title,
                    nodeIds = mappedIds.toMutableList(),
                    colorArgb = original.colorArgb
                )
            }
        }

        graph.nodes.addAll(newNodes)
        graph.edges.addAll(newEdges)
        graph.groups.addAll(newGroups)

        pasteCount++

        updateSelection(newNodes.map { it.id }.toSet())

        version++
        onGraphChanged(graph)
    }

    fun duplicateSelection() {
        val snapshot = captureSelection() ?: return

        clipboard = snapshot
        pasteCount = 0

        pasteClipboard()
    }

    // Удаление всей группы выделенных узлов.
    fun deleteSelection(): Boolean {
        if (selectedIds.isEmpty()) return false

        val deletableIds = selectedIds.filter { id ->
            val node = graph.node(id)

            node != null &&
                    node.kind != NodeKind.START &&
                    node.kind != NodeKind.END
        }.toSet()

        if (deletableIds.isEmpty()) return true

        controller?.checkpoint(graph)

        graph.edges.removeAll { edge ->
            edge.from in deletableIds || edge.to in deletableIds
        }

        graph.nodes.removeAll { node ->
            node.id in deletableIds
        }

        // Удаляем удалённые узлы из групп.
        graph.groups.forEach { group ->
            group.nodeIds.removeAll {
                it in deletableIds
            }
        }

        // Группы с менее чем двумя участниками больше не нужны.
        graph.groups.removeAll {
            it.nodeIds.size < 2
        }

        updateSelection(selectedIds - deletableIds)

        version++
        onGraphChanged(graph)

        return true
    }

    fun zoomAtCenter(factor: Float) {
        val center = Offset(
            canvasSize.width / 2f,
            canvasSize.height / 2f
        )

        val oldScale = scale

        scale = (scale * factor).coerceIn(MIN_SCALE, MAX_SCALE)

        panX = center.x - (center.x - panX) * (scale / oldScale)
        panY = center.y - (center.y - panY) * (scale / oldScale)

        version++
    }

    fun fitGraph() {
        if (canvasSize.width <= 0 || canvasSize.height <= 0) return

        if (graph.nodes.isEmpty()) {
            panX = 0f
            panY = 0f
            scale = 1f
            version++
            return
        }

        val minX = graph.nodes.minOf { it.x }
        val minY = graph.nodes.minOf { it.y }
        val maxX = graph.nodes.maxOf { it.x + NODE_W }
        val maxY = graph.nodes.maxOf { it.y + NODE_H }

        val graphWidth = (maxX - minX).coerceAtLeast(NODE_W)
        val graphHeight = (maxY - minY).coerceAtLeast(NODE_H)

        val padding = 64f

        val availableWidth =
            (canvasSize.width - padding * 2).coerceAtLeast(1f)

        val availableHeight =
            (canvasSize.height - padding * 2).coerceAtLeast(1f)

        scale = min(
            availableWidth / graphWidth,
            availableHeight / graphHeight
        ).coerceIn(MIN_SCALE, MAX_SCALE)

        panX =
            (canvasSize.width - graphWidth * scale) / 2f - minX * scale

        panY =
            (canvasSize.height - graphHeight * scale) / 2f - minY * scale

        version++
    }


    fun autoLayout() {
        if (graph.nodes.isEmpty()) return

        val outgoing = graph.edges.groupBy { it.from }

        // Считаем количество входящих связей.
        val indegree = graph.nodes.associate { node ->
            node.id to graph.edges.count { it.to == node.id }
        }.toMutableMap()

        val levels = mutableMapOf<String, Int>()
        val queue = java.util.ArrayDeque<String>()
        val processed = mutableSetOf<String>()

        // Начинаем с узлов, у которых нет входящих связей.
        graph.nodes.forEach { node ->
            if ((indegree[node.id] ?: 0) == 0) {
                queue.addLast(node.id)
                levels[node.id] = 0
            }
        }

        // Топологическая сортировка.
        while (queue.isNotEmpty()) {
            val currentId = queue.removeFirst()

            if (!processed.add(currentId)) continue

            val currentLevel = levels[currentId] ?: 0

            outgoing[currentId].orEmpty().forEach { edge ->
                levels[edge.to] = maxOf(
                    levels[edge.to] ?: 0,
                    currentLevel + 1
                )

                val remaining = (indegree[edge.to] ?: 0) - 1
                indegree[edge.to] = remaining

                if (remaining == 0) {
                    queue.addLast(edge.to)
                }
            }
        }

        // Циклические и оставшиеся недостижимые узлы.
        // Размещаем их в дополнительных колонках.
        val remainingNodes = graph.nodes.filter {
            it.id !in processed
        }

        if (remainingNodes.isNotEmpty()) {
            val fallbackLevel =
                (levels.values.maxOrNull() ?: -1) + 1

            remainingNodes.forEachIndexed { index, node ->
                levels[node.id] = fallbackLevel + index / 8
            }
        }

        // Группируем узлы по колонкам.
        val columns = graph.nodes.groupBy {
            levels[it.id] ?: 0
        }.toSortedMap()

        val columnSpacing = 280f
        val rowSpacing = 100f

        val newPositions = mutableMapOf<String, Offset>()

        columns.forEach { (column, nodes) ->
            // Стараемся сохранить относительный вертикальный порядок.
            val sortedNodes = nodes.sortedBy { it.y }

            sortedNodes.forEachIndexed { row, node ->
                newPositions[node.id] = Offset(
                    x = 80f + column * columnSpacing,
                    y = 80f + row * rowSpacing
                )
            }
        }

        val changed = graph.nodes.any { node ->
            val position = newPositions[node.id] ?: return@any false

            node.x != position.x || node.y != position.y
        }

        if (!changed) {
            fitGraph()
            return
        }

        // Одно действие в Undo/Redo.
        controller?.checkpoint(graph)

        graph.nodes.forEach { node ->
            val position = newPositions[node.id] ?: return@forEach

            node.x = position.x
            node.y = position.y
        }

        version++

        onGraphChanged(graph)

        // Автоматически показываем весь результат.
        fitGraph()
    }


    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    // Первичная подгонка масштаба только один раз.
    var initialFitDone by remember { mutableStateOf(false) }

    LaunchedEffect(canvasSize) {
        if (
            !initialFitDone &&
            canvasSize.width > 0 &&
            canvasSize.height > 0
        ) {
            fitGraph()
            initialFitDone = true
        }
    }

    // Обновление действий контроллера.
    SideEffect {
        controller?.let { canvasController ->

            canvasController.addNodeAtCenterAction = { node ->
                canvasController.checkpoint(graph)

                val center = Offset(
                    canvasSize.width / 2f,
                    canvasSize.height / 2f
                )

                val world = screenToWorld(center)

                node.x = world.x - NODE_W / 2f
                node.y = world.y - NODE_H / 2f

                graph.nodes.add(node)

                updateSelection(setOf(node.id))

                version++
                onGraphChanged(graph)
            }

            canvasController.fitGraphAction = {
                fitGraph()
            }

            canvasController.zoomInAction = {
                zoomAtCenter(1.15f)
            }

            canvasController.zoomOutAction = {
                zoomAtCenter(0.87f)
            }

            canvasController.focusNodeAction = { nodeId ->
                val target = graph.node(nodeId)

                if (target != null) {
                    selectedIds = setOf(nodeId)
                    selectedId = nodeId

                    canvasController.selectedNodeId.value = nodeId

                    panX = canvasSize.width / 2f -
                            (target.x + NODE_W / 2f) * scale

                    panY = canvasSize.height / 2f -
                            (target.y + NODE_H / 2f) * scale

                    version++

                    focusRequester.requestFocus()
                }
            }

            canvasController.undoAction = {
                val restored = canvasController.undoGraph(graph)

                if (restored != null) {
                    selectedIds = selectedIds
                        .filter { restored.node(it) != null }
                        .toCollection(linkedSetOf())

                    selectedId = selectedIds.lastOrNull()
                    canvasController.selectedNodeId.value = selectedId

                    version++
                    onGraphChanged(restored)
                }
            }

            canvasController.redoAction = {
                val restored = canvasController.redoGraph(graph)

                if (restored != null) {
                    selectedIds = selectedIds
                        .filter { restored.node(it) != null }
                        .toCollection(linkedSetOf())

                    selectedId = selectedIds.lastOrNull()
                    canvasController.selectedNodeId.value = selectedId

                    version++
                    onGraphChanged(restored)
                }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
            .background(Color(0xFF121218))
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged {
                    canvasSize = it
                }
                .focusRequester(focusRequester)
                .focusable()
                .onPreviewKeyEvent { event ->

                    if (event.type != KeyEventType.KeyDown) {
                        return@onPreviewKeyEvent false
                    }

                    if (event.isCtrlPressed) {
                        when {

                            event.key == Key.F -> {
                                searchExpanded = true
                                searchFocusRequester.requestFocus()
                                return@onPreviewKeyEvent true
                            }

                            event.key == Key.Z && event.isShiftPressed -> {
                                controller?.redo()
                                return@onPreviewKeyEvent true
                            }

                            event.key == Key.Z -> {
                                controller?.undo()
                                return@onPreviewKeyEvent true
                            }

                            event.key == Key.Y -> {
                                controller?.redo()
                                return@onPreviewKeyEvent true
                            }

                            event.key == Key.C -> {
                                copySelection()
                                return@onPreviewKeyEvent true
                            }

                            event.key == Key.V -> {
                                pasteClipboard()
                                return@onPreviewKeyEvent true
                            }

                            event.key == Key.D -> {
                                duplicateSelection()
                                return@onPreviewKeyEvent true
                            }

                            event.key == Key.A -> {
                                updateSelection(
                                    graph.nodes.map { it.id }.toSet()
                                )
                                return@onPreviewKeyEvent true
                            }
                        }
                    }

                    if (
                        event.key == Key.Delete ||
                        event.key == Key.Backspace
                    ) {
                        return@onPreviewKeyEvent deleteSelection()
                    }

                    false
                }
                .onPointerEvent(PointerEventType.Scroll) { event ->
                    val change = event.changes.firstOrNull()
                        ?: return@onPointerEvent

                    val dy = change.scrollDelta.y
                    if (dy == 0f) return@onPointerEvent

                    val position = change.position
                    val oldScale = scale

                    scale = (
                            scale * if (dy > 0f) 0.9f else 1.1f
                            ).coerceIn(MIN_SCALE, MAX_SCALE)

                    panX =
                        position.x - (position.x - panX) * (scale / oldScale)

                    panY =
                        position.y - (position.y - panY) * (scale / oldScale)

                    version++
                    change.consume()
                }
                .pointerInput(graph, controller) {
                    awaitEachGesture {

                        val downEvent = awaitPointerEvent(
                            PointerEventPass.Initial
                        )

                        val downChange = downEvent.changes.firstOrNull()
                            ?: return@awaitEachGesture

                        if (!downChange.pressed) {
                            return@awaitEachGesture
                        }

                        focusRequester.requestFocus()

                        val buttons = downEvent.buttons
                        val startWorld = screenToWorld(downChange.position)

                        // СРЕДНЯЯ КНОПКА — перемещение холста.
                        if (buttons.isTertiaryPressed) {
                            var lastPosition = downChange.position

                            while (true) {
                                val event = awaitPointerEvent()

                                val change = event.changes.firstOrNull {
                                    it.id == downChange.id
                                } ?: break

                                if (change.changedToUp()) {
                                    change.consume()
                                    break
                                }

                                val delta = change.position - lastPosition

                                panX += delta.x
                                panY += delta.y

                                lastPosition = change.position

                                version++
                                change.consume()
                            }

                            return@awaitEachGesture
                        }

                        // ПРАВАЯ КНОПКА — контекстное меню.
                        if (buttons.isSecondaryPressed) {
                            val targetGroup = hitGroupHeader(startWorld)
                            val target = if (targetGroup == null) {
                                hitNode(startWorld)
                            } else {
                                null
                            }

                            if (target != null) {
                                if (target.id !in selectedIds) {
                                    updateSelection(setOf(target.id))
                                } else {
                                    selectedId = target.id
                                    controller?.selectedNodeId?.value = target.id
                                    version++
                                }
                            }

                            contextMenu = CanvasContextMenu(
                                position = downChange.position,
                                nodeId = target?.id,
                                groupId = targetGroup?.id
                            )

                            downChange.consume()
                            return@awaitEachGesture
                        }


                        // Начало создания связи.
                        val outputHit = hitOutPort(startWorld)


// Перетаскивание за заголовок группы.
                        val groupHeaderHit = hitGroupHeader(startWorld)

                        if (groupHeaderHit != null) {
                            val movingNodes = groupHeaderHit.nodeIds.mapNotNull {
                                graph.node(it)
                            }

                            if (movingNodes.isNotEmpty()) {
                                updateSelection(
                                    movingNodes.map { it.id }.toSet()
                                )

                                val originalPositions = movingNodes.associate { node ->
                                    node.id to Offset(node.x, node.y)
                                }

                                var movementRecorded = false

                                while (true) {
                                    val event = awaitPointerEvent()

                                    val change = event.changes.firstOrNull {
                                        it.id == downChange.id
                                    } ?: break

                                    val world = screenToWorld(change.position)
                                    val delta = world - startWorld

                                    if (delta.x != 0f || delta.y != 0f) {
                                        if (!movementRecorded) {
                                            controller?.checkpoint(graph)
                                            movementRecorded = true
                                        }

                                        movingNodes.forEach { node ->
                                            val original =
                                                originalPositions[node.id] ?: return@forEach

                                            node.x = original.x + delta.x
                                            node.y = original.y + delta.y
                                        }

                                        version++
                                    }

                                    if (change.changedToUp()) {
                                        if (movementRecorded) {
                                            onGraphChanged(graph)
                                        }

                                        change.consume()
                                        break
                                    }

                                    change.consume()
                                }
                            }

                            return@awaitEachGesture
                        }


                        if (outputHit != null) {
                            val sourceNode = outputHit.first
                            val sourcePort = outputHit.second

                            linkingFrom = sourceNode.id
                            linkingPort = sourcePort

                            linkX = startWorld.x
                            linkY = startWorld.y

                            while (true) {
                                val event = awaitPointerEvent()

                                val change = event.changes.firstOrNull {
                                    it.id == downChange.id
                                } ?: break

                                val world = screenToWorld(change.position)

                                linkX = world.x
                                linkY = world.y

                                if (change.changedToUp()) {
                                    val target = hitInPort(world)

                                    if (
                                        target != null &&
                                        target.id != sourceNode.id
                                    ) {
                                        val exists = graph.edges.any {
                                            it.from == sourceNode.id &&
                                                    it.fromPort == sourcePort &&
                                                    it.to == target.id &&
                                                    it.toPort == "in"
                                        }

                                        if (!exists) {
                                            controller?.checkpoint(graph)

                                            graph.edges.removeAll {
                                                it.from == sourceNode.id &&
                                                        it.fromPort == sourcePort
                                            }

                                            graph.edges.add(
                                                GraphEdge(
                                                    from = sourceNode.id,
                                                    fromPort = sourcePort,
                                                    to = target.id,
                                                    toPort = "in"
                                                )
                                            )

                                            version++
                                            onGraphChanged(graph)
                                        }
                                    }

                                    linkingFrom = null

                                    change.consume()
                                    break
                                }

                                version++
                                change.consume()
                            }

                            return@awaitEachGesture
                        }

                        // ЛЕВАЯ КНОПКА — выбор узла или рамочное выделение.
                        val hit = hitNode(startWorld)

                        // Рамочное выделение начинается на пустом месте.
                        if (hit == null) {
                            val additive =
                                downEvent.keyboardModifiers.isShiftPressed ||
                                        downEvent.keyboardModifiers.isCtrlPressed

                            val baseSelection = if (additive) {
                                selectedIds
                            } else {
                                emptySet()
                            }

                            var movedEnough = false
                            val startPosition = downChange.position

                            marquee = MarqueeSelection(
                                start = startPosition,
                                end = startPosition
                            )

                            while (true) {
                                val event = awaitPointerEvent()

                                val change = event.changes.firstOrNull {
                                    it.id == downChange.id
                                } ?: break

                                val currentPosition = change.position

                                val delta = currentPosition - startPosition

                                if (hypot(delta.x, delta.y) > 4f) {
                                    movedEnough = true
                                }

                                marquee = MarqueeSelection(
                                    start = startPosition,
                                    end = currentPosition
                                )

                                if (change.changedToUp()) {
                                    if (movedEnough) {
                                        val left = min(
                                            startPosition.x,
                                            currentPosition.x
                                        )

                                        val right = maxOf(
                                            startPosition.x,
                                            currentPosition.x
                                        )

                                        val top = min(
                                            startPosition.y,
                                            currentPosition.y
                                        )

                                        val bottom = maxOf(
                                            startPosition.y,
                                            currentPosition.y
                                        )

                                        val intersectedIds = graph.nodes
                                            .filter { node ->
                                                val nodeLeft =
                                                    panX + node.x * scale

                                                val nodeTop =
                                                    panY + node.y * scale

                                                val nodeRight =
                                                    nodeLeft + NODE_W * scale

                                                val nodeBottom =
                                                    nodeTop + NODE_H * scale

                                                nodeLeft <= right &&
                                                        nodeRight >= left &&
                                                        nodeTop <= bottom &&
                                                        nodeBottom >= top
                                            }
                                            .map { it.id }
                                            .toSet()

                                        updateSelection(
                                            baseSelection + intersectedIds
                                        )
                                    } else if (!additive) {
                                        updateSelection(emptySet())
                                    }

                                    marquee = null

                                    change.consume()
                                    break
                                }

                                change.consume()
                            }

                            return@awaitEachGesture
                        }

                        // Выбор отдельного узла.
                        val additiveSelection =
                            downEvent.keyboardModifiers.isCtrlPressed ||
                                    downEvent.keyboardModifiers.isShiftPressed

                        // Если узел входит в группу, выбираем всю группу.
                        val nodeGroup = graph.groups.firstOrNull {
                            hit.id in it.nodeIds
                        }

                        val hitIds = nodeGroup?.nodeIds?.toSet()
                            ?: setOf(hit.id)

                        if (additiveSelection) {
                            val allAlreadySelected = hitIds.all {
                                it in selectedIds
                            }

                            if (allAlreadySelected) {
                                updateSelection(selectedIds - hitIds)
                            } else {
                                updateSelection(selectedIds + hitIds)
                            }
                        } else {
                            if (selectedIds != hitIds) {
                                updateSelection(hitIds)
                            } else {
                                selectedId = hit.id
                                controller?.selectedNodeId?.value = hit.id
                                version++
                            }
                        }

                        // Если узел исключён из выделения,
                        // перемещение не запускается.
                        if (hit.id !in selectedIds) {
                            downChange.consume()
                            return@awaitEachGesture
                        }

                        // Перемещение всей группы.
                        val movingNodes = selectedIds.mapNotNull {
                            graph.node(it)
                        }

                        val originalPositions = movingNodes.associate { node ->
                            node.id to Offset(node.x, node.y)
                        }

                        var movementRecorded = false

                        while (true) {
                            val event = awaitPointerEvent()

                            val change = event.changes.firstOrNull {
                                it.id == downChange.id
                            } ?: break

                            val world = screenToWorld(change.position)
                            val delta = world - startWorld

                            val moved = delta.x != 0f || delta.y != 0f

                            if (moved) {
                                if (!movementRecorded) {
                                    controller?.checkpoint(graph)
                                    movementRecorded = true
                                }

                                movingNodes.forEach { node ->
                                    val original =
                                        originalPositions[node.id]
                                            ?: return@forEach

                                    node.x = original.x + delta.x
                                    node.y = original.y + delta.y
                                }

                                version++
                            }

                            if (change.changedToUp()) {
                                if (movementRecorded) {
                                    onGraphChanged(graph)
                                }

                                change.consume()
                                break
                            }

                            change.consume()
                        }
                    }
                }
        ) {
            val drawVersion = version
            if (drawVersion == Int.MIN_VALUE) return@Canvas

            drawGrid(
                panX = panX,
                panY = panY,
                scale = scale
            )


// Группы отрисовываем позади связей и узлов.
            for (group in graph.groups) {
                val bounds = calculateGroupBounds(group, graph)
                    ?: continue

                val groupColor = Color(group.colorArgb.toULong())

                val topLeft = Offset(
                    panX + bounds.left * scale,
                    panY + bounds.top * scale
                )

                val groupWidth =
                    (bounds.right - bounds.left) * scale

                val groupHeight =
                    (bounds.bottom - bounds.top) * scale

                drawRoundRect(
                    color = groupColor.copy(alpha = 0.09f),
                    topLeft = topLeft,
                    size = Size(groupWidth, groupHeight),
                    cornerRadius = CornerRadius(
                        10f * scale,
                        10f * scale
                    )
                )

                drawRoundRect(
                    color = groupColor.copy(alpha = 0.85f),
                    topLeft = topLeft,
                    size = Size(groupWidth, groupHeight),
                    cornerRadius = CornerRadius(
                        10f * scale,
                        10f * scale
                    ),
                    style = Stroke(
                        width = 1.5f * scale
                    )
                )

                drawTextSafely(
                    textMeasurer = textMeasurer,
                    text = group.title,
                    topLeft = Offset(
                        topLeft.x + 9f * scale,
                        topLeft.y + 6f * scale
                    ),
                    requestedSize = Size(
                        (groupWidth - 18f * scale).coerceAtLeast(1f),
                        18f * scale
                    ),
                    style = TextStyle(
                        color = groupColor,
                        fontSize = (11f * scale.coerceAtMost(1.4f)).sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }


            // Связи.
            for (edge in graph.edges) {
                val sourceNode = graph.node(edge.from) ?: continue
                val targetNode = graph.node(edge.to) ?: continue

                val source = outputPortWorld(
                    sourceNode,
                    edge.fromPort
                )

                val start = Offset(
                    panX + source.x * scale,
                    panY + source.y * scale
                )

                val end = Offset(
                    panX + targetNode.x * scale,
                    panY + (targetNode.y + NODE_H / 2f) * scale
                )

                drawBezier(
                    start,
                    end,
                    Color(0xFF8B7EC8),
                    2.5f * scale
                )
            }

            // Создаваемая связь.
            linkingFrom?.let { id ->
                val sourceNode = graph.node(id)

                if (sourceNode != null) {
                    val source = outputPortWorld(
                        sourceNode,
                        linkingPort
                    )

                    val start = Offset(
                        panX + source.x * scale,
                        panY + source.y * scale
                    )

                    val end = Offset(
                        panX + linkX * scale,
                        panY + linkY * scale
                    )

                    drawBezier(
                        start,
                        end,
                        Color(0xFFC8B4FF),
                        2.5f * scale
                    )
                }
            }

            // Узлы.
            for (node in graph.nodes) {
                drawNode(
                    node = node,
                    selected = node.id in selectedIds,
                    textMeasurer = textMeasurer,
                    panX = panX,
                    panY = panY,
                    scale = scale
                )
            }

            // Рамка выделения поверх узлов.
            marquee?.let { selection ->
                val left = min(
                    selection.start.x,
                    selection.end.x
                )

                val top = min(
                    selection.start.y,
                    selection.end.y
                )

                val width = kotlin.math.abs(
                    selection.end.x - selection.start.x
                )

                val height = kotlin.math.abs(
                    selection.end.y - selection.start.y
                )

                val rectTopLeft = Offset(left, top)
                val rectSize = Size(width, height)

                drawRect(
                    color = Color(0x338B7EC8),
                    topLeft = rectTopLeft,
                    size = rectSize
                )

                drawRect(
                    color = Color(0xFFC8B4FF),
                    topLeft = rectTopLeft,
                    size = rectSize,
                    style = Stroke(width = 1.3f)
                )
            }
        }


// -----------------------------------------------------
// NODE SEARCH
// -----------------------------------------------------

        Surface(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp)
                .width(280.dp),
            color = Color(0xF51B1A24),
            contentColor = Color(0xFFE8E8F0),
            shape = RoundedCornerShape(9.dp),
            border = BorderStroke(
                1.dp,
                Color(0xFF49465C)
            ),
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.padding(8.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = {
                        searchQuery = it
                        searchSelectedIndex = 0
                        searchExpanded = true
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(searchFocusRequester)
                        .onPreviewKeyEvent { event ->
                            if (event.type != KeyEventType.KeyDown) {
                                return@onPreviewKeyEvent false
                            }

                            when (event.key) {
                                Key.DirectionDown -> {
                                    if (searchResults.isNotEmpty()) {
                                        searchSelectedIndex =
                                            (searchSelectedIndex + 1)
                                                .coerceAtMost(searchResults.lastIndex)
                                    }

                                    true
                                }

                                Key.DirectionUp -> {
                                    if (searchResults.isNotEmpty()) {
                                        searchSelectedIndex =
                                            (searchSelectedIndex - 1)
                                                .coerceAtLeast(0)
                                    }

                                    true
                                }

                                Key.Enter -> {
                                    val node = searchResults.getOrNull(
                                        searchSelectedIndex
                                    )

                                    if (node != null) {
                                        focusSearchResult(node)
                                    }

                                    true
                                }

                                Key.Escape -> {
                                    searchExpanded = false
                                    true
                                }

                                else -> false
                            }
                        },
                    singleLine = true,
                    placeholder = {
                        Text(
                            "Найти узел...",
                            color = Color(0xFF858296)
                        )
                    },
                    textStyle = MaterialTheme.typography.bodyMedium
                )

                if (searchExpanded && searchQuery.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))

                    if (searchResults.isEmpty()) {
                        Text(
                            "Ничего не найдено",
                            modifier = Modifier.padding(8.dp),
                            color = Color(0xFF9995AA),
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else {
                        Text(
                            "Найдено: ${searchResults.size}",
                            modifier = Modifier.padding(
                                horizontal = 8.dp,
                                vertical = 4.dp
                            ),
                            color = Color(0xFFAAA4C4),
                            style = MaterialTheme.typography.labelSmall
                        )

                        searchResults.take(8).forEachIndexed { index, node ->
                            DropdownMenuItem(
                                text = {
                                    Column(
                                        verticalArrangement =
                                            Arrangement.spacedBy(2.dp)
                                    ) {
                                        Text(
                                            node.title,
                                            color = Color(0xFFE8E8F0),
                                            maxLines = 1
                                        )

                                        Text(
                                            "${node.kind.name} • ${node.id}",
                                            color = Color(0xFF9995AA),
                                            style = MaterialTheme.typography.labelSmall,
                                            maxLines = 1
                                        )
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        if (index == searchSelectedIndex) {
                                            Color(0xFF38334D)
                                        } else {
                                            Color.Transparent
                                        }
                                    ),
                                onClick = {
                                    focusSearchResult(node)
                                }
                            )
                        }

                        if (searchResults.size > 8) {
                            Text(
                                "Показаны первые 8 результатов. Уточни запрос.",
                                modifier = Modifier.padding(8.dp),
                                color = Color(0xFF9995AA),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }

                        Text(
                            "↑ ↓ — выбор    Enter — перейти",
                            modifier = Modifier.padding(
                                horizontal = 8.dp,
                                vertical = 5.dp
                            ),
                            color = Color(0xFF777487),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }


// -----------------------------------------------------
// MINIMAP
// -----------------------------------------------------

        if (graph.nodes.isNotEmpty()) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp)
                    .width(210.dp)
                    .height(145.dp),
                color = Color(0xF51B1A24),
                shape = RoundedCornerShape(9.dp),
                border = BorderStroke(
                    1.dp,
                    Color(0xFF49465C)
                ),
                shadowElevation = 8.dp
            ) {
                Box(
                    modifier = Modifier.fillMaxSize()
                ) {
                    Text(
                        text = "MINIMAP",
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(
                                start = 10.dp,
                                top = 6.dp
                            ),
                        color = Color(0xFFAAA4C4),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(
                                start = 6.dp,
                                end = 6.dp,
                                top = 24.dp,
                                bottom = 6.dp
                            )
                            .onSizeChanged {
                                minimapCanvasSize = it
                            }
                            .pointerInput(graph, minimapCanvasSize) {
                                if (
                                    minimapCanvasSize.width <= 0 ||
                                    minimapCanvasSize.height <= 0
                                ) {
                                    return@pointerInput
                                }

                                awaitEachGesture {
                                    val down = awaitFirstDown(
                                        requireUnconsumed = false
                                    )

                                    val transform = createMinimapTransform(
                                        graph.nodes,
                                        Size(
                                            minimapCanvasSize.width.toFloat(),
                                            minimapCanvasSize.height.toFloat()
                                        )
                                    ) ?: return@awaitEachGesture

                                    fun moveCamera(position: Offset) {
                                        val world = transform.mapToWorld(position)

                                        val currentZoom =
                                            latestScale.value.coerceAtLeast(0.0001f)

                                        val mainSize = latestCanvasSize.value

                                        panX =
                                            mainSize.width / 2f -
                                                    world.x * currentZoom

                                        panY =
                                            mainSize.height / 2f -
                                                    world.y * currentZoom

                                        version++
                                    }

                                    // Клик сразу перемещает камеру.
                                    moveCamera(down.position)

                                    while (true) {
                                        val event = awaitPointerEvent()

                                        val change = event.changes.firstOrNull {
                                            it.id == down.id
                                        } ?: break

                                        moveCamera(change.position)

                                        if (change.changedToUp()) {
                                            change.consume()
                                            break
                                        }

                                        change.consume()
                                    }
                                }
                            }
                    ) {
                        val transform = createMinimapTransform(
                            graph.nodes,
                            size
                        ) ?: return@Canvas

                        // Связи.
                        for (edge in graph.edges) {
                            val source = graph.node(edge.from) ?: continue
                            val target = graph.node(edge.to) ?: continue

                            val sourceY = when {
                                source.kind == NodeKind.IF &&
                                        edge.fromPort == "true" ->
                                    source.y + NODE_H * 0.30f

                                source.kind == NodeKind.IF &&
                                        edge.fromPort == "next" ->
                                    source.y + NODE_H * 0.70f

                                else ->
                                    source.y + NODE_H / 2f
                            }

                            val start = transform.worldToMap(
                                Offset(
                                    source.x + NODE_W,
                                    sourceY
                                )
                            )

                            val end = transform.worldToMap(
                                Offset(
                                    target.x,
                                    target.y + NODE_H / 2f
                                )
                            )

                            drawLine(
                                color = Color(0xFF8176AA),
                                start = start,
                                end = end,
                                strokeWidth = 1.2f
                            )
                        }

                        // Узлы.
                        for (node in graph.nodes) {
                            val topLeft = transform.worldToMap(
                                Offset(node.x, node.y)
                            )

                            val nodeSize = Size(
                                (NODE_W * transform.mapScale).coerceAtLeast(3f),
                                (NODE_H * transform.mapScale).coerceAtLeast(3f)
                            )

                            val nodeColor = when (node.kind) {
                                NodeKind.START -> Color(0xFF438B61)
                                NodeKind.END -> Color(0xFFAD5555)
                                NodeKind.IF -> Color(0xFF8263A8)
                                NodeKind.WAIT, NodeKind.DELAY ->
                                    Color(0xFFAE9A4C)

                                else -> Color(0xFF77718F)
                            }

                            drawRoundRect(
                                color = nodeColor,
                                topLeft = topLeft,
                                size = nodeSize,
                                cornerRadius = CornerRadius(2f, 2f)
                            )
                        }

                        // Видимая область основного холста.
                        val mainSize = latestCanvasSize.value
                        val currentZoom =
                            latestScale.value.coerceAtLeast(0.0001f)

                        if (
                            mainSize.width > 0 &&
                            mainSize.height > 0
                        ) {
                            val worldLeft = -panX / currentZoom
                            val worldTop = -panY / currentZoom

                            val worldRight =
                                (mainSize.width - panX) / currentZoom

                            val worldBottom =
                                (mainSize.height - panY) / currentZoom

                            val viewportTopLeft =
                                transform.worldToMap(
                                    Offset(worldLeft, worldTop)
                                )

                            val viewportBottomRight =
                                transform.worldToMap(
                                    Offset(worldRight, worldBottom)
                                )

                            val viewportWidth =
                                viewportBottomRight.x - viewportTopLeft.x

                            val viewportHeight =
                                viewportBottomRight.y - viewportTopLeft.y

                            if (
                                viewportWidth > 0f &&
                                viewportHeight > 0f
                            ) {
                                drawRoundRect(
                                    color = Color(0xFFC8B4FF),
                                    topLeft = viewportTopLeft,
                                    size = Size(
                                        viewportWidth,
                                        viewportHeight
                                    ),
                                    cornerRadius = CornerRadius(2f, 2f),
                                    style = Stroke(width = 1.6f)
                                )
                            }
                        }
                    }
                }
            }
        }



// Контекстное меню.
        contextMenu?.let { menu ->

            val hasNodeTarget = menu.nodeId != null

            val targetGroup = menu.groupId?.let { id ->
                graph.groups.firstOrNull { it.id == id }
            }

            val canCopy = selectedIds.any { id ->
                val kind = graph.node(id)?.kind

                kind != null &&
                        kind != NodeKind.START &&
                        kind != NodeKind.END
            }

            val canDelete = canCopy
            val canPaste = clipboard != null

            val canCreateGroup = selectedIds.count {
                graph.node(it) != null
            } >= 2

            Popup(
                alignment = Alignment.TopStart,
                offset = IntOffset(
                    menu.position.x.toInt(),
                    menu.position.y.toInt()
                ),
                onDismissRequest = {
                    contextMenu = null
                    focusRequester.requestFocus()
                },
                properties = PopupProperties(
                    focusable = true
                )
            ) {
                Surface(
                    color = Color(0xFF252431),
                    contentColor = Color(0xFFE8E8F0),
                    shape = RoundedCornerShape(8.dp),
                    shadowElevation = 12.dp,
                    border = BorderStroke(
                        1.dp,
                        Color(0xFF49465C)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .width(205.dp)
                            .padding(vertical = 4.dp)
                    ) {
                        when {
                            targetGroup != null -> {
                                DropdownMenuItem(
                                    text = {
                                        Text("Настроить группу")
                                    },
                                    onClick = {
                                        openGroupDialog(targetGroup)
                                        contextMenu = null
                                    }
                                )

                                DropdownMenuItem(
                                    text = {
                                        Text("Разгруппировать")
                                    },
                                    onClick = {
                                        removeGroup(targetGroup.id)
                                        contextMenu = null
                                        focusRequester.requestFocus()
                                    }
                                )
                            }

                            hasNodeTarget -> {
                                DropdownMenuItem(
                                    text = {
                                        Text("Копировать")
                                    },
                                    enabled = canCopy,
                                    onClick = {
                                        copySelection()
                                        contextMenu = null
                                        focusRequester.requestFocus()
                                    }
                                )

                                DropdownMenuItem(
                                    text = {
                                        Text("Дублировать")
                                    },
                                    enabled = canCopy,
                                    onClick = {
                                        duplicateSelection()
                                        contextMenu = null
                                        focusRequester.requestFocus()
                                    }
                                )

                                DropdownMenuItem(
                                    text = {
                                        Text("Удалить")
                                    },
                                    enabled = canDelete,
                                    onClick = {
                                        deleteSelection()
                                        contextMenu = null
                                        focusRequester.requestFocus()
                                    }
                                )
                            }

                            else -> {
                                DropdownMenuItem(
                                    text = {
                                        Text("Вставить")
                                    },
                                    enabled = canPaste,
                                    onClick = {
                                        pasteClipboard()
                                        contextMenu = null
                                        focusRequester.requestFocus()
                                    }
                                )

                                DropdownMenuItem(
                                    text = {
                                        Text("Выбрать все")
                                    },
                                    enabled = graph.nodes.isNotEmpty(),
                                    onClick = {
                                        updateSelection(
                                            graph.nodes.map { it.id }.toSet()
                                        )

                                        contextMenu = null
                                        focusRequester.requestFocus()
                                    }
                                )

                                DropdownMenuItem(
                                    text = {
                                        Text("Автоматическая раскладка")
                                    },
                                    onClick = {
                                        autoLayout()
                                        contextMenu = null
                                        focusRequester.requestFocus()
                                    }
                                )

                                DropdownMenuItem(
                                    text = {
                                        Text("Вписать граф")
                                    },
                                    onClick = {
                                        fitGraph()
                                        contextMenu = null
                                        focusRequester.requestFocus()
                                    }
                                )
                            }
                        }

                        // Этот пункт доступен и при правом клике
                        // по узлу, и при правом клике по пустому месту.
                        if (menu.groupId == null && canCreateGroup) {
                            DropdownMenuItem(
                                text = {
                                    Text("Сгруппировать выделенное")
                                },
                                onClick = {
                                    openGroupDialog()
                                    contextMenu = null
                                }
                            )
                        }
                    }
                }
            }
        }

        // -----------------------------------------------------
        // NODE GROUP DIALOG
        // Диалог находится вне Popup контекстного меню,
        // поэтому не исчезает при закрытии самого меню.
        // -----------------------------------------------------

        if (showGroupDialog) {
            AlertDialog(
                onDismissRequest = {
                    showGroupDialog = false
                    focusRequester.requestFocus()
                },
                title = {
                    Text(
                        if (editingGroupId == null) {
                            "Создать группу"
                        } else {
                            "Настроить группу"
                        }
                    )
                },
                text = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        OutlinedTextField(
                            value = groupNameInput,
                            onValueChange = {
                                groupNameInput = it
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = {
                                Text("Название группы")
                            },
                            singleLine = true
                        )

                        Text(
                            "Цвет группы",
                            style = MaterialTheme.typography.labelMedium
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(9.dp)
                        ) {
                            NODE_GROUP_COLORS.forEachIndexed { index, argb ->
                                Box(
                                    modifier = Modifier
                                        .size(29.dp)
                                        .background(
                                            Color(argb.toULong()),
                                            CircleShape
                                        )
                                        .then(
                                            if (groupColorIndex == index) {
                                                Modifier.border(
                                                    width = 3.dp,
                                                    color = Color.White,
                                                    shape = CircleShape
                                                )
                                            } else {
                                                Modifier.border(
                                                    width = 1.dp,
                                                    color = Color(0xFF777487),
                                                    shape = CircleShape
                                                )
                                            }
                                        )
                                        .clickable {
                                            groupColorIndex = index
                                        }
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        enabled = groupNameInput.isNotBlank(),
                        onClick = {
                            saveGroupDialog()
                            focusRequester.requestFocus()
                        }
                    ) {
                        Text("Сохранить")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showGroupDialog = false
                            focusRequester.requestFocus()
                        }
                    ) {
                        Text("Отмена")
                    }
                }
            )
        }

    }
}

private fun DrawScope.drawGrid(
    panX: Float,
    panY: Float,
    scale: Float
) {
    val step = 40f * scale
    if (step <= 0f) return

    val offsetX = ((panX % step) + step) % step
    val offsetY = ((panY % step) + step) % step

    val color = Color(0xFF1C1C24)

    var x = offsetX

    while (x < size.width) {
        drawLine(
            color,
            Offset(x, 0f),
            Offset(x, size.height),
            1f
        )
        x += step
    }

    var y = offsetY

    while (y < size.height) {
        drawLine(
            color,
            Offset(0f, y),
            Offset(size.width, y),
            1f
        )
        y += step
    }
}

private fun DrawScope.drawBezier(
    start: Offset,
    end: Offset,
    color: Color,
    stroke: Float
) {
    val distance = hypot(
        end.x - start.x,
        end.y - start.y
    )

    val controlOffset = min(
        120f,
        distance * 0.5f
    )

    val path = Path().apply {
        moveTo(start.x, start.y)

        cubicTo(
            start.x + controlOffset,
            start.y,
            end.x - controlOffset,
            end.y,
            end.x,
            end.y
        )
    }

    drawPath(
        path,
        color,
        style = Stroke(width = stroke)
    )
}

private fun DrawScope.drawNode(
    node: GraphNode,
    selected: Boolean,
    textMeasurer: TextMeasurer,
    panX: Float,
    panY: Float,
    scale: Float
) {
    val safeScale = scale.coerceIn(
        MIN_SCALE,
        MAX_SCALE
    )

    val topLeft = Offset(
        panX + node.x * safeScale,
        panY + node.y * safeScale
    )

    val width = NODE_W * safeScale
    val height = NODE_H * safeScale
    val radius = 8f * safeScale

    val fill = when (node.kind) {
        NodeKind.START -> Color(0xFF1B3A2A)
        NodeKind.END -> Color(0xFF3A1B1B)
        NodeKind.WAIT, NodeKind.DELAY -> Color(0xFF2A2A18)
        NodeKind.IF -> Color(0xFF302647)
        else -> Color(0xFF1E1E28)
    }

    drawRoundRect(
        color = fill,
        topLeft = topLeft,
        size = Size(width, height),
        cornerRadius = CornerRadius(radius, radius)
    )

    drawRoundRect(
        color = if (selected) {
            Color(0xFFC8B4FF)
        } else {
            Color(0xFF3A3A4A)
        },
        topLeft = topLeft,
        size = Size(width, height),
        cornerRadius = CornerRadius(radius, radius),
        style = Stroke(
            width = (if (selected) 2.5f else 1.2f) * safeScale
        )
    )

    val textX = topLeft.x + 10f * safeScale
    val titleY = topLeft.y + 8f * safeScale

    val titleWidth = (
            (if (node.kind == NodeKind.IF) {
                NODE_W - 45f
            } else {
                NODE_W - 20f
            }) * safeScale
            ).coerceAtLeast(1f)

    val titleHeight = (20f * safeScale).coerceAtLeast(1f)

    drawTextSafely(
        textMeasurer = textMeasurer,
        text = node.title,
        topLeft = Offset(textX, titleY),
        requestedSize = Size(titleWidth, titleHeight),
        style = TextStyle(
            color = Color(0xFFE8E8F0),
            fontSize = (13f * safeScale.coerceAtMost(1.4f)).sp,
            fontWeight = FontWeight.Medium
        ),
        overflow = TextOverflow.Ellipsis
    )

    val subtitle = node.params.entries.firstOrNull()?.let {
        "${it.key}: ${it.value}"
    }.orEmpty()

    if (subtitle.isNotEmpty()) {
        drawTextSafely(
            textMeasurer = textMeasurer,
            text = subtitle,
            topLeft = Offset(
                textX,
                topLeft.y + 31f * safeScale
            ),
            requestedSize = Size(
                (NODE_W - 20f) * safeScale,
                18f * safeScale
            ),
            style = TextStyle(
                color = Color(0xFF9A9AB0),
                fontSize = (11f * safeScale.coerceAtMost(1.4f)).sp
            ),
            overflow = TextOverflow.Ellipsis
        )
    }

    // Входной порт.
    val inputPort = Offset(
        topLeft.x,
        topLeft.y + height / 2f
    )

    drawCircle(
        color = Color(0xFF88CC88),
        radius = PORT_R * safeScale,
        center = inputPort
    )

    if (node.kind == NodeKind.IF) {
        val truePort = Offset(
            topLeft.x + width,
            topLeft.y + height * 0.30f
        )

        val nextPort = Offset(
            topLeft.x + width,
            topLeft.y + height * 0.70f
        )

        drawCircle(
            color = Color(0xFF72D49A),
            radius = PORT_R * safeScale,
            center = truePort
        )

        drawCircle(
            color = Color(0xFF88AADD),
            radius = PORT_R * safeScale,
            center = nextPort
        )

        drawTextSafely(
            textMeasurer = textMeasurer,
            text = "T",
            topLeft = Offset(
                topLeft.x + width - 17f * safeScale,
                topLeft.y + height * 0.30f - 5f * safeScale
            ),
            requestedSize = Size(
                16f * safeScale,
                14f * safeScale
            ),
            style = TextStyle(
                color = Color(0xFF72D49A),
                fontSize = (8f * safeScale.coerceAtMost(1.4f)).sp,
                fontWeight = FontWeight.Bold
            ),
            overflow = TextOverflow.Clip
        )

        drawTextSafely(
            textMeasurer = textMeasurer,
            text = "N",
            topLeft = Offset(
                topLeft.x + width - 17f * safeScale,
                topLeft.y + height * 0.70f - 5f * safeScale
            ),
            requestedSize = Size(
                16f * safeScale,
                14f * safeScale
            ),
            style = TextStyle(
                color = Color(0xFF88AADD),
                fontSize = (8f * safeScale.coerceAtMost(1.4f)).sp,
                fontWeight = FontWeight.Bold
            ),
            overflow = TextOverflow.Clip
        )
    } else {
        val outputPort = Offset(
            topLeft.x + width,
            topLeft.y + height / 2f
        )

        drawCircle(
            color = Color(0xFF88AADD),
            radius = PORT_R * safeScale,
            center = outputPort
        )
    }
}

/**
 * Безопасная отрисовка текста внутри границ Canvas.
 */
private fun DrawScope.drawTextSafely(
    textMeasurer: TextMeasurer,
    text: String,
    topLeft: Offset,
    requestedSize: Size,
    style: TextStyle,
    overflow: TextOverflow = TextOverflow.Ellipsis
) {
    if (text.isEmpty()) return

    if (
        !topLeft.x.isFinite() ||
        !topLeft.y.isFinite()
    ) {
        return
    }

    if (
        !requestedSize.width.isFinite() ||
        !requestedSize.height.isFinite()
    ) {
        return
    }

    if (topLeft.x < 0f || topLeft.y < 0f) return

    if (
        topLeft.x >= size.width ||
        topLeft.y >= size.height
    ) {
        return
    }

    val availableWidth = size.width - topLeft.x
    val availableHeight = size.height - topLeft.y

    if (
        availableWidth <= 1f ||
        availableHeight <= 1f
    ) {
        return
    }

    val safeWidth = min(
        requestedSize.width.coerceAtLeast(1f),
        availableWidth
    )

    val safeHeight = min(
        requestedSize.height.coerceAtLeast(1f),
        availableHeight
    )

    if (
        safeWidth <= 1f ||
        safeHeight <= 1f
    ) {
        return
    }

    drawText(
        textMeasurer = textMeasurer,
        text = text,
        topLeft = topLeft,
        style = style,
        size = Size(safeWidth, safeHeight),
        softWrap = false,
        maxLines = 1,
        overflow = overflow
    )
}
