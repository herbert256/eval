package com.eval.ui

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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eval.chess.ChessBoard
import com.eval.chess.PieceColor
import com.eval.chess.Square
import com.eval.stockfish.AnalysisResult
import com.eval.stockfish.PvLine

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
 * Horizontal centre of a move's slot. All move graphs use it, so their current-move lines stay
 * vertically aligned with each other and with the bars of the score difference graph.
 */
internal fun moveSlotCenterX(moveIndex: Int, totalMoves: Int, width: Float): Float =
    (moveIndex + 0.5f) * width / totalMoves

/** The move whose slot contains [x]; the inverse of [moveSlotCenterX]. */
internal fun moveIndexAtX(x: Float, totalMoves: Int, width: Float): Int =
    if (width <= 0f) 0 else (x / width * totalMoves).toInt().coerceIn(0, totalMoves - 1)

/**
 * Background and move selection shared by the move graphs: tap a move in the Analyse and Manual
 * stages, drag across the moves in the Manual stage.
 */
private fun Modifier.moveGraph(
    graphSettings: GraphSettings,
    totalMoves: Int,
    currentStage: AnalysisStage,
    onMoveSelected: (Int) -> Unit
): Modifier = this
    .background(Color(graphSettings.backgroundColor.toInt()), RoundedCornerShape(8.dp))
    .padding(8.dp)
    .pointerInput(totalMoves, currentStage) {
        if (totalMoves > 0 && currentStage == AnalysisStage.MANUAL) {
            // Only act when the selected move changes; every pointer event would restart the engine.
            var lastIndex = -1
            detectHorizontalDragGestures(onDragStart = { lastIndex = -1 }) { change, _ ->
                change.consume()
                val moveIndex = moveIndexAtX(change.position.x, totalMoves, size.width.toFloat())
                if (moveIndex != lastIndex) {
                    lastIndex = moveIndex
                    onMoveSelected(moveIndex)
                }
            }
        }
    }
    .pointerInput(totalMoves, currentStage) {
        // Taps select a move in the Analyse and Manual stages (Preview is not interruptible)
        if (totalMoves > 0 && currentStage != AnalysisStage.PREVIEW) {
            detectTapGestures(
                onTap = { offset -> onMoveSelected(moveIndexAtX(offset.x, totalMoves, size.width.toFloat())) }
            )
        }
    }

/** The x-axis of the score graphs. */
private fun DrawScope.drawScoreAxis() {
    val centerY = size.height / 2
    drawLine(AppColors.DimGray, Offset(0f, centerY), Offset(size.width, centerY), strokeWidth = 1f)
}

/** The current move's vertical line, shown in the Manual stage only. */
private fun DrawScope.drawCurrentMoveLine(
    currentMoveIndex: Int,
    totalMoves: Int,
    currentStage: AnalysisStage,
    color: Color,
    strokeWidth: Float
) {
    if (currentStage != AnalysisStage.MANUAL || currentMoveIndex !in 0 until totalMoves) return
    val x = moveSlotCenterX(currentMoveIndex, totalMoves, size.width)
    drawLine(color, Offset(x, 0f), Offset(x, size.height), strokeWidth = strokeWidth)
}

/** A move's point on the score line; [score] is unclamped, from White's perspective. */
private class ScorePoint(val x: Float, val y: Float, val score: Float)

private fun DrawScope.scorePoints(scores: Map<Int, MoveScore>, totalMoves: Int, maxScore: Float): List<ScorePoint> {
    val centerY = size.height / 2
    return (0 until totalMoves).mapNotNull { moveIndex ->
        scores[moveIndex]?.let { score ->
            // Raw Stockfish score; a mate counts as the edge of the range
            val rawScore = score.graphValue(maxScore)
            val y = centerY - (rawScore.coerceIn(-maxScore, maxScore) / maxScore) * (centerY - 4)
            ScorePoint(moveSlotCenterX(moveIndex, totalMoves, size.width), y, rawScore)
        }
    }
}

/**
 * Fills between the score line and the x-axis ([positive] above, [negative] below) and draws the
 * line on top. [path] is a scratch Path reused for every segment, so a long game doesn't allocate
 * hundreds of Paths per frame.
 */
