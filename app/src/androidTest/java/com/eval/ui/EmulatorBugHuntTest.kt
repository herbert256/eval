package com.eval.ui

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Movie
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.eval.MainActivity
import com.eval.chess.ChessBoard
import com.eval.chess.Square
import com.eval.data.ChessRepository
import com.eval.data.Result
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EmulatorBugHuntTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val prefs = context.getSharedPreferences(SettingsPreferences.PREFS_NAME, Context.MODE_PRIVATE)
    private lateinit var previousSettings: String
    private var scenario: ActivityScenario<MainActivity>? = null
    private lateinit var vm: GameViewModel
    private val fen = "4k3/4p3/8/8/8/8/4P3/4K3 b - - 0 17"
    private val sample = """
        [Event "Bug hunt regression"]
        [White "Regression White"]
        [Black "Regression Black"]
        [Result "*"]

        {Consider d4} 1. e4! {[%eval 0.2]} {[%clk 0:05:00]} (1. d4 {Nf6}) e5
        2. Nf3 Nc6 *
    """.trimIndent()

    @Before fun launch() {
        previousSettings = SettingsPreferences(prefs).exportAllSettings()
        scenario = ActivityScenario.launch(MainActivity::class.java)
        scenario!!.onActivity { vm = ViewModelProvider(it)[GameViewModel::class.java] }
        await("Stockfish ready") { vm.uiState.value.stockfishReady }
        // Wait until startup's saved-game restoration has settled.
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    }

    @After fun restore() {
        scenario?.close()
        SettingsPreferences(prefs).importAllSettings(previousSettings)
        prefs.edit().commit()
    }

    private fun onUi(action: () -> Unit) = scenario!!.onActivity { action() }
    private fun await(label: String, timeout: Long = 45000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeout
        while (!condition() && System.currentTimeMillis() < deadline) Thread.sleep(100)
        val state = vm.uiState.value
        assertTrue("$label: ${state.errorMessage}; stage=${state.currentStage}, ready=${state.stockfishReady}, " +
            "move=${state.currentMoveIndex}, analysisFen=${state.analysisResult?.fen}, " +
            "openingLoading=${state.openingExplorerLoading}, opening=${state.openingExplorerData?.opening}, " +
            "openingError=${state.openingExplorerError}", condition())
    }
    private fun screenshot(name: String) {
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        context.openFileOutput("bughunt-$name.png", Context.MODE_PRIVATE).use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }

    @Test fun imported_site_links_accept_only_supported_web_hosts() {
        val sites = listOf(
            "https://lichess.org/abcd1234", "https://www.chess.com/game/live/1234",
            "https://LICHESS.ORG/abcd1234", "lichess.org/abcd1234",
            "javascript:lichess.org", "https://lichess.org.example.com/game",
            "https://example.com/chess.com/game", "https://chess.com@elsewhere.example/game"
        )
        val actual = sites.map { site ->
            var result: String? = null
            onUi {
                vm.loadGamesFromPgnContent("[Site \"$site\"]\n$sample")
                result = vm.getGameSiteUrl()
            }
            result
        }
        assertEquals(sites.take(3) + List<String?>(sites.size - 3) { null }, actual)
    }

    @Test fun opening_a_game_handles_missing_or_blocked_browsers_and_keeps_the_game() {
        val url = "https://lichess.org/abcd1234"
        var sent: Intent? = null
        var failure: RuntimeException? = android.content.ActivityNotFoundException("Test browser missing")
        val browserContext = object : ContextWrapper(context) {
            override fun startActivity(intent: Intent) {
                failure?.let { throw it }
                sent = intent
            }
        }
        onUi {
            vm.loadGamesFromPgnContent("[Site \"$url\"]\n$sample")
            val gameId = vm.uiState.value.game!!.id
            vm.viewGameOnSite(browserContext)
            assertTrue(vm.uiState.value.errorMessage.orEmpty().contains("No browser"))
            failure = SecurityException("Test browser blocked")
            vm.viewGameOnSite(browserContext)
            assertTrue(vm.uiState.value.errorMessage.orEmpty().contains("could not be opened"))
            failure = null
            vm.viewGameOnSite(browserContext)
            assertEquals(Intent.ACTION_VIEW, sent!!.action)
            assertEquals(url, sent!!.dataString)
            assertEquals(gameId, vm.uiState.value.game?.id)
        }
    }

    @Test fun annotated_game_finishes_preview_analyse_and_manual_with_correct_boards() {
        onUi { vm.loadGamesFromPgnContent(sample) }
        await("Complete analysis") {
            val s = vm.uiState.value
            s.currentStage == AnalysisStage.MANUAL && s.stockfishReady && s.previewScores.size == 4 && s.analyseScores.size == 4
        }
        assertNull(vm.uiState.value.errorMessage)
        assertEquals(listOf("e4", "e5", "Nf3", "Nc6"), vm.uiState.value.moves)
        assertEquals("0:05:00", vm.uiState.value.moveDetails.first().clockTime)
        onUi { vm.goToMove(3) }
        await("Manual evaluation") { vm.uiState.value.analysisResult != null }
        assertEquals("r1bqkbnr/pppp1ppp/2n5/4p3/4P3/5N2/PPPP1PPP/RNBQKB1R w KQkq - 2 3", vm.uiState.value.currentBoard.getFen())
        screenshot("analysed")
    }

    @Test fun opening_information_follows_navigation_and_variations_during_live_analysis() {
        onUi {
            val visibility = vm.uiState.value.interfaceVisibility
            vm.updateInterfaceVisibilitySettings(visibility.copy(
                manualStage = visibility.manualStage.copy(showOpeningName = true, showOpeningExplorer = true)))
            vm.loadGamesFromPgnContent(sample)
        }
        await("Manual stage") { vm.uiState.value.currentStage == AnalysisStage.MANUAL }
        onUi { vm.goToMove(0) }
        await("Opening name for e4") { vm.uiState.value.currentOpeningName == "King's Pawn Opening" }
        await("Opening lookup during live analysis") {
            val state = vm.uiState.value
            (state.openingExplorerData != null ||
                state.openingExplorerError == "Lichess requires authentication for opening statistics.") &&
                state.analysisResult?.fen == state.currentBoard.getFen()
        }
        assertFalse(vm.uiState.value.openingExplorerLoading)
        vm.uiState.value.openingExplorerData?.let { assertTrue(it.white > 0) }
        screenshot("opening-explorer")
        onUi { vm.exploreLine("c7c5") }
        await("Sicilian variation") { vm.uiState.value.currentOpeningName == "Sicilian Defense" }
        onUi { vm.backToOriginalGame(); vm.goToMove(1) }
        await("Return to Open Game") { vm.uiState.value.currentOpeningName == "Open Game" }
        onUi {
            val visibility = vm.uiState.value.interfaceVisibility
            vm.updateInterfaceVisibilitySettings(visibility.copy(
                manualStage = visibility.manualStage.copy(showOpeningName = false, showOpeningExplorer = false)))
        }
        await("Hidden opening data cleared") {
            vm.uiState.value.openingExplorerData == null && !vm.uiState.value.openingExplorerLoading
        }
        assertNull(vm.uiState.value.openingExplorerError)
        onUi { assertTrue(vm.startFromFen(fen)) }
        await("Custom FEN has no previous opening") { vm.uiState.value.currentOpeningName == null }
        assertNull(vm.uiState.value.openingName)
    }

    @Test fun navigation_during_the_manual_stage_transition_keeps_the_new_search() {
        onUi { vm.loadGamesFromPgnContent(sample) }
        await("Manual stage begins") { vm.uiState.value.currentStage == AnalysisStage.MANUAL }
        // Deliberately navigate before the transition has finished restarting Stockfish.
        onUi { vm.goToMove(0); vm.goToMove(2) }
        val selectedFen = vm.uiState.value.currentBoard.getFen()
        await("Transition navigation has an evaluation of the selected position") {
            val state = vm.uiState.value
            state.currentMoveIndex == 2 && state.analysisResult?.fen == selectedFen &&
                state.analysisResultFen == selectedFen
        }
        Thread.sleep(500)
        assertEquals(selectedFen, vm.uiState.value.analysisResult?.fen)
    }

    @Test fun rapid_navigation_settles_on_the_selected_board_with_live_analysis() {
        onUi { vm.loadGamesFromPgnContent(sample) }
        await("Complete analysis before navigation") {
            val state = vm.uiState.value
            state.currentStage == AnalysisStage.MANUAL && state.stockfishReady && state.analyseScores.size == 4
        }
        onUi {
            vm.goToStart()
            repeat(12) { vm.nextMove(); vm.prevMove() }
            repeat(3) { vm.nextMove() }
            assertEquals(2, vm.uiState.value.currentMoveIndex)
            val selectedFen = vm.uiState.value.currentBoard.getFen()
            vm.flipBoard()
            assertEquals(selectedFen, vm.uiState.value.currentBoard.getFen())
            vm.flipBoard()
        }
        val selectedFen = vm.uiState.value.currentBoard.getFen()
        assertEquals("rnbqkbnr/pppp1ppp/8/4p3/4P3/5N2/PPPP1PPP/RNBQKB1R b KQkq - 1 2", selectedFen)
        await("Latest navigation has live evaluation") {
            val state = vm.uiState.value
            state.stockfishReady && state.currentMoveIndex == 2 &&
                state.analysisResult != null && state.analysisResultFen == selectedFen
        }
        assertNull(vm.uiState.value.errorMessage)
        screenshot("rapid-navigation")
    }

    @Test fun exporting_while_exploring_keeps_main_game_moves_and_opening_metadata() {
        onUi { vm.loadGamesFromPgnContent(sample) }
        await("Manual stage for export") { vm.uiState.value.currentStage == AnalysisStage.MANUAL && vm.uiState.value.stockfishReady }
        onUi { vm.goToMove(0); vm.exploreLine("c7c5") }
        await("Explored opening ready") { vm.uiState.value.currentOpeningName == "Sicilian Defense" }
        val branchFen = vm.uiState.value.currentBoard.getFen()
        var sent: Intent? = null
        val shareContext = object : ContextWrapper(context) {
            override fun startActivity(intent: Intent) { sent = intent }
        }
        onUi { vm.exportAnnotatedPgn(shareContext) }
        @Suppress("DEPRECATION")
        val pgn = sent!!.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!.getStringExtra(Intent.EXTRA_TEXT)!!
        assertEquals(listOf("e4", "e5", "Nf3", "Nc6"), com.eval.chess.PgnParser.parseMoves(pgn))
        assertEquals("Italian Game / Ruy Lopez Setup", com.eval.chess.PgnParser.parseHeaders(pgn)["Opening"])
        onUi {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val previous = clipboard.primaryClip
            try {
                vm.copyPgnToClipboard(context)
                assertEquals(pgn, clipboard.primaryClip!!.getItemAt(0).text.toString())
            } finally {
                if (previous == null) clipboard.clearPrimaryClip() else clipboard.setPrimaryClip(previous)
            }
        }
        assertEquals(branchFen, vm.uiState.value.currentBoard.getFen())
        assertTrue(vm.uiState.value.isExploringLine)
    }

    @Test fun imported_uci_study_exports_legal_san_and_rejects_invalid_fen_without_losing_the_game() {
        val study = "[White \"UCI Study\"]\n[Black \"Opponent\"]\n\n1. e2e4 e7e5 2. g1f3 b8c6 *"
        onUi { vm.loadGamesFromPgnContent(study) }
        await("UCI study analysed") { vm.uiState.value.currentStage == AnalysisStage.MANUAL && vm.uiState.value.stockfishReady }
        val selectedFen = vm.uiState.value.currentBoard.getFen()
        var sent: Intent? = null
        val shareContext = object : ContextWrapper(context) {
            override fun startActivity(intent: Intent) { sent = intent }
        }
        onUi { vm.exportAnnotatedPgn(shareContext) }
        @Suppress("DEPRECATION")
        val pgn = sent!!.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!.getStringExtra(Intent.EXTRA_TEXT)!!
        assertEquals(listOf("e4", "e5", "Nf3", "Nc6"), com.eval.chess.PgnParser.parseMoves(pgn))
        onUi { assertFalse(vm.startFromFen("4k3/8/8/4P3/8/8/8/4K3 w - d6 0 2")) }
        assertEquals(selectedFen, vm.uiState.value.currentBoard.getFen())
        assertEquals("UCI Study", vm.uiState.value.game!!.players.white.user!!.name)
    }

    @Test fun nested_variations_keep_their_history_and_receive_live_analysis() {
        onUi { vm.loadGamesFromPgnContent(sample) }
        await("Manual stage before nested variation") {
            vm.uiState.value.currentStage == AnalysisStage.MANUAL && vm.uiState.value.stockfishReady
        }
        val mainMoves = vm.uiState.value.moves
        onUi {
            vm.goToStart()
            vm.exploreLine("e2e4 e7e5 g1f3", 1)
            vm.exploreLine("b1c3 b8c6", 0)
            assertEquals(listOf("e2e4", "e7e5", "b1c3", "b8c6"), vm.uiState.value.exploringLineMoves)
            assertEquals(2, vm.uiState.value.exploringLineMoveIndex)
            assertNull(vm.uiState.value.analysisResult)
        }
        val selectedFen = vm.uiState.value.currentBoard.getFen()
        await("Nested variation evaluation") {
            vm.uiState.value.analysisResult?.fen == selectedFen && vm.uiState.value.analysisResultFen == selectedFen
        }
        screenshot("nested-variation")
        onUi {
            vm.goToStart()
            assertEquals(com.eval.chess.ChessBoard().getFen(), vm.uiState.value.currentBoard.getFen())
            vm.goToEnd()
            assertEquals(3, vm.uiState.value.exploringLineMoveIndex)
            vm.backToOriginalGame()
            assertFalse(vm.uiState.value.isExploringLine)
            assertEquals(mainMoves, vm.uiState.value.moves)
            assertEquals(-1, vm.uiState.value.currentMoveIndex)
            vm.goToEnd()
        }
        val finalFen = vm.uiState.value.currentBoard.getFen()
        await("Main game evaluation after leaving the variation") { vm.uiState.value.analysisResult?.fen == finalFen }
        assertEquals(3, vm.uiState.value.currentMoveIndex)
    }

    @Test fun fen_replaces_old_history_returns_from_exploration_and_restores_after_restart() {
        onUi {
            vm.loadGamesFromPgnContent(sample)
            assertTrue(vm.startFromFen(fen))
            vm.makeManualMove(Square(4, 6), Square(4, 4))
            assertTrue(vm.uiState.value.isExploringLine)
            vm.backToOriginalGame()
            assertEquals(fen, vm.uiState.value.currentBoard.getFen())
            assertTrue(vm.uiState.value.moves.isEmpty())
            vm.goToStart()
        }
        await("FEN navigation") { vm.uiState.value.currentBoard.getFen() == fen && vm.uiState.value.analysisResult != null }
        screenshot("fen")
        scenario!!.close()
        scenario = ActivityScenario.launch(MainActivity::class.java)
        scenario!!.onActivity { vm = ViewModelProvider(it)[GameViewModel::class.java] }
        await("FEN restored with live evaluation") {
            val state = vm.uiState.value
            state.game != null && state.stockfishReady && state.analysisResult != null && state.analysisResultFen == fen
        }
        assertEquals(fen, vm.uiState.value.currentBoard.getFen())
        assertTrue(vm.uiState.value.moves.isEmpty())
        assertEquals(AnalysisStage.MANUAL, vm.uiState.value.currentStage)
    }

    @Test fun malformed_input_is_reported_and_invalid_fen_keeps_current_board() {
        onUi {
            assertTrue(vm.startFromFen(fen))
            assertFalse(vm.startFromFen("invalid"))
            assertEquals(fen, vm.uiState.value.currentBoard.getFen())
            assertEquals("Invalid FEN position", vm.uiState.value.errorMessage)
            vm.loadGamesFromPgnContent(sample.replace("Nf3", "nonsense"))
            assertEquals(listOf("e4", "e5"), vm.uiState.value.moves)
            assertTrue(vm.uiState.value.errorMessage.orEmpty().contains("nonsense"))
            assertTrue(vm.startFromFen(fen))
            assertNull(vm.uiState.value.errorMessage)
        }
    }

    @Test fun custom_position_pgn_loads_and_gif_export_completes_without_sending() {
        val pgn = "[Event \"Custom position\"]\n[White \"White\"]\n[Black \"Black\"]\n[SetUp \"1\"]\n[FEN \"$fen\"]\n\n17... e5 18. e4 *"
        onUi { vm.loadGamesFromPgnContent(pgn) }
        await("FEN PGN analysed") { vm.uiState.value.currentStage == AnalysisStage.MANUAL && vm.uiState.value.analyseScores.size == 2 }
        assertEquals(listOf("e5", "e4"), vm.uiState.value.moves)
        onUi { vm.goToMove(0) }
        screenshot("black-start")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        var state = vm.uiState.value
        val sent = java.util.concurrent.atomic.AtomicReference<Intent>()
        val wrapper = object : ContextWrapper(context) {
            override fun startActivity(intent: Intent) { sent.set(intent) }
        }
        try {
            val exporter = ExportShareManager({ state }, { state = it(state) }, scope)
            onUi { exporter.exportAsGif(wrapper) }
            await("GIF export") { sent.get() != null || state.errorMessage != null }
            assertNull(state.errorMessage)
            val chooser = requireNotNull(sent.get())
            @Suppress("DEPRECATION")
            val send = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
            assertEquals("image/gif", send.type)
            @Suppress("DEPRECATION")
            val uri = send.getParcelableExtra<android.net.Uri>(Intent.EXTRA_STREAM)!!
            val data = context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
            assertEquals("GIF89a", data.take(6).map { it.toInt().toChar() }.joinToString(""))
            val movie = Movie.decodeByteArray(data, 0, data.size)
            assertNotNull(movie)
            assertTrue(movie.width() > 0 && movie.duration() >= 3000)
        } finally { scope.cancel() }
    }

    @Test fun public_games_can_be_retrieved_from_both_servers() = runBlocking {
        val repository = ChessRepository()
        for ((label, result) in listOf(
            "Lichess" to repository.getLichessGames("DrNykterstein", 1),
            "Chess.com" to repository.getChessComGames("hikaru", 1)
        )) {
            assertTrue("$label: $result", result is Result.Success)
            val games = (result as Result.Success).data
            assertTrue(label, games.isNotEmpty())
            assertFalse(label, games.first().pgn.isNullOrBlank())
        }
    }

    @Test fun pgn_collection_selection_analyses_both_games_and_keeps_the_draw() {
        val first = "[Event \"First event\"]\n[White \"Alice \\\"Ace\\\"\"]\n[Black \"Bob\"]\n\n1. e4 e5 *"
        val second = "[Event \"Second event\"]\n[White \"Carol\"]\n[Black \"Dave\"]\n[Result \"1/2-1/2\"]\n\n1. d4 d5 1/2-1/2"
        val collection = first + "\n \n  " + second
        onUi {
            vm.loadGamesFromPgnContent(collection)
            assertTrue(vm.uiState.value.showPgnEventSelection)
            assertEquals(listOf("First event", "Second event"), vm.uiState.value.pgnEvents)
            vm.selectPgnEvent("First event")
            vm.selectPgnGameFromEvent(vm.uiState.value.pgnGamesForSelectedEvent.single())
        }
        await("First collection game analysed") {
            vm.uiState.value.currentStage == AnalysisStage.MANUAL && vm.uiState.value.analyseScores.size == 2
        }
        assertEquals("Alice \"Ace\"", vm.uiState.value.game?.players?.white?.user?.name)
        assertEquals(listOf("e4", "e5"), vm.uiState.value.moves)
        onUi {
            vm.loadGamesFromPgnContent(collection)
            vm.selectPgnEvent("Second event")
            vm.selectPgnGameFromEvent(vm.uiState.value.pgnGamesForSelectedEvent.single())
        }
        await("Second collection game analysed") {
            vm.uiState.value.currentStage == AnalysisStage.MANUAL && vm.uiState.value.analyseScores.size == 2
        }
        assertEquals(listOf("d4", "d5"), vm.uiState.value.moves)
        assertEquals("draw", vm.uiState.value.game?.status)
        assertNull(vm.uiState.value.errorMessage)
        screenshot("collection-draw")
    }
}
