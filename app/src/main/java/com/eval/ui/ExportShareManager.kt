package com.eval.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.eval.chess.ChessBoard
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

internal class ExportShareManager(
    private val getUiState: () -> GameUiState,
    private val updateUiState: (GameUiState.() -> GameUiState) -> Unit,
    private val viewModelScope: CoroutineScope
) {
    private var gifExportJob: Job? = null

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
        val fenFields = fen.split(' ')
        val whiteToMove = fenFields[1] == "w"
        val turn = if (whiteToMove) "White" else "Black"
        val game = state.game
        val analysis = state.analysisResult.takeIf { state.analysisResultFen == fen }

        val shareText = buildString {
            if (game != null) {
                appendLine("${game.players.white.user?.name ?: "White"} vs ${game.players.black.user?.name ?: "Black"}")
                appendLine()
            }
            appendLine("Position: $turn to move (move ${fenFields[5]})")
            appendLine()
            appendLine("FEN: $fen")
            if (analysis != null && analysis.lines.isNotEmpty()) {
                val topLine = analysis.lines.first()
                val evalText = if (topLine.isMate) {
                    val winnerIsWhite = if (topLine.mateIn > 0) whiteToMove else !whiteToMove
                    val winner = if (winnerIsWhite) "White" else "Black"
                    if (topLine.mateIn == 0) "$winner wins by checkmate"
                    else "$winner mates in ${kotlin.math.abs(topLine.mateIn)}"
                } else {
                    val whiteScore = if (whiteToMove) topLine.score else -topLine.score
                    String.format(Locale.US, "%+.2f", whiteScore)
                }
                appendLine()
                val perspective = if (topLine.isMate) "" else "White's perspective; "
                appendLine("Evaluation: $evalText (${perspective}depth ${analysis.depth})")
                val bestMove = topLine.pv.trim().split(Regex("\\s+")).firstOrNull().orEmpty()
                if (bestMove.isNotEmpty()) appendLine("Best move: $bestMove")
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
            openingName = openingName,
            server = state.gameSelectionServer
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
            openingName = openingName,
            server = state.gameSelectionServer
        )
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Chess PGN", pgn)
        clipboard.setPrimaryClip(clip)
    }

    fun exportAsGif(context: Context) {
        if (gifExportJob?.isActive == true) return
        val state = getUiState()
        if (state.game == null) return
        val moveDetails = state.moveDetails
        val analyseScores = if (state.analyseScores.isNotEmpty()) {
            state.previewScores + state.analyseScores
        } else {
            state.previewScores
        }

        updateUiState { copy(showGifExportDialog = true, gifExportProgress = 0f) }

        gifExportJob = viewModelScope.launch {
            val exportContext = currentCoroutineContext()
            try {
                val initialBoard = requireNotNull(com.eval.chess.PgnParser.parseInitialBoard(state.game.pgn.orEmpty())) {
                    "Invalid PGN starting position"
                }
                val history = BoardHistoryBuilder.build(moveDetails.map { it.san }, initialBoard)
                require(history.failedMoveIndex == null) { "Invalid move: ${history.failedMove}" }
                val boards = history.boards

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
                            updateUiState {
                                if (exportContext.isActive && showGifExportDialog) {
                                    copy(gifExportProgress = current.toFloat() / total)
                                } else this
                            }
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
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                currentCoroutineContext().ensureActive()
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
        gifExportJob?.cancel()
        updateUiState { copy(showGifExportDialog = false, gifExportProgress = null) }
    }
}
