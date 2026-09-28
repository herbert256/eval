package com.eval.ui

import com.eval.chess.ChessBoard
import com.eval.chess.PieceType
import com.eval.data.OpeningBook
import com.eval.data.LichessGame
import com.eval.data.OpeningExplorerResponse
import com.eval.data.Result
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Opening information belongs to the displayed position, independently of engine progress. */
internal class OpeningExplorerLoader(
    private val state: StateFlow<GameUiState>,
    private val updateState: (GameUiState.() -> GameUiState) -> Unit,
    private val scope: CoroutineScope,
    private val getBoardHistory: () -> List<ChessBoard>,
    private val getExploringLineHistory: () -> List<ChessBoard>,
    private val load: suspend (String) -> Result<OpeningExplorerResponse>
) {
    // Restoring the same saved game creates a new selection even at the same FEN.
    private class GameSelection(private val game: LichessGame?) {
        override fun equals(other: Any?) = other is GameSelection && game === other.game
        override fun hashCode() = System.identityHashCode(game)
    }

    private data class Position(
        val game: GameSelection,
        val fen: String,
        val stage: AnalysisStage,
        val moveIndex: Int,
        val exploring: Boolean,
        val branchIndex: Int,
        val savedIndex: Int,
        val enabled: Boolean,
        val hasToken: Boolean
    )

    private fun GameUiState.position(): Position {
        val visibility = interfaceVisibility.manualStage
        return Position(GameSelection(game), currentBoard.getFen(), currentStage, currentMoveIndex,
            isExploringLine, exploringLineMoveIndex, savedGameMoveIndex,
            // The opening name comes from the local book; only the statistics need Lichess.
            game != null && currentStage == AnalysisStage.MANUAL && visibility.showOpeningExplorer,
            hasLichessToken)
    }

    // After Lichess refuses the token, stop asking until the token is changed.
    private var rejectedToken = false

    fun observe(): Job = scope.launch {
        var requestJob: Job? = null
        var lastHasToken: Boolean? = null
        try {
            state.map { it.position() }.distinctUntilChanged().collect { position ->
                requestJob?.cancel()
                if (position.hasToken != lastHasToken) rejectedToken = false
                lastHasToken = position.hasToken
                val openingName = localOpeningName(state.value)
                val canRequest = position.enabled && position.hasToken && !rejectedToken
                updateState { copy(currentOpeningName = openingName,
                    openingExplorerData = null, openingExplorerLoading = canRequest,
                    openingExplorerError = when {
                        !position.enabled || canRequest -> null
                        rejectedToken -> "Lichess refused the access token. Check it in Settings > General."
                        else -> "Opening statistics need a Lichess access token (Settings > General)."
                    }) }
                if (canRequest) {
                    requestJob = launch {
                        delay(500) // Debounce rapid navigation before making a network request.
                        val result = load(position.fen)
                        currentCoroutineContext().ensureActive()
                        if ((result as? Result.Error)?.message == TOKEN_REJECTED) rejectedToken = true
                        updateState {
                            // A late response cannot describe a different game or board.
                            if (position() != position) this else copy(
                                openingExplorerData = (result as? Result.Success)?.data,
                                openingExplorerLoading = false,
                                openingExplorerError = (result as? Result.Error)?.message
                            )
                        }
                    }
                }
            }
        } finally {
            requestJob?.cancel()
        }
    }

    private fun localOpeningName(ui: GameUiState): String? {
        if (ui.game == null || ui.currentStage != AnalysisStage.MANUAL) return null
        val history = getBoardHistory()
        // Custom FEN studies do not have an opening sequence from the initial position.
        if (history.firstOrNull()?.getFen() != INITIAL_FEN) return null
        val mainIndex = if (ui.isExploringLine) ui.savedGameMoveIndex else ui.currentMoveIndex
        val boards = history.take(mainIndex + 2).toMutableList()
        if (ui.isExploringLine) {
            val branch = getExploringLineHistory()
            if (branch.firstOrNull()?.getFen() != boards.lastOrNull()?.getFen()) return null
            boards.addAll(branch.drop(1).take(ui.exploringLineMoveIndex + 1))
        }
        if (boards.lastOrNull()?.getFen() != ui.currentBoard.getFen()) return null
        val moves = boards.drop(1).map { board ->
            val move = board.getLastMove() ?: return null
            val promotion = when (move.promotion) {
                PieceType.QUEEN -> "q"
                PieceType.ROOK -> "r"
                PieceType.BISHOP -> "b"
                PieceType.KNIGHT -> "n"
                else -> ""
            }
            move.from.toAlgebraic() + move.to.toAlgebraic() + promotion
        }
        return OpeningBook.getOpeningName(moves)
    }

    companion object {
        private val INITIAL_FEN = ChessBoard().getFen()
        const val TOKEN_REJECTED = "Lichess refused the access token. Check it in Settings > General."
    }
}
