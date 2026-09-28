package com.eval.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import com.eval.stockfish.StockfishEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

    // Visibility only changes what is drawn; completed analysis and the running engine stay as they are.
    fun updateInterfaceVisibilitySettings(settings: InterfaceVisibilitySettings) {
        settingsPrefs.saveInterfaceVisibilitySettings(settings)
        updateUiState { copy(interfaceVisibility = settings) }
    }

    fun updateGeneralSettings(settings: GeneralSettings) {
        settingsPrefs.saveGeneralSettings(settings)
        updateUiState { copy(generalSettings = settings) }
    }

    fun updateAiInstructions(instructions: List<AiInstructionEntry>) {
        settingsPrefs.saveAiInstructions(instructions)
        updateUiState { copy(aiInstructions = instructions) }
    }

    fun addAiInstruction(entry: AiInstructionEntry) {
        val updated = getUiState().aiInstructions + entry
        updateAiInstructions(updated)
    }

    fun updateAiInstruction(entry: AiInstructionEntry) {
        val updated = getUiState().aiInstructions.map { if (it.id == entry.id) entry else it }
        updateAiInstructions(updated)
    }

    fun deleteAiInstruction(id: String) {
        val updated = getUiState().aiInstructions.filter { it.id != id }
        updateAiInstructions(updated)
        clearUnavailableAiChoices()
    }

    fun saveAiPrompt(entry: AiPromptEntry, system: Boolean) {
        val state = getUiState()
        val catalog = if (system) state.aiSystemPrompts else state.aiReportPrompts
        val updated = if (catalog.any { it.id == entry.id }) catalog.map { if (it.id == entry.id) entry else it }
            else catalog + entry
        saveAiSetup(if (system) updated else state.aiSystemPrompts,
            if (system) state.aiReportPrompts else updated, state.aiInstructions)
    }

    fun deleteAiPrompt(id: String, system: Boolean) {
        val state = getUiState()
        saveAiSetup(
            if (system) state.aiSystemPrompts.filterNot { it.id == id } else state.aiSystemPrompts,
            if (system) state.aiReportPrompts else state.aiReportPrompts.filterNot { it.id == id }, state.aiInstructions)
        clearUnavailableAiChoices()
    }

    private fun clearUnavailableAiChoices() {
        val state = getUiState()
        val selection = settingsPrefs.loadAiReportSelection().available(state.aiSystemPrompts, state.aiReportPrompts, state.aiInstructions)
        settingsPrefs.saveAiReportSelection(selection)
        updateUiState { copy(aiReportSelection = selection) }
    }

    private fun saveAiSetup(systems: List<AiPromptEntry>, prompts: List<AiPromptEntry>, instructions: List<AiInstructionEntry>) {
        settingsPrefs.saveAiSetup(systems, prompts, instructions)
        updateUiState { copy(aiSystemPrompts = systems, aiReportPrompts = prompts, aiInstructions = instructions) }
    }

    fun exportSettings(context: Context) {
        try {
            val json = settingsPrefs.exportAllSettings()
            val cacheDir = java.io.File(context.cacheDir, "settings_export")
            cacheDir.mkdirs()
            // Earlier exports aren't needed once shared; keep only the new one.
            cacheDir.listFiles { file -> file.name.startsWith("eval_settings") }?.forEach { it.delete() }
            val stamp = java.text.SimpleDateFormat("yyyy-MM-dd_HHmm", java.util.Locale.ROOT).format(java.util.Date())
            val file = java.io.File(cacheDir, "eval_settings_$stamp.json")
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

    /** Reads, validates and applies a settings file off the main thread, then reloads the UI state. */
    fun importSettings(
        context: Context,
        uri: Uri,
        reloadSettings: () -> Unit
    ) {
        val appContext = context.applicationContext
        viewModelScope.launch {
            val message = try {
                val json = withContext(Dispatchers.IO) { readLimited(appContext, uri) }
                val success = json != null && withContext(Dispatchers.IO) { settingsPrefs.importAllSettings(json) }
                if (success) {
                    reloadSettings()
                    "Settings imported"
                } else if (json == null) {
                    "Import failed: the file is larger than ${MAX_SETTINGS_FILE_BYTES / 1_000_000} MB"
                } else {
                    "Import failed: invalid file"
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                "Import failed: ${e.message}"
            }
            Toast.makeText(appContext, message, Toast.LENGTH_SHORT).show()
        }
    }

    /** The file's text, or null when it exceeds [MAX_SETTINGS_FILE_BYTES]. */
    private fun readLimited(context: Context, uri: Uri): String? {
        val input = context.contentResolver.openInputStream(uri) ?: throw java.io.IOException("Could not open the file")
        input.use { stream ->
            val bytes = stream.readNBytesCompat(MAX_SETTINGS_FILE_BYTES + 1)
            if (bytes.size > MAX_SETTINGS_FILE_BYTES) return null
            return String(bytes, Charsets.UTF_8)
        }
    }

    private fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        while (out.size() < limit) {
            val read = read(buffer, 0, minOf(buffer.size, limit - out.size()))
            if (read < 0) break
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }

    companion object {
        // Real exports are tens of kilobytes; this leaves ample room for large prompt catalogs.
        private const val MAX_SETTINGS_FILE_BYTES = 2_000_000
    }
}
