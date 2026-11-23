
package net.foxmediax.app.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.text.input.TextFieldValue

class TextEditorHistory(
    private val maxHistory: Int = 300
) {

    private val undoStack = ArrayDeque<TextFieldValue>()
    private val redoStack = ArrayDeque<TextFieldValue>()

    val canUndo = mutableStateOf(false)
    val canRedo = mutableStateOf(false)

    val undoCount: Int
        get() = undoStack.size

    val redoCount: Int
        get() = redoStack.size

    // =====================================================
    // CHECKPOINT
    // =====================================================

    fun checkpoint(
        previous: TextFieldValue
    ) {

        // Не сохраняем одинаковые состояния подряд.
        if (undoStack.lastOrNull() != previous) {

            undoStack.addLast(previous)

            while (undoStack.size > maxHistory.coerceAtLeast(1)) {
                undoStack.removeFirst()
            }
        }

        // Новое изменение отменяет возможность Redo.
        redoStack.clear()

        updateState()
    }

    // =====================================================
    // UNDO
    // =====================================================

    fun undo(
        current: TextFieldValue
    ): TextFieldValue? {

        if (undoStack.isEmpty()) {
            return null
        }

        val restored = undoStack.removeLast()

        redoStack.addLast(current)

        updateState()

        return restored
    }

    // =====================================================
    // REDO
    // =====================================================

    fun redo(
        current: TextFieldValue
    ): TextFieldValue? {

        if (redoStack.isEmpty()) {
            return null
        }

        val restored = redoStack.removeLast()

        undoStack.addLast(current)

        while (undoStack.size > maxHistory.coerceAtLeast(1)) {
            undoStack.removeFirst()
        }

        updateState()

        return restored
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
    // STATE
    // =====================================================

    private fun updateState() {

        canUndo.value = undoStack.isNotEmpty()
        canRedo.value = redoStack.isNotEmpty()
    }
}