private fun DrawScope.drawScoreArea(points: List<ScorePoint>, positive: Color, negative: Color, path: Path) {
    val centerY = size.height / 2
    // Slight overlap (1px) with the neighbouring segments prevents anti-aliasing gaps
    val overlap = 1f

    for (i in 0 until points.size - 1) {
        val p1 = points[i]
        val p2 = points[i + 1]
        val leftX = if (i == 0) p1.x else p1.x - overlap
        val rightX = if (i == points.size - 2) p2.x else p2.x + overlap
        val color1 = if (p1.score >= 0) positive else negative
        val color2 = if (p2.score >= 0) positive else negative

        if ((p1.score >= 0) != (p2.score >= 0)) {
            // The line crosses the x-axis: split the segment where it does
            val t = kotlin.math.abs(p1.score) / (kotlin.math.abs(p1.score) + kotlin.math.abs(p2.score))
            val crossX = p1.x + (p2.x - p1.x) * t

            path.reset()
            path.moveTo(leftX, p1.y)
            path.lineTo(crossX, centerY)
            path.lineTo(leftX, centerY)
            path.close()
            drawPath(path, color1)

            path.reset()
            path.moveTo(crossX, centerY)
            path.lineTo(rightX, p2.y)
            path.lineTo(rightX, centerY)
            path.close()
            drawPath(path, color2)

            drawLine(color1, Offset(p1.x, p1.y), Offset(crossX, centerY), strokeWidth = 2f)
            drawLine(color2, Offset(crossX, centerY), Offset(p2.x, p2.y), strokeWidth = 2f)
        } else {
            path.reset()
            path.moveTo(leftX, p1.y)
            path.lineTo(rightX, p2.y)
            path.lineTo(rightX, centerY)
            path.lineTo(leftX, centerY)
            path.close()
            drawPath(path, color1)

            drawLine(color1, Offset(p1.x, p1.y), Offset(p2.x, p2.y), strokeWidth = 2f)
        }
    }
}

/**
 * The score line graph's areas: the preview scores and, in the Manual stage, the analyse scores on
 * top. In the other stages the analyse scores are a progress line instead ([drawAnalyseProgressLine]).
 */
private fun DrawScope.drawScoreLineAreas(
    previewScores: Map<Int, MoveScore>,
    analyseScores: Map<Int, MoveScore>,
    totalMoves: Int,
    currentStage: AnalysisStage,
    maxScore: Float,
    positive: Color,
    negative: Color,
    path: Path
) {
    drawScoreArea(scorePoints(previewScores, totalMoves, maxScore), positive, negative, path)
    if (currentStage == AnalysisStage.MANUAL) {
        drawScoreArea(scorePoints(analyseScores, totalMoves, maxScore), positive, negative, path)
    }
}

/** Outside the Manual stage, the analyse scores so far as a line over the preview scores. */
private fun DrawScope.drawAnalyseProgressLine(
    analyseScores: Map<Int, MoveScore>,
    totalMoves: Int,
    currentStage: AnalysisStage,
    maxScore: Float,
    color: Color
) {
    if (currentStage == AnalysisStage.MANUAL) return
    val points = scorePoints(analyseScores, totalMoves, maxScore)
    for (i in 0 until points.size - 1) {
        drawLine(color, Offset(points[i].x, points[i].y), Offset(points[i + 1].x, points[i + 1].y), strokeWidth = 7f)
    }
}

/**
 * Change of a move's score against the previous move of the SAME COLOR (2 plies back, not 1), from
 * White's perspective, limited to [maxDiff]; null while either score is missing. This shows how much
 * the position changed after the opponent's move and the reply.
 */
