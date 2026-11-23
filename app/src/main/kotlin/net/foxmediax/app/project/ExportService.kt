
package net.foxmediax.app.project

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

import net.foxmediax.utils.nodes.GraphIssue
import net.foxmediax.utils.nodes.GraphIssueSeverity
import net.foxmediax.utils.nodes.GraphValidator
import net.foxmediax.utils.nodes.NodeGraph
import net.foxmediax.utils.nodes.NodeKind
import net.foxmediax.utils.nodes.ScriptNodeSync

import java.io.File
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

object ExportService {

    data class ProjectValidationIssue(
        val scenarioFile: String,
        val issue: GraphIssue
    )

    data class ValidationReport(
        val scenariosChecked: Int,
        val issues: List<ProjectValidationIssue>
    ) {
        val errors: Int
            get() = issues.count {
                it.issue.severity == GraphIssueSeverity.ERROR
            }

        val warnings: Int
            get() = issues.count {
                it.issue.severity == GraphIssueSeverity.WARNING
            }

        val infos: Int
            get() = issues.count {
                it.issue.severity == GraphIssueSeverity.INFO
            }

        val hasErrors: Boolean
            get() = errors > 0

        val hasIssues: Boolean
            get() = issues.isNotEmpty()
    }

    data class Result(
        val success: Boolean,
        val filesCopied: Int,
        val message: String,
        val validation: ValidationReport? = null
    )

    private data class ScenarioRecord(
        val file: File,
        val relativePath: String,
        val scriptName: String,
        val graph: NodeGraph
    )

    private data class CallReference(
        val sourceName: String,
        val targetName: String,
        val sourceFile: String,
        val nodeId: String
    )

    private data class CallTargetResult(
        val name: String? = null,
        val error: String? = null
    )

    // =====================================================
    // EXPORT OWNERSHIP MANIFEST
    // =====================================================

    private const val EXPORT_MANIFEST_FILE =
        ".scriptfx-studio-export-manifest.json"

    private const val EXPORT_MANIFEST_FORMAT =
        "scriptfx-studio-export-manifest"

    private const val EXPORT_MANIFEST_VERSION = 1

    private val exportManifestJson = Json {
        prettyPrint = true
    }

