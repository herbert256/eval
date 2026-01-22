package com.eval.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
    val serverName = if (server == ChessServer.LICHESS) "Lichess" else "Chess.com"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF3A5A7C))  // Lighter blue background
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
                modifier = Modifier.widthIn(max = 400.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(games) { game ->
                    GameListItem(
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

/**
 * Individual game row in the selection list (table-like layout).
 */
@Composable
private fun GameListItem(
    game: LichessGame,
    username: String,
    onClick: () -> Unit
) {
    // Determine if user played white or black
    val userPlayedWhite = game.players.white.user?.name?.equals(username, ignoreCase = true) == true
    val userPlayedBlack = game.players.black.user?.name?.equals(username, ignoreCase = true) == true

    // Get opponent name
    val opponentName = if (userPlayedWhite) {
        game.players.black.user?.name
            ?: game.players.black.aiLevel?.let { "Stockfish $it" }
            ?: "Anonymous"
    } else {
        game.players.white.user?.name
            ?: game.players.white.aiLevel?.let { "Stockfish $it" }
            ?: "Anonymous"
    }

    // Determine result from user's perspective
    val (resultText, resultColor) = when {
        game.winner == "white" && userPlayedWhite -> "won" to Color(0xFF4CAF50)  // Green
        game.winner == "black" && userPlayedBlack -> "won" to Color(0xFF4CAF50)  // Green
        game.winner == "white" && userPlayedBlack -> "lost" to Color(0xFFF44336) // Red
        game.winner == "black" && userPlayedWhite -> "lost" to Color(0xFFF44336) // Red
        game.status == "draw" || game.status == "stalemate" -> "draw" to Color(0xFF2196F3) // Blue
        game.winner == null && (game.status == "draw" || game.status == "stalemate" || game.status == "timeout" || game.status == "outoftime") -> {
            "draw" to Color(0xFF2196F3)
        }
        else -> game.status to Color.Gray
    }

    // Row colors based on which color the user played
    val rowBackgroundColor = if (userPlayedBlack) Color.Black else Color.White
    val rowTextColor = if (userPlayedBlack) Color.White else Color.Black

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Column 1: Opponent name
        Box(
            modifier = Modifier
                .weight(1.2f)
                .background(rowBackgroundColor)
                .padding(horizontal = 8.dp, vertical = 10.dp)
        ) {
            Text(
                text = opponentName,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = rowTextColor,
                maxLines = 1
            )
        }

        // Column 2: Format/speed
        Box(
            modifier = Modifier
                .weight(0.7f)
                .background(rowBackgroundColor)
                .padding(horizontal = 8.dp, vertical = 10.dp)
        ) {
            Text(
                text = game.speed ?: "",
                fontSize = 14.sp,
                color = rowTextColor,
                maxLines = 1
            )
        }

        // Column 4: Result
        Box(
            modifier = Modifier
                .width(50.dp)
                .background(rowBackgroundColor)
                .padding(horizontal = 8.dp, vertical = 10.dp),
            contentAlignment = Alignment.CenterEnd
        ) {
            Text(
                text = resultText,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = resultColor
            )
        }
    }
}

/**
 * Full screen view for selecting from previously analysed games.
 */
@Composable
fun AnalysedGamesScreen(
    games: List<AnalysedGame>,
    onSelectGame: (AnalysedGame) -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF3A5A7C))  // Lighter blue background
            .padding(16.dp)
    ) {
        // Header
        Text(
            text = "Previous analysed games",
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
                modifier = Modifier.widthIn(max = 500.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(games) { game ->
                    AnalysedGameListItem(
                        game = game,
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

/**
 * Individual analysed game row - shows both players with consistent styling.
 */
@Composable
private fun AnalysedGameListItem(
    game: AnalysedGame,
    onClick: () -> Unit
) {
    // Determine result color
    val resultColor = when (game.result) {
        "1-0" -> Color(0xFF4CAF50)    // Green - white won
        "0-1" -> Color(0xFF4CAF50)    // Green - black won
        else -> Color(0xFF2196F3)      // Blue - draw
    }

    // Consistent row styling (gray background)
    val rowBackgroundColor = Color(0xFF2A2A2A)  // Dark gray
    val rowTextColor = Color.White

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Column 1: White player
        Box(
            modifier = Modifier
                .weight(1f)
                .background(rowBackgroundColor)
                .padding(horizontal = 8.dp, vertical = 10.dp)
        ) {
            Text(
                text = game.whiteName,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = rowTextColor,
                maxLines = 1
            )
        }

        // Column 2: Black player
        Box(
            modifier = Modifier
                .weight(1f)
                .background(rowBackgroundColor)
                .padding(horizontal = 8.dp, vertical = 10.dp)
        ) {
            Text(
                text = game.blackName,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = rowTextColor,
                maxLines = 1
            )
        }

        // Column 3: Format/speed
        Box(
            modifier = Modifier
                .weight(0.5f)
                .background(rowBackgroundColor)
                .padding(horizontal = 8.dp, vertical = 10.dp)
        ) {
            Text(
                text = game.speed ?: "",
                fontSize = 14.sp,
                color = rowTextColor,
                maxLines = 1
            )
        }

        // Column 4: Result
        Box(
            modifier = Modifier
                .width(50.dp)
                .background(rowBackgroundColor)
                .padding(horizontal = 8.dp, vertical = 10.dp),
            contentAlignment = Alignment.CenterEnd
        ) {
            Text(
                text = game.result,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = resultColor
            )
        }
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
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF3A5A7C))  // Lighter blue background
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
                modifier = Modifier.widthIn(max = 400.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(retrieves) { entry ->
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
    val serverName = if (entry.server == ChessServer.LICHESS) "lichess.org" else "chess.com"
    val rowBackgroundColor = Color(0xFF2A2A2A)  // Dark gray
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
    onSelectGame: (LichessGame) -> Unit,
    onDismiss: () -> Unit
) {
    val serverName = if (entry.server == ChessServer.LICHESS) "lichess.org" else "chess.com"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF3A5A7C))  // Lighter blue background
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

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier.widthIn(max = 400.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(games) { game ->
                    RetrieveGameListItem(
                        game = game,
                        accountName = entry.accountName,
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

/**
 * Individual game row for retrieve games list - shows opponent and result.
 * Result is "1" green if won, "0" red if lost, "1/2" if draw.
 */
@Composable
private fun RetrieveGameListItem(
    game: LichessGame,
    accountName: String,
    onClick: () -> Unit
) {
    // Determine if account played white or black
    val accountPlayedWhite = game.players.white.user?.name?.equals(accountName, ignoreCase = true) == true
    val accountPlayedBlack = game.players.black.user?.name?.equals(accountName, ignoreCase = true) == true

    // Get opponent name
    val opponentName = if (accountPlayedWhite) {
        game.players.black.user?.name
            ?: game.players.black.aiLevel?.let { "Stockfish $it" }
            ?: "Anonymous"
    } else {
        game.players.white.user?.name
            ?: game.players.white.aiLevel?.let { "Stockfish $it" }
            ?: "Anonymous"
    }

    // Determine result from account's perspective
    val (resultText, resultColor) = when {
        game.winner == "white" && accountPlayedWhite -> "1" to Color(0xFF4CAF50)  // Green - won
        game.winner == "black" && accountPlayedBlack -> "1" to Color(0xFF4CAF50)  // Green - won
        game.winner == "white" && accountPlayedBlack -> "0" to Color(0xFFF44336) // Red - lost
        game.winner == "black" && accountPlayedWhite -> "0" to Color(0xFFF44336) // Red - lost
        else -> "1/2" to Color(0xFF2196F3) // Blue - draw
    }

    // Row colors based on which color the account played
    val rowBackgroundColor = if (accountPlayedBlack) Color.Black else Color.White
    val rowTextColor = if (accountPlayedBlack) Color.White else Color.Black

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Column 1: Opponent name
        Box(
            modifier = Modifier
                .weight(1f)
                .background(rowBackgroundColor)
                .padding(horizontal = 8.dp, vertical = 10.dp)
        ) {
            Text(
                text = opponentName,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = rowTextColor,
                maxLines = 1
            )
        }

        // Column 2: Result
        Box(
            modifier = Modifier
                .width(50.dp)
                .background(rowBackgroundColor)
                .padding(horizontal = 8.dp, vertical = 10.dp),
            contentAlignment = Alignment.CenterEnd
        ) {
            Text(
                text = resultText,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = resultColor
            )
        }
    }
}
