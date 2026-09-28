package com.eval.ui

import com.eval.chess.PgnParser
import com.eval.data.LichessGame

/** [text] is for display; [pgn] is the PGN game termination marker. */
internal enum class GameOutcome(val text: String, val pgn: String) {
    WHITE_WIN("1-0", "1-0"), BLACK_WIN("0-1", "0-1"), DRAW("½-½", "1/2-1/2"), UNKNOWN("-", "*")
}

/**
 * The single source for a game's result. A missing winner is not evidence of a draw:
 * the winner comes first, then explicit draw statuses, then the PGN's own result.
 */
internal fun gameOutcome(game: LichessGame): GameOutcome {
    if (game.status in setOf("created", "started", "*")) return GameOutcome.UNKNOWN
    return when {
        game.winner == "white" -> GameOutcome.WHITE_WIN
        game.winner == "black" -> GameOutcome.BLACK_WIN
        game.status == "1-0" -> GameOutcome.WHITE_WIN
        game.status == "0-1" -> GameOutcome.BLACK_WIN
        game.status in setOf("draw", "stalemate", "1/2-1/2") -> GameOutcome.DRAW
        else -> {
            val pgnResult = game.pgn?.let { pgn ->
                PgnParser.parseHeaders(pgn)["Result"]?.let { PgnParser.resultToken(it.trim()) ?: it }
                    ?: PgnParser.parseResult(pgn)
            }
            when (pgnResult) {
                "1-0" -> GameOutcome.WHITE_WIN
                "0-1" -> GameOutcome.BLACK_WIN
                "1/2-1/2" -> GameOutcome.DRAW
                else -> GameOutcome.UNKNOWN
            }
        }
    }
}

/** The PGN result token ("1-0", "0-1", "1/2-1/2" or "*") for exports and reports. */
internal fun gameResultToken(game: LichessGame): String = gameOutcome(game).pgn

/** Match the requested Lichess account consistently for row color and result. */
internal fun playerPlaysWhite(game: LichessGame, playerName: String): Boolean? {
    val account = playerName.trim()
    if (account.isEmpty()) return null
    fun matches(player: com.eval.data.Player): Boolean = player.user?.let {
        it.name.equals(account, ignoreCase = true) || it.id.equals(account, ignoreCase = true)
    } == true
    return when {
        matches(game.players.white) -> true
        matches(game.players.black) -> false
        else -> null
    }
}

internal fun playerResultText(game: LichessGame, playerName: String): String {
    val outcome = gameOutcome(game)
    if (outcome == GameOutcome.UNKNOWN) return "-"
    if (outcome == GameOutcome.DRAW) return "draw"
    val playsWhite = playerPlaysWhite(game, playerName) ?: return outcome.text
    val won = (outcome == GameOutcome.WHITE_WIN && playsWhite) ||
        (outcome == GameOutcome.BLACK_WIN && !playsWhite)
    return if (won) "win" else "lost"
}
