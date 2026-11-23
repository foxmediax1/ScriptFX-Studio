package net.foxmediax.app.project

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.UUID
import java.util.zip.ZipFile
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProjectVersionRestoreServiceTest {

    private val temporaryDirectories = mutableListOf<File>()

    private data class TestContext(
        val dataRoot: File,
        val store: ProjectVersionStore,
        val journalStore: ProjectRestoreJournalStore,
        val restoreService: ProjectVersionRestoreService
    )

    // =====================================================
    // TEST HELPERS
    // =====================================================

    private fun createTempDirectory(name: String): File {
        val directory = Files.createTempDirectory(name).toFile()
        temporaryDirectories += directory
        return directory
    }

    private fun createProject(): File {
        val root = createTempDirectory("scriptfx-restore-project")

        File(root, "manifest.json").writeText(
            """{"name":"Test"}"""
        )

        File(root, "scenarios").mkdirs()

        File(
            root,
            "scenarios/intro.sfxs"
        ).writeText("original scenario")

        File(root, "assets").mkdirs()

        File(
            root,
            "assets/texture.txt"
        ).writeText("original asset")

        return root
    }

    /**
     * Все сервисы используют один изолированный dataRoot.
     * Благодаря этому ProjectVersionStore и JournalStore
     * работают с одним и тем же тестовым хранилищем.
     */
    private fun createContext(): TestContext {
        val dataRoot = createTempDirectory(
            "scriptfx-restore-data"
        )

        val store = ProjectVersionStore(dataRoot)

        val journalStore = ProjectRestoreJournalStore(
            dataRoot
        )

        val restoreService = ProjectVersionRestoreService(
            versionStore = store,
            journalStore = journalStore
        )

        return TestContext(
            dataRoot = dataRoot,
            store = store,
            journalStore = journalStore,
            restoreService = restoreService
        )
    }

    private fun createWorkspace(project: File): File {
        return File(
            project.parentFile,
            ".scriptfx-restore-${UUID.randomUUID()}"
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
    // BASIC RESTORATION
    // =====================================================

    @Test
    fun `restore returns project files to saved state`() {
        val project = createProject()
        val context = createContext()

        val version = context.store.createVersion(
            project,
            "Before changes"
        )

        File(
            project,
            "scenarios/intro.sfxs"
        ).writeText("modified scenario")

        File(
            project,
            "assets/texture.txt"
        ).writeText("modified asset")

        val result = context.restoreService.restoreVersion(
            project,
            version.id
        )

        assertEquals(
            "original scenario",
            File(
                project,
                "scenarios/intro.sfxs"
            ).readText()
        )

        assertEquals(
            "original asset",
            File(
                project,
                "assets/texture.txt"
            ).readText()
        )

        assertEquals(
            3,
            result.restoredFiles
        )
    }

    // =====================================================
    // REMOVE NEW FILES
    // =====================================================

    @Test
    fun `restore removes files created after snapshot`() {
        val project = createProject()
        val context = createContext()

        val version = context.store.createVersion(
            project,
            "Clean state"
        )

        File(
            project,
            "scenarios/temporary.sfxs"
        ).writeText("temporary")

        File(
            project,
            "new-file.txt"
        ).writeText("new file")

        context.restoreService.restoreVersion(
            project,
            version.id
        )

        assertFalse(
            File(
                project,
                "scenarios/temporary.sfxs"
            ).exists()
        )

        assertFalse(
            File(
                project,
                "new-file.txt"
            ).exists()
        )

        assertTrue(
            File(
                project,
                "scenarios/intro.sfxs"
            ).exists()
        )
    }

    // =====================================================
    // PRESERVE EXCLUDED DIRECTORIES
    // =====================================================

    @Test
    fun `restore preserves excluded directories`() {
        val project = createProject()
        val context = createContext()

        val version = context.store.createVersion(
            project,
            "Before build"
        )

        val buildFile = File(
            project,
            "build/cache.txt"
        )

        buildFile.parentFile.mkdirs()
        buildFile.writeText("build cache")

        val gitFile = File(
            project,
            ".git/config"
        )

        gitFile.parentFile.mkdirs()
        gitFile.writeText("git config")

        context.restoreService.restoreVersion(
            project,
            version.id
        )

        assertEquals(
            "build cache",
            buildFile.readText()
        )

        assertEquals(
            "git config",
            gitFile.readText()
        )
    }

    // =====================================================
    // AUTOMATIC BACKUP
    // =====================================================

    @Test
    fun `restore creates automatic backup before changing project`() {
        val project = createProject()
        val context = createContext()

        val version = context.store.createVersion(
            project,
            "Initial"
        )

        File(
            project,
            "scenarios/intro.sfxs"
        ).writeText("current state")

        val result = context.restoreService.restoreVersion(
            project,
            version.id
        )

        assertNotNull(result.backupVersion)

        assertEquals(
            "Автоматическая резервная копия",
            result.backupVersion.name
        )

        assertTrue(
            context.store.verifyVersion(
                project,
                result.backupVersion.id
            )
        )

        assertEquals(
            2,
            context.store.listVersions(project).size
        )
    }

    // =====================================================
    // BACKUP CONTENT
    // =====================================================

    @Test
    fun `automatic backup contains state before restoration`() {
        val project = createProject()
        val context = createContext()

        val version = context.store.createVersion(
            project,
            "Initial"
        )

        File(
            project,
            "scenarios/intro.sfxs"
        ).writeText("state before restore")

        val result = context.restoreService.restoreVersion(
            project,
            version.id
        )

        val backupArchive = context.store.getVerifiedArchive(
            project,
            result.backupVersion.id
        )

        assertNotNull(backupArchive)

        ZipFile(backupArchive).use { zip ->

            val entry = zip.getEntry(
                "scenarios/intro.sfxs"
            )

            assertNotNull(entry)

            val content = zip.getInputStream(entry)
                .bufferedReader()
                .use {
                    it.readText()
                }

            assertEquals(
                "state before restore",
                content
            )
        }
    }

    // =====================================================
    // UNKNOWN VERSION
    // =====================================================

    @Test
    fun `restore rejects unknown version`() {
        val project = createProject()
        val context = createContext()

        val error = assertFailsWith<IOException> {
            context.restoreService.restoreVersion(
                project,
                "0000000000000-00000000-0000-0000-0000-000000000000"
            )
        }

        assertTrue(
            error.message.orEmpty().contains("не найдена")
        )
    }

    // =====================================================
    // INVALID VERSION DOES NOT MODIFY PROJECT
    // =====================================================

    @Test
    fun `restore does not modify project when version is invalid`() {
        val project = createProject()
        val context = createContext()

        val originalContent = File(
            project,
            "scenarios/intro.sfxs"
        ).readText()

        assertFailsWith<IOException> {
            context.restoreService.restoreVersion(
                project,
                "0000000000000-00000000-0000-0000-0000-000000000000"
            )
        }

        assertEquals(
            originalContent,
            File(
                project,
                "scenarios/intro.sfxs"
            ).readText()
        )
    }

    // =====================================================
    // EMPTY DIRECTORY RESTORATION
    // =====================================================

    @Test
    fun `restore recreates empty nested directories`() {
        val project = createProject()
        val context = createContext()

        val emptyDirectory = File(
            project,
            "assets/empty/nested"
        )

        assertTrue(emptyDirectory.mkdirs())

        val version = context.store.createVersion(
            project,
            "With empty directories"
        )

        assertTrue(emptyDirectory.deleteRecursively())

        context.restoreService.restoreVersion(
            project,
            version.id
        )

        assertTrue(
            File(
                project,
                "assets/empty"
            ).isDirectory
        )

        assertTrue(
            File(
                project,
                "assets/empty/nested"
            ).isDirectory
        )
    }

    // =====================================================
    // REMOVE NEW EMPTY DIRECTORIES
    // =====================================================

    @Test
    fun `restore removes empty directories created after snapshot`() {
        val project = createProject()
        val context = createContext()

        val version = context.store.createVersion(
            project,
            "Without temporary directories"
        )

        val temporaryDirectory = File(
            project,
            "scenarios/generated/nested"
        )

        assertTrue(temporaryDirectory.mkdirs())

        context.restoreService.restoreVersion(
            project,
            version.id
        )

        assertFalse(
            File(
                project,
                "scenarios/generated"
            ).exists()
        )
    }

    // =====================================================
    // EMPTY DIRECTORIES IN AUTOMATIC BACKUP
    // =====================================================

    @Test
    fun `automatic backup contains empty directories`() {
        val project = createProject()
        val context = createContext()

        val version = context.store.createVersion(
            project,
            "Initial"
        )

        val emptyDirectory = File(
            project,
            "assets/current-empty"
        )

        assertTrue(emptyDirectory.mkdirs())

        val result = context.restoreService.restoreVersion(
            project,
            version.id
        )

        val backupArchive = context.store.getVerifiedArchive(
            project,
            result.backupVersion.id
        )

        assertNotNull(backupArchive)

        ZipFile(backupArchive).use { zip ->
            assertNotNull(
                zip.getEntry("assets/current-empty/")
            )
        }
    }

    // =====================================================
    // INTERRUPTED RESTORE: PRE-REPLACEMENT
    // =====================================================

    @Test
    fun `recovery removes workspace when replacement has not started`() {
        val project = createProject()
        val context = createContext()

        val targetVersion = context.store.createVersion(
            project,
            "Target version"
        )

        val originalContent = File(
            project,
            "scenarios/intro.sfxs"
        ).readText()

        val workspace = createWorkspace(project)

        assertTrue(workspace.mkdirs())

        val staging = File(
            workspace,
            "staging"
        )

        assertTrue(staging.mkdirs())

        File(
            staging,
            "temporary.txt"
        ).writeText("incomplete staging")

        var journal = context.journalStore.create(
            projectRoot = project,
            targetVersionId = targetVersion.id,
            workspace = workspace
        )

        journal = context.journalStore.update(
            journal,
            ProjectRestoreJournalStore.State.STAGED
        )

        val result = context.restoreService
            .recoverInterruptedRestore(project)

        assertNotNull(result)

        assertEquals(
            ProjectVersionRestoreService.RecoveryAction
                .DISCARDED_BEFORE_REPLACEMENT,
            result.action
        )

        assertEquals(
            originalContent,
            File(
                project,
                "scenarios/intro.sfxs"
            ).readText()
        )

        assertFalse(workspace.exists())

        assertNull(
            context.journalStore.read(project)
        )
    }

    // =====================================================
    // INTERRUPTED RESTORE: AUTOMATIC BACKUP RECOVERY
    // =====================================================

    @Test
    fun `recovery restores previous state after interrupted replacement`() {
        val project = createProject()
        val context = createContext()

        val targetVersion = context.store.createVersion(
            project,
            "Target version"
        )

        // Это состояние должно быть возвращено после аварии.
        File(
            project,
            "scenarios/intro.sfxs"
        ).writeText("state before interrupted restore")

        val backupVersion = context.store.createVersion(
            project,
            "Automatic backup"
        )

        val workspace = createWorkspace(project)

        assertTrue(workspace.mkdirs())

        assertTrue(
            File(
                workspace,
                "staging"
            ).mkdirs()
        )

        assertTrue(
            File(
                workspace,
                "rollback"
            ).mkdirs()
        )

        var journal = context.journalStore.create(
            projectRoot = project,
            targetVersionId = targetVersion.id,
            workspace = workspace
        )

        journal = context.journalStore.update(
            journal,
            ProjectRestoreJournalStore.State.STAGED
        )

        journal = context.journalStore.update(
            journal,
            state = ProjectRestoreJournalStore.State.BACKUP_CREATED,
            backupVersionId = backupVersion.id
        )

        context.journalStore.update(
            journal,
            ProjectRestoreJournalStore.State.REPLACING
        )

        // Имитируем частично выполненную замену.
        File(
            project,
            "scenarios/intro.sfxs"
        ).writeText("partially restored content")

        File(
            project,
            "unexpected.txt"
        ).writeText("partial file")

        val result = context.restoreService
            .recoverInterruptedRestore(project)

        assertNotNull(result)

        assertEquals(
            ProjectVersionRestoreService.RecoveryAction
                .RESTORED_PREVIOUS_STATE,
            result.action
        )

        assertEquals(
            "state before interrupted restore",
            File(
                project,
                "scenarios/intro.sfxs"
            ).readText()
        )

        assertFalse(
            File(
                project,
                "unexpected.txt"
            ).exists()
        )

        assertTrue(
            context.store.verifyVersion(
                project,
                backupVersion.id
            )
        )

        assertFalse(workspace.exists())

        assertNull(
            context.journalStore.read(project)
        )
    }

    // =====================================================
    // INTERRUPTED RESTORE: COMPLETED
    // =====================================================

    @Test
    fun `recovery only cleans leftovers after completed restore`() {
        val project = createProject()
        val context = createContext()

        val targetVersion = context.store.createVersion(
            project,
            "Target version"
        )

        val workspace = createWorkspace(project)

        assertTrue(workspace.mkdirs())

        File(
            workspace,
            "temporary.txt"
        ).writeText("leftover")

        val journal = context.journalStore.create(
            projectRoot = project,
            targetVersionId = targetVersion.id,
            workspace = workspace
        )

        context.journalStore.update(
            journal,
            ProjectRestoreJournalStore.State.COMPLETED
        )

        val result = context.restoreService
            .recoverInterruptedRestore(project)

        assertNotNull(result)

        assertEquals(
            ProjectVersionRestoreService.RecoveryAction
                .CLEANED_COMPLETED_OPERATION,
            result.action
        )

        assertTrue(
            File(
                project,
                "scenarios/intro.sfxs"
            ).isFile
        )

        assertFalse(workspace.exists())

        assertNull(
            context.journalStore.read(project)
        )
    }

    // =====================================================
    // INTERRUPTED RESTORE: UNSAFE WORKSPACE PATH
    // =====================================================

    @Test
    fun `recovery rejects workspace outside project parent`() {
        val project = createProject()
        val context = createContext()

        val outsideParent = createTempDirectory(
            "outside-recovery"
        )

        val targetVersion = context.store.createVersion(
            project,
            "Target version"
        )

        val outsideWorkspace = File(
            outsideParent,
            ".scriptfx-restore-${UUID.randomUUID()}"
        )

        context.journalStore.create(
            projectRoot = project,
            targetVersionId = targetVersion.id,
            workspace = outsideWorkspace
        )

        assertFailsWith<IOException> {
            context.restoreService.recoverInterruptedRestore(
                project
            )
        }

        // Журнал нельзя удалять, если его путь не прошёл проверку.
        assertNotNull(
            context.journalStore.read(project)
        )

        assertEquals(
            "original scenario",
            File(
                project,
                "scenarios/intro.sfxs"
            ).readText()
        )

        assertFalse(outsideWorkspace.exists())
    }
}