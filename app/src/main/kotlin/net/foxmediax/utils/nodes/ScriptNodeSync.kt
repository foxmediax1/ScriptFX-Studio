package net.foxmediax.utils.nodes

object ScriptNodeSync {

    private val rePos = Regex("""^\s*#\s*node\s+pos\((\w+)\)\s*=\s*(-?\d+(?:\.\d+)?)\s*,\s*(-?\d+(?:\.\d+)?)\s*$""")
    private val reConnect = Regex("""^\s*#\s*node\s+connect\((\w+)\)\s*->\s*\((\w+)\)\s*$""")
    private val rePrint = Regex("""^\s*print\s+"(.*)"\s*$""")

    fun graphToText(graph: NodeGraph, includeMeta: Boolean = true): String {
        val sb = StringBuilder()
        if (includeMeta) {
            for (n in graph.nodes) sb.append("# node pos(${n.id})=${n.x.toInt()},${n.y.toInt()}\n")
            for (e in graph.edges) sb.append("# node connect(${e.from})->(${e.to})\n")
        }
        for (n in graph.linearChain()) {
            when (n.kind) {
                NodeKind.START -> sb.append("startscript\n")
                NodeKind.END -> sb.append("stopscript\n")
                NodeKind.PRINT -> sb.append("print \"${n.params["text"] ?: ""}\"\n")
                NodeKind.WAIT -> sb.append("wait ${n.params["ms"] ?: "1000"}\n")
                NodeKind.DELAY -> sb.append("delay ${n.params["ms"] ?: "1000"}\n")
                NodeKind.CALL -> sb.append("call ${n.params["target"] ?: ""}\n")
                else -> {}
            }
        }
        return sb.toString()
    }

    fun textToGraph(text: String, scriptName: String = "script"): NodeGraph {
        val positions = mutableMapOf<String, Pair<Float, Float>>()
        val connections = mutableListOf<Pair<String, String>>()
        for (line in text.lineSequence()) {
            rePos.matchEntire(line)?.let { m ->
                positions[m.groupValues[1]] = m.groupValues[2].toFloat() to m.groupValues[3].toFloat()
            } ?: reConnect.matchEntire(line)?.let { m ->
                connections += m.groupValues[1] to m.groupValues[2]
            }
        }

        val graph = NodeGraph()
        var printCounter = 0
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            if (line.startsWith("#") || line.isEmpty()) continue
            val node = when {
                line == "startscript" -> GraphNode("start", NodeKind.START, "Начало скрипта")
                line == "stopscript" -> GraphNode("end", NodeKind.END, "Конец скрипта")
                line.startsWith("print ") -> {
                    printCounter++
                    GraphNode("print$printCounter", NodeKind.PRINT, "Сообщение в чат",
                        params = mapOf("text" to (rePrint.matchEntire(line)?.groupValues?.get(1) ?: "")))
                }
                line.startsWith("wait ") -> GraphNode("wait${graph.nodes.size}", NodeKind.WAIT, "Ожидание",
                    params = mapOf("ms" to line.removePrefix("wait ").trim()))
                line.startsWith("delay ") -> GraphNode("delay${graph.nodes.size}", NodeKind.DELAY, "Задержка",
                    params = mapOf("ms" to line.removePrefix("delay ").trim()))
                line.startsWith("call ") -> GraphNode("call${graph.nodes.size}", NodeKind.CALL, "Вызов",
                    params = mapOf("target" to line.removePrefix("call ").trim()))
                else -> null
            } ?: continue
            if (graph.nodes.none { it.id == node.id }) graph.nodes += node
        }

        // позиции + авто-раскладка
        var autoX = 80f
        for (n in graph.nodes) {
            val p = positions[n.id]
            if (p != null) { n.x = p.first; n.y = p.second }
            else { n.x = autoX; n.y = 200f; autoX += 260f }
        }

        // рёбра
        if (connections.isNotEmpty()) {
            for ((from, to) in connections) {
                if (graph.node(from) != null && graph.node(to) != null)
                    graph.edges += GraphEdge(from, "out", to, "in")
            }
        } else {
            for (i in 0 until graph.nodes.size - 1)
                graph.edges += GraphEdge(graph.nodes[i].id, "out", graph.nodes[i + 1].id, "in")
        }

        // гарантируем START и END
        if (graph.nodes.none { it.kind == NodeKind.START }) {
            val s = GraphNode("start", NodeKind.START, "Начало скрипта", 80f, 200f)
            graph.nodes.add(0, s)
            if (graph.nodes.size >= 2) graph.edges.add(0, GraphEdge("start", "out", graph.nodes[1].id, "in"))
        }
        if (graph.nodes.none { it.kind == NodeKind.END }) {
            val e = GraphNode("end", NodeKind.END, "Конец скрипта", autoX, 200f)
            graph.nodes += e
            if (graph.nodes.size >= 2)
                graph.edges += GraphEdge(graph.nodes[graph.nodes.size - 2].id, "out", "end", "in")
        }
        return graph
    }

    fun stripMeta(text: String): String =
        text.lineSequence().filterNot { rePos.matches(it) || reConnect.matches(it) }.joinToString("\n")

    fun hasMeta(text: String): Boolean =
        text.lineSequence().any { rePos.matches(it) || reConnect.matches(it) }
}