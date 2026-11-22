
@file:OptIn(ExperimentalComposeUiApi::class)

package net.foxmediax.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
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
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import net.foxmediax.utils.nodes.GraphEdge
import net.foxmediax.utils.nodes.GraphNode
import net.foxmediax.utils.nodes.NodeGraph
import net.foxmediax.utils.nodes.NodeKind
import kotlin.math.hypot
import kotlin.math.min

private const val NODE_W = 180f
private const val NODE_H = 60f
private const val PORT_R = 7f

private const val MIN_SCALE = 0.3f
private const val MAX_SCALE = 2.5f

@Composable
fun NodeCanvas(
    graph: NodeGraph,
    onGraphChanged: (NodeGraph) -> Unit,
    modifier: Modifier = Modifier,
    controller: NodeCanvasController? = null
){
    val textMeasurer = rememberTextMeasurer()
    val focusRequester = remember { FocusRequester() }

    var panX by remember { mutableStateOf(0f) }
    var panY by remember { mutableStateOf(0f) }
    var scale by remember { mutableStateOf(1f) }

    var selectedId by remember { mutableStateOf<String?>(null) }
    var linkingFrom by remember { mutableStateOf<String?>(null) }
    var linkingPort by remember { mutableStateOf("out") }

    var linkX by remember { mutableStateOf(0f) }
    var linkY by remember { mutableStateOf(0f) }

    var version by remember { mutableIntStateOf(0) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

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
            node.kind == NodeKind.IF && port == "true" -> NODE_H * 0.30f
            node.kind == NodeKind.IF && port == "next" -> NODE_H * 0.70f
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

    fun zoomAtCenter(factor: Float) {
        val center = Offset(canvasSize.width / 2f, canvasSize.height / 2f)
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
        val availableWidth = (canvasSize.width - padding * 2).coerceAtLeast(1f)
        val availableHeight = (canvasSize.height - padding * 2).coerceAtLeast(1f)

        scale = min(
            availableWidth / graphWidth,
            availableHeight / graphHeight
        ).coerceIn(MIN_SCALE, MAX_SCALE)

        panX = (canvasSize.width - graphWidth * scale) / 2f - minX * scale
        panY = (canvasSize.height - graphHeight * scale) / 2f - minY * scale

        version++
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    LaunchedEffect(controller, canvasSize, System.identityHashCode(graph)) {
        selectedId = null
        controller?.selectedNodeId?.value = null

        controller?.addNodeAtCenterAction = { node ->
            controller.checkpoint(graph)

            val center = Offset(
                canvasSize.width / 2f,
                canvasSize.height / 2f
            )
            val world = screenToWorld(center)

            node.x = world.x - NODE_W / 2f
            node.y = world.y - NODE_H / 2f

            graph.nodes.add(node)

            selectedId = node.id
            controller.selectedNodeId.value = node.id

            version++
            onGraphChanged(graph)
        }

        controller?.fitGraphAction = { fitGraph() }
        controller?.zoomInAction = { zoomAtCenter(1.15f) }
        controller?.zoomOutAction = { zoomAtCenter(0.87f) }

        controller?.undoAction = {
            val restored = controller.undoGraph(graph)
            if (restored != null) {
                selectedId = null
                controller.selectedNodeId.value = null
                version++
                onGraphChanged(restored)
            }
        }

        controller?.redoAction = {
            val restored = controller.redoGraph(graph)
            if (restored != null) {
                selectedId = null
                controller.selectedNodeId.value = null
                version++
                onGraphChanged(restored)
            }
        }

        if (canvasSize.width > 0 && canvasSize.height > 0) {
            fitGraph()
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
            .background(Color(0xFF121218))
            .onSizeChanged { canvasSize = it }
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) {
                    return@onPreviewKeyEvent false
                }

                if (event.isCtrlPressed) {
                    when {
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
                    }
                }

                if (event.key == Key.Delete || event.key == Key.Backspace) {
                    val id = selectedId ?: return@onPreviewKeyEvent false
                    val node = graph.node(id) ?: return@onPreviewKeyEvent false

                    if (node.kind == NodeKind.START || node.kind == NodeKind.END) {
                        return@onPreviewKeyEvent true
                    }

                    controller?.checkpoint(graph)

                    graph.edges.removeAll { it.from == id || it.to == id }
                    graph.nodes.removeAll { it.id == id }

                    selectedId = null
                    controller?.selectedNodeId?.value = null

                    version++
                    onGraphChanged(graph)

                    return@onPreviewKeyEvent true
                }

                false
            }
            .onPointerEvent(PointerEventType.Scroll) { event ->
                val change = event.changes.firstOrNull() ?: return@onPointerEvent
                val dy = change.scrollDelta.y
                if (dy == 0f) return@onPointerEvent

                val position = change.position
                val oldScale = scale

                scale = (scale * if (dy > 0f) 0.9f else 1.1f)
                    .coerceIn(MIN_SCALE, MAX_SCALE)

                panX = position.x - (position.x - panX) * (scale / oldScale)
                panY = position.y - (position.y - panY) * (scale / oldScale)

                version++
                change.consume()
            }
            .pointerInput(graph, controller) {
                awaitEachGesture {
                    val downEvent = awaitPointerEvent(PointerEventPass.Initial)
                    val downChange = downEvent.changes.firstOrNull()
                        ?: return@awaitEachGesture

                    if (!downChange.pressed) return@awaitEachGesture

                    focusRequester.requestFocus()
                    val buttons = downEvent.buttons
                    val startWorld = screenToWorld(downChange.position)

                    // Pan: right or middle mouse button.
                    if (buttons.isSecondaryPressed || buttons.isTertiaryPressed) {
                        var lastPosition = downChange.position

                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == downChange.id }
                                ?: break

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

                    // Begin link dragging from output port.
                    val outputHit = hitOutPort(startWorld)

                    if (outputHit != null) {
                        val sourceNode = outputHit.first
                        val sourcePort = outputHit.second

                        linkingFrom = sourceNode.id
                        linkingPort = sourcePort
                        linkX = startWorld.x
                        linkY = startWorld.y

                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == downChange.id }
                                ?: break

                            val world = screenToWorld(change.position)
                            linkX = world.x
                            linkY = world.y

                            if (change.changedToUp()) {
                                val target = hitInPort(world)

                                if (target != null && target.id != sourceNode.id) {
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

                    // Select and move node.
                    val hit = hitNode(startWorld)
                    selectedId = hit?.id
                    controller?.selectedNodeId?.value = hit?.id

                    if (hit != null) {
                        val offsetX = startWorld.x - hit.x
                        val offsetY = startWorld.y - hit.y
                        var movementRecorded = false

                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == downChange.id }
                                ?: break

                            val world = screenToWorld(change.position)
                            val newX = world.x - offsetX
                            val newY = world.y - offsetY

                            // Record only once, when the node actually starts moving.
                            if (!movementRecorded && (newX != hit.x || newY != hit.y)) {
                                controller?.checkpoint(graph)
                                movementRecorded = true
                            }

                            hit.x = newX
                            hit.y = newY
                            version++

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
            }
    ) {
        val drawVersion = version
        if (drawVersion == Int.MIN_VALUE) return@Canvas

        drawGrid(panX, panY, scale)

        // Existing edges.
        for (edge in graph.edges) {
            val sourceNode = graph.node(edge.from) ?: continue
            val targetNode = graph.node(edge.to) ?: continue

            val source = outputPortWorld(sourceNode, edge.fromPort)

            val start = Offset(
                panX + source.x * scale,
                panY + source.y * scale
            )

            val end = Offset(
                panX + targetNode.x * scale,
                panY + (targetNode.y + NODE_H / 2f) * scale
            )

            drawBezier(start, end, Color(0xFF8B7EC8), 2.5f * scale)
        }

        // Active connection preview.
        linkingFrom?.let { id ->
            val sourceNode = graph.node(id)
            if (sourceNode != null) {
                val source = outputPortWorld(sourceNode, linkingPort)

                val start = Offset(
                    panX + source.x * scale,
                    panY + source.y * scale
                )

                val end = Offset(
                    panX + linkX * scale,
                    panY + linkY * scale
                )

                drawBezier(start, end, Color(0xFFC8B4FF), 2.5f * scale)
            }
        }

        // Nodes.
        for (node in graph.nodes) {
            drawNode(
                node = node,
                selected = node.id == selectedId,
                textMeasurer = textMeasurer,
                panX = panX,
                panY = panY,
                scale = scale
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
    val offsetX = ((panX % step) + step) % step
    val offsetY = ((panY % step) + step) % step
    val color = Color(0xFF1C1C24)

    var x = offsetX
    while (x < size.width) {
        drawLine(color, Offset(x, 0f), Offset(x, size.height), 1f)
        x += step
    }

    var y = offsetY
    while (y < size.height) {
        drawLine(color, Offset(0f, y), Offset(size.width, y), 1f)
        y += step
    }
}

private fun DrawScope.drawBezier(
    start: Offset,
    end: Offset,
    color: Color,
    stroke: Float
) {
    val distance = hypot(end.x - start.x, end.y - start.y)
    val controlOffset = min(120f, distance * 0.5f)

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

    drawPath(path, color, style = Stroke(width = stroke))
}

private fun DrawScope.drawNode(
    node: GraphNode,
    selected: Boolean,
    textMeasurer: TextMeasurer,
    panX: Float,
    panY: Float,
    scale: Float
) {
    val topLeft = Offset(panX + node.x * scale, panY + node.y * scale)
    val width = NODE_W * scale
    val height = NODE_H * scale
    val radius = 8f * scale

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
        color = if (selected) Color(0xFFC8B4FF) else Color(0xFF3A3A4A),
        topLeft = topLeft,
        size = Size(width, height),
        cornerRadius = CornerRadius(radius, radius),
        style = Stroke(width = (if (selected) 2.5f else 1.2f) * scale)
    )

    val textX = topLeft.x + 10f * scale
    val titleY = topLeft.y + 8f * scale
    val subtitleY = topLeft.y + 31f * scale

    val titleWidth = if (node.kind == NodeKind.IF) {
        (NODE_W - 45f) * scale
    } else {
        (NODE_W - 20f) * scale
    }

    if (titleXIsVisible(textX, size.width) && titleY < size.height) {
        drawText(
            textMeasurer = textMeasurer,
            text = node.title,
            topLeft = Offset(textX, titleY),
            style = TextStyle(
                color = Color(0xFFE8E8F0),
                fontSize = (13f * scale.coerceAtMost(1.4f)).sp,
                fontWeight = FontWeight.Medium
            ),
            size = Size(titleWidth.coerceAtLeast(1f), 20f * scale),
            softWrap = false,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }

    val subtitle = node.params.entries.firstOrNull()?.let {
        "${it.key}: ${it.value}"
    }.orEmpty()

    if (subtitle.isNotEmpty() && textX < size.width && subtitleY < size.height) {
        drawText(
            textMeasurer = textMeasurer,
            text = subtitle,
            topLeft = Offset(textX, subtitleY),
            style = TextStyle(
                color = Color(0xFF9A9AB0),
                fontSize = (11f * scale.coerceAtMost(1.4f)).sp
            ),
            size = Size(
                ((NODE_W - 20f) * scale).coerceAtLeast(1f),
                18f * scale
            ),
            softWrap = false,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }

    val inputPort = Offset(topLeft.x, topLeft.y + height / 2f)
    drawCircle(Color(0xFF88CC88), PORT_R * scale, inputPort)

    if (node.kind == NodeKind.IF) {
        val truePort = Offset(topLeft.x + width, topLeft.y + height * 0.30f)
        val nextPort = Offset(topLeft.x + width, topLeft.y + height * 0.70f)

        drawCircle(Color(0xFF72D49A), PORT_R * scale, truePort)
        drawCircle(Color(0xFF88AADD), PORT_R * scale, nextPort)

        drawText(
            textMeasurer = textMeasurer,
            text = "T",
            topLeft = Offset(
                topLeft.x + width - 17f * scale,
                topLeft.y + height * 0.30f - 5f * scale
            ),
            style = TextStyle(
                color = Color(0xFF72D49A),
                fontSize = (8f * scale.coerceAtMost(1.4f)).sp,
                fontWeight = FontWeight.Bold
            )
        )

        drawText(
            textMeasurer = textMeasurer,
            text = "N",
            topLeft = Offset(
                topLeft.x + width - 17f * scale,
                topLeft.y + height * 0.70f - 5f * scale
            ),
            style = TextStyle(
                color = Color(0xFF88AADD),
                fontSize = (8f * scale.coerceAtMost(1.4f)).sp,
                fontWeight = FontWeight.Bold
            )
        )
    } else {
        val outputPort = Offset(topLeft.x + width, topLeft.y + height / 2f)
        drawCircle(Color(0xFF88AADD), PORT_R * scale, outputPort)
    }
}

private fun titleXIsVisible(
    x: Float,
    canvasWidth: Float
): Boolean = x < canvasWidth
