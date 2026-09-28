package com.eval.export

import com.eval.audio.MoveSoundPlayer
import org.junit.Assert.*
import org.junit.Test

class MoveSoundSanTest {
    @Test fun check_and_mate_are_read_from_san() {
        for (san in listOf("Qh5+", "Qxf7#", "e8=Q+", "Nf3+!", "Rxe1#?!", "O-O+", " Bb5+ ")) {
            assertTrue(san, MoveSoundPlayer.sanGivesCheck(san))
        }
        for (san in listOf("e4", "Nxe5", "O-O", "e8=Q", "Kf1!?", "e2e4", "")) {
            assertFalse(san, MoveSoundPlayer.sanGivesCheck(san))
        }
    }

    @Test fun castling_is_read_from_san_with_letters_or_zeros() {
        for (san in listOf("O-O", "O-O-O", "0-0", "0-0-0", "O-O+", "O-O-O#", "O-O!")) {
            assertTrue(san, MoveSoundPlayer.sanIsCastle(san))
        }
        for (san in listOf("Kg1", "e1g1", "O", "O-O-O-O", "Nf3", "")) {
            assertFalse(san, MoveSoundPlayer.sanIsCastle(san))
        }
    }
}
