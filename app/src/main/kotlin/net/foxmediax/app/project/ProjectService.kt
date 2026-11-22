package net.foxmediax.app.project

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import net.foxmediax.utils.nodes.NodeGraph
import net.foxmediax.utils.nodes.ScriptNodeSync

class ProjectService(
    private val json: Json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    }
) {
    data class OpenedProject(
        val root: File,
        val manifest: ProjectManifest
    )

    fun createScenario(scenariosDir: File, name: String): File {
        scenariosDir.mkdirs()
        val f = File(scenariosDir, "$name.sfxs")
        val graph = NodeGraph.defaultTemplate()
        f.writeText(ScriptNodeSync.graphToText(graph, includeMeta = true))
        File(scenariosDir, "$name.graph.json").writeText(graph.toJson())
        return f
    }

    /** Создать проект в %APPDATA%\ScriptFX Studio\Projects\<name>. */
    fun createProject(name: String): OpenedProject {
        val root = File(defaultProjectsRoot(), sanitizeName(name))
        if (root.exists()) error("Проект '$name' уже существует: ${root.absolutePath}")
        root.mkdirs()
        val manifest = ProjectTemplate.createProjectSkeleton(root, name)
        return OpenedProject(root, manifest)
    }

    /** Открыть существующий проект (или восстановить скелет, если манифест потерян). */
    fun openProject(root: File): OpenedProject {
        val manifestFile = File(root, "scriptfx.project.json")
        val manifest = if (!manifestFile.isFile) {
            ProjectTemplate.createProjectSkeleton(root, root.name)
        } else {
            json.decodeFromString(ProjectManifest.serializer(), manifestFile.readText())
        }
        return OpenedProject(root, manifest)
    }

    fun listProjects(parentDir: File = defaultProjectsRoot()): List<File> {
        if (!parentDir.isDirectory) return emptyList()
        return parentDir.listFiles()
            ?.filter { it.isDirectory && !it.name.startsWith('.') }
            ?.sortedBy { it.name.lowercase() }
            ?: emptyList()
    }

    fun writeManifest(root: File, manifest: ProjectManifest) {
        atomicWrite(File(root, "scriptfx.project.json"), json.encodeToString(manifest))
    }

    /** %APPDATA%\ScriptFX Studio\Projects */
    fun defaultProjectsRoot(): File {
        val base = studioDataRoot()
        return File(base, "Projects").also { it.mkdirs() }
    }

    /** %APPDATA%\ScriptFX Studio */
    fun studioDataRoot(): File {
        val appdata = System.getenv("APPDATA")
        return if (!appdata.isNullOrBlank()) {
            File(appdata, "ScriptFX Studio")
        } else {
            File(System.getProperty("user.home"), "ScriptFX Studio")
        }.also { it.mkdirs() }
    }

    /** Типичный путь к проектам мода в Minecraft. */
    fun defaultMinecraftProjectsRoot(): File {
        val appdata = System.getenv("APPDATA")
        return if (!appdata.isNullOrBlank()) {
            File(appdata, ".minecraft/config/scriptfx/projects")
        } else {
            File(System.getProperty("user.home"), ".minecraft/config/scriptfx/projects")
        }
    }

    /** Экспорт проекта Studio в папку Minecraft. */
    fun exportToMinecraft(projectRoot: File, minecraftRoot: File): ExportService.Result =
        ExportService.export(projectRoot, minecraftRoot)

    private fun sanitizeName(raw: String): String =
        raw.trim()
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .replace(Regex("\\s+"), "_")
            .take(64)

    private fun atomicWrite(file: File, content: String) {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(content, Charsets.UTF_8)
        Files.move(
            tmp.toPath(),
            file.toPath(),
            StandardCopyOption.REPLACE_EXISTING,
            StandardCopyOption.ATOMIC_MOVE
        )
    }
}