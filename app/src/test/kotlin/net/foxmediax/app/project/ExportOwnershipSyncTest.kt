
package net.foxmediax.app.project

import net.foxmediax.utils.nodes.NodeGraph
import net.foxmediax.utils.nodes.ScriptNodeSync

import java.io.File
import java.nio.file.Files

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExportOwnershipSyncTest {

    private val tempDirectories = mutableListOf<File>()

    private fun tempDirectory(): File {
        val directory = Files
            .createTempDirectory("scriptfx-export-test-")
            .toFile()

        tempDirectories += directory

        return directory
    }

    @AfterTest
    fun cleanup() {
        tempDirectories.forEach {
            it.deleteRecursively()
        }

        tempDirectories.clear()
    }

    private fun createProject(): File {
        val root = tempDirectory()

        File(
            root,
            "scriptfx.project.json"
        ).writeText(
            """{"name":"Export Test"}"""
        )

        File(
            root,
            "scenarios"
        ).mkdirs()

        return root
    }

    private fun createMinecraft(): File {
        val root = tempDirectory()

        File(
            root,
            "config"
        ).mkdirs()

        return root
    }

    private fun addScenario(
        project: File,
        name: String
    ): File {

        val file = File(
            project,
            "scenarios/$name"
        )

        file.parentFile.mkdirs()

        val graph = NodeGraph.defaultTemplate()

        file.writeText(
            ScriptNodeSync.graphToText(
                graph = graph,
                includeMeta = true
            )
        )

        File(
            file.parentFile,
            "${file.nameWithoutExtension}.graph.json"
        ).writeText(
            graph.toJson()
        )

        return file
    }

    private fun destination(
        project: File,
        minecraft: File
    ): File {
        return File(
            minecraft,
            "config/scriptfx/projects/${project.name}"
        )
    }

    @Test
    fun `removed source scenario is removed from Minecraft`() {
        val project = createProject()
        val minecraft = createMinecraft()

        addScenario(project, "main.sfxs")
        addScenario(project, "reward.sfxs")

        val first = ExportService.export(
            project,
            minecraft
        )

        assertTrue(first.success, first.message)

        val reward = File(
            destination(project, minecraft),
            "scripts/reward.sfxs"
        )

        assertTrue(reward.isFile)

        File(
            project,
            "scenarios/reward.sfxs"
        ).delete()

        File(
            project,
            "scenarios/reward.graph.json"
        ).delete()

        val second = ExportService.export(
            project,
            minecraft
        )

        assertTrue(second.success, second.message)

        assertFalse(
            reward.exists(),
            "Удалённый из проекта сценарий должен быть очищен."
        )

        assertTrue(
            File(
                destination(project, minecraft),
                "scripts/main.sfxs"
            ).isFile
        )

        assertTrue(
            second.message.contains(
                "Удалено устаревших сценариев: 1"
            )
        )
    }

    @Test
    fun `manually modified stale scenario is preserved`() {
        val project = createProject()
        val minecraft = createMinecraft()

        addScenario(project, "main.sfxs")
        addScenario(project, "reward.sfxs")

        val first = ExportService.export(
            project,
            minecraft
        )

        assertTrue(first.success, first.message)

        val reward = File(
            destination(project, minecraft),
            "scripts/reward.sfxs"
        )

        val manualContent = """
            print "This file was modified manually"
            stopscript
        """.trimIndent()

        reward.writeText(manualContent)

        File(project, "scenarios/reward.sfxs").delete()
        File(project, "scenarios/reward.graph.json").delete()

        val second = ExportService.export(
            project,
            minecraft
        )

        assertTrue(second.success, second.message)

        assertTrue(
            reward.isFile,
            "Изменённый вручную файл должен сохраниться."
        )

        assertEquals(
            manualContent,
            reward.readText()
        )

        assertTrue(
            second.message.contains(
                "Оставлено без изменений файлов: 1"
            )
        )
    }

    @Test
    fun `untracked legacy script is preserved`() {
        val project = createProject()
        val minecraft = createMinecraft()

        addScenario(project, "main.sfxs")
        addScenario(project, "reward.sfxs")

        val projectDestination = destination(
            project,
            minecraft
        )

        val legacyScript = File(
            projectDestination,
            "scripts/legacy.sfxs"
        )

        legacyScript.parentFile.mkdirs()
        legacyScript.writeText(
            "print \"Legacy user script\""
        )

        val first = ExportService.export(
            project,
            minecraft
        )

        assertTrue(first.success, first.message)

        File(project, "scenarios/reward.sfxs").delete()
        File(project, "scenarios/reward.graph.json").delete()

        val second = ExportService.export(
            project,
            minecraft
        )

        assertTrue(second.success, second.message)

        assertTrue(
            legacyScript.isFile,
            "Studio не должна удалять файл, которого нет в её манифесте."
        )
    }

    @Test
    fun `ownership manifest is created after successful export`() {
        val project = createProject()
        val minecraft = createMinecraft()

        addScenario(project, "main.sfxs")

        val result = ExportService.export(
            project,
            minecraft
        )

        assertTrue(result.success, result.message)

        val manifest = File(
            destination(project, minecraft),
            ".scriptfx-studio-export-manifest.json"
        )

        assertTrue(manifest.isFile)

        val content = manifest.readText()

        assertTrue(
            content.contains("scriptfx-studio-export-manifest")
        )

        assertTrue(
            content.contains("scripts/main.sfxs")
        )

        assertTrue(
            content.contains("managedScripts")
        )
    }

    @Test
    fun `corrupt ownership manifest blocks export`() {
        val project = createProject()
        val minecraft = createMinecraft()

        addScenario(project, "main.sfxs")

        val projectDestination = destination(
            project,
            minecraft
        )

        projectDestination.mkdirs()

        File(
            projectDestination,
            ".scriptfx-studio-export-manifest.json"
        ).writeText(
            """{"format":"unknown","version":999}"""
        )

        val result = ExportService.export(
            project,
            minecraft
        )

        assertFalse(result.success)

        assertTrue(
            result.message.contains(
                "манифест",
                ignoreCase = true
            )
        )
    }
}
