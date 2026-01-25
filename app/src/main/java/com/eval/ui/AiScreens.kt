package com.eval.ui

import android.content.Intent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eval.data.AiHistoryManager
import com.eval.data.AiHistoryFileInfo

/**
 * AI hub screen with links to New AI Report, AI History, and Prompt History.
 * Used as a navigation destination.
 */
@Composable
fun AiHubScreen(
    onNavigateBack: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToNewReport: () -> Unit,
    onNavigateToPromptHistory: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        EvalTitleBar(
            title = "AI",
            onBackClick = onNavigateBack,
            onEvalClick = onNavigateBack
        )

        Spacer(modifier = Modifier.height(16.dp))

        // New AI Report card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onNavigateToNewReport() },
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF2A3A4A)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "\uD83D\uDCDD",
                    fontSize = 24.sp
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = "New AI Report",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                    Text(
                        text = "Create a new AI report with custom prompt",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFAAAAAA)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Prompt History card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onNavigateToPromptHistory() },
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF2A3A4A)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "\uD83D\uDD52",
                    fontSize = 24.sp
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = "Prompt History",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                    Text(
                        text = "View and reuse previously used prompts",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFAAAAAA)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // AI History card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onNavigateToHistory() },
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF2A3A4A)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "\uD83D\uDCDA",
                    fontSize = 24.sp
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = "AI History",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                    Text(
                        text = "View previously generated AI reports",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFAAAAAA)
                    )
                }
            }
        }
    }
}

/**
 * AI History screen showing generated reports with pagination.
 * Used as a navigation destination.
 */
@Composable
fun AiHistoryScreenNav(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val historyFiles = remember { mutableStateOf(AiHistoryManager.getHistoryFiles()) }
    var currentPage by remember { mutableStateOf(0) }
    val pageSize = 10

    val totalPages = if (historyFiles.value.isEmpty()) 1 else (historyFiles.value.size + pageSize - 1) / pageSize
    val startIndex = currentPage * pageSize
    val endIndex = minOf(startIndex + pageSize, historyFiles.value.size)
    val currentPageFiles = if (historyFiles.value.isNotEmpty()) {
        historyFiles.value.subList(startIndex, endIndex)
    } else {
        emptyList()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        EvalTitleBar(
            title = "AI History",
            onBackClick = onNavigateBack,
            onEvalClick = onNavigateBack
        )

        Spacer(modifier = Modifier.height(8.dp))

        if (historyFiles.value.isEmpty()) {
            // Empty state
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No AI reports yet",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color(0xFF888888)
                )
            }
        } else {
            // History list
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                currentPageFiles.forEach { fileInfo ->
                    AiHistoryRowNav(
                        fileInfo = fileInfo,
                        context = context
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Pagination
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = { if (currentPage > 0) currentPage-- },
                    enabled = currentPage > 0
                ) {
                    Text(
                        "Previous",
                        color = if (currentPage > 0) Color(0xFF6B9BFF) else Color(0xFF444444)
                    )
                }

                Text(
                    text = "${currentPage + 1} / $totalPages",
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = Color.White
                )

                TextButton(
                    onClick = { if (currentPage < totalPages - 1) currentPage++ },
                    enabled = currentPage < totalPages - 1
                ) {
                    Text(
                        "Next",
                        color = if (currentPage < totalPages - 1) Color(0xFF6B9BFF) else Color(0xFF444444)
                    )
                }
            }
        }
    }
}

/**
 * Single row in AI History showing filename with share and chrome actions.
 */
@Composable
private fun AiHistoryRowNav(
    fileInfo: AiHistoryFileInfo,
    context: android.content.Context
) {
    // Extract prompt from HTML file (cached)
    val promptPreview = remember(fileInfo.file) {
        extractPromptFromHtmlFile(fileInfo.file)
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF2A3A4A)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Filename
                Text(
                    text = fileInfo.filename,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    modifier = Modifier.weight(1f)
                )

                // Actions
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Share button
                    TextButton(
                        onClick = { shareHistoryFileNav(context, fileInfo.file) },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("share", color = Color(0xFF6B9BFF), fontSize = 14.sp)
                    }

                    // Chrome button
                    TextButton(
                        onClick = { openHistoryFileInChromeNav(context, fileInfo.file) },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("chrome", color = Color(0xFF6B9BFF), fontSize = 14.sp)
                    }
                }
            }

            // Prompt preview (first 3 lines)
            if (promptPreview.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = promptPreview,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF888888),
                    fontSize = 11.sp,
                    maxLines = 3,
                    lineHeight = 14.sp
                )
            }
        }
    }
}

