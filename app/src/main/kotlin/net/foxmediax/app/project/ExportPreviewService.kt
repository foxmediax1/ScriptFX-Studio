
package net.foxmediax.app.project

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.security.MessageDigest

object ExportPreviewService {

    enum class Action(val title: String) {
        ADD("Добавить"),
        UPDATE("Обновить"),
        UNCHANGED("Без изменений"),
        REMOVE("Удалить"),
        PRESERVE("Сохранить"),
        CONFLICT("Конфликт")
    }

    data class Entry(
        val path: String,
        val action: Action,
        val description: String
    )

    data class Plan(
        val entries: List<Entry>,
        val validation: ExportService.ValidationReport?,
        val error: String? = null
    ) {
        val additions: Int
            get() = entries.count { it.action == Action.ADD }

        val updates: Int
            get() = entries.count { it.action == Action.UPDATE }

        val unchanged: Int
            get() = entries.count { it.action == Action.UNCHANGED }

        val removals: Int
            get() = entries.count { it.action == Action.REMOVE }

        val preserved: Int
            get() = entries.count { it.action == Action.PRESERVE }

        val conflicts: Int
            get() = entries.count { it.action == Action.CONFLICT }

        val canExport: Boolean
            get() = error == null &&
                    validation?.hasErrors == false &&
                    conflicts == 0
    }

    private const val MANIFEST_FILE =
        ".scriptfx-studio-export-manifest.json"

    private const val MANIFEST_FORMAT =
        "scriptfx-studio-export-manifest"

    private const val MANIFEST_VERSION = 1

    private val json = Json {
        ignoreUnknownKeys = true
    }

    // =====================================================
    // CREATE EXPORT PLAN
    // =====================================================

