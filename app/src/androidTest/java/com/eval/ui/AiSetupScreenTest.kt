package com.eval.ui

import android.content.Context
import android.os.Bundle
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import android.view.inputmethod.InputMethodManager
import androidx.activity.compose.setContent
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.eval.MainActivity
import com.eval.ui.theme.EvalTheme
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AiSetupScreenTest {
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

    @Test fun all_three_cruds_are_independent_and_offer_the_requested_popups() {
        val settings = SettingsPreferences(context.getSharedPreferences(SettingsPreferences.PREFS_NAME, Context.MODE_PRIVATE))
        settings.seedAiSystemPrompts(BundledAiPrompts.loadSystemPrompts(context.assets))
        settings.seedAiReportPrompts(BundledAiPrompts.loadReportPrompts(context.assets))
        val previous = settings.exportAllSettings()
        try {
            settings.saveAiSetup(emptyList(), emptyList(), emptyList())
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    val vm = ViewModelProvider(activity)[GameViewModel::class.java]
                    activity.setContent { EvalTheme { SettingsScreenNav(vm, {}) } }
                }
                click("AI setup")
                click("System prompts")
                click("+ Add system prompt")
                setText("Name", "Test coach")
                setText("System prompt text", "@")
                click("@FEN@")
                hideKeyboard(scenario)
                setText("System prompt text", "Coach for @FEN@")
                click("Save")
                waitFor("System prompt persisted") { settings.loadAiSystemPrompts().singleOrNull()?.text == "Coach for @FEN@" }
                systemBack()
                click("Prompts")
                click("+ Add prompt")
                setText("Name", "Test question")
                setText("Prompt text", "@")
                click("@COLOR@")
                hideKeyboard(scenario)
                setText("Prompt text", "Explain @COLOR@ to move")
                click("Save")
                click("Test question")
                setText("Prompt text", "Explain @COLOR@ to move at @FEN@")
                click("Save")
                systemBack()
                click("AI instructions")
                click("+ Add Instruction")
                setText("Name", "Test report")
                assertFalse(visible().any { it.contentDescription?.toString()?.startsWith("Select ") == true })
                setText("AI instructions", "<")
                click("<model>")
                hideKeyboard(scenario)
                waitFor("Paired command inserted") { visible().any { editable(it) && it.text?.toString() == "<model></model>" } }
                setText("AI instructions", "<model>test@provider</model><open>")
                setText("AI instructions", "<model>test@provider</model><open>@")
                click("@FEN@")
                hideKeyboard(scenario)
                setText("AI instructions", "<model>test@provider</model><open>@FEN@</open>")
                click("Save")
                waitFor("Instruction persisted") { settings.loadAiInstructions().size == 1 }
                val saved = settings.loadAiInstructions().single()
                assertEquals("<model>test@provider</model><open>@FEN@</open>", saved.instructions)
                click("Test report")
                assertFalse(visible().any { it.contentDescription?.toString()?.startsWith("Select ") == true })
                instrumentation.uiAutomation.takeScreenshot().let { bitmap ->
                    java.io.File(context.cacheDir, "ai-setup-instruction.png").outputStream().use {
                        bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                    }
                    bitmap.recycle()
                }
                setText("Name", "Updated report")
                click("Save")
                systemBack()
                click("System prompts")
                click("Delete Test coach", description = true)
                click("Delete")
                waitFor("System prompt deleted") { settings.loadAiSystemPrompts().isEmpty() }
                assertEquals(saved.instructions, settings.loadAiInstructions().single().instructions)
                systemBack()
                click("Prompts")
                click("Delete Test question", description = true)
                click("Delete")
                waitFor("Prompt deleted") { settings.loadAiReportPrompts().isEmpty() }
                assertEquals(saved.instructions, settings.loadAiInstructions().single().instructions)
                systemBack()
                click("AI instructions")
                click("Delete Updated report", description = true)
                click("Delete")
                waitFor("Instruction deleted") { settings.loadAiInstructions().isEmpty() }
            }
        } finally { assertTrue(settings.importAllSettings(previous)) }
    }
}
