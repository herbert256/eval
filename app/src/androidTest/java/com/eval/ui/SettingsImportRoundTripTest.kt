package com.eval.ui

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsImportRoundTripTest {

    @Test
    fun typed_import_export_round_trip_restores_values() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val prefsName = "settings_roundtrip_test_${System.currentTimeMillis()}"
        val prefs = context.getSharedPreferences(prefsName, android.content.Context.MODE_PRIVATE)
        val settings = SettingsPreferences(prefs)

        settings.saveLichessUsername("TesterLichess")
        settings.saveChessComUsername("TesterChessCom")
        settings.saveLichessMaxGames(17)
        settings.saveGeneralSettings(GeneralSettings(moveSoundsEnabled = false, lichessUsername = "TesterLichess"))
        settings.saveStockfishSettings(
            StockfishSettings(
                previewStage = PreviewStageSettings(secondsForMove = 0.10f, threads = 2, hashMb = 16, useNnue = true),
                analyseStage = AnalyseStageSettings(secondsForMove = 3.0f, threads = 3, hashMb = 96, useNnue = false),
                manualStage = ManualStageSettings(depth = 28, threads = 2, hashMb = 96, multiPv = 4, useNnue = true)
            )
        )
        settings.saveAiPrompts(
            listOf(
                AiPromptEntry(
                    name = "RoundTrip",
                    prompt = "Prompt @FEN@",
                    system = "System",
                    instructions = "Instructions",
                    category = AiPromptCategory.GAME
                )
            )
        )
        settings.setAiAppDontAskAgain(true)
        settings.saveLastServerUser("TesterLichess", "lichess.org")
        settings.saveFenToHistory("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR_w_KQkq_-_0_1")

        val exported = settings.exportAllSettings()

        settings.resetAllSettingsToDefaults()
        settings.saveLichessUsername("OtherUser")

        val imported = settings.importAllSettings(exported)
        assertTrue(imported)
        assertEquals("TesterLichess", settings.savedLichessUsername)
        assertEquals("TesterChessCom", settings.savedChessComUsername)
        assertEquals(17, settings.lichessMaxGames)
        assertEquals(false, settings.loadGeneralSettings().moveSoundsEnabled)
        assertEquals(0.10f, settings.loadStockfishSettings().previewStage.secondsForMove)
        assertEquals(true, settings.getAiAppDontAskAgain())
        assertEquals("TesterLichess", settings.lastServerUser)
        assertEquals("lichess.org", settings.lastServerName)
        assertEquals(1, settings.loadAiPrompts().size)
        assertEquals(1, settings.loadFenHistory().size)
    }
}

