
package net.foxmediax.app.ui

import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.text.input.TextFieldValue

/**
 * Единая точка управления Undo/Redo проекта.
 *
 * Фактические истории текста и графа остаются независимыми.
 * Менеджер направляет действия в историю активного редактора.
 */
class ProjectHistoryManager(
    private val activeMode: () -> CanvasMode,
    private val textHistory: TextEditorHistory,
    private val graphController: NodeCanvasController,
    private val currentText: () -> TextFieldValue,
    private val onTextRestored: (TextFieldValue) -> Unit
) {

    val canUndo: State<Boolean> = derivedStateOf {
        when (activeMode()) {
            CanvasMode.TEXT -> textHistory.canUndo.value
            CanvasMode.NODES -> graphController.canUndo.value
        }
    }

    val canRedo: State<Boolean> = derivedStateOf {
        when (activeMode()) {
            CanvasMode.TEXT -> textHistory.canRedo.value
            CanvasMode.NODES -> graphController.canRedo.value
        }
    }

    // =====================================================
    // UNDO
    // =====================================================

    fun undo(): Boolean {
        if (!canUndo.value) return false

        return when (activeMode()) {

            CanvasMode.TEXT -> {
                val restored = textHistory.undo(
                    currentText()
                ) ?: return false

                onTextRestored(restored)
                true
            }

            CanvasMode.NODES -> {
                graphController.undo()
                true
            }
        }
    }

    // =====================================================
    // REDO
    // =====================================================

    fun redo(): Boolean {
        if (!canRedo.value) return false

        return when (activeMode()) {

            CanvasMode.TEXT -> {
                val restored = textHistory.redo(
                    currentText()
                ) ?: return false

                onTextRestored(restored)
                true
            }

            CanvasMode.NODES -> {
                graphController.redo()
                true
            }
        }
    }
}
