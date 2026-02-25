package com.eval.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import com.eval.stockfish.StockfishEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal class SettingsManager(
    private val getUiState: () -> GameUiState,
    private val updateUiState: (GameUiState.() -> GameUiState) -> Unit,
    private val viewModelScope: CoroutineScope,
    private val settingsPrefs: SettingsPreferences,
    private val stockfish: StockfishEngine,
    private val analysisOrchestrator: AnalysisOrchestrator
) {
    fun updateStockfishSettings(settings: StockfishSettings) {
        settingsPrefs.saveStockfishSettings(settings)
        updateUiState { copy(stockfishSettings = settings) }
        if (getUiState().stockfishReady) {
            when (getUiState().currentStage) {
                AnalysisStage.PREVIEW -> analysisOrchestrator.configureForPreviewStage()
                AnalysisStage.ANALYSE -> analysisOrchestrator.configureForAnalyseStage()
                AnalysisStage.MANUAL -> analysisOrchestrator.configureForManualStage()
            }
            if (getUiState().currentStage == AnalysisStage.MANUAL) {
                analysisOrchestrator.restartAnalysisForExploringLine()
            }
        }
    }

    fun updateBoardLayoutSettings(settings: BoardLayoutSettings) {
        settingsPrefs.saveBoardLayoutSettings(settings)
        updateUiState { copy(boardLayoutSettings = settings) }
    }

    fun updateGraphSettings(settings: GraphSettings) {
        settingsPrefs.saveGraphSettings(settings)
        updateUiState { copy(graphSettings = settings) }
    }

    fun updateInterfaceVisibilitySettings(settings: InterfaceVisibilitySettings) {
        val currentSettings = getUiState().interfaceVisibility
        val previewChanged = currentSettings.previewStage != settings.previewStage
        val analyseChanged = currentSettings.analyseStage != settings.analyseStage

        settingsPrefs.saveInterfaceVisibilitySettings(settings)
        updateUiState { copy(interfaceVisibility = settings) }

        if ((previewChanged || analyseChanged) && getUiState().game != null) {
            analysisOrchestrator.stop()

            updateUiState {
                copy(
                    currentStage = AnalysisStage.PREVIEW,
                    previewScores = emptyMap(),
                    analyseScores = emptyMap(),
                    autoAnalysisIndex = -1
                )
            }

            viewModelScope.launch {
                val ready = stockfish.restart()
                updateUiState { copy(stockfishReady = ready) }
                if (ready) {
                    stockfish.newGame()
                    analysisOrchestrator.startAnalysis()
                }
            }
        }
    }

    fun updateGeneralSettings(settings: GeneralSettings) {
        settingsPrefs.saveGeneralSettings(settings)
        updateUiState { copy(generalSettings = settings) }
    }

    fun updateAiPrompts(prompts: List<AiPromptEntry>) {
        settingsPrefs.saveAiPrompts(prompts)
        updateUiState { copy(aiPrompts = prompts) }
    }

    fun addAiPrompt(prompt: AiPromptEntry) {
        val updated = getUiState().aiPrompts + prompt
        updateAiPrompts(updated)
    }

    fun updateAiPrompt(prompt: AiPromptEntry) {
        val updated = getUiState().aiPrompts.map { if (it.id == prompt.id) prompt else it }
        updateAiPrompts(updated)
    }

    fun deleteAiPrompt(id: String) {
        val updated = getUiState().aiPrompts.filter { it.id != id }
        updateAiPrompts(updated)
    }

    fun exportSettings(context: Context) {
        try {
            val json = settingsPrefs.exportAllSettings()
            val cacheDir = java.io.File(context.cacheDir, "settings_export")
            cacheDir.mkdirs()
            val file = java.io.File(cacheDir, "eval_settings.json")
            file.writeText(json)

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Export Settings"))
        } catch (e: Exception) {
            Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun importSettings(
        context: Context,
        uri: Uri,
        reloadSettings: () -> Unit
    ): Boolean {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val json = inputStream?.bufferedReader()?.use { it.readText() } ?: return false
            val success = settingsPrefs.importAllSettings(json)
            if (success) {
                // Short delay ensures any async listeners settle before UI refresh.
                viewModelScope.launch {
                    delay(20)
                    reloadSettings()
                }
                Toast.makeText(context, "Settings imported", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Import failed: invalid file", Toast.LENGTH_SHORT).show()
            }
            success
        } catch (e: Exception) {
            Toast.makeText(context, "Import failed: ${e.message}", Toast.LENGTH_SHORT).show()
            false
        }
    }
}

