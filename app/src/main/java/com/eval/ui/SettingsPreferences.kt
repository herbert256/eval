package com.eval.ui

import android.content.SharedPreferences
import com.eval.data.ChessServer

/**
 * Helper class for managing all settings persistence via SharedPreferences.
 */
class SettingsPreferences(private val prefs: SharedPreferences) {

    // ============================================================================
    // Username and Server Properties
    // ============================================================================

    val savedLichessUsername: String
        get() = prefs.getString(KEY_LICHESS_USERNAME, "DrNykterstein") ?: "DrNykterstein"

    val savedChessComUsername: String
        get() = prefs.getString(KEY_CHESSCOM_USERNAME, "magnuscarlsen") ?: "magnuscarlsen"

    val savedActiveServer: ChessServer?
        get() {
            val serverName = prefs.getString(KEY_ACTIVE_SERVER, null) ?: return null
            return try {
                ChessServer.valueOf(serverName)
            } catch (e: Exception) {
                null
            }
        }

    val savedActivePlayer: String?
        get() = prefs.getString(KEY_ACTIVE_PLAYER, null)

    val lichessMaxGames: Int
        get() = prefs.getInt(KEY_LICHESS_MAX_GAMES, 10)

    val chessComMaxGames: Int
        get() = prefs.getInt(KEY_CHESSCOM_MAX_GAMES, 10)

    // ============================================================================
    // Username and Server Save Methods
    // ============================================================================

    fun saveLichessUsername(username: String) {
        prefs.edit().putString(KEY_LICHESS_USERNAME, username).apply()
    }

    fun saveChessComUsername(username: String) {
        prefs.edit().putString(KEY_CHESSCOM_USERNAME, username).apply()
    }

    fun saveLichessMaxGames(max: Int) {
        prefs.edit().putInt(KEY_LICHESS_MAX_GAMES, max).apply()
    }

    fun saveChessComMaxGames(max: Int) {
        prefs.edit().putInt(KEY_CHESSCOM_MAX_GAMES, max).apply()
    }

    fun saveActivePlayerAndServer(player: String, server: ChessServer) {
        prefs.edit()
            .putString(KEY_ACTIVE_SERVER, server.name)
            .putString(KEY_ACTIVE_PLAYER, player)
            .apply()
    }

    // ============================================================================
    // Stockfish Settings
    // ============================================================================

    fun loadStockfishSettings(): StockfishSettings {
        return StockfishSettings(
            previewStage = PreviewStageSettings(
                secondsForMove = prefs.getFloat(KEY_PREVIEW_SECONDS, 0.05f),
                threads = prefs.getInt(KEY_PREVIEW_THREADS, 1),
                hashMb = prefs.getInt(KEY_PREVIEW_HASH, 8),
                useNnue = prefs.getBoolean(KEY_PREVIEW_NNUE, false)
            ),
            analyseStage = AnalyseStageSettings(
                secondsForMove = prefs.getFloat(KEY_ANALYSE_SECONDS, 1.00f),
                threads = prefs.getInt(KEY_ANALYSE_THREADS, 2),
                hashMb = prefs.getInt(KEY_ANALYSE_HASH, 32),
                useNnue = prefs.getBoolean(KEY_ANALYSE_NNUE, true)
            ),
            manualStage = ManualStageSettings(
                depth = prefs.getInt(KEY_MANUAL_DEPTH, 32),
                threads = prefs.getInt(KEY_MANUAL_THREADS, 4),
                hashMb = prefs.getInt(KEY_MANUAL_HASH, 64),
                multiPv = prefs.getInt(KEY_MANUAL_MULTIPV, 3),
                useNnue = prefs.getBoolean(KEY_MANUAL_NNUE, true),
                arrowMode = ArrowMode.valueOf(prefs.getString(KEY_MANUAL_ARROW_MODE, ArrowMode.MAIN_LINE.name) ?: ArrowMode.MAIN_LINE.name),
                numArrows = prefs.getInt(KEY_MANUAL_NUMARROWS, 4),
                showArrowNumbers = prefs.getBoolean(KEY_MANUAL_SHOWNUMBERS, true),
                whiteArrowColor = prefs.getLong(KEY_MANUAL_WHITE_ARROW_COLOR, DEFAULT_WHITE_ARROW_COLOR),
                blackArrowColor = prefs.getLong(KEY_MANUAL_BLACK_ARROW_COLOR, DEFAULT_BLACK_ARROW_COLOR),
                multiLinesArrowColor = prefs.getLong(KEY_MANUAL_MULTILINES_ARROW_COLOR, DEFAULT_MULTI_LINES_ARROW_COLOR)
            )
        )
    }

