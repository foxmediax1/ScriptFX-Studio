
package net.foxmediax.app.project

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import java.util.zip.ZipFile

class ProjectVersionRestoreService(
    private val versionStore: ProjectVersionStore = ProjectVersionStore()
) {

    data class RestoreResult(
        val restoredFiles: Int,
        val backupVersion: ProjectVersionStore.VersionInfo
    )

    private companion object {
        const val MAX_FILES = 100_000
        const val MAX_TOTAL_SIZE = 2L * 1024L * 1024L * 1024L

        val EXCLUDED_DIRS = setOf(
            ".git",
            ".idea",
            ".gradle",
            "build",
            "out"
        )
    }

    /**
     * Восстанавливает проект из сохранённой версии.
     *
     * Перед изменением проекта создаётся автоматическая
     * резервная версия его текущего состояния.
     */
    fun restoreVersion(
        projectRoot: File,
        versionId: String
    ): RestoreResult {

        val originalRoot = projectRoot.absoluteFile.toPath()

        require(!Files.isSymbolicLink(originalRoot)) {
            "Папка проекта является символической ссылкой."
        }

        val root = projectRoot.canonicalFile

        require(root.isDirectory) {
            "Папка проекта не существует."
        }

        val archive = versionStore.getVerifiedArchive(
            root,
            versionId
        ) ?: throw IOException(
            "Версия не найдена или повреждена."
        )

        val parent = root.parentFile
            ?: throw IOException(
                "Не удалось определить родительскую папку проекта."
            )

        val workspace = File(
            parent,
            ".scriptfx-restore-${UUID.randomUUID()}"
        )

        if (!workspace.mkdirs()) {
            throw IOException(
                "Не удалось создать временную папку восстановления."
            )
        }

        val staging = File(workspace, "staging")
        val rollback = File(workspace, "rollback")

        var backupVersion: ProjectVersionStore.VersionInfo? = null
        var restoreStarted = false

        try {
            /*
             * 1. Сначала создаём резервную версию.
             */
            backupVersion = versionStore.createVersion(
                root,
                "Автоматическая резервная копия",
                "Создана автоматически перед восстановлением версии $versionId"
            )

            /*
             * 2. Распаковываем архив в отдельную staging-папку.
             */
            if (!staging.mkdirs()) {
                throw IOException(
                    "Не удалось создать staging-папку."
                )
            }

            val restoredFiles = extractArchive(
                archive,
                staging
            )

            /*
             * 3. Сохраняем текущие файлы для аварийного отката.
             */
            if (!rollback.mkdirs()) {
                throw IOException(
                    "Не удалось создать папку резервного восстановления."
                )
            }

            val currentFiles = collectManagedFiles(root)

            currentFiles.forEach { source ->
                val relative = source.relativeTo(root).path
                val destination = File(rollback, relative)

                destination.parentFile?.mkdirs()

                Files.copy(
                    source.toPath(),
                    destination.toPath(),
                    StandardCopyOption.REPLACE_EXISTING
                )
            }

            /*
             * 4. Проверяем staging до изменения проекта.
             */
            val stagedFiles = collectManagedFiles(staging)

            if (stagedFiles.size != restoredFiles) {
                throw IOException(
                    "Количество файлов в подготовленной версии не совпадает."
                )
            }

            /*
             * 5. Начинаем замену файлов проекта.
             */
            restoreStarted = true

            currentFiles.forEach { file ->
                if (!file.delete()) {
                    throw IOException(
                        "Не удалось удалить файл: ${file.path}"
                    )
                }
            }

            stagedFiles.forEach { source ->
                val relative = source.relativeTo(staging).path
                val destination = resolveSafeTarget(root, relative)

                destination.parentFile?.let { parentDirectory ->
                    createSafeDirectories(root, parentDirectory)
                }

                Files.copy(
                    source.toPath(),
                    destination.toPath(),
                    StandardCopyOption.REPLACE_EXISTING
                )
            }

            /*
             * 6. Проверяем итоговое количество файлов.
             */
            val finalFiles = collectManagedFiles(root)

            if (finalFiles.size != restoredFiles) {
                throw IOException(
                    "После восстановления количество файлов не совпадает."
                )
            }

            return RestoreResult(
                restoredFiles = restoredFiles,
                backupVersion = backupVersion
            )

        } catch (error: Exception) {

            /*
             * Если замена уже началась, возвращаем исходные файлы.
             */
            if (restoreStarted) {
                try {
                    restoreRollback(root, rollback)
                } catch (rollbackError: Exception) {
                    error.addSuppressed(rollbackError)
                }
            }

            throw error

        } finally {
            workspace.deleteRecursively()
        }
    }

    /**
     * Извлекает архив с проверкой каждого пути.
     */
    private fun extractArchive(
        archive: File,
        destination: File
    ): Int {

        val canonicalDestination = destination.canonicalFile
        val seenPaths = mutableSetOf<String>()

        var fileCount = 0
        var totalSize = 0L

        ZipFile(archive).use { zip ->

            val entries = zip.entries()

            while (entries.hasMoreElements()) {

                val entry = entries.nextElement()
                val name = entry.name

                if (!isSafeArchivePath(name)) {
                    throw IOException(
                        "Архив содержит небезопасный путь: $name"
                    )
                }

                val normalized = name.removeSuffix("/")

                if (!seenPaths.add(normalized)) {
                    throw IOException(
                        "Архив содержит повторяющийся путь: $name"
                    )
                }

                val target = File(
                    canonicalDestination,
                    normalized
                ).canonicalFile

                if (!target.toPath().startsWith(
                        canonicalDestination.toPath()
                    )
                ) {
                    throw IOException(
                        "Путь архива выходит за пределы staging."
                    )
                }

                if (entry.isDirectory) {
                    if (!target.mkdirs() && !target.isDirectory) {
                        throw IOException(
                            "Не удалось создать папку: $name"
                        )
                    }
                    continue
                }

                fileCount++

                if (fileCount > MAX_FILES) {
                    throw IOException(
                        "В архиве слишком много файлов."
                    )
                }

                target.parentFile?.let {
                    if (!it.mkdirs() && !it.isDirectory) {
                        throw IOException(
                            "Не удалось создать папку: ${it.path}"
                        )
                    }
                }

                var fileSize = 0L

                zip.getInputStream(entry).use { input ->
                    target.outputStream().buffered().use { output ->

                        val buffer = ByteArray(8192)

                        while (true) {
                            val read = input.read(buffer)

                            if (read < 0) break

                            fileSize += read
                            totalSize += read

                            if (totalSize > MAX_TOTAL_SIZE) {
                                throw IOException(
                                    "Размер распакованного архива превышает лимит."
                                )
                            }

                            output.write(buffer, 0, read)
                        }
                    }
                }

                if (entry.size >= 0 && fileSize != entry.size) {
                    throw IOException(
                        "Размер файла в архиве не совпадает: $name"
                    )
                }
            }
        }

        return fileCount
    }

    /**
     * Возвращает только обычные файлы проекта.
     * Исключённые каталоги сохраняются без изменений.
     */
    private fun collectManagedFiles(root: File): List<File> {

        val result = mutableListOf<File>()

        fun visit(directory: File) {

            val children = directory.listFiles()
                ?: throw IOException(
                    "Не удалось прочитать папку: ${directory.path}"
                )

            children.forEach { child ->

                if (Files.isSymbolicLink(child.toPath())) {
                    throw IOException(
                        "Обнаружена символическая ссылка: ${child.path}"
                    )
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

        return result
    }

    /**
     * Аварийно возвращает исходные файлы проекта.
     */
    private fun restoreRollback(
        root: File,
        rollback: File
    ) {

        collectManagedFiles(root).forEach { file ->
            if (!file.delete()) {
                throw IOException(
                    "Не удалось удалить файл при откате: ${file.path}"
                )
            }
        }

        val backupFiles = collectManagedFiles(rollback)

        backupFiles.forEach { source ->

            val relative = source.relativeTo(rollback).path
            val destination = resolveSafeTarget(root, relative)

            destination.parentFile?.let {
                createSafeDirectories(root, it)
            }

            Files.copy(
                source.toPath(),
                destination.toPath(),
                StandardCopyOption.REPLACE_EXISTING
            )
        }
    }

    private fun resolveSafeTarget(
        root: File,
        relativePath: String
    ): File {

        if (!isSafeArchivePath(relativePath)) {
            throw IOException(
                "Обнаружен небезопасный путь: $relativePath"
            )
        }

        val target = File(root, relativePath).canonicalFile

        if (!target.toPath().startsWith(root.canonicalFile.toPath())) {
            throw IOException(
                "Путь выходит за пределы проекта."
            )
        }

        return target
    }

    /**
     * Создаёт директории по одной, не проходя через симлинки.
     */
    private fun createSafeDirectories(
        root: File,
        directory: File
    ) {

        val canonicalRoot = root.canonicalFile.toPath()
        val canonicalDirectory = directory.canonicalFile.toPath()

        if (!canonicalDirectory.startsWith(canonicalRoot)) {
            throw IOException(
                "Папка находится за пределами проекта."
            )
        }

        var current = root.canonicalFile

        val relative = root.canonicalFile.toPath()
            .relativize(canonicalDirectory)

        for (part in relative) {

            current = File(current, part.toString())

            if (Files.isSymbolicLink(current.toPath())) {
                throw IOException(
                    "Обнаружена символическая ссылка: ${current.path}"
                )
            }

            if (!current.exists() && !current.mkdir()) {
                throw IOException(
                    "Не удалось создать папку: ${current.path}"
                )
            }

            if (!current.isDirectory) {
                throw IOException(
                    "Ожидалась папка: ${current.path}"
                )
            }
        }
    }

    private fun isSafeArchivePath(path: String): Boolean {

        if (path.isBlank()) return false
        if (path.startsWith("/")) return false
        if (path.contains('\\')) return false
        if (path.contains('\u0000')) return false

        val parts = path.removeSuffix("/").split('/')

        if (parts.any {
                it.isBlank() ||
                        it == "." ||
                        it == ".." ||
                        it.contains(':')
            }
        ) {
            return false
        }

        return true
    }
}
