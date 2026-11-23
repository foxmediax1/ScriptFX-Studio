
package net.foxmediax.app.project

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

import net.foxmediax.utils.nodes.NodeGraph
import net.foxmediax.utils.nodes.NodeGraphHistory

import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

class GraphHistoryStore(
    private val dataRoot: File = ProjectService().studioDataRoot()
) {

    @Serializable
    private data class StoredHistory(
        val format: String,
        val version: Int,
        val projectPath: String,
        val scenarioPath: String,
        val baselineFingerprint: String,
        val graphFingerprint: String,
        val undo: List<String>,
        val redo: List<String>,
        val savedAtMillis: Long
    )

    private companion object {
        const val FORMAT = "scriptfx-studio-graph-history"
        const val VERSION = 1
        const val MAX_HISTORY = 100
        const val MAX_FILE_SIZE = 128L * 1024L * 1024L
    }

    private val json = Json {
        prettyPrint = false
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    // =====================================================
    // SAVE
    // =====================================================

    fun save(
        projectRoot: File,
        scenarioFile: File,
        baselineFingerprint: String,
        currentGraphJson: String,
        snapshot: NodeGraphHistory.Snapshot
    ) {

        require(
            Regex("^[0-9a-f]{64}$").matches(baselineFingerprint)
        ) {
            "Некорректная контрольная сумма сценария."
        }

        require(
            snapshot.undo.size <= MAX_HISTORY &&
                    snapshot.redo.size <= MAX_HISTORY
        ) {
            "Превышен допустимый размер истории графа."
        }

        // Проверяем текущий граф.
        NodeGraph.fromJson(currentGraphJson)

        // Проверяем все сохранённые состояния до записи.
        (snapshot.undo + snapshot.redo).forEach {
            NodeGraph.fromJson(it)
        }

        // Пустую историю хранить не нужно.
        if (
            snapshot.undo.isEmpty() &&
            snapshot.redo.isEmpty()
        ) {
            clear(projectRoot, scenarioFile)
            return
        }

        val (root, scenario) = resolvePaths(
            projectRoot,
            scenarioFile
        )

        val file = getHistoryFile(root, scenario)
        val directory = file.parentFile
            ?: throw IOException(
                "Не удалось определить папку истории."
            )

        if (!directory.exists() && !directory.mkdirs()) {
            throw IOException(
                "Не удалось создать папку истории."
            )
        }

        if (Files.isSymbolicLink(directory.toPath())) {
            throw IOException(
                "Папка истории является символической ссылкой."
            )
        }

        if (Files.isSymbolicLink(file.toPath())) {
            throw IOException(
                "Файл истории является символической ссылкой."
            )
        }

        val record = StoredHistory(
            format = FORMAT,
            version = VERSION,
            projectPath = root.path,
            scenarioPath = scenario.path,
            baselineFingerprint = baselineFingerprint,
            graphFingerprint = sha256(currentGraphJson),
            undo = snapshot.undo,
            redo = snapshot.redo,
            savedAtMillis = System.currentTimeMillis()
        )

        val temporaryFile = File(
            directory,
            "${file.name}.tmp"
        )

        if (Files.isSymbolicLink(temporaryFile.toPath())) {
            throw IOException(
                "Временный файл истории является символической ссылкой."
            )
        }

        try {

            val content = json.encodeToString(record)

            if (
                content.toByteArray(StandardCharsets.UTF_8).size >
                MAX_FILE_SIZE
            ) {
                throw IOException(
                    "Размер истории графа превышает допустимый предел."
                )
            }

            temporaryFile.writeText(
                content,
                StandardCharsets.UTF_8
            )

            try {

                Files.move(
                    temporaryFile.toPath(),
                    file.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE
                )

            } catch (_: AtomicMoveNotSupportedException) {

                Files.move(
                    temporaryFile.toPath(),
                    file.toPath(),
                    StandardCopyOption.REPLACE_EXISTING
                )
            }

        } finally {

            if (temporaryFile.exists()) {
                temporaryFile.delete()
            }
        }
    }

    // =====================================================
    // LOAD
    // =====================================================

    fun load(
        projectRoot: File,
        scenarioFile: File,
        expectedBaselineFingerprint: String,
        expectedGraphJson: String
    ): NodeGraphHistory.Snapshot? {

        return try {

            val (root, scenario) = resolvePaths(
                projectRoot,
                scenarioFile
            )

            val file = getHistoryFile(root, scenario)

            if (!file.isFile) return null

            if (Files.isSymbolicLink(file.toPath())) {
                return null
            }

            if (file.length() > MAX_FILE_SIZE) {
                return null
            }

            // Текущий граф также должен быть корректным.
            NodeGraph.fromJson(expectedGraphJson)

            val record = json.decodeFromString<StoredHistory>(
                file.readText(StandardCharsets.UTF_8)
            )

            if (record.format != FORMAT) return null
            if (record.version != VERSION) return null

            if (record.projectPath != root.path) return null
            if (record.scenarioPath != scenario.path) return null

            if (
                record.baselineFingerprint !=
                expectedBaselineFingerprint
            ) {
                return null
            }

            // История не должна относиться к другому состоянию графа.
            if (
                record.graphFingerprint !=
                sha256(expectedGraphJson)
            ) {
                return null
            }

            if (
                record.undo.size > MAX_HISTORY ||
                record.redo.size > MAX_HISTORY
            ) {
                return null
            }

            // Проверяем каждый снимок.
            (record.undo + record.redo).forEach {
                NodeGraph.fromJson(it)
            }

            NodeGraphHistory.Snapshot(
                undo = record.undo,
                redo = record.redo
            )

        } catch (_: Exception) {
            null
        }
    }

    // =====================================================
    // CLEAR
    // =====================================================

    fun clear(
        projectRoot: File,
        scenarioFile: File
    ) {

        val (root, scenario) = resolvePaths(
            projectRoot,
            scenarioFile
        )

        val file = getHistoryFile(root, scenario)

        if (Files.isSymbolicLink(file.toPath())) {
            throw IOException(
                "Файл истории является символической ссылкой."
            )
        }

        if (file.exists() && !file.delete()) {
            throw IOException(
                "Не удалось удалить историю графа."
            )
        }
    }

    // =====================================================
    // PATH RESOLUTION
    // =====================================================

    private fun resolvePaths(
        projectRoot: File,
        scenarioFile: File
    ): Pair<File, File> {

        val root = projectRoot.canonicalFile
        val scenario = scenarioFile.canonicalFile

        val rootPath = root.toPath()
        val scenarioPath = scenario.toPath()

        require(
            scenarioPath.startsWith(rootPath) &&
                    scenarioPath != rootPath
        ) {
            "Сценарий находится за пределами проекта."
        }

        require(
            scenario.extension.equals(
                "sfxs",
                ignoreCase = true
            )
        ) {
            "Ожидался файл сценария .sfxs."
        }

        return root to scenario
    }

    // =====================================================
    // HISTORY FILE
    // =====================================================

    private fun getHistoryFile(
        projectRoot: File,
        scenarioFile: File
    ): File {

        val key = sha256(
            "${projectRoot.path}\n${scenarioFile.path}"
        )

        return File(
            File(dataRoot, "GraphHistory"),
            "$key.json"
        )
    }

    // =====================================================
    // SHA-256
    // =====================================================

    private fun sha256(value: String): String {

        val digest = MessageDigest.getInstance("SHA-256")

        val bytes = digest.digest(
            value.toByteArray(StandardCharsets.UTF_8)
        )

        return bytes.joinToString("") {
            "%02x".format(it.toInt() and 0xFF)
        }
    }
}
