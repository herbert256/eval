package com.chessreplay.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chessreplay.chess.ChessBoard
import com.chessreplay.chess.PieceColor
import com.chessreplay.chess.Square
import com.chessreplay.stockfish.PvLine

// Chess piece Unicode symbols for analysis display
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
 * Evaluation graph showing position scores over time.
 */
@Composable
fun EvaluationGraph(
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

/**
 * Panel displaying Stockfish analysis results with multiple PV lines.
 */
@Composable
fun AnalysisPanel(
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

/**
 * Row displaying a single principal variation line with score and clickable moves.
 */
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

/**
 * Format UCI moves with piece symbols and capture notation.
 */
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

/**
 * Get the correct piece symbol based on piece type and color.
 * Note: Unicode chess symbols are inverted - "white" symbols appear filled, "black" appear hollow
 */
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
