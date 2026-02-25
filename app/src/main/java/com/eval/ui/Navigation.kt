package com.eval.ui

import androidx.compose.runtime.Composable
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
    NavHost(
        navController = navController,
        startDestination = NavRoutes.GAME,
        modifier = modifier
    ) {
        composable(NavRoutes.GAME) {
            GameScreenContent(
                viewModel = viewModel,
                onNavigateToSettings = { navController.navigate(NavRoutes.SETTINGS) },
                onNavigateToHelp = { navController.navigate(NavRoutes.HELP) },
                onNavigateToRetrieve = { navController.navigate(NavRoutes.RETRIEVE) }
            )
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
                onNavigateBack = { navController.popBackStack() },
                onNavigateToGame = { navController.navigate(NavRoutes.GAME) }
            )
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
        aiPrompts = uiState.aiPrompts,
        onBack = onNavigateBack,
        onSaveStockfish = { viewModel.updateStockfishSettings(it) },
        onSaveBoardLayout = { viewModel.updateBoardLayoutSettings(it) },
        onSaveGraph = { viewModel.updateGraphSettings(it) },
        onSaveInterfaceVisibility = { viewModel.updateInterfaceVisibilitySettings(it) },
        onSaveGeneral = { viewModel.updateGeneralSettings(it) },
        onAddAiPrompt = { viewModel.addAiPrompt(it) },
        onUpdateAiPrompt = { viewModel.updateAiPrompt(it) },
        onDeleteAiPrompt = { viewModel.deleteAiPrompt(it) },
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