    fun saveStockfishSettings(settings: StockfishSettings) {
        prefs.edit()
            // Preview stage
            .putFloat(KEY_PREVIEW_SECONDS, settings.previewStage.secondsForMove)
            .putInt(KEY_PREVIEW_THREADS, settings.previewStage.threads)
            .putInt(KEY_PREVIEW_HASH, settings.previewStage.hashMb)
            .putBoolean(KEY_PREVIEW_NNUE, settings.previewStage.useNnue)
            // Analyse stage
            .putFloat(KEY_ANALYSE_SECONDS, settings.analyseStage.secondsForMove)
            .putInt(KEY_ANALYSE_THREADS, settings.analyseStage.threads)
            .putInt(KEY_ANALYSE_HASH, settings.analyseStage.hashMb)
            .putBoolean(KEY_ANALYSE_NNUE, settings.analyseStage.useNnue)
            // Manual stage
            .putInt(KEY_MANUAL_DEPTH, settings.manualStage.depth)
            .putInt(KEY_MANUAL_THREADS, settings.manualStage.threads)
            .putInt(KEY_MANUAL_HASH, settings.manualStage.hashMb)
            .putInt(KEY_MANUAL_MULTIPV, settings.manualStage.multiPv)
            .putBoolean(KEY_MANUAL_NNUE, settings.manualStage.useNnue)
            .putString(KEY_MANUAL_ARROW_MODE, settings.manualStage.arrowMode.name)
            .putInt(KEY_MANUAL_NUMARROWS, settings.manualStage.numArrows)
            .putBoolean(KEY_MANUAL_SHOWNUMBERS, settings.manualStage.showArrowNumbers)
            .putLong(KEY_MANUAL_WHITE_ARROW_COLOR, settings.manualStage.whiteArrowColor)
            .putLong(KEY_MANUAL_BLACK_ARROW_COLOR, settings.manualStage.blackArrowColor)
            .putLong(KEY_MANUAL_MULTILINES_ARROW_COLOR, settings.manualStage.multiLinesArrowColor)
            .apply()
    }

    // ============================================================================
    // Board Layout Settings
    // ============================================================================

    fun loadBoardLayoutSettings(): BoardLayoutSettings {
        val playerBarModeOrdinal = prefs.getInt(KEY_BOARD_PLAYER_BAR_MODE, PlayerBarMode.BOTH.ordinal)
        val playerBarMode = PlayerBarMode.entries.getOrElse(playerBarModeOrdinal) { PlayerBarMode.BOTH }
        val evalBarPositionOrdinal = prefs.getInt(KEY_EVAL_BAR_POSITION, EvalBarPosition.RIGHT.ordinal)
        val evalBarPosition = EvalBarPosition.entries.getOrElse(evalBarPositionOrdinal) { EvalBarPosition.RIGHT }
        return BoardLayoutSettings(
            showCoordinates = prefs.getBoolean(KEY_BOARD_SHOW_COORDINATES, true),
            showLastMove = prefs.getBoolean(KEY_BOARD_SHOW_LAST_MOVE, true),
            playerBarMode = playerBarMode,
            showRedBorderForPlayerToMove = prefs.getBoolean(KEY_BOARD_RED_BORDER_PLAYER_TO_MOVE, false),
            whiteSquareColor = prefs.getLong(KEY_BOARD_WHITE_SQUARE_COLOR, DEFAULT_WHITE_SQUARE_COLOR),
            blackSquareColor = prefs.getLong(KEY_BOARD_BLACK_SQUARE_COLOR, DEFAULT_BLACK_SQUARE_COLOR),
            whitePieceColor = prefs.getLong(KEY_BOARD_WHITE_PIECE_COLOR, DEFAULT_WHITE_PIECE_COLOR),
            blackPieceColor = prefs.getLong(KEY_BOARD_BLACK_PIECE_COLOR, DEFAULT_BLACK_PIECE_COLOR),
            evalBarPosition = evalBarPosition,
            evalBarColor1 = prefs.getLong(KEY_EVAL_BAR_COLOR_1, DEFAULT_EVAL_BAR_COLOR_1),
            evalBarColor2 = prefs.getLong(KEY_EVAL_BAR_COLOR_2, DEFAULT_EVAL_BAR_COLOR_2),
            evalBarRange = prefs.getInt(KEY_EVAL_BAR_RANGE, 5)
        )
    }

