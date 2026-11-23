
package net.foxmediax.utils.nodes

/**
 * Snapshot-based undo/redo history for NodeGraph.
 *
 * checkpoint(graph) must be called BEFORE mutating the graph.
 */
class NodeGraphHistory(
    private val maxHistory: Int = 100
) {

    data class Snapshot(
        val undo: List<String>,
        val redo: List<String>
    )

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

    private val historyLimit: Int
        get() = maxHistory.coerceAtLeast(1)

    /**
     * Stores the current graph state before an edit.
     */
    fun checkpoint(graph: NodeGraph) {
        val snapshot = graph.toJson()

        if (undoStack.lastOrNull() != snapshot) {
            undoStack.addLast(snapshot)
            trim(undoStack)
        }

        redoStack.clear()
    }

    /**
     * Restores the previous graph state.
     */
    fun undo(current: NodeGraph): NodeGraph? {
        if (undoStack.isEmpty()) return null

        val targetSnapshot = undoStack.lastOrNull() ?: return null
        val restored = decode(targetSnapshot) ?: return null

        undoStack.removeLast()
        redoStack.addLast(current.toJson())
        trim(redoStack)

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
        trim(undoStack)

        return restored
    }

    /**
     * Creates a persistent snapshot of undo/redo stacks.
     */
    fun snapshot(): Snapshot {
        return Snapshot(
            undo = undoStack.toList(),
            redo = redoStack.toList()
        )
    }

    /**
     * Restores persistent history.
     *
     * All graph snapshots are validated before changing the stacks.
     */
    fun restore(snapshot: Snapshot): Boolean {
        if (
            snapshot.undo.size > historyLimit ||
            snapshot.redo.size > historyLimit
        ) {
            return false
        }

        val allSnapshots = snapshot.undo + snapshot.redo

        if (allSnapshots.any { decode(it) == null }) {
            return false
        }

        undoStack.clear()
        redoStack.clear()

        snapshot.undo.forEach {
            undoStack.addLast(it)
        }

        snapshot.redo.forEach {
            redoStack.addLast(it)
        }

        return true
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

    private fun trim(stack: ArrayDeque<String>) {
        while (stack.size > historyLimit) {
            stack.removeFirst()
        }
    }

    private fun decode(snapshot: String): NodeGraph? {
        return runCatching {
            NodeGraph.fromJson(snapshot)
        }.getOrNull()
    }
}
