package net.foxmediax.app.project

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.Properties
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProjectRestoreJournalStoreTest {

    private val temporaryDirectories = mutableListOf<File>()

    private val targetVersionId =
        "1720000000000-12345678-1234-1234-1234-123456789abc"

    private val backupVersionId =
        "1720000000001-abcdefab-abcd-abcd-abcd-abcdefabcdef"

    private fun createTempDirectory(name: String): File {
        val directory = Files.createTempDirectory(name).toFile()
        temporaryDirectories += directory
        return directory
    }

    private fun createProject(name: String): File {
        return File(
            createTempDirectory("journal-project"),
            name
        ).apply {
            mkdirs()
            File(this, "manifest.json").writeText("{}")
        }
    }

    private fun createStore(): Pair<File, ProjectRestoreJournalStore> {
        val dataRoot = createTempDirectory("journal-data")
        return dataRoot to ProjectRestoreJournalStore(dataRoot)
    }

    private fun createJournal(
        project: File,
        store: ProjectRestoreJournalStore
    ): ProjectRestoreJournalStore.Journal {
        return store.create(
            projectRoot = project,
            targetVersionId = targetVersionId,
            workspace = File(
                project.parentFile,
                ".scriptfx-restore-test"
            )
        )
    }

    @AfterTest
    fun cleanup() {
        temporaryDirectories.forEach {
            it.deleteRecursively()
        }

        temporaryDirectories.clear()
    }

    // =====================================================
    // CREATE AND READ
    // =====================================================

    @Test
    fun `create journal persists preparing state`() {

        val project = createProject("project")
        val (dataRoot, store) = createStore()

        val workspace = File(
            project.parentFile,
            ".scriptfx-restore-test"
        )

        val created = store.create(
            projectRoot = project,
            targetVersionId = targetVersionId,
            workspace = workspace
        )

        val reloadedStore = ProjectRestoreJournalStore(dataRoot)

        val loaded = reloadedStore.read(project)

        assertNotNull(loaded)

        assertEquals(
            created.operationId,
            loaded.operationId
        )

        assertEquals(
            project.canonicalPath,
            loaded.projectPath
        )

        assertEquals(
            targetVersionId,
            loaded.targetVersionId
        )

        assertNull(loaded.backupVersionId)

        assertEquals(
            ProjectRestoreJournalStore.State.PREPARING,
            loaded.state
        )

        assertEquals(
            workspace.absoluteFile.toPath().normalize().toString(),
            loaded.workspacePath
        )
    }

    // =====================================================
    // STATE TRANSITIONS
    // =====================================================

    @Test
    fun `update persists state and automatic backup id`() {

        val project = createProject("project")
        val (dataRoot, store) = createStore()

        var journal = createJournal(
            project,
            store
        )

        journal = store.update(
            journal,
            ProjectRestoreJournalStore.State.STAGED
        )

        journal = store.update(
            journal,
            ProjectRestoreJournalStore.State.BACKUP_CREATED,
            backupVersionId = backupVersionId
        )

        journal = store.update(
            journal,
            ProjectRestoreJournalStore.State.REPLACING
        )

        val loaded = ProjectRestoreJournalStore(dataRoot)
            .read(project)

        assertNotNull(loaded)

        assertEquals(
            journal.operationId,
            loaded.operationId
        )

        assertEquals(
            ProjectRestoreJournalStore.State.REPLACING,
            loaded.state
        )

        assertEquals(
            backupVersionId,
            loaded.backupVersionId
        )

        assertEquals(
            journal.startedAtMillis,
            loaded.startedAtMillis
        )
    }

    // =====================================================
    // ROLLBACK TRANSITIONS
    // =====================================================

    @Test
    fun `rollback failed state can be retried`() {

        val project = createProject("project")
        val (_, store) = createStore()

        var journal = createJournal(
            project,
            store
        )

        journal = store.update(
            journal,
            ProjectRestoreJournalStore.State.STAGED
        )

        journal = store.update(
            journal,
            ProjectRestoreJournalStore.State.BACKUP_CREATED,
            backupVersionId = backupVersionId
        )

        journal = store.update(
            journal,
            ProjectRestoreJournalStore.State.REPLACING
        )

        journal = store.update(
            journal,
            ProjectRestoreJournalStore.State.ROLLBACK_REQUIRED
        )

        journal = store.update(
            journal,
            ProjectRestoreJournalStore.State.ROLLBACK_FAILED
        )

        journal = store.update(
            journal,
            ProjectRestoreJournalStore.State.ROLLBACK_REQUIRED
        )

        journal = store.update(
            journal,
            ProjectRestoreJournalStore.State.ROLLBACK_FAILED
        )

        assertEquals(
            ProjectRestoreJournalStore.State.ROLLBACK_FAILED,
            journal.state
        )

        val loaded = store.read(project)

        assertNotNull(loaded)

        assertEquals(
            ProjectRestoreJournalStore.State.ROLLBACK_FAILED,
            loaded.state
        )

        assertEquals(
            backupVersionId,
            loaded.backupVersionId
        )
    }

    // =====================================================
    // COMPLETED IS TERMINAL
    // =====================================================

    @Test
    fun `completed state cannot be changed`() {

        val project = createProject("project")
        val (_, store) = createStore()

        var journal = createJournal(
            project,
            store
        )

        journal = store.update(
            journal,
            ProjectRestoreJournalStore.State.STAGED
        )

        journal = store.update(
            journal,
            ProjectRestoreJournalStore.State.BACKUP_CREATED,
            backupVersionId = backupVersionId
        )

        journal = store.update(
            journal,
            ProjectRestoreJournalStore.State.REPLACING
        )

        journal = store.update(
            journal,
            ProjectRestoreJournalStore.State.COMPLETED
        )

        assertFailsWith<IOException> {
            store.update(
                journal,
                ProjectRestoreJournalStore.State.ROLLBACK_REQUIRED
            )
        }

        val loaded = store.read(project)

        assertNotNull(loaded)

        assertEquals(
            ProjectRestoreJournalStore.State.COMPLETED,
            loaded.state
        )
    }

    // =====================================================
    // INVALID TRANSITION
    // =====================================================

    @Test
    fun `update rejects transition from preparing directly to replacing`() {

        val project = createProject("project")
        val (_, store) = createStore()

        val journal = createJournal(
            project,
            store
        )

        assertFailsWith<IOException> {
            store.update(
                journal,
                ProjectRestoreJournalStore.State.REPLACING
            )
        }

        val loaded = store.read(project)

        assertNotNull(loaded)

        assertEquals(
            ProjectRestoreJournalStore.State.PREPARING,
            loaded.state
        )
    }

    // =====================================================
    // BACKUP ID REQUIRED
    // =====================================================

    @Test
    fun `backup created state requires backup version id`() {

        val project = createProject("project")
        val (_, store) = createStore()

        var journal = createJournal(
            project,
            store
        )

        journal = store.update(
            journal,
            ProjectRestoreJournalStore.State.STAGED
        )

        assertFailsWith<IOException> {
            store.update(
                journal,
                ProjectRestoreJournalStore.State.BACKUP_CREATED
            )
        }

        val loaded = store.read(project)

        assertNotNull(loaded)

        assertEquals(
            ProjectRestoreJournalStore.State.STAGED,
            loaded.state
        )

        assertNull(loaded.backupVersionId)
    }

    // =====================================================
    // EXISTING JOURNAL PROTECTION
    // =====================================================

    @Test
    fun `create does not overwrite existing journal`() {

        val project = createProject("project")
        val (_, store) = createStore()

        val original = createJournal(
            project,
            store
        )

        assertFailsWith<IOException> {
            store.create(
                projectRoot = project,
                targetVersionId = backupVersionId,
                workspace = File(
                    project.parentFile,
                    ".scriptfx-restore-second"
                )
            )
        }

        val loaded = store.read(project)

        assertNotNull(loaded)

        assertEquals(
            original.operationId,
            loaded.operationId
        )

        assertEquals(
            targetVersionId,
            loaded.targetVersionId
        )

        assertEquals(
            ProjectRestoreJournalStore.State.PREPARING,
            loaded.state
        )
    }

    // =====================================================
    // STALE JOURNAL PROTECTION
    // =====================================================

    @Test
    fun `update rejects stale journal snapshot`() {

        val project = createProject("project")
        val (_, store) = createStore()

        val original = createJournal(
            project,
            store
        )

        store.update(
            original,
            ProjectRestoreJournalStore.State.STAGED
        )

        // Используем старый объект PREPARING.
        assertFailsWith<IOException> {
            store.update(
                original,
                ProjectRestoreJournalStore.State.STAGED
            )
        }

        val loaded = store.read(project)

        assertNotNull(loaded)

        assertEquals(
            ProjectRestoreJournalStore.State.STAGED,
            loaded.state
        )
    }

    // =====================================================
    // PROJECT ISOLATION
    // =====================================================

    @Test
    fun `journals are isolated by canonical project path`() {

        val projectA = createProject("project-a")
        val projectB = createProject("project-b")

        val (_, store) = createStore()

        store.create(
            projectRoot = projectA,
            targetVersionId = targetVersionId,
            workspace = File(
                projectA.parentFile,
                "workspace-a"
            )
        )

        store.create(
            projectRoot = projectB,
            targetVersionId = backupVersionId,
            workspace = File(
                projectB.parentFile,
                "workspace-b"
            )
        )

        val journalA = store.read(projectA)
        val journalB = store.read(projectB)

        assertNotNull(journalA)
        assertNotNull(journalB)

        assertEquals(
            targetVersionId,
            journalA.targetVersionId
        )

        assertEquals(
            backupVersionId,
            journalB.targetVersionId
        )

        assertFalse(
            journalA.operationId == journalB.operationId
        )
    }

    // =====================================================
    // DELETE
    // =====================================================

    @Test
    fun `delete removes journal but preserves workspace`() {

        val project = createProject("project")
        val (_, store) = createStore()

        val workspace = File(
            project.parentFile,
            "restore-workspace"
        ).apply {
            mkdirs()
        }

        val marker = File(
            workspace,
            "recovery-data.txt"
        ).apply {
            writeText("preserve me")
        }

        store.create(
            projectRoot = project,
            targetVersionId = targetVersionId,
            workspace = workspace
        )

        assertTrue(
            store.delete(project)
        )

        assertNull(
            store.read(project)
        )

        assertTrue(
            marker.exists(),
            "Удаление журнала не должно удалять рабочую папку."
        )

        assertEquals(
            "preserve me",
            marker.readText()
        )

        assertFalse(
            store.delete(project),
            "Повторное удаление отсутствующего журнала должно возвращать false."
        )
    }

    // =====================================================
    // CORRUPTED JOURNAL
    // =====================================================

    @Test
    fun `read rejects corrupted journal instead of ignoring it`() {

        val project = createProject("project")
        val (dataRoot, store) = createStore()

        store.create(
            projectRoot = project,
            targetVersionId = targetVersionId,
            workspace = File(
                project.parentFile,
                "restore-workspace"
            )
        )

        val journalsRoot = File(
            dataRoot,
            "RestoreJournals"
        )

        val projectJournalDirectory = journalsRoot
            .listFiles()
            ?.singleOrNull()
            ?: error("Каталог журнала не найден.")

        val journalFile = File(
            projectJournalDirectory,
            "journal.properties"
        )

        assertTrue(journalFile.isFile)

        val properties = Properties()

        journalFile.inputStream().use {
            properties.load(it)
        }

        properties.setProperty(
            "state",
            "INVALID_STATE"
        )

        journalFile.outputStream().use {
            properties.store(
                it,
                "corrupted test journal"
            )
        }

        assertFailsWith<IOException> {
            store.read(project)
        }
    }

    // =====================================================
    // INVALID VERSION ID
    // =====================================================

    @Test
    fun `create rejects invalid version id`() {

        val project = createProject("project")
        val (_, store) = createStore()

        assertFailsWith<IOException> {
            store.create(
                projectRoot = project,
                targetVersionId = "../../outside",
                workspace = File(
                    project.parentFile,
                    "workspace"
                )
            )
        }

        assertNull(
            store.read(project)
        )
    }
}