    fun saveBoardLayoutSettings(settings: BoardLayoutSettings) {
        prefs.edit()
            .putBoolean(KEY_BOARD_SHOW_COORDINATES, settings.showCoordinates)
            .putBoolean(KEY_BOARD_SHOW_LAST_MOVE, settings.showLastMove)
            .putInt(KEY_BOARD_PLAYER_BAR_MODE, settings.playerBarMode.ordinal)
            .putBoolean(KEY_BOARD_RED_BORDER_PLAYER_TO_MOVE, settings.showRedBorderForPlayerToMove)
            .putLong(KEY_BOARD_WHITE_SQUARE_COLOR, settings.whiteSquareColor)
            .putLong(KEY_BOARD_BLACK_SQUARE_COLOR, settings.blackSquareColor)
            .putLong(KEY_BOARD_WHITE_PIECE_COLOR, settings.whitePieceColor)
            .putLong(KEY_BOARD_BLACK_PIECE_COLOR, settings.blackPieceColor)
            .putInt(KEY_EVAL_BAR_POSITION, settings.evalBarPosition.ordinal)
            .putLong(KEY_EVAL_BAR_COLOR_1, settings.evalBarColor1)
            .putLong(KEY_EVAL_BAR_COLOR_2, settings.evalBarColor2)
            .putInt(KEY_EVAL_BAR_RANGE, settings.evalBarRange)
            .apply()
    }

    // ============================================================================
    // Graph Settings
    // ============================================================================

    fun loadGraphSettings(): GraphSettings {
        return GraphSettings(
            plusScoreColor = prefs.getLong(KEY_GRAPH_PLUS_SCORE_COLOR, DEFAULT_GRAPH_PLUS_SCORE_COLOR),
            negativeScoreColor = prefs.getLong(KEY_GRAPH_NEGATIVE_SCORE_COLOR, DEFAULT_GRAPH_NEGATIVE_SCORE_COLOR),
            backgroundColor = prefs.getLong(KEY_GRAPH_BACKGROUND_COLOR, DEFAULT_GRAPH_BACKGROUND_COLOR),
            analyseLineColor = prefs.getLong(KEY_GRAPH_ANALYSE_LINE_COLOR, DEFAULT_GRAPH_ANALYSE_LINE_COLOR),
            verticalLineColor = prefs.getLong(KEY_GRAPH_VERTICAL_LINE_COLOR, DEFAULT_GRAPH_VERTICAL_LINE_COLOR),
            lineGraphRange = prefs.getInt(KEY_GRAPH_LINE_RANGE, 7),
            barGraphRange = prefs.getInt(KEY_GRAPH_BAR_RANGE, 3)
        )
    }

