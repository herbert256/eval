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
     * @param prompt The prompt to send to AI agents
     * @return true if launched successfully, false if AI app not installed
     */
    fun launchAiReport(context: Context, title: String, prompt: String): Boolean {
        Log.d("AiAppLauncher", "Launching AI report with title: $title")
        Log.d("AiAppLauncher", "Prompt (first 200 chars): ${prompt.take(200)}")

        // Match exact pattern from CALL_AI.md spec
        val intent = Intent().apply {
            action = AI_APP_ACTION
            setPackage(AI_APP_PACKAGE)
            putExtra("title", title)
            putExtra("prompt", prompt)
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
     * @param promptTemplate The prompt template (with @FEN@ placeholder)
     * @param whiteName White player name (for title)
     * @param blackName Black player name (for title)
     */
    fun launchGameAnalysis(
        context: Context,
        fen: String,
        promptTemplate: String,
        whiteName: String = "",
        blackName: String = ""
    ): Boolean {
        val title = if (whiteName.isNotEmpty() && blackName.isNotEmpty()) {
            "Game Analysis: $whiteName vs $blackName"
        } else {
            "Chess Position Analysis"
        }

        val prompt = processPrompt(promptTemplate, fen = fen)
        return launchAiReport(context, title, prompt)
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
        promptTemplate: String
    ): Boolean {
        val title = "Player Analysis: $playerName"
        val prompt = processPrompt(promptTemplate, player = playerName, server = server)
        return launchAiReport(context, title, prompt)
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
        promptTemplate: String
    ): Boolean {
        val title = "Player Profile: $playerName"
        val prompt = processPrompt(promptTemplate, player = playerName)
        return launchAiReport(context, title, prompt)
    }

    /**
     * Process a prompt template by replacing placeholders.
     *
     * Supported placeholders:
     * - @FEN@ - Chess position in FEN notation
     * - @PLAYER@ - Player name
     * - @SERVER@ - Chess server name
     * - @DATE@ - Current date
     */
    private fun processPrompt(
        template: String,
        fen: String? = null,
        player: String? = null,
        server: String? = null
    ): String {
        var result = template

        if (fen != null) {
            result = result.replace("@FEN@", fen)
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
}