/**
 * Extracts the prompt from an HTML file by finding the prompt-text pre element.
 * Returns the first 3 lines of the prompt, or empty string if not found.
 */
private fun extractPromptFromHtmlFile(file: java.io.File): String {
    return try {
        val html = file.readText()
        // Look for the prompt in <pre class="prompt-text">...</pre>
        val startMarker = """<pre class="prompt-text">"""
        val endMarker = "</pre>"
        val startIndex = html.indexOf(startMarker)
        if (startIndex == -1) return ""

        val contentStart = startIndex + startMarker.length
        val endIndex = html.indexOf(endMarker, contentStart)
        if (endIndex == -1) return ""

        val promptHtml = html.substring(contentStart, endIndex)
        // Unescape HTML entities
        val prompt = promptHtml
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("<br>", "\n")
            .replace("<br/>", "\n")
            .replace("<br />", "\n")

        // Get first 3 non-empty lines
        val lines = prompt.trim().lines().filter { it.isNotBlank() }.take(3)
        lines.joinToString("\n")
    } catch (e: Exception) {
        ""
    }
}

/**
 * Share a history file via Android share sheet.
 */
private fun shareHistoryFileNav(context: android.content.Context, file: java.io.File) {
    try {
        val contentUri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/html"
            putExtra(Intent.EXTRA_SUBJECT, "AI Report - ${file.nameWithoutExtension}")
            putExtra(Intent.EXTRA_TEXT, "AI analysis report.\n\nOpen the attached HTML file in a browser to view the report.")
            putExtra(Intent.EXTRA_STREAM, contentUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(Intent.createChooser(intent, "Share AI Report"))
    } catch (e: Exception) {
        android.widget.Toast.makeText(
            context,
            "Failed to share: ${e.message}",
            android.widget.Toast.LENGTH_SHORT
        ).show()
    }
}

/**
 * Open a history file in Chrome browser.
 */
private fun openHistoryFileInChromeNav(context: android.content.Context, file: java.io.File) {
    try {
        val contentUri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(contentUri, "text/html")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            setPackage("com.android.chrome")
        }

        try {
            context.startActivity(intent)
        } catch (e: android.content.ActivityNotFoundException) {
            intent.setPackage(null)
            context.startActivity(intent)
        }
    } catch (e: Exception) {
        android.widget.Toast.makeText(
            context,
            "Failed to open in Chrome: ${e.message}",
            android.widget.Toast.LENGTH_SHORT
        ).show()
    }
}

/**
 * New AI Report screen for entering a custom prompt.
 * Used as a navigation destination.
 */
@Composable
fun AiNewReportScreen(
    viewModel: GameViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToAiReports: () -> Unit = {},
    initialTitle: String = "",
    initialPrompt: String = ""
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    var title by remember { mutableStateOf(initialTitle) }
    var prompt by remember { mutableStateOf(initialPrompt) }

    // Navigate to AI Reports screen when agent selection is triggered
    LaunchedEffect(uiState.showGenericAiAgentSelection) {
        if (uiState.showGenericAiAgentSelection) {
            viewModel.dismissGenericAiAgentSelection()
            onNavigateToAiReports()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        EvalTitleBar(
            title = "New AI Report",
            onBackClick = onNavigateBack,
            onEvalClick = onNavigateBack
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Title field
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("Title") },
            placeholder = { Text("Enter a title for the report") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFF8B5CF6),
                unfocusedBorderColor = Color(0xFF444444),
                focusedLabelColor = Color(0xFF8B5CF6),
                unfocusedLabelColor = Color.Gray,
                cursorColor = Color.White
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Prompt field
        OutlinedTextField(
            value = prompt,
            onValueChange = { prompt = it },
            label = { Text("AI Prompt") },
            placeholder = { Text("Enter your prompt for the AI...") },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            minLines = 10,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFF8B5CF6),
                unfocusedBorderColor = Color(0xFF444444),
                focusedLabelColor = Color(0xFF8B5CF6),
                unfocusedLabelColor = Color.Gray,
                cursorColor = Color.White
            )
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Submit button
        Button(
            onClick = {
                if (title.isNotBlank() && prompt.isNotBlank()) {
                    // Save to prompt history
                    val prefs = context.getSharedPreferences(SettingsPreferences.PREFS_NAME, android.content.Context.MODE_PRIVATE)
                    val settingsPrefs = SettingsPreferences(prefs)
                    settingsPrefs.savePromptToHistory(title, prompt)

                    viewModel.showGenericAiAgentSelection(title, prompt)
                }
            },
            enabled = title.isNotBlank() && prompt.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF8B5CF6)
            )
        ) {
            Text("Submit", fontSize = 16.sp)
        }
    }
}

