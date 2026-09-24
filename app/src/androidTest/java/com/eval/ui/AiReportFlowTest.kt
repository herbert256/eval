package com.eval.ui

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import android.view.inputmethod.InputMethodManager
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.eval.MainActivity
import com.eval.ui.theme.EvalTheme
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AiReportFlowTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private fun nodes(node: AccessibilityNodeInfo?): List<AccessibilityNodeInfo> = if (node == null) emptyList()
        else listOf(node) + (0 until node.childCount).flatMap { nodes(node.getChild(it)) }
    private fun visible() = nodes(instrumentation.uiAutomation.rootInActiveWindow).filter { it.isVisibleToUser }
    private fun waitFor(message: String, test: () -> Boolean) {
        val end = SystemClock.uptimeMillis() + 12_000
        while (SystemClock.uptimeMillis() < end) {
            if (test()) return
            SystemClock.sleep(100)
        }
        fail("$message; visible: ${visible().map { it.text ?: it.contentDescription }}")
    }
    private fun systemBack() {
        assertFalse("Back is provided by Android only", visible().any { it.text?.toString() == "< Back" })
        assertTrue(instrumentation.uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK))
        SystemClock.sleep(250)
    }
    private fun click(text: String, description: Boolean = false) {
        fun find() = visible().firstOrNull { (if (description) it.contentDescription else it.text)?.toString() == text }
        waitFor("Find $text") { find() != null }
        waitFor("Click $text") {
            generateSequence(find()) { it.parent }.firstOrNull { it.isClickable }
                ?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
        }
        SystemClock.sleep(180)
    }
    private fun editable(node: AccessibilityNodeInfo) = node.actionList.any { it.id == AccessibilityNodeInfo.ACTION_SET_TEXT }
    private fun setText(label: String, text: String) {
        fun field(): AccessibilityNodeInfo? {
            val labeled = visible().firstOrNull { it.contentDescription?.toString() == label } ?: return null
            return generateSequence(labeled) { it.parent }.mapNotNull { node -> nodes(node).firstOrNull(::editable) }.firstOrNull()
        }
        waitFor("Find field $label") { field() != null }
        assertTrue(field()!!.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }))
        SystemClock.sleep(200)
    }
    private fun hideKeyboard(scenario: ActivityScenario<MainActivity>) {
        scenario.onActivity { activity ->
            (activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                .hideSoftInputFromWindow(activity.window.decorView.windowToken, 0)
        }
        SystemClock.sleep(200)
    }

    private val systems = listOf(AiPromptEntry("flow-system", "Flow coach", "Coach @PLAYER@"))
    private val prompts = listOf(AiPromptEntry("flow-prompt", "Flow question", "Analyse @FEN@ with @ENGINE@"))
    private val instructions = listOf(AiInstructionEntry("flow-instruction", "Flow options", "<model>original@provider</model>"))
    private val choice = AiReportSelection("flow-system", "flow-prompt", "flow-instruction")

    private fun fixture(test: (SettingsPreferences) -> Unit) {
        val settings = SettingsPreferences(context.getSharedPreferences(SettingsPreferences.PREFS_NAME, Context.MODE_PRIVATE))
        settings.seedAiSystemPrompts(BundledAiPrompts.loadSystemPrompts(context.assets))
        settings.seedAiReportPrompts(BundledAiPrompts.loadReportPrompts(context.assets))
        val previous = settings.exportAllSettings()
        try {
            settings.saveAiSetup(systems, prompts, instructions)
            settings.saveAiReportSelection(AiReportSelection())
            test(settings)
        } finally { assertTrue(settings.importAllSettings(previous)) }
    }

    private fun show(scenario: ActivityScenario<MainActivity>, receiver: Context): GameViewModel {
        lateinit var vm: GameViewModel
        scenario.onActivity { activity ->
            vm = ViewModelProvider(activity)[GameViewModel::class.java]
            activity.setContent {
                val state by vm.uiState.collectAsState()
                EvalTheme { AiReportFlowScreen(state, vm::updateAiReportSelection, vm::editAiReport,
                    vm::updateAiReportDraft, { vm.submitAiReport(receiver) }, vm::backToAiReportSelection,
                    vm::dismissAiInstructionSelection, vm::stopAiEngineAndContinue) }
            }
        }
        waitFor("Fixture loaded") { vm.uiState.value.aiInstructions == instructions }
        return vm
    }

    private fun screenshot(name: String) {
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        java.io.File(context.cacheDir, name).outputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }

    @Test fun three_choices_then_edit_all_parts_submit_once_and_remember_after_restart() = fixture { settings ->
        val sent = CopyOnWriteArrayList<Intent>()
        val receiver = object : ContextWrapper(context) {
            override fun startActivity(intent: Intent) { sent.add(intent) }
        }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val vm = show(scenario, receiver)
            scenario.onActivity { vm.requestGameAiReport() }
            click("Select System prompts", true); click("Flow coach")
            click("Select Prompts", true); click("Flow question")
            click("Select AI instructions", true); click("Flow options")
            assertEquals(choice, settings.loadAiReportSelection())
            screenshot("ai-report-select.png")
            click("Next")
            assertTrue(sent.isEmpty())
            assertNull(vm.uiState.value.aiMovesProgress)
            assertEquals(AiReportDraft(systems.single().text, prompts.single().text, instructions.single().instructions),
                vm.uiState.value.aiReportDraft)
            setText("System prompt", "Edited coach @PLAYER@")
            hideKeyboard(scenario)
            setText("Prompt", "Edited question @FEN@")
            hideKeyboard(scenario)
            setText("AI instructions", "<model>chosen@provider</model><next>View</next>")
            hideKeyboard(scenario)
            val edited = vm.uiState.value.aiReportDraft
            systemBack()
            click("Next")
            assertEquals(edited, vm.uiState.value.aiReportDraft)
            scenario.recreate()
            show(scenario, receiver)
            waitFor("Review restored") { visible().any { it.text?.toString() == "Edit AI request" } }
            assertEquals(edited, vm.uiState.value.aiReportDraft)
            screenshot("ai-report-edit.png")
            click("Submit")
            waitFor("Submitted exactly once") { sent.size == 1 && vm.uiState.value.pendingAiReport == null }
            val payload = sent.single().getStringExtra("instructions")!!
            assertTrue(payload.contains("<system>Edited coach @PLAYER@</system>"))
            assertTrue(payload.contains("<prompt>Edited question @FEN@</prompt>"))
            assertTrue(payload.contains("<model>chosen@provider</model><next>View</next>"))
            assertTrue(payload.contains("<fen>"))
            assertFalse(payload.contains("<engine>"))
            assertFalse(payload.contains("original@provider"))
            assertEquals(systems, settings.loadAiSystemPrompts())
            assertEquals(prompts, settings.loadAiReportPrompts())
            assertEquals(instructions, settings.loadAiInstructions())
        }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val vm = show(scenario, receiver)
            scenario.onActivity { vm.requestPlayerAiReport("Test player", "lichess.org") }
            waitFor("All last choices restored") { visible().count { it.text?.toString() in setOf("Flow coach", "Flow question", "Flow options") } == 3 }
            assertEquals(choice, vm.uiState.value.aiReportSelection)
            assertEquals("Test player", vm.uiState.value.pendingAiReport!!.player)
            assertEquals("", vm.uiState.value.pendingAiReport!!.fen)
            click("Next")
            assertEquals(prompts.single().text, vm.uiState.value.aiReportDraft!!.prompt)
            scenario.onActivity { vm.dismissAiInstructionSelection() }
            assertEquals(1, sent.size)
        }
    }

    @Test fun none_is_remembered_and_changed_choices_replace_draft_without_changing_templates() = fixture { settings ->
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val vm = show(scenario, context)
            scenario.onActivity { vm.requestPlayerAiReport("Test player") }
            click("Select System prompts", true); click("Flow coach")
            click("Select System prompts", true); click("None")
            assertEquals(AiReportSelection(), settings.loadAiReportSelection())
            click("Next")
            assertEquals(AiReportDraft(), vm.uiState.value.aiReportDraft)
            waitFor("Empty review rendered") { visible().any { it.text?.toString() == "Submit" } }
            assertFalse(generateSequence(visible().first { it.text?.toString() == "Submit" }) { it.parent }.all { it.isEnabled })
            setText("Prompt", "Temporary question")
            hideKeyboard(scenario)
            systemBack()
            click("Select Prompts", true); click("Flow question")
            click("Next")
            assertEquals(prompts.single().text, vm.uiState.value.aiReportDraft!!.prompt)
            scenario.onActivity {
                vm.dismissAiInstructionSelection()
                vm.deleteAiPrompt("flow-prompt", false)
                vm.requestPlayerAiReport("Second player")
            }
            assertEquals(AiReportSelection(), vm.uiState.value.aiReportSelection)
            assertEquals(AiReportSelection(), settings.loadAiReportSelection())
            scenario.onActivity { vm.dismissAiInstructionSelection() }
        }
    }
}
