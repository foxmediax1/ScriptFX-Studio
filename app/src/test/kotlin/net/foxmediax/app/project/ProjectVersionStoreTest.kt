
package net.foxmediax.app.project

import java.io.File
import java.nio.file.Files
import java.util.zip.ZipFile
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class ProjectVersionStoreTest {

    private val temporaryDirectories = mutableListOf<File>()

    private fun tempDirectory(): File {
        val directory = Files
            .createTempDirectory("scriptfx-version-test-")
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

    private fun createProject(
        root: File,
        name: String = "TestProject"
    ): File {
        val project = File(root, name)
        project.mkdirs()

        File(project, "scriptfx.project.json")
            .writeText("""{"name":"$name"}""")

        File(project, "scenarios").mkdirs()

        File(project, "scenarios/main.sfxs")
            .writeText("print \"Hello World\"")

        File(project, "scenarios/main.graph.json")
            .writeText("""{"nodes":[],"edges":[]}""")

        File(project, "assets/textures/example.png")
            .apply {
                parentFile.mkdirs()
                writeBytes(byteArrayOf(1, 2, 3, 4, 5))
            }

        return project
    }

    private fun findArchive(
        dataRoot: File,
        project: File,
        versionId: String
    ): File {
        val projectKey = project.canonicalPath
            .toByteArray(Charsets.UTF_8)
            .let {
                java.security.MessageDigest
                    .getInstance("SHA-256")
                    .digest(it)
                    .joinToString("") { byte ->
                        "%02x".format(byte.toInt() and 0xFF)
                    }
            }

        return File(
            File(
                File(dataRoot, "ProjectVersions"),
                projectKey
            ),
            "$versionId/snapshot.zip"
        )
    }

    // =====================================================
    // CREATE
    // =====================================================

    @Test
    fun `version is created successfully`() {
        val dataRoot = tempDirectory()
        val project = createProject(tempDirectory())

        val store = ProjectVersionStore(dataRoot)

        val version = store.createVersion(
            projectRoot = project,
            name = "Initial version",
            comment = "Before major changes"
        )

        assertTrue(version.id.isNotBlank())
        assertEquals("Initial version", version.name)
        assertEquals("Before major changes", version.comment)
        assertTrue(version.createdAtMillis > 0)
        assertTrue(version.archiveSize > 0)

        assertTrue(store.verifyVersion(project, version.id))
    }

    // =====================================================
    // LIST
    // =====================================================

    @Test
    fun `created versions appear in list`() {
        val dataRoot = tempDirectory()
        val project = createProject(tempDirectory())

        val store = ProjectVersionStore(dataRoot)

        store.createVersion(project, "Version 1")
        store.createVersion(project, "Version 2")
        store.createVersion(project, "Version 3")

        val versions = store.listVersions(project)

        assertEquals(3, versions.size)

        assertTrue(
            versions.any { it.name == "Version 1" }
        )

        assertTrue(
            versions.any { it.name == "Version 2" }
        )

        assertTrue(
            versions.any { it.name == "Version 3" }
        )
    }

    // =====================================================
    // ARCHIVE CONTENT
    // =====================================================

    @Test
    fun `snapshot contains project files`() {
        val dataRoot = tempDirectory()
        val project = createProject(tempDirectory())

        val store = ProjectVersionStore(dataRoot)

        val version = store.createVersion(
            project,
            "Content test"
        )

        val archive = findArchive(
            dataRoot,
            project,
            version.id
        )

        assertTrue(archive.isFile)

        ZipFile(archive).use { zip ->

            assertNotNull(
                zip.getEntry("scriptfx.project.json")
            )

            assertNotNull(
                zip.getEntry("scenarios/main.sfxs")
            )

            assertNotNull(
                zip.getEntry("scenarios/main.graph.json")
            )

            assertNotNull(
                zip.getEntry("assets/textures/example.png")
            )

            val scenario = zip.getEntry(
                "scenarios/main.sfxs"
            )

            val content = zip.getInputStream(scenario)
                .bufferedReader()
                .readText()

            assertEquals(
                "print \"Hello World\"",
                content
            )
        }
    }

    // =====================================================
    // EXCLUDED DIRECTORIES
    // =====================================================

    @Test
    fun `excluded directories are not archived`() {
        val dataRoot = tempDirectory()
        val project = createProject(tempDirectory())

        File(project, ".git/objects/test")
            .apply {
                parentFile.mkdirs()
                writeText("git data")
            }

        File(project, ".idea/workspace.xml")
            .apply {
                parentFile.mkdirs()
                writeText("idea data")
            }

        File(project, "build/output.jar")
            .apply {
                parentFile.mkdirs()
                writeText("build data")
            }

        File(project, ".gradle/cache.bin")
            .apply {
                parentFile.mkdirs()
                writeText("gradle data")
            }

        val store = ProjectVersionStore(dataRoot)

        val version = store.createVersion(
            project,
            "Exclude test"
        )

        val archive = findArchive(
            dataRoot,
            project,
            version.id
        )

        ZipFile(archive).use { zip ->

            assertNull(zip.getEntry(".git/objects/test"))
            assertNull(zip.getEntry(".idea/workspace.xml"))
            assertNull(zip.getEntry("build/output.jar"))
            assertNull(zip.getEntry(".gradle/cache.bin"))

            assertNotNull(
                zip.getEntry("scenarios/main.sfxs")
            )
        }
    }

    // =====================================================
    // INDEPENDENT PROJECTS
    // =====================================================

    @Test
    fun `versions of different projects are independent`() {
        val dataRoot = tempDirectory()

        val projectA = createProject(
            tempDirectory(),
            "ProjectA"
        )

        val projectB = createProject(
            tempDirectory(),
            "ProjectB"
        )

        val store = ProjectVersionStore(dataRoot)

        val versionA = store.createVersion(
            projectA,
            "A snapshot"
        )

        val versionB = store.createVersion(
            projectB,
            "B snapshot"
        )

        assertEquals(
            1,
            store.listVersions(projectA).size
        )

        assertEquals(
            1,
            store.listVersions(projectB).size
        )

        assertTrue(
            store.verifyVersion(projectA, versionA.id)
        )

        assertTrue(
            store.verifyVersion(projectB, versionB.id)
        )

        assertFalse(
            store.verifyVersion(projectA, versionB.id)
        )
    }

    // =====================================================
    // DELETE
    // =====================================================

    @Test
    fun `deleting version does not affect other versions`() {
        val dataRoot = tempDirectory()
        val project = createProject(tempDirectory())

        val store = ProjectVersionStore(dataRoot)

        val first = store.createVersion(
            project,
            "First"
        )

        val second = store.createVersion(
            project,
            "Second"
        )

        assertTrue(
            store.deleteVersion(project, first.id)
        )

        assertFalse(
            store.listVersions(project)
                .any { it.id == first.id }
        )

        assertTrue(
            store.listVersions(project)
                .any { it.id == second.id }
        )

        assertTrue(
            store.verifyVersion(project, second.id)
        )
    }

    @Test
    fun `deleting unknown version returns false`() {
        val dataRoot = tempDirectory()
        val project = createProject(tempDirectory())

        val store = ProjectVersionStore(dataRoot)

        assertFalse(
            store.deleteVersion(
                project,
                "1234567890123-00000000-0000-0000-0000-000000000000"
            )
        )
    }

    // =====================================================
    // INTEGRITY
    // =====================================================

    @Test
    fun `modified archive fails verification`() {
        val dataRoot = tempDirectory()
        val project = createProject(tempDirectory())

        val store = ProjectVersionStore(dataRoot)

        val version = store.createVersion(
            project,
            "Integrity test"
        )

        val archive = findArchive(
            dataRoot,
            project,
            version.id
        )

        assertTrue(
            store.verifyVersion(project, version.id)
        )

        archive.appendBytes(
            byteArrayOf(10, 20, 30, 40)
        )

        assertFalse(
            store.verifyVersion(project, version.id)
        )
    }

    // =====================================================
    // EMPTY PROJECT
    // =====================================================

    @Test
    fun `empty project can be versioned`() {
        val dataRoot = tempDirectory()
        val project = tempDirectory()

        val store = ProjectVersionStore(dataRoot)

        val version = store.createVersion(
            project,
            "Empty project"
        )

        assertTrue(
            store.verifyVersion(project, version.id)
        )

        assertEquals(
            0,
            store.listVersions(project)
                .single()
                .archiveSize.let {
                    // Архив пустого проекта все равно
                    // содержит ZIP-заголовки.
                    if (it > 0) 0 else 1
                }
        )
    }

    // =====================================================
    // INVALID INPUT
    // =====================================================

    @Test
    fun `blank version name is rejected`() {
        val dataRoot = tempDirectory()
        val project = createProject(tempDirectory())

        val store = ProjectVersionStore(dataRoot)

        assertFailsWith<IllegalArgumentException> {
            store.createVersion(
                project,
                "   "
            )
        }
    }

    @Test
    fun `missing project directory is rejected`() {
        val dataRoot = tempDirectory()

        val project = File(
            tempDirectory(),
            "MissingProject"
        )

        val store = ProjectVersionStore(dataRoot)

        assertFailsWith<IllegalArgumentException> {
            store.createVersion(
                project,
                "Missing"
            )
        }
    }
}
