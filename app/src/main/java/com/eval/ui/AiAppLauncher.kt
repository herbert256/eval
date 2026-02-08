package com.eval.ui

import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Utility object for launching the external AI app for report generation.
 * The AI app handles all API calls and agent configuration.
 */
object AiAppLauncher {

    private const val AI_APP_ACTION = "com.ai.ACTION_NEW_REPORT"
    private const val AI_APP_PACKAGE = "com.ai"

    /**
     * Check if the AI app is installed.
     */
    fun isAiAppInstalled(context: Context): Boolean {
        val intent = Intent(AI_APP_ACTION).apply {
            setPackage(AI_APP_PACKAGE)
        }
        return intent.resolveActivity(context.packageManager) != null
    }

    /**
     * Launch the AI app with a prompt for report generation.
     *
     * @param context Android context
     * @param title Report title (optional, appears in report header)
     * @param system System prompt for AI models (optional)
     * @param prompt The prompt to send to AI agents
     * @param instructions Control tags for report behavior (optional)
     * @return true if launched successfully, false if AI app not installed
     */
    fun launchAiReport(
        context: Context,
        title: String,
        prompt: String,
        system: String = "",
        instructions: String = ""
    ): Boolean {
        Log.d("AiAppLauncher", "Launching AI report with title: $title")
        Log.d("AiAppLauncher", "Prompt (first 200 chars): ${prompt.take(200)}")

        val intent = Intent().apply {
            action = AI_APP_ACTION
            setPackage(AI_APP_PACKAGE)
            putExtra("title", title)
            putExtra("prompt", prompt)
            if (system.isNotBlank()) putExtra("system", system)
            if (instructions.isNotBlank()) putExtra("instructions", instructions)
        }

        return if (intent.resolveActivity(context.packageManager) != null) {
            Log.d("AiAppLauncher", "AI app found, starting activity")
            context.startActivity(intent)
            true
        } else {
            Log.e("AiAppLauncher", "AI app not installed!")
            Toast.makeText(context, "AI app not installed", Toast.LENGTH_SHORT).show()
            false
        }
    }

    /**
     * Launch AI report for game position analysis.
     *
     * @param context Android context
     * @param fen The FEN string of the position to analyze
     * @param promptTemplate The prompt template (with @FEN@, @BOARD@ placeholders)
     * @param whiteName White player name (for title and board)
     * @param blackName Black player name (for title and board)
     */
    fun launchGameAnalysis(
        context: Context,
        fen: String,
        promptTemplate: String,
        systemPrompt: String = "",
        whiteName: String = "",
        blackName: String = "",
        currentMoveIndex: Int = -1,
        lastMoveDetails: MoveDetails? = null,
        instructions: String = ""
    ): Boolean {
        val title = if (whiteName.isNotEmpty() && blackName.isNotEmpty()) {
            "Game Analysis: $whiteName vs $blackName"
        } else {
            "Chess Position Analysis"
        }

        val prompt = processPrompt(
            template = promptTemplate,
            fen = fen,
            whiteName = whiteName,
            blackName = blackName,
            currentMoveIndex = currentMoveIndex,
            lastMoveDetails = lastMoveDetails
        )
        val system = processPrompt(
            template = systemPrompt,
            fen = fen,
            whiteName = whiteName,
            blackName = blackName,
            currentMoveIndex = currentMoveIndex,
            lastMoveDetails = lastMoveDetails
        )
        return launchAiReport(context, title, prompt, system, instructions)
    }

    /**
     * Launch AI report for player analysis (Lichess/Chess.com player).
     *
     * @param context Android context
     * @param playerName The player's username
     * @param server The chess server (e.g., "lichess.org", "chess.com")
     * @param promptTemplate The prompt template (with @PLAYER@ and @SERVER@ placeholders)
     */
    fun launchServerPlayerAnalysis(
        context: Context,
        playerName: String,
        server: String,
        promptTemplate: String,
        systemPrompt: String = "",
        instructions: String = ""
    ): Boolean {
        val title = "Player Analysis: $playerName"
        val prompt = processPrompt(promptTemplate, player = playerName, server = server)
        val system = processPrompt(systemPrompt, player = playerName, server = server)
        return launchAiReport(context, title, prompt, system, instructions)
    }

    /**
     * Launch AI report for general player analysis (not tied to a server).
     *
     * @param context Android context
     * @param playerName The player's name
     * @param promptTemplate The prompt template (with @PLAYER@ placeholder)
     */
    fun launchOtherPlayerAnalysis(
        context: Context,
        playerName: String,
        promptTemplate: String,
        systemPrompt: String = "",
        instructions: String = ""
    ): Boolean {
        val title = "Player Profile: $playerName"
        val prompt = processPrompt(promptTemplate, player = playerName)
        val system = processPrompt(systemPrompt, player = playerName)
        return launchAiReport(context, title, prompt, system, instructions)
    }

    /**
     * Process a prompt template by replacing placeholders.
     *
     * Supported placeholders:
     * - @FEN@ - Chess position in FEN notation
     * - @BOARD@ - HTML code for interactive chess board with position
     * - @PLAYER@ - Player name
     * - @SERVER@ - Chess server name
     * - @DATE@ - Current date
     */
    private fun processPrompt(
        template: String,
        fen: String? = null,
        player: String? = null,
        server: String? = null,
        whiteName: String? = null,
        blackName: String? = null,
        currentMoveIndex: Int = -1,
        lastMoveDetails: MoveDetails? = null
    ): String {
        var result = template

        if (fen != null) {
            result = result.replace("@FEN@", fen)
            // Generate HTML board code for @BOARD@ placeholder
            val boardHtml = generateBoardHtml(fen, whiteName ?: "", blackName ?: "", currentMoveIndex, lastMoveDetails)
            result = result.replace("@BOARD@", boardHtml)
        }
        if (player != null) {
            result = result.replace("@PLAYER@", player)
        }
        if (server != null) {
            result = result.replace("@SERVER@", server)
        }

        // Always replace @DATE@ with current date
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        result = result.replace("@DATE@", dateFormat.format(Date()))

        return result
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
            val pieceCode = "${pieceColor}${lastMoveDetails.pieceType}"
            val pieceImg = "<img src=\"https://lichess1.org/assets/piece/cburnett/$pieceCode.svg\" style=\"height:20px;vertical-align:text-top;\">"
            val separator = if (lastMoveDetails.isCapture) "x" else "-"
            val dots = if (isWhiteMove) "" else " ....."
            "<div style=\"text-align:center;padding:6px 12px;color:#ccc;font-size:18px;\">" +
                "Last move $sideText: $moveNumber$dots $pieceImg ${lastMoveDetails.from} $separator ${lastMoveDetails.to}" +
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
            "<div style=\"background:#333;color:white;padding:8px 12px;font-weight:bold;text-align:center;\">$topPlayer</div>" +
            "<div id=\"board\" style=\"width:100%;\"></div>" +
            "<div style=\"background:#333;color:white;padding:8px 12px;font-weight:bold;text-align:center;\">$bottomPlayer</div>" +
            toMoveHtml +
            "</div>" +
            "<script>var board=Chessboard('board',{position:'$fen',orientation:'$orientation',pieceTheme:'https://lichess1.org/assets/piece/cburnett/{piece}.svg'});</script>"
    }
}
