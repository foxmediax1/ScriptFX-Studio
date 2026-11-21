package net.foxmediax.app.project

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

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

    fun createProject(
        parentDir: File,
        rawName: String,
        displayName: String? = null
    ): Result<OpenedProject> = runCatching {
        val name = sanitizeName(rawName)
        require(name.isNotEmpty()) { "Пустое имя проекта" }

        val root = File(parentDir, name)
        require(!root.exists()) { "Папка уже существует: ${root.absolutePath}" }

        root.mkdirs()
        ProjectTemplate.dirs().forEach { File(root, it).mkdirs() }

        val manifest = ProjectTemplate.buildManifest(name, displayName?.ifBlank { null } ?: name)
        writeManifest(root, manifest)

        File(root, "scenarios/intro.sfxs").writeText(ProjectTemplate.starterSfxs(name), Charsets.UTF_8)
        File(root, "scenarios/intro.scn").writeText(ProjectTemplate.starterScn(), Charsets.UTF_8)
        File(root, "npc/.gitkeep").writeText("")

        OpenedProject(root, manifest)
    }

    fun openProject(root: File): Result<OpenedProject> = runCatching {
        require(root.isDirectory) { "Не папка: ${root.path}" }
        val manifestFile = File(root, "scriptfx.project.json")
        val manifest = if (manifestFile.isFile) {
            json.decodeFromString<ProjectManifest>(manifestFile.readText(Charsets.UTF_8))
        } else {
            val m = ProjectTemplate.buildManifest(root.name)
            writeManifest(root, m)
            m
        }
        OpenedProject(root.canonicalFile, manifest)
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

    fun defaultProjectsRoot(): File {
        val appdata = System.getenv("APPDATA")
        val base = if (!appdata.isNullOrBlank()) {
            File(appdata, "ScriptFX Studio")
        } else {
            File(System.getProperty("user.home"), "ScriptFX Studio")
        }
        return File(base, "Projects").also { it.mkdirs() }
    }

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

    /** Корень данных студии: %APPDATA%\ScriptFX Studio\ */
    fun studioDataRoot(): File {
        val appdata = System.getenv("APPDATA")
        return if (!appdata.isNullOrBlank()) {
            File(appdata, "ScriptFX Studio")
        } else {
            File(System.getProperty("user.home"), "ScriptFX Studio")
        }.also { it.mkdirs() }
    }

    /**
     * Типичный путь к проектам мода в Minecraft.
     * Пользователь может переопределить при экспорте.
     */
    fun defaultMinecraftProjectsRoot(): File {
        val appdata = System.getenv("APPDATA")
        return if (!appdata.isNullOrBlank()) {
            File(appdata, ".minecraft/config/scriptfx/projects")
        } else {
            File(System.getProperty("user.home"), ".minecraft/config/scriptfx/projects")
        }
    }

    /**
     * Копирует содержимое проекта студии в папку мода:
     * <minecraftProjectsRoot>/<projectName>/
     *
     * Позже можно фильтровать только runtime-файлы.
     */
    fun exportToMinecraft(
        projectRoot: File,
        minecraftProjectsRoot: File = defaultMinecraftProjectsRoot()
    ): Result<File> = runCatching {
        require(projectRoot.isDirectory) { "Нет папки проекта: ${projectRoot.path}" }
        val target = File(minecraftProjectsRoot, projectRoot.name)
        target.mkdirs()
        projectRoot.copyRecursively(target, overwrite = true)
        target
    }
}