    fun saveGraphSettings(settings: GraphSettings) {
        prefs.edit()
            .putLong(KEY_GRAPH_PLUS_SCORE_COLOR, settings.plusScoreColor)
            .putLong(KEY_GRAPH_NEGATIVE_SCORE_COLOR, settings.negativeScoreColor)
            .putLong(KEY_GRAPH_BACKGROUND_COLOR, settings.backgroundColor)
            .putLong(KEY_GRAPH_ANALYSE_LINE_COLOR, settings.analyseLineColor)
            .putLong(KEY_GRAPH_VERTICAL_LINE_COLOR, settings.verticalLineColor)
            .putInt(KEY_GRAPH_LINE_RANGE, settings.lineGraphRange)
            .putInt(KEY_GRAPH_BAR_RANGE, settings.barGraphRange)
            .apply()
    }

    // ============================================================================
    // Interface Visibility Settings
    // ============================================================================

    fun loadInterfaceVisibilitySettings(): InterfaceVisibilitySettings {
        return InterfaceVisibilitySettings(
            previewStage = PreviewStageVisibility(
                showScoreBarsGraph = prefs.getBoolean(KEY_PREVIEW_VIS_SCOREBARSGRAPH, false),
                showResultBar = prefs.getBoolean(KEY_PREVIEW_VIS_RESULTBAR, false),
                showBoard = prefs.getBoolean(KEY_PREVIEW_VIS_BOARD, false),
                showMoveList = prefs.getBoolean(KEY_PREVIEW_VIS_MOVELIST, false),
                showPgn = prefs.getBoolean(KEY_PREVIEW_VIS_PGN, false)
            ),
            analyseStage = AnalyseStageVisibility(
                showScoreLineGraph = prefs.getBoolean(KEY_ANALYSE_VIS_SCORELINEGRAPH, true),
                showScoreBarsGraph = prefs.getBoolean(KEY_ANALYSE_VIS_SCOREBARSGRAPH, true),
                showBoard = prefs.getBoolean(KEY_ANALYSE_VIS_BOARD, true),
                showStockfishAnalyse = prefs.getBoolean(KEY_ANALYSE_VIS_STOCKFISHANALYSE, true),
                showResultBar = prefs.getBoolean(KEY_ANALYSE_VIS_RESULTBAR, false),
                showMoveList = prefs.getBoolean(KEY_ANALYSE_VIS_MOVELIST, false),
                showGameInfo = prefs.getBoolean(KEY_ANALYSE_VIS_GAMEINFO, false),
                showPgn = prefs.getBoolean(KEY_ANALYSE_VIS_PGN, false)
            ),
            manualStage = ManualStageVisibility(
                showResultBar = prefs.getBoolean(KEY_MANUAL_VIS_RESULTBAR, true),
                showScoreLineGraph = prefs.getBoolean(KEY_MANUAL_VIS_SCORELINEGRAPH, true),
                showScoreBarsGraph = prefs.getBoolean(KEY_MANUAL_VIS_SCOREBARSGRAPH, true),
                showMoveList = prefs.getBoolean(KEY_MANUAL_VIS_MOVELIST, true),
                showGameInfo = prefs.getBoolean(KEY_MANUAL_VIS_GAMEINFO, false),
                showPgn = prefs.getBoolean(KEY_MANUAL_VIS_PGN, false)
            )
        )
    }

