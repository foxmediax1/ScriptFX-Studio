
package net.foxmediax.app.project

import net.foxmediax.utils.nodes.GraphIssue
import net.foxmediax.utils.nodes.GraphIssueSeverity
import net.foxmediax.utils.nodes.GraphValidator
import net.foxmediax.utils.nodes.NodeGraph
import net.foxmediax.utils.nodes.NodeKind
import net.foxmediax.utils.nodes.ScriptNodeSync
import java.io.File
import java.io.IOException

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
        // Studio хранит создаваемые сценарии в scenarios/.
        // Поэтому проверяем обе папки.
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

        val externalFilesByName = mutableMapOf<String, MutableList<String>>()

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
                ?.forEach { otherProject ->

                    val scriptsDir = File(
                        otherProject,
                        "scripts"
                    )

                    if (!scriptsDir.isDirectory) {
                        return@forEach
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

        // Уникальные имена существующих сценариев.
        val uniqueLocalNames = localFilesByName
            .filterValues { it.size == 1 }
            .keys

        records.forEach { record ->

            record.graph.nodes
                .filter { it.kind == NodeKind.CALL }
                .forEach { node ->

                    val rawTarget = node.params["target"].orEmpty()

                    val parsed = parseCallTarget(rawTarget)

                    if (parsed.error != null) {
                        addError(
                            record.relativePath,
                            "CALL: ${parsed.error}",
                            node.id
                        )

                        return@forEach
                    }

                    val targetName = parsed.name
                        ?: return@forEach

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
                false,
                0,
                "Проект не найден: ${projectRoot.absolutePath}"
            )
        }

        val manifest = File(
            projectRoot,
            "scriptfx.project.json"
        )

        if (!manifest.isFile) {
            return Result(
                false,
                0,
                "Нет scriptfx.project.json в ${projectRoot.absolutePath}"
            )
        }

        val projectName = subfolder ?: projectRoot.name

        // Проверяем также имена сценариев в уже установленных проектах.
        val validation = validateProject(
            projectRoot = projectRoot,
            targetDir = targetDir,
            subfolder = subfolder
        )

        // Критические ошибки блокируют экспорт.
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

        val destRoot = File(
            targetDir,
            "config/scriptfx/projects/${sanitize(projectName)}"
        )

        return try {
            var copied = 0

            projectRoot.walkTopDown().forEach { src ->

                if (!src.isFile) {
                    return@forEach
                }

                val relativePath = src
                    .relativeTo(projectRoot)
                    .path
                    .replace('\\', '/')

                if (
                    relativePath.startsWith(".git/") ||
                    relativePath.endsWith(".tmp") ||
                    relativePath.endsWith("~") ||
                    relativePath.endsWith(
                        ".graph.json",
                        ignoreCase = true
                    )
                ) {
                    return@forEach
                }

                // Studio хранит сценарии в scenarios/,
                // но ScriptFX Runtime загружает их из scripts/.
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

                destination.parentFile.mkdirs()

                src.copyTo(
                    destination,
                    overwrite = true
                )

                copied++
            }

            val warningSuffix = if (validation.warnings > 0) {
                " Предупреждений: ${validation.warnings}."
            } else {
                ""
            }

            Result(
                success = true,
                filesCopied = copied,
                message =
                    "Экспортировано $copied файлов в ${destRoot.absolutePath}. " +
                            "Проверено сценариев: ${validation.scenariosChecked}." +
                            warningSuffix,
                validation = validation
            )

        } catch (e: IOException) {
            Result(
                success = false,
                filesCopied = 0,
                message = "Ошибка копирования: ${e.message}",
                validation = validation
            )
        } catch (e: SecurityException) {
            Result(
                success = false,
                filesCopied = 0,
                message = "Нет доступа при экспорте: ${e.message}",
                validation = validation
            )
        }
    }

    private fun sanitize(name: String): String =
        name.replace(
            Regex("""[\\/:*?"<>|]"""),
            "_"
        ).ifBlank {
            "project"
        }
}