// Helper functions for sharing/opening generic AI reports
internal fun shareGenericAiReports(context: android.content.Context, uiState: GameUiState) {
    try {
        val appVersion = try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown"
        } catch (e: Exception) { "unknown" }
        val html = convertGenericAiReportsToHtml(uiState, appVersion)

        // Save to AI history
        com.eval.data.AiHistoryManager.saveReport(html, com.eval.data.AiReportType.GENERAL)

        val cacheDir = java.io.File(context.cacheDir, "ai_analysis")
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }

        val title = uiState.genericAiPromptTitle
        val htmlFile = java.io.File(cacheDir, "generic_ai_reports.html")
        htmlFile.writeText(html)

        val contentUri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            htmlFile
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/html"
            putExtra(Intent.EXTRA_SUBJECT, "AI Report - $title")
            putExtra(Intent.EXTRA_TEXT, "AI analysis report: $title.\n\nOpen the attached HTML file in a browser to view the report.")
            putExtra(Intent.EXTRA_STREAM, contentUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(Intent.createChooser(intent, "Share AI Report"))
    } catch (e: Exception) {
        android.widget.Toast.makeText(
            context,
            "Failed to share: ${e.message}",
            android.widget.Toast.LENGTH_SHORT
        ).show()
    }
}

internal fun openGenericAiReportsInChrome(context: android.content.Context, uiState: GameUiState) {
    try {
        val appVersion = try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown"
        } catch (e: Exception) { "unknown" }
        val html = convertGenericAiReportsToHtml(uiState, appVersion)

        // Save to AI history
        com.eval.data.AiHistoryManager.saveReport(html, com.eval.data.AiReportType.GENERAL)

        val cacheDir = java.io.File(context.cacheDir, "ai_analysis")
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }

        val htmlFile = java.io.File(cacheDir, "generic_ai_reports.html")
        htmlFile.writeText(html)

        val contentUri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            htmlFile
        )

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(contentUri, "text/html")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(intent)
    } catch (e: Exception) {
        android.widget.Toast.makeText(
            context,
            "Failed to open in Chrome: ${e.message}",
            android.widget.Toast.LENGTH_SHORT
        ).show()
    }
}

/**
 * Prompt History screen showing previously used prompts with pagination.
 * Used as a navigation destination.
 */
@Composable
fun PromptHistoryScreen(
    onNavigateBack: () -> Unit,
    onSelectEntry: (PromptHistoryEntry) -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(SettingsPreferences.PREFS_NAME, android.content.Context.MODE_PRIVATE) }
    val settingsPrefs = remember { SettingsPreferences(prefs) }
    val historyEntries = remember { mutableStateOf(settingsPrefs.loadPromptHistory()) }
    var currentPage by remember { mutableStateOf(0) }
    val pageSize = 25

    val totalPages = if (historyEntries.value.isEmpty()) 1 else (historyEntries.value.size + pageSize - 1) / pageSize
    val startIndex = currentPage * pageSize
    val endIndex = minOf(startIndex + pageSize, historyEntries.value.size)
    val currentPageEntries = if (historyEntries.value.isNotEmpty()) {
        historyEntries.value.subList(startIndex, endIndex)
    } else {
        emptyList()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        EvalTitleBar(
            title = "Prompt History",
            onBackClick = onNavigateBack,
            onEvalClick = onNavigateBack
        )

        Spacer(modifier = Modifier.height(8.dp))

        if (historyEntries.value.isEmpty()) {
            // Empty state
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No prompt history yet",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color(0xFF888888)
                )
            }
        } else {
            // History list
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                currentPageEntries.forEach { entry ->
                    PromptHistoryRow(
                        entry = entry,
                        onClick = { onSelectEntry(entry) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Pagination
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = { if (currentPage > 0) currentPage-- },
                    enabled = currentPage > 0
                ) {
                    Text(
                        "Previous",
                        color = if (currentPage > 0) Color(0xFF6B9BFF) else Color(0xFF444444)
                    )
                }

                Text(
                    text = "${currentPage + 1} / $totalPages",
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = Color.White
                )

                TextButton(
                    onClick = { if (currentPage < totalPages - 1) currentPage++ },
                    enabled = currentPage < totalPages - 1
                ) {
                    Text(
                        "Next",
                        color = if (currentPage < totalPages - 1) Color(0xFF6B9BFF) else Color(0xFF444444)
                    )
                }
            }
        }
    }
}

