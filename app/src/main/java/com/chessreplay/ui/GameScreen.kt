package com.chessreplay.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.window.Dialog
import androidx.activity.compose.BackHandler
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chessreplay.chess.ChessBoard
import com.chessreplay.chess.PieceColor
import com.chessreplay.chess.Square
import com.chessreplay.data.ChessSource
import com.chessreplay.data.LichessGame
import com.chessreplay.stockfish.PvLine
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

// Chess piece Unicode symbols
private const val WHITE_KING = "♔"
private const val WHITE_QUEEN = "♕"
private const val WHITE_ROOK = "♖"
private const val WHITE_BISHOP = "♗"
private const val WHITE_KNIGHT = "♘"
private const val WHITE_PAWN = "♙"
private const val BLACK_KING = "♚"
private const val BLACK_QUEEN = "♛"
private const val BLACK_ROOK = "♜"
private const val BLACK_BISHOP = "♝"
private const val BLACK_KNIGHT = "♞"
private const val BLACK_PAWN = "♟"

// Get just the piece symbol from a SAN move (for use with separate coordinates)
private fun getPieceSymbolFromSan(move: String, isWhite: Boolean): String {
    if (move.isEmpty()) return ""

    // Handle castling
    if (move == "O-O" || move == "O-O-O") {
        return if (isWhite) WHITE_KING else BLACK_KING
    }

    val pieceChar = move.first()
    return when {
        pieceChar == 'K' -> if (isWhite) WHITE_KING else BLACK_KING
        pieceChar == 'Q' -> if (isWhite) WHITE_QUEEN else BLACK_QUEEN
        pieceChar == 'R' -> if (isWhite) WHITE_ROOK else BLACK_ROOK
        pieceChar == 'B' -> if (isWhite) WHITE_BISHOP else BLACK_BISHOP
        pieceChar == 'N' -> if (isWhite) WHITE_KNIGHT else BLACK_KNIGHT
        pieceChar.isLowerCase() -> if (isWhite) WHITE_PAWN else BLACK_PAWN // Pawn move
        else -> if (isWhite) WHITE_PAWN else BLACK_PAWN
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameScreen(
    modifier: Modifier = Modifier,
    viewModel: GameViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var lichessUsername by remember { mutableStateOf(viewModel.savedLichessUsername) }
    var chessComUsername by remember { mutableStateOf(viewModel.savedChessComUsername) }
    var lichessGamesCount by remember { mutableStateOf(uiState.lichessMaxGames.toString()) }
    var chessComGamesCount by remember { mutableStateOf(uiState.chessComMaxGames.toString()) }
    val focusManager = LocalFocusManager.current

    // Keep screen on during Preview and Analyse stages
    val view = LocalView.current
    DisposableEffect(uiState.currentStage) {
        if (uiState.currentStage != AnalysisStage.MANUAL) {
            view.keepScreenOn = true
        }
        onDispose {
            view.keepScreenOn = false
        }
    }

    // Show settings screen or main game screen
    if (uiState.showSettingsDialog) {
        SettingsScreen(
            stockfishSettings = uiState.stockfishSettings,
            onBack = { viewModel.hideSettingsDialog() },
            onSaveStockfish = { viewModel.updateStockfishSettings(it) }
        )
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF2A2A2A))  // Lighter dark gray background
            .padding(horizontal = 12.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Title row with buttons (when game loaded) and settings button
        Row(
            modifier = Modifier
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (uiState.game != null) {
                // Two buttons on the left
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.offset(y = (-4).dp)) {
                    // Reload last game from active server
                    IconButton(onClick = { viewModel.reloadLastGame() }) {
                        Text("↻", fontSize = 34.sp, lineHeight = 34.sp, modifier = Modifier.offset(y = (-3).dp))
                    }
                    // Show retrieve games view
                    IconButton(onClick = { viewModel.clearGame() }) {
                        Text("≡", fontSize = 34.sp, lineHeight = 34.sp)
                    }
                }
            } else {
                Spacer(modifier = Modifier.width(96.dp))
            }
            Text(
                text = "Chess Replay",
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
                fontWeight = FontWeight.Light,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { viewModel.showSettingsDialog() }) {
                Text("⚙", fontSize = 30.sp, lineHeight = 30.sp)
            }
        }

        // Stage indicator - only show during Preview and Analyse stages
        if (uiState.game != null && uiState.currentStage != AnalysisStage.MANUAL) {
            val isPreviewStage = uiState.currentStage == AnalysisStage.PREVIEW
            val stageText = if (isPreviewStage) "Preview stage" else "Analyse stage"
            val stageColor = if (isPreviewStage) Color(0xFFFFAA00) else Color(0xFF6B9BFF)

            if (isPreviewStage) {
                // Preview stage: not clickable, just a label
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .background(stageColor.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stageText,
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                        color = stageColor
                    )
                }
            } else {
                // Analyse stage: clickable, enters Manual stage at biggest change
                Button(
                    onClick = { viewModel.enterManualStageAtBiggestChange() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = stageColor.copy(alpha = 0.2f),
                        contentColor = stageColor
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Text(
                        text = "$stageText - Click for manual stage",
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp
                    )
                }
            }
        }

        // Search section - only show when no game is loaded
        if (uiState.game == null) {
            // Subtitle
            Text(
                text = "Select Chess Server and Account",
                style = MaterialTheme.typography.titleMedium,
                color = Color(0xFFAAAAAA),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            )

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
                        text = uiState.errorMessage!!,
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
                                    viewModel.fetchGames(lichessUsername, ChessSource.LICHESS, lichessCount)
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
                                    viewModel.fetchGames(lichessUsername, ChessSource.LICHESS, 1)
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
                                    viewModel.fetchGames(chessComUsername, ChessSource.CHESS_COM, chessComCount)
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
                                    viewModel.fetchGames(chessComUsername, ChessSource.CHESS_COM, 1)
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

        // Game selection dialog
        if (uiState.showGameSelection && uiState.gameList.isNotEmpty()) {
            GameSelectionDialog(
                games = uiState.gameList,
                onSelectGame = { viewModel.selectGame(it) },
                onDismiss = { viewModel.dismissGameSelection() }
            )
        }

        // Game content
        if (uiState.game != null) {
            GameContent(uiState = uiState, viewModel = viewModel)
        }
    }
}

@Composable
private fun GameSelectionDialog(
    games: List<LichessGame>,
    onSelectGame: (LichessGame) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.8f),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Select a game",
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(games) { index, game ->
                        GameListItem(
                            game = game,
                            index = index + 1,
                            onClick = { onSelectGame(game) }
                        )
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
    }
}