private fun scoreDifference(
    moveIndex: Int,
    previewScores: Map<Int, MoveScore>,
    analyseScores: Map<Int, MoveScore>,
    currentStage: AnalysisStage,
    maxDiff: Float
): Float? {
    val currentScore: MoveScore?
    val prevSameColorScore: MoveScore?

    // Previous move of same color is 2 plies back
    val prevSameColorIndex = moveIndex - 2

    if (currentStage == AnalysisStage.ANALYSE) {
        // During Analyse stage: use analyse scores if BOTH are available,
        // otherwise fall back to preview scores
        val hasAnalyseCurrent = analyseScores.containsKey(moveIndex)
        val hasAnalysePrev = prevSameColorIndex < 0 || analyseScores.containsKey(prevSameColorIndex)

        if (hasAnalyseCurrent && hasAnalysePrev) {
            // Both analyse scores available - use them
            currentScore = analyseScores[moveIndex]
            prevSameColorScore = if (prevSameColorIndex >= 0) analyseScores[prevSameColorIndex] else null
        } else {
            // Fall back to preview scores
            currentScore = previewScores[moveIndex]
            prevSameColorScore = if (prevSameColorIndex >= 0) previewScores[prevSameColorIndex] else null
        }
    } else {
        // Preview/Manual stage: prefer analyse scores, fall back to preview
        currentScore = analyseScores[moveIndex] ?: previewScores[moveIndex]
        prevSameColorScore = if (prevSameColorIndex >= 0) {
            analyseScores[prevSameColorIndex] ?: previewScores[prevSameColorIndex]
        } else null
    }

    if (currentScore == null || prevSameColorScore == null) return null

    // Calculate difference based on mate handling rules
    val prevIsMate = prevSameColorScore.isMate
    val currIsMate = currentScore.isMate

    // M value is the absolute value of mateIn (ignoring sign)
    val prevMValue = kotlin.math.abs(prevSameColorScore.mateIn)
    val currMValue = kotlin.math.abs(currentScore.mateIn)

    // Check if winning (+M*) or losing (-M*) mate
    val prevIsPositiveMate = prevSameColorScore.isPositiveMate
    val prevIsNegativeMate = prevIsMate && !prevIsPositiveMate
    val currIsPositiveMate = currentScore.isPositiveMate
    val currIsNegativeMate = currIsMate && !currIsPositiveMate

    val rawDiff: Float = when {
        // Both +M* (winning mate for both)
        prevIsPositiveMate && currIsPositiveMate -> when {
            currMValue == prevMValue -> 0f
            currMValue == prevMValue - 1 -> 0f
            currMValue > prevMValue -> -(1 + (currMValue - prevMValue)).toFloat().coerceAtMost(maxDiff)
            currMValue < prevMValue -> ((prevMValue - currMValue) + 1).toFloat().coerceAtMost(3f)
            else -> 0f
        }

        // Both -M* (losing mate for both)
        prevIsNegativeMate && currIsNegativeMate -> when {
            currMValue == prevMValue -> 0f
            currMValue == prevMValue - 1 -> 0f
            currMValue > prevMValue -> (1 + (currMValue - prevMValue)).toFloat().coerceAtMost(maxDiff)
            currMValue < prevMValue -> -((prevMValue - currMValue) + 1).toFloat().coerceAtLeast(-maxDiff)
            else -> 0f
        }

        // +M* to -M* (lost winning mate, now losing)
        prevIsPositiveMate && currIsNegativeMate -> -maxDiff

        // -M* to +M* (escaped losing mate, now winning)
        prevIsNegativeMate && currIsPositiveMate -> maxDiff

        // Previous normal, current +M*
        !prevIsMate && currIsPositiveMate -> maxDiff

        // Previous normal, current -M*
        !prevIsMate && currIsNegativeMate -> -maxDiff

        // Previous +M*, current normal
        prevIsPositiveMate && !currIsMate -> -maxDiff

        // Previous -M*, current normal
        prevIsNegativeMate && !currIsMate -> maxDiff

        // Both normal scores
        !prevIsMate && !currIsMate -> when {
            currentScore.score == prevSameColorScore.score -> 0f
            currentScore.score > prevSameColorScore.score ->
                (currentScore.score - prevSameColorScore.score).coerceAtMost(maxDiff)
            else ->
                (currentScore.score - prevSameColorScore.score).coerceAtLeast(-maxDiff)
        }

        else -> 0f
    }
    return rawDiff.coerceIn(-maxDiff, maxDiff)
}

