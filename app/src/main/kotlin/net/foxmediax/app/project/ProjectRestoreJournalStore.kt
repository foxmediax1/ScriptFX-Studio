package net.foxmediax.app.project

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.Properties
import java.util.UUID

class ProjectRestoreJournalStore(
    private val dataRoot: File = ProjectService().studioDataRoot()
) {

    enum class State {
        PREPARING,
        STAGED,
        BACKUP_CREATED,
        REPLACING,
        ROLLBACK_REQUIRED,
        ROLLBACK_FAILED,
        COMPLETED
    }

    data class Journal(
        val operationId: String,
        val projectPath: String,
        val targetVersionId: String,
        val backupVersionId: String?,
        val workspacePath: String,
        val state: State,
        val startedAtMillis: Long,
        val updatedAtMillis: Long
    )

    private companion object {
        const val FORMAT = "scriptfx-restore-journal"
        const val FORMAT_VERSION = 1
        const val JOURNAL_FILE = "journal.properties"

        private val VERSION_ID_PATTERN = Regex(
            "^\\d+-[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-" +
                    "[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"
        )
    }

    // =====================================================
    // CREATE
    // =====================================================

    fun create(
        projectRoot: File,
        targetVersionId: String,
        workspace: File
    ): Journal {

        requireValidVersionId(targetVersionId)

        val root = projectRoot.canonicalFile

        if (!root.isDirectory) {
            throw IOException("Папка проекта не существует.")
        }

        val now = System.currentTimeMillis()

        val journal = Journal(
            operationId = UUID.randomUUID().toString(),
            projectPath = root.path,
            targetVersionId = targetVersionId,
            backupVersionId = null,
            workspacePath = workspace.absoluteFile.toPath()
                .normalize()
                .toString(),
            state = State.PREPARING,
            startedAtMillis = now,
            updatedAtMillis = now
        )

        write(journal)

        return journal
    }

    // =====================================================
    // UPDATE
    // =====================================================

    fun update(
        journal: Journal,
        state: State,
        backupVersionId: String? = journal.backupVersionId
    ): Journal {

        validateJournal(journal)

        backupVersionId?.let {
            requireValidVersionId(it)
        }

        val updated = journal.copy(
            backupVersionId = backupVersionId,
            state = state,
            updatedAtMillis = System.currentTimeMillis()
        )

        write(updated)

        return updated
    }

    // =====================================================
    // READ
    // =====================================================

    /**
     * null — журнал отсутствует.
     * IOException — журнал существует, но не может быть прочитан
     * или не прошёл проверку.
     */
    fun read(projectRoot: File): Journal? {

        val root = projectRoot.canonicalFile
        val file = journalFile(root)

        if (!file.exists()) {
            return null
        }

        if (Files.isSymbolicLink(file.toPath())) {
            throw IOException(
                "Файл журнала восстановления является символической ссылкой."
            )
        }

        try {

            val properties = Properties()

            file.inputStream().use {
                properties.load(it)
            }

            if (properties.getProperty("format") != FORMAT) {
                throw IOException(
                    "Неизвестный формат журнала восстановления."
                )
            }

            val version = properties
                .getProperty("formatVersion")
                ?.toIntOrNull()
                ?: throw IOException(
                    "Некорректная версия журнала."
                )

            if (version != FORMAT_VERSION) {
                throw IOException(
                    "Неподдерживаемая версия журнала: $version"
                )
            }

            val state = properties
                .getProperty("state")
                ?.let {
                    runCatching {
                        State.valueOf(it)
                    }.getOrNull()
                }
                ?: throw IOException(
                    "Некорректное состояние журнала."
                )

            val journal = Journal(
                operationId = required(properties, "operationId"),
                projectPath = required(properties, "projectPath"),
                targetVersionId = required(properties, "targetVersionId"),
                backupVersionId = properties
                    .getProperty("backupVersionId")
                    ?.takeIf { it.isNotBlank() },
                workspacePath = required(properties, "workspacePath"),
                state = state,
                startedAtMillis = requiredLong(properties, "startedAtMillis"),
                updatedAtMillis = requiredLong(properties, "updatedAtMillis")
            )

            validateJournal(journal)

            if (journal.projectPath != root.path) {
                throw IOException(
                    "Журнал относится к другому пути проекта."
                )
            }

            return journal

        } catch (error: IOException) {
            throw error
        } catch (error: Exception) {
            throw IOException(
                "Не удалось прочитать журнал восстановления.",
                error
            )
        }
    }

    // =====================================================
    // DELETE
    // =====================================================

    /**
     * Удаляет только журнал.
     * Рабочую папку восстановления намеренно не трогает.
     */
    fun delete(projectRoot: File): Boolean {

        val root = projectRoot.canonicalFile
        val file = journalFile(root)

        if (!file.exists()) {
            return false
        }

        if (Files.isSymbolicLink(file.toPath())) {
            throw IOException(
                "Файл журнала восстановления является символической ссылкой."
            )
        }

        if (!file.delete() && file.exists()) {
            throw IOException(
                "Не удалось удалить журнал восстановления."
            )
        }

        return true
    }

    // =====================================================
    // WRITE
    // =====================================================

    private fun write(journal: Journal) {

        validateJournal(journal)

        val directory = journalFile(
            File(journal.projectPath)
        ).parentFile

        ensureSafeDirectory(directory)

        val destination = File(
            directory,
            JOURNAL_FILE
        )

        val temporary = File(
            directory,
            "journal.tmp"
        )

        if (Files.isSymbolicLink(destination.toPath())) {
            throw IOException(
                "Файл журнала восстановления является символической ссылкой."
            )
        }

        if (Files.isSymbolicLink(temporary.toPath())) {
            throw IOException(
                "Временный файл журнала является символической ссылкой."
            )
        }

        val properties = Properties().apply {

            setProperty("format", FORMAT)
            setProperty("formatVersion", FORMAT_VERSION.toString())

            setProperty("operationId", journal.operationId)
            setProperty("projectPath", journal.projectPath)

            setProperty("targetVersionId", journal.targetVersionId)
            setProperty(
                "backupVersionId",
                journal.backupVersionId.orEmpty()
            )

            setProperty("workspacePath", journal.workspacePath)

            setProperty("state", journal.state.name)

            setProperty(
                "startedAtMillis",
                journal.startedAtMillis.toString()
            )

            setProperty(
                "updatedAtMillis",
                journal.updatedAtMillis.toString()
            )
        }

        try {

            temporary.outputStream().use { output ->
                properties.store(
                    output,
                    "ScriptFX Studio restore journal"
                )
            }

            try {

                Files.move(
                    temporary.toPath(),
                    destination.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
                )

            } catch (error: java.nio.file.AtomicMoveNotSupportedException) {

                Files.move(
                    temporary.toPath(),
                    destination.toPath(),
                    StandardCopyOption.REPLACE_EXISTING
                )
            }

        } finally {

            if (temporary.exists()) {
                temporary.delete()
            }
        }
    }

    // =====================================================
    // JOURNAL PATH
    // =====================================================

    private fun journalFile(projectRoot: File): File {

        val key = sha256(
            projectRoot.canonicalPath
        )

        val journalsRoot = File(
            dataRoot,
            "RestoreJournals"
        )

        val projectJournalDirectory = File(
            journalsRoot,
            key
        )

        return File(
            projectJournalDirectory,
            JOURNAL_FILE
        )
    }

    // =====================================================
    // DIRECTORY VALIDATION
    // =====================================================

    private fun ensureSafeDirectory(
        directory: File
    ) {

        val journalsRoot = File(
            dataRoot,
            "RestoreJournals"
        )

        if (Files.isSymbolicLink(dataRoot.toPath())) {
            throw IOException(
                "Каталог данных Studio является символической ссылкой."
            )
        }

        if (!journalsRoot.exists() && !journalsRoot.mkdirs()) {
            throw IOException(
                "Не удалось создать каталог журналов восстановления."
            )
        }

        if (Files.isSymbolicLink(journalsRoot.toPath())) {
            throw IOException(
                "Каталог журналов восстановления является символической ссылкой."
            )
        }

        if (!directory.exists() && !directory.mkdirs()) {
            throw IOException(
                "Не удалось создать каталог журнала."
            )
        }

        if (Files.isSymbolicLink(directory.toPath())) {
            throw IOException(
                "Каталог журнала является символической ссылкой."
            )
        }

        if (
            directory.canonicalFile.parentFile !=
            journalsRoot.canonicalFile
        ) {
            throw IOException(
                "Каталог журнала находится вне разрешённого расположения."
            )
        }
    }

    // =====================================================
    // VALIDATION
    // =====================================================

    private fun validateJournal(
        journal: Journal
    ) {

        if (
            !runCatching {
                UUID.fromString(journal.operationId)
            }.isSuccess
        ) {
            throw IOException(
                "Некорректный ID операции восстановления."
            )
        }

        requireValidVersionId(
            journal.targetVersionId
        )

        journal.backupVersionId?.let {
            requireValidVersionId(it)
        }

        if (
            journal.projectPath.isBlank() ||
            journal.workspacePath.isBlank()
        ) {
            throw IOException(
                "В журнале отсутствует путь проекта или рабочей папки."
            )
        }

        if (
            journal.startedAtMillis <= 0L ||
            journal.updatedAtMillis < journal.startedAtMillis
        ) {
            throw IOException(
                "Некорректное время в журнале восстановления."
            )
        }
    }

    private fun requireValidVersionId(
        id: String
    ) {

        if (!VERSION_ID_PATTERN.matches(id)) {
            throw IOException(
                "Некорректный ID версии проекта."
            )
        }
    }

    private fun required(
        properties: Properties,
        key: String
    ): String {

        return properties.getProperty(key)
            ?.takeIf {
                it.isNotBlank()
            }
            ?: throw IOException(
                "В журнале отсутствует поле '$key'."
            )
    }

    private fun requiredLong(
        properties: Properties,
        key: String
    ): Long {

        return required(
            properties,
            key
        ).toLongOrNull()
            ?: throw IOException(
                "Некорректное числовое поле '$key' в журнале."
            )
    }

    // =====================================================
    // HASH
    // =====================================================

    private fun sha256(
        value: String
    ): String {

        val digest = MessageDigest
            .getInstance("SHA-256")
            .digest(
                value.toByteArray(Charsets.UTF_8)
            )

        return digest.joinToString("") {
            "%02x".format(it)
        }
    }
}