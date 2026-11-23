
package net.foxmediax.app.project

import net.foxmediax.utils.nodes.NodeGraph
import net.foxmediax.utils.nodes.NodeKind

import java.io.File
import java.nio.file.Files

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GraphRecoveryStoreTest {

    private val temporaryDirectories = mutableListOf<File>()

    private fun tempDirectory(): File {

        val directory = Files
            .createTempDirectory("scriptfx-graph-recovery-")
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
        name: String,
        content: String = "print \"Hello\""
    ): File {

        val file = File(
            project,
            "scenarios/$name.sfxs"
        )

        file.parentFile.mkdirs()
        file.writeText(content)

        return file
    }

    private fun createChangedGraph(): NodeGraph {

        val graph = NodeGraph.defaultTemplate()

        val printNode = graph.nodes.first {
            it.kind == NodeKind.PRINT
        }

        printNode.params = mapOf(
            "text" to "Recovered after crash"
        )

        return graph
    }

    // =====================================================
    // SAVE AND LOAD
    // =====================================================

    @Test
    fun `saved graph can be restored`() {

        val dataRoot = tempDirectory()
        val project = createProject()
        val scenario = createScenario(project, "main")

        val store = GraphRecoveryStore(dataRoot)

        val fingerprint = store.fingerprint(
            project,
            scenario
        )

        val graph = createChangedGraph()

        store.save(
            projectRoot = project,
            scenarioFile = scenario,
            baselineFingerprint = fingerprint,
            graphJson = graph.toJson()
        )

        val restored = store.load(
            projectRoot = project,
            scenarioFile = scenario,
            expectedBaselineFingerprint = fingerprint
        )

        assertNotNull(restored)

        assertEquals(
            graph.toJson(),
            restored.graph.toJson()
        )

        assertTrue(
            restored.savedAtMillis > 0
        )
    }

    // =====================================================
    // SOURCE CHANGE
    // =====================================================

    @Test
    fun `changed source invalidates recovery`() {

        val dataRoot = tempDirectory()
        val project = createProject()

        val scenario = createScenario(
            project,
            "main",
            "Original script"
        )

        val store = GraphRecoveryStore(dataRoot)

        val originalFingerprint = store.fingerprint(
            project,
            scenario
        )

        store.save(
            projectRoot = project,
            scenarioFile = scenario,
            baselineFingerprint = originalFingerprint,
            graphJson = createChangedGraph().toJson()
        )

        scenario.writeText(
            "Externally modified script"
        )

        val newFingerprint = store.fingerprint(
            project,
            scenario
        )

        val restored = store.load(
            projectRoot = project,
            scenarioFile = scenario,
            expectedBaselineFingerprint = newFingerprint
        )

        assertNull(restored)
    }

    // =====================================================
    // SIDECAR CHANGE
    // =====================================================

    @Test
    fun `changed graph sidecar invalidates recovery`() {

        val dataRoot = tempDirectory()
        val project = createProject()

        val scenario = createScenario(
            project,
            "main"
        )

        val sidecar = File(
            scenario.parentFile,
            "main.graph.json"
        )

        val store = GraphRecoveryStore(dataRoot)

        val originalFingerprint = store.fingerprint(
            project,
            scenario
        )

        store.save(
            projectRoot = project,
            scenarioFile = scenario,
            baselineFingerprint = originalFingerprint,
            graphJson = createChangedGraph().toJson()
        )

        sidecar.writeText(
            NodeGraph.defaultTemplate().toJson()
        )

        val newFingerprint = store.fingerprint(
            project,
            scenario
        )

        assertNull(
            store.load(
                projectRoot = project,
                scenarioFile = scenario,
                expectedBaselineFingerprint = newFingerprint
            )
        )
    }

    // =====================================================
    // DIFFERENT SCENARIOS
    // =====================================================

    @Test
    fun `recovery data of different scenarios is independent`() {

        val dataRoot = tempDirectory()
        val project = createProject()

        val main = createScenario(project, "main")
        val reward = createScenario(project, "reward")

        val store = GraphRecoveryStore(dataRoot)

        val mainGraph = createChangedGraph()

        val rewardGraph = NodeGraph.defaultTemplate().apply {
            nodes.first {
                it.kind == NodeKind.PRINT
            }.params = mapOf(
                "text" to "Reward scenario"
            )
        }

        val mainFingerprint = store.fingerprint(
            project,
            main
        )

        val rewardFingerprint = store.fingerprint(
            project,
            reward
        )

        store.save(
            project,
            main,
            mainFingerprint,
            mainGraph.toJson()
        )

        store.save(
            project,
            reward,
            rewardFingerprint,
            rewardGraph.toJson()
        )

        val restoredMain = store.load(
            project,
            main,
            mainFingerprint
        )

        val restoredReward = store.load(
            project,
            reward,
            rewardFingerprint
        )

        assertNotNull(restoredMain)
        assertNotNull(restoredReward)

        assertEquals(
            "Recovered after crash",
            restoredMain.graph.nodes.first {
                it.kind == NodeKind.PRINT
            }.params["text"]
        )

        assertEquals(
            "Reward scenario",
            restoredReward.graph.nodes.first {
                it.kind == NodeKind.PRINT
            }.params["text"]
        )
    }

    // =====================================================
    // CLEAR
    // =====================================================

    @Test
    fun `clearing recovery affects only selected scenario`() {

        val dataRoot = tempDirectory()
        val project = createProject()

        val main = createScenario(project, "main")
        val reward = createScenario(project, "reward")

        val store = GraphRecoveryStore(dataRoot)

        val graph = createChangedGraph()

        val mainFingerprint = store.fingerprint(
            project,
            main
        )

        val rewardFingerprint = store.fingerprint(
            project,
            reward
        )

        store.save(
            project,
            main,
            mainFingerprint,
            graph.toJson()
        )

        store.save(
            project,
            reward,
            rewardFingerprint,
            graph.toJson()
        )

        store.clear(
            project,
            main
        )

        assertNull(
            store.load(
                project,
                main,
                mainFingerprint
            )
        )

        assertNotNull(
            store.load(
                project,
                reward,
                rewardFingerprint
            )
        )
    }

    // =====================================================
    // PATH PROTECTION
    // =====================================================

    @Test
    fun `scenario outside project is rejected`() {

        val dataRoot = tempDirectory()
        val project = createProject()

        val outsideScenario = File(
            tempDirectory(),
            "outside.sfxs"
        ).apply {
            writeText("print \"Outside\"")
        }

        val store = GraphRecoveryStore(dataRoot)

        assertFailsWith<IllegalArgumentException> {
            store.fingerprint(
                project,
                outsideScenario
            )
        }
    }
}
