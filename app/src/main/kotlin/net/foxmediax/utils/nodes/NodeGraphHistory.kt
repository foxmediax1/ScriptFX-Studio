
package net.foxmediax.utils.nodes

/**
 * Snapshot-based undo/redo history for NodeGraph.
 *
 * checkpoint(graph) must be called BEFORE mutating the graph.
 */
class NodeGraphHistory(
    private val maxHistory: Int = 100
) {
    private val undoStack = ArrayDeque<String>()
    private val redoStack = ArrayDeque<String>()

    private var lastCheckpoint: String? = null

    val canUndo: Boolean
        get() = undoStack.isNotEmpty()

    val canRedo: Boolean
        get() = redoStack.isNotEmpty()

    val undoCount: Int
        get() = undoStack.size

    val redoCount: Int
        get() = redoStack.size

    /**
     * Stores the graph state before an action takes place.
     * Call this before modifying nodes, edges, or parameters.
     */
    fun checkpoint(graph: NodeGraph) {
        val snapshot = graph.toJson()

        if (snapshot != lastCheckpoint) {
            undoStack.addLast(snapshot)

            while (undoStack.size > maxHistory.coerceAtLeast(1)) {
                undoStack.removeFirst()
            }

            lastCheckpoint = snapshot
        }

        // Любое новое редактирование отменяет возможность Redo.
        redoStack.clear()
    }

    /**
     * Restores the previous graph state.
     */
    fun undo(current: NodeGraph): NodeGraph? {
        if (undoStack.isEmpty()) return null

        redoStack.addLast(current.toJson())

        val snapshot = undoStack.removeLast()
        lastCheckpoint = snapshot

        return decode(snapshot)
    }

    /**
     * Reapplies the last undone action.
     */
    fun redo(current: NodeGraph): NodeGraph? {
        if (redoStack.isEmpty()) return null

        undoStack.addLast(current.toJson())

        while (undoStack.size > maxHistory.coerceAtLeast(1)) {
            undoStack.removeFirst()
        }

        val snapshot = redoStack.removeLast()
        lastCheckpoint = snapshot

        return decode(snapshot)
    }

    /**
     * Resets the history when a different graph is opened.
     */
    fun reset(graph: NodeGraph) {
        undoStack.clear()
        redoStack.clear()
        lastCheckpoint = graph.toJson()
    }

    fun clearRedo() {
        redoStack.clear()
    }

    private fun decode(snapshot: String): NodeGraph? {
        return runCatching {
            NodeGraph.fromJson(snapshot)
        }.getOrNull()
    }
}
