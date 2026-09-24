package com.eval.ui

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.eval.MainActivity
import com.eval.chess.PgnParser
import com.eval.data.ChessDocumentReader
import com.eval.data.ClipboardHistory
import com.eval.data.SharedChessInput
import com.eval.data.WebChessKind
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.MediaType.Companion.toMediaType
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(AndroidJUnit4::class)
class DocumentImportTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val fen = "4k3/8/8/8/8/8/8/4K3 b - - 0 1"
    private val placement = "r1bqk1nr/pppp1ppp/2n5/2b1p3/2B1P3/5N2/PPPP1PPP/RNBQK2R"
    private fun fixture(name: String) = instrumentation.context.assets.open("documents/$name").use { it.readBytes() }
    private fun directory() = File(context.cacheDir, "settings_export/document-test-${UUID.randomUUID()}").apply { mkdirs() }
    private fun uri(file: File) = FileProvider.getUriForFile(context, "com.eval.fileprovider", file)
    private suspend fun UrlGameScanner.settled(): UrlScanState {
        withTimeout(120_000) { while (uiState.value.busy) delay(40) }
        return uiState.value.also { assertNull(it.toString(), it.error) }
    }
    private fun assertChess(state: UrlScanState) {
        assertTrue(state.toString(), state.results.any { it.kind == WebChessKind.FEN && it.content == fen })
        assertTrue(state.toString(), state.results.any { it.kind == WebChessKind.PGN &&
            PgnParser.parseHeaders(it.content)["White"] == "Alice" && PgnParser.parseMoves(it.content) == listOf("e4", "e5", "Nf3") })
    }

    @Test fun imports_pdf_word_rtf_open_document_epub_spreadsheets_slides_and_zip_with_text_and_boards() = runBlocking {
        val directory = directory()
        try {
            withContext(Dispatchers.Main) {
                val scanner = UrlGameScanner(context, this)
                try {
                    for (extension in listOf("docx", "pdf", "rtf", "odt", "epub", "xlsx", "pptx", "zip")) {
                        val file = File(directory, "chess.$extension").apply { writeBytes(fixture("chess.$extension")) }
                        scanner.openLocalFile(uri(file))
                        val state = scanner.settled()
                        assertChess(state)
                        assertTrue("$extension: ${state.warnings}", state.warnings.isEmpty())
                        if (extension in setOf("docx", "pdf", "rtf")) {
                            val board = state.results.single { it.kind == WebChessKind.IMAGE }
                            assertEquals(extension, "$placement w - - 0 1", board.content)
                            assertTrue(board.needsReview)
                        }
                    }
                } finally { scanner.close() }
            }
        } finally { directory.deleteRecursively() }
    }

    @Test fun document_detection_works_for_generic_url_downloads_shares_and_saved_clipboard_files() = runBlocking {
        val directory = directory()
        val history = ClipboardHistory(context, File(directory, "history"))
        val document = fixture("chess.xlsx")
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("Fixture")
                .body(document.toResponseBody("application/octet-stream".toMediaType())).build()
        }.build()
        try {
            val file = File(directory, "unknown.bin").apply { writeBytes(document) }
            history.record(ClipData.newUri(context.contentResolver, "Spreadsheet", uri(file))).await()
            withContext(Dispatchers.Main) {
                val scanner = UrlGameScanner(context, this, client)
                try {
                    scanner.open("https://fixture.test/download")
                    assertChess(scanner.settled())
                    assertFalse(scanner.uiState.value.hasPage)
                    scanner.openShared(SharedChessInput(streams = listOf(uri(file))))
                    assertChess(scanner.settled())
                    assertTrue(file.delete())
                    scanner.openClipboard(history.uiState.value.entries.single().input)
                    assertChess(scanner.settled())
                } finally { scanner.close() }
            }
        } finally { history.close(); directory.deleteRecursively() }
    }

    private fun zip(vararg entries: Pair<String, ByteArray>): ByteArray = ByteArrayOutputStream().use { output ->
        ZipOutputStream(output).use { zip ->
            for ((name, data) in entries) { zip.putNextEntry(ZipEntry(name)); zip.write(data); zip.closeEntry() }
        }
        output.toByteArray()
    }

    @Test fun bounds_archives_rejects_external_xml_entities_and_retains_earlier_parts() = runBlocking {
        val texts = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        suspend fun read(data: ByteArray) {
            ChessDocumentReader(context, { value, _, _ -> texts += value }, { _, _, _ -> }, warnings::add)
                .read(data, "application/octet-stream", "archive.zip")
        }
        val bomb = zip("position.fen" to fen.toByteArray(), "huge.txt" to ByteArray(9_000_000) { 65 })
        read(bomb)
        assertTrue(texts.contains(fen))
        assertTrue(warnings.toString(), warnings.any { it.contains("8 MB") })
        texts.clear(); warnings.clear()
        val xml = """<?xml version="1.0"?><!DOCTYPE w:document [<!ENTITY stolen SYSTEM "file:///data/data/com.eval/shared_prefs/eval_prefs.xml">]><w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body><w:p><w:r><w:t>&stolen;</w:t></w:r></w:p></w:body></w:document>"""
        read(zip("position.fen" to fen.toByteArray(), "word/document.xml" to xml.toByteArray()))
        assertTrue(warnings.toString(), warnings.any { it.contains("word/document.xml") })
        assertTrue(texts.contains(fen))
        try { read(zip("../outside.txt" to fen.toByteArray())); fail("Unsafe path accepted") }
        catch (e: Exception) {
            // Android 14+ rejects traversal inside ZipInputStream before our own path check.
            assertTrue(e.toString(), e is IllegalArgumentException || e is java.util.zip.ZipException)
            assertTrue(e.toString(), e.message!!.contains("path"))
        }
    }

    @Test fun password_protected_legacy_damaged_and_cancelled_documents_have_clear_results() = runBlocking {
        val directory = directory()
        try {
            withContext(Dispatchers.Main) {
                val scanner = UrlGameScanner(context, this)
                try {
                    val inputs = listOf(
                        Triple("locked.pdf", fixture("locked.pdf"), "password"),
                        Triple("old.doc", byteArrayOf(0xd0.toByte(), 0xcf.toByte(), 0x11, 0xe0.toByte(), 0xa1.toByte(), 0xb1.toByte(), 0x1a, 0xe1.toByte()), "Older binary Office"),
                        Triple("bad.docx", "Not a document".toByteArray(), "damaged")
                    )
                    for ((name, bytes, warning) in inputs) {
                        scanner.openShared(SharedChessInput(texts = listOf(fen), streams = listOf(uri(File(directory, name).apply { writeBytes(bytes) }))))
                        val state = scanner.settled()
                        assertEquals(fen, state.results.single().content)
                        assertTrue(state.warnings.toString(), state.warnings.any { it.contains(warning) })
                    }
                    val pdf = File(directory, "chess.pdf").apply { writeBytes(fixture("chess.pdf")) }
                    scanner.openLocalFile(uri(pdf))
                    scanner.cancel()
                    delay(500)
                    assertFalse(scanner.uiState.value.busy)
                    assertTrue(scanner.uiState.value.results.isEmpty())
                } finally { scanner.close() }
            }
        } finally { directory.deleteRecursively() }
    }

    @Test fun android_offers_eval_for_document_shares() {
        for (type in listOf("application/pdf", "application/rtf", "application/zip", "application/epub+zip",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "application/vnd.oasis.opendocument.text")) {
            for (action in listOf(Intent.ACTION_SEND, Intent.ACTION_SEND_MULTIPLE)) {
                @Suppress("DEPRECATION")
                val activities = context.packageManager.queryIntentActivities(Intent(action).setType(type).setPackage(context.packageName), PackageManager.MATCH_DEFAULT_ONLY)
                assertTrue(type, activities.any { it.activityInfo.name == MainActivity::class.java.name })
            }
        }
    }
}
