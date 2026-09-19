package com.eval.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eval.data.ChessServer
import com.eval.data.LichessGame

private fun playerResultColor(result: String): Color = when (result) {
    "win" -> AppColors.ResultWon
    "lost" -> AppColors.ResultLost
    "draw" -> AppColors.ResultDraw
    else -> AppColors.MediumGray
}

/**
 * Full screen game selection view.
 */
@Composable
fun GameSelectionScreen(
    games: List<LichessGame>,
    username: String,
    server: ChessServer,
    onSelectGame: (LichessGame) -> Unit,
    onDismiss: () -> Unit
) {
    val serverName = if (server == ChessServer.LICHESS) "Lichess" else "Local"

    // Handle back navigation
    BackHandler { onDismiss() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.BlueGrayAccent)  // Lighter blue background
            .padding(16.dp)
    ) {
        // Header with user and server
        Text(
            text = "$username @ $serverName",
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            color = Color.White,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(games, key = { it.id }) { game ->
                    PlayerGameRow(
                        game = game,
                        username = username,
                        onClick = { onSelectGame(game) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Cancel")
        }
    }
}

/** A player's games show the opponent, their playing color, and their result. */
@Composable
internal fun PlayerGameRow(
    game: LichessGame,
    username: String,
    onClick: () -> Unit,
    format: String = game.speed
) {
    val playsWhite = playerPlaysWhite(game, username)
    fun displayName(player: com.eval.data.Player): String = player.user?.name
        ?: player.aiLevel?.let { "Stockfish $it" }
        ?: "Anonymous"
    val opponentName = when (playsWhite) {
        true -> displayName(game.players.black)
        false -> displayName(game.players.white)
        null -> "${displayName(game.players.white)} – ${displayName(game.players.black)}"
    }
    val resultText = playerResultText(game, username)
    val rowBackgroundColor = when (playsWhite) {
        true -> Color.White
        false -> Color.Black
        null -> AppColors.CardBackground
    }
    val rowTextColor = if (playsWhite == true) Color.Black else Color.White
    val resultColor = if (playsWhite == true) when (resultText) {
        "win" -> Color(0xFF256029)
        "lost" -> Color(0xFFB71C1C)
        "draw" -> Color(0xFF1565C0)
        else -> Color.DarkGray
    } else playerResultColor(resultText)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(rowBackgroundColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = opponentName,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = rowTextColor,
            modifier = Modifier.weight(1.2f),
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
        Text(
            text = format,
            fontSize = 14.sp,
            color = rowTextColor,
            modifier = Modifier.weight(0.7f),
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
        Text(
            text = resultText,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = resultColor,
            modifier = Modifier.width(48.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
            maxLines = 1
        )
    }
}

/**
 * Full screen view for selecting from previous game retrieves.
 */
@Composable
fun PreviousRetrievesScreen(
    retrieves: List<RetrievedGamesEntry>,
    onSelectRetrieve: (RetrievedGamesEntry) -> Unit,
    onDismiss: () -> Unit
) {
    // Handle back navigation
    BackHandler { onDismiss() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.BlueGrayAccent)  // Lighter blue background
            .padding(16.dp)
    ) {
        // Header
        Text(
            text = "Previous game retrieves",
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            color = Color.White,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(retrieves, key = { "${it.accountName}|${it.server}" }) { entry ->
                    RetrieveListItem(
                        entry = entry,
                        onClick = { onSelectRetrieve(entry) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Cancel")
        }
    }
}

/**
 * Individual retrieve entry row - shows account name and chess server.
 */
@Composable
private fun RetrieveListItem(
    entry: RetrievedGamesEntry,
    onClick: () -> Unit
) {
    val serverName = if (entry.server == ChessServer.LICHESS) "lichess.org" else "Local"
    val rowBackgroundColor = AppColors.CardBackground  // Dark gray
    val rowTextColor = Color.White

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Column 1: Account name
        Box(
            modifier = Modifier
                .weight(1f)
                .background(rowBackgroundColor)
                .padding(horizontal = 8.dp, vertical = 10.dp)
        ) {
            Text(
                text = entry.accountName,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = rowTextColor,
                maxLines = 1
            )
        }

        // Column 2: Chess server
        Box(
            modifier = Modifier
                .weight(0.7f)
                .background(rowBackgroundColor)
                .padding(horizontal = 8.dp, vertical = 10.dp)
        ) {
            Text(
                text = serverName,
                fontSize = 14.sp,
                color = rowTextColor,
                maxLines = 1
            )
        }
    }
}

/**
 * Full screen view for selecting a game from a previous retrieve.
 */
@Composable
fun SelectedRetrieveGamesScreen(
    entry: RetrievedGamesEntry,
    games: List<LichessGame>,
    currentPage: Int,
    isLoading: Boolean,
    hasMoreGames: Boolean,
    errorMessage: String?,
    onNextPage: (Int) -> Unit,
    onPreviousPage: () -> Unit,
    onSelectGame: (LichessGame) -> Unit,
    onDismiss: () -> Unit
) {
    val serverName = if (entry.server == ChessServer.LICHESS) "lichess.org" else "Local"
    val serverColor = AppColors.PaginationLink

    // Handle back navigation
    BackHandler { onDismiss() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.BlueGrayAccent)  // Lighter blue background
            .padding(16.dp)
    ) {
        // Header with account and server
        Text(
            text = "${entry.accountName} @ $serverName",
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            color = Color.White,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.TopCenter
        ) {
            val pageSize = (maxHeight.value / 38f).toInt().coerceAtLeast(5)

            // Calculate current page games
            val startIndex = currentPage * pageSize
            val endIndex = minOf(startIndex + pageSize, games.size)
            val currentGames = if (games.isNotEmpty() && startIndex < games.size) {
                games.subList(startIndex, endIndex)
            } else {
                emptyList()
            }

            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                if (errorMessage != null) {
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                // Games list
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(currentGames, key = { it.id }) { game ->
                        PlayerGameRow(
                            game = game,
                            username = entry.accountName,
                            onClick = { onSelectGame(game) }
                        )
                    }
                }

                // Pagination controls
                val nextPageStartIndex = (currentPage + 1) * pageSize
                val canGoNext = nextPageStartIndex < games.size || hasMoreGames
                val showPagination = games.size > pageSize || hasMoreGames || currentPage > 0

                if (showPagination) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Previous button
                        if (currentPage > 0) {
                            TextButton(onClick = onPreviousPage) {
                                Text(
                                    text = "← Previous",
                                    color = serverColor,
                                    fontSize = 14.sp
                                )
                            }
                        } else {
                            Spacer(modifier = Modifier.width(1.dp))
                        }

                        // Page indicator
                        Text(
                            text = "Page ${currentPage + 1}",
                            color = Color.White,
                            fontSize = 12.sp
                        )

                        // Next button
                        if (canGoNext) {
                            TextButton(
                                onClick = { onNextPage(pageSize) },
                                enabled = !isLoading
                            ) {
                                if (isLoading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        color = serverColor,
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Text(
                                        text = "Next →",
                                        color = serverColor,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        } else {
                            Spacer(modifier = Modifier.width(1.dp))
                        }
                    }

                    // Show count info
                    if (games.isNotEmpty()) {
                        Text(
                            text = if (hasMoreGames) {
                                "Showing ${startIndex + 1}-$endIndex (${games.size} loaded)"
                            } else {
                                "Showing ${startIndex + 1}-$endIndex of ${games.size} games"
                            },
                            color = AppColors.LightGray,
                            fontSize = 11.sp,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Cancel")
        }
    }
}

/**
 * Full screen selection for previously analysed games.
 */
@Composable
fun AnalysedGamesSelectionScreen(
    games: List<AnalysedGame>,
    currentPage: Int,
    onNextPage: (Int) -> Unit,
    onPreviousPage: () -> Unit,
    onSelectGame: (AnalysedGame) -> Unit,
    onDismiss: () -> Unit
) {
    BackHandler { onDismiss() }

    val dateFormat = java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault())

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.BlueGrayAccent)
            .padding(16.dp)
    ) {
        Text(
            text = "Previous Analysed Games",
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            color = Color.White,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.TopCenter
        ) {
            val pageSize = (maxHeight.value / 72f).toInt().coerceAtLeast(3)

            val startIndex = currentPage * pageSize
            val endIndex = minOf(startIndex + pageSize, games.size)
            val currentGames = if (games.isNotEmpty() && startIndex < games.size) {
                games.subList(startIndex, endIndex)
            } else {
                emptyList()
            }

            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(currentGames, key = { it.timestamp }) { game ->
                        AnalysedGameListItem(
                            game = game,
                            dateFormat = dateFormat,
                            onClick = { onSelectGame(game) }
                        )
                    }
                }

                val showPagination = games.size > pageSize || currentPage > 0
                if (showPagination) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (currentPage > 0) {
                            TextButton(onClick = onPreviousPage) {
                                Text(
                                    text = "← Previous",
                                    color = AppColors.PaginationLink,
                                    fontSize = 14.sp
                                )
                            }
                        } else {
                            Spacer(modifier = Modifier.width(1.dp))
                        }

                        Text(
                            text = "Page ${currentPage + 1}",
                            color = Color.White,
                            fontSize = 12.sp
                        )

                        val nextPageStartIndex = (currentPage + 1) * pageSize
                        if (nextPageStartIndex < games.size) {
                            TextButton(onClick = { onNextPage(pageSize) }) {
                                Text(
                                    text = "Next →",
                                    color = AppColors.PaginationLink,
                                    fontSize = 14.sp
                                )
                            }
                        } else {
                            Spacer(modifier = Modifier.width(1.dp))
                        }
                    }

                    if (games.isNotEmpty()) {
                        Text(
                            text = "Showing ${startIndex + 1}-$endIndex of ${games.size} games",
                            color = AppColors.LightGray,
                            fontSize = 11.sp,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Cancel")
        }
    }
}

/**
 * Individual row for an analysed game - matches the player game row style.
 * Shows white player on white row and black player on black row.
 */
@Composable
private fun AnalysedGameListItem(
    game: AnalysedGame,
    dateFormat: java.text.SimpleDateFormat,
    onClick: () -> Unit
) {
    val (resultText, resultColor) = when (game.result) {
        "1-0" -> "1-0" to AppColors.ResultWon
        "0-1" -> "0-1" to AppColors.ResultLost
        "1/2-1/2" -> "1/2" to AppColors.ResultDraw
        else -> "-" to AppColors.MediumGray
    }

    val dateText = try {
        dateFormat.format(java.util.Date(game.timestamp))
    } catch (e: Exception) {
        ""
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        // White player row (white background)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Column 1: White player name
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(Color.White)
                    .padding(horizontal = 8.dp, vertical = 10.dp)
            ) {
                Text(
                    text = game.whiteName,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.Black,
                    maxLines = 1
                )
            }

            // Column 2: Speed
            Box(
                modifier = Modifier
                    .weight(0.6f)
                    .background(Color.White)
                    .padding(horizontal = 8.dp, vertical = 10.dp)
            ) {
                Text(
                    text = game.speed ?: "",
                    fontSize = 14.sp,
                    color = Color.Black,
                    maxLines = 1
                )
            }

            // Column 3: Result
            Box(
                modifier = Modifier
                    .width(56.dp)
                    .background(Color.White)
                    .padding(horizontal = 4.dp, vertical = 10.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Text(
                    text = resultText,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = resultColor,
                    maxLines = 1
                )
            }
        }

        // Black player row (black background)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Column 1: Black player name
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(Color.Black)
                    .padding(horizontal = 8.dp, vertical = 10.dp)
            ) {
                Text(
                    text = game.blackName,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White,
                    maxLines = 1
                )
            }

            // Column 2: Date
            Box(
                modifier = Modifier
                    .weight(0.6f)
                    .background(Color.Black)
                    .padding(horizontal = 8.dp, vertical = 10.dp)
            ) {
                Text(
                    text = dateText,
                    fontSize = 14.sp,
                    color = Color.White,
                    maxLines = 1
                )
            }

            // Column 3: Empty spacer to align with result column
            Box(
                modifier = Modifier
                    .width(56.dp)
                    .background(Color.Black)
                    .padding(horizontal = 4.dp, vertical = 10.dp)
            )
        }
    }
}
