
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

class ExportPreviewServiceTest {

    private val temporaryDirectories = mutableListOf<File>()

    private fun tempDirectory(): File {

        val directory = Files
            .createTempDirectory("scriptfx-preview-test-")
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
            "scriptfx.project.json"
        ).writeText(
            """{"name":"Preview Test"}"""
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

    // =====================================================
    // ADD
    // =====================================================

    @Test
    fun `new scenario appears as ADD`() {

        val project = createProject()
        val minecraft = createMinecraft()

        addScenario(
            project,
            "main.sfxs"
        )

        val plan = ExportPreviewService.createPlan(
            project,
            minecraft
        )

        assertTrue(plan.canExport)

        assertEquals(
            ExportPreviewService.Action.ADD,
            plan.entries.single {
                it.path == "scripts/main.sfxs"
            }.action
        )
    }

    // =====================================================
    // UNCHANGED
    // =====================================================

    @Test
    fun `second export appears as UNCHANGED`() {

        val project = createProject()
        val minecraft = createMinecraft()

        addScenario(
            project,
            "main.sfxs"
        )

        val first = ExportService.export(
            project,
            minecraft
        )

        assertTrue(first.success, first.message)

        val plan = ExportPreviewService.createPlan(
            project,
            minecraft
        )

        assertTrue(plan.canExport)

        assertEquals(
            ExportPreviewService.Action.UNCHANGED,
            plan.entries.single {
                it.path == "scripts/main.sfxs"
            }.action
        )
    }

    // =====================================================
    // UPDATE
    // =====================================================

    @Test
    fun `source modification appears as UPDATE`() {

        val project = createProject()
        val minecraft = createMinecraft()

        val source = addScenario(
            project,
            "main.sfxs"
        )

        val first = ExportService.export(
            project,
            minecraft
        )

        assertTrue(first.success, first.message)

        source.writeText(
            source.readText() + "\n"
        )

        val plan = ExportPreviewService.createPlan(
            project,
            minecraft
        )

        assertTrue(plan.canExport)

        assertEquals(
            ExportPreviewService.Action.UPDATE,
            plan.entries.single {
                it.path == "scripts/main.sfxs"
            }.action
        )

        val second = ExportService.export(
            project,
            minecraft
        )

        assertTrue(second.success, second.message)
    }

    // =====================================================
    // MANUAL EDIT PROTECTION
    // =====================================================

    @Test
    fun `manually modified current scenario blocks export`() {

        val project = createProject()
        val minecraft = createMinecraft()

        addScenario(
            project,
            "main.sfxs"
        )

        val first = ExportService.export(
            project,
            minecraft
        )

        assertTrue(first.success, first.message)

        val destinationFile = File(
            destination(project, minecraft),
            "scripts/main.sfxs"
        )

        val manualContent = """
            print "Manual edit"
            stopscript
        """.trimIndent()

        destinationFile.writeText(manualContent)

        val plan = ExportPreviewService.createPlan(
            project,
            minecraft
        )

        assertFalse(plan.canExport)

        assertEquals(
            1,
            plan.conflicts
        )

        assertEquals(
            ExportPreviewService.Action.CONFLICT,
            plan.entries.single {
                it.path == "scripts/main.sfxs"
            }.action
        )

        // Проверяем также прямой вызов ExportService.
        val second = ExportService.export(
            project,
            minecraft
        )

        assertFalse(second.success)

        assertEquals(
            manualContent,
            destinationFile.readText()
        )
    }

    // =====================================================
    // PRESERVE
    // =====================================================

    @Test
    fun `manually modified stale scenario appears as PRESERVE`() {

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

        reward.writeText(
            "print \"User-owned reward\""
        )

        File(
            project,
            "scenarios/reward.sfxs"
        ).delete()

        File(
            project,
            "scenarios/reward.graph.json"
        ).delete()

        val plan = ExportPreviewService.createPlan(
            project,
            minecraft
        )

        assertTrue(plan.canExport)

        assertEquals(
            ExportPreviewService.Action.PRESERVE,
            plan.entries.single {
                it.path == "scripts/reward.sfxs"
            }.action
        )
    }

    // =====================================================
    // REMOVE
    // =====================================================

    @Test
    fun `unchanged stale scenario appears as REMOVE`() {

        val project = createProject()
        val minecraft = createMinecraft()

        addScenario(project, "main.sfxs")
        addScenario(project, "reward.sfxs")

        val first = ExportService.export(
            project,
            minecraft
        )

        assertTrue(first.success, first.message)

        File(
            project,
            "scenarios/reward.sfxs"
        ).delete()

        File(
            project,
            "scenarios/reward.graph.json"
        ).delete()

        val plan = ExportPreviewService.createPlan(
            project,
            minecraft
        )

        assertTrue(plan.canExport)

        assertEquals(
            ExportPreviewService.Action.REMOVE,
            plan.entries.single {
                it.path == "scripts/reward.sfxs"
            }.action
        )
    }

    // =====================================================
    // UNTRACKED FILE PROTECTION
    // =====================================================

    @Test
    fun `untracked existing destination file causes conflict`() {

        val project = createProject()
        val minecraft = createMinecraft()

        addScenario(
            project,
            "main.sfxs"
        )

        val existingFile = File(
            destination(project, minecraft),
            "scripts/main.sfxs"
        )

        existingFile.parentFile.mkdirs()

        existingFile.writeText(
            "User-owned script"
        )

        val plan = ExportPreviewService.createPlan(
            project,
            minecraft
        )

        assertFalse(plan.canExport)

        assertEquals(
            1,
            plan.conflicts
        )

        val result = ExportService.export(
            project,
            minecraft
        )

        assertFalse(result.success)

        assertEquals(
            "User-owned script",
            existingFile.readText()
        )
    }
}
