package com.eval.stockfish

import org.junit.Assert.*
import org.junit.Test

class EngineIdentityTest {
    @Test fun reads_version_and_build_names_without_assuming_a_version() {
        assertEquals("Stockfish 19", StockfishEngine.parseUciEngineName("id name Stockfish 19"))
        assertEquals("Stockfish 17.1", StockfishEngine.parseUciEngineName("id name Stockfish 17.1"))
        assertEquals("Stockfish dev-20260915", StockfishEngine.parseUciEngineName("  id\tname  Stockfish dev-20260915  "))
    }

    @Test fun ignores_other_uci_messages_and_missing_names() {
        for (line in listOf("id author Stockfish developers", "option name Threads type spin", "uciok", "id name", "id name   ")) {
            assertNull(line, StockfishEngine.parseUciEngineName(line))
        }
    }
}
