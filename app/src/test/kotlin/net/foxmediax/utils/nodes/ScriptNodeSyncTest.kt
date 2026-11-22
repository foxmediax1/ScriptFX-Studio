
package net.foxmediax.utils.nodes

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ScriptNodeSyncTest {

    @Test
    fun `CALL target with spaces survives graph text round trip`() {
        val graph = NodeGraph(
            nodes = mutableListOf(
                GraphNode(
                    id = "start",
                    kind = NodeKind.START,
                    title = "Начало"
                ),
                GraphNode(
                    id = "call",
                    kind = NodeKind.CALL,
                    title = "Запустить сценарий",
                    params = mapOf(
                        "target" to "reward scene"
                    )
                ),
                GraphNode(
                    id = "end",
                    kind = NodeKind.END,
                    title = "Конец"
                )
            ),
            edges = mutableListOf(
                GraphEdge("start", "out", "call", "in"),
                GraphEdge("call", "out", "end", "in")
            )
        )

        val text = ScriptNodeSync.graphToText(
            graph = graph,
            includeMeta = false
        )

        assertContains(
            text,
            "startscript \"reward scene\""
        )

        val restored = ScriptNodeSync.textToGraph(text)

        val callNode = restored.nodes.single {
            it.kind == NodeKind.CALL
        }

        assertEquals(
            "reward scene",
            callNode.params["target"]
        )
    }

    @Test
    fun `CALL target with extension is restored correctly`() {
        val text = """
            startscript "bonus round.sfxs"
            stopscript
        """.trimIndent()

        val graph = ScriptNodeSync.textToGraph(text)

        val callNode = graph.nodes.single {
            it.kind == NodeKind.CALL
        }

        assertEquals(
            "bonus round.sfxs",
            callNode.params["target"]
        )
    }

    @Test
    fun `legacy call command supports quoted target`() {
        val text = """
            call "legacy reward"
            stopscript
        """.trimIndent()

        val graph = ScriptNodeSync.textToGraph(text)

        val callNode = graph.nodes.single {
            it.kind == NodeKind.CALL
        }

        assertEquals(
            "legacy reward",
            callNode.params["target"]
        )
    }

    @Test
    fun `CALL target without quotes remains supported`() {
        val text = """
            startscript reward
            stopscript
        """.trimIndent()

        val graph = ScriptNodeSync.textToGraph(text)

        val callNode = graph.nodes.single {
            it.kind == NodeKind.CALL
        }

        assertEquals(
            "reward",
            callNode.params["target"]
        )
    }

    @Test
    fun `empty graph text still creates START and END`() {
        val graph = ScriptNodeSync.textToGraph("")

        assertTrue(
            graph.nodes.any {
                it.kind == NodeKind.START
            }
        )

        assertTrue(
            graph.nodes.any {
                it.kind == NodeKind.END
            }
        )
    }
}
