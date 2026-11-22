package net.foxmediax.utils.nodes

object ScriptNodeSync {

    private val rePos = Regex(
        """^\s*#\s*node\s+pos\(([\w.-]+)\)\s*=\s*(-?\d+(?:\.\d+)?)\s*,\s*(-?\d+(?:\.\d+)?)\s*$"""
    )

    private val reConnect = Regex(
        """^\s*#\s*node\s+connect\(([\w.-]+)(?:,([\w.-]+))?\)\s*->\s*\(([\w.-]+)\)\s*$"""
    )

    private val rePrint = Regex(
        """^print\s+"(.*)"$"""
    )

    private val reIf = Regex(
        """^if\s+([^\s{}]+)\s*==\s*([^\s{}]+)\s*\{$"""
    )

    private val numericDuration = Regex(
        """^\d+(?:\.\d+)?$"""
    )

    private data class ParsedNode(
        val node: GraphNode,
        val body: List<ParsedNode> = emptyList()
    )

    /**
     * Преобразует граф Studio в текстовый формат ScriptFX.
     *
     * START — структурная нода, команда не выводится.
     * PRINT — print
     * WAIT / DELAY — ticktime
     * CALL — startscript
     * IF — if variable == value { ... }
     * END — stopscript
     */
    fun graphToText(
        graph: NodeGraph,
        includeMeta: Boolean = true
    ): String {
        val output = StringBuilder()

        if (includeMeta) {
            graph.nodes.forEach { node ->
                output.append(
                    "# node pos(${node.id})=${node.x},${node.y}\n"
                )
            }

            graph.edges.forEach { edge ->
                val portSuffix = if (edge.fromPort == "out") {
                    ""
                } else {
                    ",${edge.fromPort}"
                }

                output.append(
                    "# node connect(${edge.from}$portSuffix)->(${edge.to})\n"
                )
            }
        }

        val visited = mutableSetOf<String>()

        fun emitPath(
            startId: String?,
            indent: Int,
            stopBefore: Set<String> = emptySet()
        ) {
            var currentId = startId

            while (currentId != null) {
                if (currentId in stopBefore) break
                if (!visited.add(currentId)) break

                val node = graph.node(currentId) ?: break
                val prefix = "    ".repeat(indent)

                if (node.kind == NodeKind.IF) {
                    val variable = conditionToken(
                        node.params["variable"],
                        "dialog_button"
                    )

                    val value = conditionToken(
                        node.params["value"],
                        "1"
                    )

                    val trueTarget = graph.outEdges(node.id)
                        .firstOrNull {
                            it.fromPort == "true"
                        }?.to
                        ?: graph.outEdges(node.id)
                            .firstOrNull {
                                it.fromPort == "out"
                            }?.to

                    val nextTarget = graph.outEdges(node.id)
                        .firstOrNull {
                            it.fromPort == "next"
                        }?.to

                    output.append(prefix)
                        .append("if ")
                        .append(variable)
                        .append(" == ")
                        .append(value)
                        .append(" {\n")

                    val branchStops = if (nextTarget != null) {
                        stopBefore + nextTarget
                    } else {
                        stopBefore
                    }

                    emitPath(
                        startId = trueTarget,
                        indent = indent + 1,
                        stopBefore = branchStops
                    )

                    output.append(prefix)
                        .append("}\n")

                    currentId = nextTarget
                    continue
                }

                when (node.kind) {
                    NodeKind.START -> {
                        // START — только входная точка графа.
                        // Команда startscript здесь не нужна.
                    }

                    NodeKind.END -> {
                        output.append(prefix)
                            .append("stopscript\n")

                        // После END сценарий заканчивается.
                        break
                    }

                    NodeKind.PRINT -> {
                        output.append(prefix)
                            .append("print \"")
                            .append(escapeQuoted(node.params["text"].orEmpty()))
                            .append("\"\n")
                    }

                    NodeKind.WAIT,
                    NodeKind.DELAY -> {
                        val duration = runtimeDuration(
                            node.params["ms"]
                        )

                        output.append(prefix)
                            .append("ticktime ")
                            .append(duration)
                            .append("\n")
                    }

                    NodeKind.CALL -> {
                        val target = node.params["target"]
                            ?.trim()
                            .orEmpty()

                        if (target.isNotEmpty()) {
                            output.append(prefix)
                                .append("startscript \"")
                                .append(escapeQuoted(target))
                                .append("\"\n")
                        } else {
                            output.append(prefix)
                                .append("# ERROR: CALL target is empty\n")
                        }
                    }

                    NodeKind.SET -> {
                        output.append(prefix)
                            .append("# ERROR: SET is not supported by ScriptFX runtime\n")
                    }

                    NodeKind.OTHER -> {
                        output.append(prefix)
                            .append("# ERROR: unsupported Studio node ${node.id}\n")
                    }

                    NodeKind.IF -> Unit
                }

                currentId = graph.outEdges(node.id)
                    .firstOrNull {
                        it.fromPort == "out"
                    }?.to
            }
        }

        val startId = graph.nodes.firstOrNull {
            it.kind == NodeKind.START
        }?.id ?: graph.nodes.firstOrNull()?.id

        emitPath(
            startId = startId,
            indent = 0
        )

        return output.toString()
    }