/**
 * Single row in Prompt History showing title and timestamp.
 */
@Composable
private fun PromptHistoryRow(
    entry: PromptHistoryEntry,
    onClick: () -> Unit
) {
    val dateFormat = remember { java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()) }
    val formattedDate = remember(entry.timestamp) { dateFormat.format(java.util.Date(entry.timestamp)) }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF2A3A4A)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // Title and timestamp row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = entry.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = formattedDate,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF888888)
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Prompt preview (truncated)
            Text(
                text = if (entry.prompt.length > 100) entry.prompt.take(100) + "..." else entry.prompt,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFAAAAAA),
                maxLines = 2
            )
        }
    }
}

/**
 * Type of AI report being generated.
 */
enum class AiReportScreenType { GAME, PLAYER, GENERIC }

/**
 * Navigation wrapper for AI Reports screen.
 */
@Composable
fun AiReportsScreenNav(
    viewModel: GameViewModel,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // Determine report type: generic > player > game
    val reportType = when {
        uiState.genericAiPromptTitle.isNotEmpty() -> AiReportScreenType.GENERIC
        uiState.playerAiReportsPlayerName.isNotEmpty() -> AiReportScreenType.PLAYER
        else -> AiReportScreenType.GAME
    }

    AiReportsScreen(
        uiState = uiState,
        savedAgentIds = viewModel.loadAiReportAgents(),
        reportType = reportType,
        onGenerate = { selectedAgentIds ->
            viewModel.saveAiReportAgents(selectedAgentIds)
            when (reportType) {
                AiReportScreenType.GENERIC -> viewModel.generateGenericAiReports(selectedAgentIds)
                AiReportScreenType.PLAYER -> viewModel.startPlayerAiReportsWithAgents(selectedAgentIds)
                AiReportScreenType.GAME -> viewModel.generateAiReportsWithAgents(selectedAgentIds)
            }
        },
        onShare = {
            when (reportType) {
                AiReportScreenType.GENERIC -> shareGenericAiReports(context, uiState)
                AiReportScreenType.PLAYER -> sharePlayerAiReports(context, uiState)
                AiReportScreenType.GAME -> shareAiReports(context, uiState)
            }
        },
        onOpenInBrowser = {
            when (reportType) {
                AiReportScreenType.GENERIC -> openGenericAiReportsInChrome(context, uiState)
                AiReportScreenType.PLAYER -> openPlayerAiReportsInChrome(context, uiState)
                AiReportScreenType.GAME -> openAiReportsInChrome(context, uiState)
            }
        },
        onDismiss = {
            when (reportType) {
                AiReportScreenType.GENERIC -> viewModel.dismissGenericAiReportsDialog()
                AiReportScreenType.PLAYER -> viewModel.dismissPlayerAiReportsDialog()
                AiReportScreenType.GAME -> viewModel.dismissAiReportsDialog()
            }
            onNavigateBack()
        }
    )
}

/**
 * Full-screen AI Reports generation and results screen.
 * Shows agent selection first, then progress and results.
 * Supports game reports, player reports, and generic reports.
 */
