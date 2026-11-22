package net.foxmediax.utils.nodes

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
enum class NodeKind {
    START, END, PRINT, WAIT, DELAY, CALL, IF, SET, OTHER
}

@Serializable
data class GraphNode(
    var id: String,
    var kind: NodeKind,
    var title: String,
    var x: Float = 0f,
    var y: Float = 0f,
    var params: Map<String, String> = emptyMap()
)

@Serializable
data class GraphEdge(
    val from: String,
    val fromPort: String = "out",
    val to: String,
    val toPort: String = "in"
)

@Serializable
data class NodeGraph(
    var nodes: MutableList<GraphNode> = mutableListOf(),
    var edges: MutableList<GraphEdge> = mutableListOf()
) {
    fun node(id: String): GraphNode? = nodes.firstOrNull { it.id == id }
    fun outEdges(fromId: String): List<GraphEdge> = edges.filter { it.from == fromId }
    fun inEdges(toId: String): List<GraphEdge> = edges.filter { it.to == toId }

    fun linearChain(): List<GraphNode> {
        val start = nodes.firstOrNull { it.kind == NodeKind.START } ?: return nodes.toList()
        val result = mutableListOf<GraphNode>()
        val visited = mutableSetOf<String>()
        var current: GraphNode? = start
        while (current != null && visited.add(current.id)) {
            result += current
            val nextId = outEdges(current.id).firstOrNull()?.to ?: break
            current = node(nextId)
        }
        return result
    }

    companion object {
        private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

        fun defaultTemplate(): NodeGraph {
            val start = GraphNode("start", NodeKind.START, "Начало скрипта", 80f, 200f)
            val print = GraphNode(
                "print1", NodeKind.PRINT, "Сообщение в чат", 340f, 200f,
                params = mapOf("text" to "Привет, мир!")
            )
            val end = GraphNode("end", NodeKind.END, "Конец скрипта", 600f, 200f)
            return NodeGraph(
                nodes = mutableListOf(start, print, end),
                edges = mutableListOf(
                    GraphEdge("start", "out", "print1", "in"),
                    GraphEdge("print1", "out", "end", "in")
                )
            )
        }

        fun fromJson(text: String): NodeGraph = json.decodeFromString(serializer(), text)
    }

    fun toJson(): String = Json { prettyPrint = true }.encodeToString(serializer(), this)
}