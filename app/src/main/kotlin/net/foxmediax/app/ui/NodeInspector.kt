
package net.foxmediax.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import net.foxmediax.utils.nodes.GraphIssue
import net.foxmediax.utils.nodes.GraphIssueSeverity
import net.foxmediax.utils.nodes.GraphValidator
import net.foxmediax.utils.nodes.NodeGraph
import net.foxmediax.utils.nodes.NodeKind

@Composable
fun NodeInspector(
    graph: NodeGraph,
    selectedNodeId: String?,
    revision: Int,
    onGraphChanged: (NodeGraph) -> Unit,
    controller: NodeCanvasController,
    modifier: Modifier = Modifier
) {
    val node = graph.node(selectedNodeId ?: "")

    val issues = remember(revision) {
        GraphValidator.validate(graph)
    }

    val errors = issues.count {
        it.severity == GraphIssueSeverity.ERROR
    }

    val warnings = issues.count {
        it.severity == GraphIssueSeverity.WARNING
    }

    Column(
        modifier = modifier
            .background(StudioColors.Panel)
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "Инспектор",
            style = MaterialTheme.typography.titleMedium,
            color = StudioColors.Text
        )

        HorizontalDivider(color = StudioColors.Border)

        if (node == null) {
            Text(
                "Выбери ноду на холсте, чтобы изменить её свойства.",
                style = MaterialTheme.typography.bodySmall,
                color = StudioColors.TextMuted
            )
        } else {
            Text(
                node.kind.name,
                style = MaterialTheme.typography.labelMedium,
                color = StudioColors.Accent
            )

            // -------------------------------------------------
            // NODE TITLE
            // -------------------------------------------------

            var titleEditRecorded by remember(node.id) {
                mutableStateOf(false)
            }

            OutlinedTextField(
                value = node.title,
                onValueChange = { newTitle ->
                    if (newTitle != node.title) {
                        if (!titleEditRecorded) {
                            controller.checkpoint(graph)
                            titleEditRecorded = true
                        }

                        node.title = newTitle
                        onGraphChanged(graph)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { state ->
                        if (!state.isFocused) {
                            titleEditRecorded = false
                        }
                    },
                label = {
                    Text("Название")
                },
                singleLine = true
            )

            Text(
                "ID: ${node.id}",
                style = MaterialTheme.typography.bodySmall,
                color = StudioColors.TextMuted
            )

            HorizontalDivider(color = StudioColors.Border)

            // -------------------------------------------------
            // PARAMETERS
            // -------------------------------------------------

            Text(
                "Параметры",
                style = MaterialTheme.typography.titleSmall,
                color = StudioColors.Text
            )

            if (node.kind == NodeKind.IF) {
                Text(
                    "Условие проверяет равенство переменной и значения (==).",
                    style = MaterialTheme.typography.bodySmall,
                    color = StudioColors.TextMuted
                )
            }

            if (node.params.isEmpty()) {
                Text(
                    "У этой ноды нет параметров.",
                    style = MaterialTheme.typography.bodySmall,
                    color = StudioColors.TextMuted
                )
            }

            node.params.toSortedMap().forEach { (key, value) ->

                var editRecorded by remember(node.id, key) {
                    mutableStateOf(false)
                }

                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            key,
                            style = MaterialTheme.typography.labelMedium,
                            color = StudioColors.TextMuted
                        )

                        TextButton(
                            onClick = {
                                controller.checkpoint(graph)

                                node.params = node.params - key

                                onGraphChanged(graph)
                            },
                            contentPadding = PaddingValues(
                                horizontal = 6.dp,
                                vertical = 0.dp
                            )
                        ) {
                            Text(
                                "Удалить",
                                color = StudioColors.Error,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }

                    OutlinedTextField(
                        value = value,
                        onValueChange = { newValue ->
                            if (newValue != value) {
                                if (!editRecorded) {
                                    controller.checkpoint(graph)
                                    editRecorded = true
                                }

                                node.params = node.params + (key to newValue)

                                onGraphChanged(graph)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { state ->
                                if (!state.isFocused) {
                                    editRecorded = false
                                }
                            },
                        singleLine = true
                    )
                }
            }

            // -------------------------------------------------
            // ADD PARAMETER
            // -------------------------------------------------

            var newKey by remember(selectedNodeId) {
                mutableStateOf("")
            }

            var newValue by remember(selectedNodeId) {
                mutableStateOf("")
            }

            HorizontalDivider(color = StudioColors.Border)

            Text(
                "Добавить параметр",
                style = MaterialTheme.typography.titleSmall,
                color = StudioColors.Text
            )

            OutlinedTextField(
                value = newKey,
                onValueChange = {
                    newKey = it
                },
                modifier = Modifier.fillMaxWidth(),
                label = {
                    Text("Ключ")
                },
                singleLine = true
            )

            OutlinedTextField(
                value = newValue,
                onValueChange = {
                    newValue = it
                },
                modifier = Modifier.fillMaxWidth(),
                label = {
                    Text("Значение")
                },
                singleLine = true
            )

            Button(
                onClick = {
                    val key = newKey.trim()

                    if (
                        key.isNotEmpty() &&
                        key !in node.params
                    ) {
                        controller.checkpoint(graph)

                        node.params = node.params + (key to newValue)

                        newKey = ""
                        newValue = ""

                        onGraphChanged(graph)
                    }
                },
                enabled = newKey.isNotBlank() &&
                        newKey.trim() !in node.params,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Добавить параметр")
            }
        }

        // -----------------------------------------------------
        // VALIDATION
        // -----------------------------------------------------

        HorizontalDivider(color = StudioColors.Border)

        Text(
            "Проверка сценария",
            style = MaterialTheme.typography.titleSmall,
            color = StudioColors.Text
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "Ошибки: $errors",
                color = if (errors > 0) {
                    StudioColors.Error
                } else {
                    StudioColors.Success
                },
                style = MaterialTheme.typography.labelMedium
            )

            Text(
                "Предупреждения: $warnings",
                color = if (warnings > 0) {
                    StudioColors.Warning
                } else {
                    StudioColors.TextMuted
                },
                style = MaterialTheme.typography.labelMedium
            )
        }

        if (issues.isEmpty()) {
            Text(
                "Критических проблем не найдено.",
                color = StudioColors.Success,
                style = MaterialTheme.typography.bodySmall
            )
        } else {
            issues.forEach { issue ->
                DiagnosticItem(issue)
            }
        }
    }
}

@Composable
private fun DiagnosticItem(
    issue: GraphIssue
) {
    val color = when (issue.severity) {
        GraphIssueSeverity.ERROR ->
            StudioColors.Error

        GraphIssueSeverity.WARNING ->
            StudioColors.Warning

        GraphIssueSeverity.INFO ->
            StudioColors.Accent
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color.copy(alpha = 0.08f),
                MaterialTheme.shapes.small
            )
            .padding(9.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = when (issue.severity) {
                GraphIssueSeverity.ERROR -> "ОШИБКА"
                GraphIssueSeverity.WARNING -> "ПРЕДУПРЕЖДЕНИЕ"
                GraphIssueSeverity.INFO -> "ИНФОРМАЦИЯ"
            },
            color = color,
            style = MaterialTheme.typography.labelSmall
        )

        Text(
            issue.message,
            color = StudioColors.Text,
            style = MaterialTheme.typography.bodySmall
        )

        issue.nodeId?.let { id ->
            Text(
                "Node: $id",
                color = StudioColors.TextMuted,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}
