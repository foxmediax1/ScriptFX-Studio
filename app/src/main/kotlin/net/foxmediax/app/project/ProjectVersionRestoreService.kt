
package net.foxmediax.app.project

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Locale
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
        const val MAX_ENTRIES = 200_000

        const val MAX_TOTAL_SIZE =
            2L * 1024L * 1024L * 1024L

        val EXCLUDED_DIRS = setOf(
            ".git",
            ".idea",
            ".gradle",
            "build",
            "out"
        )
    }

    // =====================================================
    // RESTORE VERSION
    // =====================================================

    /**
     * Восстанавливает проект из сохранённой версии.
     *
     * Последовательность:
     * 1. Проверка архива.
     * 2. Распаковка в staging.
     * 3. Создание автоматической резервной версии.
     * 4. Создание временной копии текущего состояния.
     * 5. Замена содержимого проекта.
     * 6. Проверка результата.
     *
     * Если операция завершается ошибкой после начала замены,
     * сервис пытается вернуть прежнее состояние.
     *
     * Если аварийный откат тоже завершается ошибкой,
     * временная папка не удаляется.
     */
    fun restoreVersion(
        projectRoot: File,
        versionId: String
    ): RestoreResult {

        val originalPath = projectRoot.absoluteFile
            .toPath()
            .normalize()

        require(!Files.isSymbolicLink(originalPath)) {
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

        val staging = File(
            workspace,
            "staging"
        )

        val rollback = File(
            workspace,
            "rollback"
        )

        var restoreStarted = false
        var preserveWorkspace = false

        var backupVersion: ProjectVersionStore.VersionInfo? = null

        try {

            // -------------------------------------------------
            // 1. Подготовка staging
            // -------------------------------------------------

            if (!staging.mkdirs()) {
                throw IOException(
                    "Не удалось создать staging-папку."
                )
            }

            val restoredFiles = extractArchive(
                archive = archive,
                destination = staging
            )

            val stagedFiles = collectManagedFiles(staging)
            val stagedDirectories = collectManagedDirectories(staging)

            if (stagedFiles.size != restoredFiles) {
                throw IOException(
                    "Количество файлов в подготовленной версии не совпадает."
                )
            }

            if (stagedFiles.size > MAX_FILES) {
                throw IOException(
                    "В подготовленной версии слишком много файлов."
                )
            }

            if (
                stagedFiles.size + stagedDirectories.size >
                MAX_ENTRIES
            ) {
                throw IOException(
                    "В подготовленной версии слишком много элементов."
                )
            }

            // -------------------------------------------------
            // 2. Автоматическая резервная версия
            // -------------------------------------------------

            backupVersion = versionStore.createVersion(
                projectRoot = root,
                name = "Автоматическая резервная копия",
                comment = "Создана перед восстановлением версии $versionId"
            )

            // -------------------------------------------------
            // 3. Подготовка rollback
            // -------------------------------------------------

            if (!rollback.mkdirs()) {
                throw IOException(
                    "Не удалось создать папку аварийного восстановления."
                )
            }

            val currentFiles = collectManagedFiles(root)
            val currentDirectories = collectManagedDirectories(root)

            // Сначала сохраняем структуру папок.
            currentDirectories.forEach { directory ->

                val relative = relativePath(
                    root,
                    directory
                )

                val destination = resolveSafeTarget(
                    rollback,
                    relative
                )

                createSafeDirectories(
                    rollback,
                    destination
                )
            }

            // Затем сохраняем файлы.
            currentFiles.forEach { source ->

                val relative = relativePath(
                    root,
                    source
                )

                val destination = resolveSafeTarget(
                    rollback,
                    relative
                )

                destination.parentFile?.let {
                    createSafeDirectories(
                        rollback,
                        it
                    )
                }

                Files.copy(
                    source.toPath(),
                    destination.toPath(),
                    StandardCopyOption.REPLACE_EXISTING
                )
            }

            // Проверяем, что резервная копия файлов полная.
            val rollbackFiles = collectManagedFiles(rollback)

            val expectedRollbackPaths = currentFiles
                .map { relativePath(root, it) }
                .toSet()

            val actualRollbackPaths = rollbackFiles
                .map { relativePath(rollback, it) }
                .toSet()

            if (expectedRollbackPaths != actualRollbackPaths) {
                throw IOException(
                    "Не удалось полностью подготовить аварийную копию."
                )
            }

            // -------------------------------------------------
            // 4. Начинаем изменять проект
            // -------------------------------------------------

            restoreStarted = true

            removeManagedContents(root)

            // -------------------------------------------------
            // 5. Восстанавливаем структуру директорий
            // -------------------------------------------------

            stagedDirectories.forEach { directory ->

                val relative = relativePath(
                    staging,
                    directory
                )

                val destination = resolveSafeTarget(
                    root,
                    relative
                )

                createSafeDirectories(
                    root,
                    destination
                )
            }

            // -------------------------------------------------
            // 6. Восстанавливаем файлы
            // -------------------------------------------------

            stagedFiles.forEach { source ->

                val relative = relativePath(
                    staging,
                    source
                )

                val destination = resolveSafeTarget(
                    root,
                    relative
                )

                destination.parentFile?.let {
                    createSafeDirectories(
                        root,
                        it
                    )
                }

                if (Files.isSymbolicLink(destination.toPath())) {
                    throw IOException(
                        "Целевой файл является символической ссылкой: " +
                                destination.path
                    )
                }

                if (destination.isDirectory) {
                    throw IOException(
                        "Вместо файла обнаружена директория: " +
                                destination.path
                    )
                }

                Files.copy(
                    source.toPath(),
                    destination.toPath(),
                    StandardCopyOption.REPLACE_EXISTING
                )
            }

            // -------------------------------------------------
            // 7. Проверяем итоговые файлы
            // -------------------------------------------------

            verifyRestoredFiles(
                root = root,
                staging = staging
            )

            // -------------------------------------------------
            // 8. Проверяем восстановленные директории
            // -------------------------------------------------

            stagedDirectories.forEach { directory ->

                val relative = relativePath(
                    staging,
                    directory
                )

                val restoredDirectory = resolveSafeTarget(
                    root,
                    relative
                )

                if (!restoredDirectory.isDirectory) {
                    throw IOException(
                        "Не удалось восстановить папку: $relative"
                    )
                }
            }

            return RestoreResult(
                restoredFiles = restoredFiles,
                backupVersion = backupVersion
            )

        } catch (error: Exception) {

            // -------------------------------------------------
            // АВАРИЙНЫЙ ОТКАТ
            // -------------------------------------------------

            if (restoreStarted) {

                try {

                    restoreRollback(
                        root = root,
                        rollback = rollback
                    )

                } catch (rollbackError: Exception) {

                    preserveWorkspace = true

                    val recoveryError = IOException(
                        "Восстановление и аварийный откат завершились ошибкой. " +
                                "Временные данные сохранены в: " +
                                "${workspace.absolutePath}. " +
                                "Резервная версия: " +
                                "${backupVersion?.id ?: "не создана"}.",
                        error
                    )

                    recoveryError.addSuppressed(
                        rollbackError
                    )

                    throw recoveryError
                }
            }

            throw error

        } finally {

            /*
             * Если аварийный откат не удался,
             * временная папка остаётся на диске.
             */
            if (!preserveWorkspace) {
                workspace.deleteRecursively()
            }
        }
    }

    // =====================================================
    // ZIP EXTRACTION
    // =====================================================

    private fun extractArchive(
        archive: File,
        destination: File
    ): Int {

        val canonicalDestination = destination.canonicalFile

        val seenPaths = mutableMapOf<String, Boolean>()

        var fileCount = 0
        var totalSize = 0L

        ZipFile(archive).use { zip ->

            val entries = zip.entries()

            while (entries.hasMoreElements()) {

                val entry = entries.nextElement()
                val name = entry.name

                val normalized = name.removeSuffix("/")

                if (!isSafeArchivePath(normalized)) {
                    throw IOException(
                        "Архив содержит небезопасный путь: $name"
                    )
                }

                val pathKey = normalized.lowercase(Locale.ROOT)

                // Проверяем повторяющиеся записи.
                if (seenPaths.containsKey(pathKey)) {
                    throw IOException(
                        "Архив содержит повторяющийся путь: $name"
                    )
                }

                val parts = normalized.split('/')

                // Проверяем родительские элементы.
                for (index in 1 until parts.size) {

                    val parent = parts
                        .take(index)
                        .joinToString("/")
                        .lowercase(Locale.ROOT)

                    if (seenPaths[parent] == false) {
                        throw IOException(
                            "Файл используется как родительская папка: $parent"
                        )
                    }
                }

                // Запрещаем архиву записывать данные
                // в исключённые каталоги.
                val directoryParts = if (entry.isDirectory) {
                    parts
                } else {
                    parts.dropLast(1)
                }

                val touchesExcludedDirectory =
                    directoryParts.any { part ->
                        EXCLUDED_DIRS.any {
                            it.equals(part, ignoreCase = true)
                        }
                    }

                if (touchesExcludedDirectory) {
                    throw IOException(
                        "Архив пытается изменить исключённую папку: $name"
                    )
                }

                // Файл не может заменить директорию,
                // в которой уже находятся другие элементы.
                if (
                    !entry.isDirectory &&
                    seenPaths.keys.any {
                        it.startsWith("$pathKey/")
                    }
                ) {
                    throw IOException(
                        "Конфликт файловой структуры архива: $name"
                    )
                }

                seenPaths[pathKey] = entry.isDirectory

                if (seenPaths.size > MAX_ENTRIES) {
                    throw IOException(
                        "В архиве слишком много записей."
                    )
                }

                val target = resolveSafeTarget(
                    canonicalDestination,
                    normalized
                )

                // -------------------------------------------------
                // Директория
                // -------------------------------------------------

                if (entry.isDirectory) {

                    if (entry.size > 0L) {
                        throw IOException(
                            "Директория архива содержит неожиданные данные: $name"
                        )
                    }

                    createSafeDirectories(
                        canonicalDestination,
                        target
                    )

                    continue
                }

                // -------------------------------------------------
                // Файл
                // -------------------------------------------------

                fileCount++

                if (fileCount > MAX_FILES) {
                    throw IOException(
                        "В архиве слишком много файлов."
                    )
                }

                target.parentFile?.let {
                    createSafeDirectories(
                        canonicalDestination,
                        it
                    )
                }

                if (Files.isSymbolicLink(target.toPath())) {
                    throw IOException(
                        "Целевой путь является символической ссылкой: " +
                                target.path
                    )
                }

                if (target.isDirectory) {
                    throw IOException(
                        "Вместо файла обнаружена директория: $name"
                    )
                }

                var fileSize = 0L

                zip.getInputStream(entry).use { input ->

                    target.outputStream().buffered().use { output ->

                        val buffer = ByteArray(8192)

                        while (true) {

                            val read = input.read(buffer)

                            if (read < 0) break

                            if (
                                read.toLong() >
                                MAX_TOTAL_SIZE - totalSize
                            ) {
                                throw IOException(
                                    "Размер распакованного архива превышает лимит."
                                )
                            }

                            fileSize += read
                            totalSize += read

                            output.write(
                                buffer,
                                0,
                                read
                            )
                        }
                    }
                }

                if (
                    entry.size >= 0L &&
                    fileSize != entry.size
                ) {
                    throw IOException(
                        "Размер файла в архиве не совпадает: $name"
                    )
                }
            }
        }

        return fileCount
    }

    // =====================================================
    // COLLECT MANAGED FILES
    // =====================================================

    /**
     * Возвращает все обычные файлы,
     * за исключением содержимого защищённых каталогов.
     */
    private fun collectManagedFiles(
        root: File
    ): List<File> {

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

        return result.sortedBy {
            relativePath(root, it)
        }
    }

    // =====================================================
    // COLLECT MANAGED DIRECTORIES
    // =====================================================

    /**
     * Собирает все управляемые директории,
     * включая пустые и вложенные.
     */
    private fun collectManagedDirectories(
        root: File
    ): List<File> {

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

                    result += child

                    visit(child)
                }
            }
        }

        visit(root)

        return result.sortedBy {
            relativePath(root, it).count { char ->
                char == '/'
            }
        }
    }

    // =====================================================
    // REMOVE MANAGED CONTENTS
    // =====================================================

    /**
     * Удаляет все управляемые файлы и пустые директории.
     *
     * Исключённые каталоги сохраняются.
     * Родительские папки, необходимые для сохранения
     * вложенных исключённых каталогов, также сохраняются.
     */
    private fun removeManagedContents(
        root: File
    ) {

        // Сначала удаляем файлы.
        val files = collectManagedFiles(root)

        files.forEach { file ->

            if (!file.delete() && file.exists()) {
                throw IOException(
                    "Не удалось удалить файл: ${file.path}"
                )
            }
        }

        // Затем удаляем директории снизу вверх.
        val directories = collectManagedDirectories(root)
            .sortedByDescending {
                relativePath(root, it).count { char ->
                    char == '/'
                }
            }

        directories.forEach { directory ->

            if (!directory.exists()) {
                return@forEach
            }

            val children = directory.listFiles()
                ?: throw IOException(
                    "Не удалось прочитать папку: ${directory.path}"
                )

            if (children.isEmpty()) {

                if (!directory.delete() && directory.exists()) {
                    throw IOException(
                        "Не удалось удалить папку: ${directory.path}"
                    )
                }

            } else if (!containsExcludedDirectory(directory)) {

                throw IOException(
                    "Не удалось удалить папку: ${directory.path}"
                )
            }
        }
    }

    /**
     * Проверяет, содержит ли папка защищённые каталоги
     * на любом уровне вложенности.
     */
    private fun containsExcludedDirectory(
        directory: File
    ): Boolean {

        val children = directory.listFiles()
            ?: throw IOException(
                "Не удалось прочитать папку: ${directory.path}"
            )

        return children.any { child ->

            if (Files.isSymbolicLink(child.toPath())) {
                throw IOException(
                    "Обнаружена символическая ссылка: ${child.path}"
                )
            }

            if (!child.isDirectory) {
                false
            } else if (child.name in EXCLUDED_DIRS) {
                true
            } else {
                containsExcludedDirectory(child)
            }
        }
    }

    // =====================================================
    // VERIFY RESTORED FILES
    // =====================================================

    private fun verifyRestoredFiles(
        root: File,
        staging: File
    ) {

        val expectedPaths = collectManagedFiles(staging)
            .map {
                relativePath(staging, it)
            }
            .toSet()

        val actualPaths = collectManagedFiles(root)
            .map {
                relativePath(root, it)
            }
            .toSet()

        if (expectedPaths != actualPaths) {
            throw IOException(
                "Итоговый список файлов не совпадает с сохранённой версией."
            )
        }
    }

    // =====================================================
    // ROLLBACK
    // =====================================================

    /**
     * Возвращает проект в состояние до начала восстановления.
     */
    private fun restoreRollback(
        root: File,
        rollback: File
    ) {

        // Удаляем частично восстановленные файлы.
        removeManagedContents(root)

        // Возвращаем директории.
        val backupDirectories = collectManagedDirectories(rollback)

        backupDirectories.forEach { directory ->

            val relative = relativePath(
                rollback,
                directory
            )

            val destination = resolveSafeTarget(
                root,
                relative
            )

            createSafeDirectories(
                root,
                destination
            )
        }

        // Возвращаем файлы.
        val backupFiles = collectManagedFiles(rollback)

        backupFiles.forEach { source ->

            val relative = relativePath(
                rollback,
                source
            )

            val destination = resolveSafeTarget(
                root,
                relative
            )

            destination.parentFile?.let {
                createSafeDirectories(
                    root,
                    it
                )
            }

            if (Files.isSymbolicLink(destination.toPath())) {
                throw IOException(
                    "Целевой путь является символической ссылкой: " +
                            destination.path
                )
            }

            Files.copy(
                source.toPath(),
                destination.toPath(),
                StandardCopyOption.REPLACE_EXISTING
            )
        }

        // Проверяем восстановленные файлы.
        val expectedPaths = backupFiles
            .map {
                relativePath(rollback, it)
            }
            .toSet()

        val actualPaths = collectManagedFiles(root)
            .map {
                relativePath(root, it)
            }
            .toSet()

        if (expectedPaths != actualPaths) {
            throw IOException(
                "Проверка аварийного отката не пройдена."
            )
        }

        // Проверяем восстановленные директории.
        val backupDirectoriesAfterRestore =
            collectManagedDirectories(rollback)

        backupDirectoriesAfterRestore.forEach { directory ->

            val relative = relativePath(
                rollback,
                directory
            )

            val restoredDirectory = resolveSafeTarget(
                root,
                relative
            )

            if (!restoredDirectory.isDirectory) {
                throw IOException(
                    "Не удалось вернуть папку: $relative"
                )
            }
        }
    }

    // =====================================================
    // SAFE TARGET
    // =====================================================

    /**
     * Создаёт путь внутри указанного корневого каталога.
     *
     * Не использует canonicalFile для целевого пути,
     * чтобы не скрывать существующие символические ссылки.
     */
    private fun resolveSafeTarget(
        root: File,
        relativePath: String
    ): File {

        if (!isSafeArchivePath(relativePath)) {
            throw IOException(
                "Обнаружен небезопасный путь: $relativePath"
            )
        }

        val canonicalRoot = root.canonicalFile
        val rootPath = canonicalRoot.toPath()

        val platformPath = relativePath.replace(
            '/',
            File.separatorChar
        )

        val targetPath = rootPath
            .resolve(platformPath)
            .normalize()

        if (!targetPath.startsWith(rootPath)) {
            throw IOException(
                "Путь выходит за пределы корневого каталога: $relativePath"
            )
        }

        return targetPath.toFile()
    }

    // =====================================================
    // SAFE DIRECTORY CREATION
    // =====================================================

    /**
     * Создаёт директории по одной и проверяет каждый компонент.
     */
    private fun createSafeDirectories(
        root: File,
        directory: File
    ) {

        val canonicalRoot = root.canonicalFile
        val rootPath = canonicalRoot.toPath()

        val directoryPath = directory.absoluteFile
            .toPath()
            .normalize()

        if (!directoryPath.startsWith(rootPath)) {
            throw IOException(
                "Папка находится за пределами корневого каталога."
            )
        }

        val relative = rootPath.relativize(directoryPath)

        var current = canonicalRoot

        for (part in relative) {

            current = File(
                current,
                part.toString()
            )

            if (Files.isSymbolicLink(current.toPath())) {
                throw IOException(
                    "Обнаружена символическая ссылка: ${current.path}"
                )
            }

            if (!current.exists()) {

                if (!current.mkdir()) {
                    throw IOException(
                        "Не удалось создать папку: ${current.path}"
                    )
                }
            }

            if (!current.isDirectory) {
                throw IOException(
                    "Ожидалась директория: ${current.path}"
                )
            }
        }
    }

    // =====================================================
    // SAFE ARCHIVE PATH
    // =====================================================

    private fun isSafeArchivePath(
        path: String
    ): Boolean {

        if (path.isBlank()) return false
        if (path.startsWith("/")) return false
        if (path.contains('\\')) return false
        if (path.contains('\u0000')) return false

        val parts = path.split('/')

        if (
            parts.any {
                it.isBlank() ||
                        it == "." ||
                        it == ".." ||
                        it.contains(':') ||
                        it.endsWith(".") ||
                        it.endsWith(" ")
            }
        ) {
            return false
        }

        val reservedNames = buildSet {
            addAll(
                listOf(
                    "CON",
                    "PRN",
                    "AUX",
                    "NUL"
                )
            )

            for (index in 1..9) {
                add("COM$index")
                add("LPT$index")
            }
        }

        if (
            parts.any {
                it.substringBefore('.')
                    .uppercase(Locale.ROOT) in reservedNames
            }
        ) {
            return false
        }

        return true
    }

    // =====================================================
    // RELATIVE PATH
    // =====================================================

    private fun relativePath(
        root: File,
        file: File
    ): String {

        return file.relativeTo(root)
            .path
            .replace(File.separatorChar, '/')
    }
}
