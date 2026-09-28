package com.eval.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Xml
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.*
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.Writer
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.util.zip.ZipException
import java.util.zip.ZipInputStream

/** Extracts inert document content; embedded programs/macros and external XML entities never run. */
internal class ChessDocumentReader(
    private val context: Context,
    private val onText: suspend (String, Boolean, String) -> Unit,
    private val onImage: suspend (ByteArray, String, String) -> Unit,
    private val onWarning: suspend (String) -> Unit
) {
    private var textRemaining = SharedChessInput.MAX_TEXT
    private var imageCount = 0

    suspend fun read(data: ByteArray, type: String, name: String) = withContext(Dispatchers.IO) {
        when {
            isPdf(data) -> pdf(data, name)
            isZip(data) -> archive(data, name)
            isRtf(data) -> {
                val rtf = RtfChessText.read(data.toString(Charsets.ISO_8859_1))
                text(rtf.text, false, name)
                for (picture in rtf.images) image(picture.first, picture.second, "$name · image")
                for (warning in rtf.warnings) onWarning(warning)
            }
            isOle(data) -> error("Older binary Office files (.doc, .xls, .ppt) and encrypted Office files cannot be read. Save an unlocked .docx, .xlsx, .pptx or PDF copy and choose it again.")
            type.startsWith("image/") -> image(data, type, name)
            extension(name) in setOf("pdf", "docx", "docm", "odt", "ods", "odp", "pptx", "pptm", "xlsx", "xlsm", "epub", "zip") ->
                error("This document is damaged or is not in the expected format.")
            else -> text(decodeText(data), type.contains("html") || extension(name) in setOf("html", "htm", "xhtml", "xml"), name)
        }
    }

    private suspend fun text(value: String, html: Boolean, source: String) {
        currentCoroutineContext().ensureActive()
        if (value.length > textRemaining) onWarning("Document text was limited to 2 MB.")
        val part = value.take(textRemaining)
        textRemaining -= part.length
        if (part.isNotBlank()) onText(part, html, source)
    }

    private suspend fun image(data: ByteArray, type: String, source: String) {
        currentCoroutineContext().ensureActive()
        if (imageCount >= 20) { onWarning("Scanned the first 20 document images/pages."); return }
        imageCount++
        try { onImage(data, type, source) }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { onWarning("Could not scan an image in $source.") }
        catch (_: OutOfMemoryError) { onWarning("An image in $source is too large to scan on this device.") }
    }

    private suspend fun pdf(data: ByteArray, name: String) {
        PDFBoxResourceLoader.init(context.applicationContext)
        val coroutine = currentCoroutineContext()
        try {
            val memory = MemoryUsageSetting.setupMixed(4_000_000).apply { tempDir = context.cacheDir }
            PDDocument.load(data.inputStream(), memory).use { document ->
                require(document.currentAccessPermission.canExtractContent()) {
                    "This PDF does not allow content extraction. Export an unlocked copy and choose it again."
                }
                val buffer = StringBuilder()
                val writer = object : Writer() {
                    override fun write(chars: CharArray, off: Int, len: Int) {
                        coroutine.ensureActive()
                        val count = minOf(len, textRemaining - buffer.length)
                        buffer.append(chars, off, count)
                        if (count < len) throw TextLimit()
                    }
                    override fun flush() = Unit
                    override fun close() = Unit
                }
                val stripper = PDFTextStripper().apply { startPage = 1; endPage = 100; sortByPosition = true }
                try { stripper.writeText(document, writer) }
                catch (_: TextLimit) { onWarning("Document text was limited to 2 MB.") }
                text(buffer.toString(), false, name)
                if (document.numberOfPages > 100) onWarning("Read text from the first 100 PDF pages.")
            }
        } catch (_: InvalidPasswordException) {
            error("This PDF needs a password. Export an unlocked copy and choose it again.")
        }
        // Native rendering also finds scanned pages and boards made of vector shapes/chess fonts.
        val file = File.createTempFile("chess-document-", ".pdf", context.cacheDir)
        try {
            file.writeBytes(data)
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                PdfRenderer(descriptor).use { renderer ->
                    if (renderer.pageCount > 20) onWarning("Scanned board images on the first 20 PDF pages.")
                    for (pageIndex in 0 until minOf(renderer.pageCount, 20)) {
                        coroutine.ensureActive()
                        val bytes = renderer.openPage(pageIndex).use { page ->
                            val scale = 1600f / maxOf(page.width, page.height).coerceAtLeast(1)
                            val bitmap = Bitmap.createBitmap((page.width * scale).toInt().coerceAtLeast(1),
                                (page.height * scale).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
                            try {
                                bitmap.eraseColor(Color.WHITE)
                                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                ByteArrayOutputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it); it.toByteArray() }
                            } finally { bitmap.recycle() }
                        }
                        image(bytes, "image/png", "$name · page ${pageIndex + 1}")
                    }
                }
            }
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { onWarning("PDF text was read, but some pages could not be rendered for board recognition.") }
        catch (_: OutOfMemoryError) { onWarning("PDF text was read, but some pages could not be rendered for board recognition.") }
        finally { file.delete() }
    }

    private suspend fun archive(data: ByteArray, name: String) {
        val parts = readArchive(data, ::interesting, onWarning)
        val office = parts.keys.any { it.startsWith("word/") || it.startsWith("ppt/") || it.startsWith("xl/") || it == "content.xml" }
        for ((path, bytes) in parts.entries.sortedBy { extension(it.key) in imageExtensions }) {
            currentCoroutineContext().ensureActive()
            val source = "$name · $path"
            val ext = extension(path)
            try {
                when {
                    ext in imageExtensions -> image(bytes, imageType(ext), source)
                    path.endsWith(".rels") -> text(xmlText(bytes, relationships = true), false, source)
                    office && ext == "xml" -> {
                        text(xmlText(bytes), false, source)
                    }
                    ext in setOf("html", "htm", "xhtml") -> text(decodeText(bytes), true, source)
                    ext in textExtensions -> text(decodeText(bytes), ext == "xml", source)
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { onWarning("Could not read $source.") }
        }
        if (parts.isEmpty()) onWarning("No supported text or images found in this archive. Nested archives and embedded Office/PDF attachments are not scanned.")
    }

    private fun interesting(path: String): Boolean {
        val lower = path.lowercase()
        if (lower.startsWith("word/")) return lower.matches(Regex("word/(document|header[0-9]*|footer[0-9]*|footnotes|endnotes|comments)\\.xml")) ||
            lower.startsWith("word/media/") || lower.endsWith(".rels")
        if (lower.startsWith("ppt/")) return lower.matches(Regex("ppt/(slides/slide|notesslides/notesslide)[0-9]+\\.xml")) ||
            lower.startsWith("ppt/media/") || lower.endsWith(".rels")
        if (lower.startsWith("xl/")) return lower == "xl/sharedstrings.xml" || lower.startsWith("xl/worksheets/") ||
            lower.startsWith("xl/media/") || lower.endsWith(".rels")
        if (lower == "content.xml") return true
        if (lower in setOf("styles.xml", "meta.xml", "settings.xml") || lower.startsWith("meta-inf/")) return false
        return extension(lower) in imageExtensions + textExtensions + setOf("html", "htm", "xhtml")
    }

    private suspend fun xmlText(bytes: ByteArray, relationships: Boolean = false): String {
        val parser = Xml.newPullParser().apply {
            setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
            setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL, false)
            setInput(bytes.inputStream(), null)
        }
        val output = StringBuilder()
        val links = linkedSetOf<String>()
        var textDepth = 0
        val coroutine = currentCoroutineContext()
        while (parser.eventType != XmlPullParser.END_DOCUMENT && output.length < textRemaining) {
            coroutine.ensureActive()
            when (parser.eventType) {
                XmlPullParser.DOCDECL -> error("XML document declarations are not supported.")
                XmlPullParser.START_TAG -> {
                    val tag = parser.name
                    if (tag == "t" || (parser.namespace.startsWith("urn:oasis:names:tc:opendocument:xmlns:text") && tag in setOf("p", "h", "span"))) textDepth++
                    if (tag in setOf("br", "line-break", "tab", "s")) output.append(if (tag == "br" || tag == "line-break") '\n' else ' ')
                    for (i in 0 until parser.attributeCount) {
                        val value = parser.getAttributeValue(i)
                        if (parser.getAttributeName(i) in setOf("href", "Target") && value.startsWith("https://")) {
                            if (relationships) output.append(value).append('\n') else links += value
                        }
                    }
                }
                XmlPullParser.TEXT, XmlPullParser.CDSECT, XmlPullParser.ENTITY_REF -> if (!relationships && textDepth > 0)
                    output.append(parser.text.orEmpty().take(textRemaining - output.length))
                XmlPullParser.END_TAG -> {
                    val tag = parser.name
                    if (tag == "t" || (parser.namespace.startsWith("urn:oasis:names:tc:opendocument:xmlns:text") && tag in setOf("p", "h", "span"))) textDepth--
                    if (tag in setOf("p", "h", "si", "row", "tr", "table-row", "table-cell", "tc")) output.append('\n')
                }
            }
            parser.nextToken()
        }
        if (output.length >= textRemaining) onWarning("Document text was limited to 2 MB.")
        return if (links.isEmpty()) output.toString() else output.toString() + "\n" + links.take(13).joinToString("\n")
    }

    private class TextLimit : IOException()

    companion object {
        private const val MAX_FILE_BYTES = 16_000_000
        private val windows1252 = charset("windows-1252")
        private val imageExtensions = setOf("png", "jpg", "jpeg", "webp", "gif", "bmp", "svg")
        private val textExtensions = setOf("txt", "fen", "pgn", "md", "csv", "tsv", "json", "xml", "log")
        private fun imageType(ext: String) = if (ext == "svg") "image/svg+xml" else if (ext == "jpg") "image/jpeg" else "image/$ext"
        private fun extension(name: String) = name.substringBefore('?').substringBefore('#').substringAfterLast('.').lowercase()
        private fun isPdf(data: ByteArray) = data.take(1024).toByteArray().toString(Charsets.ISO_8859_1).contains("%PDF-")
        private fun isZip(data: ByteArray) = data.size >= 4 && data[0] == 0x50.toByte() && data[1] == 0x4b.toByte()
        private fun isRtf(data: ByteArray) = data.take(12).toByteArray().toString(Charsets.ISO_8859_1).trimStart().startsWith("{\\rtf")
        private fun isOle(data: ByteArray) = data.take(8).toByteArray().contentEquals(byteArrayOf(0xd0.toByte(), 0xcf.toByte(), 0x11, 0xe0.toByte(), 0xa1.toByte(), 0xb1.toByte(), 0x1a, 0xe1.toByte()))
        fun isDocument(data: ByteArray, type: String, name: String) = isPdf(data) || isZip(data) || isRtf(data) || isOle(data) ||
            type.contains("pdf") || extension(name) in setOf("pdf", "docx", "docm", "doc", "odt", "ods", "odp", "rtf", "epub", "pptx", "pptm", "ppt", "xlsx", "xlsm", "xls", "zip")
        fun decodeText(data: ByteArray): String = decodeBytes(data).also { text ->
            require(text.take(4096).none { it == '\u0000' }) { "This file is not a supported text, document or image file." }
        }

        /**
         * UTF-16 with a byte-order mark, otherwise strict UTF-8. Malformed UTF-8 falls back to windows-1252,
         * a superset of the ISO-8859-1 that the PGN standard specifies and older databases use.
         */
        fun decodeBytes(data: ByteArray): String {
            val utf16 = data.size >= 2 && ((data[0] == 0xff.toByte() && data[1] == 0xfe.toByte()) ||
                (data[0] == 0xfe.toByte() && data[1] == 0xff.toByte()))
            val text = if (utf16) data.toString(Charsets.UTF_16) else try {
                Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(data)).toString()
            } catch (_: CharacterCodingException) { data.toString(windows1252) }
            return text.removePrefix("\uFEFF")
        }

        /**
         * Bounded ZIP expansion: 512 entries, 32 MB expanded in total and 8 MB per kept part; unsafe paths are
         * rejected. An oversized part is skipped and later parts are still read while the total budget remains.
         */
        suspend fun readArchive(data: ByteArray, keep: (String) -> Boolean, onWarning: suspend (String) -> Unit): Map<String, ByteArray> {
            val parts = linkedMapOf<String, ByteArray>()
            var expanded = 0L
            var count = 0
            val coroutine = currentCoroutineContext()
            ZipInputStream(data.inputStream()).use { zip ->
                entries@ while (true) {
                    coroutine.ensureActive()
                    val entry = zip.nextEntry ?: break
                    if (++count > 512) { onWarning("Read the first 512 document/archive entries."); break }
                    val path = entry.name.replace('\\', '/')
                    require(!path.startsWith('/') && path.split('/').none { it == ".." }) { "This archive contains an unsafe file path." }
                    var output = if (!entry.isDirectory && keep(path)) ByteArrayOutputStream() else null
                    val buffer = ByteArray(16_384)
                    while (true) {
                        coroutine.ensureActive()
                        val length = zip.read(buffer)
                        if (length < 0) break
                        expanded += length
                        // Stop without draining the rest of an oversized compressed archive.
                        if (expanded > 32_000_000) {
                            onWarning("Expanded document content was limited to 32 MB; later parts were skipped.")
                            break@entries
                        }
                        if (output != null && output.size() + length > 8_000_000) {
                            onWarning("An archive part exceeds 8 MB and was skipped: $path")
                            output = null
                        }
                        output?.write(buffer, 0, length)
                    }
                    output?.let { parts[path] = it.toByteArray() }
                }
            }
            require(count > 0) { "This archive is empty or damaged." }
            return parts
        }

        /**
         * Reads a PGN file, or every .pgn file in a ZIP archive (joined by blank lines), for the PGN game list.
         * Runs on the IO dispatcher and is cancellable. Files are limited to 16 MB and archives to the limits of
         * [readArchive]; skipped archive parts are reported through [onWarning], on the IO dispatcher. Text is decoded
         * like [decodeText].
         * @throws IOException with a message that can be shown to the user when nothing readable was found.
         */
        suspend fun readPgnFile(context: Context, uri: Uri, onWarning: (String) -> Unit = {}): String = withContext(Dispatchers.IO) {
            try {
                val coroutine = currentCoroutineContext()
                val data = context.contentResolver.openInputStream(uri)?.use { stream ->
                    val output = ByteArrayOutputStream()
                    val buffer = ByteArray(16_384)
                    while (true) {
                        coroutine.ensureActive()
                        val count = stream.read(buffer)
                        if (count < 0) break
                        require(output.size() + count <= MAX_FILE_BYTES) { "This file is too large (limit 16 MB)." }
                        output.write(buffer, 0, count)
                    }
                    output.toByteArray()
                } ?: throw IllegalArgumentException("This file could not be opened. Choose it again.")
                val text = if (isZip(data)) {
                    val parts = readArchive(data, { it.lowercase().endsWith(".pgn") }) { onWarning(it) }
                    require(parts.isNotEmpty()) { "This ZIP archive contains no .pgn files." }
                    parts.values.joinToString("\n\n") { decodeText(it) }
                } else decodeText(data)
                require(text.isNotBlank()) { "This PGN file is empty." }
                text
            } catch (e: CancellationException) { throw e }
            catch (e: IllegalArgumentException) { throw IOException(e.message, e) }
            catch (e: ZipException) { throw IOException("This ZIP archive is damaged or contains an unsafe file path.", e) }
            catch (e: OutOfMemoryError) { throw IOException("This file is too large to read on this device.", e) }
            catch (e: Exception) { throw IOException("This file could not be read. Choose it again.", e) }
        }
    }
}
