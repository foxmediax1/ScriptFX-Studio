package net.foxmediax.app.project

import java.io.File
import java.io.IOException

object ExportService {

    data class Result(
        val success: Boolean,
        val filesCopied: Int,
        val message: String
    )

    fun validateTarget(target: File): String? {
        if (!target.exists()) return "Папка не существует: ${target.absolutePath}"
        if (!target.isDirectory) return "Это не папка: ${target.absolutePath}"
        val s = target.absolutePath.replace('\\', '/').lowercase()
        val looksLikeMinecraft = s.endsWith("/.minecraft") || s.endsWith("/minecraft") ||
                s.contains("/.minecraft/") || File(target, "config").exists() ||
                File(target, "mods").exists()
        if (!looksLikeMinecraft) {
            return "Похоже, это не папка Minecraft. Ожидался путь вида " +
                    "%APPDATA%/.minecraft или .../config/scriptfx/projects"
        }
        return null
    }

    fun export(projectRoot: File, targetDir: File, subfolder: String? = null): Result {
        validateTarget(targetDir)?.let { return Result(false, 0, it) }
        if (!projectRoot.isDirectory) return Result(false, 0, "Проект не найден: ${projectRoot.absolutePath}")

        val manifest = File(projectRoot, "scriptfx.project.json")
        if (!manifest.isFile) return Result(false, 0, "Нет scriptfx.project.json в ${projectRoot.absolutePath}")

        val projectName = subfolder ?: projectRoot.name
        val destRoot = File(targetDir, "config/scriptfx/projects/${sanitize(projectName)}")

        return try {
            var copied = 0
            projectRoot.walkTopDown().forEach { src ->
                if (!src.isFile) return@forEach
                val rel = src.relativeTo(projectRoot).path.replace('\\', '/')
                if (rel.startsWith(".git/") || rel.endsWith(".tmp") || rel.endsWith("~")) return@forEach
                val dst = File(destRoot, rel)
                dst.parentFile.mkdirs()
                src.copyTo(dst, overwrite = true)
                copied++
            }
            Result(true, copied, "Экспортировано $copied файлов в ${destRoot.absolutePath}")
        } catch (e: IOException) {
            Result(false, 0, "Ошибка копирования: ${e.message}")
        }
    }

    private fun sanitize(name: String): String =
        name.replace(Regex("""[\\/:*?"<>|]"""), "_").ifBlank { "project" }
}