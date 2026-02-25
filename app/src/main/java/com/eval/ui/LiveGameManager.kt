package com.eval.ui

import com.eval.audio.MoveSoundPlayer
import com.eval.chess.PieceType
import com.eval.chess.Square
import com.eval.data.ChessRepository
import com.eval.data.LiveGameEvent
import com.eval.data.StreamMoveData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Manages live game following functionality.
 */
internal class LiveGameManager(
    private val repository: ChessRepository,
    private val getUiState: () -> GameUiState,
    private val updateUiState: (GameUiState.() -> GameUiState) -> Unit,
    private val viewModelScope: CoroutineScope,
    private val moveSoundPlayer: MoveSoundPlayer,
    private val appendBoardHistory: (com.eval.chess.ChessBoard) -> Unit
) {
    private var liveGameJob: Job? = null
    private var liveBoard: com.eval.chess.ChessBoard? = null

    /**
     * Start following a live game.
     */
    fun startLiveFollow(gameId: String) {
        stopLiveFollow()
        liveBoard = buildLatestBoard(getUiState())

        updateUiState {
            copy(
                isLiveGame = true,
                liveGameId = gameId,
                liveStreamConnected = false
            )
        }

        liveGameJob = viewModelScope.launch {
            repository.streamLiveGame(gameId).collect { event ->
                when (event) {
                    is LiveGameEvent.Connected -> {
                        updateUiState { copy(liveStreamConnected = true) }
                    }
                    is LiveGameEvent.Disconnected -> {
                        updateUiState { copy(liveStreamConnected = false) }
                    }
                    is LiveGameEvent.GameInfo -> {
                        // Initial game info received
                    }
                    is LiveGameEvent.Move -> {
                        handleLiveMove(event.data)
                    }
                    is LiveGameEvent.GameEnd -> {
                        updateUiState {
                            copy(
                                isLiveGame = false,
                                liveStreamConnected = false
                            )
                        }
                        val currentGame = getUiState().game
                        if (currentGame != null) {
                            updateUiState {
                                copy(
                                    game = currentGame.copy(
                                        winner = event.winner,
                                        status = event.status ?: "ended"
                                    )
                                )
                            }
                        }
                    }
                    is LiveGameEvent.Error -> {
                        updateUiState {
                            copy(errorMessage = "Live stream: ${event.message}")
                        }
                    }
                }
            }
        }
    }

    /**
     * Handle a new move from the live stream.
     */
    private fun handleLiveMove(moveData: StreamMoveData) {
        val uciMove = moveData.lm ?: return
        if (uciMove.length < 4) return
        val state = getUiState()
        val currentMoves = state.moves.toMutableList()
        val currentMoveDetails = state.moveDetails.toMutableList()
        val board = liveBoard?.copy() ?: buildLatestBoard(state)
        val from = uciMove.substring(0, 2)
        val to = uciMove.substring(2, 4)
        val fromSquare = Square.fromAlgebraic(from)
        val toSquare = Square.fromAlgebraic(to)

        if (fromSquare != null && toSquare != null) {
            val piece = board.getPiece(fromSquare) ?: return  // Invalid move - no piece at source
            val targetPiece = board.getPiece(toSquare)
            val isEnPassant = piece.type == PieceType.PAWN &&
                fromSquare.file != toSquare.file &&
                targetPiece == null
            val isCapture = targetPiece != null || isEnPassant

            val promotion = if (uciMove.length > 4) {
                when (uciMove[4]) {
                    'q' -> PieceType.QUEEN
                    'r' -> PieceType.ROOK
                    'b' -> PieceType.BISHOP
                    'n' -> PieceType.KNIGHT
                    else -> null
                }
            } else null

            val moveApplied = board.makeMoveFromSquares(fromSquare, toSquare, promotion)
            if (!moveApplied) return
            liveBoard = board.copy()
            appendBoardHistory(board)
            currentMoves.add(uciMove)

            val pieceType = when (piece.type) {
                PieceType.KING -> "K"
                PieceType.QUEEN -> "Q"
                PieceType.ROOK -> "R"
                PieceType.BISHOP -> "B"
                PieceType.KNIGHT -> "N"
                PieceType.PAWN -> "P"
            }

            // Move index is 0-based: even indices (0, 2, 4) are white moves
            val moveIndex = currentMoves.size - 1
            val clockTime = if (moveIndex % 2 == 0) {
                moveData.wc?.let { formatClockSeconds(it) }
            } else {
                moveData.bc?.let { formatClockSeconds(it) }
            }

            val moveDetail = MoveDetails(
                san = uciMove,
                from = from,
                to = to,
                isCapture = isCapture,
                pieceType = pieceType,
                clockTime = clockTime
            )
            currentMoveDetails.add(moveDetail)

            val autoFollow = state.autoFollowLive
            val newMoveIndex = if (autoFollow) currentMoves.size - 1 else state.currentMoveIndex

            updateUiState {
                copy(
                    moves = currentMoves,
                    moveDetails = currentMoveDetails,
                    currentMoveIndex = newMoveIndex,
                    currentBoard = if (autoFollow) board else currentBoard
                )
            }

            if (state.generalSettings.moveSoundsEnabled && autoFollow) {
                moveSoundPlayer.playMove(isCapture = isCapture, isCheck = false, isCastle = false)
            }
        }
    }

    private fun formatClockSeconds(seconds: Int): String {
        val hours = seconds / 3600
        val mins = (seconds % 3600) / 60
        val secs = seconds % 60
        return if (hours > 0) {
            "%d:%02d:%02d".format(hours, mins, secs)
        } else {
            "%d:%02d".format(mins, secs)
        }
    }

    /**
     * Stop following a live game.
     */
    fun stopLiveFollow() {
        liveGameJob?.cancel()
        liveGameJob = null
        liveBoard = null
        updateUiState {
            copy(
                isLiveGame = false,
                liveGameId = null,
                liveStreamConnected = false
            )
        }
    }

    /**
     * Toggle auto-follow mode for live games.
     */
    fun toggleAutoFollowLive() {
        updateUiState {
            copy(autoFollowLive = !autoFollowLive)
        }
    }

    fun cancel() {
        liveGameJob?.cancel()
        liveGameJob = null
        liveBoard = null
    }

    private fun buildLatestBoard(state: GameUiState): com.eval.chess.ChessBoard {
        val board = com.eval.chess.ChessBoard()
        for (move in state.moves) {
            val success = board.makeMove(move) || board.makeUciMove(move)
            if (!success) break
        }
        return board
    }
}
