package net.foxmediax.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.foxmediax.utils.nodes.NodeKind

data class PaletteEntry(
    val kind: NodeKind,
    val title: String,
    val description: String,
    val defaultParams: Map<String, String> = emptyMap()
)

val DefaultPalette = listOf(
    PaletteEntry(
        kind = NodeKind.PRINT,
        title = "Сообщение в чат",
        description = "Вывести текст в чат игрока",
        defaultParams = mapOf(
            "text" to "Привет, мир!"
        )
    ),

    PaletteEntry(
        kind = NodeKind.WAIT,
        title = "Ожидание",
        description = "Приостановить сценарий на время",
        defaultParams = mapOf(
            "ms" to "1000"
        )
    ),

    PaletteEntry(
        kind = NodeKind.DELAY,
        title = "Задержка",
        description = "Приостановить сценарий на время",
        defaultParams = mapOf(
            "ms" to "1000"
        )
    ),

    PaletteEntry(
        kind = NodeKind.CALL,
        title = "Запустить сценарий",
        description = "Запустить другой сценарий параллельно",
        defaultParams = mapOf(
            "target" to "other.sfxs"
        )
    ),

    PaletteEntry(
        kind = NodeKind.IF,
        title = "Условие IF",
        description = "Выполнить блок при совпадении значения",
        defaultParams = mapOf(
            "variable" to "dialog_button",
            "value" to "1"
        )
    )
)

@Composable
fun NodePalette(
    onAdd: (PaletteEntry) -> Unit,
    modifier: Modifier = Modifier
) {
    var query by remember {
        mutableStateOf("")
    }

    val filtered = remember(query) {
        if (query.isBlank()) {
            DefaultPalette
        } else {
            DefaultPalette.filter { entry ->
                entry.title.contains(query, ignoreCase = true) ||
                        entry.description.contains(query, ignoreCase = true) ||
                        entry.kind.name.contains(query, ignoreCase = true)
            }
        }
    }

    Column(
        modifier = modifier
            .background(StudioColors.Panel)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            "Ноды",
            style = MaterialTheme.typography.titleMedium,
            color = StudioColors.Text
        )

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text("Поиск...")
            },
            singleLine = true
        )

        HorizontalDivider(
            color = StudioColors.Border
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(filtered) { entry ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onAdd(entry)
                        },
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = StudioColors.PanelRaised
                    )
                ) {
                    Column(
                        Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            entry.title,
                            color = StudioColors.Text,
                            fontSize = 13.sp
                        )

                        Text(
                            entry.description,
                            color = StudioColors.TextMuted,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            if (filtered.isEmpty()) {
                item {
                    Text(
                        "Ничего не найдено",
                        color = StudioColors.TextMuted,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}