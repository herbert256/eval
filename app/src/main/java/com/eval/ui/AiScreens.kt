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
 * AI hub screen with links to New AI Report and AI History.
 * Used as a navigation destination.
 */
@Composable
fun AiHubScreen(
    onNavigateBack: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToNewReport: () -> Unit
) {
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
                text = "AI",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            TextButton(onClick = onNavigateBack) {
                Text("Close", color = Color(0xFF6B9BFF))
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

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
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "AI History",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            TextButton(onClick = onNavigateBack) {
                Text("Close", color = Color(0xFF6B9BFF))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

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
    Card(
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF2A3A4A)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
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
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var title by remember { mutableStateOf("") }
    var prompt by remember { mutableStateOf("") }

    // Handle agent selection dialog
    if (uiState.showGenericAiAgentSelection) {
        AiAgentsReportsSelectionDialog(
            aiSettings = uiState.aiSettings,
            savedAgentIds = viewModel.loadAiReportAgents(),
            onGenerate = { selectedAgentIds ->
                viewModel.saveAiReportAgents(selectedAgentIds)
                viewModel.generateGenericAiReports(selectedAgentIds)
            },
            onDismiss = { viewModel.dismissGenericAiAgentSelection() },
            title = "Select AI Agents"
        )
    }

    // Handle AI reports generation dialog
    if (uiState.showGenericAiReportsDialog) {
        GenericAiReportsDialog(
            uiState = uiState,
            viewModel = viewModel,
            onDismiss = { viewModel.dismissGenericAiReportsDialog() }
        )
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
                text = "New AI Report",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            TextButton(onClick = onNavigateBack) {
                Text("Cancel", color = Color(0xFF6B9BFF))
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

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

/**
 * Dialog for showing generic AI reports generation progress.
 */
@Composable
private fun GenericAiReportsDialog(
    uiState: GameUiState,
    viewModel: GameViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val isComplete = uiState.genericAiReportsProgress >= uiState.genericAiReportsTotal && uiState.genericAiReportsTotal > 0

    AlertDialog(
        onDismissRequest = { if (isComplete) onDismiss() },
        title = {
            Text(
                text = if (isComplete) "AI Reports Ready" else "Generating AI Reports",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Show all selected agents with their status
                uiState.genericAiReportsSelectedAgents.mapNotNull { agentId ->
                    uiState.aiSettings.getAgentById(agentId)
                }.sortedBy { it.name.lowercase() }.forEach { agent ->
                    val result = uiState.genericAiReportsAgentResults[agent.id]
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(agent.name, fontWeight = FontWeight.Medium)
                        when {
                            result == null -> {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = Color.Gray
                                )
                            }
                            result.isSuccess -> {
                                Text(
                                    text = "\u2713",
                                    color = Color(0xFF4CAF50),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            else -> {
                                Text(
                                    text = "\u2717",
                                    color = Color(0xFFF44336),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (isComplete) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            shareGenericAiReportsNav(context, uiState)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF4CAF50)
                        )
                    ) {
                        Text("Share")
                    }
                    Button(
                        onClick = {
                            openGenericAiReportsInChromeNav(context, uiState)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF8B5CF6)
                        )
                    ) {
                        Text("View in Chrome")
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(if (isComplete) "Close" else "Cancel")
            }
        }
    )
}

// Helper functions for sharing/opening generic AI reports
private fun shareGenericAiReportsNav(context: android.content.Context, uiState: GameUiState) {
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

private fun openGenericAiReportsInChromeNav(context: android.content.Context, uiState: GameUiState) {
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
 * Player info screen wrapper for navigation.
 */
@Composable
fun PlayerInfoScreenNav(
    viewModel: GameViewModel,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    PlayerInfoScreen(
        playerInfo = uiState.playerInfo,
        isLoading = uiState.playerInfoLoading,
        error = uiState.playerInfoError,
        games = uiState.playerGames,
        gamesLoading = uiState.playerGamesLoading,
        currentPage = uiState.playerGamesPage,
        pageSize = uiState.playerGamesPageSize,
        hasMoreGames = uiState.playerGamesHasMore,
        onNextPage = { viewModel.nextPlayerGamesPage() },
        onPreviousPage = { viewModel.previousPlayerGamesPage() },
        onGameSelected = { game -> viewModel.selectGameFromPlayerInfo(game) },
        onAiReportsClick = {
            uiState.playerInfo?.let { info ->
                val serverName = if (uiState.playerInfoError != null) {
                    null
                } else {
                    when (info.server) {
                        com.eval.data.ChessServer.LICHESS -> "lichess.org"
                        com.eval.data.ChessServer.CHESS_COM -> "chess.com"
                    }
                }
                viewModel.showPlayerAiReportsSelectionDialog(info.username, serverName, info)
            }
        },
        hasAiApiKeys = uiState.aiSettings.hasAnyApiKey(),
        onDismiss = onNavigateBack
    )
}