    fun saveInterfaceVisibilitySettings(settings: InterfaceVisibilitySettings) {
        prefs.edit()
            // Preview stage
            .putBoolean(KEY_PREVIEW_VIS_SCOREBARSGRAPH, settings.previewStage.showScoreBarsGraph)
            .putBoolean(KEY_PREVIEW_VIS_RESULTBAR, settings.previewStage.showResultBar)
            .putBoolean(KEY_PREVIEW_VIS_BOARD, settings.previewStage.showBoard)
            .putBoolean(KEY_PREVIEW_VIS_MOVELIST, settings.previewStage.showMoveList)
            .putBoolean(KEY_PREVIEW_VIS_PGN, settings.previewStage.showPgn)
            // Analyse stage
            .putBoolean(KEY_ANALYSE_VIS_SCORELINEGRAPH, settings.analyseStage.showScoreLineGraph)
            .putBoolean(KEY_ANALYSE_VIS_SCOREBARSGRAPH, settings.analyseStage.showScoreBarsGraph)
            .putBoolean(KEY_ANALYSE_VIS_BOARD, settings.analyseStage.showBoard)
            .putBoolean(KEY_ANALYSE_VIS_STOCKFISHANALYSE, settings.analyseStage.showStockfishAnalyse)
            .putBoolean(KEY_ANALYSE_VIS_RESULTBAR, settings.analyseStage.showResultBar)
            .putBoolean(KEY_ANALYSE_VIS_MOVELIST, settings.analyseStage.showMoveList)
            .putBoolean(KEY_ANALYSE_VIS_GAMEINFO, settings.analyseStage.showGameInfo)
            .putBoolean(KEY_ANALYSE_VIS_PGN, settings.analyseStage.showPgn)
            // Manual stage
            .putBoolean(KEY_MANUAL_VIS_RESULTBAR, settings.manualStage.showResultBar)
            .putBoolean(KEY_MANUAL_VIS_SCORELINEGRAPH, settings.manualStage.showScoreLineGraph)
            .putBoolean(KEY_MANUAL_VIS_SCOREBARSGRAPH, settings.manualStage.showScoreBarsGraph)
            .putBoolean(KEY_MANUAL_VIS_MOVELIST, settings.manualStage.showMoveList)
            .putBoolean(KEY_MANUAL_VIS_GAMEINFO, settings.manualStage.showGameInfo)
            .putBoolean(KEY_MANUAL_VIS_PGN, settings.manualStage.showPgn)
            .apply()
    }

    // ============================================================================
    // General Settings
    // ============================================================================

    fun loadGeneralSettings(): GeneralSettings {
        // Full screen mode is not persistent - always starts as false
        return GeneralSettings(
            longTapForFullScreen = false
        )
    }

    @Suppress("UNUSED_PARAMETER")
    fun saveGeneralSettings(settings: GeneralSettings) {
        // Full screen mode is not persistent - do not save
    }

    // ============================================================================
    // AI Settings
    // ============================================================================

    fun loadAiSettings(): AiSettings {
        return AiSettings(
            showAiLogos = prefs.getBoolean(KEY_AI_SHOW_LOGOS, true),
            chatGptApiKey = prefs.getString(KEY_AI_CHATGPT_API_KEY, "") ?: "",
            chatGptModel = prefs.getString(KEY_AI_CHATGPT_MODEL, "gpt-4o-mini") ?: "gpt-4o-mini",
            chatGptPrompt = prefs.getString(KEY_AI_CHATGPT_PROMPT, DEFAULT_AI_PROMPT) ?: DEFAULT_AI_PROMPT,
            claudeApiKey = prefs.getString(KEY_AI_CLAUDE_API_KEY, "") ?: "",
            claudeModel = prefs.getString(KEY_AI_CLAUDE_MODEL, "claude-sonnet-4-20250514") ?: "claude-sonnet-4-20250514",
            claudePrompt = prefs.getString(KEY_AI_CLAUDE_PROMPT, DEFAULT_AI_PROMPT) ?: DEFAULT_AI_PROMPT,
            geminiApiKey = prefs.getString(KEY_AI_GEMINI_API_KEY, "") ?: "",
            geminiModel = prefs.getString(KEY_AI_GEMINI_MODEL, "gemini-2.0-flash") ?: "gemini-2.0-flash",
            geminiPrompt = prefs.getString(KEY_AI_GEMINI_PROMPT, DEFAULT_AI_PROMPT) ?: DEFAULT_AI_PROMPT,
            grokApiKey = prefs.getString(KEY_AI_GROK_API_KEY, "") ?: "",
            grokModel = prefs.getString(KEY_AI_GROK_MODEL, "grok-3-mini") ?: "grok-3-mini",
            grokPrompt = prefs.getString(KEY_AI_GROK_PROMPT, DEFAULT_AI_PROMPT) ?: DEFAULT_AI_PROMPT,
            deepSeekApiKey = prefs.getString(KEY_AI_DEEPSEEK_API_KEY, "") ?: "",
            deepSeekModel = prefs.getString(KEY_AI_DEEPSEEK_MODEL, "deepseek-chat") ?: "deepseek-chat",
            deepSeekPrompt = prefs.getString(KEY_AI_DEEPSEEK_PROMPT, DEFAULT_AI_PROMPT) ?: DEFAULT_AI_PROMPT,
            mistralApiKey = prefs.getString(KEY_AI_MISTRAL_API_KEY, "") ?: "",
            mistralModel = prefs.getString(KEY_AI_MISTRAL_MODEL, "mistral-small-latest") ?: "mistral-small-latest",
            mistralPrompt = prefs.getString(KEY_AI_MISTRAL_PROMPT, DEFAULT_AI_PROMPT) ?: DEFAULT_AI_PROMPT
        )
    }

