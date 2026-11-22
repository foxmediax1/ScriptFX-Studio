@file:OptIn(ExperimentalComposeUiApi::class)

package net.foxmediax.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.isTertiaryPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
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
) {
    val textMeasurer = rememberTextMeasurer()
    val focusRequester = remember { FocusRequester() }

    var panX by remember { mutableStateOf(0f) }
    var panY by remember { mutableStateOf(0f) }
    var scale by remember { mutableStateOf(1f) }

    var selectedId by remember { mutableStateOf<String?>(null) }
    var dragNodeId by remember { mutableStateOf<String?>(null) }
    var dragOffX by remember { mutableStateOf(0f) }
    var dragOffY by remember { mutableStateOf(0f) }

    var linkingFrom by remember { mutableStateOf<String?>(null) }
    var linkX by remember { mutableStateOf(0f) }
    var linkY by remember { mutableStateOf(0f) }

    var version by remember { mutableStateOf(0) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    // локальные функции — ОБЪЯВЛЕНЫ ДО LaunchedEffect, который их использует
    fun screenToWorld(p: Offset) = Offset((p.x - panX) / scale, (p.y - panY) / scale)
    fun hitNode(world: Offset): GraphNode? = graph.nodes.asReversed().firstOrNull { n ->
        world.x in n.x..(n.x + NODE_W) && world.y in n.y..(n.y + NODE_H)
    }
    fun hitOutPort(world: Offset): GraphNode? = graph.nodes.asReversed().firstOrNull { n ->
        hypot(world.x - (n.x + NODE_W), world.y - (n.y + NODE_H / 2f)) <= PORT_R * 2f
    }
    fun hitInPort(world: Offset): GraphNode? = graph.nodes.asReversed().firstOrNull { n ->
        hypot(world.x - n.x, world.y - (n.y + NODE_H / 2f)) <= PORT_R * 2.5f
    }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    LaunchedEffect(graph) { selectedId = null; version++ }

    LaunchedEffect(controller, canvasSize) {
        controller?.addNodeAtCenter = { node ->
            val cx = canvasSize.width / 2f
            val cy = canvasSize.height / 2f
            val world = screenToWorld(Offset(cx, cy))
            node.x = world.x - NODE_W / 2f
            node.y = world.y - NODE_H / 2f
            graph.nodes.add(node)
            selectedId = node.id
            version++
            onGraphChanged(graph)
        }
        controller?.resetView = {
            panX = 0f; panY = 0f; scale = 1f
            version++
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF121218))
            .onSizeChanged { canvasSize = it }
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { ev ->
                if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                if (ev.key == Key.Delete || ev.key == Key.Backspace) {
                    val id = selectedId ?: return@onPreviewKeyEvent false
                    val n = graph.node(id) ?: return@onPreviewKeyEvent false
                    if (n.kind == NodeKind.START || n.kind == NodeKind.END) return@onPreviewKeyEvent true
                    graph.edges.removeAll { it.from == id || it.to == id }
                    graph.nodes.removeAll { it.id == id }
                    selectedId = null
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
                val pos = change.position
                val old = scale
                scale = (scale * (if (dy > 0) 0.9f else 1.1f)).coerceIn(MIN_SCALE, MAX_SCALE)
                panX = pos.x - (pos.x - panX) * (scale / old)
                panY = pos.y - (pos.y - panY) * (scale / old)
                change.consume()
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val downEvent = awaitPointerEvent(PointerEventPass.Initial)
                    val downChange = downEvent.changes.firstOrNull() ?: return@awaitEachGesture
                    if (!downChange.pressed) return@awaitEachGesture

                    focusRequester.requestFocus()
                    val buttons = downEvent.buttons
                    val startWorld = screenToWorld(downChange.position)

                    if (buttons.isSecondaryPressed || buttons.isTertiaryPressed) {
                        var lastPos = downChange.position
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == downChange.id } ?: break
                            if (change.changedToUp()) { change.consume(); break }
                            panX += change.position.x - lastPos.x
                            panY += change.position.y - lastPos.y
                            lastPos = change.position
                            change.consume()
                        }
                        return@awaitEachGesture
                    }

                    val outNode = hitOutPort(startWorld)
                    if (outNode != null) {
                        linkingFrom = outNode.id
                        linkX = startWorld.x
                        linkY = startWorld.y
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == downChange.id } ?: break
                            val world = screenToWorld(change.position)
                            linkX = world.x
                            linkY = world.y
                            if (change.changedToUp()) {
                                val target = hitInPort(world)
                                if (target != null && target.id != outNode.id) {
                                    graph.edges.removeAll { it.from == outNode.id && it.fromPort == "out" }
                                    graph.edges.add(GraphEdge(outNode.id, "out", target.id, "in"))
                                    version++
                                    onGraphChanged(graph)
                                }
                                linkingFrom = null
                                change.consume()
                                break
                            }
                            change.consume()
                        }
                        return@awaitEachGesture
                    }

                    val hit = hitNode(startWorld)
                    selectedId = hit?.id
                    if (hit != null) {
                        dragNodeId = hit.id
                        dragOffX = startWorld.x - hit.x
                        dragOffY = startWorld.y - hit.y
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == downChange.id } ?: break
                            val world = screenToWorld(change.position)
                            hit.x = world.x - dragOffX
                            hit.y = world.y - dragOffY
                            version++
                            if (change.changedToUp()) {
                                dragNodeId = null
                                onGraphChanged(graph)
                                change.consume()
                                break
                            }
                            change.consume()
                        }
                    }
                }
            }
    ) {
        val trigger = version
        if (trigger == Int.MIN_VALUE) return@Canvas

        drawGrid(panX, panY, scale)

        for (e in graph.edges) {
            val a = graph.node(e.from) ?: continue
            val b = graph.node(e.to) ?: continue
            val p1 = Offset(panX + (a.x + NODE_W) * scale, panY + (a.y + NODE_H / 2f) * scale)
            val p2 = Offset(panX + b.x * scale, panY + (b.y + NODE_H / 2f) * scale)
            drawBezier(p1, p2, Color(0xFF8B7EC8), 2.5f * scale)
        }

        linkingFrom?.let { id ->
            graph.node(id)?.let { a ->
                val p1 = Offset(panX + (a.x + NODE_W) * scale, panY + (a.y + NODE_H / 2f) * scale)
                val p2 = Offset(panX + linkX * scale, panY + linkY * scale)
                drawBezier(p1, p2, Color(0xFFC8B4FF), 2.5f * scale)
            }
        }

        for (n in graph.nodes) {
            drawNode(n, n.id == selectedId, textMeasurer, panX, panY, scale)
        }
    }
}

