package net.foxmediax.app.project

import kotlinx.serialization.Serializable

@Serializable
data class EntryInfo(
    val scenario: String = "scenarios/intro.sfxs",
    val npc: String = "",
    val quest: String = "",
    val trigger: String = "",
    val cutscene: String = "",
    val hud: String = "",
    val particles: String = ""
)

@Serializable
data class FilesIndex(
    val scenarios: List<String> = emptyList(),
    val npc: List<String> = emptyList(),
    val quests: List<String> = emptyList(),
    val triggers: List<String> = emptyList(),
    val cutscenes: List<String> = emptyList(),
    val hud: List<String> = emptyList(),
    val particles: List<String> = emptyList(),
    val videos: List<String> = emptyList(),
    val audio: List<String> = emptyList()
)

@Serializable
data class ProjectManifest(
    val schemaVersion: Int = 1,
    val name: String,
    val displayName: String = "",
    val minecraft: String = "26.1",
    val entry: EntryInfo = EntryInfo(),
    val files: FilesIndex = FilesIndex(),
    val exportPath: String = ""
)