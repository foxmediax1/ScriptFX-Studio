package net.foxmediax.app.project

import net.foxmediax.utils.nodes.NodeGraph
import net.foxmediax.utils.nodes.ScriptNodeSync
import java.io.File

object ProjectTemplate {

    private const val DEFAULT_SCENARIO = "intro"

    fun createProjectSkeleton(projectRoot: File, name: String): ProjectManifest {
        projectRoot.mkdirs()

        val dirs = listOf(
            "scenarios", "npc", "quests", "triggers", "cutscenes",
            "hud", "particles", "video", "audio", "assets", "graphs"
        )
        dirs.forEach { File(projectRoot, it).mkdirs() }

        // --- дефолтный сценарий: граф + .sfxs + .graph.json ---
        val graph = NodeGraph.defaultTemplate()
        val scenariosDir = File(projectRoot, "scenarios")
        val sfxs = File(scenariosDir, "$DEFAULT_SCENARIO.sfxs")
        sfxs.writeText(ScriptNodeSync.graphToText(graph, includeMeta = true))
        File(scenariosDir, "$DEFAULT_SCENARIO.graph.json").writeText(graph.toJson())

        // --- .scn (пустая сцена) ---
        File(scenariosDir, "$DEFAULT_SCENARIO.scn")
            .writeText("# Сценарий $DEFAULT_SCENARIO\n# startscene\n# endscene\n")

        // --- .graph.json отдельно в graphs/ ---
        File(projectRoot, "graphs/$DEFAULT_SCENARIO.graph.json")
            .writeText(graph.toJson())

        val manifest = ProjectManifest(
            schemaVersion = 1,
            name = name,
            displayName = name,
            minecraft = "26.1",
            entry = EntryInfo(scenario = "scenarios/$DEFAULT_SCENARIO.sfxs"),
            files = FilesIndex(
                scenarios = listOf("$DEFAULT_SCENARIO.sfxs", "$DEFAULT_SCENARIO.scn")
            ),
            exportPath = ""
        )

        File(projectRoot, "scriptfx.project.json")
            .writeText(json.encodeToString(ProjectManifest.serializer(), manifest))

        return manifest
    }

    private val json = kotlinx.serialization.json.Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
}