    fun saveAiSettings(settings: AiSettings) {
        prefs.edit()
            .putBoolean(KEY_AI_SHOW_LOGOS, settings.showAiLogos)
            .putString(KEY_AI_CHATGPT_API_KEY, settings.chatGptApiKey)
            .putString(KEY_AI_CHATGPT_MODEL, settings.chatGptModel)
            .putString(KEY_AI_CHATGPT_PROMPT, settings.chatGptPrompt)
            .putString(KEY_AI_CLAUDE_API_KEY, settings.claudeApiKey)
            .putString(KEY_AI_CLAUDE_MODEL, settings.claudeModel)
            .putString(KEY_AI_CLAUDE_PROMPT, settings.claudePrompt)
            .putString(KEY_AI_GEMINI_API_KEY, settings.geminiApiKey)
            .putString(KEY_AI_GEMINI_MODEL, settings.geminiModel)
            .putString(KEY_AI_GEMINI_PROMPT, settings.geminiPrompt)
            .putString(KEY_AI_GROK_API_KEY, settings.grokApiKey)
            .putString(KEY_AI_GROK_MODEL, settings.grokModel)
            .putString(KEY_AI_GROK_PROMPT, settings.grokPrompt)
            .putString(KEY_AI_DEEPSEEK_API_KEY, settings.deepSeekApiKey)
            .putString(KEY_AI_DEEPSEEK_MODEL, settings.deepSeekModel)
            .putString(KEY_AI_DEEPSEEK_PROMPT, settings.deepSeekPrompt)
            .putString(KEY_AI_MISTRAL_API_KEY, settings.mistralApiKey)
            .putString(KEY_AI_MISTRAL_MODEL, settings.mistralModel)
            .putString(KEY_AI_MISTRAL_PROMPT, settings.mistralPrompt)
            .apply()
    }

    // ============================================================================
    // First Run and App Version Tracking
    // ============================================================================

    fun getFirstGameRetrievedVersion(): Long {
        return prefs.getLong(KEY_FIRST_GAME_RETRIEVED_VERSION, 0)
    }

    fun setFirstGameRetrievedVersion(version: Long) {
        prefs.edit().putLong(KEY_FIRST_GAME_RETRIEVED_VERSION, version).apply()
    }

    // ============================================================================
    // Reset Settings to Defaults
    // ============================================================================

    fun resetAllSettingsToDefaults() {
        // Reset stockfish settings
        saveStockfishSettings(StockfishSettings())
        // Reset board layout settings
        saveBoardLayoutSettings(BoardLayoutSettings())
        // Reset graph settings
        saveGraphSettings(GraphSettings())
        // Reset interface visibility settings
        saveInterfaceVisibilitySettings(InterfaceVisibilitySettings())
    }