/** One bar per move with its [scoreDifference]: up in [positive], down in [negative]. */
private fun DrawScope.drawScoreBars(
    previewScores: Map<Int, MoveScore>,
    analyseScores: Map<Int, MoveScore>,
    totalMoves: Int,
    currentStage: AnalysisStage,
    maxDiff: Float,
    positive: Color,
    negative: Color
) {
    val centerY = size.height / 2
    val barWidth = (size.width / totalMoves) * 0.8f
    for (moveIndex in 0 until totalMoves) {
        val diff = scoreDifference(moveIndex, previewScores, analyseScores, currentStage, maxDiff) ?: continue
        val barHeight = kotlin.math.abs(diff / maxDiff) * (centerY - 4)
        val barX = moveSlotCenterX(moveIndex, totalMoves, size.width) - barWidth / 2
        drawRect(
            color = if (diff >= 0) positive else negative,
            // Up from the axis for a gain, down for a loss
            topLeft = Offset(barX, if (diff >= 0) centerY - barHeight else centerY),
            size = Size(barWidth, barHeight)
        )
    }
}

/**
 * Evaluation graph showing position scores over time.
 */
@Composable
fun EvaluationGraph(
    previewScores: Map<Int, MoveScore>,
    analyseScores: Map<Int, MoveScore>,
    moveQualities: Map<Int, MoveQuality>,
    totalMoves: Int,
    currentMoveIndex: Int,
    currentStage: AnalysisStage,
    userPlayedBlack: Boolean,
    graphSettings: GraphSettings,
    onMoveSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    // Always show scores from WHITE's perspective (positive = good for white)
    val greenColor = Color(graphSettings.plusScoreColor.toInt())
    val redColor = Color(graphSettings.negativeScoreColor.toInt())
    val scratchPath = remember { Path() }

    Canvas(modifier = modifier.moveGraph(graphSettings, totalMoves, currentStage, onMoveSelected)) {
        if (totalMoves == 0) return@Canvas
        val maxScore = graphSettings.lineGraphRange.toFloat() // Range from settings

        drawScoreAxis()
        drawScoreLineAreas(previewScores, analyseScores, totalMoves, currentStage, maxScore, greenColor, redColor, scratchPath)
        drawAnalyseProgressLine(analyseScores, totalMoves, currentStage, maxScore, Color(graphSettings.analyseLineColor.toInt()))
        drawCurrentMoveLine(currentMoveIndex, totalMoves, currentStage, Color(graphSettings.verticalLineColor.toInt()), 5f)
    }
}

/**
 * Time usage graph showing remaining clock time for both players.
 */
