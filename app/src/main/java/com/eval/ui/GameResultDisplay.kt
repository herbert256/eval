package com.eval.ui

import com.eval.chess.PgnParser
import com.eval.data.LichessGame

internal enum class GameOutcome(val text: String) {
    WHITE_WIN("1-0"), BLACK_WIN("0-1"), DRAW("½-½"), UNKNOWN("-")
}

/** A missing winner is not evidence of a draw. Only display confirmed results. */
internal fun gameOutcome(game: LichessGame): GameOutcome {
    if (game.status in setOf("created", "started", "*")) return GameOutcome.UNKNOWN
    return when {
        game.winner == "white" -> GameOutcome.WHITE_WIN
        game.winner == "black" -> GameOutcome.BLACK_WIN
        game.status == "1-0" -> GameOutcome.WHITE_WIN
        game.status == "0-1" -> GameOutcome.BLACK_WIN
        game.status in setOf("draw", "stalemate", "1/2-1/2") -> GameOutcome.DRAW
        else -> {
            val pgnResult = game.pgn?.let { PgnParser.parseHeaders(it)["Result"] ?: PgnParser.parseResult(it) }
            when (pgnResult) {
                "1-0" -> GameOutcome.WHITE_WIN
                "0-1" -> GameOutcome.BLACK_WIN
                "1/2-1/2" -> GameOutcome.DRAW
                else -> GameOutcome.UNKNOWN
            }
        }
    }
}

internal fun playerResultText(game: LichessGame, playerName: String): String {
    val outcome = gameOutcome(game)
    if (outcome == GameOutcome.UNKNOWN) return "-"
    if (outcome == GameOutcome.DRAW) return "draw"
    val playsWhite = game.players.white.user?.name?.equals(playerName, ignoreCase = true) == true
    val playsBlack = game.players.black.user?.name?.equals(playerName, ignoreCase = true) == true
    if (!playsWhite && !playsBlack) return outcome.text
    val won = (outcome == GameOutcome.WHITE_WIN && playsWhite) ||
        (outcome == GameOutcome.BLACK_WIN && playsBlack)
    return if (won) "won" else "lost"
}