    companion object {
        const val PREFS_NAME = "eval_prefs"

        // Current game storage
        const val KEY_CURRENT_GAME_JSON = "current_game_json"

        // Lichess settings
        private const val KEY_LICHESS_USERNAME = "lichess_username"
        private const val KEY_LICHESS_MAX_GAMES = "lichess_max_games"

        // Chess.com settings
        private const val KEY_CHESSCOM_USERNAME = "chesscom_username"
        private const val KEY_CHESSCOM_MAX_GAMES = "chesscom_max_games"

        // Active player/server for reload button
        private const val KEY_ACTIVE_SERVER = "active_server"
        private const val KEY_ACTIVE_PLAYER = "active_player"

        // Retrieved games storage - list of lists
        const val KEY_RETRIEVES_LIST = "retrieves_list"
        const val KEY_RETRIEVED_GAMES_PREFIX = "retrieved_games_"
        const val MAX_RETRIEVES = 25

        // Analysed games storage
        const val KEY_ANALYSED_GAMES = "analysed_games"
        const val MAX_ANALYSED_GAMES = 50

        // Preview stage settings
        private const val KEY_PREVIEW_SECONDS = "preview_seconds"
        private const val KEY_PREVIEW_THREADS = "preview_threads"
        private const val KEY_PREVIEW_HASH = "preview_hash"
        private const val KEY_PREVIEW_NNUE = "preview_nnue"

        // Analyse stage settings
        private const val KEY_ANALYSE_SECONDS = "analyse_seconds"
        private const val KEY_ANALYSE_THREADS = "analyse_threads"
        private const val KEY_ANALYSE_HASH = "analyse_hash"
        private const val KEY_ANALYSE_NNUE = "analyse_nnue"

        // Manual stage settings
        private const val KEY_MANUAL_DEPTH = "manual_depth"
        private const val KEY_MANUAL_THREADS = "manual_threads"
        private const val KEY_MANUAL_HASH = "manual_hash"
        private const val KEY_MANUAL_MULTIPV = "manual_multipv"
        private const val KEY_MANUAL_NNUE = "manual_nnue"
        private const val KEY_MANUAL_ARROW_MODE = "manual_arrow_mode"
        private const val KEY_MANUAL_NUMARROWS = "manual_numarrows"
        private const val KEY_MANUAL_SHOWNUMBERS = "manual_shownumbers"
        private const val KEY_MANUAL_WHITE_ARROW_COLOR = "manual_white_arrow_color"
        private const val KEY_MANUAL_BLACK_ARROW_COLOR = "manual_black_arrow_color"
        private const val KEY_MANUAL_MULTILINES_ARROW_COLOR = "manual_multilines_arrow_color"

        // Board layout settings
        private const val KEY_BOARD_SHOW_COORDINATES = "board_show_coordinates"
        private const val KEY_BOARD_SHOW_LAST_MOVE = "board_show_last_move"
        private const val KEY_BOARD_PLAYER_BAR_MODE = "board_player_bar_mode"
        private const val KEY_BOARD_RED_BORDER_PLAYER_TO_MOVE = "board_red_border_player_to_move"
        private const val KEY_BOARD_WHITE_SQUARE_COLOR = "board_white_square_color"
        private const val KEY_BOARD_BLACK_SQUARE_COLOR = "board_black_square_color"
        private const val KEY_BOARD_WHITE_PIECE_COLOR = "board_white_piece_color"
        private const val KEY_BOARD_BLACK_PIECE_COLOR = "board_black_piece_color"

        // Evaluation bar settings
        private const val KEY_EVAL_BAR_POSITION = "eval_bar_position"
        private const val KEY_EVAL_BAR_COLOR_1 = "eval_bar_color_1"
        private const val KEY_EVAL_BAR_COLOR_2 = "eval_bar_color_2"
        private const val KEY_EVAL_BAR_RANGE = "eval_bar_range"

        // Graph settings
        private const val KEY_GRAPH_PLUS_SCORE_COLOR = "graph_plus_score_color"
        private const val KEY_GRAPH_NEGATIVE_SCORE_COLOR = "graph_negative_score_color"
        private const val KEY_GRAPH_BACKGROUND_COLOR = "graph_background_color"
        private const val KEY_GRAPH_ANALYSE_LINE_COLOR = "graph_analyse_line_color"
        private const val KEY_GRAPH_VERTICAL_LINE_COLOR = "graph_vertical_line_color"
        private const val KEY_GRAPH_LINE_RANGE = "graph_line_range"
        private const val KEY_GRAPH_BAR_RANGE = "graph_bar_range"

        // Interface visibility settings - Preview stage
        private const val KEY_PREVIEW_VIS_SCOREBARSGRAPH = "preview_vis_scorebarsgraph"
        private const val KEY_PREVIEW_VIS_RESULTBAR = "preview_vis_resultbar"
        private const val KEY_PREVIEW_VIS_BOARD = "preview_vis_board"
        private const val KEY_PREVIEW_VIS_MOVELIST = "preview_vis_movelist"
        private const val KEY_PREVIEW_VIS_PGN = "preview_vis_pgn"

        // Interface visibility settings - Analyse stage
        private const val KEY_ANALYSE_VIS_SCORELINEGRAPH = "analyse_vis_scorelinegraph"
        private const val KEY_ANALYSE_VIS_SCOREBARSGRAPH = "analyse_vis_scorebarsgraph"
        private const val KEY_ANALYSE_VIS_BOARD = "analyse_vis_board"
        private const val KEY_ANALYSE_VIS_STOCKFISHANALYSE = "analyse_vis_stockfishanalyse"
        private const val KEY_ANALYSE_VIS_RESULTBAR = "analyse_vis_resultbar"
        private const val KEY_ANALYSE_VIS_MOVELIST = "analyse_vis_movelist"
        private const val KEY_ANALYSE_VIS_GAMEINFO = "analyse_vis_gameinfo"
        private const val KEY_ANALYSE_VIS_PGN = "analyse_vis_pgn"

        // Interface visibility settings - Manual stage
        private const val KEY_MANUAL_VIS_RESULTBAR = "manual_vis_resultbar"
        private const val KEY_MANUAL_VIS_SCORELINEGRAPH = "manual_vis_scorelinegraph"
        private const val KEY_MANUAL_VIS_SCOREBARSGRAPH = "manual_vis_scorebarsgraph"
        private const val KEY_MANUAL_VIS_MOVELIST = "manual_vis_movelist"
        private const val KEY_MANUAL_VIS_GAMEINFO = "manual_vis_gameinfo"
        private const val KEY_MANUAL_VIS_PGN = "manual_vis_pgn"

        // First run tracking
        private const val KEY_FIRST_GAME_RETRIEVED_VERSION = "first_game_retrieved_version"

        // AI Analysis settings
        private const val KEY_AI_SHOW_LOGOS = "ai_show_logos"
        private const val KEY_AI_CHATGPT_API_KEY = "ai_chatgpt_api_key"
        private const val KEY_AI_CHATGPT_MODEL = "ai_chatgpt_model"
        private const val KEY_AI_CLAUDE_API_KEY = "ai_claude_api_key"
        private const val KEY_AI_CLAUDE_MODEL = "ai_claude_model"
        private const val KEY_AI_GEMINI_API_KEY = "ai_gemini_api_key"
        private const val KEY_AI_GEMINI_MODEL = "ai_gemini_model"
        private const val KEY_AI_GROK_API_KEY = "ai_grok_api_key"
        private const val KEY_AI_GROK_MODEL = "ai_grok_model"
        private const val KEY_AI_DEEPSEEK_API_KEY = "ai_deepseek_api_key"
        private const val KEY_AI_DEEPSEEK_MODEL = "ai_deepseek_model"

        // AI prompts
        private const val KEY_AI_CHATGPT_PROMPT = "ai_chatgpt_prompt"
        private const val KEY_AI_CLAUDE_PROMPT = "ai_claude_prompt"
        private const val KEY_AI_GEMINI_PROMPT = "ai_gemini_prompt"
        private const val KEY_AI_GROK_PROMPT = "ai_grok_prompt"
        private const val KEY_AI_DEEPSEEK_PROMPT = "ai_deepseek_prompt"
        private const val KEY_AI_MISTRAL_API_KEY = "ai_mistral_api_key"
        private const val KEY_AI_MISTRAL_MODEL = "ai_mistral_model"
        private const val KEY_AI_MISTRAL_PROMPT = "ai_mistral_prompt"

        // AI report email
        const val KEY_AI_REPORT_EMAIL = "ai_report_email"
    }
}
