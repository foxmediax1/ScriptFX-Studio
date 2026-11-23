
package net.foxmediax.app.project

import java.io.File
import java.nio.file.Files
import java.util.zip.ZipFile
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class ProjectVersionRestoreServiceTest {

    private val temporaryDirectories = mutableListOf<File>()

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

    private fun createService(
        project: File
    ): Pair<ProjectVersionStore, ProjectVersionRestoreService> {

        val dataRoot = createTempDirectory(
            "scriptfx-restore-data"
        )

        val store = ProjectVersionStore(dataRoot)

        return store to ProjectVersionRestoreService(store)
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
        val (store, restoreService) = createService(project)

        val version = store.createVersion(
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

        val result = restoreService.restoreVersion(
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
        val (store, restoreService) = createService(project)

        val version = store.createVersion(
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

        restoreService.restoreVersion(
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
        val (store, restoreService) = createService(project)

        val version = store.createVersion(
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

        restoreService.restoreVersion(
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
        val (store, restoreService) = createService(project)

        val version = store.createVersion(
            project,
            "Initial"
        )

        File(
            project,
            "scenarios/intro.sfxs"
        ).writeText("current state")

        val result = restoreService.restoreVersion(
            project,
            version.id
        )

        assertNotNull(result.backupVersion)

        assertEquals(
            "Автоматическая резервная копия",
            result.backupVersion.name
        )

        assertTrue(
            store.verifyVersion(
                project,
                result.backupVersion.id
            )
        )

        assertEquals(
            2,
            store.listVersions(project).size
        )
    }

    // =====================================================
    // BACKUP CONTENT
    // =====================================================

    @Test
    fun `automatic backup contains state before restoration`() {

        val project = createProject()
        val (store, restoreService) = createService(project)

        val version = store.createVersion(
            project,
            "Initial"
        )

        File(
            project,
            "scenarios/intro.sfxs"
        ).writeText("state before restore")

        val result = restoreService.restoreVersion(
            project,
            version.id
        )

        val backupArchive = store.getVerifiedArchive(
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
        val (_, restoreService) = createService(project)

        val error = assertFailsWith<java.io.IOException> {

            restoreService.restoreVersion(
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
        val (_, restoreService) = createService(project)

        val originalContent = File(
            project,
            "scenarios/intro.sfxs"
        ).readText()

        assertFailsWith<java.io.IOException> {

            restoreService.restoreVersion(
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
        val (store, restoreService) = createService(project)

        val emptyDirectory = File(
            project,
            "assets/empty/nested"
        )

        assertTrue(
            emptyDirectory.mkdirs()
        )

        val version = store.createVersion(
            project,
            "With empty directories"
        )

        assertTrue(
            emptyDirectory.deleteRecursively()
        )

        restoreService.restoreVersion(
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
        val (store, restoreService) = createService(project)

        val version = store.createVersion(
            project,
            "Without temporary directories"
        )

        val temporaryDirectory = File(
            project,
            "scenarios/generated/nested"
        )

        assertTrue(
            temporaryDirectory.mkdirs()
        )

        restoreService.restoreVersion(
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
        val (store, restoreService) = createService(project)

        val version = store.createVersion(
            project,
            "Initial"
        )

        val emptyDirectory = File(
            project,
            "assets/current-empty"
        )

        assertTrue(
            emptyDirectory.mkdirs()
        )

        val result = restoreService.restoreVersion(
            project,
            version.id
        )

        val backupArchive = store.getVerifiedArchive(
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
}
