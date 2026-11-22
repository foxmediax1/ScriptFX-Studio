
package net.foxmediax.app.project

import net.foxmediax.utils.nodes.GraphEdge
import net.foxmediax.utils.nodes.GraphNode
import net.foxmediax.utils.nodes.NodeGraph
import net.foxmediax.utils.nodes.NodeKind
import net.foxmediax.utils.nodes.ScriptNodeSync

import java.io.File
import java.nio.file.Files

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ExportServiceTest {

    private val temporaryDirectories = mutableListOf<File>()

    private fun createTempDirectory(): File {
        val directory = Files
            .createTempDirectory("scriptfx-test-")
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
        val root = createTempDirectory()

        File(
            root,
            "scriptfx.project.json"
        ).writeText(
            """{"name":"Test Project"}"""
        )

        File(
            root,
            "scenarios"
        ).mkdirs()

        return root
    }

    private fun createMinecraftDirectory(): File {
        val root = createTempDirectory()

        // validateTarget распознаёт каталог Minecraft
        // по наличию config или mods.
        File(
            root,
            "config"
        ).mkdirs()

        return root
    }

    private fun createCallGraph(
        target: String
    ): NodeGraph {
        return NodeGraph(
            nodes = mutableListOf(
                GraphNode(
                    id = "start",
                    kind = NodeKind.START,
                    title = "Начало"
                ),
                GraphNode(
                    id = "call",
                    kind = NodeKind.CALL,
                    title = "Запустить сценарий",
                    params = mapOf(
                        "target" to target
                    )
                ),
                GraphNode(
                    id = "end",
                    kind = NodeKind.END,
                    title = "Конец"
                )
            ),
            edges = mutableListOf(
                GraphEdge(
                    from = "start",
                    fromPort = "out",
                    to = "call",
                    toPort = "in"
                ),
                GraphEdge(
                    from = "call",
                    fromPort = "out",
                    to = "end",
                    toPort = "in"
                )
            )
        )
    }

    private fun writeScenario(
        projectRoot: File,
        relativePath: String,
        graph: NodeGraph
    ): File {
        val file = File(
            projectRoot,
            "scenarios/$relativePath"
        )

        file.parentFile.mkdirs()

        file.writeText(
            ScriptNodeSync.graphToText(
                graph = graph,
                includeMeta = true
            )
        )

        val sidecar = File(
            file.parentFile,
            "${file.nameWithoutExtension}.graph.json"
        )

        sidecar.writeText(
            graph.toJson()
        )

        return file
    }

    @Test
    fun `default scenario exports successfully`() {
        val project = createProject()
        val minecraft = createMinecraftDirectory()

        writeScenario(
            projectRoot = project,
            relativePath = "main.sfxs",
            graph = NodeGraph.defaultTemplate()
        )

        val result = ExportService.export(
            projectRoot = project,
            targetDir = minecraft
        )

        assertTrue(
            result.success,
            result.message
        )

        assertTrue(result.filesCopied >= 2)

        assertNotNull(result.validation)
        assertFalse(result.validation.hasErrors)
    }

    @Test
    fun `scenarios are exported into runtime scripts directory`() {
        val project = createProject()
        val minecraft = createMinecraftDirectory()

        writeScenario(
            projectRoot = project,
            relativePath = "main.sfxs",
            graph = NodeGraph.defaultTemplate()
        )

        val asset = File(
            project,
            "assets/example.txt"
        )

        asset.parentFile.mkdirs()
        asset.writeText("test asset")

        val result = ExportService.export(
            projectRoot = project,
            targetDir = minecraft
        )

        assertTrue(
            result.success,
            result.message
        )

        val destination = File(
            minecraft,
            "config/scriptfx/projects/${project.name}"
        )

        assertTrue(
            File(
                destination,
                "scripts/main.sfxs"
            ).isFile
        )

        assertFalse(
            File(
                destination,
                "scenarios/main.sfxs"
            ).exists()
        )

        assertFalse(
            File(
                destination,
                "scenarios/main.graph.json"
            ).exists()
        )

        assertTrue(
            File(
                destination,
                "assets/example.txt"
            ).isFile
        )

        assertTrue(
            File(
                destination,
                "scriptfx.project.json"
            ).isFile
        )
    }

    @Test
    fun `missing CALL target blocks export`() {
        val project = createProject()
        val minecraft = createMinecraftDirectory()

        writeScenario(
            projectRoot = project,
            relativePath = "main.sfxs",
            graph = createCallGraph("missing_reward")
        )

        val result = ExportService.export(
            projectRoot = project,
            targetDir = minecraft
        )

        assertFalse(result.success)

        assertNotNull(result.validation)
        assertTrue(result.validation.hasErrors)

        assertTrue(
            result.validation.issues.any {
                it.issue.message.contains(
                    "отсутствующий сценарий",
                    ignoreCase = true
                )
            }
        )

        assertFalse(
            File(
                minecraft,
                "config/scriptfx/projects/${project.name}/scripts/main.sfxs"
            ).exists()
        )
    }

    @Test
    fun `existing local CALL target passes validation`() {
        val project = createProject()

        writeScenario(
            projectRoot = project,
            relativePath = "main.sfxs",
            graph = createCallGraph("reward")
        )

        writeScenario(
            projectRoot = project,
            relativePath = "reward.sfxs",
            graph = NodeGraph.defaultTemplate()
        )

        val report = ExportService.validateProject(project)

        assertFalse(
            report.hasErrors,
            report.issues.joinToString {
                it.issue.message
            }
        )
    }

    @Test
    fun `duplicate scenario names are rejected`() {
        val project = createProject()

        writeScenario(
            projectRoot = project,
            relativePath = "one/reward.sfxs",
            graph = NodeGraph.defaultTemplate()
        )

        writeScenario(
            projectRoot = project,
            relativePath = "two/reward.sfxs",
            graph = NodeGraph.defaultTemplate()
        )

        val report = ExportService.validateProject(project)

        assertTrue(report.hasErrors)

        assertTrue(
            report.issues.any {
                it.issue.message.contains(
                    "повторяется",
                    ignoreCase = true
                )
            }
        )
    }

    @Test
    fun `recursive CALL cycle is detected`() {
        val project = createProject()

        writeScenario(
            projectRoot = project,
            relativePath = "main.sfxs",
            graph = createCallGraph("reward")
        )

        writeScenario(
            projectRoot = project,
            relativePath = "reward.sfxs",
            graph = createCallGraph("main")
        )

        val report = ExportService.validateProject(project)

        assertTrue(report.hasErrors)

        assertTrue(
            report.issues.any {
                it.issue.message.contains(
                    "циклический вызов сценариев",
                    ignoreCase = true
                )
            }
        )
    }

    @Test
    fun `CALL with directory path is rejected`() {
        val project = createProject()

        writeScenario(
            projectRoot = project,
            relativePath = "main.sfxs",
            graph = createCallGraph("folder/reward")
        )

        val report = ExportService.validateProject(project)

        assertTrue(report.hasErrors)

        assertTrue(
            report.issues.any {
                it.issue.message.contains(
                    "имя сценария, а не путь",
                    ignoreCase = true
                )
            }
        )
    }

    @Test
    fun `CALL can resolve scenario from another installed project`() {
        val project = createProject()
        val minecraft = createMinecraftDirectory()

        writeScenario(
            projectRoot = project,
            relativePath = "main.sfxs",
            graph = createCallGraph("external_reward")
        )

        val externalScript = File(
            minecraft,
            "config/scriptfx/projects/OtherProject/scripts/external_reward.sfxs"
        )

        externalScript.parentFile.mkdirs()
        externalScript.writeText(
            "print \"External reward\""
        )

        val report = ExportService.validateProject(
            projectRoot = project,
            targetDir = minecraft
        )

        assertFalse(
            report.hasErrors,
            report.issues.joinToString {
                it.issue.message
            }
        )
    }

    @Test
    fun `scenario count includes nested scenario files`() {
        val project = createProject()

        writeScenario(
            projectRoot = project,
            relativePath = "main.sfxs",
            graph = NodeGraph.defaultTemplate()
        )

        writeScenario(
            projectRoot = project,
            relativePath = "nested/reward.sfxs",
            graph = NodeGraph.defaultTemplate()
        )

        val report = ExportService.validateProject(project)

        assertEquals(
            2,
            report.scenariosChecked
        )
    }
}
