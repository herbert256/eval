package com.eval.ui

import com.google.gson.Gson
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Score Combi graph replaces the line and bars graphs by default in the Manual stage only. */
class ScoreCombiGraphVisibilityTest {
    @Test fun defaults() {
        val visibility = InterfaceVisibilitySettings()
        assertFalse(visibility.previewStage.showScoreCombiGraph)
        assertFalse(visibility.analyseStage.showScoreCombiGraph)
        assertTrue(visibility.manualStage.showScoreCombiGraph)
        assertFalse(visibility.manualStage.showScoreLineGraph)
        assertFalse(visibility.manualStage.showScoreBarsGraph)
    }

    @Test fun an_export_without_the_combi_graph_keeps_its_graphs_and_gets_the_default() {
        val json = """{"schemaVersion":5,"interfaceVisibilitySettings":{
            "analyseStage":{"showScoreLineGraph":true,"showScoreBarsGraph":true},
            "manualStage":{"showScoreLineGraph":true,"showScoreBarsGraph":true}}}"""
        val snapshot = Gson().fromJson(json, SettingsSnapshotV5::class.java)
        val visibility = snapshot.interfaceVisibilitySettings
        assertTrue(visibility.manualStage.showScoreLineGraph)
        assertTrue(visibility.manualStage.showScoreBarsGraph)
        assertTrue(visibility.manualStage.showScoreCombiGraph)
        assertFalse(visibility.analyseStage.showScoreCombiGraph)
    }
}
