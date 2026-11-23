
package net.foxmediax.app.project

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ProjectVersionStore(
    private val dataRoot: File = ProjectService().studioDataRoot()
) {

    data class VersionInfo(
        val id: String,
        val name: String,
        val comment: String,
        val createdAtMillis: Long,
        val archiveSize: Long
    )

    @Serializable
    private data class VersionMetadata(
        val format: String,
        val version: Int,
        val id: String,
        val projectPath: String,
        val name: String,
        val comment: String,
        val createdAtMillis: Long,
        val archiveSha256: String,
        val fileCount: Int
    )

    private companion object {
        const val FORMAT = "scriptfx-project-version"
        const val VERSION = 1
        const val MAX_FILES = 100_000
        const val MAX_TOTAL_SIZE = 2L * 1024L * 1024L * 1024L
        const val MAX_ARCHIVE_SIZE = 2L * 1024L * 1024L * 1024L

        val EXCLUDED_DIRS = setOf(
            ".git",
            ".idea",
            ".gradle",
            "build",
            "out"
        )
    }

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = false
    }

    // =====================================================
    // CREATE VERSION
    // =====================================================

    fun createVersion(
        projectRoot: File,
        name: String,
        comment: String = ""
    ): VersionInfo {

        require(name.isNotBlank()) {
            "Название версии не может быть пустым."
        }

        val root = projectRoot.canonicalFile

        require(root.isDirectory) {
            "Папка проекта не существует."
        }

        require(!Files.isSymbolicLink(root.toPath())) {
            "Папка проекта является символической ссылкой."
        }

        val files = collectFiles(root)

        require(files.size <= MAX_FILES) {
            "В проекте слишком много файлов."
        }

        val totalSize = files.sumOf { it.length() }

        require(totalSize <= MAX_TOTAL_SIZE) {
            "Размер проекта превышает лимит версии."
        }

        val projectKey = sha256(root.path)
        val versionsDir = File(
            File(dataRoot, "ProjectVersions"),
            projectKey
        )

        if (!versionsDir.exists() && !versionsDir.mkdirs()) {
            throw IOException("Не удалось создать каталог версий.")
        }

        if (Files.isSymbolicLink(versionsDir.toPath())) {
            throw IOException("Каталог версий является символической ссылкой.")
        }

        val id = "${System.currentTimeMillis()}-${java.util.UUID.randomUUID()}"
        val versionDir = File(versionsDir, id)

        if (!versionDir.mkdirs()) {
            throw IOException("Не удалось создать версию проекта.")
        }

        val archive = File(versionDir, "snapshot.zip")
        val metadataFile = File(versionDir, "metadata.json")
        val temporaryArchive = File(versionDir, "snapshot.tmp")

        try {
            createArchive(root, files, temporaryArchive)

            require(temporaryArchive.length() <= MAX_ARCHIVE_SIZE) {
                "Размер архива версии превышает допустимый."
            }

            val archiveHash = sha256(temporaryArchive)

            Files.move(
                temporaryArchive.toPath(),
                archive.toPath()
            )

            val metadata = VersionMetadata(
                format = FORMAT,
                version = VERSION,
                id = id,
                projectPath = root.path,
                name = name.trim(),
                comment = comment.trim(),
                createdAtMillis = System.currentTimeMillis(),
                archiveSha256 = archiveHash,
                fileCount = files.size
            )

            val temporaryMetadata = File(
                versionDir,
                "metadata.tmp"
            )

            temporaryMetadata.writeText(
                json.encodeToString(metadata),
                StandardCharsets.UTF_8
            )

            Files.move(
                temporaryMetadata.toPath(),
                metadataFile.toPath()
            )

            return VersionInfo(
                id = id,
                name = metadata.name,
                comment = metadata.comment,
                createdAtMillis = metadata.createdAtMillis,
                archiveSize = archive.length()
            )

        } catch (error: Exception) {
            versionDir.deleteRecursively()
            throw error
        }
    }

    // =====================================================
    // LIST VERSIONS
    // =====================================================

    fun listVersions(projectRoot: File): List<VersionInfo> {

        val root = projectRoot.canonicalFile
        val versionsDir = getVersionsDirectory(root)

        if (!versionsDir.isDirectory) {
            return emptyList()
        }

        if (Files.isSymbolicLink(versionsDir.toPath())) {
            return emptyList()
        }

        return versionsDir.listFiles()
            ?.filter { directory ->
                directory.isDirectory &&
                        !Files.isSymbolicLink(directory.toPath())
            }
            ?.mapNotNull { readVersion(it, root) }
            ?.sortedByDescending { it.createdAtMillis }
            ?: emptyList()
    }

    // =====================================================
    // DELETE VERSION
    // =====================================================

    fun deleteVersion(
        projectRoot: File,
        versionId: String
    ): Boolean {

        require(isValidId(versionId)) {
            "Некорректный ID версии."
        }

        val root = projectRoot.canonicalFile
        val versionsDir = getVersionsDirectory(root)
        val versionDir = File(versionsDir, versionId).canonicalFile

        require(versionDir.parentFile == versionsDir.canonicalFile) {
            "Версия находится вне каталога проекта."
        }

        if (!versionDir.exists()) {
            return false
        }

        if (Files.isSymbolicLink(versionDir.toPath())) {
            throw IOException("Каталог версии является символической ссылкой.")
        }

        return versionDir.deleteRecursively()
    }

    // =====================================================
    // VERIFY VERSION
    // =====================================================

    fun verifyVersion(
        projectRoot: File,
        versionId: String
    ): Boolean {

        val metadata = getMetadata(projectRoot, versionId)
            ?: return false

        val archive = getArchive(projectRoot, versionId)
            ?: return false

        if (!archive.isFile) return false
        if (archive.length() > MAX_ARCHIVE_SIZE) return false

        return try {
            sha256(archive) == metadata.archiveSha256 &&
                    validateArchive(archive, metadata.fileCount)
        } catch (_: Exception) {
            false
        }
    }


    /**
     * Возвращает архив только после полной проверки его целостности
     * и принадлежности указанному проекту.
     */
    fun getVerifiedArchive(
        projectRoot: File,
        versionId: String
    ): File? {
        if (!verifyVersion(projectRoot, versionId)) {
            return null
        }

        return getArchive(projectRoot, versionId)
            ?.takeIf { it.isFile }
    }


    // =====================================================
    // READ METADATA
    // =====================================================

    private fun readVersion(
        directory: File,
        expectedRoot: File
    ): VersionInfo? {

        return try {

            val metadataFile = File(directory, "metadata.json")
            val archive = File(directory, "snapshot.zip")

            if (!metadataFile.isFile || !archive.isFile) {
                return null
            }

            if (Files.isSymbolicLink(metadataFile.toPath())) {
                return null
            }

            if (Files.isSymbolicLink(archive.toPath())) {
                return null
            }

            val metadata = json.decodeFromString<VersionMetadata>(
                metadataFile.readText(StandardCharsets.UTF_8)
            )

            if (metadata.format != FORMAT) return null
            if (metadata.version != VERSION) return null
            if (metadata.projectPath != expectedRoot.path) return null
            if (metadata.id != directory.name) return null

            VersionInfo(
                id = metadata.id,
                name = metadata.name,
                comment = metadata.comment,
                createdAtMillis = metadata.createdAtMillis,
                archiveSize = archive.length()
            )

        } catch (_: Exception) {
            null
        }
    }

    private fun getMetadata(
        projectRoot: File,
        versionId: String
    ): VersionMetadata? {

        if (!isValidId(versionId)) return null

        return try {

            val directory = File(
                getVersionsDirectory(projectRoot),
                versionId
            )

            if (!directory.isDirectory) return null
            if (Files.isSymbolicLink(directory.toPath())) return null

            val file = File(directory, "metadata.json")

            if (!file.isFile) return null
            if (Files.isSymbolicLink(file.toPath())) return null

            val metadata = json.decodeFromString<VersionMetadata>(
                file.readText(StandardCharsets.UTF_8)
            )

            if (metadata.format != FORMAT) return null
            if (metadata.version != VERSION) return null
            if (metadata.id != versionId) return null
            if (metadata.projectPath != projectRoot.canonicalPath) return null

            metadata

        } catch (_: Exception) {
            null
        }
    }

    private fun getArchive(
        projectRoot: File,
        versionId: String
    ): File? {

        if (!isValidId(versionId)) return null

        val directory = File(
            getVersionsDirectory(projectRoot),
            versionId
        ).canonicalFile

        val expectedParent = getVersionsDirectory(projectRoot).canonicalFile

        if (directory.parentFile != expectedParent) return null
        if (Files.isSymbolicLink(directory.toPath())) return null

        val archive = File(directory, "snapshot.zip")

        if (Files.isSymbolicLink(archive.toPath())) return null

        return archive
    }

    // =====================================================
    // PROJECT FILE COLLECTION
    // =====================================================

    private fun collectFiles(root: File): List<File> {

        val result = mutableListOf<File>()

        fun visit(directory: File) {

            val children = directory.listFiles()
                ?: throw IOException(
                    "Не удалось прочитать папку: ${directory.path}"
                )

            children.forEach { child ->

                if (Files.isSymbolicLink(child.toPath())) {
                    return@forEach
                }

                if (child.isDirectory) {

                    if (child.name in EXCLUDED_DIRS) {
                        return@forEach
                    }

                    visit(child)

                } else if (child.isFile) {

                    result += child
                }
            }
        }

        visit(root)

        return result.sortedBy {
            it.relativeTo(root).path.replace(File.separatorChar, '/')
        }
    }

    // =====================================================
    // ZIP CREATE
    // =====================================================

    private fun createArchive(
        root: File,
        files: List<File>,
        destination: File
    ) {

        ZipOutputStream(
            destination.outputStream().buffered()
        ).use { zip ->

            files.forEach { file ->

                val relative = file.relativeTo(root)
                    .path
                    .replace(File.separatorChar, '/')

                val entry = ZipEntry(relative)

                zip.putNextEntry(entry)

                file.inputStream().buffered().use { input ->
                    input.copyTo(zip)
                }

                zip.closeEntry()
            }
        }
    }

    // =====================================================
    // ZIP VALIDATION
    // =====================================================

    private fun validateArchive(
        archive: File,
        expectedFileCount: Int
    ): Boolean {

        var count = 0
        var totalSize = 0L

        java.util.zip.ZipFile(archive).use { zip ->

            val entries = zip.entries()

            while (entries.hasMoreElements()) {

                val entry = entries.nextElement()

                if (entry.isDirectory) continue

                if (!isSafeArchivePath(entry.name)) {
                    return false
                }

                count++

                if (count > MAX_FILES) {
                    return false
                }

                zip.getInputStream(entry).use { input ->

                    val buffer = ByteArray(8192)

                    while (true) {

                        val read = input.read(buffer)

                        if (read < 0) break

                        totalSize += read

                        if (totalSize > MAX_TOTAL_SIZE) {
                            return false
                        }
                    }
                }
            }
        }

        return count == expectedFileCount
    }

    private fun isSafeArchivePath(path: String): Boolean {

        if (path.isBlank()) return false
        if (path.startsWith("/")) return false
        if (path.contains('\\')) return false

        val parts = path.split('/')

        if (parts.any { it.isBlank() || it == "." || it == ".." }) {
            return false
        }

        return true
    }

    // =====================================================
    // PATHS
    // =====================================================

    private fun getVersionsDirectory(
        projectRoot: File
    ): File {

        val key = sha256(projectRoot.canonicalPath)

        return File(
            File(dataRoot, "ProjectVersions"),
            key
        )
    }

    private fun isValidId(id: String): Boolean =
        id.matches(
            Regex("^[0-9]{13}-[0-9a-fA-F-]{36}$")
        )

    // =====================================================
    // SHA-256
    // =====================================================

    private fun sha256(value: String): String =
        sha256(value.toByteArray(StandardCharsets.UTF_8))

    private fun sha256(file: File): String {

        file.inputStream().buffered().use { input ->

            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(8192)

            while (true) {

                val read = input.read(buffer)

                if (read < 0) break

                digest.update(buffer, 0, read)
            }

            return digest.digest().joinToString("") {
                "%02x".format(it.toInt() and 0xFF)
            }
        }
    }

    private fun sha256(bytes: ByteArray): String {

        val digest = MessageDigest.getInstance("SHA-256")

        return digest.digest(bytes).joinToString("") {
            "%02x".format(it.toInt() and 0xFF)
        }
    }
}
