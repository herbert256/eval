package com.eval.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

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
    LaunchedEffect(sharedInput?.id) {
        if (sharedInput != null) navController.navigate(NavRoutes.SHARED) {
            popUpTo(NavRoutes.GAME)
            launchSingleTop = true
        }
    }
    val uiState by viewModel.uiState.collectAsState()
    val home: () -> Unit = {
        viewModel.dismissSharedContent()
        viewModel.hideRetrieveScreen()
        viewModel.dismissAiInstructionSelection()
        viewModel.dismissPlayerInfo()
        viewModel.dismissGameSelection()
        viewModel.hideSharePositionDialog()
        viewModel.hideAiAppNotInstalledDialog()
        if (viewModel.uiState.value.showGifExportDialog) viewModel.cancelGifExport()
        navController.popBackStack(NavRoutes.GAME, false)
    }
    fun navigate(route: String) {
        navController.navigate(route) { launchSingleTop = true }
    }
    val menu = EvalMenuActions(
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
        reload = if (uiState.game != null || uiState.hasLastServerUser) ({
            home()
            viewModel.reloadLastGame()
        }) else null
    )
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
        onImportSettings = { uri -> viewModel.importSettings(context, uri) }
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
