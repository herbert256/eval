package com.chessreplay.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chessreplay.chess.ChessBoard
import com.chessreplay.chess.PgnParser
import com.chessreplay.data.ChessRepository
import com.chessreplay.data.ChessServer
import com.chessreplay.data.LichessGame
import com.chessreplay.data.Result
import com.google.gson.Gson
import com.chessreplay.stockfish.AnalysisResult
import com.chessreplay.stockfish.StockfishEngine
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// Analysis stage - the 3 sequential stages of game analysis
enum class AnalysisStage {
    PREVIEW,        // Quick analysis pass - not interruptible
    ANALYSE,        // Deep analysis pass - interruptible
    MANUAL          // Manual exploration - final stage
}

// Settings for Preview Stage (quick analysis during navigation)
data class PreviewStageSettings(
    val secondsForMove: Float = 0.05f,  // 0.01, 0.05, 0.10, 0.25, 0.50
    val threads: Int = 1,               // 1-4
    val hashMb: Int = 8,                // 8, 16, 64
    val useNnue: Boolean = false
)

// Settings for Analyse Stage (auto-analysis)
data class AnalyseStageSettings(
    val secondsForMove: Float = 1.00f,  // 0.50, 0.75, 1.00, 1.50, 2.50, 5.00, 10.00
    val threads: Int = 2,               // 1-8
    val hashMb: Int = 64,               // 16, 64, 96, 128, 192, 256
    val useNnue: Boolean = true
)

// Arrow drawing modes
enum class ArrowMode {
    NONE,        // No arrows
    MAIN_LINE,   // Draw arrows from PV line (current behavior)
    MULTI_LINES  // Draw one arrow per Stockfish line with score
}

// Default arrow colors (with alpha for semi-transparency)
const val DEFAULT_WHITE_ARROW_COLOR = 0xCC3399FFL  // Semi-transparent blue
const val DEFAULT_BLACK_ARROW_COLOR = 0xCC44BB44L  // Semi-transparent green
const val DEFAULT_MULTI_LINES_ARROW_COLOR = 0xCCFFFF00L  // Semi-transparent yellow

// Settings for Manual Analyse Stage (interactive deep analysis)
data class ManualStageSettings(
    val depth: Int = 32,                // 16-64
    val threads: Int = 4,               // 1-16
    val hashMb: Int = 128,              // 32, 64, 96, 128, 192, 256, 384, 512
    val multiPv: Int = 3,               // 1-32
    val useNnue: Boolean = true,
    // Main line arrow settings
    val arrowMode: ArrowMode = ArrowMode.MAIN_LINE,
    val numArrows: Int = 4,             // 1-8 arrows from PV
    val showArrowNumbers: Boolean = true,
    val whiteArrowColor: Long = DEFAULT_WHITE_ARROW_COLOR,
    val blackArrowColor: Long = DEFAULT_BLACK_ARROW_COLOR,
    // Multi lines arrow settings
    val multiLinesArrowColor: Long = DEFAULT_MULTI_LINES_ARROW_COLOR
)

// Combined Stockfish settings for all stages
data class StockfishSettings(
    val previewStage: PreviewStageSettings = PreviewStageSettings(),
    val analyseStage: AnalyseStageSettings = AnalyseStageSettings(),
    val manualStage: ManualStageSettings = ManualStageSettings()
)

// Default board colors
const val DEFAULT_WHITE_SQUARE_COLOR = 0xFFF0D9B5L  // Light brown
const val DEFAULT_BLACK_SQUARE_COLOR = 0xFFB58863L  // Dark brown
const val DEFAULT_WHITE_PIECE_COLOR = 0xFFFFFFFF   // White
const val DEFAULT_BLACK_PIECE_COLOR = 0xFF000000L  // Black

// Evaluation bar defaults
const val DEFAULT_EVAL_BAR_COLOR_1 = 0xFFFFFFFF   // White (score color)
const val DEFAULT_EVAL_BAR_COLOR_2 = 0xFF000000L  // Black (filler color)

// Graph color defaults
const val DEFAULT_GRAPH_PLUS_SCORE_COLOR = 0xFF00E676L    // Bright green
const val DEFAULT_GRAPH_NEGATIVE_SCORE_COLOR = 0xFFFF5252L // Bright red
const val DEFAULT_GRAPH_BACKGROUND_COLOR = 0xFF1A1A1AL    // Dark gray
const val DEFAULT_GRAPH_ANALYSE_LINE_COLOR = 0xFFFFFFFFL  // White
const val DEFAULT_GRAPH_VERTICAL_LINE_COLOR = 0xFF2196F3L // Blue

// Graph settings
data class GraphSettings(
    val plusScoreColor: Long = DEFAULT_GRAPH_PLUS_SCORE_COLOR,
    val negativeScoreColor: Long = DEFAULT_GRAPH_NEGATIVE_SCORE_COLOR,
    val backgroundColor: Long = DEFAULT_GRAPH_BACKGROUND_COLOR,
    val analyseLineColor: Long = DEFAULT_GRAPH_ANALYSE_LINE_COLOR,
    val verticalLineColor: Long = DEFAULT_GRAPH_VERTICAL_LINE_COLOR
)

// Player bar display mode
enum class PlayerBarMode {
    NONE,    // No player bars
    TOP,     // Single combined bar at top
    BOTTOM,  // Single combined bar at bottom
    BOTH     // Separate bars above and below board (default)
}

// Evaluation bar position
enum class EvalBarPosition {
    NONE,    // No evaluation bar
    LEFT,    // Evaluation bar on the left of the board
    RIGHT    // Evaluation bar on the right of the board (default)
}

// Board layout settings
data class BoardLayoutSettings(
    val showCoordinates: Boolean = true,
    val showLastMove: Boolean = true,
    val playerBarMode: PlayerBarMode = PlayerBarMode.BOTH,
    val showRedBorderForPlayerToMove: Boolean = false,
    val whiteSquareColor: Long = DEFAULT_WHITE_SQUARE_COLOR,
    val blackSquareColor: Long = DEFAULT_BLACK_SQUARE_COLOR,
    val whitePieceColor: Long = DEFAULT_WHITE_PIECE_COLOR,
    val blackPieceColor: Long = DEFAULT_BLACK_PIECE_COLOR,
    // Evaluation bar settings
    val evalBarPosition: EvalBarPosition = EvalBarPosition.RIGHT,
    val evalBarColor1: Long = DEFAULT_EVAL_BAR_COLOR_1,
    val evalBarColor2: Long = DEFAULT_EVAL_BAR_COLOR_2,
    val evalBarRange: Int = 5
)

// Interface visibility settings for Preview stage
data class PreviewStageVisibility(
    val showMoveList: Boolean = false,
    val showBoard: Boolean = false,
    val showGameInfo: Boolean = true,
    val showPgn: Boolean = false
)

// Interface visibility settings for Analyse stage
data class AnalyseStageVisibility(
    val showMoveList: Boolean = false,
    val showScoreLineGraph: Boolean = true,
    val showScoreBarsGraph: Boolean = true,
    val showResultBar: Boolean = false,
    val showGameInfo: Boolean = true,
    val showBoard: Boolean = false,
    val showPgn: Boolean = false
)

// Interface visibility settings for Manual Analyse stage
data class ManualStageVisibility(
    val showResultBar: Boolean = true,
    val showScoreLineGraph: Boolean = true,
    val showScoreBarsGraph: Boolean = true,
    val showMoveList: Boolean = true,
    val showGameInfo: Boolean = false,
    val showPgn: Boolean = false
)

// Combined interface visibility settings
data class InterfaceVisibilitySettings(
    val previewStage: PreviewStageVisibility = PreviewStageVisibility(),
    val analyseStage: AnalyseStageVisibility = AnalyseStageVisibility(),
    val manualStage: ManualStageVisibility = ManualStageVisibility()
)

data class MoveScore(
    val score: Float,
    val isMate: Boolean,
    val mateIn: Int,
    val depth: Int = 0,
    val nodes: Long = 0
)

data class MoveDetails(
    val san: String,
    val from: String,
    val to: String,
    val isCapture: Boolean,
    val pieceType: String, // K, Q, R, B, N, P
    val clockTime: String? = null  // Format: "H:MM:SS" or "M:SS" or null if not available
)

data class GameUiState(
    val stockfishInstalled: Boolean = true,  // Assume true until checked
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    // Game list for selection
    val gameList: List<LichessGame> = emptyList(),
    val showGameSelection: Boolean = false,
    // Currently loaded game
    val game: LichessGame? = null,
    val openingName: String? = null,  // Extracted from PGN headers
    val currentBoard: ChessBoard = ChessBoard(),
    val moves: List<String> = emptyList(),
    val moveDetails: List<MoveDetails> = emptyList(),
    val currentMoveIndex: Int = -1,
    val analysisEnabled: Boolean = true,
    val analysisResult: AnalysisResult? = null,
    val analysisResultFen: String? = null,  // FEN for which analysisResult is valid
    val stockfishReady: Boolean = false,
    val flippedBoard: Boolean = false,
    val userPlayedBlack: Boolean = false,  // True if searched user played black (for score perspective)
    val stockfishSettings: StockfishSettings = StockfishSettings(),
    val boardLayoutSettings: BoardLayoutSettings = BoardLayoutSettings(),
    val graphSettings: GraphSettings = GraphSettings(),
    val interfaceVisibility: InterfaceVisibilitySettings = InterfaceVisibilitySettings(),
    val showSettingsDialog: Boolean = false,
    val showHelpScreen: Boolean = false,
    // Exploring line state
    val isExploringLine: Boolean = false,
    val exploringLineMoves: List<String> = emptyList(),
    val exploringLineMoveIndex: Int = -1,
    val savedGameMoveIndex: Int = -1,
    // Analysis stage state
    val currentStage: AnalysisStage = AnalysisStage.PREVIEW,
    val autoAnalysisIndex: Int = -1,
    val previewScores: Map<Int, MoveScore> = emptyMap(),     // Preview stage scores
    val analyseScores: Map<Int, MoveScore> = emptyMap(),     // Analyse stage scores
    val autoAnalysisCurrentScore: MoveScore? = null,
    val remainingAnalysisMoves: List<Int> = emptyList(),
    // Lichess settings
    val lichessMaxGames: Int = 10,
    // Chess.com settings
    val chessComMaxGames: Int = 10,
    // Last server used for reload
    val hasLastServerUser: Boolean = false,
    // General settings (fullScreenMode is stored here, not persistent)
    val generalSettings: GeneralSettings = GeneralSettings()
)

class GameViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ChessRepository()
    private val stockfish = StockfishEngine(application)
    private val prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    private val _uiState = MutableStateFlow(GameUiState())
    val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()

    private var boardHistory = mutableListOf<ChessBoard>()
    private var exploringLineHistory = mutableListOf<ChessBoard>()
    private var autoAnalysisJob: Job? = null

    // Track settings when dialog opens to detect changes
    private var settingsOnDialogOpen: SettingsSnapshot? = null

    private data class SettingsSnapshot(
        val previewStageSettings: PreviewStageSettings,
        val analyseStageSettings: AnalyseStageSettings,
        val manualStageSettings: ManualStageSettings
    )

    val savedLichessUsername: String
        get() = prefs.getString(KEY_LICHESS_USERNAME, "DrNykterstein") ?: "DrNykterstein"

    val savedChessComUsername: String
        get() = prefs.getString(KEY_CHESSCOM_USERNAME, "magnuscarlsen") ?: "magnuscarlsen"

    val savedLastServer: ChessServer?
        get() {
            val serverName = prefs.getString(KEY_LAST_SERVER, null) ?: return null
            return try {
                ChessServer.valueOf(serverName)
            } catch (e: Exception) {
                null
            }
        }

    val savedLastUsername: String?
        get() = prefs.getString(KEY_LAST_USERNAME, null)

    companion object {
        private const val PREFS_NAME = "chess_replay_prefs"
        // Current game storage
        private const val KEY_CURRENT_GAME_JSON = "current_game_json"
        // Lichess settings
        private const val KEY_LICHESS_USERNAME = "lichess_username"
        private const val KEY_LICHESS_MAX_GAMES = "lichess_max_games"
        // Chess.com settings
        private const val KEY_CHESSCOM_USERNAME = "chesscom_username"
        private const val KEY_CHESSCOM_MAX_GAMES = "chesscom_max_games"
        // Last server/user for reload
        private const val KEY_LAST_SERVER = "last_server"
        private const val KEY_LAST_USERNAME = "last_username"
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
        // Interface visibility settings - Preview stage
        private const val KEY_PREVIEW_VIS_MOVELIST = "preview_vis_movelist"
        private const val KEY_PREVIEW_VIS_BOARD = "preview_vis_board"
        private const val KEY_PREVIEW_VIS_GAMEINFO = "preview_vis_gameinfo"
        private const val KEY_PREVIEW_VIS_PGN = "preview_vis_pgn"
        // Interface visibility settings - Analyse stage
        private const val KEY_ANALYSE_VIS_MOVELIST = "analyse_vis_movelist"
        private const val KEY_ANALYSE_VIS_SCORELINEGRAPH = "analyse_vis_scorelinegraph"
        private const val KEY_ANALYSE_VIS_SCOREBARSGRAPH = "analyse_vis_scorebarsgraph"
        private const val KEY_ANALYSE_VIS_RESULTBAR = "analyse_vis_resultbar"
        private const val KEY_ANALYSE_VIS_GAMEINFO = "analyse_vis_gameinfo"
        private const val KEY_ANALYSE_VIS_BOARD = "analyse_vis_board"
        private const val KEY_ANALYSE_VIS_PGN = "analyse_vis_pgn"
        // Interface visibility settings - Manual stage
        private const val KEY_MANUAL_VIS_RESULTBAR = "manual_vis_resultbar"
        private const val KEY_MANUAL_VIS_SCORELINEGRAPH = "manual_vis_scorelinegraph"
        private const val KEY_MANUAL_VIS_SCOREBARSGRAPH = "manual_vis_scorebarsgraph"
        private const val KEY_MANUAL_VIS_MOVELIST = "manual_vis_movelist"
        private const val KEY_MANUAL_VIS_GAMEINFO = "manual_vis_gameinfo"
        private const val KEY_MANUAL_VIS_PGN = "manual_vis_pgn"
        // First run tracking - stores the app version code when user first made a choice
        private const val KEY_FIRST_GAME_RETRIEVED_VERSION = "first_game_retrieved_version"
    }

    private fun loadStockfishSettings(): StockfishSettings {
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

    private fun saveStockfishSettings(settings: StockfishSettings) {
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

    private fun loadBoardLayoutSettings(): BoardLayoutSettings {
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

    private fun saveBoardLayoutSettings(settings: BoardLayoutSettings) {
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

    private fun loadGraphSettings(): GraphSettings {
        return GraphSettings(
            plusScoreColor = prefs.getLong(KEY_GRAPH_PLUS_SCORE_COLOR, DEFAULT_GRAPH_PLUS_SCORE_COLOR),
            negativeScoreColor = prefs.getLong(KEY_GRAPH_NEGATIVE_SCORE_COLOR, DEFAULT_GRAPH_NEGATIVE_SCORE_COLOR),
            backgroundColor = prefs.getLong(KEY_GRAPH_BACKGROUND_COLOR, DEFAULT_GRAPH_BACKGROUND_COLOR),
            analyseLineColor = prefs.getLong(KEY_GRAPH_ANALYSE_LINE_COLOR, DEFAULT_GRAPH_ANALYSE_LINE_COLOR),
            verticalLineColor = prefs.getLong(KEY_GRAPH_VERTICAL_LINE_COLOR, DEFAULT_GRAPH_VERTICAL_LINE_COLOR)
        )
    }

    private fun saveGraphSettings(settings: GraphSettings) {
        prefs.edit()
            .putLong(KEY_GRAPH_PLUS_SCORE_COLOR, settings.plusScoreColor)
            .putLong(KEY_GRAPH_NEGATIVE_SCORE_COLOR, settings.negativeScoreColor)
            .putLong(KEY_GRAPH_BACKGROUND_COLOR, settings.backgroundColor)
            .putLong(KEY_GRAPH_ANALYSE_LINE_COLOR, settings.analyseLineColor)
            .putLong(KEY_GRAPH_VERTICAL_LINE_COLOR, settings.verticalLineColor)
            .apply()
    }

    private fun loadInterfaceVisibilitySettings(): InterfaceVisibilitySettings {
        return InterfaceVisibilitySettings(
            previewStage = PreviewStageVisibility(
                showMoveList = prefs.getBoolean(KEY_PREVIEW_VIS_MOVELIST, false),
                showBoard = prefs.getBoolean(KEY_PREVIEW_VIS_BOARD, false),
                showGameInfo = prefs.getBoolean(KEY_PREVIEW_VIS_GAMEINFO, true),
                showPgn = prefs.getBoolean(KEY_PREVIEW_VIS_PGN, false)
            ),
            analyseStage = AnalyseStageVisibility(
                showMoveList = prefs.getBoolean(KEY_ANALYSE_VIS_MOVELIST, false),
                showScoreLineGraph = prefs.getBoolean(KEY_ANALYSE_VIS_SCORELINEGRAPH, true),
                showScoreBarsGraph = prefs.getBoolean(KEY_ANALYSE_VIS_SCOREBARSGRAPH, true),
                showResultBar = prefs.getBoolean(KEY_ANALYSE_VIS_RESULTBAR, false),
                showGameInfo = prefs.getBoolean(KEY_ANALYSE_VIS_GAMEINFO, true),
                showBoard = prefs.getBoolean(KEY_ANALYSE_VIS_BOARD, false),
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

    private fun saveInterfaceVisibilitySettings(settings: InterfaceVisibilitySettings) {
        prefs.edit()
            // Preview stage
            .putBoolean(KEY_PREVIEW_VIS_MOVELIST, settings.previewStage.showMoveList)
            .putBoolean(KEY_PREVIEW_VIS_BOARD, settings.previewStage.showBoard)
            .putBoolean(KEY_PREVIEW_VIS_GAMEINFO, settings.previewStage.showGameInfo)
            .putBoolean(KEY_PREVIEW_VIS_PGN, settings.previewStage.showPgn)
            // Analyse stage
            .putBoolean(KEY_ANALYSE_VIS_MOVELIST, settings.analyseStage.showMoveList)
            .putBoolean(KEY_ANALYSE_VIS_SCORELINEGRAPH, settings.analyseStage.showScoreLineGraph)
            .putBoolean(KEY_ANALYSE_VIS_SCOREBARSGRAPH, settings.analyseStage.showScoreBarsGraph)
            .putBoolean(KEY_ANALYSE_VIS_RESULTBAR, settings.analyseStage.showResultBar)
            .putBoolean(KEY_ANALYSE_VIS_GAMEINFO, settings.analyseStage.showGameInfo)
            .putBoolean(KEY_ANALYSE_VIS_BOARD, settings.analyseStage.showBoard)
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

    private fun loadGeneralSettings(): GeneralSettings {
        // Full screen mode is not persistent - always starts as false
        return GeneralSettings(
            longTapForFullScreen = false
        )
    }

    @Suppress("UNUSED_PARAMETER")
    private fun saveGeneralSettings(settings: GeneralSettings) {
        // Full screen mode is not persistent - do not save
        // Just update the UI state
    }

    /**
     * Save the current game to SharedPreferences as JSON.
     */
    private fun saveCurrentGame(game: LichessGame) {
        val json = gson.toJson(game)
        prefs.edit().putString(KEY_CURRENT_GAME_JSON, json).apply()
    }

    /**
     * Load the current game from SharedPreferences.
     * Returns null if no game is stored.
     */
    private fun loadCurrentGame(): LichessGame? {
        val json = prefs.getString(KEY_CURRENT_GAME_JSON, null) ?: return null
        return try {
            gson.fromJson(json, LichessGame::class.java)
        } catch (e: Exception) {
            null
        }
    }

    private fun configureForPreviewStage() {
        val settings = _uiState.value.stockfishSettings.previewStage
        stockfish.configure(settings.threads, settings.hashMb, 1, settings.useNnue) // MultiPV=1 for preview stage
    }

    private fun configureForAnalyseStage() {
        val settings = _uiState.value.stockfishSettings.analyseStage
        stockfish.configure(settings.threads, settings.hashMb, 1, settings.useNnue) // MultiPV=1 for analyse stage
    }

    private fun configureForManualStage() {
        val settings = _uiState.value.stockfishSettings.manualStage
        stockfish.configure(settings.threads, settings.hashMb, settings.multiPv, settings.useNnue)
    }

    /**
     * Get the current app version code.
     */
    private fun getAppVersionCode(): Long {
        return try {
            val packageInfo = getApplication<Application>().packageManager
                .getPackageInfo(getApplication<Application>().packageName, 0)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                packageInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode.toLong()
            }
        } catch (e: Exception) {
            0L
        }
    }

    /**
     * Check if this is a first run (fresh install or app update).
     * Returns true if user hasn't made a game retrieval choice for this app version.
     */
    private fun isFirstRun(): Boolean {
        val savedVersionCode = prefs.getLong(KEY_FIRST_GAME_RETRIEVED_VERSION, 0L)
        return savedVersionCode != getAppVersionCode()
    }

    /**
     * Mark that the user has made their first game retrieval choice for this app version.
     */
    private fun markFirstRunComplete() {
        prefs.edit().putLong(KEY_FIRST_GAME_RETRIEVED_VERSION, getAppVersionCode()).apply()
    }

    /**
     * Reset all settings to their default values.
     * Called on first run after fresh install or app update.
     */
    private fun resetSettingsToDefaults() {
        prefs.edit()
            // Clear all settings (except version tracking)
            // Preview stage
            .remove(KEY_PREVIEW_SECONDS)
            .remove(KEY_PREVIEW_THREADS)
            .remove(KEY_PREVIEW_HASH)
            .remove(KEY_PREVIEW_NNUE)
            // Analyse stage
            .remove(KEY_ANALYSE_SECONDS)
            .remove(KEY_ANALYSE_THREADS)
            .remove(KEY_ANALYSE_HASH)
            .remove(KEY_ANALYSE_NNUE)
            // Manual stage
            .remove(KEY_MANUAL_DEPTH)
            .remove(KEY_MANUAL_THREADS)
            .remove(KEY_MANUAL_HASH)
            .remove(KEY_MANUAL_MULTIPV)
            .remove(KEY_MANUAL_NNUE)
            // Lichess settings
            .remove(KEY_LICHESS_MAX_GAMES)
            .apply()
    }

    init {
        // Check if Stockfish is installed first
        val stockfishInstalled = stockfish.isStockfishInstalled()
        _uiState.value = _uiState.value.copy(stockfishInstalled = stockfishInstalled)

        // Only proceed with initialization if Stockfish is installed
        if (stockfishInstalled) {
            // Reset settings to defaults on first run (fresh install or app update)
            if (isFirstRun()) {
                resetSettingsToDefaults()
            }

            // Load saved settings (will use defaults if reset or not previously set)
            val settings = loadStockfishSettings()
            val boardSettings = loadBoardLayoutSettings()
            val graphSettings = loadGraphSettings()
            val interfaceVisibility = loadInterfaceVisibilitySettings()
            val generalSettings = loadGeneralSettings()
            val lichessMaxGames = prefs.getInt(KEY_LICHESS_MAX_GAMES, 10)
            val chessComMaxGames = prefs.getInt(KEY_CHESSCOM_MAX_GAMES, 10)
            val hasLastServerUser = savedLastServer != null && savedLastUsername != null
            _uiState.value = _uiState.value.copy(
                stockfishSettings = settings,
                boardLayoutSettings = boardSettings,
                graphSettings = graphSettings,
                interfaceVisibility = interfaceVisibility,
                generalSettings = generalSettings,
                lichessMaxGames = lichessMaxGames,
                chessComMaxGames = chessComMaxGames,
                hasLastServerUser = hasLastServerUser
            )

            // Initialize Stockfish with manual stage settings (default)
            viewModelScope.launch {
                val ready = stockfish.initialize()
                if (ready) {
                    configureForManualStage()
                }
                _uiState.value = _uiState.value.copy(stockfishReady = ready)

                // Auto-load the last user's most recent game and start analysis
                // Skip on first run (after install or update) - user must make a choice first
                if (ready && !isFirstRun()) {
                    autoLoadLastGame()
                }
            }

            // Observe analysis results (only for Preview/Analyse stages - Manual stage handles its own updates)
            viewModelScope.launch {
                stockfish.analysisResult.collect { result ->
                    // In Manual stage, results are handled directly by ensureStockfishAnalysis
                    // to avoid race conditions. Only update UI here for other stages.
                    if (_uiState.value.currentStage != AnalysisStage.MANUAL) {
                        if (result != null) {
                            val expectedFen = currentAnalysisFen
                            if (expectedFen != null && expectedFen == _uiState.value.currentBoard.getFen()) {
                                _uiState.value = _uiState.value.copy(
                                    analysisResult = result,
                                    analysisResultFen = expectedFen
                                )
                            }
                        } else {
                            _uiState.value = _uiState.value.copy(
                                analysisResult = null,
                                analysisResultFen = null
                            )
                        }
                    }
                }
            }

            // Observe engine ready state
            viewModelScope.launch {
                stockfish.isReady.collect { ready ->
                    _uiState.value = _uiState.value.copy(stockfishReady = ready)
                }
            }
        }
    }

    /**
     * Check if Stockfish is installed. Returns true if installed.
     */
    fun checkStockfishInstalled(): Boolean {
        return stockfish.isStockfishInstalled()
    }

    /**
     * Initialize Stockfish after it has been installed.
     * Called when the app detects Stockfish was installed while on the "not installed" screen.
     */
    fun initializeStockfish() {
        val installed = stockfish.isStockfishInstalled()
        if (!installed) return

        _uiState.value = _uiState.value.copy(stockfishInstalled = true)

        // Reset settings to defaults on first run (fresh install or app update)
        if (isFirstRun()) {
            resetSettingsToDefaults()
        }

        // Load saved settings (will use defaults if reset or not previously set)
        val settings = loadStockfishSettings()
        val boardSettings = loadBoardLayoutSettings()
        val graphSettings = loadGraphSettings()
        val interfaceVisibility = loadInterfaceVisibilitySettings()
        val generalSettings = loadGeneralSettings()
        val lichessMaxGames = prefs.getInt(KEY_LICHESS_MAX_GAMES, 10)
        val chessComMaxGames = prefs.getInt(KEY_CHESSCOM_MAX_GAMES, 10)
        val hasLastServerUser = savedLastServer != null && savedLastUsername != null
        _uiState.value = _uiState.value.copy(
            stockfishSettings = settings,
            boardLayoutSettings = boardSettings,
            graphSettings = graphSettings,
            interfaceVisibility = interfaceVisibility,
            generalSettings = generalSettings,
            lichessMaxGames = lichessMaxGames,
            chessComMaxGames = chessComMaxGames,
            hasLastServerUser = hasLastServerUser
        )

        // Initialize Stockfish with manual stage settings (default)
        viewModelScope.launch {
            val ready = stockfish.initialize()
            if (ready) {
                configureForManualStage()
            }
            _uiState.value = _uiState.value.copy(stockfishReady = ready)

            // Auto-load the last user's most recent game and start analysis
            // Skip on first run (after install or update) - user must make a choice first
            if (ready && !isFirstRun()) {
                autoLoadLastGame()
            }
        }

        // Observe analysis results (only for Preview/Analyse stages - Manual stage handles its own updates)
        viewModelScope.launch {
            stockfish.analysisResult.collect { result ->
                // In Manual stage, results are handled directly by ensureStockfishAnalysis
                // to avoid race conditions. Only update UI here for other stages.
                if (_uiState.value.currentStage != AnalysisStage.MANUAL) {
                    if (result != null) {
                        val expectedFen = currentAnalysisFen
                        if (expectedFen != null && expectedFen == _uiState.value.currentBoard.getFen()) {
                            _uiState.value = _uiState.value.copy(
                                analysisResult = result,
                                analysisResultFen = expectedFen
                            )
                        }
                    } else {
                        _uiState.value = _uiState.value.copy(
                            analysisResult = null,
                            analysisResultFen = null
                        )
                    }
                }
            }
        }

        // Observe engine ready state
        viewModelScope.launch {
            stockfish.isReady.collect { ready ->
                _uiState.value = _uiState.value.copy(stockfishReady = ready)
            }
        }
    }

    /**
     * Automatically load a game and start analysis on app startup.
     * First tries to load the stored current game, then falls back to fetching
     * the most recent game from Lichess for DrNykterstein.
     */
    private suspend fun autoLoadLastGame() {
        // First, try to load the stored current game
        val storedGame = loadCurrentGame()
        if (storedGame != null) {
            loadGame(storedGame, null, null) // No server/user update needed for stored game
            return
        }

        // No stored game - fetch the last game from Lichess (default: DrNykterstein)
        val username = "DrNykterstein"

        _uiState.value = _uiState.value.copy(
            isLoading = true,
            errorMessage = null
        )

        // Fetch only 1 game (the most recent)
        when (val result = repository.getLichessGames(username, 1)) {
            is Result.Success -> {
                val games = result.data
                if (games.isNotEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        gameList = games,
                        showGameSelection = false
                    )
                    loadGame(games.first(), ChessServer.LICHESS, username)
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = null // No error, just no game to auto-load
                    )
                }
            }
            is Result.Error -> {
                // Don't show error on auto-load failure, just continue to manual mode
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = null
                )
            }
        }
    }

    /**
     * Reload the last game from the stored last server/user.
     * Called when user clicks the reload button.
     * Uses the saved "last server/user" to fetch fresh game.
     */
    fun reloadLastGame() {
        val server = savedLastServer ?: return
        val username = savedLastUsername ?: return

        viewModelScope.launch {
            fetchLastGameFromServer(server, username)
        }
    }

    /**
     * Fetch the most recent game from a specific server for a username.
     * Used by the reload button - always fetches fresh from the server.
     */
    private suspend fun fetchLastGameFromServer(server: ChessServer, username: String) {
        if (username.isBlank()) return

        _uiState.value = _uiState.value.copy(
            isLoading = true,
            errorMessage = null
        )

        val result = when (server) {
            ChessServer.LICHESS -> repository.getLichessGames(username, 1)
            ChessServer.CHESS_COM -> repository.getChessComGames(username, 1)
        }

        when (result) {
            is Result.Success -> {
                val games = result.data
                if (games.isNotEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        gameList = games,
                        showGameSelection = false
                    )
                    loadGame(games.first(), server, username)
                } else {
                    val serverName = if (server == ChessServer.LICHESS) "Lichess" else "Chess.com"
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "No games found for $username on $serverName"
                    )
                }
            }
            is Result.Error -> {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = result.message
                )
            }
        }
    }

    fun setLichessMaxGames(max: Int) {
        val validMax = max.coerceIn(1, 25)
        prefs.edit().putInt(KEY_LICHESS_MAX_GAMES, validMax).apply()
        _uiState.value = _uiState.value.copy(lichessMaxGames = validMax)
    }

    fun setChessComMaxGames(max: Int) {
        val validMax = max.coerceIn(1, 25)
        prefs.edit().putInt(KEY_CHESSCOM_MAX_GAMES, validMax).apply()
        _uiState.value = _uiState.value.copy(chessComMaxGames = validMax)
    }

    fun fetchGames(server: ChessServer, username: String, maxGames: Int) {
        // Save the username for next time
        when (server) {
            ChessServer.LICHESS -> prefs.edit().putString(KEY_LICHESS_USERNAME, username).apply()
            ChessServer.CHESS_COM -> prefs.edit().putString(KEY_CHESSCOM_USERNAME, username).apply()
        }

        // Mark first run complete - user has made their game retrieval choice
        markFirstRunComplete()

        // Cancel any ongoing auto-analysis
        autoAnalysisJob?.cancel()

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                errorMessage = null,
                game = null,
                gameList = emptyList(),
                showGameSelection = false
            )

            val result = when (server) {
                ChessServer.LICHESS -> repository.getLichessGames(username, maxGames)
                ChessServer.CHESS_COM -> repository.getChessComGames(username, maxGames)
            }

            when (result) {
                is Result.Success -> {
                    val games = result.data
                    if (games.size == 1) {
                        // Auto-select if only 1 game
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            gameList = games,
                            showGameSelection = false
                        )
                        loadGame(games.first(), server, username)
                    } else {
                        // Store server/username for when user selects a game
                        pendingGameSelectionServer = server
                        pendingGameSelectionUsername = username
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            gameList = games,
                            showGameSelection = true
                        )
                    }
                }
                is Result.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = result.message
                    )
                }
            }
        }
    }

    // Temporary storage for server/username when showing game selection dialog
    private var pendingGameSelectionServer: ChessServer? = null
    private var pendingGameSelectionUsername: String? = null

    fun selectGame(game: LichessGame) {
        _uiState.value = _uiState.value.copy(showGameSelection = false)
        val server = pendingGameSelectionServer
        val username = pendingGameSelectionUsername
        pendingGameSelectionServer = null
        pendingGameSelectionUsername = null
        loadGame(game, server, username)
    }

    fun dismissGameSelection() {
        _uiState.value = _uiState.value.copy(showGameSelection = false)
    }

    fun clearGame() {
        // Stop any ongoing auto-analysis
        autoAnalysisJob?.cancel()
        stockfish.stop()

        // Clear game state and return to search screen
        boardHistory.clear()
        exploringLineHistory.clear()
        _uiState.value = _uiState.value.copy(
            game = null,
            gameList = emptyList(),
            showGameSelection = false,
            currentBoard = ChessBoard(),
            moves = emptyList(),
            moveDetails = emptyList(),
            currentMoveIndex = -1,
            analysisResult = null,
            flippedBoard = false,
            userPlayedBlack = false,
            isExploringLine = false,
            exploringLineMoves = emptyList(),
            exploringLineMoveIndex = -1,
            savedGameMoveIndex = -1,
            currentStage = AnalysisStage.PREVIEW,
            previewScores = emptyMap(),
            analyseScores = emptyMap(),
            autoAnalysisIndex = -1
        )
    }

    private fun loadGame(game: LichessGame, server: ChessServer?, username: String?) {
        // Cancel any ongoing analysis before loading new game
        autoAnalysisJob?.cancel()
        manualAnalysisJob?.cancel()
        stockfish.stop()

        val pgn = game.pgn
        if (pgn == null) {
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                errorMessage = "No PGN data available"
            )
            return
        }

        // Save the server/username as "last server/user" for reload button
        if (server != null && username != null) {
            prefs.edit()
                .putString(KEY_LAST_SERVER, server.name)
                .putString(KEY_LAST_USERNAME, username)
                .apply()
            _uiState.value = _uiState.value.copy(hasLastServerUser = true)
        }

        // Extract opening name from PGN headers
        val pgnHeaders = PgnParser.parseHeaders(pgn)
        val openingName = pgnHeaders["Opening"] ?: pgnHeaders["ECO"]

        val parsedMoves = PgnParser.parseMovesWithClock(pgn)
        val initialBoard = ChessBoard()
        boardHistory.clear()
        exploringLineHistory.clear()
        boardHistory.add(initialBoard.copy())

        // Pre-compute all board positions and move details for efficient navigation
        val tempBoard = ChessBoard()
        val moveDetailsList = mutableListOf<MoveDetails>()
        val validMoves = mutableListOf<String>()

        for ((index, parsedMove) in parsedMoves.withIndex()) {
            val move = parsedMove.san
            val moveNum = (index / 2) + 1
            val isWhite = index % 2 == 0
            // Check if this move is a capture (target square has a piece before the move)
            val boardBeforeMove = tempBoard.copy()
            val moveSuccess = tempBoard.makeMove(move)
            if (!moveSuccess) {
                // Skip invalid moves (e.g., malformed PGN artifacts)
                val prefix = if (isWhite) "$moveNum." else "$moveNum..."
                android.util.Log.e("GameViewModel", "FAILED to apply move $prefix $move - FEN: ${boardBeforeMove.getFen()}")
                continue
            }
            validMoves.add(move)
            boardHistory.add(tempBoard.copy())

            // Get move details from the board's last move
            val lastMove = tempBoard.getLastMove()
            if (lastMove != null) {
                val fromSquare = lastMove.from.toAlgebraic()
                val toSquare = lastMove.to.toAlgebraic()
                val capturedPiece = boardBeforeMove.getPiece(lastMove.to)
                val movedPiece = tempBoard.getPiece(lastMove.to)
                val pieceType = when (movedPiece?.type) {
                    com.chessreplay.chess.PieceType.KING -> "K"
                    com.chessreplay.chess.PieceType.QUEEN -> "Q"
                    com.chessreplay.chess.PieceType.ROOK -> "R"
                    com.chessreplay.chess.PieceType.BISHOP -> "B"
                    com.chessreplay.chess.PieceType.KNIGHT -> "N"
                    com.chessreplay.chess.PieceType.PAWN -> "P"
                    else -> "P"
                }
                // Check for en passant capture (pawn capture but no piece on target square)
                val isEnPassant = pieceType == "P" &&
                    lastMove.from.file != lastMove.to.file &&
                    capturedPiece == null
                val isCapture = capturedPiece != null || isEnPassant

                moveDetailsList.add(MoveDetails(
                    san = move,
                    from = fromSquare,
                    to = toSquare,
                    isCapture = isCapture,
                    pieceType = pieceType,
                    clockTime = parsedMove.clockTime
                ))
            }
        }

        // Flip board if the searched user played black
        // Use the username parameter if provided, otherwise fall back to saved Lichess username
        val searchedUser = (username ?: savedLichessUsername).lowercase()
        val blackPlayerName = game.players.black.user?.name?.lowercase() ?: ""
        val userPlayedBlack = searchedUser.isNotEmpty() && searchedUser == blackPlayerName

        _uiState.value = _uiState.value.copy(
            isLoading = false,
            game = game,
            openingName = openingName,
            moves = validMoves,
            moveDetails = moveDetailsList,
            currentBoard = initialBoard,
            currentMoveIndex = -1,
            flippedBoard = userPlayedBlack,
            userPlayedBlack = userPlayedBlack,
            // Reset exploring state
            isExploringLine = false,
            exploringLineMoves = emptyList(),
            exploringLineMoveIndex = -1,
            savedGameMoveIndex = -1,
            // Reset analysis state - start at Preview stage
            currentStage = AnalysisStage.PREVIEW,
            previewScores = emptyMap(),
            analyseScores = emptyMap(),
            autoAnalysisIndex = -1
        )

        // Save this game as the current game for next app startup
        saveCurrentGame(game)

        // Start analysis - runs Preview stage, then Analyse stage, then enters Manual stage
        startAnalysis()
    }

    /**
     * Check if navigation is allowed in the current stage.
     * Preview stage: not interruptible, navigation not allowed
     * Analyse stage: interruptible, will switch to Manual stage
     * Manual stage: navigation always allowed
     */
    private fun canNavigate(): Boolean {
        return _uiState.value.currentStage != AnalysisStage.PREVIEW
    }

    /**
     * Handle navigation during Analyse stage - interrupts analysis and switches to Manual stage.
     * Returns true if we should proceed with navigation, false if blocked.
     */
    private fun handleNavigationInterrupt(): Boolean {
        when (_uiState.value.currentStage) {
            AnalysisStage.PREVIEW -> return false  // Preview stage is not interruptible
            AnalysisStage.ANALYSE -> {
                // Interrupt analyse stage and switch to manual
                enterManualStageAtCurrentPosition()
                return false  // Don't proceed - enterManualStageAtCurrentPosition handles navigation
            }
            AnalysisStage.MANUAL -> return true  // Allow navigation in manual stage
        }
    }

    fun goToStart() {
        if (!handleNavigationInterrupt()) return

        if (_uiState.value.isExploringLine) {
            val newBoard = exploringLineHistory.firstOrNull()?.copy() ?: ChessBoard()
            _uiState.value = _uiState.value.copy(
                currentBoard = newBoard,
                exploringLineMoveIndex = -1
            )
            analyzePosition(newBoard)
        } else {
            // In manual stage, use restartAnalysisAtMove for reliable sync
            if (_uiState.value.currentStage == AnalysisStage.MANUAL) {
                restartAnalysisAtMove(-1)
            } else {
                val newBoard = boardHistory.firstOrNull()?.copy() ?: ChessBoard()
                _uiState.value = _uiState.value.copy(
                    currentBoard = newBoard,
                    currentMoveIndex = -1
                )
                analyzePosition(newBoard)
            }
        }
    }

    fun goToEnd() {
        if (!handleNavigationInterrupt()) return

        if (_uiState.value.isExploringLine) {
            val moves = _uiState.value.exploringLineMoves
            if (moves.isEmpty()) {
                analyzePosition(_uiState.value.currentBoard)
                return
            }
            val newBoard = exploringLineHistory.lastOrNull()?.copy() ?: ChessBoard()
            _uiState.value = _uiState.value.copy(
                currentBoard = newBoard,
                exploringLineMoveIndex = moves.size - 1
            )
            analyzePosition(newBoard)
        } else {
            val moves = _uiState.value.moves
            if (moves.isEmpty()) return
            // In manual stage, use restartAnalysisAtMove for reliable sync
            if (_uiState.value.currentStage == AnalysisStage.MANUAL) {
                restartAnalysisAtMove(moves.size - 1)
            } else {
                val newBoard = boardHistory.lastOrNull()?.copy() ?: ChessBoard()
                _uiState.value = _uiState.value.copy(
                    currentBoard = newBoard,
                    currentMoveIndex = moves.size - 1
                )
                analyzePosition(newBoard)
            }
        }
    }

    fun goToMove(index: Int) {
        if (!handleNavigationInterrupt()) return

        val newBoard: ChessBoard
        if (_uiState.value.isExploringLine) {
            val moves = _uiState.value.exploringLineMoves
            if (index < -1 || index >= moves.size) return
            newBoard = exploringLineHistory.getOrNull(index + 1)?.copy() ?: ChessBoard()
            _uiState.value = _uiState.value.copy(
                currentBoard = newBoard,
                exploringLineMoveIndex = index
            )
        } else {
            val moves = _uiState.value.moves
            if (index < -1 || index >= moves.size) return
            newBoard = boardHistory.getOrNull(index + 1)?.copy() ?: ChessBoard()
            _uiState.value = _uiState.value.copy(
                currentBoard = newBoard,
                currentMoveIndex = index
            )
        }
        // Pass the exact board we just set to avoid any race conditions
        analyzePosition(newBoard)
    }

    /**
     * Restart analysis at a specific move - stops Stockfish and starts fresh.
     * Used for graph clicks in manual stage to ensure clean state.
     */
    fun restartAnalysisAtMove(moveIndex: Int) {
        // Cancel any running analysis
        manualAnalysisJob?.cancel()

        // Get the board for the clicked position
        val validIndex = moveIndex.coerceIn(-1, boardHistory.size - 2)
        val board = boardHistory.getOrNull(validIndex + 1) ?: ChessBoard()

        viewModelScope.launch {
            // Stop Stockfish completely
            stockfish.stop()

            // Increment request ID to invalidate any pending results
            analysisRequestId++
            val thisRequestId = analysisRequestId

            // Set up the new position
            val fenToAnalyze = board.getFen()
            currentAnalysisFen = fenToAnalyze

            // Update UI state - keep analysisResult to avoid UI jumping, just clear the FEN
            // The card will stay visible with old content until new results arrive
            _uiState.value = _uiState.value.copy(
                currentMoveIndex = validIndex,
                currentBoard = board.copy(),
                analysisResultFen = null  // Mark as stale, but keep result for UI stability
            )

            // Small delay to ensure Stockfish has stopped
            delay(50)

            // Send new game command to clear Stockfish's internal state
            stockfish.newGame()
            delay(50)

            // Start fresh analysis
            if (_uiState.value.currentStage == AnalysisStage.MANUAL) {
                ensureStockfishAnalysis(fenToAnalyze, thisRequestId)
            }
        }
    }

    fun nextMove() {
        if (!handleNavigationInterrupt()) return

        if (_uiState.value.isExploringLine) {
            val currentIndex = _uiState.value.exploringLineMoveIndex
            val moves = _uiState.value.exploringLineMoves
            if (currentIndex >= moves.size - 1) return
            val newIndex = currentIndex + 1
            val newBoard = exploringLineHistory.getOrNull(newIndex + 1)?.copy() ?: _uiState.value.currentBoard
            _uiState.value = _uiState.value.copy(
                currentBoard = newBoard,
                exploringLineMoveIndex = newIndex
            )
            // Use full restart for proper Stockfish analysis
            restartAnalysisForExploringLine()
        } else {
            val currentIndex = _uiState.value.currentMoveIndex
            val moves = _uiState.value.moves
            if (currentIndex >= moves.size - 1) return
            // In manual stage, use restartAnalysisAtMove for reliable sync
            if (_uiState.value.currentStage == AnalysisStage.MANUAL) {
                restartAnalysisAtMove(currentIndex + 1)
            } else {
                val newIndex = currentIndex + 1
                val newBoard = boardHistory.getOrNull(newIndex + 1)?.copy() ?: _uiState.value.currentBoard
                _uiState.value = _uiState.value.copy(
                    currentBoard = newBoard,
                    currentMoveIndex = newIndex
                )
                analyzePosition(newBoard)
            }
        }
    }

    fun prevMove() {
        if (!handleNavigationInterrupt()) return

        if (_uiState.value.isExploringLine) {
            val currentIndex = _uiState.value.exploringLineMoveIndex
            if (currentIndex < 0) return
            val newIndex = currentIndex - 1
            val newBoard = exploringLineHistory.getOrNull(newIndex + 1)?.copy() ?: ChessBoard()
            _uiState.value = _uiState.value.copy(
                currentBoard = newBoard,
                exploringLineMoveIndex = newIndex
            )
            // Use full restart for proper Stockfish analysis
            restartAnalysisForExploringLine()
        } else {
            val currentIndex = _uiState.value.currentMoveIndex
            if (currentIndex < 0) return
            // In manual stage, use restartAnalysisAtMove for reliable sync
            if (_uiState.value.currentStage == AnalysisStage.MANUAL) {
                restartAnalysisAtMove(currentIndex - 1)
            } else {
                val newIndex = currentIndex - 1
                val newBoard = boardHistory.getOrNull(newIndex + 1)?.copy() ?: ChessBoard()
                _uiState.value = _uiState.value.copy(
                    currentBoard = newBoard,
                    currentMoveIndex = newIndex
                )
                analyzePosition(newBoard)
            }
        }
    }

    fun exploreLine(pv: String, moveIndex: Int = 0) {
        if (pv.isBlank()) return

        // Save current game position
        val savedMoveIndex = _uiState.value.currentMoveIndex

        // Get the starting board (current position before exploring)
        val startBoard = _uiState.value.currentBoard.copy()

        // Parse UCI moves and build board history for the line
        val uciMoves = pv.split(" ").filter { it.isNotBlank() }
        exploringLineHistory.clear()
        exploringLineHistory.add(startBoard)

        val tempBoard = startBoard.copy()
        for (uciMove in uciMoves) {
            if (tempBoard.makeUciMove(uciMove)) {
                exploringLineHistory.add(tempBoard.copy())
            } else {
                break // Invalid move, stop here
            }
        }

        // Go to the specified move index
        val targetIndex = moveIndex.coerceIn(-1, exploringLineHistory.size - 2)

        _uiState.value = _uiState.value.copy(
            isExploringLine = true,
            exploringLineMoves = uciMoves.take(exploringLineHistory.size - 1),
            exploringLineMoveIndex = targetIndex,
            savedGameMoveIndex = savedMoveIndex,
            currentBoard = exploringLineHistory.getOrNull(targetIndex + 1)?.copy() ?: startBoard
        )

        // Use full restart for proper Stockfish analysis
        restartAnalysisForExploringLine()
    }

    fun backToOriginalGame() {
        val savedIndex = _uiState.value.savedGameMoveIndex
        exploringLineHistory.clear()

        _uiState.value = _uiState.value.copy(
            isExploringLine = false,
            exploringLineMoves = emptyList(),
            exploringLineMoveIndex = -1,
            savedGameMoveIndex = -1,
            currentBoard = boardHistory.getOrNull(savedIndex + 1)?.copy() ?: ChessBoard(),
            currentMoveIndex = savedIndex
        )

        // Use full restart for proper Stockfish analysis
        restartAnalysisForExploringLine()
    }

    fun setAnalysisEnabled(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(analysisEnabled = enabled)
        if (enabled) {
            // Use full restart for proper Stockfish analysis
            restartAnalysisForExploringLine()
        } else {
            stockfish.stop()
        }
    }

    fun flipBoard() {
        _uiState.value = _uiState.value.copy(flippedBoard = !_uiState.value.flippedBoard)
    }

    fun cycleArrowMode() {
        val currentSettings = _uiState.value.stockfishSettings
        val currentMode = currentSettings.manualStage.arrowMode
        val newMode = when (currentMode) {
            ArrowMode.NONE -> ArrowMode.MAIN_LINE
            ArrowMode.MAIN_LINE -> ArrowMode.MULTI_LINES
            ArrowMode.MULTI_LINES -> ArrowMode.NONE
        }
        val newSettings = currentSettings.copy(
            manualStage = currentSettings.manualStage.copy(arrowMode = newMode)
        )
        saveStockfishSettings(newSettings)
        _uiState.value = _uiState.value.copy(stockfishSettings = newSettings)
    }

    fun showSettingsDialog() {
        // Store current settings to detect changes when dialog closes
        settingsOnDialogOpen = SettingsSnapshot(
            previewStageSettings = _uiState.value.stockfishSettings.previewStage,
            analyseStageSettings = _uiState.value.stockfishSettings.analyseStage,
            manualStageSettings = _uiState.value.stockfishSettings.manualStage
        )
        _uiState.value = _uiState.value.copy(showSettingsDialog = true)
    }

    fun hideSettingsDialog() {
        _uiState.value = _uiState.value.copy(showSettingsDialog = false)

        // Check what settings changed
        val originalSettings = settingsOnDialogOpen
        val currentPreviewStageSettings = _uiState.value.stockfishSettings.previewStage
        val currentAnalyseStageSettings = _uiState.value.stockfishSettings.analyseStage
        val currentManualStageSettings = _uiState.value.stockfishSettings.manualStage

        val previewStageSettingsChanged = originalSettings?.previewStageSettings != currentPreviewStageSettings
        val analyseStageSettingsChanged = originalSettings?.analyseStageSettings != currentAnalyseStageSettings
        val manualStageSettingsChanged = originalSettings?.manualStageSettings != currentManualStageSettings

        // Clear the snapshot
        settingsOnDialogOpen = null

        // If no game loaded or no settings changed, nothing to do
        if (_uiState.value.game == null) return
        if (!previewStageSettingsChanged && !analyseStageSettingsChanged && !manualStageSettingsChanged) return

        viewModelScope.launch {
            // Stop any ongoing analysis
            autoAnalysisJob?.cancel()
            stockfish.stop()

            // Set stockfishReady to false while restarting
            _uiState.value = _uiState.value.copy(stockfishReady = false)

            // Kill and restart Stockfish engine
            val ready = stockfish.restart()

            // Verify Stockfish is truly ready by checking isReady flow
            if (ready) {
                // Wait a moment for the engine to stabilize
                kotlinx.coroutines.delay(200)
                val confirmedReady = stockfish.isReady.value
                _uiState.value = _uiState.value.copy(stockfishReady = confirmedReady)

                if (!confirmedReady) {
                    return@launch
                }
            } else {
                _uiState.value = _uiState.value.copy(stockfishReady = false)
                return@launch
            }

            // Decide which mode to activate based on what changed
            if (previewStageSettingsChanged || analyseStageSettingsChanged) {
                // Stockfish stage settings changed
                // -> Restart analysis from Preview stage
                _uiState.value = _uiState.value.copy(
                    currentStage = AnalysisStage.PREVIEW,
                    previewScores = emptyMap(),
                    analyseScores = emptyMap()
                )
                startAnalysis()
            } else if (manualStageSettingsChanged) {
                // Only Manual stage settings changed
                // -> If in Manual stage, just reconfigure; otherwise enter Manual stage
                if (_uiState.value.currentStage == AnalysisStage.MANUAL) {
                    configureForManualStage()
                    // Use full restart for proper Stockfish analysis
                    restartAnalysisForExploringLine()
                } else {
                    enterManualStageAtCurrentPosition()
                }
            }
        }
    }

    fun showHelpScreen() {
        _uiState.value = _uiState.value.copy(showHelpScreen = true)
    }

    fun hideHelpScreen() {
        _uiState.value = _uiState.value.copy(showHelpScreen = false)
    }

    fun updateStockfishSettings(settings: StockfishSettings) {
        saveStockfishSettings(settings)
        _uiState.value = _uiState.value.copy(
            stockfishSettings = settings
        )
        // Apply new settings to Stockfish based on current stage
        if (_uiState.value.stockfishReady) {
            when (_uiState.value.currentStage) {
                AnalysisStage.PREVIEW -> configureForPreviewStage()
                AnalysisStage.ANALYSE -> configureForAnalyseStage()
                AnalysisStage.MANUAL -> configureForManualStage()
            }
            if (_uiState.value.currentStage == AnalysisStage.MANUAL) {
                // Use full restart for proper Stockfish analysis
                restartAnalysisForExploringLine()
            }
        }
    }

    fun updateBoardLayoutSettings(settings: BoardLayoutSettings) {
        saveBoardLayoutSettings(settings)
        _uiState.value = _uiState.value.copy(
            boardLayoutSettings = settings
        )
    }

    fun updateGraphSettings(settings: GraphSettings) {
        saveGraphSettings(settings)
        _uiState.value = _uiState.value.copy(
            graphSettings = settings
        )
    }

    fun updateInterfaceVisibilitySettings(settings: InterfaceVisibilitySettings) {
        val currentSettings = _uiState.value.interfaceVisibility

        // Check if Preview or Analyse stage visibility changed
        val previewChanged = currentSettings.previewStage != settings.previewStage
        val analyseChanged = currentSettings.analyseStage != settings.analyseStage

        saveInterfaceVisibilitySettings(settings)
        _uiState.value = _uiState.value.copy(
            interfaceVisibility = settings
        )

        // If Preview or Analyse stage visibility changed, restart from Preview stage
        if ((previewChanged || analyseChanged) && _uiState.value.game != null) {
            // Cancel any ongoing analysis
            autoAnalysisJob?.cancel()
            manualAnalysisJob?.cancel()
            stockfish.stop()

            // Reset to Preview stage and restart analysis
            _uiState.value = _uiState.value.copy(
                currentStage = AnalysisStage.PREVIEW,
                previewScores = emptyMap(),
                analyseScores = emptyMap(),
                autoAnalysisIndex = -1
            )

            // Restart analysis from Preview stage
            viewModelScope.launch {
                val ready = stockfish.restart()
                _uiState.value = _uiState.value.copy(stockfishReady = ready)
                if (ready) {
                    stockfish.newGame()
                    startAnalysis()
                }
            }
        }
    }

    fun updateGeneralSettings(settings: GeneralSettings) {
        saveGeneralSettings(settings)
        _uiState.value = _uiState.value.copy(
            generalSettings = settings
        )
    }

    /**
     * Toggle full screen mode via long tap.
     * Directly toggles the fullScreenMode setting.
     */
    fun toggleFullScreen() {
        val currentSettings = _uiState.value.generalSettings
        val newSettings = currentSettings.copy(
            longTapForFullScreen = !currentSettings.longTapForFullScreen
        )
        _uiState.value = _uiState.value.copy(
            generalSettings = newSettings
        )
    }

    private var manualAnalysisJob: Job? = null
    private var currentAnalysisFen: String? = null  // Track which FEN is being analyzed
    private var analysisRequestId: Long = 0  // Incremented for each new analysis request

    /**
     * Analyze the current position from UI state.
     * Use analyzePosition(board) when you have the board directly to avoid race conditions.
     */
    private fun analyzeCurrentPosition() {
        analyzePosition(_uiState.value.currentBoard)
    }

    /**
     * Analyze a specific board position.
     * This is the preferred method when you have the board directly (e.g., after navigation).
     */
    private fun analyzePosition(board: ChessBoard) {
        if (!_uiState.value.analysisEnabled) return

        // Cancel any previous manual analysis job
        manualAnalysisJob?.cancel()

        // Increment request ID to invalidate any pending results from old analyses
        analysisRequestId++
        val thisRequestId = analysisRequestId

        // Track which position we're analyzing and clear old result
        val fenToAnalyze = board.getFen()
        currentAnalysisFen = fenToAnalyze
        _uiState.value = _uiState.value.copy(analysisResult = null, analysisResultFen = null)

        // Only run manual analysis in Manual stage
        if (_uiState.value.currentStage != AnalysisStage.MANUAL) {
            return
        }

        // In manual stage: ensure Stockfish card is shown - pass the FEN and request ID
        manualAnalysisJob = viewModelScope.launch {
            ensureStockfishAnalysis(fenToAnalyze, thisRequestId)
        }
    }

    /**
     * Ensure Stockfish analysis is running and producing results in manual stage.
     * If no results come back, restart Stockfish and try again.
     * @param fen The FEN position to analyze (captured at call time to avoid race conditions)
     * @param requestId The request ID to validate results against
     */
    private suspend fun ensureStockfishAnalysis(fen: String, requestId: Long) {
        val maxRetries = 2
        var attempt = 0

        while (attempt < maxRetries) {
            // Check if Stockfish is ready, restart if not
            if (!_uiState.value.stockfishReady) {
                val ready = stockfish.restart()
                _uiState.value = _uiState.value.copy(stockfishReady = ready)
                if (!ready) {
                    attempt++
                    continue
                }
                configureForManualStage()
            }

            // Start analysis with the FEN that was captured when this analysis was requested
            val depth = _uiState.value.stockfishSettings.manualStage.depth
            stockfish.analyze(fen, depth)

            // Wait for results (up to 2 seconds)
            var waitTime = 0
            val maxWaitTime = 2000
            val checkInterval = 50L

            var gotFirstResult = false
            while (true) {
                delay(checkInterval)

                // Check if a new analysis request was started - abort this one
                if (analysisRequestId != requestId) {
                    return // User navigated away, a new analysis will be started
                }

                // If we're no longer in manual stage, abort
                if (_uiState.value.currentStage != AnalysisStage.MANUAL) {
                    return
                }

                // Check if we got results directly from Stockfish
                val result = stockfish.analysisResult.value
                if (result != null) {
                    // Double-check request ID before updating UI
                    if (analysisRequestId == requestId) {
                        _uiState.value = _uiState.value.copy(
                            analysisResult = result,
                            analysisResultFen = fen
                        )
                        gotFirstResult = true
                    } else {
                        return // Request changed while checking
                    }
                }

                // If we haven't got any result after timeout, break to retry
                if (!gotFirstResult) {
                    waitTime += checkInterval.toInt()
                    if (waitTime >= maxWaitTime) {
                        break
                    }
                }
            }

            // No results after waiting - restart Stockfish and try again
            android.util.Log.w("GameViewModel", "No Stockfish results after ${maxWaitTime}ms, restarting (attempt ${attempt + 1})")

            stockfish.stop()
            _uiState.value = _uiState.value.copy(stockfishReady = false)

            val ready = stockfish.restart()
            _uiState.value = _uiState.value.copy(stockfishReady = ready)

            if (ready) {
                configureForManualStage()
            }

            attempt++
        }

        // Failed to get Stockfish analysis after max retries
    }

    /**
     * Build the list of move indices for analysis based on the current stage.
     * Preview stage: Forward sequence (move 1 to end)
     * Analyse stage: Backwards sequence (end to move 1), unless board is visible then forward
     */
    private fun buildMoveIndices(): List<Int> {
        val moves = _uiState.value.moves
        val showBoardInAnalyse = _uiState.value.interfaceVisibility.analyseStage.showBoard
        return when (_uiState.value.currentStage) {
            AnalysisStage.PREVIEW -> (0 until moves.size).toList()  // Forward
            AnalysisStage.ANALYSE -> if (showBoardInAnalyse) {
                (0 until moves.size).toList()  // Forward when board is visible
            } else {
                (moves.size - 1 downTo 0).toList()  // Backwards when board is hidden
            }
            AnalysisStage.MANUAL -> emptyList()  // No auto-analysis in manual stage
        }
    }

    /**
     * Start the three-stage analysis flow: Preview → Analyse → Manual.
     * Each stage kills the current Stockfish process and starts a new one with appropriate settings.
     */
    private fun startAnalysis() {
        if (!_uiState.value.stockfishReady) return

        // Cancel any previous analysis
        autoAnalysisJob?.cancel()

        autoAnalysisJob = viewModelScope.launch {
            try {
                val moves = _uiState.value.moves
                if (moves.isEmpty()) {
                    android.util.Log.e("Analysis", "EXIT: moves list is empty")
                    enterManualStageInternal(-1)
                    return@launch
                }

                // Store expected board history size to detect if game was reloaded
                val expectedBoardHistorySize = boardHistory.size
                android.util.Log.d("Analysis", "START: moves=${moves.size}, boardHistory=$expectedBoardHistorySize")

                // ===== PREVIEW STAGE =====
                android.util.Log.d("Analysis", "Starting PREVIEW stage")
                _uiState.value = _uiState.value.copy(
                    currentStage = AnalysisStage.PREVIEW,
                    previewScores = emptyMap(),
                    analyseScores = emptyMap(),
                    autoAnalysisCurrentScore = null,
                    remainingAnalysisMoves = buildMoveIndices()
                )

                // Kill current Stockfish and start new one for Preview stage
                stockfish.stop()
                var ready = stockfish.restart()
                _uiState.value = _uiState.value.copy(stockfishReady = ready)
                if (!ready) {
                    android.util.Log.e("Analysis", "Failed to start Stockfish for Preview stage")
                    enterManualStageInternal(-1)
                    return@launch
                }

                stockfish.newGame()
                configureForPreviewStage()
                delay(50)

                val previewTimeMs = (_uiState.value.stockfishSettings.previewStage.secondsForMove * 1000).toInt()
                val previewComplete = runStageAnalysis(
                    stageName = "PREVIEW",
                    timePerMoveMs = previewTimeMs,
                    expectedBoardHistorySize = expectedBoardHistorySize,
                    storeScore = { moveIndex, score ->
                        _uiState.value = _uiState.value.copy(
                            previewScores = _uiState.value.previewScores + (moveIndex to score),
                            autoAnalysisCurrentScore = score
                        )
                    },
                    configureEngine = { configureForPreviewStage() }
                )

                if (!previewComplete) {
                    android.util.Log.d("Analysis", "Preview stage was interrupted or failed")
                    return@launch
                }

                // ===== ANALYSE STAGE =====
                android.util.Log.d("Analysis", "Starting ANALYSE stage")
                _uiState.value = _uiState.value.copy(
                    currentStage = AnalysisStage.ANALYSE,
                    autoAnalysisCurrentScore = null,
                    remainingAnalysisMoves = buildMoveIndices()
                )

                // Kill current Stockfish and start new one for Analyse stage
                stockfish.stop()
                ready = stockfish.restart()
                _uiState.value = _uiState.value.copy(stockfishReady = ready)
                if (!ready) {
                    android.util.Log.e("Analysis", "Failed to start Stockfish for Analyse stage")
                    enterManualStageInternal(findBiggestScoreChangeMove())
                    return@launch
                }

                stockfish.newGame()
                configureForAnalyseStage()
                delay(50)

                val analyseTimeMs = (_uiState.value.stockfishSettings.analyseStage.secondsForMove * 1000).toInt()
                val analyseComplete = runStageAnalysis(
                    stageName = "ANALYSE",
                    timePerMoveMs = analyseTimeMs,
                    expectedBoardHistorySize = expectedBoardHistorySize,
                    storeScore = { moveIndex, score ->
                        _uiState.value = _uiState.value.copy(
                            analyseScores = _uiState.value.analyseScores + (moveIndex to score),
                            autoAnalysisCurrentScore = score
                        )
                    },
                    configureEngine = { configureForAnalyseStage() }
                )

                if (!analyseComplete) {
                    android.util.Log.d("Analysis", "Analyse stage was interrupted")
                    return@launch
                }

                // ===== MANUAL STAGE =====
                android.util.Log.d("Analysis", "Analysis complete, entering MANUAL stage")
                val biggestChangeMoveIndex = findBiggestScoreChangeMove()
                enterManualStageInternal(biggestChangeMoveIndex)

            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e // Re-throw to properly cancel
            } catch (e: Exception) {
                android.util.Log.e("Analysis", "Error during analysis: ${e.message}")
                // Enter manual stage on error
                _uiState.value = _uiState.value.copy(
                    currentStage = AnalysisStage.MANUAL,
                    autoAnalysisIndex = -1
                )
            }
        }
    }

    /**
     * Run a single stage of analysis (Preview or Analyse).
     * Returns true if completed successfully, false if interrupted or failed.
     */
    private suspend fun runStageAnalysis(
        stageName: String,
        timePerMoveMs: Int,
        expectedBoardHistorySize: Int,
        storeScore: (Int, MoveScore) -> Unit,
        configureEngine: () -> Unit
    ): Boolean {
        val moveIndices = buildMoveIndices()
        android.util.Log.d("Analysis", "$stageName: analyzing ${moveIndices.size} moves, time=${timePerMoveMs}ms")

        val remainingMoves = moveIndices.toMutableList()
        var analyzedCount = 0

        for (moveIndex in moveIndices) {
            // Check for cancellation
            kotlinx.coroutines.yield()

            // Check if board history was modified (game reloaded)
            if (boardHistory.size != expectedBoardHistorySize) {
                android.util.Log.e("Analysis", "$stageName EXIT: boardHistory changed")
                return false
            }

            // Get the board position after this move
            val board = boardHistory.getOrNull(moveIndex + 1) ?: continue

            // Update remaining moves
            remainingMoves.remove(moveIndex)

            // Update UI state
            _uiState.value = _uiState.value.copy(
                autoAnalysisIndex = moveIndex,
                currentBoard = board,
                currentMoveIndex = moveIndex,
                autoAnalysisCurrentScore = null,
                analysisResult = null,
                remainingAnalysisMoves = remainingMoves.toList()
            )

            val fen = board.getFen()

            // Start analysis with time limit
            stockfish.analyzeWithTime(fen, timePerMoveMs)

            // Wait for completion
            val completed = stockfish.waitForCompletion(timePerMoveMs.toLong() + 2000)
            if (!completed) {
                stockfish.stop()
                delay(100)
            }

            // Check if engine crashed and restart if needed
            if (!stockfish.isReady.value) {
                android.util.Log.w("Analysis", "$stageName: Engine died at move $moveIndex, restarting...")
                val restarted = stockfish.restart()
                if (restarted) {
                    stockfish.newGame()
                    configureEngine()
                    delay(100)

                    // Retry the failed move
                    stockfish.analyzeWithTime(fen, timePerMoveMs)
                    val retryCompleted = stockfish.waitForCompletion(timePerMoveMs.toLong() + 2000)
                    if (!retryCompleted) {
                        stockfish.stop()
                        delay(100)
                    }
                } else {
                    android.util.Log.e("Analysis", "$stageName: Failed to restart engine")
                    return false
                }
            }

            // Check for cancellation after waiting
            kotlinx.coroutines.yield()

            // Check if board history was modified during wait
            if (boardHistory.size != expectedBoardHistorySize) {
                android.util.Log.e("Analysis", "$stageName EXIT after wait: boardHistory changed")
                return false
            }

            // Get the current analysis result and store the score
            val result = stockfish.analysisResult.value
            if (result != null) {
                val bestLine = result.bestLine
                if (bestLine != null) {
                    analyzedCount++
                    // Score adjustment: Stockfish gives score from side-to-move's perspective
                    // We want score from WHITE's perspective (positive = good for white)
                    val isWhiteToMove = board.getTurn() == com.chessreplay.chess.PieceColor.WHITE
                    val adjustedScore = if (isWhiteToMove) bestLine.score else -bestLine.score
                    val adjustedMateIn = if (isWhiteToMove) bestLine.mateIn else -bestLine.mateIn

                    val score = MoveScore(
                        score = adjustedScore,
                        isMate = bestLine.isMate,
                        mateIn = adjustedMateIn,
                        depth = result.depth,
                        nodes = result.nodes
                    )
                    storeScore(moveIndex, score)
                }
            }
        }

        android.util.Log.d("Analysis", "$stageName completed: analyzed=$analyzedCount out of ${moveIndices.size} moves")
        return true
    }

    /**
     * Find the move index with the biggest score change compared to the previous move.
     * Uses analyse scores if available, otherwise preview scores.
     */
    private fun findBiggestScoreChangeMove(): Int {
        val scores = _uiState.value.analyseScores.ifEmpty { _uiState.value.previewScores }
        if (scores.size < 2) return 0

        var maxChange = 0f
        var maxChangeIndex = 0

        val sortedIndices = scores.keys.sorted()
        for (i in 1 until sortedIndices.size) {
            val currentIndex = sortedIndices[i]
            val prevIndex = sortedIndices[i - 1]
            val currentScore = scores[currentIndex]?.score ?: continue
            val prevScore = scores[prevIndex]?.score ?: continue

            val change = kotlin.math.abs(currentScore - prevScore)
            if (change > maxChange) {
                maxChange = change
                maxChangeIndex = currentIndex
            }
        }

        return maxChangeIndex
    }

    /**
     * Internal function to enter Manual stage at a specific move.
     * Kills current Stockfish and starts a new one configured for Manual stage.
     */
    private fun enterManualStageInternal(moveIndex: Int) {
        viewModelScope.launch {
            // Stop any running analysis
            autoAnalysisJob?.cancel()
            stockfish.stop()

            // Navigate to the specified move
            val validIndex = moveIndex.coerceIn(-1, boardHistory.size - 2)
            val board = boardHistory.getOrNull(validIndex + 1) ?: ChessBoard()

            val fenToAnalyze = board.getFen()
            currentAnalysisFen = fenToAnalyze
            analysisRequestId++
            val thisRequestId = analysisRequestId

            _uiState.value = _uiState.value.copy(
                currentStage = AnalysisStage.MANUAL,
                autoAnalysisIndex = -1,
                currentMoveIndex = validIndex,
                currentBoard = board.copy(),
                autoAnalysisCurrentScore = null,
                remainingAnalysisMoves = emptyList(),
                stockfishReady = false,
                analysisResult = null,
                analysisResultFen = null
            )

            // Start new Stockfish process for Manual stage
            val ready = stockfish.restart()
            _uiState.value = _uiState.value.copy(stockfishReady = ready)

            if (ready) {
                delay(200)
                stockfish.newGame()
                delay(100)
                configureForManualStage()
                delay(100)
                ensureStockfishAnalysis(fenToAnalyze, thisRequestId)
            }
        }
    }

    /**
     * Enter Manual stage at the current position.
     * Called when user interrupts Analyse stage by navigating.
     */
    private fun enterManualStageAtCurrentPosition() {
        val currentIndex = _uiState.value.currentMoveIndex
        enterManualStageInternal(currentIndex)
    }

    /**
     * Enter Manual stage at the move with the biggest score change.
     * Called when user clicks the stage indicator bar during Analyse stage.
     */
    fun enterManualStageAtBiggestChange() {
        if (_uiState.value.currentStage != AnalysisStage.ANALYSE) return
        val biggestChangeMoveIndex = findBiggestScoreChangeMove()
        enterManualStageInternal(biggestChangeMoveIndex)
    }

    /**
     * Enter Manual stage at a specific move index.
     * Called when user clicks on the graph during Analyse stage.
     */
    fun enterManualStageAtMove(moveIndex: Int) {
        if (_uiState.value.currentStage == AnalysisStage.PREVIEW) return  // Preview is not interruptible
        enterManualStageInternal(moveIndex)
    }

    /**
     * Make a manual move on the board (from user drag-and-drop).
     * Only allowed during Manual stage.
     */
    fun makeManualMove(from: com.chessreplay.chess.Square, to: com.chessreplay.chess.Square) {
        // Only allow moves during Manual stage
        if (_uiState.value.currentStage != AnalysisStage.MANUAL) return

        val currentBoard = _uiState.value.currentBoard

        // Check if move is legal
        if (!currentBoard.isLegalMove(from, to)) return

        // Handle pawn promotion - default to queen for simplicity
        val promotion = if (currentBoard.needsPromotion(from, to)) {
            com.chessreplay.chess.PieceType.QUEEN
        } else null

        // Make a copy of the board and execute the move
        val newBoard = currentBoard.copy()
        if (!newBoard.makeMoveFromSquares(from, to, promotion)) return

        if (_uiState.value.isExploringLine) {
            // Add the new board position to exploring line history
            exploringLineHistory.add(newBoard.copy())
            val newMoveIndex = _uiState.value.exploringLineMoveIndex + 1
            val uciMove = from.toAlgebraic() + to.toAlgebraic() + (promotion?.let {
                when (it) {
                    com.chessreplay.chess.PieceType.QUEEN -> "q"
                    com.chessreplay.chess.PieceType.ROOK -> "r"
                    com.chessreplay.chess.PieceType.BISHOP -> "b"
                    com.chessreplay.chess.PieceType.KNIGHT -> "n"
                    else -> ""
                }
            } ?: "")

            _uiState.value = _uiState.value.copy(
                currentBoard = newBoard,
                exploringLineMoves = _uiState.value.exploringLineMoves + uciMove,
                exploringLineMoveIndex = newMoveIndex
            )
        } else {
            // In main game: enter exploring line mode with this move
            exploringLineHistory.clear()
            exploringLineHistory.add(currentBoard.copy()) // Starting position
            exploringLineHistory.add(newBoard.copy())     // After the move

            val uciMove = from.toAlgebraic() + to.toAlgebraic() + (promotion?.let {
                when (it) {
                    com.chessreplay.chess.PieceType.QUEEN -> "q"
                    com.chessreplay.chess.PieceType.ROOK -> "r"
                    com.chessreplay.chess.PieceType.BISHOP -> "b"
                    com.chessreplay.chess.PieceType.KNIGHT -> "n"
                    else -> ""
                }
            } ?: "")

            _uiState.value = _uiState.value.copy(
                isExploringLine = true,
                exploringLineMoves = listOf(uciMove),
                exploringLineMoveIndex = 0,
                savedGameMoveIndex = _uiState.value.currentMoveIndex,
                currentBoard = newBoard
            )
        }

        // Run Stockfish analysis on the new position - use full restart similar to navigation
        restartAnalysisForExploringLine()
    }

    /**
     * Restart Stockfish analysis for exploring line moves.
     * Similar to restartAnalysisAtMove but uses the current board position.
     */
    private fun restartAnalysisForExploringLine() {
        // Cancel any running analysis
        manualAnalysisJob?.cancel()

        viewModelScope.launch {
            // Stop Stockfish completely
            stockfish.stop()

            // Increment request ID to invalidate any pending results
            analysisRequestId++
            val thisRequestId = analysisRequestId

            // Get the current board (already set by makeManualMove)
            val board = _uiState.value.currentBoard
            val fenToAnalyze = board.getFen()
            currentAnalysisFen = fenToAnalyze

            // Clear analysis result but keep UI stable
            _uiState.value = _uiState.value.copy(
                analysisResultFen = null  // Mark as stale, but keep result for UI stability
            )

            // Small delay to ensure Stockfish has stopped
            delay(50)

            // Send new game command to clear Stockfish's internal state
            stockfish.newGame()
            delay(50)

            // Start fresh analysis
            if (_uiState.value.currentStage == AnalysisStage.MANUAL) {
                ensureStockfishAnalysis(fenToAnalyze, thisRequestId)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        autoAnalysisJob?.cancel()
        manualAnalysisJob?.cancel()
        stockfish.shutdown()
    }
}