@Composable
private fun GameListItem(
    game: LichessGame,
    index: Int,
    onClick: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()) }
    val gameDate = remember(game.createdAt) {
        game.createdAt?.let { dateFormat.format(Date(it)) } ?: ""
    }

    val whiteName = game.players.white.user?.name
        ?: game.players.white.aiLevel?.let { "Stockfish $it" }
        ?: "Anonymous"
    val blackName = game.players.black.user?.name
        ?: game.players.black.aiLevel?.let { "Stockfish $it" }
        ?: "Anonymous"

    val result = when (game.winner) {
        "white" -> "1-0"
        "black" -> "0-1"
        else -> if (game.status == "draw" || game.status == "stalemate") "½-½" else game.status
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Game number
            Text(
                text = "$index.",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                modifier = Modifier.width(32.dp)
            )

            Column(modifier = Modifier.weight(1f)) {
                // Players - names below each other
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .background(Color.White, RoundedCornerShape(2.dp))
                            .border(1.dp, Color.Gray, RoundedCornerShape(2.dp))
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = whiteName,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .background(Color.Black, RoundedCornerShape(2.dp))
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = blackName,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Game info
                Row {
                    Text(
                        text = gameDate,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "• ${game.speed}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Result
            Text(
                text = result,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = when (game.winner) {
                    "white" -> Color(0xFF4CAF50)
                    "black" -> Color(0xFFF44336)
                    else -> Color(0xFF2196F3)
                }
            )
        }
    }
}

@Composable
private fun GameContent(
    uiState: GameUiState,
    viewModel: GameViewModel
) {
    val game = uiState.game ?: return

    // Determine whose turn it is
    val turn = uiState.currentBoard.getTurn()
    val isWhiteTurn = turn == PieceColor.WHITE

    // Format initial clock time from game settings (e.g., "10:00" for 10 minutes)
    val initialClockTime = remember(game.clock) {
        game.clock?.let { clock ->
            val totalSeconds = clock.initial
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            if (seconds > 0) "%d:%02d".format(minutes, seconds) else "%d:00".format(minutes)
        }
    }

    // Get clock times for each player (find most recent clock for each color)
    val whiteClockTime = remember(uiState.currentMoveIndex, uiState.moveDetails, initialClockTime) {
        // White moves are at even indices (0, 2, 4, ...)
        // Start from most recent even index
        val startIdx = if (uiState.currentMoveIndex % 2 == 0) uiState.currentMoveIndex else uiState.currentMoveIndex - 1
        if (startIdx >= 0) {
            (startIdx downTo 0 step 2)
                .firstNotNullOfOrNull { idx -> uiState.moveDetails.getOrNull(idx)?.clockTime }
        } else {
            // At start position, use initial time from game settings
            initialClockTime
        }
    }
    val blackClockTime = remember(uiState.currentMoveIndex, uiState.moveDetails, initialClockTime) {
        // Black moves are at odd indices (1, 3, 5, ...)
        // Start from most recent odd index
        val startIdx = if (uiState.currentMoveIndex % 2 == 1) uiState.currentMoveIndex else uiState.currentMoveIndex - 1
        if (startIdx >= 1) {
            (startIdx downTo 1 step 2)
                .firstNotNullOfOrNull { idx -> uiState.moveDetails.getOrNull(idx)?.clockTime }
        } else {
            // At start position, use initial time from game settings
            initialClockTime
        }
    }

    // Player names and ratings
    val whiteName = game.players.white.user?.name
        ?: game.players.white.aiLevel?.let { "Stockfish $it" }
        ?: "Anonymous"
    val blackName = game.players.black.user?.name
        ?: game.players.black.aiLevel?.let { "Stockfish $it" }
        ?: "Anonymous"
    val whiteRating = game.players.white.rating
    val blackRating = game.players.black.rating

    // Result bar composable - shows move info, depth/nodes, and score
    val ResultBar: @Composable () -> Unit = {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Color(0xFF4A4A4A),  // Dark gray background
                    RoundedCornerShape(4.dp)
                )
                .border(1.dp, Color(0xFFFFD700), RoundedCornerShape(4.dp))  // Yellow border
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val moveIndex = uiState.currentMoveIndex
            val currentMove = uiState.moves.getOrNull(moveIndex)
            val isWhiteMove = moveIndex % 2 == 0
            val lastMove = uiState.currentBoard.getLastMove()

            // Determine which score/analysis to show - prefer analyse scores, fall back to preview
            val storedScore = uiState.analyseScores[moveIndex] ?: uiState.previewScores[moveIndex]
            val liveResult = uiState.analysisResult
            val isManualMode = uiState.currentStage == AnalysisStage.MANUAL

            if (currentMove != null && moveIndex >= 0) {
                val completeMoveNumber = (moveIndex / 2) + 1
                val totalCompleteMoves = (uiState.moves.size + 1) / 2

                val pieceSymbol = getPieceSymbolFromSan(currentMove, isWhiteMove)
                val fromSquare = lastMove?.from?.toAlgebraic() ?: ""
                val toSquare = lastMove?.to?.toAlgebraic() ?: ""
                val moveColor = if (isWhiteMove) Color.White else Color.Black

                // LEFT section - move number
                Text(
                    text = "Move: $completeMoveNumber/$totalCompleteMoves",
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                    color = Color.White,
                    modifier = Modifier.weight(1f)
                )

                // MIDDLE section - piece and coordinates
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = pieceSymbol,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 22.sp,
                        color = moveColor
                    )
                    Text(
                        text = " $fromSquare-$toSquare",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 20.sp,
                        color = moveColor
                    )
                }

                // RIGHT: Score
                val isCurrentlyAnalyzing = uiState.currentStage != AnalysisStage.MANUAL && uiState.autoAnalysisIndex == moveIndex
                val displayScore: MoveScore? = if ((isManualMode || isCurrentlyAnalyzing) && liveResult != null) {
                    val bestLine = liveResult.bestLine
                    if (bestLine != null) {
                        // Invert score to always show from WHITE's perspective
                        val adjustedScore = if (isWhiteTurn) -bestLine.score else bestLine.score
                        val adjustedMateIn = if (isWhiteTurn) -bestLine.mateIn else bestLine.mateIn
                        MoveScore(adjustedScore, bestLine.isMate, adjustedMateIn)
                    } else null
                } else {
                    storedScore
                }

                if (displayScore != null) {
                    val scoreText = if (displayScore.isMate) {
                        "M${kotlin.math.abs(displayScore.mateIn)}"
                    } else {
                        val absScore = kotlin.math.abs(displayScore.score)
                        if (displayScore.score >= 0) "+%.1f".format(absScore) else "-%.1f".format(absScore)
                    }
                    val scoreColor = when {
                        displayScore.isMate && displayScore.mateIn > 0 -> Color(0xFF00E676)  // Bright green
                        displayScore.isMate && displayScore.mateIn < 0 -> Color(0xFFFF1744)  // Vivid red
                        displayScore.score > 0.1f -> Color(0xFF00E676)  // Bright green
                        displayScore.score < -0.1f -> Color(0xFFFF1744)  // Vivid red
                        else -> Color(0xFF64B5F6)  // Bright blue
                    }
                    Text(
                        text = scoreText,
                        color = scoreColor,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 20.sp,
                        textAlign = TextAlign.End,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }
            } else {
                Text(
                    text = "Start position",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }
        }
    }

    // Graph content as a composable lambda
    val GraphContent: @Composable () -> Unit = {
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            if (uiState.moveDetails.isNotEmpty()) {
                // Use key to force recomposition when scores change
                key(uiState.previewScores.size, uiState.analyseScores.size) {
                    EvaluationGraph(
                        previewScores = uiState.previewScores,
                        analyseScores = uiState.analyseScores,
                        totalMoves = uiState.moveDetails.size,
                        currentMoveIndex = uiState.currentMoveIndex,
                        currentStage = uiState.currentStage,
                        onMoveSelected = { moveIndex ->
                            when (uiState.currentStage) {
                                AnalysisStage.PREVIEW -> { /* Not interruptible - ignore clicks */ }
                                AnalysisStage.ANALYSE -> viewModel.enterManualStageAtMove(moveIndex)
                                AnalysisStage.MANUAL -> viewModel.goToMove(moveIndex)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                    )
                }
            }
        }
    }

    // Game info card - shows graph and result bar in analyse mode, just graph in manual mode
    val GameInfoCard: @Composable () -> Unit = {
        GraphContent()
        if (uiState.currentStage != AnalysisStage.MANUAL) {
            Spacer(modifier = Modifier.height(8.dp))
            ResultBar()
        }
    }

    // Show game info card at top during analyse stage
    if (uiState.currentStage != AnalysisStage.MANUAL) {
        GameInfoCard()
        Spacer(modifier = Modifier.height(8.dp))
    }

    // Result bar above board in manual stage
    if (uiState.currentStage == AnalysisStage.MANUAL) {
        ResultBar()
        Spacer(modifier = Modifier.height(4.dp))
    }

    // Always show the board (no hiding during any stage)
    run {
        // Player bar above board (opponent when not flipped, or player when flipped)
        val topIsBlack = !uiState.flippedBoard
        PlayerBar(
            isWhite = !topIsBlack,
            playerName = if (topIsBlack) blackName else whiteName,
            rating = if (topIsBlack) blackRating else whiteRating,
            clockTime = if (topIsBlack) blackClockTime else whiteClockTime,
            isToMove = if (topIsBlack) !isWhiteTurn else isWhiteTurn
        )

        // Chess board - drag to make moves during manual replay (no interaction during other stages)
        // Get move arrows from Stockfish PV line (only in Manual stage)
        val numArrows = uiState.stockfishSettings.manualStage.numArrows
        val moveArrows: List<MoveArrow> = if (uiState.currentStage == AnalysisStage.MANUAL && numArrows > 0) {
            val pvLine = uiState.analysisResult?.pv ?: ""
            val pvMoves = pvLine.split(" ").filter { it.length >= 4 }.take(numArrows)
            val isWhiteTurnNow = uiState.currentBoard.getTurn() == PieceColor.WHITE

            pvMoves.mapIndexedNotNull { index, uciMove ->
                val fromFile = uciMove[0] - 'a'
                val fromRank = uciMove[1] - '1'
                val toFile = uciMove[2] - 'a'
                val toRank = uciMove[3] - '1'
                if (fromFile in 0..7 && fromRank in 0..7 && toFile in 0..7 && toRank in 0..7) {
                    // First move is by current turn, then alternates
                    val isWhiteMove = if (index % 2 == 0) isWhiteTurnNow else !isWhiteTurnNow
                    MoveArrow(
                        from = Square(fromFile, fromRank),
                        to = Square(toFile, toRank),
                        isWhiteMove = isWhiteMove,
                        index = index
                    )
                } else null
            }
        } else emptyList()

        Box(contentAlignment = Alignment.Center) {
            ChessBoardView(
                board = uiState.currentBoard,
                flipped = uiState.flippedBoard,
                interactionEnabled = !uiState.currentStage != AnalysisStage.MANUAL,
                onMove = { from, to -> viewModel.makeManualMove(from, to) },
                moveArrows = moveArrows,
                showArrowNumbers = uiState.stockfishSettings.manualStage.showArrowNumbers,
                whiteArrowColor = Color(uiState.stockfishSettings.manualStage.whiteArrowColor.toInt()),
                blackArrowColor = Color(uiState.stockfishSettings.manualStage.blackArrowColor.toInt()),
                modifier = Modifier.fillMaxWidth()
            )

            // Show game result overlay when at end position
            val isAtEndPosition = uiState.currentMoveIndex >= uiState.moveDetails.size - 1 && uiState.moveDetails.isNotEmpty()
            if (isAtEndPosition) {
                val gameResult = when (game.winner) {
                    "white" -> "1 - 0"
                    "black" -> "0 - 1"
                    else -> if (game.status == "draw" || game.status == "stalemate") "½ - ½" else null
                }
                gameResult?.let { result ->
                    Box(
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(16.dp))
                            .padding(horizontal = 24.dp, vertical = 12.dp)
                    ) {
                        Text(
                            text = result,
                            fontSize = 48.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }

        // Player bar below board (player when not flipped, or opponent when flipped)
        PlayerBar(
            isWhite = topIsBlack,
            playerName = if (topIsBlack) whiteName else blackName,
            rating = if (topIsBlack) whiteRating else blackRating,
            clockTime = if (topIsBlack) whiteClockTime else blackClockTime,
            isToMove = if (topIsBlack) isWhiteTurn else !isWhiteTurn
        )
    }

    // Back to original game button (shown when exploring a line) - above navigation buttons
    if (uiState.isExploringLine) {
        Button(
            onClick = { viewModel.backToOriginalGame() },
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF4A6741)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
        ) {
            Text("↩ Back to game", fontSize = 13.sp)
        }
    }

    // Controls - hide during auto-analysis
    if (!uiState.currentStage != AnalysisStage.MANUAL) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Use exploring line indices when exploring, otherwise use main game indices
            val isAtStart = if (uiState.isExploringLine) {
                uiState.exploringLineMoveIndex < 0
            } else {
                uiState.currentMoveIndex < 0
            }
            val isAtEnd = if (uiState.isExploringLine) {
                uiState.exploringLineMoveIndex >= uiState.exploringLineMoves.size - 1
            } else {
                uiState.currentMoveIndex >= uiState.moveDetails.size - 1
            }

            // Left part: Navigation buttons with fixed positions
            Row(
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Button 1: Go to start (hidden when exploring)
                if (!uiState.isExploringLine) {
                    ControlButton("⏮", enabled = !isAtStart) { viewModel.goToStart() }
                    Spacer(modifier = Modifier.width(6.dp))
                }

                // Button 2: Previous move (hidden when exploring and at start)
                val showPrevButton = !uiState.isExploringLine || !isAtStart
                if (showPrevButton) {
                    ControlButton("◀", enabled = !isAtStart) { viewModel.prevMove() }
                    Spacer(modifier = Modifier.width(6.dp))
                }

                // Button 3: Next move (hidden when exploring and at end)
                val showNextButton = !uiState.isExploringLine || !isAtEnd
                if (showNextButton) {
                    ControlButton("▶", enabled = !isAtEnd) { viewModel.nextMove() }
                }

                // Button 4: Go to end (hidden when exploring)
                if (!uiState.isExploringLine) {
                    Spacer(modifier = Modifier.width(6.dp))
                    ControlButton("⏭", enabled = !isAtEnd) { viewModel.goToEnd() }
                }
            }

            // Right part: Arrow toggle and flip board icons
            Row(
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Draw arrows toggle icon
                val drawArrowsEnabled = uiState.stockfishSettings.manualStage.drawArrows
                IconButton(onClick = { viewModel.toggleDrawArrows() }) {
                    Text(
                        text = "↗",
                        fontSize = 24.sp,
                        color = if (drawArrowsEnabled) Color.White else Color.Gray
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                // Flip board button
                ControlButton("↻") { viewModel.flipBoard() }
            }
        }
    }

    // Stockfish analysis panel - hide during auto-analysis
    if (!uiState.currentStage != AnalysisStage.MANUAL) {
        Spacer(modifier = Modifier.height(4.dp))
        AnalysisPanel(
            uiState = uiState,
            onExploreLine = { pv, moveIndex -> viewModel.exploreLine(pv, moveIndex) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 4.dp)
        )
    }

    // Show game info card between Stockfish panel and moves list during manual stage
    if (!uiState.currentStage != AnalysisStage.MANUAL) {
        GameInfoCard()
    }

    // Moves list - only show during manual replay (not during auto-analysis)
    if (!uiState.currentStage != AnalysisStage.MANUAL) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            modifier = Modifier
                .fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    text = "Moves",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                // Use analyse scores if available (they override preview scores)
                val displayScores = if (uiState.analyseScores.isNotEmpty()) {
                    // Merge: use analyse score if available, otherwise preview
                    uiState.previewScores + uiState.analyseScores
                } else {
                    uiState.previewScores
                }
                MovesList(
                    moveDetails = uiState.moveDetails,
                    currentMoveIndex = uiState.currentMoveIndex,
                    moveScores = displayScores,
                    currentStage = uiState.currentStage,
                    autoAnalysisIndex = uiState.autoAnalysisIndex,
                    onMoveClick = { viewModel.goToMove(it) }
                )
            }
        }
    }

    // PGN Info Card - always shown at bottom
    Spacer(modifier = Modifier.height(12.dp))
    Card(
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF2A4A6A)  // Lighter blue background
        ),
        modifier = Modifier
            .fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Centered title
            Text(
                text = "Game Information",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            // White player
            Text(
                text = "White: $whiteName${whiteRating?.let { " ($it)" } ?: ""}",
                fontSize = 13.sp,
                color = Color.White
            )
            // Black player
            Text(
                text = "Black: $blackName${blackRating?.let { " ($it)" } ?: ""}",
                fontSize = 13.sp,
                color = Color.White
            )
            // Time format
            val timeFormatText = buildString {
                append("Format: ")
                append(game.speed.replaceFirstChar { it.uppercase() })
                game.clock?.let { clock ->
                    val minutes = clock.initial / 60
                    val increment = clock.increment
                    append(" $minutes+$increment")
                }
                if (game.rated) append(" • Rated") else append(" • Casual")
            }
            Text(
                text = timeFormatText,
                fontSize = 13.sp,
                color = Color.White
            )
            // Opening
            uiState.openingName?.let { opening ->
                Text(
                    text = "Opening: $opening",
                    fontSize = 13.sp,
                    color = Color.White
                )
            }
            // Date
            game.createdAt?.let { timestamp ->
                val date = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
                    .format(java.util.Date(timestamp))
                Text(
                    text = "Date: $date",
                    fontSize = 13.sp,
                    color = Color.White
                )
            }
            // Result
            val resultText = when (game.winner) {
                "white" -> "1-0"
                "black" -> "0-1"
                else -> if (game.status == "draw" || game.status == "stalemate") "½-½" else game.status
            }
            Text(
                text = "Result: $resultText",
                fontSize = 13.sp,
                color = Color.White
            )
        }
    }
}