    fun createPlan(
        projectRoot: File,
        targetDir: File,
        subfolder: String? = null
    ): Plan {

        ExportService.validateTarget(targetDir)?.let {
            return Plan(
                entries = emptyList(),
                validation = null,
                error = it
            )
        }

        if (!projectRoot.isDirectory) {
            return Plan(
                entries = emptyList(),
                validation = null,
                error = "Папка проекта не найдена."
            )
        }

        if (!File(projectRoot, "scriptfx.project.json").isFile) {
            return Plan(
                entries = emptyList(),
                validation = null,
                error = "В проекте отсутствует scriptfx.project.json."
            )
        }

        val validation = ExportService.validateProject(
            projectRoot = projectRoot,
            targetDir = targetDir,
            subfolder = subfolder
        )

        if (validation.hasErrors) {
            return Plan(
                entries = emptyList(),
                validation = validation,
                error = "Экспорт невозможен: проект содержит " +
                        "${validation.errors} ошибок."
            )
        }

        val projectName = sanitize(
            subfolder ?: projectRoot.name
        )

        val destinationRoot = File(
            targetDir,
            "config/scriptfx/projects/$projectName"
        )

        if (!isSafePath(targetDir, destinationRoot)) {
            return Plan(
                entries = emptyList(),
                validation = validation,
                error = "Небезопасный путь папки экспорта."
            )
        }

        if (
            destinationRoot.exists() &&
            !destinationRoot.isDirectory
        ) {
            return Plan(
                entries = emptyList(),
                validation = validation,
                error = "Путь экспорта занят файлом."
            )
        }

        // -------------------------------------------------
        // READ OWNERSHIP MANIFEST
        // -------------------------------------------------

        val manifestFile = File(
            destinationRoot,
            MANIFEST_FILE
        )

        val previousScripts = try {

            when {
                Files.isSymbolicLink(manifestFile.toPath()) ->
                    throw IOException(
                        "Манифест является символической ссылкой."
                    )

                manifestFile.exists() && !manifestFile.isFile ->
                    throw IOException(
                        "Манифест не является файлом."
                    )

                manifestFile.exists() ->
                    readManifest(manifestFile)

                else ->
                    emptyMap()
            }

        } catch (e: Exception) {
            return Plan(
                entries = emptyList(),
                validation = validation,
                error = "Не удалось прочитать манифест экспорта: " +
                        (e.message ?: "неизвестная ошибка")
            )
        }

        // -------------------------------------------------
        // COLLECT CURRENT SCRIPTS
        // -------------------------------------------------

        val currentScripts = try {
            collectCurrentScripts(projectRoot)
        } catch (e: Exception) {
            return Plan(
                entries = emptyList(),
                validation = validation,
                error = "Не удалось собрать список сценариев: " +
                        (e.message ?: "неизвестная ошибка")
            )
        }

        val entries = mutableListOf<Entry>()

        // -------------------------------------------------
        // CURRENT SCRIPTS
        // -------------------------------------------------

        currentScripts.forEach { (relativePath, sourceFile) ->

            val destinationFile = File(
                destinationRoot,
                relativePath
            )

            if (!isSafePath(destinationRoot, destinationFile)) {
                entries.add(
                    Entry(
                        path = relativePath,
                        action = Action.CONFLICT,
                        description =
                            "Путь назначения выходит за пределы папки проекта."
                    )
                )

                return@forEach
            }

            if (Files.isSymbolicLink(destinationFile.toPath())) {
                entries.add(
                    Entry(
                        path = relativePath,
                        action = Action.CONFLICT,
                        description =
                            "Файл назначения является символической ссылкой."
                    )
                )

                return@forEach
            }

            // Файл ещё не существует в Minecraft.
            if (!destinationFile.exists()) {
                entries.add(
                    Entry(
                        path = relativePath,
                        action = Action.ADD,
                        description =
                            "Сценарий ещё не установлен в Minecraft."
                    )
                )

                return@forEach
            }

            if (!destinationFile.isFile) {
                entries.add(
                    Entry(
                        path = relativePath,
                        action = Action.CONFLICT,
                        description =
                            "Вместо файла назначения существует папка или другой объект."
                    )
                )

                return@forEach
            }

            val previousHash = previousScripts[relativePath]

            // Сценарий уже существует, но Studio
            // не имеет подтверждения, что владеет им.
            if (previousHash == null) {
                entries.add(
                    Entry(
                        path = relativePath,
                        action = Action.CONFLICT,
                        description =
                            "Файл уже существует, но отсутствует в манифесте Studio. " +
                                    "Автоматическая перезапись запрещена."
                    )
                )

                return@forEach
            }

            val sourceHash = sha256(sourceFile)
            val destinationHash = sha256(destinationFile)

            when {

                // Файлы полностью совпадают.
                destinationHash == sourceHash -> {
                    entries.add(
                        Entry(
                            path = relativePath,
                            action = Action.UNCHANGED,
                            description =
                                "Содержимое проекта и Minecraft совпадает."
                        )
                    )
                }

                // Minecraft-файл не менялся после прошлого экспорта.
                destinationHash == previousHash -> {
                    entries.add(
                        Entry(
                            path = relativePath,
                            action = Action.UPDATE,
                            description =
                                "Файл не изменялся вручную. Его можно обновить."
                        )
                    )
                }

                // Файл был изменён пользователем.
                else -> {
                    entries.add(
                        Entry(
                            path = relativePath,
                            action = Action.CONFLICT,
                            description =
                                "Файл изменён после предыдущего экспорта. " +
                                        "Studio не будет перезаписывать его."
                        )
                    )
                }
            }
        }

        // -------------------------------------------------
        // REMOVED SCRIPTS
        // -------------------------------------------------

        val stalePaths = previousScripts.keys.filterNot {
            it in currentScripts
        }

        stalePaths.forEach { relativePath ->

            val staleFile = File(
                destinationRoot,
                relativePath
            )

            // Если файл отсутствует и не является битой
            // символической ссылкой — удалять нечего.
            if (
                !staleFile.exists() &&
                !Files.isSymbolicLink(staleFile.toPath())
            ) {
                return@forEach
            }

            if (
                !isSafePath(destinationRoot, staleFile) ||
                Files.isSymbolicLink(staleFile.toPath()) ||
                !staleFile.isFile
            ) {
                entries.add(
                    Entry(
                        path = relativePath,
                        action = Action.PRESERVE,
                        description =
                            "Небезопасный или нестандартный объект. " +
                                    "Studio оставит его нетронутым."
                    )
                )

                return@forEach
            }

            val currentHash = sha256(staleFile)
            val previousHash = previousScripts[relativePath]

            if (currentHash == previousHash) {
                entries.add(
                    Entry(
                        path = relativePath,
                        action = Action.REMOVE,
                        description =
                            "Сценарий удалён из проекта и не изменялся вручную."
                    )
                )
            } else {
                entries.add(
                    Entry(
                        path = relativePath,
                        action = Action.PRESERVE,
                        description =
                            "Сценарий удалён из проекта, но изменён вручную. " +
                                    "Studio сохранит его."
                    )
                )
            }
        }

        return Plan(
            entries = entries.sortedBy { it.path },
            validation = validation
        )
    }

