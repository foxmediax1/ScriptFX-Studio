
package net.foxmediax.app.project

import net.foxmediax.utils.nodes.NodeGraph
import net.foxmediax.utils.nodes.NodeGraphHistory
import net.foxmediax.utils.nodes.NodeKind

import java.io.File
import java.nio.file.Files

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GraphHistoryStoreTest {

    private val temporaryDirectories = mutableListOf<File>()

    private fun tempDirectory(): File {
        val directory = Files
            .createTempDirectory("scriptfx-graph-history-")
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

        File(root, "scenarios").mkdirs()

        return root
    }

    private fun createScenario(
        project: File,
        name: String
    ): File {
        val file = File(
            project,
            "scenarios/$name.sfxs"
        )

        file.parentFile.mkdirs()
        file.writeText("print \"$name\"")

        return file
    }

    private fun graphWithText(text: String): NodeGraph {
        val graph = NodeGraph.defaultTemplate()

        graph.nodes.first {
            it.kind == NodeKind.PRINT
        }.params = mapOf(
            "text" to text
        )

        return graph
    }

    private fun nodeText(graph: NodeGraph): String? {
        return graph.nodes.first {
            it.kind == NodeKind.PRINT
        }.params["text"]
    }

    private fun createHistory(): NodeGraphHistory {
        return NodeGraphHistory().apply {
            checkpoint(graphWithText("Before"))
            checkpoint(graphWithText("Middle"))
        }
    }

    private val baseline = "a".repeat(64)

    // =====================================================
    // SAVE AND RESTORE
    // =====================================================

    @Test
    fun `history survives store recreation`() {

        val dataRoot = tempDirectory()
        val project = createProject()
        val scenario = createScenario(project, "main")

        val currentGraph = graphWithText("Current")
        val history = createHistory()

        val firstStore = GraphHistoryStore(dataRoot)

        firstStore.save(
            projectRoot = project,
            scenarioFile = scenario,
            baselineFingerprint = baseline,
            currentGraphJson = currentGraph.toJson(),
            snapshot = history.snapshot()
        )

        // Имитируем повторный запуск приложения.
        val secondStore = GraphHistoryStore(dataRoot)

        val restored = secondStore.load(
            projectRoot = project,
            scenarioFile = scenario,
            expectedBaselineFingerprint = baseline,
            expectedGraphJson = currentGraph.toJson()
        )

        assertNotNull(restored)

        val restoredHistory = NodeGraphHistory()

        assertTrue(
            restoredHistory.restore(restored)
        )

        val undone = restoredHistory.undo(currentGraph)

        assertEquals(
            "Middle",
            nodeText(undone!!)
        )

        val redone = restoredHistory.redo(undone)

        assertEquals(
            "Current",
            nodeText(redone!!)
        )
    }

    // =====================================================
    // REDO RESTORATION
    // =====================================================

    @Test
    fun `redo history survives store recreation`() {

        val dataRoot = tempDirectory()
        val project = createProject()
        val scenario = createScenario(project, "main")

        val currentGraph = graphWithText("Current")
        val history = createHistory()

        // Выполняем Undo до сохранения.
        val undone = history.undo(currentGraph)!!

        val store = GraphHistoryStore(dataRoot)

        store.save(
            projectRoot = project,
            scenarioFile = scenario,
            baselineFingerprint = baseline,
            currentGraphJson = undone.toJson(),
            snapshot = history.snapshot()
        )

        val restored = store.load(
            projectRoot = project,
            scenarioFile = scenario,
            expectedBaselineFingerprint = baseline,
            expectedGraphJson = undone.toJson()
        )

        assertNotNull(restored)

        val restoredHistory = NodeGraphHistory()

        assertTrue(
            restoredHistory.restore(restored)
        )

        val redone = restoredHistory.redo(undone)

        assertEquals(
            "Current",
            nodeText(redone!!)
        )
    }

    // =====================================================
    // BASELINE PROTECTION
    // =====================================================

    @Test
    fun `changed baseline invalidates history`() {

        val dataRoot = tempDirectory()
        val project = createProject()
        val scenario = createScenario(project, "main")

        val graph = graphWithText("Current")
        val store = GraphHistoryStore(dataRoot)

        store.save(
            project,
            scenario,
            baseline,
            graph.toJson(),
            createHistory().snapshot()
        )

        val restored = store.load(
            projectRoot = project,
            scenarioFile = scenario,
            expectedBaselineFingerprint = "b".repeat(64),
            expectedGraphJson = graph.toJson()
        )

        assertNull(restored)
    }

    // =====================================================
    // GRAPH PROTECTION
    // =====================================================

    @Test
    fun `changed current graph invalidates history`() {

        val dataRoot = tempDirectory()
        val project = createProject()
        val scenario = createScenario(project, "main")

        val graph = graphWithText("Current")
        val store = GraphHistoryStore(dataRoot)

        store.save(
            project,
            scenario,
            baseline,
            graph.toJson(),
            createHistory().snapshot()
        )

        val changedGraph = graphWithText("Externally changed")

        val restored = store.load(
            projectRoot = project,
            scenarioFile = scenario,
            expectedBaselineFingerprint = baseline,
            expectedGraphJson = changedGraph.toJson()
        )

        assertNull(restored)
    }

    // =====================================================
    // SCENARIO ISOLATION
    // =====================================================

    @Test
    fun `histories of different scenarios are independent`() {

        val dataRoot = tempDirectory()
        val project = createProject()

        val main = createScenario(project, "main")
        val reward = createScenario(project, "reward")

        val store = GraphHistoryStore(dataRoot)

        val mainGraph = graphWithText("Main")
        val rewardGraph = graphWithText("Reward")

        val mainHistory = createHistory()

        val rewardHistory = NodeGraphHistory().apply {
            checkpoint(graphWithText("Reward before"))
        }

        store.save(
            project,
            main,
            baseline,
            mainGraph.toJson(),
            mainHistory.snapshot()
        )

        store.save(
            project,
            reward,
            baseline,
            rewardGraph.toJson(),
            rewardHistory.snapshot()
        )

        val restoredMain = store.load(
            project,
            main,
            baseline,
            mainGraph.toJson()
        )

        val restoredReward = store.load(
            project,
            reward,
            baseline,
            rewardGraph.toJson()
        )

        assertNotNull(restoredMain)
        assertNotNull(restoredReward)

        assertEquals(
            2,
            restoredMain.undo.size
        )

        assertEquals(
            1,
            restoredReward.undo.size
        )
    }

    // =====================================================
    // CLEAR
    // =====================================================

    @Test
    fun `clear removes only selected scenario history`() {

        val dataRoot = tempDirectory()
        val project = createProject()

        val main = createScenario(project, "main")
        val reward = createScenario(project, "reward")

        val store = GraphHistoryStore(dataRoot)

        val mainGraph = graphWithText("Main")
        val rewardGraph = graphWithText("Reward")
        val history = createHistory().snapshot()

        store.save(
            project,
            main,
            baseline,
            mainGraph.toJson(),
            history
        )

        store.save(
            project,
            reward,
            baseline,
            rewardGraph.toJson(),
            history
        )

        store.clear(
            project,
            main
        )

        assertNull(
            store.load(
                project,
                main,
                baseline,
                mainGraph.toJson()
            )
        )

        assertNotNull(
            store.load(
                project,
                reward,
                baseline,
                rewardGraph.toJson()
            )
        )
    }

    // =====================================================
    // EMPTY HISTORY
    // =====================================================

    @Test
    fun `empty history removes stored file`() {

        val dataRoot = tempDirectory()
        val project = createProject()
        val scenario = createScenario(project, "main")

        val store = GraphHistoryStore(dataRoot)
        val graph = graphWithText("Current")

        store.save(
            project,
            scenario,
            baseline,
            graph.toJson(),
            createHistory().snapshot()
        )

        store.save(
            project,
            scenario,
            baseline,
            graph.toJson(),
            NodeGraphHistory.Snapshot(
                undo = emptyList(),
                redo = emptyList()
            )
        )

        assertNull(
            store.load(
                project,
                scenario,
                baseline,
                graph.toJson()
            )
        )
    }
}
