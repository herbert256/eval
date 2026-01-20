package com.chessreplay.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chessreplay.chess.PieceColor
import com.chessreplay.chess.Square
import com.chessreplay.data.ChessSource

// Chess piece Unicode symbols for game display
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

/**
 * Get just the piece symbol from a SAN move (for use with separate coordinates).
 */
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

/**
 * Main game content composable displaying the chess board, player bars, analysis, and moves list.
 */
@Composable
fun GameContent(
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
                val currentMoveDetails = uiState.moveDetails.getOrNull(moveIndex)
                val separator = if (currentMoveDetails?.isCapture == true) "x" else "-"

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
                        text = " $fromSquare$separator$toSquare",
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

    // Game info card - shows graph and result bar in analyse mode, just graph in manual/preview mode
    val GameInfoCard: @Composable () -> Unit = {
        GraphContent()
        if (uiState.currentStage == AnalysisStage.ANALYSE) {
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

    // Show the board (hide during Preview stage)
    if (uiState.currentStage != AnalysisStage.PREVIEW) {
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
        val drawArrowsSetting = uiState.stockfishSettings.manualStage.drawArrows
        val numArrows = uiState.stockfishSettings.manualStage.numArrows
        val moveArrows: List<MoveArrow> = if (uiState.currentStage == AnalysisStage.MANUAL && drawArrowsSetting && numArrows > 0) {
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
                interactionEnabled = uiState.currentStage == AnalysisStage.MANUAL,
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
    if (uiState.currentStage == AnalysisStage.MANUAL) {
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
    if (uiState.currentStage == AnalysisStage.MANUAL) {
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
    if (uiState.currentStage == AnalysisStage.MANUAL) {
        GameInfoCard()
    }

    // Moves list - only show during manual replay (not during auto-analysis)
    if (uiState.currentStage == AnalysisStage.MANUAL) {
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
            // Chess server
            val serverName = when (uiState.lastSource) {
                ChessSource.LICHESS -> "lichess.org"
                ChessSource.CHESS_COM -> "chess.com"
            }
            Text(
                text = "Server: $serverName",
                fontSize = 13.sp,
                color = Color.White
            )
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

/**
 * Navigation control button for move navigation.
 */
@Composable
fun ControlButton(
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

/**
 * Player bar showing player name, rating, and clock time.
 */
@Composable
fun PlayerBar(
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
