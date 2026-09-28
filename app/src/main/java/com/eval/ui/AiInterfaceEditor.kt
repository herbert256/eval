package com.eval.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.Alignment

/** Edit a reusable instruction independently of the system prompt and prompt. */
@Composable
fun AiInstructionEditScreen(
    existingInstruction: AiInstructionEntry?,
    onBackToList: () -> Unit,
    onBackToGame: () -> Unit,
    onSave: (AiInstructionEntry) -> Unit
) {
    var name by rememberSaveable(existingInstruction?.id) { mutableStateOf(existingInstruction?.name ?: "") }
    var instructions by rememberSaveable(existingInstruction?.id, stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(existingInstruction?.instructions ?: ""))
    }
    EvalScreen(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        topBar = { EvalTitleBar(if (existingInstruction != null) "Edit AI instruction" else "New AI instruction",
            onBackClick = onBackToList, onEvalClick = onBackToGame) }
    ) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") },
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Name" }, singleLine = true)
            Text("Type < for AI options, or @ for position, player and date placeholders.",
                style = MaterialTheme.typography.bodySmall, color = AppColors.SubtleText)
            AiCompletionTextField(instructions, { instructions = it }, "AI instructions", allowCommands = true)
        }
        Button(onClick = {
            onSave(AiInstructionEntry(id = existingInstruction?.id ?: java.util.UUID.randomUUID().toString(),
                name = name.trim(), instructions = instructions.text))
        }, enabled = name.isNotBlank(), modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = AppColors.ButtonGreen)) { Text("Save") }
    }
}

/** Inline radio group (the app's selection pattern) with "None" first. */
@Composable
internal fun AiPromptSelection(label: String, selectedId: String, entries: List<AiPromptEntry>, onSelect: (String) -> Unit) {
    val selected = entries.find { it.id == selectedId }
    val options = listOf("" to "None") + entries.sortedBy { it.name.lowercase() }.map { it.id to it.name }
    Column(verticalArrangement = Arrangement.spacedBy(0.dp), modifier = Modifier.selectableGroup()) {
        Text(label, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onBackground)
        options.forEach { (id, name) ->
            val isSelected = id == (selected?.id ?: "")
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelect(id) })
                    .semantics { contentDescription = "$label: $name" }
            ) {
                RadioButton(selected = isSelected, onClick = null)
                Text(name, color = MaterialTheme.colorScheme.onBackground)
            }
        }
        if (selected != null) Text(selected.text, maxLines = 3, style = MaterialTheme.typography.bodySmall, color = AppColors.SubtleText)
        else if (entries.isEmpty()) Text("Add entries in Settings > AI setup > $label.",
            style = MaterialTheme.typography.bodySmall, color = AppColors.SubtleText)
    }
}

/** Shared editor: prompt bodies offer @; the options editor also offers <. */
@Composable
internal fun AiCompletionTextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    label: String,
    allowCommands: Boolean,
    minHeight: androidx.compose.ui.unit.Dp = 240.dp,
    maxLines: Int = Int.MAX_VALUE
) {
    var completion by remember { mutableStateOf<AiInterfaceCompletion?>(null) }
    var restoreEditorFocus by remember { mutableStateOf(false) }
    val editorFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(restoreEditorFocus, completion) {
        if (restoreEditorFocus && completion == null) {
            editorFocus.requestFocus()
            keyboard?.show()
            restoreEditorFocus = false
        }
    }
    OutlinedTextField(value = value, onValueChange = { updated ->
        if (updated.text != value.text) {
            completion = aiInterfaceCompletion(value, updated)?.takeIf {
                allowCommands || it.kind == AiInterfaceChoiceKind.PLACEHOLDER
            }
            if (completion != null) keyboard?.hide()
        }
        onValueChange(updated)
    }, label = { Text(label) }, modifier = Modifier.fillMaxWidth().heightIn(min = minHeight)
        .focusRequester(editorFocus).semantics { contentDescription = label },
        textStyle = MaterialTheme.typography.bodySmall, maxLines = maxLines)
    completion?.let { pending ->
        AiInterfaceChoicePopup(kind = pending.kind, onSelect = { choice ->
            onValueChange(insertAiInterfaceChoice(value, pending, choice))
            completion = null
            restoreEditorFocus = true
        }, onDismiss = { completion = null; restoreEditorFocus = true })
    }
}

// This editor explicitly offers popups so typing a trigger does not leave the editor screen.
@Composable
private fun AiInterfaceChoicePopup(
    kind: AiInterfaceChoiceKind,
    onSelect: (AiInterfaceChoice) -> Unit,
    onDismiss: () -> Unit
) {
    val choices = if (kind == AiInterfaceChoiceKind.COMMAND) aiInterfaceCommands else aiInterfacePlaceholders
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(kind.title) },
        text = {
            LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                items(choices, key = { it.name }) { choice ->
                    Column(
                        modifier = Modifier.fillMaxWidth().clickable { onSelect(choice) }
                            .padding(vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            if (kind == AiInterfaceChoiceKind.COMMAND) "<${choice.name}>" else "@${choice.name}@",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            choice.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
