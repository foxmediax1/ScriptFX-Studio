package net.foxmediax.app.ui

import net.foxmediax.utils.nodes.GraphNode

/**
 * Мост между родительским Composable и NodeCanvas.
 * Позволяет добавить ноду в центр холста (используется палитрой).
 */
class NodeCanvasController {
    internal var addNodeAtCenter: ((GraphNode) -> Unit)? = null
    internal var resetView: (() -> Unit)? = null

    fun addNode(node: GraphNode) {
        addNodeAtCenter?.invoke(node)
    }

    fun resetView() {
        resetView?.invoke()
    }
}