private fun DrawScope.drawGrid(panX: Float, panY: Float, scale: Float) {
    val step = 40f * scale
    val ox = ((panX % step) + step) % step
    val oy = ((panY % step) + step) % step
    val c = Color(0xFF1C1C24)
    var x = ox
    while (x < size.width) { drawLine(c, Offset(x, 0f), Offset(x, size.height), 1f); x += step }
    var y = oy
    while (y < size.height) { drawLine(c, Offset(0f, y), Offset(size.width, y), 1f); y += step }
}

private fun DrawScope.drawBezier(a: Offset, b: Offset, color: Color, stroke: Float) {
    val dx = min(120f, hypot(b.x - a.x, b.y - a.y) * 0.5f)
    val path = Path().apply {
        moveTo(a.x, a.y)
        cubicTo(a.x + dx, a.y, b.x - dx, b.y, b.x, b.y)
    }
    drawPath(path, color, style = Stroke(width = stroke))
}

private fun DrawScope.drawNode(
    n: GraphNode, selected: Boolean, textMeasurer: TextMeasurer,
    panX: Float, panY: Float, scale: Float
) {
    val tl = Offset(panX + n.x * scale, panY + n.y * scale)
    val w = NODE_W * scale
    val h = NODE_H * scale
    val r = 8f * scale

    val fill = when (n.kind) {
        NodeKind.START -> Color(0xFF1B3A2A)
        NodeKind.END   -> Color(0xFF3A1B1B)
        NodeKind.WAIT, NodeKind.DELAY -> Color(0xFF2A2A18)
        else -> Color(0xFF1E1E28)
    }
    drawRoundRect(fill, topLeft = tl, size = Size(w, h), cornerRadius = CornerRadius(r, r))
    drawRoundRect(
        color = if (selected) Color(0xFFC8B4FF) else Color(0xFF3A3A4A),
        topLeft = tl, size = Size(w, h), cornerRadius = CornerRadius(r, r),
        style = Stroke(width = (if (selected) 2.5f else 1.2f) * scale)
    )

    drawText(
        textMeasurer = textMeasurer, text = n.title,
        topLeft = Offset(tl.x + 10f * scale, tl.y + 8f * scale),
        style = TextStyle(color = Color(0xFFE8E8F0),
            fontSize = (13 * scale.coerceAtMost(1.4f)).sp, fontWeight = FontWeight.Medium)
    )
    val sub = n.params.entries.firstOrNull()?.let { "${it.key}: ${it.value}" } ?: ""
    if (sub.isNotEmpty()) {
        drawText(
            textMeasurer = textMeasurer, text = sub.take(24),
            topLeft = Offset(tl.x + 10f * scale, tl.y + 30f * scale),
            style = TextStyle(color = Color(0xFF9A9AB0),
                fontSize = (11 * scale.coerceAtMost(1.4f)).sp)
        )
    }

    val inP = Offset(tl.x, tl.y + h / 2f)
    val outP = Offset(tl.x + w, tl.y + h / 2f)
    drawCircle(Color(0xFF88CC88), PORT_R * scale, inP)
    drawCircle(Color(0xFF88AADD), PORT_R * scale, outP)
}