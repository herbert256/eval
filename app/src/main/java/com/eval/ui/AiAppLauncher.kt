package com.eval.ui

import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun String.htmlEscape(): String = buildString(length) {
    for (c in this@htmlEscape) when (c) {
        '&' -> append("&amp;")
        '<' -> append("&lt;")
        '>' -> append("&gt;")
        '"' -> append("&quot;")
        '\'' -> append("&#39;")
        else -> append(c)
    }
}

private fun jsString(value: String): String = JSONObject.quote(value)

/**
 * Utility object for launching the external AI app for report generation.
 * The AI app handles all API calls and agent configuration.
 */
object AiAppLauncher {

    private const val AI_APP_ACTION = "com.ai.ACTION_NEW_REPORT"
    private const val AI_APP_PACKAGE = "com.ai"

    // Optional SHA-256 fingerprint (colon-separated, uppercase) of the com.ai signing
    // certificate. Leave empty to trust any installation. When set, the launcher
    // refuses to send data to a package whose signer does not match.
    // To obtain the fingerprint, run the app once with this empty and copy the value
    // logged as "observed com.ai signature: ...".
    private const val AI_APP_SIGNATURE_SHA256 = ""

    private fun observedSignatureSha256(context: Context): String? {
        return try {
            val pm = context.packageManager
            @Suppress("DEPRECATION")
            val signatures = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                val info = pm.getPackageInfo(AI_APP_PACKAGE, android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES)
                info.signingInfo?.let {
                    if (it.hasMultipleSigners()) it.apkContentsSigners else it.signingCertificateHistory
                } ?: emptyArray()
            } else {
                pm.getPackageInfo(AI_APP_PACKAGE, android.content.pm.PackageManager.GET_SIGNATURES).signatures
                    ?: emptyArray()
            }
            if (signatures.isEmpty()) return null
            val digest = java.security.MessageDigest.getInstance("SHA-256").digest(signatures[0].toByteArray())
            digest.joinToString(":") { "%02X".format(it) }
        } catch (e: Exception) {
            Log.w("AiAppLauncher", "failed to read signature: ${e.message}")
            null
        }
    }

    private fun isSignerTrusted(context: Context): Boolean {
        val observed = observedSignatureSha256(context)
        if (observed != null) Log.i("AiAppLauncher", "observed com.ai signature: $observed")
        if (AI_APP_SIGNATURE_SHA256.isBlank()) return true
        return observed != null && observed.equals(AI_APP_SIGNATURE_SHA256.trim(), ignoreCase = true)
    }

    /**
     * Check if the AI app is installed.
     */
    fun isAiAppInstalled(context: Context): Boolean {
        val intent = Intent(AI_APP_ACTION).apply {
            setPackage(AI_APP_PACKAGE)
        }
        return intent.resolveActivity(context.packageManager) != null
    }

    /** Send only named instructions and context; the AI app owns both kinds of prompt. */
    fun launchAiReport(
        context: Context,
        entry: AiInstructionEntry,
        reportContext: AiReportContext
    ): Boolean {
        val intent = Intent(AI_APP_ACTION).apply {
            setPackage(AI_APP_PACKAGE)
            putExtra("title", reportContext.title)
            putExtra("instructions", buildInstructions(entry.instructions, reportContext))
        }
        if (intent.resolveActivity(context.packageManager) == null) {
            Toast.makeText(context, "AI app not installed", Toast.LENGTH_SHORT).show()
            return false
        }
        if (!isSignerTrusted(context)) {
            Toast.makeText(context, "AI app signature mismatch", Toast.LENGTH_LONG).show()
            return false
        }
        context.startActivity(intent)
        return true
    }

    fun gameContext(
        fen: String,
        whiteName: String = "",
        blackName: String = "",
        server: String = "",
        pgn: String = "",
        currentMoveIndex: Int = -1,
        lastMoveDetails: MoveDetails? = null
    ): AiReportContext {
        val color = if (fen.split(" ").getOrNull(1) == "b") "Black" else "White"
        return AiReportContext(
            title = if (whiteName.isNotBlank() && blackName.isNotBlank())
                "Game Analysis: $whiteName vs $blackName" else "Chess Position Analysis",
            fen = fen, color = color, server = server,
            player = if (color == "White") whiteName else blackName,
            pgn = pgn,
            board = generateBoardHtml(fen, whiteName, blackName, currentMoveIndex, lastMoveDetails)
        )
    }

    private val commandTags = setOf("system", "prompt", "parameters", "agent", "flock", "swarm",
        "open", "close", "next", "email", "select", "return")
    private val entryBlocks = Regex("<([A-Za-z_][A-Za-z0-9_.:-]*)>(.*?)</\\1>",
        setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
    private val placeholders = Regex("@([A-Za-z_][A-Za-z0-9_.:-]*)@")
    private val wrapper = Regex("^\\s*<instructions>(.*?)</instructions>\\s*$",
        setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
    private val obsoleteFlags = Regex("</?(?:default|model|edit|type)>", RegexOption.IGNORE_CASE)

    private data class InstructionParts(val template: String, val data: Map<String, String>, val wrapped: Boolean) {
        val usedNames: Set<String> get() = placeholders.findAll(template)
            .map { it.groupValues[1].lowercase(Locale.US) }.toSet()
    }

    private fun splitInstructions(instructions: String): InstructionParts {
        val wrapped = wrapper.matchEntire(instructions)
        val body = wrapped?.groupValues?.get(1) ?: instructions
        val data = linkedMapOf<String, String>()
        val template = buildString {
            var end = 0
            entryBlocks.findAll(body).forEach { entry ->
                // Only remove obsolete standalone flags outside value bodies.
                append(obsoleteFlags.replace(body.substring(end, entry.range.first), ""))
                val tag = entry.groupValues[1].lowercase(Locale.US)
                if (tag in commandTags) append(entry.value) else data[tag] = entry.groupValues[2]
                end = entry.range.last + 1
            }
            append(obsoleteFlags.replace(body.substring(end), ""))
        }
        return InstructionParts(template, data, wrapped != null)
    }

    /** Data values are literal, so tokens inside those values do not request more data. */
    internal fun usedContextNames(instructions: String): Set<String> = splitInstructions(instructions).usedNames

    /** Send only referenced data fields; the receiving AI app expands the unchanged templates. */
    internal fun buildInstructions(instructions: String, data: AiReportContext): String {
        val parts = splitInstructions(instructions)
        val usedNames = parts.usedNames
        val values = linkedMapOf(
            "fen" to data.fen, "color" to data.color, "server" to data.server,
            "player" to data.player, "pgn" to data.pgn, "board" to data.board,
            "moves" to data.moves, "engine" to data.engine,
            "date" to if ("date" in usedNames) SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) else ""
        )
        val fields = parts.data.filterKeys { it in usedNames && it !in values }.toMutableMap()
        values.filterKeys { it in usedNames }.forEach { (tag, value) ->
            fields[tag] = if (tag == "board") value else value.htmlEscape()
        }
        val payload = buildString {
            append(parts.template)
            if (fields.isNotEmpty() && isNotEmpty() && last() != '\n') append('\n')
            for ((tag, value) in fields) append("<$tag>$value</$tag>\n")
        }
        return if (parts.wrapped) "<instructions>$payload</instructions>" else payload
    }

    /**
     * Generate HTML code for an interactive chess board showing the position.
     * Uses chessboard.js with Lichess piece images.
     * Board orientation is set so the side to move is at the bottom.
     * Includes last move info above the board and side-to-move below.
     *
     * @param fen The FEN string of the position
     * @param whiteName White player name
     * @param blackName Black player name
     * @param currentMoveIndex Current move index (0-based, -1 if no move)
     * @param lastMoveDetails Details of the last move played
     * @return HTML code string
     */
    private fun generateBoardHtml(
        fen: String,
        whiteName: String,
        blackName: String,
        currentMoveIndex: Int = -1,
        lastMoveDetails: MoveDetails? = null
    ): String {
        // Determine who is to play from FEN (second field after the position)
        val fenParts = fen.split(" ")
        val toPlay = if (fenParts.size > 1) fenParts[1] else "w"
        val orientation = if (toPlay == "b") "black" else "white"

        // Top player is the opponent of the side to move
        val topPlayer = if (toPlay == "b") whiteName.ifEmpty { "White" } else blackName.ifEmpty { "Black" }
        val bottomPlayer = if (toPlay == "b") blackName.ifEmpty { "Black" } else whiteName.ifEmpty { "White" }

        // Last move info (displayed above the board)
        val lastMoveHtml = if (currentMoveIndex >= 0 && lastMoveDetails != null) {
            val moveNumber = (currentMoveIndex / 2) + 1
            val isWhiteMove = currentMoveIndex % 2 == 0
            val sideText = if (isWhiteMove) "white" else "black"
            val pieceColor = if (isWhiteMove) "w" else "b"
            val pieceCode = "${pieceColor}${lastMoveDetails.pieceType}".htmlEscape()
            val pieceImg = "<img src=\"https://lichess1.org/assets/piece/cburnett/$pieceCode.svg\" style=\"height:20px;vertical-align:text-top;\">"
            val separator = if (lastMoveDetails.isCapture) "x" else "-"
            val dots = if (isWhiteMove) "" else " ....."
            "<div style=\"text-align:center;padding:6px 12px;color:#ccc;font-size:18px;\">" +
                "Last move $sideText: $moveNumber$dots $pieceImg ${lastMoveDetails.from.htmlEscape()} $separator ${lastMoveDetails.to.htmlEscape()}" +
                "</div>"
        } else ""

        // Side to move info (displayed below the board)
        val toMoveText = if (toPlay == "w") "White to move" else "Black to move"
        val toMoveHtml = "<div style=\"text-align:center;padding:6px 12px;color:#ccc;font-size:18px;\">$toMoveText</div>"

        // Compact HTML without extra whitespace
        return "<link rel=\"stylesheet\" href=\"https://unpkg.com/@chrisoakman/chessboardjs@1.0.0/dist/chessboard-1.0.0.min.css\">" +
            "<script src=\"https://code.jquery.com/jquery-3.7.1.min.js\"></script>" +
            "<script src=\"https://unpkg.com/@chrisoakman/chessboardjs@1.0.0/dist/chessboard-1.0.0.min.js\"></script>" +
            "<div style=\"max-width:400px;margin:20px auto;\">" +
            lastMoveHtml +
            "<div style=\"background:#333;color:white;padding:8px 12px;font-weight:bold;text-align:center;\">${topPlayer.htmlEscape()}</div>" +
            "<div id=\"board\" style=\"width:100%;\"></div>" +
            "<div style=\"background:#333;color:white;padding:8px 12px;font-weight:bold;text-align:center;\">${bottomPlayer.htmlEscape()}</div>" +
            toMoveHtml +
            "</div>" +
            "<script>var board=Chessboard('board',{position:${jsString(fen)},orientation:${jsString(orientation)},pieceTheme:'https://lichess1.org/assets/piece/cburnett/{piece}.svg'});</script>"
    }
}
