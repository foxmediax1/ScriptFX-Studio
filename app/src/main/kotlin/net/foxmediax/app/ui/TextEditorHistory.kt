
package net.foxmediax.app.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import kotlinx.serialization.Serializable

class TextEditorHistory(
    private val maxHistory: Int = 300
) {

    @Serializable
    data class State(
        val text: String,
        val selectionStart: Int,
        val selectionEnd: Int
    ) {
        fun toTextFieldValue(): TextFieldValue {
            val start = selectionStart.coerceIn(0, text.length)
            val end = selectionEnd.coerceIn(0, text.length)

            return TextFieldValue(
                text = text,
                selection = TextRange(start, end)
            )
        }

        companion object {
            fun from(value: TextFieldValue) = State(
                text = value.text,
                selectionStart = value.selection.start,
                selectionEnd = value.selection.end
            )
        }
    }

    @Serializable
    data class Snapshot(
        val current: State,
        val undo: List<State>,
        val redo: List<State>
    )

    private val undoStack = ArrayDeque<State>()
    private val redoStack = ArrayDeque<State>()

    val canUndo = mutableStateOf(false)
    val canRedo = mutableStateOf(false)

    val undoCount: Int
        get() = undoStack.size

    val redoCount: Int
        get() = redoStack.size

    private val historyLimit: Int
        get() = maxHistory.coerceAtLeast(1)

    // =====================================================
    // CHECKPOINT
    // =====================================================

    fun checkpoint(previous: TextFieldValue) {
        val state = State.from(previous)

        if (undoStack.lastOrNull() != state) {
            undoStack.addLast(state)
            trim(undoStack)
        }

        redoStack.clear()
        updateState()
    }

    // =====================================================
    // UNDO
    // =====================================================

    fun undo(current: TextFieldValue): TextFieldValue? {
        if (undoStack.isEmpty()) return null

        val restored = undoStack.removeLast()

        redoStack.addLast(State.from(current))
        trim(redoStack)

        updateState()

        return restored.toTextFieldValue()
    }

    // =====================================================
    // REDO
    // =====================================================

    fun redo(current: TextFieldValue): TextFieldValue? {
        if (redoStack.isEmpty()) return null

        val restored = redoStack.removeLast()

        undoStack.addLast(State.from(current))
        trim(undoStack)

        updateState()

        return restored.toTextFieldValue()
    }

    // =====================================================
    // SNAPSHOT
    // =====================================================

    fun snapshot(current: TextFieldValue): Snapshot {
        return Snapshot(
            current = State.from(current),
            undo = undoStack.toList(),
            redo = redoStack.toList()
        )
    }

    // =====================================================
    // RESTORE SNAPSHOT
    // =====================================================

    fun restore(snapshot: Snapshot): TextFieldValue {
        reset()

        snapshot.undo
            .takeLast(historyLimit)
            .forEach {
                undoStack.addLast(it)
            }

        snapshot.redo
            .takeLast(historyLimit)
            .forEach {
                redoStack.addLast(it)
            }

        updateState()

        return snapshot.current.toTextFieldValue()
    }

    // =====================================================
    // RESET
    // =====================================================

    fun reset() {
        undoStack.clear()
        redoStack.clear()

        updateState()
    }

    // =====================================================
    // LIMIT
    // =====================================================

    private fun trim(stack: ArrayDeque<State>) {
        while (stack.size > historyLimit) {
            stack.removeFirst()
        }
    }

    // =====================================================
    // STATE
    // =====================================================

    private fun updateState() {
        canUndo.value = undoStack.isNotEmpty()
        canRedo.value = redoStack.isNotEmpty()
    }
}
