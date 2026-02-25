package com.eval.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.eval.chess.ChessBoard
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal class ExportShareManager(
    private val getUiState: () -> GameUiState,
    private val updateUiState: (GameUiState.() -> GameUiState) -> Unit,
    private val viewModelScope: CoroutineScope
) {
    fun showSharePositionDialog() {
        updateUiState { copy(showSharePositionDialog = true) }
    }

    fun hideSharePositionDialog() {
        updateUiState { copy(showSharePositionDialog = false) }
    }

    fun getCurrentFen(): String = getUiState().currentBoard.getFen()

    fun copyFenToClipboard(context: Context) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Chess FEN", getCurrentFen())
        clipboard.setPrimaryClip(clip)
    }

    fun sharePositionAsText(context: Context) {
        val state = getUiState()
        val fen = state.currentBoard.getFen()
        val moveIndex = state.currentMoveIndex
        val game = state.game
        val analysis = state.analysisResult

        val shareText = buildString {
            if (game != null) {
                appendLine("${game.players.white.user?.name ?: "White"} vs ${game.players.black.user?.name ?: "Black"}")
                appendLine()
            }
            appendLine("Position after move ${(moveIndex + 2) / 2}${if (moveIndex % 2 == 0) "." else "..."}")
            appendLine()
            appendLine("FEN: $fen")
            if (analysis != null && analysis.lines.isNotEmpty()) {
                val topLine = analysis.lines.first()
                val evalText = if (topLine.isMate) {
                    "Mate in ${kotlin.math.abs(topLine.mateIn)}"
                } else {
                    "%.2f".format(topLine.score)
                }
                appendLine()
                appendLine("Evaluation: $evalText (depth ${analysis.depth})")
                appendLine("Best move: ${topLine.pv.split(" ").firstOrNull() ?: "N/A"}")
            }
            appendLine()
            val lichessFen = fen.replace(' ', '_')
            appendLine("Analyze at: https://lichess.org/analysis/$lichessFen")
        }

        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, shareText)
            type = "text/plain"
        }
        context.startActivity(Intent.createChooser(sendIntent, "Share position"))
    }

    fun exportAnnotatedPgn(context: Context) {
        val state = getUiState()
        val game = state.game ?: return
        val openingName = state.currentOpeningName ?: state.openingName

        val pgn = com.eval.export.PgnExporter.exportAnnotatedPgn(
            game = game,
            moveDetails = state.moveDetails,
            analyseScores = state.analyseScores,
            moveQualities = state.moveQualities,
            openingName = openingName
        )

        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, pgn)
            putExtra(Intent.EXTRA_SUBJECT, "Chess Game PGN - ${game.players.white.user?.name} vs ${game.players.black.user?.name}")
            type = "text/plain"
        }
        context.startActivity(Intent.createChooser(sendIntent, "Export PGN"))
    }

    fun copyPgnToClipboard(context: Context) {
        val state = getUiState()
        val game = state.game ?: return
        val openingName = state.currentOpeningName ?: state.openingName
        val pgn = com.eval.export.PgnExporter.exportAnnotatedPgn(
            game = game,
            moveDetails = state.moveDetails,
            analyseScores = state.analyseScores,
            moveQualities = state.moveQualities,
            openingName = openingName
        )
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Chess PGN", pgn)
        clipboard.setPrimaryClip(clip)
    }

    fun exportAsGif(context: Context) {
        val state = getUiState()
        if (state.game == null) return
        val moveDetails = state.moveDetails
        val analyseScores = if (state.analyseScores.isNotEmpty()) {
            state.previewScores + state.analyseScores
        } else {
            state.previewScores
        }

        updateUiState { copy(showGifExportDialog = true, gifExportProgress = 0f) }

        viewModelScope.launch {
            try {
                val boards = mutableListOf<ChessBoard>()
                var board = ChessBoard()
                boards.add(board.copy())

                for (move in moveDetails) {
                    val success = board.makeMove(move.san) || board.makeUciMove(move.san)
                    if (success) {
                        boards.add(board.copy())
                    }
                }

                val boardScores = mutableMapOf<Int, MoveScore>()
                analyseScores.forEach { (moveIndex, score) ->
                    boardScores[moveIndex + 1] = score
                }

                val moves = moveDetails.map { it.san }
                val file = com.eval.export.GifExporter.exportAsGifWithAnnotations(
                    context = context,
                    boards = boards,
                    moves = moves,
                    scores = boardScores,
                    frameDelay = 1000,
                    callback = object : com.eval.export.GifExporter.ProgressCallback {
                        override fun onProgress(current: Int, total: Int) {
                            updateUiState { copy(gifExportProgress = current.toFloat() / total) }
                        }
                    }
                )

                updateUiState { copy(showGifExportDialog = false, gifExportProgress = null) }

                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )

                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "image/gif"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(shareIntent, "Share GIF"))
            } catch (e: Exception) {
                updateUiState {
                    copy(
                        showGifExportDialog = false,
                        gifExportProgress = null,
                        errorMessage = "GIF export failed: ${e.message}"
                    )
                }
            }
        }
    }

    fun cancelGifExport() {
        updateUiState { copy(showGifExportDialog = false, gifExportProgress = null) }
    }
}

