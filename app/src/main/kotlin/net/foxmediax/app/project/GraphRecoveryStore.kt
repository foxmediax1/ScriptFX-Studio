
package net.foxmediax.app.project

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

import net.foxmediax.utils.nodes.NodeGraph

import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

class GraphRecoveryStore(
    private val dataRoot: File = ProjectService().studioDataRoot()
) {

    data class Recovery(
        val graph: NodeGraph,
        val savedAtMillis: Long
    )

    @Serializable
    private data class RecoveryRecord(
        val format: String,
        val version: Int,
        val projectPath: String,
        val scenarioPath: String,
        val baselineFingerprint: String,
        val graphJson: String,
        val savedAtMillis: Long
    )

    private companion object {
        const val FORMAT = "scriptfx-studio-graph-recovery"
        const val VERSION = 1
        const val MAX_FILE_SIZE = 64L * 1024L * 1024L
    }

    private val json = Json {
        prettyPrint = false
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    // =====================================================
    // BASELINE FINGERPRINT
    // =====================================================

    /**
     * Создаёт контрольную сумму исходного состояния сценария.
     *
     * Проверяются:
     * - основной .sfxs файл;
     * - соответствующий .graph.json файл.
     *
     * Если исходные файлы изменились извне, старая
     * резервная копия не будет автоматически восстановлена.
     */
    fun fingerprint(
        projectRoot: File,
        scenarioFile: File
    ): String {

        val (root, scenario) = resolvePaths(
            projectRoot,
            scenarioFile
        )

        if (!scenario.isFile) {
            throw IOException(
                "Файл сценария не найден: ${scenario.absolutePath}"
            )
        }

        val sidecar = File(
            scenario.parentFile,
            "${scenario.nameWithoutExtension}.graph.json"
        )

        if (Files.isSymbolicLink(sidecar.toPath())) {
            throw IOException(
                "Файл графа является символической ссылкой."
            )
        }

        val sidecarHash = when {
            sidecar.isFile -> sha256(sidecar)

            sidecar.exists() -> throw IOException(
                "Файл графа имеет неправильный тип."
            )

            else -> "missing"
        }

        val sourceHash = sha256(scenario)

        return sha256(
            "project=${root.path}\n" +
                    "scenario=${scenario.path}\n" +
                    "source=$sourceHash\n" +
                    "sidecar=$sidecarHash"
        )
    }

    // =====================================================
    // SAVE
    // =====================================================

    /**
     * Сохраняет резервную копию графа.
     *
     * graphJson должен содержать корректный NodeGraph JSON.
     */
    fun save(
        projectRoot: File,
        scenarioFile: File,
        baselineFingerprint: String,
        graphJson: String
    ) {

        require(
            Regex("^[0-9a-f]{64}$").matches(
                baselineFingerprint
            )
        ) {
            "Некорректная контрольная сумма исходного сценария."
        }

        if (graphJson.toByteArray(StandardCharsets.UTF_8).size > MAX_FILE_SIZE) {
            throw IOException(
                "Размер резервной копии слишком большой."
            )
        }

        // Проверяем, что JSON действительно содержит граф.
        NodeGraph.fromJson(graphJson)

        val (root, scenario) = resolvePaths(
            projectRoot,
            scenarioFile
        )

        val file = getRecoveryFile(root, scenario)
        val directory = file.parentFile
            ?: throw IOException(
                "Не удалось определить папку восстановления."
            )

        if (!directory.exists() && !directory.mkdirs()) {
            throw IOException(
                "Не удалось создать папку восстановления."
            )
        }

        if (Files.isSymbolicLink(directory.toPath())) {
            throw IOException(
                "Папка восстановления является символической ссылкой."
            )
        }

        if (file.exists() && Files.isSymbolicLink(file.toPath())) {
            throw IOException(
                "Файл восстановления является символической ссылкой."
            )
        }

        val record = RecoveryRecord(
            format = FORMAT,
            version = VERSION,
            projectPath = root.path,
            scenarioPath = scenario.path,
            baselineFingerprint = baselineFingerprint,
            graphJson = graphJson,
            savedAtMillis = System.currentTimeMillis()
        )

        val temporaryFile = File(
            directory,
            "${file.name}.tmp"
        )

        if (Files.isSymbolicLink(temporaryFile.toPath())) {
            throw IOException(
                "Временный файл восстановления является символической ссылкой."
            )
        }

        try {

            temporaryFile.writeText(
                json.encodeToString(record),
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

    /**
     * Загружает резервную копию только в том случае,
     * если исходные файлы с момента её создания не изменились.
     */
    fun load(
        projectRoot: File,
        scenarioFile: File,
        expectedBaselineFingerprint: String
    ): Recovery? {

        return try {

            val (root, scenario) = resolvePaths(
                projectRoot,
                scenarioFile
            )

            val file = getRecoveryFile(root, scenario)

            if (!file.isFile) return null

            if (Files.isSymbolicLink(file.toPath())) {
                return null
            }

            if (file.length() > MAX_FILE_SIZE) {
                return null
            }

            val record = json.decodeFromString<RecoveryRecord>(
                file.readText(StandardCharsets.UTF_8)
            )

            if (record.format != FORMAT) return null
            if (record.version != VERSION) return null

            if (record.projectPath != root.path) {
                return null
            }

            if (record.scenarioPath != scenario.path) {
                return null
            }

            if (
                record.baselineFingerprint !=
                expectedBaselineFingerprint
            ) {
                return null
            }

            val restoredGraph = NodeGraph.fromJson(
                record.graphJson
            )

            Recovery(
                graph = restoredGraph,
                savedAtMillis = record.savedAtMillis
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

        val file = getRecoveryFile(root, scenario)

        if (Files.isSymbolicLink(file.toPath())) {
            throw IOException(
                "Файл восстановления является символической ссылкой."
            )
        }

        if (file.exists() && !file.delete()) {
            throw IOException(
                "Не удалось удалить резервную копию графа."
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
    // RECOVERY FILE
    // =====================================================

    private fun getRecoveryFile(
        projectRoot: File,
        scenarioFile: File
    ): File {

        val key = sha256(
            "${projectRoot.path}\n${scenarioFile.path}"
        )

        return File(
            File(dataRoot, "Recovery"),
            "graph_$key.json"
        )
    }

    // =====================================================
    // SHA-256
    // =====================================================

    private fun sha256(file: File): String {

        val digest = MessageDigest.getInstance("SHA-256")

        file.inputStream().buffered().use { input ->

            val buffer = ByteArray(8192)

            while (true) {

                val read = input.read(buffer)

                if (read < 0) break

                digest.update(buffer, 0, read)
            }
        }

        return digest.digest().joinToString("") {
            "%02x".format(it.toInt() and 0xFF)
        }
    }

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
