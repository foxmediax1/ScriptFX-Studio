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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.foxmediax.utils.nodes.GraphNode
import net.foxmediax.utils.nodes.NodeKind

/** Описание ноды в палитре. */
data class PaletteEntry(
    val kind: NodeKind,
    val title: String,
    val description: String,
    val defaultParams: Map<String, String> = emptyMap()
)

val DefaultPalette: List<PaletteEntry> = listOf(
    PaletteEntry(NodeKind.PRINT, "Сообщение в чат", "Вывести текст в чат игрока",
        mapOf("text" to "Привет, мир!")),
    PaletteEntry(NodeKind.WAIT, "Ожидание", "Подождать N мс", mapOf("ms" to "1000")),
    PaletteEntry(NodeKind.DELAY, "Задержка", "Задержка сцены", mapOf("ms" to "1000")),
    PaletteEntry(NodeKind.CALL, "Вызов", "Вызвать другой скрипт", mapOf("target" to "other.sfxs")),
    PaletteEntry(NodeKind.IF, "Условие", "Ветвление по условию", mapOf("expr" to "true")),
    PaletteEntry(NodeKind.SET, "Присвоить", "Установить переменную", mapOf("name" to "var", "value" to "0"))
)

@Composable
fun NodePalette(
    onAdd: (PaletteEntry) -> Unit,
    modifier: Modifier = Modifier
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(query) {
        if (query.isBlank()) DefaultPalette
        else DefaultPalette.filter {
            it.title.contains(query, ignoreCase = true) ||
                    it.description.contains(query, ignoreCase = true)
        }
    }

    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Ноды", style = MaterialTheme.typography.titleMedium, color = Color(0xFFE8E8F0))
        OutlinedTextField(
            value = query, onValueChange = { query = it },
            placeholder = { Text("Поиск…") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        HorizontalDivider()
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(filtered) { entry ->
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { onAdd(entry) },
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E28))
                ) {
                    Column(Modifier.padding(10.dp)) {
                        Text(entry.title, color = Color(0xFFE8E8F0), fontSize = 13.sp)
                        Text(entry.description, color = Color(0xFF9A9AB0), fontSize = 11.sp)
                    }
                }
            }
            if (filtered.isEmpty()) {
                item { Text("Ничего не найдено", color = Color(0xFF9A9AB0)) }
            }
        }
    }
}