@Composable
fun AiReportsScreen(
    uiState: GameUiState,
    savedAgentIds: Set<String>,
    reportType: AiReportScreenType = AiReportScreenType.GAME,
    onGenerate: (Set<String>) -> Unit,
    onShare: () -> Unit,
    onOpenInBrowser: () -> Unit,
    onDismiss: () -> Unit
) {
    // Use appropriate state based on report type
    val reportsTotal = when (reportType) {
        AiReportScreenType.GENERIC -> uiState.genericAiReportsTotal
        AiReportScreenType.PLAYER -> uiState.playerAiReportsTotal
        AiReportScreenType.GAME -> uiState.aiReportsTotal
    }
    val reportsProgress = when (reportType) {
        AiReportScreenType.GENERIC -> uiState.genericAiReportsProgress
        AiReportScreenType.PLAYER -> uiState.playerAiReportsProgress
        AiReportScreenType.GAME -> uiState.aiReportsProgress
    }
    val reportsAgentResults = when (reportType) {
        AiReportScreenType.GENERIC -> uiState.genericAiReportsAgentResults
        AiReportScreenType.PLAYER -> uiState.playerAiReportsAgentResults
        AiReportScreenType.GAME -> uiState.aiReportsAgentResults
    }
    val reportsSelectedAgents = when (reportType) {
        AiReportScreenType.GENERIC -> uiState.genericAiReportsSelectedAgents
        AiReportScreenType.PLAYER -> uiState.playerAiReportsSelectedAgents
        AiReportScreenType.GAME -> uiState.aiReportsSelectedAgents
    }

    val isGenerating = reportsTotal > 0
    val isComplete = reportsProgress >= reportsTotal && reportsTotal > 0

    // Agent selection state
    val configuredAgents = uiState.aiSettings.getConfiguredAgents()
    var selectedAgentIds by remember {
        mutableStateOf(
            if (savedAgentIds.isNotEmpty()) {
                savedAgentIds.filter { id -> configuredAgents.any { it.id == id } }.toSet()
            } else {
                configuredAgents.map { it.id }.toSet()
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        EvalTitleBar(
            title = when {
                isComplete -> when (reportType) {
                    AiReportScreenType.GENERIC -> "Report Ready"
                    AiReportScreenType.PLAYER -> "Player Reports Ready"
                    AiReportScreenType.GAME -> "AI Reports Ready"
                }
                isGenerating -> when (reportType) {
                    AiReportScreenType.GENERIC -> "Generating Report"
                    AiReportScreenType.PLAYER -> "Generating Player Reports"
                    AiReportScreenType.GAME -> "Generating AI Reports"
                }
                else -> when (reportType) {
                    AiReportScreenType.GENERIC -> "Report: ${uiState.genericAiPromptTitle}"
                    AiReportScreenType.PLAYER -> "Player: ${uiState.playerAiReportsPlayerName}"
                    AiReportScreenType.GAME -> "AI Reports"
                }
            },
            onBackClick = onDismiss,
            onEvalClick = onDismiss
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (!isGenerating) {
            // Selection UI
            Text(
                text = "Select AI agents to generate reports:",
                color = Color.White,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (configuredAgents.isEmpty()) {
                        Text(
                            text = "No AI agents configured. Please configure agents in Settings > AI Setup.",
                            color = Color(0xFFAAAAAA)
                        )
                    } else {
                        configuredAgents.sortedBy { it.name.lowercase() }.forEach { agent ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedAgentIds = if (agent.id in selectedAgentIds) {
                                            selectedAgentIds - agent.id
                                        } else {
                                            selectedAgentIds + agent.id
                                        }
                                    }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = agent.id in selectedAgentIds,
                                    onCheckedChange = { checked ->
                                        selectedAgentIds = if (checked) {
                                            selectedAgentIds + agent.id
                                        } else {
                                            selectedAgentIds - agent.id
                                        }
                                    }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = agent.name,
                                        fontWeight = FontWeight.Medium,
                                        color = Color.White
                                    )
                                    Text(
                                        text = "${agent.provider.displayName} - ${agent.model}",
                                        fontSize = 12.sp,
                                        color = Color(0xFFAAAAAA)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Generate button
            Button(
                onClick = { onGenerate(selectedAgentIds) },
                enabled = selectedAgentIds.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF8B5CF6)
                )
            ) {
                Text("Generate Reports")
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Cancel button
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Cancel")
            }
        } else {
            // Progress/Results UI
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Show all selected agents with their status
                    reportsSelectedAgents.mapNotNull { agentId ->
                        uiState.aiSettings.getAgentById(agentId)
                    }.sortedBy { it.name.lowercase() }.forEach { agent ->
                        val result = reportsAgentResults[agent.id]
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = agent.name,
                                fontWeight = FontWeight.Medium,
                                color = Color.White
                            )
                            when {
                                result == null -> {
                                    // Still pending - show small spinner
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp,
                                        color = Color.Gray
                                    )
                                }
                                result.isSuccess -> {
                                    Text(
                                        text = "✓",
                                        color = Color(0xFF4CAF50),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp
                                    )
                                }
                                else -> {
                                    Text(
                                        text = "✗",
                                        color = Color(0xFFF44336),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Action buttons at the bottom
            if (isComplete) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = onShare,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF4CAF50)
                        )
                    ) {
                        Text("Share")
                    }
                    Button(
                        onClick = onOpenInBrowser,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF8B5CF6)
                        )
                    ) {
                        Text("Browser")
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Close/Cancel button
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (isComplete) "Close" else "Cancel")
            }
        }
    }
}
