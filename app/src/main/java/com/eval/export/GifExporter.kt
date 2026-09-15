package com.eval.export

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import com.eval.chess.ChessBoard
import com.eval.chess.Piece
import com.eval.chess.PieceColor
import com.eval.chess.PieceType
import com.eval.ui.MoveScore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs

/**
 * Exports a chess game as an animated GIF.
 */
object GifExporter {
    private val exportMutex = Mutex()

    private const val BOARD_SIZE = 400
    private const val SQUARE_SIZE = BOARD_SIZE / 8
    private const val EVAL_BAR_WIDTH = 20
    private const val TOTAL_WIDTH = BOARD_SIZE + EVAL_BAR_WIDTH

    // Colors
    private const val WHITE_SQUARE = 0xFFF0D9B5.toInt()
    private const val BLACK_SQUARE = 0xFFB58863.toInt()
    private const val WHITE_PIECE = 0xFFFFFFFF.toInt()
    private const val BLACK_PIECE = 0xFF000000.toInt()
    private const val EVAL_WHITE = 0xFFFFFFFF.toInt()
    private const val EVAL_BLACK = 0xFF333333.toInt()

    // Piece unicode characters
    private val PIECE_SYMBOLS = mapOf(
        'K' to "\u2654", 'Q' to "\u2655", 'R' to "\u2656",
        'B' to "\u2657", 'N' to "\u2658", 'P' to "\u2659",
        'k' to "\u265A", 'q' to "\u265B", 'r' to "\u265C",
        'b' to "\u265D", 'n' to "\u265E", 'p' to "\u265F"
    )

    // Paint instances reused across draws. Export is a sequential user flow
    // (one GIF at a time) so sharing is safe; reconfigure fields per draw.
    private val squarePaint = Paint()
    private val piecePaint = Paint().apply {
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
        typeface = Typeface.DEFAULT
    }
    private val evalBarPaint = Paint()
    private val evalTextPaint = Paint().apply {
        textSize = 12f
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
        typeface = Typeface.DEFAULT_BOLD
    }
    private val annotationBgPaint = Paint().apply { color = 0xFF2D2D2D.toInt() }
    private val annotationTextPaint = Paint().apply {
        color = Color.WHITE
        textSize = 18f
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
        typeface = Typeface.DEFAULT_BOLD
    }

    /**
     * Convert a Piece to its FEN character representation.
     */
    private fun pieceToChar(piece: Piece): Char {
        val baseChar = when (piece.type) {
            PieceType.KING -> 'K'
            PieceType.QUEEN -> 'Q'
            PieceType.ROOK -> 'R'
            PieceType.BISHOP -> 'B'
            PieceType.KNIGHT -> 'N'
            PieceType.PAWN -> 'P'
        }
        return if (piece.color == PieceColor.WHITE) baseChar else baseChar.lowercaseChar()
    }

    /**
     * Progress callback for GIF export.
     */
    interface ProgressCallback {
        fun onProgress(current: Int, total: Int)
    }

    /**
     * Encode a sequence of frames into an animated GIF file.
     *
     * @param context Android context
     * @param frameCount Number of frames to encode
     * @param filePrefix Prefix for the generated filename
     * @param frameDelay Delay between frames in milliseconds
     * @param renderFrame Lambda that renders frame at given index to a Bitmap
     * @param callback Progress callback
     * @return File containing the exported GIF
     */
    private suspend fun encodeGif(
        context: Context,
        frameCount: Int,
        filePrefix: String,
        frameDelay: Int,
        renderFrame: (Int) -> Bitmap,
        callback: ProgressCallback?
    ): File {
        var outputFile: File? = null
        try {
            return withContext(Dispatchers.IO) {
                exportMutex.withLock {
                    encodeFrames(context, frameCount, filePrefix, frameDelay, renderFrame, callback) {
                        outputFile = it
                    }
                }
            }
        } catch (e: CancellationException) {
            // Also clean up if cancellation happens while dispatching the
            // completed file back to the caller.
            outputFile?.delete()
            throw e
        }
    }