    /**
     * Разрешены только относительные пути runtime-сценариев:
     *
     * scripts/main.sfxs
     * scripts/nested/reward.sfxs
     *
     * Пути за пределы scripts/ запрещены.
     */
    private fun normalizeManagedScriptPath(
        path: String
    ): String? {

        if (path.isBlank()) return null

        if (
            path.startsWith("/") ||
            path.contains('\\') ||
            path.contains(':')
        ) {
            return null
        }

        if (!path.startsWith("scripts/")) {
            return null
        }

        if (!path.endsWith(".sfxs")) {
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

    /**
     * Проверяет, что candidate находится внутри root.
     *
     * canonicalPath учитывает существующие символические ссылки.
     */
    private fun isSafeDestinationPath(
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

    /**
     * SHA-256 файла.
     */
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

        return digest.digest().joinToString("") { byte ->
            "%02x".format(byte.toInt() and 0xFF)
        }
    }

    /**
     * Загружает предыдущий манифест.
     *
     * Повреждённый или неизвестный манифест блокирует экспорт,
     * чтобы не выполнять небезопасную очистку файлов.
     */
    private fun readExportManifest(
        file: File
    ): Map<String, String> {

        val root = exportManifestJson
            .parseToJsonElement(file.readText())
            .jsonObject

        val format = root["format"]
            ?.jsonPrimitive
            ?.content

        if (format != EXPORT_MANIFEST_FORMAT) {
            throw IOException(
                "Неизвестный формат манифеста экспорта."
            )
        }

        val version = root["version"]
            ?.jsonPrimitive
            ?.int

        if (version != EXPORT_MANIFEST_VERSION) {
            throw IOException(
                "Неподдерживаемая версия манифеста экспорта: $version"
            )
        }

        val scripts = root["managedScripts"]
            ?.jsonObject
            ?: throw IOException(
                "В манифесте отсутствует managedScripts."
            )

        val result = linkedMapOf<String, String>()

        scripts.forEach { (rawPath, rawHash) ->

            val path = normalizeManagedScriptPath(rawPath)
                ?: throw IOException(
                    "Небезопасный путь в манифесте: $rawPath"
                )

            val hash = rawHash.jsonPrimitive.content

            if (!Regex("""^[0-9a-f]{64}$""").matches(hash)) {
                throw IOException(
                    "Некорректный SHA-256 для файла: $path"
                )
            }

            result[path] = hash
        }

        return result
    }

    /**
     * Атомарно записывает манифест.
     */
    private fun writeExportManifest(
        destRoot: File,
        managedScripts: Map<String, String>
    ) {

        val manifestFile = File(
            destRoot,
            EXPORT_MANIFEST_FILE
        )

        val temporaryFile = File(
            destRoot,
            "$EXPORT_MANIFEST_FILE.tmp"
        )

        if (
            !isSafeDestinationPath(destRoot, manifestFile) ||
            !isSafeDestinationPath(destRoot, temporaryFile)
        ) {
            throw SecurityException(
                "Небезопасный путь манифеста экспорта."
            )
        }

        val manifest = JsonObject(
            linkedMapOf(
                "format" to JsonPrimitive(
                    EXPORT_MANIFEST_FORMAT
                ),
                "version" to JsonPrimitive(
                    EXPORT_MANIFEST_VERSION
                ),
                "managedScripts" to JsonObject(
                    managedScripts
                        .toSortedMap()
                        .mapValues { (_, hash) ->
                            JsonPrimitive(hash)
                        }
                )
            )
        )

        temporaryFile.writeText(manifest.toString())

        try {
            try {
                Files.move(
                    temporaryFile.toPath(),
                    manifestFile.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(
                    temporaryFile.toPath(),
                    manifestFile.toPath(),
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
    // TARGET VALIDATION
    // =====================================================

    fun validateTarget(target: File): String? {
        if (!target.exists()) {
            return "Папка не существует: ${target.absolutePath}"
        }

        if (!target.isDirectory) {
            return "Это не папка: ${target.absolutePath}"
        }

        val path = target.absolutePath
            .replace('\\', '/')
            .lowercase()

        val looksLikeMinecraft =
            path.endsWith("/.minecraft") ||
                    path.endsWith("/minecraft") ||
                    path.contains("/.minecraft/") ||
                    File(target, "config").exists() ||
                    File(target, "mods").exists()

        if (!looksLikeMinecraft) {
            return "Похоже, это не папка Minecraft. " +
                    "Ожидался путь вида %APPDATA%/.minecraft"
        }

        return null
    }

    // =====================================================
    // PROJECT VALIDATION
    // =====================================================

    fun validateProject(
        projectRoot: File,
        targetDir: File? = null,
        subfolder: String? = null
    ): ValidationReport {

        val issues = mutableListOf<ProjectValidationIssue>()

        fun addIssue(
            file: String,
            severity: GraphIssueSeverity,
            message: String,
            nodeId: String? = null
        ) {
            issues.add(
                ProjectValidationIssue(
                    scenarioFile = file,
                    issue = GraphIssue(
                        severity = severity,
                        message = message,
                        nodeId = nodeId
                    )
                )
            )
        }

        fun addError(
            file: String,
            message: String,
            nodeId: String? = null
        ) {
            addIssue(
                file,
                GraphIssueSeverity.ERROR,
                message,
                nodeId
            )
        }

        if (!projectRoot.isDirectory) {
            addError(
                "Проект",
                "Папка проекта не найдена: ${projectRoot.absolutePath}"
            )

            return ValidationReport(0, issues)
        }

        // Runtime ScriptFX загружает файлы из scripts/.
        // Studio хранит сценарии в scenarios/.
        val sourceRoots = listOf(
            File(projectRoot, "scenarios"),
            File(projectRoot, "scripts")
        ).filter {
            it.isDirectory
        }

        val scenarioFiles = sourceRoots
            .flatMap { root ->
                root.walkTopDown()
                    .filter { file ->
                        file.isFile &&
                                file.extension.equals(
                                    "sfxs",
                                    ignoreCase = true
                                )
                    }
                    .toList()
            }
            .distinctBy { file ->
                runCatching {
                    file.canonicalPath
                }.getOrDefault(file.absolutePath)
            }
            .sortedBy { file ->
                file.relativeTo(projectRoot).invariantSeparatorsPath
            }

        if (scenarioFiles.isEmpty()) {
            addError(
                "Проект",
                "В проекте нет сценариев .sfxs в папках scenarios/ или scripts/."
            )

            return ValidationReport(0, issues)
        }

        // Runtime использует точное расширение lowercase .sfxs.
        scenarioFiles
            .filter { it.extension != "sfxs" }
            .forEach { file ->
                addError(
                    file.relativeTo(projectRoot).invariantSeparatorsPath,
                    "Runtime ScriptFX загружает файлы только с расширением .sfxs."
                )
            }

        val runtimeScenarioFiles = scenarioFiles.filter {
            it.extension == "sfxs"
        }

        // -------------------------------------------------
        // SCRIPT NAMES
        // -------------------------------------------------

        val localFilesByName = runtimeScenarioFiles.groupBy {
            it.nameWithoutExtension
        }

        // ScriptManager хранит скрипты глобально по basename,
        // а не по пути к папке.
        localFilesByName
            .filterValues { it.size > 1 }
            .forEach { (scriptName, files) ->
                files.forEach { file ->
                    addError(
                        file.relativeTo(projectRoot).invariantSeparatorsPath,
                        "Имя сценария '$scriptName' повторяется. " +
                                "Runtime ScriptFX использует имена без путей, " +
                                "поэтому такие файлы конфликтуют."
                    )
                }
            }

        // -------------------------------------------------
        // SCRIPTS FROM OTHER EXPORTED PROJECTS
        // -------------------------------------------------

        val externalFilesByName =
            mutableMapOf<String, MutableList<String>>()

        if (targetDir != null) {
            val projectsDir = File(
                targetDir,
                "config/scriptfx/projects"
            )

            val currentProjectFolder = sanitize(
                subfolder ?: projectRoot.name
            )

            projectsDir.listFiles()
                ?.filter { directory ->
                    directory.isDirectory &&
                            directory.name != currentProjectFolder
                }
                ?.forEach projectLoop@ { otherProject ->

                    val scriptsDir = File(
                        otherProject,
                        "scripts"
                    )

                    if (!scriptsDir.isDirectory) {
                        return@projectLoop
                    }

                    scriptsDir.walkTopDown()
                        .filter { file ->
                            file.isFile &&
                                    file.extension == "sfxs"
                        }
                        .forEach { file ->
                            externalFilesByName
                                .getOrPut(file.nameWithoutExtension) {
                                    mutableListOf()
                                }
                                .add(otherProject.name)
                        }
                }
        }

        // Конфликт имён между текущим проектом
        // и уже установленными проектами.
        localFilesByName.forEach { (scriptName, files) ->
            val externalProjects =
                externalFilesByName[scriptName].orEmpty()

            if (externalProjects.isNotEmpty()) {
                files.forEach { file ->
                    addError(
                        file.relativeTo(projectRoot).invariantSeparatorsPath,
                        "Имя сценария '$scriptName' уже используется " +
                                "другим установленным проектом: " +
                                externalProjects.distinct().joinToString() +
                                ". Runtime хранит сценарии по общему имени."
                    )
                }
            }
        }

        // -------------------------------------------------
        // LOAD AND VALIDATE GRAPHS
        // -------------------------------------------------

        val records = mutableListOf<ScenarioRecord>()

        runtimeScenarioFiles.forEach { scenarioFile ->

            val relativePath = scenarioFile
                .relativeTo(projectRoot)
                .invariantSeparatorsPath

            val sidecar = File(
                scenarioFile.parentFile,
                "${scenarioFile.nameWithoutExtension}.graph.json"
            )

            val graphResult = runCatching {
                if (sidecar.isFile) {
                    NodeGraph.fromJson(
                        sidecar.readText()
                    )
                } else {
                    ScriptNodeSync.textToGraph(
                        scenarioFile.readText()
                    )
                }
            }

            val graph = graphResult.getOrNull()

            if (graph == null) {
                addError(
                    relativePath,
                    "Не удалось прочитать граф: " +
                            (
                                    graphResult.exceptionOrNull()?.message
                                        ?: "неизвестная ошибка"
                                    )
                )

                return@forEach
            }

            records.add(
                ScenarioRecord(
                    file = scenarioFile,
                    relativePath = relativePath,
                    scriptName = scenarioFile.nameWithoutExtension,
                    graph = graph
                )
            )

            val graphIssues = runCatching {
                GraphValidator.validate(graph)
            }.getOrElse { exception ->
                listOf(
                    GraphIssue(
                        severity = GraphIssueSeverity.ERROR,
                        message = "Ошибка проверки графа: " +
                                (exception.message ?: "неизвестная ошибка")
                    )
                )
            }

            graphIssues.forEach { issue ->
                issues.add(
                    ProjectValidationIssue(
                        scenarioFile = relativePath,
                        issue = issue
                    )
                )
            }
        }

        // -------------------------------------------------
        // CALL TARGET RESOLUTION
        // -------------------------------------------------

        fun parseCallTarget(raw: String): CallTargetResult {
            val target = raw.trim()

            if (target.isBlank()) {
                return CallTargetResult(
                    error = "Не указано имя вызываемого сценария."
                )
            }

            if (
                target.contains('/') ||
                target.contains('\\')
            ) {
                return CallTargetResult(
                    error = "CALL принимает имя сценария, а не путь. " +
                            "Укажи имя файла без папок."
                )
            }

            if (
                target.contains('\n') ||
                target.contains('\r') ||
                target.startsWith('"') ||
                target.endsWith('"')
            ) {
                return CallTargetResult(
                    error = "Укажи имя сценария без кавычек и переносов строк."
                )
            }

            val hasExtension = target.endsWith(
                ".sfxs",
                ignoreCase = true
            )

            if (!hasExtension && target.contains('.')) {
                return CallTargetResult(
                    error = "Добавь расширение .sfxs к имени сценария. " +
                            "Runtime удаляет последнюю часть после точки."
                )
            }

            val scriptName = if (hasExtension) {
                target.dropLast(".sfxs".length)
            } else {
                target
            }

            if (scriptName.isBlank()) {
                return CallTargetResult(
                    error = "Имя вызываемого сценария пустое."
                )
            }

            return CallTargetResult(name = scriptName)
        }

        val calls = mutableListOf<CallReference>()

        records.forEach { record ->

            record.graph.nodes
                .filter { it.kind == NodeKind.CALL }
                .forEach nodeLoop@ { node ->

                    val rawTarget = node.params["target"].orEmpty()

                    val parsed = parseCallTarget(rawTarget)

                    if (parsed.error != null) {
                        addError(
                            record.relativePath,
                            "CALL: ${parsed.error}",
                            node.id
                        )

                        return@nodeLoop
                    }

                    val targetName = parsed.name
                        ?: return@nodeLoop

                    val localMatches =
                        localFilesByName[targetName].orEmpty()

                    val externalMatches =
                        externalFilesByName[targetName].orEmpty()

                    val totalMatches =
                        localMatches.size + externalMatches.size

                    when {
                        totalMatches == 0 -> {
                            addError(
                                record.relativePath,
                                "CALL ссылается на отсутствующий сценарий " +
                                        "'$targetName'. Проверь имя файла в " +
                                        "scenarios/ или scripts/.",
                                node.id
                            )
                        }

                        totalMatches > 1 -> {
                            addError(
                                record.relativePath,
                                "CALL '$targetName' неоднозначен: найдено " +
                                        "$totalMatches сценариев с таким именем. " +
                                        "Runtime не различает папки.",
                                node.id
                            )
                        }

                        localMatches.size == 1 -> {
                            calls.add(
                                CallReference(
                                    sourceName = record.scriptName,
                                    targetName = targetName,
                                    sourceFile = record.relativePath,
                                    nodeId = node.id
                                )
                            )
                        }

                        // Сценарий существует в другом установленном проекте.
                        // Он доступен runtime, но его граф здесь не анализируется.
                        externalMatches.size == 1 -> Unit
                    }
                }
        }

        // -------------------------------------------------
        // CROSS-SCENARIO CYCLE DETECTION
        // -------------------------------------------------

        val uniqueRecords = records
            .groupBy { it.scriptName }
            .filterValues { it.size == 1 }
            .mapValues { (_, values) -> values.first() }

        val callsBySource = calls
            .filter { reference ->
                reference.sourceName in uniqueRecords &&
                        reference.targetName in uniqueRecords
            }
            .groupBy { it.sourceName }

        val state = mutableMapOf<String, Int>()
        val stack = mutableListOf<String>()

        val reportedCycles = mutableSetOf<String>()

        fun detectScriptCycles(scriptName: String) {
            state[scriptName] = 1
            stack.add(scriptName)

            callsBySource[scriptName].orEmpty().forEach { reference ->

                when (state[reference.targetName] ?: 0) {

                    // Возврат к активному узлу означает цикл.
                    1 -> {
                        val cycleStart = stack.indexOf(
                            reference.targetName
                        )

                        val cyclePath = (
                                stack.drop(cycleStart) +
                                        reference.targetName
                                ).joinToString(" → ")

                        val reportKey =
                            "${reference.sourceFile}:${reference.nodeId}"

                        if (reportedCycles.add(reportKey)) {
                            addError(
                                reference.sourceFile,
                                "Обнаружен циклический вызов сценариев: " +
                                        cyclePath +
                                        ". Такая цепочка может бесконечно " +
                                        "запускать новые экземпляры скриптов.",
                                reference.nodeId
                            )
                        }
                    }

                    // Ещё не посещённый сценарий.
                    0 -> {
                        detectScriptCycles(reference.targetName)
                    }
                }
            }

            stack.removeAt(stack.lastIndex)
            state[scriptName] = 2
        }

        uniqueRecords.keys.forEach { name ->
            if ((state[name] ?: 0) == 0) {
                detectScriptCycles(name)
            }
        }

        return ValidationReport(
            scenariosChecked = scenarioFiles.size,
            issues = issues
        )
    }

    // =====================================================
    // EXPORT
    // =====================================================


    fun export(
        projectRoot: File,
        targetDir: File,
        subfolder: String? = null
    ): Result {

        validateTarget(targetDir)?.let {
            return Result(
                success = false,
                filesCopied = 0,
                message = it
            )
        }

        if (!projectRoot.isDirectory) {
            return Result(
                success = false,
                filesCopied = 0,
                message = "Проект не найден: ${projectRoot.absolutePath}"
            )
        }

        val projectManifest = File(
            projectRoot,
            "scriptfx.project.json"
        )

        if (!projectManifest.isFile) {
            return Result(
                success = false,
                filesCopied = 0,
                message = "Нет scriptfx.project.json в ${projectRoot.absolutePath}"
            )
        }

        // -------------------------------------------------
        // EXPORT PREVIEW / PREFLIGHT
        // -------------------------------------------------

        val preflight = ExportPreviewService.createPlan(
            projectRoot = projectRoot,
            targetDir = targetDir,
            subfolder = subfolder
        )

        val validation = preflight.validation
            ?: validateProject(
                projectRoot = projectRoot,
                targetDir = targetDir,
                subfolder = subfolder
            )

        if (!preflight.canExport) {

            val reason = preflight.error
                ?: "Обнаружено конфликтов: ${preflight.conflicts}."

            return Result(
                success = false,
                filesCopied = 0,
                message = "Экспорт отменён. $reason",
                validation = validation
            )
        }

        if (validation.hasErrors) {
            return Result(
                success = false,
                filesCopied = 0,
                message =
                    "Экспорт отменён. " +
                            "Проверено сценариев: ${validation.scenariosChecked}. " +
                            "Ошибок: ${validation.errors}. " +
                            "Предупреждений: ${validation.warnings}.",
                validation = validation
            )
        }

        val projectName = subfolder ?: projectRoot.name

        val destRoot = File(
            targetDir,
            "config/scriptfx/projects/${sanitize(projectName)}"
        )

        val destinationExistedBeforeExport = destRoot.exists()

        var copied = 0

        return try {

            // ---------------------------------------------
            // CHECK DESTINATION
            // ---------------------------------------------

            if (!isSafeDestinationPath(targetDir, destRoot)) {
                throw SecurityException(
                    "Папка экспорта находится за пределами Minecraft."
                )
            }

            if (
                destRoot.exists() &&
                !destRoot.isDirectory
            ) {
                throw IOException(
                    "Путь экспорта существует, но не является папкой."
                )
            }

            if (!destRoot.exists() && !destRoot.mkdirs()) {
                throw IOException(
                    "Не удалось создать папку экспорта: ${destRoot.absolutePath}"
                )
            }

            if (!isSafeDestinationPath(targetDir, destRoot)) {
                throw SecurityException(
                    "Небезопасный путь папки экспорта."
                )
            }

            // ---------------------------------------------
            // READ PREVIOUS OWNERSHIP MANIFEST
            // ---------------------------------------------

            val ownershipFile = File(
                destRoot,
                EXPORT_MANIFEST_FILE
            )

            val hadOwnershipManifest = ownershipFile.exists()

            if (
                hadOwnershipManifest &&
                (
                        !ownershipFile.isFile ||
                                !isSafeDestinationPath(destRoot, ownershipFile)
                        )
            ) {
                throw IOException(
                    "Манифест экспорта имеет небезопасный путь."
                )
            }

            val previousManagedScripts =
                if (hadOwnershipManifest) {
                    try {
                        readExportManifest(ownershipFile)
                    } catch (e: Exception) {
                        throw IOException(
                            "Не удалось прочитать манифест экспорта. " +
                                    "Очистка отменена: ${e.message}",
                            e
                        )
                    }
                } else {
                    emptyMap()
                }

            // ---------------------------------------------
            // COPY CURRENT PROJECT FILES
            // ---------------------------------------------

            val currentManagedScripts =
                linkedMapOf<String, String>()

            val sourceFiles = projectRoot
                .walkTopDown()
                .filter { it.isFile }
                .toList()

            sourceFiles.forEach { src ->

                val relativePath = src
                    .relativeTo(projectRoot)
                    .invariantSeparatorsPath

                // Не экспортируем внутренние файлы Studio.
                if (
                    relativePath.startsWith(".git/") ||
                    relativePath.endsWith(".tmp") ||
                    relativePath.endsWith("~") ||
                    relativePath == EXPORT_MANIFEST_FILE ||
                    relativePath == "$EXPORT_MANIFEST_FILE.tmp" ||
                    relativePath.endsWith(
                        ".graph.json",
                        ignoreCase = true
                    )
                ) {
                    return@forEach
                }

                // Studio scenarios/ -> Runtime scripts/
                val exportPath = if (
                    relativePath.startsWith("scenarios/") &&
                    src.extension.equals("sfxs", ignoreCase = true)
                ) {
                    "scripts/" + relativePath.removePrefix("scenarios/")
                } else {
                    relativePath
                }

                val destination = File(
                    destRoot,
                    exportPath
                )

                if (!isSafeDestinationPath(destRoot, destination)) {
                    throw SecurityException(
                        "Небезопасный путь назначения: $exportPath"
                    )
                }

                val parent = destination.parentFile
                    ?: throw IOException(
                        "Не удалось определить папку назначения."
                    )

                if (!parent.exists() && !parent.mkdirs()) {
                    throw IOException(
                        "Не удалось создать папку: ${parent.absolutePath}"
                    )
                }

                if (!parent.isDirectory) {
                    throw IOException(
                        "Папка назначения недоступна: ${parent.absolutePath}"
                    )
                }

                // -----------------------------------------
                // PROTECT MANAGED SCRIPTS
                // -----------------------------------------

                val managedPath = normalizeManagedScriptPath(
                    exportPath
                )

                if (
                    managedPath != null &&
                    (
                            destination.exists() ||
                                    Files.isSymbolicLink(destination.toPath())
                            )
                ) {

                    if (
                        Files.isSymbolicLink(destination.toPath()) ||
                        !destination.isFile
                    ) {
                        throw IOException(
                            "Небезопасный файл назначения: $exportPath"
                        )
                    }

                    val previousHash =
                        previousManagedScripts[managedPath]
                            ?: throw IOException(
                                "Конфликт экспорта: файл '$exportPath' " +
                                        "существует, но не принадлежит Studio."
                            )

                    val currentDestinationHash = sha256(destination)
                    val currentSourceHash = sha256(src)

                    // Разрешаем перезапись только если:
                    // 1. Файл в Minecraft не меняли после предыдущего экспорта.
                    // 2. Или он уже совпадает с текущим исходником.
                    if (
                        currentDestinationHash != previousHash &&
                        currentDestinationHash != currentSourceHash
                    ) {
                        throw IOException(
                            "Файл '$exportPath' изменён вручную. " +
                                    "Перезапись отменена."
                        )
                    }
                }

                // -----------------------------------------
                // COPY
                // -----------------------------------------

                src.copyTo(
                    destination,
                    overwrite = true
                )

                copied++

                if (managedPath != null) {
                    currentManagedScripts[managedPath] =
                        sha256(destination)
                }
            }

            // ---------------------------------------------
            // REMOVE STALE MANAGED SCRIPTS
            // ---------------------------------------------

            var removed = 0
            var preserved = 0

            val stalePaths = previousManagedScripts.keys
                .filterNot {
                    it in currentManagedScripts
                }

            stalePaths.forEach { relativePath ->

                val staleFile = File(
                    destRoot,
                    relativePath
                )

                if (
                    !isSafeDestinationPath(destRoot, staleFile) ||
                    Files.isSymbolicLink(staleFile.toPath())
                ) {
                    preserved++
                    return@forEach
                }

                // Файл уже удалён вручную.
                if (!staleFile.exists()) {
                    return@forEach
                }

                if (!staleFile.isFile) {
                    preserved++
                    return@forEach
                }

                val previousHash =
                    previousManagedScripts[relativePath]
                        ?: return@forEach

                // Изменённый вручную файл не удаляем.
                val currentHash = sha256(staleFile)

                if (currentHash != previousHash) {
                    preserved++
                    return@forEach
                }

                if (!staleFile.delete()) {
                    throw IOException(
                        "Не удалось удалить устаревший сценарий: $relativePath"
                    )
                }

                removed++
            }

            // ---------------------------------------------
            // WRITE NEW OWNERSHIP MANIFEST
            // ---------------------------------------------

            writeExportManifest(
                destRoot = destRoot,
                managedScripts = currentManagedScripts
            )

            // ---------------------------------------------
            // RESULT MESSAGE
            // ---------------------------------------------

            val message = buildString {

                append(
                    "Экспортировано $copied файлов в " +
                            "${destRoot.absolutePath}. "
                )

                append(
                    "Проверено сценариев: " +
                            "${validation.scenariosChecked}. "
                )

                append(
                    "Удалено устаревших сценариев: $removed."
                )

                if (preserved > 0) {
                    append(
                        " Оставлено без изменений файлов: $preserved " +
                                "(изменены вручную или защищены)."
                    )
                }

                if (
                    destinationExistedBeforeExport &&
                    !hadOwnershipManifest
                ) {
                    append(
                        " Старые файлы без манифеста автоматически не удалялись."
                    )
                }

                if (validation.warnings > 0) {
                    append(
                        " Предупреждений проверки: ${validation.warnings}."
                    )
                }
            }

            Result(
                success = true,
                filesCopied = copied,
                message = message,
                validation = validation
            )

        } catch (e: IOException) {

            Result(
                success = false,
                filesCopied = 0,
                message = "Ошибка экспорта: ${e.message}",
                validation = validation
            )

        } catch (e: SecurityException) {

            Result(
                success = false,
                filesCopied = 0,
                message = "Безопасность экспорта: ${e.message}",
                validation = validation
            )

        } catch (e: Exception) {

            Result(
                success = false,
                filesCopied = 0,
                message = "Неожиданная ошибка экспорта: ${e.message}",
                validation = validation
            )
        }
    }


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
