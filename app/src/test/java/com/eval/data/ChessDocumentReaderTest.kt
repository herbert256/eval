package com.eval.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ChessDocumentReaderTest {
    private fun zip(vararg entries: Pair<String, ByteArray>): ByteArray = ByteArrayOutputStream().use { output ->
        ZipOutputStream(output).use { zip ->
            for ((name, data) in entries) { zip.putNextEntry(ZipEntry(name)); zip.write(data); zip.closeEntry() }
        }
        output.toByteArray()
    }

    @Test fun text_is_utf8_with_a_windows_1252_fallback() {
        val tags = "[White \"Réti, Richard\"]\n[Black \"Gligorić, Svetozar\"]"
        assertEquals(tags, ChessDocumentReader.decodeText(tags.toByteArray(Charsets.UTF_8)))
        assertEquals(tags, ChessDocumentReader.decodeText(("\uFEFF" + tags).toByteArray(Charsets.UTF_8)))
        assertEquals(tags, ChessDocumentReader.decodeText(tags.toByteArray(Charsets.UTF_16)))
        val latin = "[White \"Réti, Richard\"] 1. e4 – ½"
        assertEquals(latin, ChessDocumentReader.decodeText(latin.toByteArray(charset("windows-1252"))))
        try { ChessDocumentReader.decodeText(byteArrayOf(0, 1, 2, 0)); fail("Binary data accepted") }
        catch (_: IllegalArgumentException) { }
    }

    @Test fun an_oversized_archive_part_is_skipped_and_later_parts_are_read() = runBlocking {
        val warnings = mutableListOf<String>()
        val parts = ChessDocumentReader.readArchive(zip("a.fen" to "first".toByteArray(), "video.txt" to ByteArray(9_000_000) { 65 },
            "b.pgn" to "later".toByteArray()), { true }) { warnings += it }
        assertEquals(listOf("a.fen", "b.pgn"), parts.keys.toList())
        assertEquals("later", String(parts.getValue("b.pgn")))
        assertEquals(listOf("An archive part exceeds 8 MB and was skipped: video.txt"), warnings)
    }

    @Test fun archives_are_bounded_in_total_size_and_entries_and_reject_unsafe_paths() = runBlocking {
        val warnings = mutableListOf<String>()
        val big = (1..5).map { "part$it.txt" to ByteArray(7_000_000) { 66 } }.toTypedArray()
        val parts = ChessDocumentReader.readArchive(zip(*big, "late.pgn" to "x".toByteArray()), { true }) { warnings += it }
        assertEquals(listOf("part1.txt", "part2.txt", "part3.txt", "part4.txt"), parts.keys.toList())
        assertTrue(warnings.toString(), warnings.single().contains("32 MB"))
        warnings.clear()
        val many = (1..600).map { "p$it.pgn" to byteArrayOf(65) }.toTypedArray()
        assertEquals(512, ChessDocumentReader.readArchive(zip(*many), { true }) { warnings += it }.size)
        assertTrue(warnings.toString(), warnings.single().contains("512"))
        val kept = ChessDocumentReader.readArchive(zip("a.pgn" to byteArrayOf(65), "b.txt" to byteArrayOf(66)), { it.endsWith(".pgn") }) { }
        assertEquals(listOf("a.pgn"), kept.keys.toList())
        try { ChessDocumentReader.readArchive(zip("../outside.pgn" to byteArrayOf(65)), { true }) { }; fail("Unsafe path accepted") }
        catch (e: IllegalArgumentException) { assertTrue(e.message!!.contains("path")) }
    }
}
