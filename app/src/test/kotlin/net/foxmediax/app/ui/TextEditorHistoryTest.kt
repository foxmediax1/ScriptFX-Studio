
package net.foxmediax.app.ui

import androidx.compose.ui.text.input.TextFieldValue

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TextEditorHistoryTest {

    @Test
    fun `undo restores previous text`() {

        val history = TextEditorHistory()

        val first = TextFieldValue("Hello")
        val second = TextFieldValue("Hello world")

        history.checkpoint(first)

        val restored = history.undo(second)

        assertEquals(
            "Hello",
            restored?.text
        )

        assertTrue(history.canRedo.value)
        assertFalse(history.canUndo.value)
    }

    @Test
    fun `redo restores undone text`() {

        val history = TextEditorHistory()

        val first = TextFieldValue("Hello")
        val second = TextFieldValue("Hello world")

        history.checkpoint(first)

        val undone = history.undo(second)

        assertEquals("Hello", undone?.text)

        val redone = history.redo(undone!!)

        assertEquals(
            "Hello world",
            redone?.text
        )

        assertTrue(history.canUndo.value)
        assertFalse(history.canRedo.value)
    }

    @Test
    fun `new edit clears redo history`() {

        val history = TextEditorHistory()

        val first = TextFieldValue("A")
        val second = TextFieldValue("AB")
        val third = TextFieldValue("AC")

        history.checkpoint(first)

        val undone = history.undo(second)

        assertEquals("A", undone?.text)

        history.checkpoint(undone!!)

        assertFalse(history.canRedo.value)

        val result = history.undo(third)

        assertEquals("A", result?.text)
    }

    @Test
    fun `history respects maximum size`() {

        val history = TextEditorHistory(
            maxHistory = 3
        )

        history.checkpoint(TextFieldValue("A"))
        history.checkpoint(TextFieldValue("AB"))
        history.checkpoint(TextFieldValue("ABC"))
        history.checkpoint(TextFieldValue("ABCD"))

        assertEquals(
            3,
            history.undoCount
        )
    }

    @Test
    fun `reset clears undo and redo`() {

        val history = TextEditorHistory()

        history.checkpoint(
            TextFieldValue("Before")
        )

        history.reset()

        assertFalse(history.canUndo.value)
        assertFalse(history.canRedo.value)

        assertEquals(0, history.undoCount)
        assertEquals(0, history.redoCount)
    }
}