@Composable
private fun ControlButton(
    text: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.surface,
            disabledContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.4f),
            disabledContentColor = Color.Gray
        ),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(text = text, fontSize = 18.sp)
    }
}

@Composable
private fun PlayerBar(
    isWhite: Boolean,
    playerName: String,
    rating: Int?,
    clockTime: String?,
    isToMove: Boolean
) {
    val backgroundColor = if (isWhite) Color.White else Color.Black
    val textColor = if (isWhite) Color.Black else Color.White
    val borderColor = if (isToMove) Color.Red else Color.Transparent

    // Build player text: "Name (1234)"
    val playerText = buildString {
        append(playerName)
        if (rating != null) {
            append(" ($rating)")
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .then(
                if (isToMove) {
                    Modifier.border(2.dp, borderColor)
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = playerText,
            color = textColor,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp
        )

        // Clock time on the right
        if (clockTime != null) {
            Text(
                text = clockTime,
                color = textColor,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp
            )
        }
    }
}

@Composable
private fun MovesList(
    moveDetails: List<MoveDetails>,
    currentMoveIndex: Int,
    moveScores: Map<Int, MoveScore>,
    currentStage: AnalysisStage,
    autoAnalysisIndex: Int,
    onMoveClick: (Int) -> Unit
) {
    val movePairs = remember(moveDetails) { moveDetails.chunked(2) }
    val isAutoAnalyzing = currentStage != AnalysisStage.MANUAL

    Column(modifier = Modifier.fillMaxWidth()) {
        movePairs.forEachIndexed { pairIndex, pair ->
            key(pairIndex) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Move number
                    Text(
                        text = "${pairIndex + 1}.",
                        color = Color(0xFF666666),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 15.sp,
                        modifier = Modifier.width(32.dp)
                    )

                    // White move
                    val whiteIndex = pairIndex * 2
                    MoveChip(
                        moveDetails = pair[0],
                        isWhite = true,
                        isActive = whiteIndex == currentMoveIndex,
                        isAnalyzing = isAutoAnalyzing && autoAnalysisIndex == whiteIndex,
                        score = moveScores[whiteIndex],
                        onClick = { onMoveClick(whiteIndex) },
                        modifier = Modifier.weight(1f)
                    )

                    // Black move
                    if (pair.size > 1) {
                        Spacer(modifier = Modifier.width(4.dp))
                        val blackIndex = pairIndex * 2 + 1
                        MoveChip(
                            moveDetails = pair[1],
                            isWhite = false,
                            isActive = blackIndex == currentMoveIndex,
                            isAnalyzing = isAutoAnalyzing && autoAnalysisIndex == blackIndex,
                            score = moveScores[blackIndex],
                            onClick = { onMoveClick(blackIndex) },
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun MoveChip(
    moveDetails: MoveDetails,
    isWhite: Boolean,
    isActive: Boolean,
    isAnalyzing: Boolean = false,
    score: MoveScore? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = when {
        isActive -> MaterialTheme.colorScheme.primary
        isAnalyzing -> Color(0xFFFFE082) // Light yellow when analyzing
        else -> Color.Transparent
    }

    // Get piece symbol
    val pieceSymbol = when (moveDetails.pieceType) {
        "K" -> if (isWhite) BLACK_KING else WHITE_KING
        "Q" -> if (isWhite) BLACK_QUEEN else WHITE_QUEEN
        "R" -> if (isWhite) BLACK_ROOK else WHITE_ROOK
        "B" -> if (isWhite) BLACK_BISHOP else WHITE_BISHOP
        "N" -> if (isWhite) BLACK_KNIGHT else WHITE_KNIGHT
        else -> if (isWhite) BLACK_PAWN else WHITE_PAWN
    }

    // Build move text: piece from-to or piece fromxto
    val separator = if (moveDetails.isCapture) "x" else "-"
    val moveText = "$pieceSymbol ${moveDetails.from}$separator${moveDetails.to}"

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = moveText,
            fontSize = 15.sp,
            color = if (isActive) Color.White else MaterialTheme.colorScheme.onSurface
        )

        // Show clock time if available
        if (moveDetails.clockTime != null) {
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = moveDetails.clockTime,
                fontSize = 11.sp,
                color = if (isActive) Color.White.copy(alpha = 0.7f) else Color(0xFF888888)
            )
        }

        // Show score if available
        if (score != null) {
            Spacer(modifier = Modifier.width(4.dp))
            val scoreText = if (score.isMate) {
                "M${kotlin.math.abs(score.mateIn)}"
            } else {
                "%.1f".format(kotlin.math.abs(score.score))
            }
            val scoreColor = when {
                isActive -> Color.White.copy(alpha = 0.9f)
                score.isMate && score.mateIn > 0 -> Color(0xFF4CAF50) // Green for white winning mate
                score.isMate && score.mateIn < 0 -> Color(0xFFF44336) // Red for black winning mate
                score.score > 0.1f -> Color(0xFF4CAF50) // Green for white better
                score.score < -0.1f -> Color(0xFFF44336) // Red for black better
                else -> Color(0xFF2196F3) // Blue for equal (0)
            }
            Text(
                text = scoreText,
                fontSize = 15.sp,
                color = scoreColor
            )
        } else if (isAnalyzing) {
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "...",
                fontSize = 15.sp,
                color = Color(0xFF666666)
            )
        }
    }
}

@Composable
private fun EvaluationGraph(
    previewScores: Map<Int, MoveScore>,
    analyseScores: Map<Int, MoveScore>,
    totalMoves: Int,
    currentMoveIndex: Int,
    currentStage: AnalysisStage,
    onMoveSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val greenColor = Color(0xFF4CAF50)
    val redColor = Color(0xFFF44336)
    val lineColor = Color(0xFF666666)
    val currentMoveColor = Color(0xFF2196F3)
    val analyseColor = Color(0xFFFFEB3B) // Yellow for analyse stage scores

    // Track the graph width for calculating move index from drag position
    var graphWidth by remember { mutableStateOf(0f) }
    val isManualStage = currentStage == AnalysisStage.MANUAL

    Canvas(
        modifier = modifier
            .background(Color(0xFF1A1A1A), RoundedCornerShape(8.dp))
            .padding(8.dp)
            .pointerInput(totalMoves, currentStage) {
                // Only allow horizontal drag navigation in manual stage
                if (totalMoves > 0 && isManualStage) {
                    detectHorizontalDragGestures { change, _ ->
                        change.consume()
                        val x = change.position.x.coerceIn(0f, graphWidth)
                        val moveIndex = if (totalMoves > 1) {
                            ((x / graphWidth) * (totalMoves - 1)).toInt().coerceIn(0, totalMoves - 1)
                        } else {
                            0
                        }
                        onMoveSelected(moveIndex)
                    }
                }
            }
            .pointerInput(totalMoves, currentStage) {
                // Allow taps in Analyse and Manual stages (not Preview)
                if (totalMoves > 0 && currentStage != AnalysisStage.PREVIEW) {
                    detectTapGestures(
                        onTap = { offset ->
                            val x = offset.x.coerceIn(0f, graphWidth)
                            val moveIndex = if (totalMoves > 1) {
                                ((x / graphWidth) * (totalMoves - 1)).toInt().coerceIn(0, totalMoves - 1)
                            } else {
                                0
                            }
                            onMoveSelected(moveIndex)
                        }
                    )
                }
            }
    ) {
        if (totalMoves == 0) return@Canvas

        val width = size.width
        val height = size.height
        graphWidth = width
        val centerY = height / 2
        val maxScore = 5f // Cap the display at +/- 5 pawns

        // Draw center line (x-axis)
        drawLine(
            color = lineColor,
            start = Offset(0f, centerY),
            end = Offset(width, centerY),
            strokeWidth = 1f
        )

        // Calculate point spacing
        val pointSpacing = if (totalMoves > 1) width / (totalMoves - 1) else width / 2

        // Build list of points with their scores
        data class GraphPoint(val x: Float, val y: Float, val score: Float)
        val points = mutableListOf<GraphPoint>()

        for (moveIndex in 0 until totalMoves) {
            val score = previewScores[moveIndex]
            if (score != null) {
                val x = if (totalMoves > 1) moveIndex * pointSpacing else width / 2
                val clampedScore = score.score.coerceIn(-maxScore, maxScore)
                val y = centerY - (clampedScore / maxScore) * (height / 2 - 4)
                points.add(GraphPoint(x, y, score.score))
            }
        }

        // Draw filled areas and lines between consecutive points
        for (i in 0 until points.size - 1) {
            val p1 = points[i]
            val p2 = points[i + 1]

            // Check if the line crosses the x-axis (scores have different signs)
            val crossesAxis = (p1.score >= 0 && p2.score < 0) || (p1.score < 0 && p2.score >= 0)

            if (crossesAxis) {
                // Find the x-coordinate where the line crosses the x-axis
                // Linear interpolation: crossX = p1.x + (p2.x - p1.x) * t, where t = |p1.score| / (|p1.score| + |p2.score|)
                val t = kotlin.math.abs(p1.score) / (kotlin.math.abs(p1.score) + kotlin.math.abs(p2.score))
                val crossX = p1.x + (p2.x - p1.x) * t

                // Draw first segment (from p1 to crossing point)
                val color1 = if (p1.score >= 0) greenColor else redColor
                val path1 = androidx.compose.ui.graphics.Path().apply {
                    moveTo(p1.x, p1.y)
                    lineTo(crossX, centerY)
                    lineTo(p1.x, centerY)
                    close()
                }
                drawPath(path1, color1.copy(alpha = 0.4f))

                // Draw second segment (from crossing point to p2)
                val color2 = if (p2.score >= 0) greenColor else redColor
                val path2 = androidx.compose.ui.graphics.Path().apply {
                    moveTo(crossX, centerY)
                    lineTo(p2.x, p2.y)
                    lineTo(p2.x, centerY)
                    close()
                }
                drawPath(path2, color2.copy(alpha = 0.4f))

                // Draw solid line on top (two segments with different colors)
                drawLine(color1, Offset(p1.x, p1.y), Offset(crossX, centerY), strokeWidth = 2f)
                drawLine(color2, Offset(crossX, centerY), Offset(p2.x, p2.y), strokeWidth = 2f)
            } else {
                // No crossing - draw single colored area
                val color = if (p1.score >= 0) greenColor else redColor

                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(p1.x, p1.y)
                    lineTo(p2.x, p2.y)
                    lineTo(p2.x, centerY)
                    lineTo(p1.x, centerY)
                    close()
                }
                drawPath(path, color.copy(alpha = 0.4f))

                // Draw solid line on top
                drawLine(color, Offset(p1.x, p1.y), Offset(p2.x, p2.y), strokeWidth = 2f)
            }
        }

        // Build list of points for analyse stage scores
        val pointsAnalyse = mutableListOf<GraphPoint>()
        for (moveIndex in 0 until totalMoves) {
            val score = analyseScores[moveIndex]
            if (score != null) {
                val x = if (totalMoves > 1) moveIndex * pointSpacing else width / 2
                val clampedScore = score.score.coerceIn(-maxScore, maxScore)
                val y = centerY - (clampedScore / maxScore) * (height / 2 - 4)
                pointsAnalyse.add(GraphPoint(x, y, score.score))
            }
        }

        // Draw analyse stage as yellow line
        for (i in 0 until pointsAnalyse.size - 1) {
            val p1 = pointsAnalyse[i]
            val p2 = pointsAnalyse[i + 1]

            drawLine(
                color = analyseColor,
                start = Offset(p1.x, p1.y),
                end = Offset(p2.x, p2.y),
                strokeWidth = 2f
            )
        }

        // Draw current move indicator (only in manual stage)
        if (isManualStage && currentMoveIndex >= 0 && currentMoveIndex < totalMoves) {
            val x = if (totalMoves > 1) currentMoveIndex * pointSpacing else width / 2
            drawLine(
                color = currentMoveColor,
                start = Offset(x, 0f),
                end = Offset(x, height),
                strokeWidth = 5f
            )
        }
    }
}

@Composable
private fun AnalysisPanel(
    uiState: GameUiState,
    onExploreLine: (String, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val result = uiState.analysisResult
    val turn = uiState.currentBoard.getTurn()
    val isWhiteTurn = turn == PieceColor.WHITE

    // Only show if analysis is enabled and ready with results
    if (!uiState.analysisEnabled || !uiState.stockfishReady || result == null) {
        return
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Title
            Text(
                text = "Stockfish 17.1",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFAAAAAA)
            )
            result.lines.forEach { line ->
                PvLineRow(
                    line = line,
                    board = uiState.currentBoard,
                    isWhiteTurn = isWhiteTurn,
                    onMoveClick = { moveIndex ->
                        onExploreLine(line.pv, moveIndex)
                    }
                )
            }
        }
    }
}

@Composable
private fun PvLineRow(
    line: PvLine,
    board: ChessBoard,
    isWhiteTurn: Boolean,
    onMoveClick: (Int) -> Unit
) {
    // Score display: always from white's perspective (positive = white better, negative = black better)
    // Invert score to show from WHITE's perspective
    val adjustedScore = if (isWhiteTurn) -line.score else line.score
    val adjustedMateIn = if (isWhiteTurn) -line.mateIn else line.mateIn

    val displayScore = if (line.isMate) {
        if (adjustedMateIn > 0) "M$adjustedMateIn" else "M${kotlin.math.abs(adjustedMateIn)}"
    } else {
        if (adjustedScore >= 0) "+%.1f".format(adjustedScore)
        else "%.1f".format(adjustedScore)
    }

    val scoreColor = when {
        line.isMate -> Color(0xFFFF6B6B)
        else -> {
            when {
                adjustedScore > 0.3f -> Color.White
                adjustedScore < -0.3f -> Color(0xFF888888)
                else -> Color(0xFF6B9BFF)
            }
        }
    }

    // Format UCI moves with piece symbols and - or x for captures
    val formattedMoves = remember(line.pv, board) {
        formatUciMovesWithCaptures(line.pv, board, isWhiteTurn)
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Score box - consistent styling for all lines
        Box(
            modifier = Modifier
                .width(50.dp)
                .background(Color(0xFF151D30), RoundedCornerShape(4.dp))
                .padding(horizontal = 6.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = displayScore,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = scoreColor
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // PV moves with piece symbols - clickable
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState())
                .background(Color(0xFF0F1629), RoundedCornerShape(4.dp))
                .padding(horizontal = 4.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            formattedMoves.forEachIndexed { index, formattedMove ->
                Text(
                    text = formattedMove,
                    fontSize = 14.sp,
                    color = Color(0xFFCCCCCC),
                    modifier = Modifier
                        .clip(RoundedCornerShape(3.dp))
                        .clickable { onMoveClick(index) }
                        .background(Color(0xFF1A2744))
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                )
            }
        }
    }
}

// Format UCI moves with piece symbols and capture notation
private fun formatUciMovesWithCaptures(pv: String, startBoard: ChessBoard, isWhiteTurn: Boolean): List<String> {
    if (pv.isBlank()) return emptyList()

    val moves = pv.split(" ").filter { it.isNotBlank() }
    val result = mutableListOf<String>()
    val tempBoard = startBoard.copy()
    var currentIsWhite = isWhiteTurn

    for (uciMove in moves) {
        if (uciMove.length < 4) continue

        val fromStr = uciMove.substring(0, 2)
        val toStr = uciMove.substring(2, 4)
        val promotion = if (uciMove.length > 4) uciMove.substring(4).uppercase() else ""

        val fromSquare = Square.fromAlgebraic(fromStr)
        val toSquare = Square.fromAlgebraic(toStr)

        // Get the piece on the from square to determine the correct symbol
        val piece = fromSquare?.let { tempBoard.getPiece(it) }
        val symbol = if (piece != null) {
            val isWhitePiece = piece.color == com.chessreplay.chess.PieceColor.WHITE
            getPieceSymbol(piece.type, isWhitePiece)
        } else {
            // Fallback - inverted because Unicode symbols are visually inverted
            if (currentIsWhite) BLACK_PAWN else WHITE_PAWN
        }

        // Check for capture: either there's a piece on target square, or it's en passant
        val targetPiece = toSquare?.let { tempBoard.getPiece(it) }
        val isPawn = piece?.type == com.chessreplay.chess.PieceType.PAWN
        val isEnPassant = isPawn && fromSquare != null && toSquare != null &&
            fromSquare.file != toSquare.file && targetPiece == null
        val isCapture = targetPiece != null || isEnPassant

        val separator = if (isCapture) "x" else "-"
        val formatted = "$symbol $fromStr$separator$toStr$promotion"

        result.add(formatted)

        // Make the move on temp board for next iteration
        tempBoard.makeUciMove(uciMove)
        currentIsWhite = !currentIsWhite
    }

    return result
}

// Get the correct piece symbol based on piece type and color
// Note: Unicode chess symbols are inverted - "white" symbols appear filled, "black" appear hollow
private fun getPieceSymbol(pieceType: com.chessreplay.chess.PieceType, isWhite: Boolean): String {
    return when (pieceType) {
        com.chessreplay.chess.PieceType.KING -> if (isWhite) BLACK_KING else WHITE_KING
        com.chessreplay.chess.PieceType.QUEEN -> if (isWhite) BLACK_QUEEN else WHITE_QUEEN
        com.chessreplay.chess.PieceType.ROOK -> if (isWhite) BLACK_ROOK else WHITE_ROOK
        com.chessreplay.chess.PieceType.BISHOP -> if (isWhite) BLACK_BISHOP else WHITE_BISHOP
        com.chessreplay.chess.PieceType.KNIGHT -> if (isWhite) BLACK_KNIGHT else WHITE_KNIGHT
        com.chessreplay.chess.PieceType.PAWN -> if (isWhite) BLACK_PAWN else WHITE_PAWN
    }
}

// Color picker dialog with hue/saturation grid and brightness slider
@Composable
private fun ColorPickerDialog(
    currentColor: Long,
    title: String,
    onColorSelected: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    // Extract HSV from current color
    val initialColor = Color(currentColor.toInt())
    val hsv = FloatArray(3)
    android.graphics.Color.RGBToHSV(
        (initialColor.red * 255).toInt(),
        (initialColor.green * 255).toInt(),
        (initialColor.blue * 255).toInt(),
        hsv
    )

    var hue by remember { mutableStateOf(hsv[0]) }
    var saturation by remember { mutableStateOf(hsv[1]) }
    var brightness by remember { mutableStateOf(hsv[2]) }
    var alpha by remember { mutableStateOf(initialColor.alpha) }

    // Compute current color from HSV
    val currentHsvColor = remember(hue, saturation, brightness, alpha) {
        val rgb = android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, brightness))
        Color(
            red = android.graphics.Color.red(rgb) / 255f,
            green = android.graphics.Color.green(rgb) / 255f,
            blue = android.graphics.Color.blue(rgb) / 255f,
            alpha = alpha
        )
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                // Color preview
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(currentHsvColor)
                        .border(1.dp, Color.Gray, RoundedCornerShape(8.dp))
                )

                // Hue/Saturation picker (2D grid)
                Text("Color", style = MaterialTheme.typography.labelMedium)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, Color.Gray, RoundedCornerShape(8.dp))
                ) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectTapGestures { offset ->
                                    hue = (offset.x / size.width * 360f).coerceIn(0f, 360f)
                                    saturation = (1f - offset.y / size.height).coerceIn(0f, 1f)
                                }
                            }
                            .pointerInput(Unit) {
                                detectHorizontalDragGestures { change, _ ->
                                    val offset = change.position
                                    hue = (offset.x / size.width * 360f).coerceIn(0f, 360f)
                                    saturation = (1f - offset.y / size.height).coerceIn(0f, 1f)
                                }
                            }
                    ) {
                        // Draw hue/saturation gradient
                        val step = 4f
                        for (x in 0 until size.width.toInt() step step.toInt()) {
                            for (y in 0 until size.height.toInt() step step.toInt()) {
                                val h = x / size.width * 360f
                                val s = 1f - y / size.height
                                val rgb = android.graphics.Color.HSVToColor(floatArrayOf(h, s, brightness))
                                drawRect(
                                    color = Color(rgb),
                                    topLeft = Offset(x.toFloat(), y.toFloat()),
                                    size = androidx.compose.ui.geometry.Size(step, step)
                                )
                            }
                        }
                        // Draw crosshair at current position
                        val crossX = hue / 360f * size.width
                        val crossY = (1f - saturation) * size.height
                        drawCircle(Color.White, 8f, Offset(crossX, crossY))
                        drawCircle(Color.Black, 6f, Offset(crossX, crossY))
                    }
                }

                // Brightness slider
                Text("Brightness", style = MaterialTheme.typography.labelMedium)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(30.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .border(1.dp, Color.Gray, RoundedCornerShape(4.dp))
                ) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectTapGestures { offset ->
                                    brightness = (offset.x / size.width).coerceIn(0f, 1f)
                                }
                            }
                            .pointerInput(Unit) {
                                detectHorizontalDragGestures { change, _ ->
                                    brightness = (change.position.x / size.width).coerceIn(0f, 1f)
                                }
                            }
                    ) {
                        // Draw brightness gradient
                        for (x in 0 until size.width.toInt()) {
                            val b = x / size.width
                            val rgb = android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, b))
                            drawLine(
                                Color(rgb),
                                Offset(x.toFloat(), 0f),
                                Offset(x.toFloat(), size.height),
                                strokeWidth = 1f
                            )
                        }
                        // Draw indicator
                        val indicatorX = brightness * size.width
                        drawLine(Color.White, Offset(indicatorX, 0f), Offset(indicatorX, size.height), 3f)
                        drawLine(Color.Black, Offset(indicatorX, 0f), Offset(indicatorX, size.height), 1f)
                    }
                }

                // Alpha/Opacity slider
                Text("Opacity", style = MaterialTheme.typography.labelMedium)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(30.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .border(1.dp, Color.Gray, RoundedCornerShape(4.dp))
                ) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectTapGestures { offset ->
                                    alpha = (offset.x / size.width).coerceIn(0f, 1f)
                                }
                            }
                            .pointerInput(Unit) {
                                detectHorizontalDragGestures { change, _ ->
                                    alpha = (change.position.x / size.width).coerceIn(0f, 1f)
                                }
                            }
                    ) {
                        // Draw checkerboard background for transparency
                        val checkSize = 8f
                        for (x in 0 until (size.width / checkSize).toInt()) {
                            for (y in 0 until (size.height / checkSize).toInt()) {
                                val isLight = (x + y) % 2 == 0
                                drawRect(
                                    if (isLight) Color.White else Color.LightGray,
                                    Offset(x * checkSize, y * checkSize),
                                    androidx.compose.ui.geometry.Size(checkSize, checkSize)
                                )
                            }
                        }
                        // Draw alpha gradient
                        val baseRgb = android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, brightness))
                        val baseColor = Color(baseRgb)
                        for (x in 0 until size.width.toInt()) {
                            val a = x / size.width
                            drawLine(
                                baseColor.copy(alpha = a),
                                Offset(x.toFloat(), 0f),
                                Offset(x.toFloat(), size.height),
                                strokeWidth = 1f
                            )
                        }
                        // Draw indicator
                        val indicatorX = alpha * size.width
                        drawLine(Color.White, Offset(indicatorX, 0f), Offset(indicatorX, size.height), 3f)
                        drawLine(Color.Black, Offset(indicatorX, 0f), Offset(indicatorX, size.height), 1f)
                    }
                }

                // Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Button(onClick = {
                        // Convert to Long color value
                        val rgb = android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, brightness))
                        val r = android.graphics.Color.red(rgb)
                        val g = android.graphics.Color.green(rgb)
                        val b = android.graphics.Color.blue(rgb)
                        val a = (alpha * 255).toInt()
                        val colorLong = ((a.toLong() and 0xFF) shl 24) or
                                ((r.toLong() and 0xFF) shl 16) or
                                ((g.toLong() and 0xFF) shl 8) or
                                (b.toLong() and 0xFF)
                        onColorSelected(colorLong)
                        onDismiss()
                    }) {
                        Text("Select")
                    }
                }
            }
        }
    }
}

