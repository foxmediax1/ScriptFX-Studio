
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

    val canUndo: Boolean
        get() = undoStack.isNotEmpty()

    val canRedo: Boolean
        get() = redoStack.isNotEmpty()

    val undoCount: Int
        get() = undoStack.size

    val redoCount: Int
        get() = redoStack.size

    /**
     * Stores the current graph state before an edit.
     */
    fun checkpoint(graph: NodeGraph) {
        val snapshot = graph.toJson()

        // Do not store duplicate consecutive states.
        if (undoStack.lastOrNull() != snapshot) {
            undoStack.addLast(snapshot)

            while (undoStack.size > maxHistory.coerceAtLeast(1)) {
                undoStack.removeFirst()
            }
        }

        // A new edit invalidates the redo branch.
        redoStack.clear()
    }

    /**
     * Restores the previous graph state.
     */
    fun undo(current: NodeGraph): NodeGraph? {
        if (undoStack.isEmpty()) return null

        val targetSnapshot = undoStack.lastOrNull() ?: return null

        // Decode before changing either stack.
        val restored = decode(targetSnapshot) ?: return null

        undoStack.removeLast()
        redoStack.addLast(current.toJson())

        return restored
    }

    /**
     * Reapplies the last undone graph state.
     */
    fun redo(current: NodeGraph): NodeGraph? {
        if (redoStack.isEmpty()) return null

        val targetSnapshot = redoStack.lastOrNull() ?: return null
        val restored = decode(targetSnapshot) ?: return null

        redoStack.removeLast()
        undoStack.addLast(current.toJson())

        while (undoStack.size > maxHistory.coerceAtLeast(1)) {
            undoStack.removeFirst()
        }

        return restored
    }

    /**
     * Clears history when another scenario is opened.
     */
    fun reset(graph: NodeGraph) {
        undoStack.clear()
        redoStack.clear()
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
