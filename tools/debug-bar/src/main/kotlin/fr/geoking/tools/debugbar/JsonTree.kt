package fr.geoking.tools.debugbar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.serialization.json.*

@Composable
fun JsonTree(
    jsonElement: JsonElement,
    modifier: Modifier = Modifier,
    initialExpanded: Boolean = false
) {
    var expanded by remember { mutableStateOf(initialExpanded) }

    when (jsonElement) {
        is JsonObject -> {
            Column(modifier = modifier) {
                Text(
                    text = if (expanded) "▼ { ... }" else "▶ { ... } (${jsonElement.size} keys)",
                    color = Color(0xFF93C5FD),
                    fontSize = 12.sp,
                    modifier = Modifier.clickable { expanded = !expanded }
                )
                if (expanded) {
                    Column(modifier = Modifier.padding(start = 12.dp)) {
                        jsonElement.forEach { (key, value) ->
                            Row {
                                Text("$key: ", color = Color(0xFFFDE047), fontSize = 12.sp)
                                JsonTree(value, initialExpanded = false)
                            }
                        }
                    }
                }
            }
        }
        is JsonArray -> {
            Column(modifier = modifier) {
                Text(
                    text = if (expanded) "▼ [ ... ]" else "▶ [ ... ] (${jsonElement.size} items)",
                    color = Color(0xFF93C5FD),
                    fontSize = 12.sp,
                    modifier = Modifier.clickable { expanded = !expanded }
                )
                if (expanded) {
                    Column(modifier = Modifier.padding(start = 12.dp)) {
                        jsonElement.forEachIndexed { index, value ->
                            Row {
                                Text("[$index]: ", color = Color(0xFF94A3B8), fontSize = 12.sp)
                                JsonTree(value, initialExpanded = false)
                            }
                        }
                    }
                }
            }
        }
        is JsonPrimitive -> {
            Text(
                text = jsonElement.content,
                color = if (jsonElement.isString) Color(0xFF86EFAC) else Color(0xFFF472B6),
                fontSize = 12.sp
            )
        }
    }
}
