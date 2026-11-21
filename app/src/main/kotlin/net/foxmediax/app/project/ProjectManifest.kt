package net.foxmediax.app.project

import kotlinx.serialization.Serializable

@Serializable
data class ProjectManifest(
    val schemaVersion: Int = 1,
    val name: String,
    val displayName: String = name,
    val minecraft: MinecraftInfo = MinecraftInfo(),
    val entry: EntryInfo = EntryInfo(),
    val files: FilesIndex = FilesIndex(),
    val tags: List<String> = emptyList()
)

@Serializable
data class MinecraftInfo(
    val minVersion: String = "26.1.2"
)

@Serializable
data class EntryInfo(
    val defaultScenario: String = "intro"
)

@Serializable
data class FilesIndex(
    val scenarios: List<FileRef> = emptyList(),
    val npc: List<FileRef> = emptyList(),
    val video: List<FileRef> = emptyList(),
    val audio: List<FileRef> = emptyList()
)

@Serializable
data class FileRef(
    val id: String,
    val path: String,
    val kind: String = "script"
)