// Settings sub-screen enum
private enum class SettingsSubScreen {
    MAIN,
    ARROW_SETTINGS,
    STOCKFISH
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(
    stockfishSettings: StockfishSettings,
    onBack: () -> Unit,
    onSaveStockfish: (StockfishSettings) -> Unit
) {
    var currentSubScreen by remember { mutableStateOf(SettingsSubScreen.MAIN) }

    // Handle Android back button
    BackHandler {
        if (currentSubScreen == SettingsSubScreen.MAIN) {
            onBack()
        } else {
            currentSubScreen = SettingsSubScreen.MAIN
        }
    }

    when (currentSubScreen) {
        SettingsSubScreen.MAIN -> SettingsMainScreen(
            onBack = onBack,
            onNavigate = { currentSubScreen = it }
        )
        SettingsSubScreen.ARROW_SETTINGS -> ArrowSettingsScreen(
            stockfishSettings = stockfishSettings,
            onBack = { currentSubScreen = SettingsSubScreen.MAIN },
            onSave = onSaveStockfish
        )
        SettingsSubScreen.STOCKFISH -> StockfishSettingsScreen(
            stockfishSettings = stockfishSettings,
            onBack = { currentSubScreen = SettingsSubScreen.MAIN },
            onSave = onSaveStockfish
        )
    }
}

@Composable
private fun SettingsMainScreen(
    onBack: () -> Unit,
    onNavigate: (SettingsSubScreen) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Title
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // Back button at top
        Button(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Back")
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Arrow settings card
        SettingsNavigationCard(
            title = "Arrow settings",
            description = "Arrow display, colors, numbers",
            onClick = { onNavigate(SettingsSubScreen.ARROW_SETTINGS) }
        )

        // Stockfish settings card
        SettingsNavigationCard(
            title = "Stockfish",
            description = "Engine settings for all stages",
            onClick = { onNavigate(SettingsSubScreen.STOCKFISH) }
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Back button at bottom
        Button(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Back")
        }
    }
}

@Composable
private fun SettingsNavigationCard(
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFAAAAAA)
                )
            }
            Text(
                text = ">",
                style = MaterialTheme.typography.headlineMedium,
                color = Color(0xFF888888)
            )
        }
    }
}

