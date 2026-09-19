package com.eval.ui

import androidx.compose.foundation.background
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

/** Create or edit the commands and context sent to the AI app. */
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

    Column(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .imePadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        EvalTitleBar(
            title = if (existingInstruction != null) "Edit AI interface" else "New AI interface",
            onBackClick = onBackToList,
            onEvalClick = onBackToGame
        )
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Name", style = MaterialTheme.typography.titleSmall)
            OutlinedTextField(
                value = name, onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                placeholder = { Text("e.g. Position report") }
            )
            Text("AI interface", style = MaterialTheme.typography.titleSmall)
            Text(
                "Commands control how the AI app creates and presents a report. " +
                    "Context placeholders insert details from the current position, player or date. " +
                    "Type < for a popup of commands, or @ for a popup of placeholders.",
                style = MaterialTheme.typography.bodySmall, color = AppColors.MediumGray
            )
            OutlinedTextField(
                value = instructions,
                onValueChange = { updated ->
                    if (updated.text != instructions.text) {
                        completion = aiInterfaceCompletion(instructions, updated)
                        if (completion != null) keyboard?.hide()
                    }
                    instructions = updated
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 240.dp)
                    .focusRequester(editorFocus)
                    .semantics { contentDescription = "AI interface instructions" },
                textStyle = MaterialTheme.typography.bodySmall
            )
        }
        Button(
            onClick = {
                onSave(AiInstructionEntry(
                    id = existingInstruction?.id ?: java.util.UUID.randomUUID().toString(),
                    name = name.trim(), instructions = instructions.text
                ))
            },
            enabled = name.isNotBlank(), modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = AppColors.ButtonGreen)
        ) { Text("Save") }
    }

    completion?.let { pending ->
        AiInterfaceChoicePopup(
            kind = pending.kind,
            onSelect = { choice ->
                instructions = insertAiInterfaceChoice(instructions, pending, choice)
                completion = null
                restoreEditorFocus = true
            },
            onDismiss = {
                completion = null
                restoreEditorFocus = true
            }
        )
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
