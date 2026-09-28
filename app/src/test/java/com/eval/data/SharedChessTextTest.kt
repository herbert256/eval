package com.eval.data

import org.junit.Assert.*
import org.junit.Test

class SharedChessTextTest {
    // The former regex, kept as the reference for the linear scanner.
    private val legacyBlocks = Regex("<(pre|code|textarea)\\b[^>]*>(.*?)</\\1\\s*>",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))

    @Test fun code_blocks_match_the_former_regex() {
        val samples = mutableListOf(
            "<p>Game</p><pre>[Event \"A\"]\n1. e4 *</pre><code class=x>fen</code >",
            "<PRE>upper</pre><textarea rows=3>1. d4</TEXTAREA><pre>open", "<preview>not a block</preview><pre>x</pre>",
            "<code>a<code>b</code>c</code>", "<pre\n  data-x='1'>\nmultiline\n</pre\n>", "<pre>unclosed <code>inner</code>")
        val tokens = listOf("<pre>", "</pre>", "<code>", "</code>", "<textarea x=1>", "</textarea >", "<pre", ">", "<", "</",
            "text", " ", "\n", "<prefix>", "</PRE>", "<Code>", "pre", "/")
        val random = java.util.Random(11)
        repeat(3000) { samples += (1..random.nextInt(25)).joinToString("") { tokens[random.nextInt(tokens.size)] } }
        for (html in samples) {
            assertEquals(html, legacyBlocks.findAll(html).take(100).map { it.groupValues[2] }.toList(), SharedChessText.codeBlocks(html))
        }
    }

    @Test fun unclosed_blocks_are_scanned_in_linear_time() {
        for (unit in listOf("<pre>", "<code x", "<textarea>a</pre>", "<pre><code>")) {
            val html = unit.repeat(2_000_000 / unit.length)
            val started = System.nanoTime()
            SharedChessText.codeBlocks(html)
            val millis = (System.nanoTime() - started) / 1_000_000
            assertTrue("$unit: $millis ms", millis < 1_000)
        }
    }
}
