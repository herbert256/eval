package com.eval.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Navigation routes for the app.
 */
object NavRoutes {
    const val GAME = "game"
    const val SETTINGS = "settings"
    const val HELP = "help"
    const val RETRIEVE = "retrieve"
    const val SHARED = "shared"

}

/**
 * Main navigation host for the app.
 */
@Composable
fun EvalNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    viewModel: GameViewModel = viewModel()
) {
    val sharedInput by viewModel.sharedImport.collectAsState()
    // Remember which share was already shown, so recreating the activity (rotation) doesn't
    // pull the user back to the share screen from Settings or Help.
    var shownShareId by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(sharedInput?.id) {
        val id = sharedInput?.id
        if (id != null && id != shownShareId) {
            shownShareId = id
            navController.navigate(NavRoutes.SHARED) {
                popUpTo(NavRoutes.GAME)
                launchSingleTop = true
            }
        }
    }

    // A prepared AI request is sent only while Eval is in the foreground, with this Activity.
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(viewModel, lifecycleOwner, context) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.pendingAiLaunch.collect { if (it != null) viewModel.completeAiLaunch(context) }
        }
    }
    LaunchedEffect(viewModel, lifecycleOwner, context) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.shareRequests.collect { intent -> context.startActivity(intent) }
        }
    }

    // Collect only what the menu needs: the host must not recompose on every engine update.
    val canReload by remember(viewModel) {
        viewModel.uiState.map { it.game != null || it.hasLastServerUser }.distinctUntilChanged()
    }.collectAsState(initial = viewModel.uiState.value.let { it.game != null || it.hasLastServerUser })
    val menu = remember(navController, viewModel, canReload) {
        val home: () -> Unit = {
            viewModel.dismissSharedContent()
            viewModel.hideRetrieveScreen()
            viewModel.dismissAiInstructionSelection()
            viewModel.dismissPlayerInfo()
            viewModel.dismissLichessTv()
            viewModel.dismissTournaments()
            viewModel.dismissBroadcasts()
            viewModel.dismissStreamers()
            viewModel.dismissGameSelection()
            viewModel.hideSharePositionDialog()
            viewModel.hideAiAppNotInstalledDialog()
            if (viewModel.uiState.value.showGifExportDialog) viewModel.cancelGifExport()
            navController.popBackStack(NavRoutes.GAME, false)
        }
        fun navigate(route: String) {
            navController.navigate(route) { launchSingleTop = true }
        }
        EvalMenuActions(
            home = home,
            selectGame = {
                home()
                viewModel.dismissAnalysedGamesSelection()
                viewModel.dismissSelectedRetrieveGames()
                viewModel.dismissPreviousRetrievesSelection()
                viewModel.dismissPgnEventSelection()
                viewModel.showRetrieveScreen()
                navigate(NavRoutes.RETRIEVE)
            },
            settings = { navigate(NavRoutes.SETTINGS) },
            help = { navigate(NavRoutes.HELP) },
            reload = if (canReload) ({
                home()
                viewModel.reloadLastGame()
            }) else null
        )
    }
    val home = menu.home
    CompositionLocalProvider(LocalEvalMenuActions provides menu) {
        NavHost(
            navController = navController,
            startDestination = NavRoutes.GAME,
            modifier = modifier
        ) {
            composable(NavRoutes.SHARED) {
                val input = sharedInput
                val close = {
                    viewModel.dismissSharedContent()
                    navController.popBackStack(NavRoutes.GAME, false)
                    Unit
                }
                if (input == null) {
                    LaunchedEffect(Unit) { navController.popBackStack(NavRoutes.GAME, false) }
                } else key(input.id) {
                    UrlGameScreen(
                        sharedInput = input,
                        onStartFen = { fen -> viewModel.startFromFen(fen).also { if (it) close() } },
                        onStartPgn = { pgn -> viewModel.loadGamesFromPgnContent(pgn); close() },
                        onBack = close
                    )
                }
            }
            composable(NavRoutes.GAME) {
                GameScreenContent(viewModel = viewModel)
            }

            composable(NavRoutes.SETTINGS) {
                SettingsScreenNav(
                    viewModel = viewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(NavRoutes.HELP) {
                HelpScreen(
                    onBack = { navController.popBackStack() }
                )
            }

            composable(NavRoutes.RETRIEVE) {
                RetrieveScreenNav(
                    viewModel = viewModel,
                    onNavigateBack = {
                        viewModel.hideRetrieveScreen()
                        navController.popBackStack()
                    },
                    onNavigateToGame = home
                )
            }

        }
    }
}

/**
 * Wrapper for SettingsScreen that gets state from ViewModel.
 */
@Composable
fun SettingsScreenNav(
    viewModel: GameViewModel,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current

    SettingsScreen(
        stockfishSettings = uiState.stockfishSettings,
        boardLayoutSettings = uiState.boardLayoutSettings,
        graphSettings = uiState.graphSettings,
        interfaceVisibility = uiState.interfaceVisibility,
        generalSettings = uiState.generalSettings,
        aiInstructions = uiState.aiInstructions,
        aiSystemPrompts = uiState.aiSystemPrompts,
        aiReportPrompts = uiState.aiReportPrompts,
        onSaveAiPrompt = viewModel::saveAiPrompt,
        onDeleteAiPrompt = viewModel::deleteAiPrompt,
        onBack = onNavigateBack,
        onSaveStockfish = { viewModel.updateStockfishSettings(it) },
        onSaveBoardLayout = { viewModel.updateBoardLayoutSettings(it) },
        onSaveGraph = { viewModel.updateGraphSettings(it) },
        onSaveInterfaceVisibility = { viewModel.updateInterfaceVisibilitySettings(it) },
        onSaveGeneral = { viewModel.updateGeneralSettings(it) },
        onAddAiInstruction = { viewModel.addAiInstruction(it) },
        onUpdateAiInstruction = { viewModel.updateAiInstruction(it) },
        onDeleteAiInstruction = { viewModel.deleteAiInstruction(it) },
        onExportSettings = { viewModel.exportSettings(context) },
        onImportSettings = { uri -> viewModel.importSettings(context, uri) },
        showNnueToggles = uiState.engineSupportsNnueOption,
        lichessToken = remember { viewModel.lichessToken },
        onSaveLichessToken = viewModel::saveLichessToken
    )
}

/**
 * Wrapper for RetrieveScreen that gets state from ViewModel.
 */
@Composable
fun RetrieveScreenNav(
    viewModel: GameViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToGame: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()

    RetrieveScreen(
        viewModel = viewModel,
        uiState = uiState,
        onBack = onNavigateBack,
        onNavigateToGame = onNavigateToGame
    )
}
