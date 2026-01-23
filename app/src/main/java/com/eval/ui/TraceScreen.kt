package com.eval.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eval.data.ApiTrace
import com.eval.data.ApiTracer
import com.eval.data.TraceFileInfo
import java.text.SimpleDateFormat
import java.util.*

private const val PAGE_SIZE = 25

/**
 * Screen showing the list of traced API calls.
 */
@Composable
fun TraceListScreen(
    onBack: () -> Unit,
    onSelectTrace: (String) -> Unit,
    onClearTraces: () -> Unit
) {
    var traceFiles by remember { mutableStateOf(ApiTracer.getTraceFiles()) }
    var currentPage by remember { mutableIntStateOf(0) }

    val totalPages = (traceFiles.size + PAGE_SIZE - 1) / PAGE_SIZE
    val startIndex = currentPage * PAGE_SIZE
    val endIndex = minOf(startIndex + PAGE_SIZE, traceFiles.size)
    val currentPageItems = if (traceFiles.isNotEmpty() && startIndex < traceFiles.size) {
        traceFiles.subList(startIndex, endIndex)
    } else {
        emptyList()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "API Trace Log",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clickable { onBack() },
                contentAlignment = Alignment.Center
            ) {
                Text("✕", fontSize = 24.sp, color = Color.White)
            }
        }

        Text(
            text = "${traceFiles.size} trace files",
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFFAAAAAA)
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Pagination controls
        if (totalPages > 1) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { if (currentPage > 0) currentPage-- },
                    enabled = currentPage > 0,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF3366BB),
                        disabledContainerColor = Color(0xFF333333)
                    )
                ) {
                    Text("◀ Prev")
                }
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = "Page ${currentPage + 1} of $totalPages",
                    color = Color.White,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.width(16.dp))
                Button(
                    onClick = { if (currentPage < totalPages - 1) currentPage++ },
                    enabled = currentPage < totalPages - 1,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF3366BB),
                        disabledContainerColor = Color(0xFF333333)
                    )
                ) {
                    Text("Next ▶")
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Table header
        Card(
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF2A2A2A)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Hostname",
                    color = Color(0xFF6B9BFF),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    modifier = Modifier.weight(1.5f)
                )
                Text(
                    text = "Date/Time",
                    color = Color(0xFF6B9BFF),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    modifier = Modifier.weight(1.2f),
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Status",
                    color = Color(0xFF6B9BFF),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    modifier = Modifier.weight(0.5f),
                    textAlign = TextAlign.End
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Trace list
        if (traceFiles.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No API traces recorded yet",
                    color = Color(0xFFAAAAAA),
                    fontSize = 16.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(currentPageItems) { traceInfo ->
                    TraceListItem(
                        traceInfo = traceInfo,
                        onClick = { onSelectTrace(traceInfo.filename) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Clear button
        Button(
            onClick = {
                onClearTraces()
                traceFiles = emptyList()
                currentPage = 0
            },
            enabled = traceFiles.isNotEmpty(),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFCC3333),
                disabledContainerColor = Color(0xFF444444)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Clear trace container")
        }
    }
}

@Composable
private fun TraceListItem(
    traceInfo: TraceFileInfo,
    onClick: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("MM/dd HH:mm:ss", Locale.US) }
    val statusColor = when {
        traceInfo.statusCode in 200..299 -> Color(0xFF4CAF50)  // Green for success
        traceInfo.statusCode in 400..499 -> Color(0xFFFF9800)  // Orange for client errors
        traceInfo.statusCode >= 500 -> Color(0xFFF44336)       // Red for server errors
        else -> Color.White
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1E1E1E)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = traceInfo.hostname,
                color = Color.White,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1.5f)
            )
            Text(
                text = dateFormat.format(Date(traceInfo.timestamp)),
                color = Color(0xFFAAAAAA),
                fontSize = 12.sp,
                modifier = Modifier.weight(1.2f),
                textAlign = TextAlign.Center
            )
            Text(
                text = "${traceInfo.statusCode}",
                color = statusColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(0.5f),
                textAlign = TextAlign.End
            )
        }
    }
}

/**
 * Screen showing the details of a single API trace.
 */
@Composable
fun TraceDetailScreen(
    filename: String,
    onBack: () -> Unit
) {
    val trace = remember { ApiTracer.readTraceFile(filename) }
    var showPostDataDialog by remember { mutableStateOf(false) }
    var showResponseDataDialog by remember { mutableStateOf(false) }

    if (trace == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            Text("Error loading trace file", color = Color.White)
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Trace Detail",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clickable { onBack() },
                contentAlignment = Alignment.Center
            ) {
                Text("✕", fontSize = 24.sp, color = Color.White)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (!trace.request.body.isNullOrBlank()) {
                Button(
                    onClick = { showPostDataDialog = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF3366BB)
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Show POST data", fontSize = 12.sp)
                }
            }
            if (!trace.response.body.isNullOrBlank()) {
                Button(
                    onClick = { showResponseDataDialog = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF3366BB)
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Show RESPONSE data", fontSize = 12.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Full JSON pretty print
        val prettyJson = remember { ApiTracer.prettyPrintJson(ApiTracer.readTraceFileRaw(filename)) }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color(0xFF1A1A1A))
        ) {
            val verticalScrollState = rememberScrollState()
            val horizontalScrollState = rememberScrollState()

            Text(
                text = prettyJson,
                color = Color(0xFFE0E0E0),
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                modifier = Modifier
                    .padding(8.dp)
                    .verticalScroll(verticalScrollState)
                    .horizontalScroll(horizontalScrollState)
            )
        }
    }

    // POST data dialog
    if (showPostDataDialog && !trace.request.body.isNullOrBlank()) {
        PrettyPrintDialog(
            title = "POST Data",
            content = trace.request.body,
            onDismiss = { showPostDataDialog = false }
        )
    }

    // Response data dialog
    if (showResponseDataDialog && !trace.response.body.isNullOrBlank()) {
        PrettyPrintDialog(
            title = "Response Data",
            content = trace.response.body,
            onDismiss = { showResponseDataDialog = false }
        )
    }
}

@Composable
private fun PrettyPrintDialog(
    title: String,
    content: String,
    onDismiss: () -> Unit
) {
    val prettyContent = remember { ApiTracer.prettyPrintJson(content) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1E1E1E),
        title = {
            Text(
                text = title,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
                    .background(Color(0xFF141414))
            ) {
                val verticalScrollState = rememberScrollState()
                val horizontalScrollState = rememberScrollState()

                Text(
                    text = prettyContent,
                    color = Color(0xFFE0E0E0),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    modifier = Modifier
                        .padding(8.dp)
                        .verticalScroll(verticalScrollState)
                        .horizontalScroll(horizontalScrollState)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = Color(0xFF6B9BFF))
            }
        }
    )
}