    // =====================================================
    // COLLECT PROJECT SCRIPTS
    // =====================================================

    private fun collectCurrentScripts(
        projectRoot: File
    ): Map<String, File> {

        val result = linkedMapOf<String, File>()

        fun add(
            path: String,
            file: File
        ) {
            val normalized = normalizeManagedPath(path)
                ?: throw IOException(
                    "Недопустимый путь сценария: $path"
                )

            if (result.put(normalized, file) != null) {
                throw IOException(
                    "Повторяющийся путь сценария: $normalized"
                )
            }
        }

        // Studio scenarios/ -> Runtime scripts/
        val scenariosRoot = File(
            projectRoot,
            "scenarios"
        )

        if (scenariosRoot.isDirectory) {
            scenariosRoot.walkTopDown()
                .filter {
                    it.isFile && it.extension == "sfxs"
                }
                .forEach { file ->

                    val relative = file
                        .relativeTo(scenariosRoot)
                        .invariantSeparatorsPath

                    add(
                        "scripts/$relative",
                        file
                    )
                }
        }

        // Уже существующие scripts/ остаются на месте.
        val scriptsRoot = File(
            projectRoot,
            "scripts"
        )

        if (scriptsRoot.isDirectory) {
            scriptsRoot.walkTopDown()
                .filter {
                    it.isFile && it.extension == "sfxs"
                }
                .forEach { file ->

                    val relative = file
                        .relativeTo(scriptsRoot)
                        .invariantSeparatorsPath

                    add(
                        "scripts/$relative",
                        file
                    )
                }
        }

        return result.toSortedMap()
    }

    // =====================================================
    // READ MANIFEST
    // =====================================================

    private fun readManifest(
        file: File
    ): Map<String, String> {

        val root = json
            .parseToJsonElement(file.readText())
            .jsonObject

        val format = root["format"]
            ?.jsonPrimitive
            ?.content

        if (format != MANIFEST_FORMAT) {
            throw IOException(
                "Неизвестный формат манифеста."
            )
        }

        val version = root["version"]
            ?.jsonPrimitive
            ?.int

        if (version != MANIFEST_VERSION) {
            throw IOException(
                "Неподдерживаемая версия манифеста: $version"
            )
        }

        val scripts = root["managedScripts"]
            ?.jsonObject
            ?: throw IOException(
                "В манифесте отсутствует managedScripts."
            )

        val result = linkedMapOf<String, String>()

        scripts.forEach { (rawPath, rawHash) ->

            val path = normalizeManagedPath(rawPath)
                ?: throw IOException(
                    "Недопустимый путь в манифесте: $rawPath"
                )

            val hash = rawHash.jsonPrimitive.content

            if (!Regex("""^[0-9a-f]{64}$""").matches(hash)) {
                throw IOException(
                    "Некорректная контрольная сумма: $path"
                )
            }

            result[path] = hash
        }

        return result
    }

    // =====================================================
    // PATH VALIDATION
    // =====================================================

    private fun normalizeManagedPath(
        path: String
    ): String? {

        if (
            path.isBlank() ||
            path.startsWith("/") ||
            path.contains('\\') ||
            path.contains(':') ||
            !path.startsWith("scripts/") ||
            !path.endsWith(".sfxs")
        ) {
            return null
        }

        val segments = path.split('/')

        if (
            segments.size < 2 ||
            segments.first() != "scripts" ||
            segments.drop(1).any {
                it.isBlank() ||
                        it == "." ||
                        it == ".."
            }
        ) {
            return null
        }

        return path
    }

    private fun isSafePath(
        root: File,
        candidate: File
    ): Boolean {
        return runCatching {
            val rootPath = root.canonicalFile.toPath()
            val candidatePath = candidate.canonicalFile.toPath()

            candidatePath != rootPath &&
                    candidatePath.startsWith(rootPath)
        }.getOrDefault(false)
    }

    // =====================================================
    // HASH
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

    // =====================================================
    // SANITIZE PROJECT NAME
    // =====================================================

    private fun sanitize(name: String): String {

        val sanitized = name
            .replace(
                Regex("""[\\/:*?"<>|]"""),
                "_"
            )
            .trim()

        return if (
            sanitized.isBlank() ||
            sanitized == "." ||
            sanitized == ".."
        ) {
            "project"
        } else {
            sanitized
        }
    }
}
