package com.eval.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class RtfChessTextTest {
    private val fen = "4k3/8/8/8/8/8/8/4K3 b - - 0 1"
    // A 1x1 PNG.
    private val png = "89504e470d0a1a0a0000000d4948445200000001000000010806000000" +
        "1f15c4890000000d4944415478da63f8cfc0f01f0005000201a5f6e2a30000000049454e44ae426082"
    private fun bytes(hex: String) = ByteArray(hex.length / 2) { hex.substring(it * 2, it * 2 + 2).toInt(16).toByte() }

    @Test fun word_pictures_with_nested_property_groups_keep_their_image_bytes() = runBlocking {
        // Word writes shape properties and a hex blip UID inside \pict, and a WMF copy for old readers.
        val rtf = """{\rtf1\ansi\ansicpg1252\deff0{\fonttbl{\f0\fswiss Calibri;}}{\*\generator Msftedit 5.41.21.2510;}\viewkind4\uc1
            |\pard Position: $fen\par
            |{\*\shppict{\pict{\*\picprop\shplid1025{\sp{\sn shapeType}{\sv 75}}{\sp{\sn fFlipH}{\sv 0}}{\sp{\sn fLayoutInCell}{\sv 1}}}
            |\picscalex100\picscaley100\piccropl0\piccropr0\piccropt0\piccropb0\picw26\pich26\picwgoal15\pichgoal15\pngblip\bliptag-1283416386{\*\blipuid b381e8be5d1d8c2cd4c3b7bda6a8fbe1}
            |${png.chunked(40).joinToString("\r\n")}}}{\nonshppict{\pict\picscalex100\picscaley100\picw26\pich26\picwgoal15\pichgoal15\wmetafile8\bliptag-1283416386
            |0100090000035000000000002700000000000400000003010800050000000b0200000000}}
            |\par}""".trimMargin()
        val result = RtfChessText.read(rtf)
        val image = result.images.single()
        assertEquals("image/png", image.second)
        assertArrayEquals(bytes(png), image.first)
        assertTrue(result.text, result.text.contains(fen))
        assertTrue(result.warnings.toString(), result.warnings.isEmpty())
    }

    @Test fun escaped_bytes_use_the_document_code_page() = runBlocking {
        assertEquals("Réti", RtfChessText.read("""{\rtf1\ansi R\'e9ti}""").text)
        assertEquals("Привет", RtfChessText.read("""{\rtf1\ansi\ansicpg1251 \'cf\'f0\'e8\'e2\'e5\'f2}""").text)
        // Double-byte code pages split one character over two escapes, even across a line break.
        assertEquals("あい", RtfChessText.read("{\\rtf1\\ansi\\ansicpg932 \\'82\\'a0\\'82\r\n\\'a2}").text)
        // Unicode characters skip their code page fallback, counted in bytes.
        assertEquals("€ and あ", RtfChessText.read("{\\rtf1\\ansi\\ansicpg932\\uc1\\u8364\\'80 and \\uc2\\u12354\\'82\\'a0}").text)
    }
}
