package com.eval.data

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Small RTF reader for visible text and PNG/JPEG pictures; ignores formatting and embedded objects. */
internal data class RtfChessText(val text: String, val images: List<Pair<ByteArray, String>>, val warnings: List<String>) {
    companion object {
        private data class Picture(val hex: StringBuilder = StringBuilder(), var type: String = "")
        private data class Group(var skip: Boolean = false, var unicodeFallback: Int = 1,
            var picture: Picture? = null, var ownsPicture: Boolean = false, var ignorable: Boolean = false)

        suspend fun read(input: String): RtfChessText {
            val output = StringBuilder()
            val images = mutableListOf<Pair<ByteArray, String>>()
            val warnings = linkedSetOf<String>()
            val stack = ArrayDeque<Group>()
            var group = Group()
            var fallback = 0
            var position = 0
            val coroutine = currentCoroutineContext()
            fun append(value: Char) {
                if (fallback > 0) { fallback--; return }
                if (!group.skip && output.length < SharedChessInput.MAX_TEXT) output.append(value)
            }
            while (position < input.length) {
                if (position % 1024 == 0) coroutine.ensureActive()
                when (val c = input[position++]) {
                    '{' -> {
                        require(stack.size < 512) { "RTF nesting is too deep." }
                        stack.addLast(group)
                        group = group.copy(ownsPicture = false, ignorable = false)
                    }
                    '}' -> {
                        if (group.ownsPicture) {
                            val picture = group.picture!!
                            if (picture.type.isNotEmpty() && picture.hex.isNotEmpty() && picture.hex.length <= 8_000_000 && images.size < 20) {
                                val bytes = ByteArray(picture.hex.length / 2) { i ->
                                    ((picture.hex[i * 2].digitToInt(16) shl 4) + picture.hex[i * 2 + 1].digitToInt(16)).toByte()
                                }
                                images += bytes to picture.type
                            } else warnings += "Some RTF pictures were skipped (only PNG/JPEG, up to 4 MB each and 20 images)."
                        }
                        require(stack.isNotEmpty()) { "This RTF document is damaged." }
                        group = stack.removeLast()
                        fallback = 0
                    }
                    '\\' -> {
                        if (position >= input.length) break
                        val symbol = input[position]
                        if (symbol in "\\{}") { position++; append(symbol); continue }
                        if (symbol == '\'') {
                            if (position + 2 < input.length) {
                                val value = input.substring(position + 1, position + 3).toIntOrNull(16)
                                value?.let { append(byteArrayOf(it.toByte()).toString(charset("windows-1252"))[0]) }
                            }
                            position = minOf(input.length, position + 3)
                            continue
                        }
                        if (!symbol.isLetter()) {
                            position++
                            when (symbol) { '*' -> group.ignorable = true; '~' -> append(' '); '_' -> append('-') }
                            continue
                        }
                        val start = position
                        while (position < input.length && input[position].isLetter()) position++
                        val word = input.substring(start, position)
                        val numberStart = position
                        if (position < input.length && input[position] == '-') position++
                        while (position < input.length && input[position].isDigit()) position++
                        val number = input.substring(numberStart, position).toIntOrNull()
                        if (position < input.length && input[position] == ' ') position++
                        if (group.ignorable && word !in setOf("pict", "shppict")) group.skip = true
                        group.ignorable = false
                        when (word) {
                            "fonttbl", "colortbl", "stylesheet", "info", "object", "fldinst", "datastore", "themedata", "nonshppict" -> group.skip = true
                            "uc" -> group.unicodeFallback = (number ?: 1).coerceIn(0, 16)
                            "u" -> {
                                fallback = 0
                                number?.let { append((it and 0xffff).toChar()) }
                                fallback = group.unicodeFallback
                            }
                            "par", "line", "row" -> append('\n')
                            "tab", "cell" -> append(' ')
                            "pict" -> if (!group.skip) { group.picture = Picture(); group.ownsPicture = true; group.skip = true }
                            "pngblip" -> group.picture?.type = "image/png"
                            "jpegblip" -> group.picture?.type = "image/jpeg"
                            "bin" -> {
                                position = (position.toLong() + (number ?: 0).coerceAtLeast(0)).coerceAtMost(input.length.toLong()).toInt()
                                warnings += "RTF binary objects are not scanned. Save as PDF or DOCX to include them."
                            }
                        }
                    }
                    '\r', '\n' -> Unit
                    else -> {
                        val picture = group.picture
                        if (picture != null && c.digitToIntOrNull(16) != null) {
                            if (picture.hex.length <= 8_000_000) picture.hex.append(c)
                        } else append(c)
                    }
                }
            }
            require(stack.isEmpty()) { "This RTF document is incomplete." }
            if (output.length >= SharedChessInput.MAX_TEXT) warnings += "Document text was limited to 2 MB."
            return RtfChessText(output.toString(), images, warnings.toList())
        }
    }
}
