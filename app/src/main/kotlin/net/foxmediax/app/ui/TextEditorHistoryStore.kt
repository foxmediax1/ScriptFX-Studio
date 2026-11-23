
package net.foxmediax.app.ui

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

import net.foxmediax.app.project.ProjectService

import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.Locale

class TextEditorHistoryStore(
    private val dataRoot: File = ProjectService().studioDataRoot()
) {

    data class RestoredHistory(
        val baselineText: String,
        val snapshot: TextEditorHistory.Snapshot
    )

    @Serializable
    private data class StoredHistory(
        val format: String,
        val version: Int,
        val baselineText: String,
        val snapshot: TextEditorHistory.Snapshot
    )

    private companion object {
        const val FORMAT = "scriptfx-studio-text-history"
        const val VERSION = 1
        const val MAX_FILE_SIZE = 64L * 1024L * 1024L
    }

    private val json = Json {
        prettyPrint = false
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    // =====================================================
    // LOAD
    // =====================================================

    fun load(
        projectRoot: File,
        scenarioFile: File,
        expectedBaselineText: String
    ): RestoredHistory? {

        return try {
            val file = getHistoryFile(
                projectRoot,
                scenarioFile
            )

            if (!file.isFile) return null

            if (Files.isSymbolicLink(file.toPath())) {
                return null
            }

            if (file.length() > MAX_FILE_SIZE) {
                return null
            }

            val stored = json.decodeFromString<StoredHistory>(
                file.readText(StandardCharsets.UTF_8)
            )

            if (stored.format != FORMAT) return null
            if (stored.version != VERSION) return null

            // Если исходный файл изменился извне,
            // старая история для него больше не подходит.
            if (stored.baselineText != expectedBaselineText) {
                return null
            }

            RestoredHistory(
                baselineText = stored.baselineText,
                snapshot = stored.snapshot
            )

        } catch (_: Exception) {
            null
        }
    }

    // =====================================================
    // SAVE
    // =====================================================

    fun save(
        projectRoot: File,
        scenarioFile: File,
        baselineText: String,
        snapshot: TextEditorHistory.Snapshot
    ) {

        val file = getHistoryFile(
            projectRoot,
            scenarioFile
        )

        val stored = StoredHistory(
            format = FORMAT,
            version = VERSION,
            baselineText = baselineText,
            snapshot = snapshot
        )

        val directory = file.parentFile
            ?: throw IOException(
                "Не удалось определить папку истории."
            )

        if (!directory.exists() && !directory.mkdirs()) {
            throw IOException(
                "Не удалось создать папку истории."
            )
        }

        val temporaryFile = File(
            directory,
            "${file.name}.tmp"
        )

        try {
            temporaryFile.writeText(
                json.encodeToString(stored),
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
    // CLEAR
    // =====================================================

    fun clear(
        projectRoot: File,
        scenarioFile: File
    ) {

        val file = getHistoryFile(
            projectRoot,
            scenarioFile
        )

        if (
            file.exists() &&
            !file.delete()
        ) {
            throw IOException(
                "Не удалось очистить историю сценария."
            )
        }
    }

    // =====================================================
    // HISTORY FILE
    // =====================================================

    private fun getHistoryFile(
        projectRoot: File,
        scenarioFile: File
    ): File {

        val rootPath = projectRoot.canonicalFile.toPath()
        val scenarioPath = scenarioFile.canonicalFile.toPath()

        require(
            scenarioPath.startsWith(rootPath) &&
                    scenarioPath != rootPath
        ) {
            "Сценарий находится за пределами проекта."
        }

        val normalizedRoot = rootPath
            .toString()
            .lowercase(Locale.ROOT)

        val normalizedScenario = scenarioPath
            .toString()
            .lowercase(Locale.ROOT)

        val key = sha256(
            "$normalizedRoot\n$normalizedScenario"
        )

        return File(
            File(dataRoot, "TextHistory"),
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
