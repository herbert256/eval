package com.eval.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp

/** Shared by position and player reports. Selection and review never launch AI. */
@Composable
fun AiReportFlowScreen(
    state: GameUiState,
    onSelectionChange: (AiReportSelection) -> Unit,
    onNext: () -> Unit,
    onDraftChange: (AiReportDraft) -> Unit,
    onSubmit: () -> Unit,
    onBackToSelection: () -> Unit,
    onDismiss: () -> Unit,
    onStopAndContinue: () -> Unit = {}
) {
    if (state.aiMovesProgress != null) {
        AiReportProgressScreen(onDismiss, state.aiMovesProgress, state.aiReportError,
            state.aiEngineProgress, state.aiEngineStopping, onStopAndContinue)
    } else if (state.aiReportEditing && state.aiReportDraft != null) {
        AiReportEditScreen(state.aiReportDraft, state.aiReportError, onDraftChange, onSubmit,
            onBackToSelection, onDismiss)
    } else {
        BackHandler(onBack = onDismiss)
        EvalScreen(verticalArrangement = Arrangement.spacedBy(12.dp),
            topBar = { EvalTitleBar("Select AI parts", onBackClick = onDismiss, onEvalClick = onDismiss) }) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Choose the three parts for this request. Your last choices are remembered. Select None to skip a saved part.",
                    color = AppColors.SubtleText, style = MaterialTheme.typography.bodySmall)
                val choice = state.aiReportSelection
                AiPromptSelection("System prompts", choice.systemPromptId, state.aiSystemPrompts) {
                    onSelectionChange(choice.copy(systemPromptId = it))
                }
                AiPromptSelection("Prompts", choice.promptId, state.aiReportPrompts) {
                    onSelectionChange(choice.copy(promptId = it))
                }
                AiPromptSelection("AI instructions", choice.instructionId,
                    state.aiInstructions.map { AiPromptEntry(it.id, it.name, it.instructions) }) {
                    onSelectionChange(choice.copy(instructionId = it))
                }
            }
            Button(onClick = onNext, modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.ButtonGreen)) { Text("Next") }
        }
    }
}

@Composable
private fun AiReportEditScreen(
    draft: AiReportDraft,
    error: String?,
    onChange: (AiReportDraft) -> Unit,
    onSubmit: () -> Unit,
    onBack: () -> Unit,
    onDismiss: () -> Unit
) {
    var system by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue(draft.systemPrompt)) }
    var prompt by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue(draft.prompt)) }
    var instruction by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue(draft.instructions)) }
    fun changed() = onChange(AiReportDraft(system.text, prompt.text, instruction.text))
    BackHandler(onBack = onBack)
    EvalScreen(verticalArrangement = Arrangement.spacedBy(12.dp),
        topBar = { EvalTitleBar("Edit AI request", onBackClick = onBack, onEvalClick = onDismiss) }) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Changes apply to this request only. Type @ for chess context, or < in AI instructions for options.",
                style = MaterialTheme.typography.bodySmall, color = AppColors.SubtleText)
            AiCompletionTextField(system, { system = it; changed() }, "System prompt", false, 120.dp, 6)
            AiCompletionTextField(prompt, { prompt = it; changed() }, "Prompt", false, 120.dp, 6)
            AiCompletionTextField(instruction, { instruction = it; changed() }, "AI instructions", true, 120.dp, 6)
            if (error != null) Text(error, color = MaterialTheme.colorScheme.error)
        }
        Button(onClick = onSubmit, enabled = listOf(system.text, prompt.text, instruction.text).any { it.isNotBlank() },
            modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = AppColors.ButtonGreen)) {
            Text("Submit")
        }
    }
}