@Composable
fun TimeUsageGraph(
    moveDetails: List<MoveDetails>,
    currentMoveIndex: Int,
    currentStage: AnalysisStage,
    graphSettings: GraphSettings,
    onMoveSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    // 1 when the game starts with Black to move, so index 0 is Black's move.
    firstPly: Int = 0
) {
    val whiteTimeColor = Color(0xFFFFFFFF)  // White for white's time
    val blackTimeColor = AppColors.MediumGray  // Gray for black's time
    val lineColor = Color(0xFF444444)
    val currentMoveColor = Color(graphSettings.verticalLineColor.toInt())

    // Parse clock times to seconds
    val whiteTimes = mutableListOf<Pair<Int, Int>>()  // (moveIndex, seconds)
    val blackTimes = mutableListOf<Pair<Int, Int>>()

    moveDetails.forEachIndexed { index, detail ->
        val seconds = parseClockTimeToSeconds(detail.clockTime)
        if (seconds != null) {
            if ((index + firstPly) % 2 == 0) {
                whiteTimes.add(index to seconds)
            } else {
                blackTimes.add(index to seconds)
            }
        }
    }

    // If no clock data, don't show the graph
    if (whiteTimes.isEmpty() && blackTimes.isEmpty()) {
        return
    }

    val maxTime = maxOf(
        whiteTimes.maxOfOrNull { it.second } ?: 0,
        blackTimes.maxOfOrNull { it.second } ?: 0
    ).toFloat().coerceAtLeast(60f)

    Canvas(modifier = modifier.moveGraph(graphSettings, moveDetails.size, currentStage, onMoveSelected)) {
        val width = size.width
        val height = size.height
        val totalMoves = moveDetails.size
        if (totalMoves == 0) return@Canvas

        // Draw horizontal grid lines
        for (i in 1..3) {
            val y = height * i / 4
            drawLine(lineColor, Offset(0f, y), Offset(width, y), strokeWidth = 1f)
        }

        // Draw white's time line
        if (whiteTimes.size > 1) {
            for (i in 0 until whiteTimes.size - 1) {
                val (idx1, t1) = whiteTimes[i]
                val (idx2, t2) = whiteTimes[i + 1]
                val x1 = moveSlotCenterX(idx1, totalMoves, width)
                val x2 = moveSlotCenterX(idx2, totalMoves, width)
                val y1 = height - (t1 / maxTime) * height
                val y2 = height - (t2 / maxTime) * height
                drawLine(whiteTimeColor, Offset(x1, y1), Offset(x2, y2), strokeWidth = 2f)
            }
        }

        // Draw black's time line
        if (blackTimes.size > 1) {
            for (i in 0 until blackTimes.size - 1) {
                val (idx1, t1) = blackTimes[i]
                val (idx2, t2) = blackTimes[i + 1]
                val x1 = moveSlotCenterX(idx1, totalMoves, width)
                val x2 = moveSlotCenterX(idx2, totalMoves, width)
                val y1 = height - (t1 / maxTime) * height
                val y2 = height - (t2 / maxTime) * height
                drawLine(blackTimeColor, Offset(x1, y1), Offset(x2, y2), strokeWidth = 2f)
            }
        }

        drawCurrentMoveLine(currentMoveIndex, totalMoves, currentStage, currentMoveColor, 3f)
    }
}

/**
 * Parse clock time string (e.g., "10:30" or "1:30:45") to total seconds.
 */
private fun parseClockTimeToSeconds(time: String?): Int? {
    if (time.isNullOrBlank()) return null
    val parts = time.split(":")
    return try {
        // Seconds may carry tenths ("0:02:59.9", as chess.com writes them).
        val seconds = parts.last().toDouble().toInt()
        when (parts.size) {
            3 -> parts[0].toInt() * 3600 + parts[1].toInt() * 60 + seconds
            2 -> parts[0].toInt() * 60 + seconds
            1 -> seconds
            else -> null
        }
    } catch (e: NumberFormatException) {
        null
    }
}

/**
 * Bar graph showing the score difference between consecutive moves.
 * Highlights blunders (big negative bars) and good moves (positive bars).
 * Uses analyse scores when available, otherwise preview scores.
 */
@Composable
fun ScoreDifferenceGraph(
    previewScores: Map<Int, MoveScore>,
    analyseScores: Map<Int, MoveScore>,
    totalMoves: Int,
    currentMoveIndex: Int,
    currentStage: AnalysisStage,
    userPlayedBlack: Boolean,
    graphSettings: GraphSettings,
    onMoveSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    // Always show scores from WHITE's perspective (positive = good for white)
    val goodMoveColor = Color(graphSettings.plusScoreColor.toInt())
    val blunderColor = Color(graphSettings.negativeScoreColor.toInt())

    Canvas(modifier = modifier.moveGraph(graphSettings, totalMoves, currentStage, onMoveSelected)) {
        if (totalMoves == 0) return@Canvas
        val maxDiff = graphSettings.barGraphRange.toFloat() // Range from settings

        drawScoreAxis()
        drawScoreBars(previewScores, analyseScores, totalMoves, currentStage, maxDiff, goodMoveColor, blunderColor)
        drawCurrentMoveLine(currentMoveIndex, totalMoves, currentStage, Color(graphSettings.verticalLineColor.toInt()), 3f)
    }
}

/**
 * Score combi graph: the score line graph and the score bars graph in one. The line's areas use light
 * versions of the score colours; the bars, drawn on top, use the bars graph's colours. Each keeps
 * its own range.
 */