    private suspend fun encodeFrames(
        context: Context,
        frameCount: Int,
        filePrefix: String,
        frameDelay: Int,
        renderFrame: (Int) -> Bitmap,
        callback: ProgressCallback?,
        onFileCreated: (File) -> Unit
    ): File {
        val directory = File(context.cacheDir, "gif_exports")
        check(directory.isDirectory || directory.mkdirs()) { "Cannot create GIF export folder" }
        val file = File.createTempFile("${filePrefix}_", ".gif", directory)
        onFileCreated(file)
        val encoder = AnimatedGifEncoder()
        // Chess board frames share nearly-identical palettes; reuse the first
        // frame's palette to skip NeuQuant training on every subsequent frame.
        encoder.reusePalette = true

        var success = false
        try {
            FileOutputStream(file).use { fos ->
                check(encoder.start(fos)) { "Cannot start GIF encoder" }
                encoder.setDelay(frameDelay)
                encoder.setRepeat(0) // Loop forever
                encoder.setQuality(10)

                for (index in 0 until frameCount) {
                    currentCoroutineContext().ensureActive()
                    val bitmap = renderFrame(index)
                    try {
                        check(encoder.addFrame(bitmap)) { "Cannot encode GIF frame" }
                    } finally {
                        bitmap.recycle()
                    }
                    currentCoroutineContext().ensureActive()
                    callback?.onProgress(index + 1, frameCount)
                }

                currentCoroutineContext().ensureActive()
                check(encoder.finish()) { "Cannot finish GIF" }
            }
            success = true
        } finally {
            // Delete the (possibly truncated) file if encoding threw, so the
            // cacheDir doesn't accumulate partial GIFs over time.
            if (!success && file.exists()) file.delete()
        }

        return file
    }

    /**
     * Export a game as an animated GIF.
     *
     * @param context Android context
     * @param boards List of ChessBoard states for each position
     * @param scores Map of move index to evaluation score
     * @param frameDelay Delay between frames in milliseconds
     * @param callback Progress callback
     * @return File containing the exported GIF
     */
    suspend fun exportAsGif(
        context: Context,
        boards: List<ChessBoard>,
        scores: Map<Int, MoveScore> = emptyMap(),
        frameDelay: Int = 800,
        callback: ProgressCallback? = null
    ): File = encodeGif(context, boards.size, "game", frameDelay, { index ->
        renderFrame(boards[index], scores[index])
    }, callback)

    /**
     * Draw board squares and pieces onto the canvas.
     */
    private fun drawBoardContent(canvas: Canvas, board: ChessBoard, squareSize: Int, lightColor: Int, darkColor: Int) {
        for (row in 0 until 8) {
            for (col in 0 until 8) {
                val isLight = (row + col) % 2 == 0
                squarePaint.color = if (isLight) lightColor else darkColor
                canvas.drawRect(
                    (col * squareSize).toFloat(),
                    (row * squareSize).toFloat(),
                    ((col + 1) * squareSize).toFloat(),
                    ((row + 1) * squareSize).toFloat(),
                    squarePaint
                )
            }
        }

        piecePaint.textSize = squareSize * 0.85f

        for (row in 0 until 8) {
            for (col in 0 until 8) {
                val piece = board.getPiece(col, 7 - row) // file, rank (rank 7 is top row)
                if (piece != null) {
                    val pieceChar = pieceToChar(piece)
                    val symbol = PIECE_SYMBOLS[pieceChar] ?: continue
                    val isWhite = piece.color == PieceColor.WHITE

                    // Draw piece with outline for visibility
                    val x = col * squareSize + squareSize / 2f
                    val y = row * squareSize + squareSize * 0.75f

                    // Draw outline
                    piecePaint.style = Paint.Style.STROKE
                    piecePaint.strokeWidth = 2f
                    piecePaint.color = if (isWhite) BLACK_PIECE else WHITE_PIECE
                    canvas.drawText(symbol, x, y, piecePaint)

                    // Draw fill
                    piecePaint.style = Paint.Style.FILL
                    piecePaint.color = if (isWhite) WHITE_PIECE else BLACK_PIECE
                    canvas.drawText(symbol, x, y, piecePaint)
                }
            }
        }
    }

