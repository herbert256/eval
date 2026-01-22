package com.chessreplay.ui

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
import com.chessreplay.data.ChessServer
import com.chessreplay.data.LichessGame

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