@Composable
private fun SettingsBackButton(onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("Back to settings")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArrowSettingsScreen(
    stockfishSettings: StockfishSettings,
    onBack: () -> Unit,
    onSave: (StockfishSettings) -> Unit
) {
    var drawArrows by remember { mutableStateOf(stockfishSettings.manualStage.drawArrows) }
    var numArrows by remember { mutableStateOf(stockfishSettings.manualStage.numArrows) }
    var showArrowNumbers by remember { mutableStateOf(stockfishSettings.manualStage.showArrowNumbers) }
    var whiteArrowColor by remember { mutableStateOf(stockfishSettings.manualStage.whiteArrowColor) }
    var blackArrowColor by remember { mutableStateOf(stockfishSettings.manualStage.blackArrowColor) }
    var showWhiteColorPicker by remember { mutableStateOf(false) }
    var showBlackColorPicker by remember { mutableStateOf(false) }
    var drawArrowsExpanded by remember { mutableStateOf(false) }
    var numArrowsExpanded by remember { mutableStateOf(false) }
    var showArrowNumbersExpanded by remember { mutableStateOf(false) }

    val numArrowsOptions = listOf(1, 2, 3, 4, 5, 6, 7, 8)

    fun saveSettings(
        newDrawArrows: Boolean = drawArrows,
        newNumArrows: Int = numArrows,
        newShowArrowNumbers: Boolean = showArrowNumbers,
        newWhiteArrowColor: Long = whiteArrowColor,
        newBlackArrowColor: Long = blackArrowColor
    ) {
        onSave(stockfishSettings.copy(
            manualStage = stockfishSettings.manualStage.copy(
                drawArrows = newDrawArrows,
                numArrows = newNumArrows,
                showArrowNumbers = newShowArrowNumbers,
                whiteArrowColor = newWhiteArrowColor,
                blackArrowColor = newBlackArrowColor
            )
        ))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Arrow settings",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        SettingsBackButton(onClick = onBack)

        Spacer(modifier = Modifier.height(8.dp))

        // Draw arrows dropdown (always visible)
        ExposedDropdownMenuBox(
            expanded = drawArrowsExpanded,
            onExpandedChange = { drawArrowsExpanded = it }
        ) {
            OutlinedTextField(
                value = if (drawArrows) "Yes" else "No",
                onValueChange = {},
                readOnly = true,
                label = { Text("Draw arrows") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = drawArrowsExpanded) },
                modifier = Modifier.fillMaxWidth().menuAnchor()
            )
            ExposedDropdownMenu(expanded = drawArrowsExpanded, onDismissRequest = { drawArrowsExpanded = false }) {
                DropdownMenuItem(
                    text = { Text("Yes") },
                    onClick = {
                        drawArrows = true
                        drawArrowsExpanded = false
                        saveSettings(newDrawArrows = true)
                    }
                )
                DropdownMenuItem(
                    text = { Text("No") },
                    onClick = {
                        drawArrows = false
                        drawArrowsExpanded = false
                        saveSettings(newDrawArrows = false)
                    }
                )
            }
        }

        // All other settings are only visible when drawArrows is true
        if (drawArrows) {
            // Number of arrows dropdown
            ExposedDropdownMenuBox(
                expanded = numArrowsExpanded,
                onExpandedChange = { numArrowsExpanded = it }
            ) {
                OutlinedTextField(
                    value = numArrows.toString(),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Number of arrows") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = numArrowsExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor()
                )
                ExposedDropdownMenu(expanded = numArrowsExpanded, onDismissRequest = { numArrowsExpanded = false }) {
                    numArrowsOptions.forEach { num ->
                        DropdownMenuItem(
                            text = { Text(num.toString()) },
                            onClick = {
                                numArrows = num
                                numArrowsExpanded = false
                                saveSettings(newNumArrows = num)
                            }
                        )
                    }
                }
            }

            // Show move number in arrow dropdown
            ExposedDropdownMenuBox(
                expanded = showArrowNumbersExpanded,
                onExpandedChange = { showArrowNumbersExpanded = it }
            ) {
                OutlinedTextField(
                    value = if (showArrowNumbers) "Yes" else "No",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Show move number in arrow") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showArrowNumbersExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor()
                )
                ExposedDropdownMenu(expanded = showArrowNumbersExpanded, onDismissRequest = { showArrowNumbersExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text("Yes") },
                        onClick = {
                            showArrowNumbers = true
                            showArrowNumbersExpanded = false
                            saveSettings(newShowArrowNumbers = true)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("No") },
                        onClick = {
                            showArrowNumbers = false
                            showArrowNumbersExpanded = false
                            saveSettings(newShowArrowNumbers = false)
                        }
                    )
                }
            }

            // Color picker for white move arrows
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("White move color")
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(whiteArrowColor.toInt()))
                        .border(2.dp, Color.Gray, RoundedCornerShape(8.dp))
                        .clickable { showWhiteColorPicker = true }
                )
            }

            // Color picker for black move arrows
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Black move color")
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(blackArrowColor.toInt()))
                        .border(2.dp, Color.Gray, RoundedCornerShape(8.dp))
                        .clickable { showBlackColorPicker = true }
                )
            }
        }

        // Color picker dialogs (outside the if block so they can still be dismissed)
        if (showWhiteColorPicker) {
            ColorPickerDialog(
                currentColor = whiteArrowColor,
                title = "Arrow color for white moves",
                onColorSelected = { color ->
                    whiteArrowColor = color
                    saveSettings(newWhiteArrowColor = color)
                },
                onDismiss = { showWhiteColorPicker = false }
            )
        }

        if (showBlackColorPicker) {
            ColorPickerDialog(
                currentColor = blackArrowColor,
                title = "Arrow color for black moves",
                onColorSelected = { color ->
                    blackArrowColor = color
                    saveSettings(newBlackArrowColor = color)
                },
                onDismiss = { showBlackColorPicker = false }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        SettingsBackButton(onClick = onBack)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StockfishSettingsScreen(
    stockfishSettings: StockfishSettings,
    onBack: () -> Unit,
    onSave: (StockfishSettings) -> Unit
) {
    // Preview Stage state
    var previewSeconds by remember { mutableStateOf(stockfishSettings.previewStage.secondsForMove) }
    var previewThreads by remember { mutableStateOf(stockfishSettings.previewStage.threads) }
    var previewHash by remember { mutableStateOf(stockfishSettings.previewStage.hashMb) }
    var previewNnue by remember { mutableStateOf(stockfishSettings.previewStage.useNnue) }

    // Analyse Stage state
    var analyseSeconds by remember { mutableStateOf(stockfishSettings.analyseStage.secondsForMove) }
    var analyseThreads by remember { mutableStateOf(stockfishSettings.analyseStage.threads) }
    var analyseHash by remember { mutableStateOf(stockfishSettings.analyseStage.hashMb) }
    var analyseNnue by remember { mutableStateOf(stockfishSettings.analyseStage.useNnue) }

    // Manual Analyse Stage state
    var manualDepth by remember { mutableStateOf(stockfishSettings.manualStage.depth) }
    var manualThreads by remember { mutableStateOf(stockfishSettings.manualStage.threads) }
    var manualHash by remember { mutableStateOf(stockfishSettings.manualStage.hashMb) }
    var manualMultiPv by remember { mutableStateOf(stockfishSettings.manualStage.multiPv) }
    var manualNnue by remember { mutableStateOf(stockfishSettings.manualStage.useNnue) }

    // Dropdown expanded state
    var previewSecondsExpanded by remember { mutableStateOf(false) }
    var previewThreadsExpanded by remember { mutableStateOf(false) }
    var previewHashExpanded by remember { mutableStateOf(false) }
    var analyseSecondsExpanded by remember { mutableStateOf(false) }
    var analyseThreadsExpanded by remember { mutableStateOf(false) }
    var analyseHashExpanded by remember { mutableStateOf(false) }
    var manualDepthExpanded by remember { mutableStateOf(false) }
    var manualThreadsExpanded by remember { mutableStateOf(false) }
    var manualHashExpanded by remember { mutableStateOf(false) }
    var manualMultiPvExpanded by remember { mutableStateOf(false) }

    // Options for dropdowns
    val previewSecondsOptions = listOf(0.01f, 0.05f, 0.10f, 0.25f, 0.50f)
    val previewThreadsOptions = (1..2).toList()
    val previewHashOptions = listOf(8, 16, 32, 64)

    val analyseSecondsOptions = listOf(0.50f, 0.75f, 1.00f, 1.50f, 2.50f, 5.00f, 10.00f)
    val analyseThreadsOptions = (1..4).toList()
    val analyseHashOptions = listOf(16, 32, 64, 128)

    val manualDepthOptions = (16..32).toList()
    val manualThreadsOptions = (1..12).toList()
    val manualHashOptions = listOf(32, 64, 128, 256)
    val manualMultiPvOptions = (1..6).toList()

    fun saveAllSettings() {
        onSave(stockfishSettings.copy(
            previewStage = PreviewStageSettings(
                secondsForMove = previewSeconds,
                threads = previewThreads,
                hashMb = previewHash,
                useNnue = previewNnue
            ),
            analyseStage = AnalyseStageSettings(
                secondsForMove = analyseSeconds,
                threads = analyseThreads,
                hashMb = analyseHash,
                useNnue = analyseNnue
            ),
            manualStage = stockfishSettings.manualStage.copy(
                depth = manualDepth,
                threads = manualThreads,
                hashMb = manualHash,
                multiPv = manualMultiPv,
                useNnue = manualNnue
            )
        ))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Stockfish",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        SettingsBackButton(onClick = onBack)

        Spacer(modifier = Modifier.height(8.dp))

        // ===== PREVIEW STAGE CARD =====
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
                Text(
                    text = "Preview Stage",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )

                // Seconds for move
                ExposedDropdownMenuBox(
                    expanded = previewSecondsExpanded,
                    onExpandedChange = { previewSecondsExpanded = it }
                ) {
                    OutlinedTextField(
                        value = String.format("%.2f s", previewSeconds),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Seconds for move") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = previewSecondsExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(expanded = previewSecondsExpanded, onDismissRequest = { previewSecondsExpanded = false }) {
                        previewSecondsOptions.forEach { seconds ->
                            DropdownMenuItem(
                                text = { Text(String.format("%.2f s", seconds)) },
                                onClick = {
                                    previewSeconds = seconds
                                    previewSecondsExpanded = false
                                    saveAllSettings()
                                }
                            )
                        }
                    }
                }

                // Number of threads
                ExposedDropdownMenuBox(
                    expanded = previewThreadsExpanded,
                    onExpandedChange = { previewThreadsExpanded = it }
                ) {
                    OutlinedTextField(
                        value = previewThreads.toString(),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Number of threads") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = previewThreadsExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(expanded = previewThreadsExpanded, onDismissRequest = { previewThreadsExpanded = false }) {
                        previewThreadsOptions.forEach { threads ->
                            DropdownMenuItem(
                                text = { Text(threads.toString()) },
                                onClick = {
                                    previewThreads = threads
                                    previewThreadsExpanded = false
                                    saveAllSettings()
                                }
                            )
                        }
                    }
                }

                // Hash memory
                ExposedDropdownMenuBox(
                    expanded = previewHashExpanded,
                    onExpandedChange = { previewHashExpanded = it }
                ) {
                    OutlinedTextField(
                        value = "$previewHash MB",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Hash memory (MB)") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = previewHashExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(expanded = previewHashExpanded, onDismissRequest = { previewHashExpanded = false }) {
                        previewHashOptions.forEach { hash ->
                            DropdownMenuItem(
                                text = { Text("$hash MB") },
                                onClick = {
                                    previewHash = hash
                                    previewHashExpanded = false
                                    saveAllSettings()
                                }
                            )
                        }
                    }
                }

                // Use NNUE toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Use NNUE", color = Color.White)
                    Switch(
                        checked = previewNnue,
                        onCheckedChange = {
                            previewNnue = it
                            saveAllSettings()
                        }
                    )
                }
            }
        }

        // ===== ANALYSE STAGE CARD =====
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
                Text(
                    text = "Analyse Stage",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )

                // Seconds for move
                ExposedDropdownMenuBox(
                    expanded = analyseSecondsExpanded,
                    onExpandedChange = { analyseSecondsExpanded = it }
                ) {
                    OutlinedTextField(
                        value = String.format("%.2f s", analyseSeconds),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Seconds for move") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = analyseSecondsExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(expanded = analyseSecondsExpanded, onDismissRequest = { analyseSecondsExpanded = false }) {
                        analyseSecondsOptions.forEach { seconds ->
                            DropdownMenuItem(
                                text = { Text(String.format("%.2f s", seconds)) },
                                onClick = {
                                    analyseSeconds = seconds
                                    analyseSecondsExpanded = false
                                    saveAllSettings()
                                }
                            )
                        }
                    }
                }

                // Number of threads
                ExposedDropdownMenuBox(
                    expanded = analyseThreadsExpanded,
                    onExpandedChange = { analyseThreadsExpanded = it }
                ) {
                    OutlinedTextField(
                        value = analyseThreads.toString(),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Number of threads") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = analyseThreadsExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(expanded = analyseThreadsExpanded, onDismissRequest = { analyseThreadsExpanded = false }) {
                        analyseThreadsOptions.forEach { threads ->
                            DropdownMenuItem(
                                text = { Text(threads.toString()) },
                                onClick = {
                                    analyseThreads = threads
                                    analyseThreadsExpanded = false
                                    saveAllSettings()
                                }
                            )
                        }
                    }
                }

                // Hash memory
                ExposedDropdownMenuBox(
                    expanded = analyseHashExpanded,
                    onExpandedChange = { analyseHashExpanded = it }
                ) {
                    OutlinedTextField(
                        value = "$analyseHash MB",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Hash memory (MB)") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = analyseHashExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(expanded = analyseHashExpanded, onDismissRequest = { analyseHashExpanded = false }) {
                        analyseHashOptions.forEach { hash ->
                            DropdownMenuItem(
                                text = { Text("$hash MB") },
                                onClick = {
                                    analyseHash = hash
                                    analyseHashExpanded = false
                                    saveAllSettings()
                                }
                            )
                        }
                    }
                }

                // Use NNUE toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Use NNUE", color = Color.White)
                    Switch(
                        checked = analyseNnue,
                        onCheckedChange = {
                            analyseNnue = it
                            saveAllSettings()
                        }
                    )
                }
            }
        }

        // ===== MANUAL ANALYSE STAGE CARD =====
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
                Text(
                    text = "Manual Analyse Stage",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )

                // Depth
                ExposedDropdownMenuBox(
                    expanded = manualDepthExpanded,
                    onExpandedChange = { manualDepthExpanded = it }
                ) {
                    OutlinedTextField(
                        value = manualDepth.toString(),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Depth") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = manualDepthExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(expanded = manualDepthExpanded, onDismissRequest = { manualDepthExpanded = false }) {
                        manualDepthOptions.forEach { depth ->
                            DropdownMenuItem(
                                text = { Text(depth.toString()) },
                                onClick = {
                                    manualDepth = depth
                                    manualDepthExpanded = false
                                    saveAllSettings()
                                }
                            )
                        }
                    }
                }

                // Number of threads
                ExposedDropdownMenuBox(
                    expanded = manualThreadsExpanded,
                    onExpandedChange = { manualThreadsExpanded = it }
                ) {
                    OutlinedTextField(
                        value = manualThreads.toString(),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Number of threads") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = manualThreadsExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(expanded = manualThreadsExpanded, onDismissRequest = { manualThreadsExpanded = false }) {
                        manualThreadsOptions.forEach { threads ->
                            DropdownMenuItem(
                                text = { Text(threads.toString()) },
                                onClick = {
                                    manualThreads = threads
                                    manualThreadsExpanded = false
                                    saveAllSettings()
                                }
                            )
                        }
                    }
                }

                // Hash memory
                ExposedDropdownMenuBox(
                    expanded = manualHashExpanded,
                    onExpandedChange = { manualHashExpanded = it }
                ) {
                    OutlinedTextField(
                        value = "$manualHash MB",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Hash memory (MB)") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = manualHashExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(expanded = manualHashExpanded, onDismissRequest = { manualHashExpanded = false }) {
                        manualHashOptions.forEach { hash ->
                            DropdownMenuItem(
                                text = { Text("$hash MB") },
                                onClick = {
                                    manualHash = hash
                                    manualHashExpanded = false
                                    saveAllSettings()
                                }
                            )
                        }
                    }
                }

                // MultiPV lines
                ExposedDropdownMenuBox(
                    expanded = manualMultiPvExpanded,
                    onExpandedChange = { manualMultiPvExpanded = it }
                ) {
                    OutlinedTextField(
                        value = manualMultiPv.toString(),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("MultiPV lines") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = manualMultiPvExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(expanded = manualMultiPvExpanded, onDismissRequest = { manualMultiPvExpanded = false }) {
                        manualMultiPvOptions.forEach { multiPv ->
                            DropdownMenuItem(
                                text = { Text(multiPv.toString()) },
                                onClick = {
                                    manualMultiPv = multiPv
                                    manualMultiPvExpanded = false
                                    saveAllSettings()
                                }
                            )
                        }
                    }
                }

                // Use NNUE toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Use NNUE", color = Color.White)
                    Switch(
                        checked = manualNnue,
                        onCheckedChange = {
                            manualNnue = it
                            saveAllSettings()
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        SettingsBackButton(onClick = onBack)
    }
}
