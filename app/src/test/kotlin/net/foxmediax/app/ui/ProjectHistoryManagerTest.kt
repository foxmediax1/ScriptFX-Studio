
package net.foxmediax.app.ui

import androidx.compose.ui.text.input.TextFieldValue
import net.foxmediax.utils.nodes.NodeGraph
import net.foxmediax.utils.nodes.NodeKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import androidx.compose.runtime.mutableStateOf

class ProjectHistoryManagerTest {

    private fun graphWithText(value: String): NodeGraph {

        val graph = NodeGraph.defaultTemplate()

        graph.nodes.first {
            it.kind == NodeKind.PRINT
        }.params = mapOf(
            "text" to value
        )

        return graph
    }

    private fun nodeText(graph: NodeGraph): String? {
        return graph.nodes.first {
            it.kind == NodeKind.PRINT
        }.params["text"]
    }

    // =====================================================
    // TEXT UNDO
    // =====================================================

    @Test
    fun `undo is dispatched to active text editor`() {

        var currentText = TextFieldValue("After")

        val textHistory = TextEditorHistory()

        textHistory.checkpoint(
            TextFieldValue("Before")
        )

        val controller = NodeCanvasController()

        val manager = ProjectHistoryManager(
            activeMode = {
                CanvasMode.TEXT
            },
            textHistory = textHistory,
            graphController = controller,
            currentText = {
                currentText
            },
            onTextRestored = {
                currentText = it
            }
        )

        assertTrue(manager.undo())

        assertEquals(
            "Before",
            currentText.text
        )
    }

    // =====================================================
    // TEXT REDO
    // =====================================================

    @Test
    fun `redo is dispatched to active text editor`() {

        var currentText = TextFieldValue("After")

        val textHistory = TextEditorHistory()

        textHistory.checkpoint(
            TextFieldValue("Before")
        )

        val controller = NodeCanvasController()

        val manager = ProjectHistoryManager(
            activeMode = {
                CanvasMode.TEXT
            },
            textHistory = textHistory,
            graphController = controller,
            currentText = {
                currentText
            },
            onTextRestored = {
                currentText = it
            }
        )

        manager.undo()
        assertEquals("Before", currentText.text)

        assertTrue(manager.redo())

        assertEquals(
            "After",
            currentText.text
        )
    }

    // =====================================================
    // GRAPH UNDO
    // =====================================================

    @Test
    fun `undo is dispatched to active graph editor`() {

        var currentGraph = graphWithText("After")

        val controller = NodeCanvasController()

        controller.checkpoint(
            graphWithText("Before")
        )

        controller.undoAction = {
            controller.undoGraph(currentGraph)?.let {
                currentGraph = it
            }
        }

        controller.redoAction = {
            controller.redoGraph(currentGraph)?.let {
                currentGraph = it
            }
        }

        val manager = ProjectHistoryManager(
            activeMode = {
                CanvasMode.NODES
            },
            textHistory = TextEditorHistory(),
            graphController = controller,
            currentText = {
                TextFieldValue("")
            },
            onTextRestored = {}
        )

        assertTrue(manager.undo())

        assertEquals(
            "Before",
            nodeText(currentGraph)
        )
    }

    // =====================================================
    // GRAPH REDO
    // =====================================================

    @Test
    fun `redo is dispatched to active graph editor`() {

        var currentGraph = graphWithText("After")

        val controller = NodeCanvasController()

        controller.checkpoint(
            graphWithText("Before")
        )

        controller.undoAction = {
            controller.undoGraph(currentGraph)?.let {
                currentGraph = it
            }
        }

        controller.redoAction = {
            controller.redoGraph(currentGraph)?.let {
                currentGraph = it
            }
        }

        val manager = ProjectHistoryManager(
            activeMode = {
                CanvasMode.NODES
            },
            textHistory = TextEditorHistory(),
            graphController = controller,
            currentText = {
                TextFieldValue("")
            },
            onTextRestored = {}
        )

        manager.undo()

        assertTrue(manager.redo())

        assertEquals(
            "After",
            nodeText(currentGraph)
        )
    }

    // =====================================================
    // ACTIVE EDITOR
    // =====================================================

    @Test
    fun `history availability follows active editor`() {

        val mode = mutableStateOf(CanvasMode.TEXT)

        val textHistory = TextEditorHistory()

        textHistory.checkpoint(
            TextFieldValue("Before")
        )

        val controller = NodeCanvasController()

        val manager = ProjectHistoryManager(
            activeMode = {
                mode.value
            },
            textHistory = textHistory,
            graphController = controller,
            currentText = {
                TextFieldValue("Current")
            },
            onTextRestored = {}
        )

        assertTrue(manager.canUndo.value)

        // В графическом режиме у нас пока нет истории.
        mode.value = CanvasMode.NODES

        assertFalse(manager.canUndo.value)

        // Добавляем действие в графовую историю.
        controller.checkpoint(
            graphWithText("Before graph")
        )

        assertTrue(manager.canUndo.value)

        // Возвращаемся в текстовый режим.
        mode.value = CanvasMode.TEXT

        assertTrue(manager.canUndo.value)
    }

    // =====================================================
    // EMPTY HISTORY
    // =====================================================

    @Test
    fun `undo and redo return false when history is empty`() {

        val controller = NodeCanvasController()
        val history = TextEditorHistory()

        val manager = ProjectHistoryManager(
            activeMode = {
                CanvasMode.TEXT
            },
            textHistory = history,
            graphController = controller,
            currentText = {
                TextFieldValue("")
            },
            onTextRestored = {}
        )

        assertFalse(manager.undo())
        assertFalse(manager.redo())
    }
}
