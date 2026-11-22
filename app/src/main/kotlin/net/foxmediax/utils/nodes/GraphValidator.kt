package net.foxmediax.utils.nodes

import java.util.ArrayDeque

enum class GraphIssueSeverity {
    ERROR,
    WARNING,
    INFO
}

data class GraphIssue(
    val severity: GraphIssueSeverity,
    val message: String,
    val nodeId: String? = null
)

object GraphValidator {

    private val durationPattern = Regex(
        """^\d+(?:\.\d+)?(?:\.(?:ms|milli|millis|t|tick|ticks|s|sec|secs|second|seconds))?$""",
        RegexOption.IGNORE_CASE
    )

    fun validate(graph: NodeGraph): List<GraphIssue> {
        val issues = mutableListOf<GraphIssue>()

        fun error(message: String, nodeId: String? = null) {
            issues.add(
                GraphIssue(
                    GraphIssueSeverity.ERROR,
                    message,
                    nodeId
                )
            )
        }

        fun warning(message: String, nodeId: String? = null) {
            issues.add(
                GraphIssue(
                    GraphIssueSeverity.WARNING,
                    message,
                    nodeId
                )
            )
        }

        fun info(message: String, nodeId: String? = null) {
            issues.add(
                GraphIssue(
                    GraphIssueSeverity.INFO,
                    message,
                    nodeId
                )
            )
        }

        // Пустой граф.
        if (graph.nodes.isEmpty()) {
            error("Граф пустой. Добавь ноды сценария.")
            return issues
        }

        // Уникальность ID.
        graph.nodes
            .groupBy { it.id }
            .filterValues { it.size > 1 }
            .forEach { (id, _) ->
                error("Повторяющийся ID ноды: $id", id)
            }

        val nodesById = graph.nodes.associateBy { it.id }

        // START и END.
        val starts = graph.nodes.filter {
            it.kind == NodeKind.START
        }

        val ends = graph.nodes.filter {
            it.kind == NodeKind.END
        }

        when {
            starts.isEmpty() ->
                error("В графе отсутствует START.")

            starts.size > 1 ->
                error("В графе должно быть не больше одного START.")
        }

        if (ends.isEmpty()) {
            warning(
                "Нет END. Скрипт завершится после последней команды."
            )
        }

        // Проверка соединений.
        graph.edges.forEach { edge ->
            val source = nodesById[edge.from]
            val target = nodesById[edge.to]

            if (source == null) {
                error(
                    "Не найдена исходная нода: ${edge.from}"
                )
            }

            if (target == null) {
                error(
                    "Не найдена целевая нода: ${edge.to}"
                )
            }

            if (source != null) {
                if (source.kind == NodeKind.IF) {
                    if (
                        edge.fromPort !in
                        setOf("true", "next", "out")
                    ) {
                        error(
                            "IF: неизвестный выход '${edge.fromPort}'.",
                            source.id
                        )
                    }

                    if (edge.fromPort == "out") {
                        warning(
                            "IF использует старый выход out. " +
                                    "Переподключи его к TRUE или NEXT.",
                            source.id
                        )
                    }
                } else if (edge.fromPort != "out") {
                    error(
                        "У ${source.kind} неизвестный выход '${edge.fromPort}'.",
                        source.id
                    )
                }
            }

            if (target != null && edge.toPort != "in") {
                error(
                    "Неизвестный вход '${edge.toPort}'.",
                    target.id
                )
            }
        }

        // Один выход не должен вести сразу к нескольким нодам.
        graph.edges
            .groupBy { it.from to it.fromPort }
            .filterValues { it.size > 1 }
            .forEach { (connection, _) ->
                error(
                    "Выход '${connection.second}' подключён " +
                            "более чем к одной ноде.",
                    connection.first
                )
            }

        // Проверка параметров нод.
        graph.nodes.forEach { node ->
            when (node.kind) {
                NodeKind.START -> {
                    if (graph.inEdges(node.id).isNotEmpty()) {
                        warning(
                            "START имеет входящее соединение.",
                            node.id
                        )
                    }
                }

                NodeKind.END -> {
                    if (graph.outEdges(node.id).isNotEmpty()) {
                        error(
                            "END не должен иметь исходящих соединений.",
                            node.id
                        )
                    }
                }

                NodeKind.PRINT -> {
                    if (node.params["text"].isNullOrEmpty()) {
                        warning(
                            "Текст сообщения пустой.",
                            node.id
                        )
                    }
                }

                NodeKind.WAIT,
                NodeKind.DELAY -> {
                    val duration = node.params["ms"]
                        ?.trim()
                        .orEmpty()

                    if (!durationPattern.matches(duration)) {
                        error(
                            "Некорректная длительность '$duration'. " +
                                    "Допустимые примеры: 1000, 500.ms, " +
                                    "2.sec, 20.tick.",
                            node.id
                        )
                    }
                }

                NodeKind.CALL -> {
                    if (node.params["target"].isNullOrBlank()) {
                        error(
                            "Не указано имя запускаемого сценария.",
                            node.id
                        )
                    } else {
                        info(
                            "CALL запускает другой сценарий " +
                                    "параллельно через startscript.",
                            node.id
                        )
                    }
                }

                NodeKind.IF -> {
                    val variable = node.params["variable"]
                        ?.trim()
                        .orEmpty()

                    val value = node.params["value"]
                        ?.trim()
                        .orEmpty()

                    if (variable.isEmpty()) {
                        error(
                            "IF: не указана переменная.",
                            node.id
                        )
                    } else if (variable.any { it.isWhitespace() }) {
                        error(
                            "IF: имя переменной не должно содержать пробелы.",
                            node.id
                        )
                    }

                    if (value.isEmpty()) {
                        error(
                            "IF: не указано значение сравнения.",
                            node.id
                        )
                    }

                    val hasTrue = graph.outEdges(node.id).any {
                        it.fromPort == "true" ||
                                it.fromPort == "out"
                    }

                    val hasNext = graph.outEdges(node.id).any {
                        it.fromPort == "next"
                    }

                    if (!hasTrue) {
                        error(
                            "IF: подключи выход TRUE к первой ноде блока.",
                            node.id
                        )
                    }

                    if (!hasNext) {
                        warning(
                            "IF: выход NEXT не подключён.",
                            node.id
                        )
                    }
                }

                NodeKind.SET -> {
                    error(
                        "SET пока не поддерживается runtime ScriptFX.",
                        node.id
                    )
                }

                NodeKind.OTHER -> {
                    error(
                        "Неизвестный тип ноды нельзя экспортировать.",
                        node.id
                    )
                }
            }
        }

        // Построение списка смежности.
        val adjacency = graph.edges
            .filter { edge ->
                nodesById.containsKey(edge.from) &&
                        nodesById.containsKey(edge.to)
            }
            .groupBy { it.from }
            .mapValues { (_, edges) ->
                edges.map { it.to }
            }

        // Поиск циклов.
        val visited = mutableSetOf<String>()
        val active = mutableSetOf<String>()
        var cycleFound = false

        fun detectCycle(id: String) {
            if (cycleFound) return

            if (id in active) {
                cycleFound = true
                return
            }

            if (id in visited) return

            visited.add(id)
            active.add(id)

            for (nextId in adjacency[id].orEmpty()) {
                detectCycle(nextId)

                if (cycleFound) return
            }

            active.remove(id)
        }

        graph.nodes.forEach { node ->
            if (node.id !in visited) {
                detectCycle(node.id)
            }
        }

        if (cycleFound) {
            error(
                "В графе найден цикл. " +
                        "Циклические сценарии пока не поддерживаются."
            )
        }

        // Проверка достижимости нод от START.
        val start = starts.firstOrNull()

        if (start != null) {
            val reachable = mutableSetOf<String>()
            val queue = ArrayDeque<String>()

            queue.addLast(start.id)

            while (queue.isNotEmpty()) {
                val id = queue.removeFirst()

                if (!reachable.add(id)) continue

                adjacency[id].orEmpty().forEach { nextId ->
                    if (nextId !in reachable) {
                        queue.addLast(nextId)
                    }
                }
            }

            graph.nodes
                .filter { it.id !in reachable }
                .forEach { node ->
                    warning(
                        "Нода недостижима от START.",
                        node.id
                    )
                }
        }

        return issues
    }
}