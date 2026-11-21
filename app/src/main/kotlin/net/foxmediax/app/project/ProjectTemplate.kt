package net.foxmediax.app.project

object ProjectTemplate {

    fun starterSfxs(projectName: String): String = """
        |# ScriptFX starter — $projectName
        |# node connect("start") if ("print1")
        |# node connect("print1") if ("end")
        |
        |startscript
        |print "Привет, мир!"
        |stopscript
    """.trimMargin()

    fun starterScn(): String = """
        |// ScriptFX Scenario v1
        |play_cutscene "Intro"
        |wait 3s
        |show_dialogue "Привет, путник!"
        |give_quest "main_quest"
    """.trimMargin()

    fun dirs(): List<String> = listOf(
        "scenarios", "npc", "video", "audio", "assets", "graphs"
    )

    fun buildManifest(name: String, displayName: String = name): ProjectManifest =
        ProjectManifest(
            name = name,
            displayName = displayName,
            entry = EntryInfo(defaultScenario = "intro"),
            files = FilesIndex(
                scenarios = listOf(
                    FileRef(id = "intro", path = "scenarios/intro.sfxs", kind = "script"),
                    FileRef(id = "intro_scn", path = "scenarios/intro.scn", kind = "scenario")
                )
            ),
            tags = listOf("new")
        )
}