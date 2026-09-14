package com.eval.chess

import com.eval.data.*
import com.eval.export.PgnExporter
import com.eval.ui.MoveDetails
import com.eval.ui.MoveScore
import org.junit.Assert.*
import org.junit.Test
import java.util.Locale

class PgnEvaluationExportTest {
    private fun export(score: MoveScore): String {
        val game = LichessGame("test", false, "standard", "blitz", null, "*", null,
            Players(Player(User("A", "a"), null, null), Player(User("B", "b"), null, null)),
            "1. e4 *", null, null, null, null)
        return PgnExporter.exportAnnotatedPgn(game, listOf(MoveDetails("e4", "e2", "e4", false, "P")), mapOf(0 to score), emptyMap(), null)
    }

    @Test fun numeric_evaluations_use_a_decimal_point_under_comma_locales() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            val pgn = export(MoveScore(1.25f, false, 0))
            assertTrue(pgn, pgn.contains("[%eval 1.25]"))
        } finally { Locale.setDefault(original) }
    }

    @Test fun mate_evaluations_use_pgn_annotation_not_ui_display_notation() {
        val pgn = export(MoveScore(-100f, true, -3))
        assertTrue(pgn, pgn.contains("[%eval #-3]"))
    }
}
