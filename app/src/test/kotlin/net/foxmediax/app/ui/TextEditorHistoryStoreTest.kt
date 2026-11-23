
package net.foxmediax.app.ui

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

import java.io.File
import java.nio.file.Files

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class TextEditorHistoryStoreTest {

    private val temporaryDirectories = mutableListOf<File>()

    private fun tempDirectory(): File {

        val directory = Files
            .createTempDirectory("scriptfx-history-test-")
            .toFile()

        temporaryDirectories.add(directory)

        return directory
    }

    @AfterTest
    fun cleanup() {

        temporaryDirectories.forEach {
            it.deleteRecursively()
        }

        temporaryDirectories.clear()
    }

    private fun createProject(): File {

        val root = tempDirectory()

        File(
            root,
            "scenarios"
        ).mkdirs()

        return root
    }

    private fun createScenario(
        project: File,
        name: String,
        content: String
    ): File {

        val file = File(
            project,
            "scenarios/$name.sfxs"
        )

        file.parentFile.mkdirs()
        file.writeText(content)

        return file
    }

    @Test
    fun `history survives store recreation`() {

        val dataRoot = tempDirectory()
        val project = createProject()

        val baseline = """print "Hello""""

        val file = createScenario(
            project,
            "main",
            baseline
        )

        val history = TextEditorHistory()

        val previous = TextFieldValue(
            text = baseline,
            selection = TextRange(5)
        )

        val current = TextFieldValue(
            text = """print "Hello world"""",
            selection = TextRange(17)
        )

        history.checkpoint(previous)

        val firstStore = TextEditorHistoryStore(dataRoot)

        firstStore.save(
            projectRoot = project,
            scenarioFile = file,
            baselineText = baseline,
            snapshot = history.snapshot(current)
        )

        // Новый объект имитирует новый запуск Studio.
        val secondStore = TextEditorHistoryStore(dataRoot)

        val restored = secondStore.load(
            projectRoot = project,
            scenarioFile = file,
            expectedBaselineText = baseline
        )

        assertNotNull(restored)

        val restoredHistory = TextEditorHistory()

        val restoredCurrent = restoredHistory.restore(
            restored.snapshot
        )

        assertEquals(
            current.text,
            restoredCurrent.text
        )

        assertEquals(
            current.selection,
            restoredCurrent.selection
        )

        val undone = restoredHistory.undo(
            restoredCurrent
        )

        assertEquals(
            baseline,
            undone?.text
        )
    }

    @Test
    fun `restored redo history is available`() {

        val dataRoot = tempDirectory()
        val project = createProject()

        val baseline = "A"

        val file = createScenario(
            project,
            "main",
            baseline
        )

        val history = TextEditorHistory()

        history.checkpoint(
            TextFieldValue("A")
        )

        val current = TextFieldValue("AB")

        val afterUndo = history.undo(current)

        assertEquals("A", afterUndo?.text)

        val store = TextEditorHistoryStore(dataRoot)

        store.save(
            project,
            file,
            baseline,
            history.snapshot(afterUndo!!)
        )

        val restored = store.load(
            project,
            file,
            baseline
        )

        assertNotNull(restored)

        val restoredHistory = TextEditorHistory()

        val restoredCurrent = restoredHistory.restore(
            restored.snapshot
        )

        val redone = restoredHistory.redo(
            restoredCurrent
        )

        assertEquals(
            "AB",
            redone?.text
        )
    }

    @Test
    fun `changed baseline invalidates old history`() {

        val dataRoot = tempDirectory()
        val project = createProject()

        val file = createScenario(
            project,
            "main",
            "Original"
        )

        val history = TextEditorHistory()

        history.checkpoint(
            TextFieldValue("Original")
        )

        val store = TextEditorHistoryStore(dataRoot)

        store.save(
            projectRoot = project,
            scenarioFile = file,
            baselineText = "Original",
            snapshot = history.snapshot(
                TextFieldValue("Draft")
            )
        )

        val restored = store.load(
            projectRoot = project,
            scenarioFile = file,
            expectedBaselineText = "Externally changed"
        )

        assertNull(restored)
    }

    @Test
    fun `histories of different scenarios are independent`() {

        val dataRoot = tempDirectory()
        val project = createProject()

        val main = createScenario(
            project,
            "main",
            "Main"
        )

        val reward = createScenario(
            project,
            "reward",
            "Reward"
        )

        val store = TextEditorHistoryStore(dataRoot)

        val mainHistory = TextEditorHistory()
        mainHistory.checkpoint(
            TextFieldValue("Main")
        )

        val rewardHistory = TextEditorHistory()
        rewardHistory.checkpoint(
            TextFieldValue("Reward")
        )

        store.save(
            project,
            main,
            "Main",
            mainHistory.snapshot(
                TextFieldValue("Main draft")
            )
        )

        store.save(
            project,
            reward,
            "Reward",
            rewardHistory.snapshot(
                TextFieldValue("Reward draft")
            )
        )

        val restoredMain = store.load(
            project,
            main,
            "Main"
        )

        val restoredReward = store.load(
            project,
            reward,
            "Reward"
        )

        assertNotNull(restoredMain)
        assertNotNull(restoredReward)

        assertEquals(
            "Main draft",
            restoredMain.snapshot.current.text
        )

        assertEquals(
            "Reward draft",
            restoredReward.snapshot.current.text
        )
    }

    @Test
    fun `clear removes history for selected scenario only`() {

        val dataRoot = tempDirectory()
        val project = createProject()

        val main = createScenario(project, "main", "A")
        val reward = createScenario(project, "reward", "B")

        val store = TextEditorHistoryStore(dataRoot)

        val history = TextEditorHistory()
        history.checkpoint(TextFieldValue("A"))

        store.save(
            project,
            main,
            "A",
            history.snapshot(TextFieldValue("A1"))
        )

        store.save(
            project,
            reward,
            "B",
            history.snapshot(TextFieldValue("B1"))
        )

        store.clear(
            project,
            main
        )

        assertNull(
            store.load(project, main, "A")
        )

        assertNotNull(
            store.load(project, reward, "B")
        )
    }
}
