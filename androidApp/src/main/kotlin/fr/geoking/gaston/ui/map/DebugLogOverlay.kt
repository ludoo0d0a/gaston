package fr.geoking.gaston.ui.map

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*
import fr.geoking.gaston.CacheManager
import fr.geoking.gaston.shared.logging.DebugLogStore
import fr.geoking.gaston.shared.logging.NetworkLog
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun DebugLogOverlay(
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        DebugLogOverlayContent()
    }
}

@Composable
private fun DebugLogOverlayContent() {
    var isExpanded by remember { mutableStateOf(false) }
    val logs by DebugLogStore.logs.collectAsState()
    var selectedLog by remember { mutableStateOf<NetworkLog?>(null) }
    var selectedHost by remember { mutableStateOf<String?>(null) }
    val availableHosts = remember(logs) {
        logs.map { it.host }.filter { it.isNotEmpty() }.distinct().sorted()
    }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Box(modifier = Modifier.padding(16.dp).zIndex(2f)) {
        if (!isExpanded) {
            FloatingActionButton(
                onClick = { isExpanded = true },
                containerColor = Color(0xFF334155).copy(alpha = 0.8f),
                contentColor = Color.White,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(Icons.Default.BugReport, contentDescription = "Show Logs")
            }
        } else {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.6f),
                color = Color(0xFF0F172A).copy(alpha = 0.95f),
                shape = RoundedCornerShape(16.dp),
                border = BoxShadow(Color.White.copy(alpha = 0.2f))
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Network Debug Logs (${logs.size})",
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        Row {
                            IconButton(onClick = {
                                scope.launch {
                                    CacheManager.clearAllCaches(context)
                                }
                            }) {
                                Icon(Icons.Default.CleaningServices, "Clear Cache", tint = Color.White)
                            }
                            IconButton(onClick = { DebugLogStore.clearLogs() }) {
                                Icon(Icons.Default.Delete, "Clear Logs", tint = Color.White)
                            }
                            IconButton(onClick = { isExpanded = false }) {
                                Icon(Icons.Default.Close, "Close", tint = Color.White)
                            }
                        }
                    }

                    if (availableHosts.isNotEmpty()) {
                        LazyRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            item {
                                FilterChip(
                                    selected = selectedHost == null,
                                    onClick = { selectedHost = null },
                                    label = { Text("All", fontSize = 11.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        containerColor = Color.Transparent,
                                        labelColor = Color.White.copy(alpha = 0.6f),
                                        selectedContainerColor = Color.White.copy(alpha = 0.2f),
                                        selectedLabelColor = Color.White
                                    ),
                                    border = FilterChipDefaults.filterChipBorder(
                                        borderColor = Color.White.copy(alpha = 0.2f),
                                        selectedBorderColor = Color.White.copy(alpha = 0.5f),
                                        borderWidth = 1.dp,
                                        selectedBorderWidth = 1.dp,
                                        enabled = true,
                                        selected = selectedHost == null
                                    )
                                )
                            }
                            items(availableHosts) { host ->
                                FilterChip(
                                    selected = selectedHost == host,
                                    onClick = { selectedHost = if (selectedHost == host) null else host },
                                    label = { Text(host, fontSize = 11.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        containerColor = Color.Transparent,
                                        labelColor = Color.White.copy(alpha = 0.6f),
                                        selectedContainerColor = Color.White.copy(alpha = 0.2f),
                                        selectedLabelColor = Color.White
                                    ),
                                    border = FilterChipDefaults.filterChipBorder(
                                        borderColor = Color.White.copy(alpha = 0.2f),
                                        selectedBorderColor = Color.White.copy(alpha = 0.5f),
                                        borderWidth = 1.dp,
                                        selectedBorderWidth = 1.dp,
                                        enabled = true,
                                        selected = selectedHost == host
                                    )
                                )
                            }
                        }
                    }

                    val filteredLogs = if (selectedHost == null) {
                        logs
                    } else {
                        logs.filter { it.host == selectedHost }
                    }

                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(filteredLogs, key = { it.id }) { log ->
                            LogItem(log, onClick = { selectedLog = log })
                        }
                    }
                }
            }
        }
    }

    if (selectedLog != null) {
        LogDetailsDialog(log = selectedLog!!, onDismiss = { selectedLog = null })
    }
}