@Composable
fun CombinedScoreGraph(
    previewScores: Map<Int, MoveScore>,
    analyseScores: Map<Int, MoveScore>,
    totalMoves: Int,
    currentMoveIndex: Int,
    currentStage: AnalysisStage,
    graphSettings: GraphSettings,
    onMoveSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val positive = Color(graphSettings.plusScoreColor.toInt())
    val negative = Color(graphSettings.negativeScoreColor.toInt())
    val lightPositive = lerp(positive, Color.White, 0.5f)
    val lightNegative = lerp(negative, Color.White, 0.5f)
    val scratchPath = remember { Path() }

    Canvas(modifier = modifier.moveGraph(graphSettings, totalMoves, currentStage, onMoveSelected)) {
        if (totalMoves == 0) return@Canvas
        val maxScore = graphSettings.lineGraphRange.toFloat()

        drawScoreAxis()
        drawScoreLineAreas(previewScores, analyseScores, totalMoves, currentStage, maxScore, lightPositive, lightNegative, scratchPath)
        drawScoreBars(previewScores, analyseScores, totalMoves, currentStage, graphSettings.barGraphRange.toFloat(), positive, negative)
        drawAnalyseProgressLine(analyseScores, totalMoves, currentStage, maxScore, Color(graphSettings.analyseLineColor.toInt()))
        drawCurrentMoveLine(currentMoveIndex, totalMoves, currentStage, Color(graphSettings.verticalLineColor.toInt()), 5f)
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
    if (!uiState.analysisEnabled) return

    // While the next position is being analysed, keep the previous lines on screen (dimmed, not
    // tappable) so the content below doesn't jump on every move. Lines are always paired with the
    // board they belong to. A new game starts empty.
    val lastShown = remember(uiState.gameLoadVersion) { arrayOfNulls<Pair<AnalysisResult, ChessBoard>>(1) }
    val fresh = result != null && uiState.stockfishReady && result.fen == uiState.currentBoard.getFen()
    if (fresh) lastShown[0] = result!! to uiState.currentBoard
    val (shownResult, shownBoard) = lastShown[0] ?: return

    StockfishLinesCard(
        shownResult, shownBoard, uiState.stockfishName,
        if (fresh) modifier else modifier.alpha(0.45f),
        if (fresh) onExploreLine else null
    )
}

/** Shared with AI preparation; null exploration keeps the captured position fixed. */
@Composable
fun StockfishLinesCard(
    result: AnalysisResult,
    board: ChessBoard,
    engineName: String,
    modifier: Modifier = Modifier,
    onExploreLine: ((String, Int) -> Unit)? = null
) {
    val isWhiteTurn = board.getTurn() == PieceColor.WHITE
    // Scores use White's perspective (Stockfish reports them for the side to move).
    val whiteScores = result.lines.map { line ->
        if (isWhiteTurn) MoveScore(line.score, line.isMate, line.mateIn)
        else MoveScore(-line.score, line.isMate, -line.mateIn)
    }
    // One score column for all lines, wide enough that the longest score (e.g. "+12.5") stays on
    // one line at any font scale.
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val scoreStyle = LocalTextStyle.current.merge(PvScoreStyle)
    val scoreTexts = whiteScores.map { it.formatDisplay() }
    val scoreWidth = remember(scoreTexts, scoreStyle, density) {
        val widest = scoreTexts.maxOfOrNull {
            textMeasurer.measure(it, scoreStyle, maxLines = 1, softWrap = false).size.width
        } ?: 0
        // 1.dp absorbs dp/px rounding.
        maxOf(50.dp, with(density) { widest.toDp() } + PvScoreHorizontalPadding * 2 + 1.dp)
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
            // Title row with depth and nodes info
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = engineName,
                    modifier = Modifier.weight(1f).padding(end = 8.dp),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.SubtleText
                )
                // Format nodes with K or M suffix
                val nodesFormatted = when {
                    result.nodes >= 1_000_000 -> "${result.nodes / 1_000_000}M"
                    result.nodes >= 1_000 -> "${result.nodes / 1_000}K"
                    else -> "${result.nodes}"
                }
                Text(
                    text = "Depth: ${result.depth}  Nodes: $nodesFormatted",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.MediumGray
                )
            }
            result.lines.forEachIndexed { index, line ->
                val onMoveClick: ((Int) -> Unit)? = onExploreLine?.let { explore ->
                    { moveIndex -> explore(line.pv, moveIndex) }
                }
                PvLineRow(
                    line = line,
                    whiteScore = whiteScores[index],
                    scoreWidth = scoreWidth,
                    board = board,
                    isWhiteTurn = isWhiteTurn,
                    onMoveClick = onMoveClick
                )
            }
        }
    }
}

