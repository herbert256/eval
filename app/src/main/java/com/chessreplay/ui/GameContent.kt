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

                // RIGHT: Score (from player's perspective)
                val isCurrentlyAnalyzing = uiState.currentStage != AnalysisStage.MANUAL && uiState.autoAnalysisIndex == moveIndex
                // Only use live result if it's for the current position (FEN matches)
                val currentFen = uiState.currentBoard.getFen()
                val isResultForCurrentPosition = uiState.analysisResultFen == currentFen
                // Get score in WHITE's perspective first
                val whiteScore: MoveScore? = if ((isManualMode || isCurrentlyAnalyzing) && liveResult != null && isResultForCurrentPosition) {
                    val bestLine = liveResult.bestLine
                    if (bestLine != null) {
                        // Convert score to WHITE's perspective (Stockfish gives score from side-to-move's view)
                        val adjustedScore = if (isWhiteTurn) bestLine.score else -bestLine.score
                        val adjustedMateIn = if (isWhiteTurn) bestLine.mateIn else -bestLine.mateIn
                        MoveScore(adjustedScore, bestLine.isMate, adjustedMateIn)
                    } else null
                } else {
                    storedScore
                }
                // Convert to PLAYER's perspective
                val displayScore: MoveScore? = if (whiteScore != null && uiState.userPlayedBlack) {
                    MoveScore(-whiteScore.score, whiteScore.isMate, -whiteScore.mateIn)
                } else {
                    whiteScore
                }

                if (displayScore != null) {
                    val scoreText = if (displayScore.isMate) {
                        if (displayScore.mateIn > 0) "+M${displayScore.mateIn}" else "-M${kotlin.math.abs(displayScore.mateIn)}"
                    } else {
                        if (displayScore.score >= 0) "+%.1f".format(displayScore.score) else "%.1f".format(displayScore.score)
                    }
                    val scoreColor = when {
                        displayScore.isMate && displayScore.mateIn > 0 -> Color(0xFF00E676)  // Green for player winning mate
                        displayScore.isMate && displayScore.mateIn < 0 -> Color(0xFFFF5252)  // Red for player losing mate
                        displayScore.score > 0.1f -> Color(0xFF00E676)  // Green for player better
                        displayScore.score < -0.1f -> Color(0xFFFF5252)  // Red for player worse
                        else -> Color(0xFF64B5F6)  // Bright blue for equal
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
        Column(modifier = Modifier.offset(y = (-8).dp)) {
            if (uiState.moveDetails.isNotEmpty()) {
                // Use key to force recomposition when scores change
                key(uiState.previewScores.size, uiState.analyseScores.size) {
                    EvaluationGraph(
                        previewScores = uiState.previewScores,
                        analyseScores = uiState.analyseScores,
                        totalMoves = uiState.moveDetails.size,
                        currentMoveIndex = uiState.currentMoveIndex,
                        currentStage = uiState.currentStage,
                        userPlayedBlack = uiState.userPlayedBlack,
                        onMoveSelected = { moveIndex ->
                            when (uiState.currentStage) {
                                AnalysisStage.PREVIEW -> { /* Not interruptible - ignore clicks */ }
                                AnalysisStage.ANALYSE -> viewModel.enterManualStageAtMove(moveIndex)
                                AnalysisStage.MANUAL -> viewModel.restartAnalysisAtMove(moveIndex)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                    )
                }

                // Score difference graph - shows change between consecutive moves
                // Only show during Analyse and Manual stages when we have scores
                if (uiState.currentStage != AnalysisStage.PREVIEW) {
                    Spacer(modifier = Modifier.height(8.dp))
                    key(uiState.previewScores.size, uiState.analyseScores.size) {
                        ScoreDifferenceGraph(
                            previewScores = uiState.previewScores,
                            analyseScores = uiState.analyseScores,
                            totalMoves = uiState.moveDetails.size,
                            currentMoveIndex = uiState.currentMoveIndex,
                            currentStage = uiState.currentStage,
                            userPlayedBlack = uiState.userPlayedBlack,
                            onMoveSelected = { moveIndex ->
                                when (uiState.currentStage) {
                                    AnalysisStage.PREVIEW -> { /* Not interruptible - ignore clicks */ }
                                    AnalysisStage.ANALYSE -> viewModel.enterManualStageAtMove(moveIndex)
                                    AnalysisStage.MANUAL -> viewModel.restartAnalysisAtMove(moveIndex)
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(80.dp)
                        )
                    }
                }
            }
        }
    }

    // Game info card - shows graph and result bar in analyse mode, just graph in manual/preview mode
    val GameInfoCard: @Composable () -> Unit = {
        GraphContent()
        if (uiState.currentStage == AnalysisStage.ANALYSE) {
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
        Spacer(modifier = Modifier.height(8.dp))
    }

    // Show the board (hide during Preview stage)
    if (uiState.currentStage != AnalysisStage.PREVIEW) {
        // Calculate game results for each player
        val whiteResult = when (game.winner) {
            "white" -> "won"
            "black" -> "lost"
            null -> if (game.status == "draw" || game.status == "stalemate") "draw" else null
            else -> null
        }
        val blackResult = when (game.winner) {
            "black" -> "won"
            "white" -> "lost"
            null -> if (game.status == "draw" || game.status == "stalemate") "draw" else null
            else -> null
        }

        // Player bar above board (opponent when not flipped, or player when flipped)
        val topIsBlack = !uiState.flippedBoard
        PlayerBar(
            isWhite = !topIsBlack,
            playerName = if (topIsBlack) blackName else whiteName,
            rating = if (topIsBlack) blackRating else whiteRating,
            clockTime = if (topIsBlack) blackClockTime else whiteClockTime,
            isToMove = if (topIsBlack) !isWhiteTurn else isWhiteTurn,
            gameResult = if (topIsBlack) blackResult else whiteResult
        )

        // Chess board - drag to make moves during manual replay (no interaction during other stages)
        // Get move arrows based on arrow mode (only in Manual stage, and only if result is for current position)
        val arrowMode = uiState.stockfishSettings.manualStage.arrowMode
        val numArrows = uiState.stockfishSettings.manualStage.numArrows
        val arrowResultMatchesPosition = uiState.analysisResultFen == uiState.currentBoard.getFen()
        val isWhiteTurnNow = uiState.currentBoard.getTurn() == PieceColor.WHITE

        val moveArrows: List<MoveArrow> = if (uiState.currentStage == AnalysisStage.MANUAL && arrowMode != ArrowMode.NONE && arrowResultMatchesPosition) {
            when (arrowMode) {
                ArrowMode.NONE -> emptyList()
                ArrowMode.MAIN_LINE -> {
                    // Draw arrows from PV line (existing behavior)
                    val pvLine = uiState.analysisResult?.pv ?: ""
                    val pvMoves = pvLine.split(" ").filter { it.length >= 4 }.take(numArrows)

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
                }
                ArrowMode.MULTI_LINES -> {
                    // Draw one arrow per Stockfish line with score displayed
                    val analysisLines = uiState.analysisResult?.lines ?: emptyList()
                    val multiLinesColor = Color(uiState.stockfishSettings.manualStage.multiLinesArrowColor.toInt())

                    analysisLines.mapIndexedNotNull { index, line ->
                        val firstMove = line.pv.split(" ").firstOrNull { it.length >= 4 }
                        if (firstMove != null) {
                            val fromFile = firstMove[0] - 'a'
                            val fromRank = firstMove[1] - '1'
                            val toFile = firstMove[2] - 'a'
                            val toRank = firstMove[3] - '1'
                            if (fromFile in 0..7 && fromRank in 0..7 && toFile in 0..7 && toRank in 0..7) {
                                // Format score for display
                                val scoreText = if (line.isMate) {
                                    if (line.mateIn > 0) "M${line.mateIn}" else "-M${kotlin.math.abs(line.mateIn)}"
                                } else {
                                    val score = if (isWhiteTurnNow) line.score else -line.score
                                    if (score >= 0) "+%.1f".format(score) else "%.1f".format(score)
                                }
                                MoveArrow(
                                    from = Square(fromFile, fromRank),
                                    to = Square(toFile, toRank),
                                    isWhiteMove = isWhiteTurnNow,
                                    index = index,
                                    scoreText = scoreText,
                                    overrideColor = multiLinesColor
                                )
                            } else null
                        } else null
                    }
                }
            }
        } else emptyList()

        ChessBoardView(
            board = uiState.currentBoard,
            flipped = uiState.flippedBoard,
            interactionEnabled = uiState.currentStage == AnalysisStage.MANUAL,
            onMove = { from, to -> viewModel.makeManualMove(from, to) },
            moveArrows = moveArrows,
            showArrowNumbers = uiState.stockfishSettings.manualStage.showArrowNumbers,
            whiteArrowColor = Color(uiState.stockfishSettings.manualStage.whiteArrowColor.toInt()),
            blackArrowColor = Color(uiState.stockfishSettings.manualStage.blackArrowColor.toInt()),
            showCoordinates = uiState.boardLayoutSettings.showCoordinates,
            showLastMove = uiState.boardLayoutSettings.showLastMove,
            whiteSquareColor = Color(uiState.boardLayoutSettings.whiteSquareColor.toInt()),
            blackSquareColor = Color(uiState.boardLayoutSettings.blackSquareColor.toInt()),
            whitePieceColor = Color(uiState.boardLayoutSettings.whitePieceColor.toInt()),
            blackPieceColor = Color(uiState.boardLayoutSettings.blackPieceColor.toInt()),
            modifier = Modifier.fillMaxWidth()
        )

        // Player bar below board (player when not flipped, or opponent when flipped)
        PlayerBar(
            isWhite = topIsBlack,
            playerName = if (topIsBlack) whiteName else blackName,
            rating = if (topIsBlack) whiteRating else blackRating,
            clockTime = if (topIsBlack) whiteClockTime else blackClockTime,
            isToMove = if (topIsBlack) isWhiteTurn else !isWhiteTurn,
            gameResult = if (topIsBlack) whiteResult else blackResult
        )
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

            // Left part: Navigation buttons with fixed positions (slots maintain size even when hidden)
            Row(
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val buttonWidth = 52.dp
                val spacerWidth = 6.dp

                // Button 1: Go to start (hidden when exploring)
                Box(modifier = Modifier.width(buttonWidth)) {
                    if (!uiState.isExploringLine) {
                        ControlButton("⏮", enabled = !isAtStart) { viewModel.goToStart() }
                    }
                }
                Spacer(modifier = Modifier.width(spacerWidth))

                // Button 2: Previous move (hidden when exploring and at start)
                Box(modifier = Modifier.width(buttonWidth)) {
                    val showPrevButton = !uiState.isExploringLine || !isAtStart
                    if (showPrevButton) {
                        ControlButton("◀", enabled = !isAtStart) { viewModel.prevMove() }
                    }
                }
                Spacer(modifier = Modifier.width(spacerWidth))

                // Button 3: Next move (hidden when exploring and at end)
                Box(modifier = Modifier.width(buttonWidth)) {
                    val showNextButton = !uiState.isExploringLine || !isAtEnd
                    if (showNextButton) {
                        ControlButton("▶", enabled = !isAtEnd) { viewModel.nextMove() }
                    }
                }
                Spacer(modifier = Modifier.width(spacerWidth))

                // Button 4: Go to end (hidden when exploring)
                Box(modifier = Modifier.width(buttonWidth)) {
                    if (!uiState.isExploringLine) {
                        ControlButton("⏭", enabled = !isAtEnd) { viewModel.goToEnd() }
                    }
                }
            }

            // Right part: Back to game button (when exploring) or flip board icon
            Row(
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (uiState.isExploringLine) {
                    // Back to game button - same style as navigation buttons
                    Button(
                        onClick = { viewModel.backToOriginalGame() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Text("Back to game", fontSize = 18.sp)
                    }
                } else {
                    // Flip board button - same size as nav buttons but bigger icon
                    Button(
                        onClick = { viewModel.flipBoard() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Text(text = "↻", fontSize = 26.sp)
                    }
                }
            }
        }
    }

    // Stockfish analysis panel - hide during auto-analysis
    if (uiState.currentStage == AnalysisStage.MANUAL) {
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
        Spacer(modifier = Modifier.height(8.dp))
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
                    userPlayedBlack = uiState.userPlayedBlack,
                    onMoveClick = { viewModel.goToMove(it) }
                )
            }
        }
    }

    // Game Information Card - table layout
    Spacer(modifier = Modifier.height(12.dp))
    Card(
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF2A4A6A)  // Lighter blue background
        ),
        modifier = Modifier.fillMaxWidth()
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

            // Table rows with aligned labels and values
            val labelWidth = 90.dp

            // White player
            Row(modifier = Modifier.fillMaxWidth()) {
                Text("White:", fontSize = 13.sp, color = Color(0xFFAAAAAA), modifier = Modifier.width(labelWidth))
                Text("$whiteName${whiteRating?.let { " ($it)" } ?: ""}", fontSize = 13.sp, color = Color.White)
            }
            // Black player
            Row(modifier = Modifier.fillMaxWidth()) {
                Text("Black:", fontSize = 13.sp, color = Color(0xFFAAAAAA), modifier = Modifier.width(labelWidth))
                Text("$blackName${blackRating?.let { " ($it)" } ?: ""}", fontSize = 13.sp, color = Color.White)
            }
            // Format
            val formatText = buildString {
                append(game.speed.replaceFirstChar { it.uppercase() })
                game.clock?.let { clock ->
                    val minutes = clock.initial / 60
                    val increment = clock.increment
                    append(" $minutes+$increment")
                }
                if (game.rated) append(" • Rated") else append(" • Casual")
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                Text("Format:", fontSize = 13.sp, color = Color(0xFFAAAAAA), modifier = Modifier.width(labelWidth))
                Text(formatText, fontSize = 13.sp, color = Color.White)
            }
            // Opening
            uiState.openingName?.let { opening ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text("Opening:", fontSize = 13.sp, color = Color(0xFFAAAAAA), modifier = Modifier.width(labelWidth))
                    Text(opening, fontSize = 13.sp, color = Color.White)
                }
            }
            // Date
            game.createdAt?.let { timestamp ->
                val date = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
                    .format(java.util.Date(timestamp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text("Date:", fontSize = 13.sp, color = Color(0xFFAAAAAA), modifier = Modifier.width(labelWidth))
                    Text(date, fontSize = 13.sp, color = Color.White)
                }
            }
            // Result
            val resultText = when (game.winner) {
                "white" -> "1-0"
                "black" -> "0-1"
                else -> if (game.status == "draw" || game.status == "stalemate") "½-½" else game.status
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                Text("Result:", fontSize = 13.sp, color = Color(0xFFAAAAAA), modifier = Modifier.width(labelWidth))
                Text(resultText, fontSize = 13.sp, color = Color.White)
            }
            // Termination - extract from PGN headers
            game.pgn?.let { pgn ->
                val terminationMatch = Regex("\\[Termination \"([^\"]+)\"\\]").find(pgn)
                terminationMatch?.groupValues?.get(1)?.let { termination ->
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text("Termination:", fontSize = 13.sp, color = Color(0xFFAAAAAA), modifier = Modifier.width(labelWidth))
                        Text(termination, fontSize = 13.sp, color = Color.White)
                    }
                }
            }
        }
    }

    // PGN Card - always shown at bottom
    Spacer(modifier = Modifier.height(12.dp))
    Card(
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF3A3A3A)  // Dark gray background
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "PGN",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            game.pgn?.let { pgn ->
                // Format PGN with each move on a new line
                val formattedPgn = pgn.replace(Regex("(\\d+\\.)")) { match ->
                    "\n${match.value}"
                }.trimStart()
                Text(
                    text = formattedPgn,
                    fontSize = 11.sp,
                    color = Color(0xFFCCCCCC),
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    lineHeight = 14.sp
                )
            } ?: Text(
                text = "No PGN data available",
                fontSize = 12.sp,
                color = Color(0xFF888888),
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
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
 * Player bar showing player name, rating, clock time, and game result.
 * @param gameResult "won", "lost", "draw", or null if game not finished
 */
@Composable
fun PlayerBar(
    isWhite: Boolean,
    playerName: String,
    rating: Int?,
    clockTime: String?,
    isToMove: Boolean,
    gameResult: String? = null
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
        // Left side: game result + player name
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Game result indicator
            if (gameResult != null) {
                val (resultText, resultColor) = when (gameResult) {
                    "won" -> "1" to Color(0xFF00C853)  // Green
                    "lost" -> "0" to Color(0xFFFF1744)  // Red
                    "draw" -> "½" to Color(0xFF64B5F6)  // Blue
                    else -> "" to Color.Transparent
                }
                if (resultText.isNotEmpty()) {
                    Text(
                        text = resultText,
                        color = resultColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            }

            Text(
                text = playerText,
                color = textColor,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp
            )
        }

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