@Composable
private fun LogItem(log: NetworkLog, onClick: () -> Unit) {
    val time = remember(log.timestamp) {
        SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date(log.timestamp))
    }
    val statusColor = when (log.statusCode) {
        in 200..299 -> Color(0xFF4ADE80)
        in 400..499 -> Color(0xFFFACC15)
        in 500..599 -> Color(0xFFF87171)
        else -> Color.Gray
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = log.method,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                modifier = Modifier
                    .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = log.statusCode?.toString() ?: "ERR",
                color = statusColor,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "${log.durationMs}ms",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 11.sp
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = time,
                color = Color.White.copy(alpha = 0.4f),
                fontSize = 10.sp
            )
        }
        Text(
            text = log.url,
            color = Color.White.copy(alpha = 0.8f),
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp)
        )
        HorizontalDivider(
            modifier = Modifier.padding(top = 8.dp),
            color = Color.White.copy(alpha = 0.1f)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LogDetailsDialog(log: NetworkLog, onDismiss: () -> Unit) {
    var fullscreenBody by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
        title = {
            Text("Request Details", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        },
        text = {
            SelectionContainer {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    item {
                        DetailSection("General")
                        DetailItem("URL", log.url)
                        DetailItem("Method", log.method)
                        DetailItem("Status", log.statusCode?.toString() ?: "N/A")
                        DetailItem("Duration", "${log.durationMs}ms")
                        DetailItem("Timestamp", Date(log.timestamp).toString())

                        Spacer(modifier = Modifier.height(16.dp))
                        DetailSection("Request Headers")
                        log.requestHeaders.forEach { (k, v) ->
                            DetailItem(k, v.joinToString(", "))
                        }

                        val reqBody = log.safeRequestBody
                        if (reqBody.isNotBlank()) {
                            Spacer(modifier = Modifier.height(16.dp))
                            DetailSection("Request Body")
                            BodyContent(reqBody, onFullscreen = { fullscreenBody = reqBody })
                        }

                        log.responseHeaders?.let { headers ->
                            Spacer(modifier = Modifier.height(16.dp))
                            DetailSection("Response Headers")
                            headers.forEach { (k, v) ->
                                DetailItem(k, v.joinToString(", "))
                            }
                        }

                        val respBody = log.safeResponseBody
                        if (respBody.isNotBlank()) {
                            Spacer(modifier = Modifier.height(16.dp))
                            DetailSection("Response Body")
                            BodyContent(respBody, onFullscreen = { fullscreenBody = respBody })
                        }
                    }
                }
            }
        },
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.fillMaxWidth(0.95f).fillMaxHeight(0.8f)
    )

    if (fullscreenBody != null) {
        FullscreenBodyDialog(
            body = fullscreenBody!!,
            onDismiss = { fullscreenBody = null }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FullscreenBodyDialog(body: String, onDismiss: () -> Unit) {
    val jsonElement = remember(body) {
        try {
            Json.parseToJsonElement(body)
        } catch (e: Exception) {
            null
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Body Viewer", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }
        },
        text = {
            SelectionContainer {
                Surface(
                    color = Color.Black.copy(alpha = 0.05f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (jsonElement != null) {
                        JsonTree(
                            jsonElement = jsonElement,
                            modifier = Modifier.fillMaxSize().padding(8.dp),
                            initialExpanded = true,
                            useLazyColumn = true
                        )
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                            item {
                                Text(
                                    text = body,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            }
        },
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
private fun DetailSection(title: String) {
    Text(
        text = title,
        color = MaterialTheme.colorScheme.primary,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(vertical = 4.dp)
    )
}

@Composable
private fun DetailItem(label: String, value: String) {
    Row(modifier = Modifier.padding(vertical = 2.dp)) {
        Text(
            text = "$label: ",
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            modifier = Modifier.width(100.dp)
        )
        Text(text = value, fontSize = 12.sp)
    }
}

private data class JsonNode(
    val path: String,
    val key: String?,
    val value: JsonElement,
    val depth: Int
)

@Composable
private fun JsonTree(
    jsonElement: JsonElement,
    modifier: Modifier = Modifier,
    initialExpanded: Boolean = false,
    useLazyColumn: Boolean = false
) {
    val expandedPaths = remember { mutableStateMapOf<String, Boolean>() }

    val nodes = remember(jsonElement, expandedPaths.toMap()) {
        val list = mutableListOf<JsonNode>()
        fun collectNodes(path: String, key: String?, value: JsonElement, depth: Int) {
            list.add(JsonNode(path, key, value, depth))
            val isExpanded = expandedPaths.getOrPut(path) { initialExpanded }
            if (isExpanded) {
                when (value) {
                    is JsonObject -> {
                        value.forEach { (k, v) ->
                            collectNodes("$path/$k", k, v, depth + 1)
                        }
                    }
                    is JsonArray -> {
                        value.forEachIndexed { i, v ->
                            collectNodes("$path/$i", i.toString(), v, depth + 1)
                        }
                    }
                    else -> {}
                }
            }
        }
        collectNodes("", null, jsonElement, 0)
        list
    }

    if (useLazyColumn) {
        LazyColumn(modifier = modifier) {
            items(nodes, key = { it.path }) { node ->
                JsonNodeRow(
                    node = node,
                    isExpanded = expandedPaths.getOrPut(node.path) { initialExpanded },
                    onToggle = { expandedPaths[node.path] = !expandedPaths.getOrDefault(node.path, initialExpanded) }
                )
            }
        }
    } else {
        Column(modifier = modifier) {
            nodes.forEach { node ->
                JsonNodeRow(
                    node = node,
                    isExpanded = expandedPaths.getOrPut(node.path) { initialExpanded },
                    onToggle = { expandedPaths[node.path] = !expandedPaths.getOrDefault(node.path, initialExpanded) }
                )
            }
        }
    }
}

@Composable
private fun JsonNodeRow(
    node: JsonNode,
    isExpanded: Boolean,
    onToggle: () -> Unit
) {
    val indent = (node.depth * 12).dp
    val value = node.value

    when (value) {
        is JsonObject, is JsonArray -> {
            val label = when (value) {
                is JsonObject -> if (value.isEmpty()) "{ }" else "{ ... }"
                else -> if ((value as JsonArray).isEmpty()) "[ ]" else "[ ... ]"
            }
            ExpandableNode(
                indent = indent,
                key = node.key,
                label = label,
                isExpanded = isExpanded,
                onToggle = onToggle
            )
        }
        is JsonPrimitive -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = indent, top = 2.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Invisible spacer to align with expandable nodes (which have a 16dp icon)
                Spacer(modifier = Modifier.width(16.dp))
                if (node.key != null) {
                    Text(
                        text = "\"${node.key}\": ",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = if (value.isString) "\"${value.content}\"" else value.content,
                    color = when {
                        value.isString -> Color(0xFF2DD4BF)
                        value.content == "true" || value.content == "false" -> Color(0xFFF472B6)
                        value.content == "null" -> Color(0xFF94A3B8)
                        else -> Color(0xFFFB923C)
                    },
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
private fun ExpandableNode(
    indent: Dp,
    key: String?,
    label: String,
    isExpanded: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(start = indent, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (isExpanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowRight,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.5f),
            modifier = Modifier.size(16.dp)
        )
        if (key != null) {
            Text(
                text = "\"$key\": ",
                color = Color(0xFF94A3B8),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
        }
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun BodyContent(
    body: String,
    onFullscreen: (() -> Unit)? = null
) {
    val jsonElement = remember(body) {
        try {
            Json.parseToJsonElement(body)
        } catch (e: Exception) {
            null
        }
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        Surface(
            color = Color.Black.copy(alpha = 0.05f),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            if (jsonElement != null) {
                JsonTree(
                    jsonElement = jsonElement,
                    modifier = Modifier.padding(8.dp)
                )
            } else {
                Text(
                    text = body,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(8.dp)
                )
            }
        }

        if (onFullscreen != null) {
            IconButton(
                onClick = onFullscreen,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(24.dp)
            ) {
                Icon(
                    Icons.Default.Fullscreen,
                    contentDescription = "Fullscreen",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

private fun BoxShadow(color: Color) = androidx.compose.foundation.BorderStroke(1.dp, color)