private val PvScoreStyle = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium)
private val PvScoreHorizontalPadding = 6.dp

/**
 * Row displaying a single principal variation line with score and clickable moves.
 */
@Composable
private fun PvLineRow(
    line: PvLine,
    whiteScore: MoveScore,
    scoreWidth: Dp,
    board: ChessBoard,
    isWhiteTurn: Boolean,
    onMoveClick: ((Int) -> Unit)?
) {
    // Score display: always from WHITE's perspective (positive = good for white)
    val adjustedScore = whiteScore.score
    val displayScore = whiteScore.formatDisplay()

    val scoreColor = when {
        line.isMate -> if (whiteScore.isPositiveMate) AppColors.PositiveGreen else AppColors.NegativeRed
        else -> {
            when {
                adjustedScore > 0.3f -> AppColors.PositiveGreen  // Green - good for player
                adjustedScore < -0.3f -> AppColors.NegativeRed  // Red - bad for player
                else -> AppColors.AccentBlue  // Blue - equal
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
                .width(scoreWidth)
                .background(AppColors.AnalysisPanelBg, RoundedCornerShape(4.dp))
                .padding(horizontal = PvScoreHorizontalPadding, vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = displayScore,
                style = LocalTextStyle.current.merge(PvScoreStyle),
                color = scoreColor,
                maxLines = 1,
                softWrap = false
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // PV moves with piece symbols - clickable
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState())
                .background(AppColors.AnalysisPanelMoveBg, RoundedCornerShape(4.dp))
                .padding(horizontal = 4.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            formattedMoves.forEachIndexed { index, formattedMove ->
                Text(
                    text = formattedMove,
                    fontSize = 14.sp,
                    color = AppColors.LightGray,
                    modifier = Modifier
                        .clip(RoundedCornerShape(3.dp))
                        .clickable(enabled = onMoveClick != null) { onMoveClick?.invoke(index) }
                        .background(AppColors.AnalysisMoveChipBg)
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                )
            }
        }
    }
}

/**
 * Format UCI moves with piece symbols and capture notation.
 */
internal fun formatUciMovesWithCaptures(pv: String, startBoard: ChessBoard, isWhiteTurn: Boolean): List<String> {
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
            val isWhitePiece = piece.color == com.eval.chess.PieceColor.WHITE
            getPieceSymbol(piece.type, isWhitePiece)
        } else {
            // Fallback - inverted because Unicode symbols are visually inverted
            if (currentIsWhite) BLACK_PAWN else WHITE_PAWN
        }

        // Check for capture: either there's a piece on target square, or it's en passant
        val targetPiece = toSquare?.let { tempBoard.getPiece(it) }
        val isPawn = piece?.type == com.eval.chess.PieceType.PAWN
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
private fun getPieceSymbol(pieceType: com.eval.chess.PieceType, isWhite: Boolean): String {
    return when (pieceType) {
        com.eval.chess.PieceType.KING -> if (isWhite) BLACK_KING else WHITE_KING
        com.eval.chess.PieceType.QUEEN -> if (isWhite) BLACK_QUEEN else WHITE_QUEEN
        com.eval.chess.PieceType.ROOK -> if (isWhite) BLACK_ROOK else WHITE_ROOK
        com.eval.chess.PieceType.BISHOP -> if (isWhite) BLACK_BISHOP else WHITE_BISHOP
        com.eval.chess.PieceType.KNIGHT -> if (isWhite) BLACK_KNIGHT else WHITE_KNIGHT
        com.eval.chess.PieceType.PAWN -> if (isWhite) BLACK_PAWN else WHITE_PAWN
    }
}
