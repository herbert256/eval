package com.eval.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.eval.data.ChessServer

/**
 * Retrieve screen for fetching chess games from Lichess or Chess.com.
 */
@Composable
fun RetrieveScreen(
    viewModel: GameViewModel,
    uiState: GameUiState,
    onBack: () -> Unit
) {
    var lichessUsername by remember { mutableStateOf(viewModel.savedLichessUsername) }
    var lichessGamesCount by remember { mutableStateOf(uiState.lichessMaxGames.toString()) }
    var chessComUsername by remember { mutableStateOf(viewModel.savedChessComUsername) }
    var chessComGamesCount by remember { mutableStateOf(uiState.chessComMaxGames.toString()) }
    val focusManager = LocalFocusManager.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF2A2A2A))
            .padding(horizontal = 12.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Title row with back button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) {
                Text("< Back", color = Color.White)
            }
            Text(
                text = "Retrieve games",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Button to select from previous retrieves
        if (uiState.hasPreviousRetrieves) {
            Button(
                onClick = { viewModel.showPreviousRetrieves() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            ) {
                Text("Select from a previous retrieve")
            }
        }

        // Button to select from previous analysed games
        if (uiState.hasAnalysedGames) {
            Button(
                onClick = { viewModel.showAnalysedGames() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            ) {
                Text("Select from previous analysed games")
            }
        }

        // Error message
        if (uiState.errorMessage != null) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.error
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            ) {
                Text(
                    text = uiState.errorMessage ?: "",
                    color = Color.White,
                    modifier = Modifier.padding(12.dp),
                    textAlign = TextAlign.Center
                )
            }
        }

        // ===== LICHESS CARD =====
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "lichess.org",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFE0E0E0)
                )

                // Username field
                OutlinedTextField(
                    value = lichessUsername,
                    onValueChange = { lichessUsername = it },
                    placeholder = { Text("Enter username") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = Color(0xFF555555),
                        focusedBorderColor = MaterialTheme.colorScheme.primary
                    )
                )

                // Games count field
                OutlinedTextField(
                    value = lichessGamesCount,
                    onValueChange = { newValue ->
                        val filtered = newValue.filter { it.isDigit() }
                        lichessGamesCount = filtered
                        filtered.toIntOrNull()?.let { count ->
                            viewModel.setLichessMaxGames(count)
                        }
                    },
                    label = { Text("Number of games") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = Color(0xFF555555),
                        focusedBorderColor = MaterialTheme.colorScheme.primary
                    )
                )

                // Buttons row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val lichessCount = lichessGamesCount.toIntOrNull() ?: uiState.lichessMaxGames
                    Button(
                        onClick = {
                            focusManager.clearFocus()
                            if (lichessUsername.isNotBlank()) {
                                viewModel.fetchGames(ChessServer.LICHESS, lichessUsername, lichessCount)
                            }
                        },
                        enabled = !uiState.isLoading && lichessUsername.isNotBlank(),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Retrieve last $lichessCount games")
                    }
                    Button(
                        onClick = {
                            focusManager.clearFocus()
                            if (lichessUsername.isNotBlank()) {
                                viewModel.fetchGames(ChessServer.LICHESS, lichessUsername, 1)
                            }
                        },
                        enabled = !uiState.isLoading && lichessUsername.isNotBlank(),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Retrieve last game")
                    }
                }
            }
        }

        // ===== CHESS.COM CARD =====
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "chess.com",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFE0E0E0)
                )

                // Username field
                OutlinedTextField(
                    value = chessComUsername,
                    onValueChange = { chessComUsername = it },
                    placeholder = { Text("Enter username") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = Color(0xFF555555),
                        focusedBorderColor = MaterialTheme.colorScheme.primary
                    )
                )

                // Games count field
                OutlinedTextField(
                    value = chessComGamesCount,
                    onValueChange = { newValue ->
                        val filtered = newValue.filter { it.isDigit() }
                        chessComGamesCount = filtered
                        filtered.toIntOrNull()?.let { count ->
                            viewModel.setChessComMaxGames(count)
                        }
                    },
                    label = { Text("Number of games") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = Color(0xFF555555),
                        focusedBorderColor = MaterialTheme.colorScheme.primary
                    )
                )

                // Buttons row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val chessComCount = chessComGamesCount.toIntOrNull() ?: uiState.chessComMaxGames
                    Button(
                        onClick = {
                            focusManager.clearFocus()
                            if (chessComUsername.isNotBlank()) {
                                viewModel.fetchGames(ChessServer.CHESS_COM, chessComUsername, chessComCount)
                            }
                        },
                        enabled = !uiState.isLoading && chessComUsername.isNotBlank(),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Retrieve last $chessComCount games")
                    }
                    Button(
                        onClick = {
                            focusManager.clearFocus()
                            if (chessComUsername.isNotBlank()) {
                                viewModel.fetchGames(ChessServer.CHESS_COM, chessComUsername, 1)
                            }
                        },
                        enabled = !uiState.isLoading && chessComUsername.isNotBlank(),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Retrieve last game")
                    }
                }
            }
        }

        // Loading indicator
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(40.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Fetching games...",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