    /**
     * Render a chess position to a bitmap, with optional annotation text at the top.
     *
     * @param board Chess board state to render
     * @param score Evaluation score for the eval bar (null shows 50%)
     * @param annotationText Optional move annotation text shown above the board
     * @return Bitmap of the rendered frame
     */
    private fun renderFrame(board: ChessBoard, score: MoveScore?, annotationText: String? = null): Bitmap {
        val annotationHeight = if (annotationText != null) 30 else 0
        val totalHeight = BOARD_SIZE + annotationHeight

        val bitmap = Bitmap.createBitmap(TOTAL_WIDTH, totalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Draw annotation bar if needed
        if (annotationText != null) {
            drawAnnotationBar(canvas, annotationText, annotationHeight)
        }

        // Translate canvas down for board content
        canvas.save()
        canvas.translate(0f, annotationHeight.toFloat())
        drawBoardContent(canvas, board, SQUARE_SIZE, WHITE_SQUARE, BLACK_SQUARE)
        drawEvalBar(canvas, score)
        canvas.restore()

        return bitmap
    }

    /**
     * Draw the move annotation bar at the top of the frame.
     */
    private fun drawAnnotationBar(canvas: Canvas, text: String, height: Int) {
        canvas.drawRect(0f, 0f, TOTAL_WIDTH.toFloat(), height.toFloat(), annotationBgPaint)
        canvas.drawText(text, TOTAL_WIDTH / 2f, 22f, annotationTextPaint)
    }

    /**
     * Draw the evaluation bar on the right side.
     */
    private fun drawEvalBar(canvas: Canvas, score: MoveScore?) {
        val barX = BOARD_SIZE.toFloat()

        // Background (black side)
        evalBarPaint.color = EVAL_BLACK
        canvas.drawRect(barX, 0f, barX + EVAL_BAR_WIDTH, BOARD_SIZE.toFloat(), evalBarPaint)

        // Calculate white portion
        val whiteHeight = if (score == null) {
            BOARD_SIZE / 2f // 50% when no score
        } else if (score.isMate) {
            if (score.isPositiveMate) BOARD_SIZE.toFloat() else 0f
        } else {
            // Convert score to percentage (clamp to -10 to +10)
            val clampedScore = score.score.coerceIn(-10f, 10f)
            val percentage = (clampedScore + 10f) / 20f
            BOARD_SIZE * percentage
        }

        // Draw white portion from bottom
        evalBarPaint.color = EVAL_WHITE
        canvas.drawRect(
            barX,
            BOARD_SIZE - whiteHeight,
            barX + EVAL_BAR_WIDTH,
            BOARD_SIZE.toFloat(),
            evalBarPaint
        )

        // Draw score text
        if (score != null) {
            drawEvalBarText(canvas, score, barX)
        }
    }

    /**
     * Draw the score text on the evaluation bar.
     */
    private fun drawEvalBarText(canvas: Canvas, score: MoveScore, barX: Float) {
        // M3 / -M3 style for the eval bar; strip the leading '+' to save space.
        val scoreText = score.formatDisplay(decimals = 1).removePrefix("+")

        // Draw at center of bar
        val textX = barX + EVAL_BAR_WIDTH / 2
        val textY = BOARD_SIZE / 2f + 4f

        // Background for readability
        evalTextPaint.color = Color.WHITE
        evalTextPaint.style = Paint.Style.FILL
        canvas.drawText(scoreText, textX, textY, evalTextPaint)
        evalTextPaint.color = Color.BLACK
        evalTextPaint.style = Paint.Style.STROKE
        evalTextPaint.strokeWidth = 0.5f
        canvas.drawText(scoreText, textX, textY, evalTextPaint)
    }

    /**
     * Export with move annotations overlay.
     */
    suspend fun exportAsGifWithAnnotations(
        context: Context,
        boards: List<ChessBoard>,
        moves: List<String>, // SAN notation
        scores: Map<Int, MoveScore> = emptyMap(),
        frameDelay: Int = 800,
        callback: ProgressCallback? = null
    ): File = encodeGif(context, boards.size, "game_annotated", frameDelay, { index ->
        val moveText = if (index > 0 && index <= moves.size) {
            val before = boards[index - 1]
            val moveNum = before.getFen().substringAfterLast(' ')
            val isWhite = before.getTurn() == PieceColor.WHITE
            if (isWhite) "$moveNum. ${moves[index - 1]}" else "$moveNum... ${moves[index - 1]}"
        } else null
        renderFrame(boards[index], scores[index], moveText)
    }, callback)
}