    /**
     * Восстанавливает граф из .sfxs.
     *
     * Поддерживает:
     * - вложенные IF;
     * - ticktime;
     * - startscript;
     * - старые wait/delay/call для миграции старых проектов;
     * - metadata координат и соединений.
     */
    fun textToGraph(
        text: String,
        scriptName: String = "script"
    ): NodeGraph {
        val positions = mutableMapOf<String, Pair<Float, Float>>()
        val metadataEdges = mutableListOf<GraphEdge>()

        for (line in text.lineSequence()) {
            rePos.matchEntire(line)?.let { match ->
                positions[match.groupValues[1]] =
                    match.groupValues[2].toFloat() to
                            match.groupValues[3].toFloat()
            }

            reConnect.matchEntire(line)?.let { match ->
                metadataEdges += GraphEdge(
                    from = match.groupValues[1],
                    fromPort = match.groupValues[2].ifEmpty { "out" },
                    to = match.groupValues[3],
                    toPort = "in"
                )
            }
        }

        val usedIds = mutableSetOf<String>()

        fun newId(base: String): String {
            var candidate = base
            var suffix = 2

            while (!usedIds.add(candidate)) {
                candidate = "${base}_$suffix"
                suffix++
            }

            return candidate
        }

        var printCounter = 0
        var waitCounter = 0
        var delayCounter = 0
        var callCounter = 0
        var ifCounter = 0

        fun parseSimple(line: String): GraphNode? {
            return when {
                line == "startscript" -> {
                    GraphNode(
                        id = newId("start"),
                        kind = NodeKind.START,
                        title = "Начало скрипта"
                    )
                }

                line.startsWith("startscript ") -> {
                    callCounter++

                    GraphNode(
                        id = newId("call$callCounter"),
                        kind = NodeKind.CALL,
                        title = "Запустить сценарий",
                        params = mapOf(
                            "target" to line.removePrefix("startscript ").trim()
                        )
                    )
                }

                line == "stopscript" -> {
                    GraphNode(
                        id = newId("end"),
                        kind = NodeKind.END,
                        title = "Конец скрипта"
                    )
                }

                line.startsWith("print ") -> {
                    printCounter++

                    val message = rePrint.matchEntire(line)
                        ?.groupValues
                        ?.get(1)
                        ?.replace("\\\"", "\"")
                        .orEmpty()

                    GraphNode(
                        id = newId("print$printCounter"),
                        kind = NodeKind.PRINT,
                        title = "Сообщение в чат",
                        params = mapOf("text" to message)
                    )
                }

                line.startsWith("ticktime ") -> {
                    waitCounter++

                    GraphNode(
                        id = newId("wait$waitCounter"),
                        kind = NodeKind.WAIT,
                        title = "Ожидание",
                        params = mapOf(
                            "ms" to line.removePrefix("ticktime ").trim()
                        )
                    )
                }

                // Поддержка старых файлов Studio.
                line.startsWith("wait ") -> {
                    waitCounter++

                    GraphNode(
                        id = newId("wait$waitCounter"),
                        kind = NodeKind.WAIT,
                        title = "Ожидание",
                        params = mapOf(
                            "ms" to line.removePrefix("wait ").trim()
                        )
                    )
                }

                line.startsWith("delay ") -> {
                    delayCounter++

                    GraphNode(
                        id = newId("delay$delayCounter"),
                        kind = NodeKind.DELAY,
                        title = "Задержка",
                        params = mapOf(
                            "ms" to line.removePrefix("delay ").trim()
                        )
                    )
                }

                // Поддержка старого формата Studio.
                line.startsWith("call ") -> {
                    callCounter++

                    GraphNode(
                        id = newId("call$callCounter"),
                        kind = NodeKind.CALL,
                        title = "Запустить сценарий",
                        params = mapOf(
                            "target" to line.removePrefix("call ").trim()
                        )
                    )
                }

                else -> null
            }
        }

        val lines = text
            .removePrefix("\uFEFF")
            .lineSequence()
            .toList()

        var cursor = 0

        fun parseBlock(): List<ParsedNode> {
            val result = mutableListOf<ParsedNode>()

            while (cursor < lines.size) {
                val line = lines[cursor].trim()

                if (
                    line.isEmpty() ||
                    line.startsWith("#") ||
                    line.startsWith("//")
                ) {
                    cursor++
                    continue
                }

                if (line == "}") {
                    cursor++
                    return result
                }

                if (line.startsWith("if ") && line.endsWith("{")) {
                    val match = reIf.matchEntire(line)

                    if (match != null) {
                        val variable = match.groupValues[1]
                        val value = match.groupValues[2]

                        cursor++
                        ifCounter++

                        val body = parseBlock()

                        result += ParsedNode(
                            node = GraphNode(
                                id = newId("if$ifCounter"),
                                kind = NodeKind.IF,
                                title = "Условие IF",
                                params = mapOf(
                                    "variable" to variable,
                                    "value" to value
                                )
                            ),
                            body = body
                        )

                        continue
                    }
                }

                parseSimple(line)?.let {
                    result += ParsedNode(it)
                }

                cursor++
            }

            return result
        }

        val roots = parseBlock().toMutableList()

        if (roots.none { it.node.kind == NodeKind.START }) {
            roots.add(
                0,
                ParsedNode(
                    GraphNode(
                        id = newId("start"),
                        kind = NodeKind.START,
                        title = "Начало скрипта"
                    )
                )
            )
        }

        if (roots.none { it.node.kind == NodeKind.END }) {
            roots.add(
                ParsedNode(
                    GraphNode(
                        id = newId("end"),
                        kind = NodeKind.END,
                        title = "Конец скрипта"
                    )
                )
            )
        }

        val graph = NodeGraph()

        fun collectNodes(items: List<ParsedNode>) {
            for (item in items) {
                graph.nodes += item.node
                collectNodes(item.body)
            }
        }

        collectNodes(roots)

        // Распределение нод по горизонтали.
        fun placeNodes(
            items: List<ParsedNode>,
            startX: Float,
            y: Float
        ) {
            var x = startX

            for (item in items) {
                val saved = positions[item.node.id]

                item.node.x = saved?.first ?: x
                item.node.y = saved?.second ?: y

                if (item.body.isNotEmpty()) {
                    placeNodes(
                        item.body,
                        x + 220f,
                        y + 120f
                    )
                }

                x += 260f
            }
        }

        placeNodes(
            roots,
            startX = 80f,
            y = 200f
        )

        /**
         * continuation — нода после текущего вложенного блока.
         *
         * Это позволяет правильно восстанавливать вложенные IF
         * и соединять выход ветки с общей следующей нодой.
         */
        fun connectSequence(
            items: List<ParsedNode>,
            continuation: GraphNode? = null
        ) {
            for (index in items.indices) {
                val item = items[index]

                val next = items.getOrNull(index + 1)?.node
                    ?: continuation

                if (item.node.kind == NodeKind.IF) {
                    val firstBodyNode = item.body.firstOrNull()?.node

                    if (firstBodyNode != null) {
                        graph.edges += GraphEdge(
                            from = item.node.id,
                            fromPort = "true",
                            to = firstBodyNode.id,
                            toPort = "in"
                        )
                    }

                    if (next != null) {
                        graph.edges += GraphEdge(
                            from = item.node.id,
                            fromPort = "next",
                            to = next.id,
                            toPort = "in"
                        )
                    }

                    connectSequence(
                        items = item.body,
                        continuation = next
                    )
                } else if (next != null) {
                    graph.edges += GraphEdge(
                        from = item.node.id,
                        fromPort = "out",
                        to = next.id,
                        toPort = "in"
                    )
                }
            }
        }

        connectSequence(roots)

        // Если все metadata-соединения корректны,
        // восстанавливаем именно исходную структуру графа.
        val validMetadataEdges = metadataEdges.filter { edge ->
            graph.node(edge.from) != null &&
                    graph.node(edge.to) != null
        }

        if (
            metadataEdges.isNotEmpty() &&
            validMetadataEdges.size == metadataEdges.size
        ) {
            graph.edges.clear()
            graph.edges.addAll(validMetadataEdges)
        }

        return graph
    }

    fun stripMeta(text: String): String {
        return text.lineSequence()
            .filterNot { line ->
                rePos.matches(line) ||
                        reConnect.matches(line)
            }
            .joinToString("\n")
    }

    fun hasMeta(text: String): Boolean {
        return text.lineSequence().any { line ->
            rePos.matches(line) ||
                    reConnect.matches(line)
        }
    }

    private fun conditionToken(
        raw: String?,
        fallback: String
    ): String {
        val value = raw?.trim().orEmpty()

        if (value.isEmpty()) return fallback

        if (
            value.any { it.isWhitespace() } ||
            value.contains('{') ||
            value.contains('}')
        ) {
            return fallback
        }

        return value
    }

    private fun runtimeDuration(raw: String?): String {
        val value = raw?.trim().orEmpty().ifEmpty {
            "1000"
        }

        // Обычное число параметра ms превращаем
        // в формат Durations runtime: 1000.ms.
        return if (numericDuration.matches(value)) {
            "$value.ms"
        } else {
            value
        }
    }

    private fun escapeQuoted(value: String): String {
        return value
            .replace("\r", " ")
            .replace("\n", " ")
            .replace("\"", "\\\"")
    }
}