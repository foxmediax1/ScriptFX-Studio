
package net.foxmediax.app.ui

import androidx.compose.runtime.mutableStateOf
import net.foxmediax.utils.nodes.GraphNode
import net.foxmediax.utils.nodes.NodeGraph
import net.foxmediax.utils.nodes.NodeGraphHistory

/**
 * Управляет NodeCanvas, камерой и историей изменений графа.
 */
class NodeCanvasController {

    private val history = NodeGraphHistory(100)

    internal var addNodeAtCenterAction: ((GraphNode) -> Unit)? = null
    internal var fitGraphAction: (() -> Unit)? = null
    internal var zoomInAction: (() -> Unit)? = null
    internal var zoomOutAction: (() -> Unit)? = null

    internal var undoAction: (() -> Unit)? = null
    internal var redoAction: (() -> Unit)? = null

    // Переход к узлу по ID.
    internal var focusNodeAction: ((String) -> Unit)? = null

    val selectedNodeId = mutableStateOf<String?>(null)

    val canUndo = mutableStateOf(false)
    val canRedo = mutableStateOf(false)

    fun addNode(node: GraphNode) {
        addNodeAtCenterAction?.invoke(node)
    }

    fun fitGraph() {
        fitGraphAction?.invoke()
    }

    fun zoomIn() {
        zoomInAction?.invoke()
    }

    fun zoomOut() {
        zoomOutAction?.invoke()
    }

    fun undo() {
        undoAction?.invoke()
    }

    fun redo() {
        redoAction?.invoke()
    }

    /**
     * Центрирует холст на указанном узле.
     */
    fun focusNode(nodeId: String) {
        focusNodeAction?.invoke(nodeId)
    }

    /**
     * Вызывать перед изменением графа.
     */
    fun checkpoint(graph: NodeGraph) {
        history.checkpoint(graph)
        updateHistoryState()
    }

    /**
     * Вызывать при открытии другого сценария.
     */
    fun resetHistory(graph: NodeGraph) {
        history.reset(graph)
        selectedNodeId.value = null
        updateHistoryState()
    }

    internal fun undoGraph(current: NodeGraph): NodeGraph? {
        val result = history.undo(current)
        updateHistoryState()
        return result
    }

    internal fun redoGraph(current: NodeGraph): NodeGraph? {
        val result = history.redo(current)
        updateHistoryState()
        return result
    }

    private fun updateHistoryState() {
        canUndo.value = history.canUndo
        canRedo.value = history.canRedo
    }
}
