
package net.foxmediax.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items

import androidx.compose.material3.*

import androidx.compose.runtime.Composable

import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

import net.foxmediax.app.project.ExportPreviewService

@Composable
fun ExportPreviewDialog(
    plan: ExportPreviewService.Plan,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {

    AlertDialog(
        onDismissRequest = onDismiss,

        title = {
            Text("Предпросмотр экспорта")
        },

        text = {

            Column(
                modifier = Modifier.widthIn(max = 650.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {

                // -------------------------------------------------
                // ERROR
                // -------------------------------------------------

                plan.error?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                // -------------------------------------------------
                // VALIDATION SUMMARY
                // -------------------------------------------------

                plan.validation?.let { report ->

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {

                        Text(
                            text = "Ошибки: ${report.errors}",
                            color = MaterialTheme.colorScheme.error
                        )

                        Text(
                            text = "Предупреждения: ${report.warnings}",
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }

                    if (report.issues.isNotEmpty()) {

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 130.dp),
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {

                            report.issues.take(8).forEach { entry ->

                                Text(
                                    text = "${entry.scenarioFile}: " +
                                            entry.issue.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (
                                        entry.issue.severity.name == "ERROR"
                                    ) {
                                        MaterialTheme.colorScheme.error
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }

                            if (report.issues.size > 8) {
                                Text(
                                    text = "И ещё проблем: ${report.issues.size - 8}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                HorizontalDivider()

                // -------------------------------------------------
                // COUNTERS
                // -------------------------------------------------

                Text(
                    text = "Добавить: ${plan.additions}   " +
                            "Обновить: ${plan.updates}"
                )

                Text(
                    text = "Без изменений: ${plan.unchanged}   " +
                            "Удалить: ${plan.removals}"
                )

                Text(
                    text = "Сохранить: ${plan.preserved}   " +
                            "Конфликты: ${plan.conflicts}",
                    color = if (plan.conflicts > 0) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )

                HorizontalDivider()

                // -------------------------------------------------
                // FILE LIST
                // -------------------------------------------------

                if (plan.entries.isEmpty()) {

                    Text(
                        text = "Нет изменений сценариев.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                } else {

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {

                        items(
                            items = plan.entries,
                            key = { it.path }
                        ) { entry ->

                            val color = when (entry.action) {

                                ExportPreviewService.Action.ADD ->
                                    MaterialTheme.colorScheme.primary

                                ExportPreviewService.Action.UPDATE ->
                                    MaterialTheme.colorScheme.tertiary

                                ExportPreviewService.Action.UNCHANGED ->
                                    MaterialTheme.colorScheme.onSurfaceVariant

                                ExportPreviewService.Action.REMOVE ->
                                    MaterialTheme.colorScheme.error

                                ExportPreviewService.Action.PRESERVE ->
                                    MaterialTheme.colorScheme.secondary

                                ExportPreviewService.Action.CONFLICT ->
                                    MaterialTheme.colorScheme.error
                            }

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 5.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {

                                Text(
                                    text = "${entry.action.title}: ${entry.path}",
                                    color = color,
                                    style = MaterialTheme.typography.labelLarge
                                )

                                Text(
                                    text = entry.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant
                            )
                        }
                    }
                }

                // -------------------------------------------------
                // FOOTER
                // -------------------------------------------------

                Text(
                    text = "Дополнительные файлы проекта также будут " +
                            "скопированы. Файлы .graph.json не экспортируются.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },

        // -------------------------------------------------
        // BUTTONS
        // -------------------------------------------------

        confirmButton = {

            Button(
                enabled = plan.canExport,
                onClick = onConfirm
            ) {
                Text("Экспортировать")
            }
        },

        dismissButton = {

            TextButton(
                onClick = onDismiss
            ) {
                Text("Отмена")
            }
        }
    )
}
