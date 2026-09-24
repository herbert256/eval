package com.eval.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import java.util.UUID

/** A dedicated settings area for the three reusable parts of an AI request. */
@Composable
fun AiSetupScreen(
    systemPrompts: List<AiPromptEntry>,
    prompts: List<AiPromptEntry>,
    instructions: List<AiInstructionEntry>,
    onBack: () -> Unit,
    onBackToGame: () -> Unit,
    onSavePrompt: (AiPromptEntry, Boolean) -> Unit,
    onDeletePrompt: (String, Boolean) -> Unit,
    onSaveInstruction: (AiInstructionEntry) -> Unit,
    onDeleteInstruction: (String) -> Unit
) {
    var page by rememberSaveable { mutableStateOf("setup") }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    val back = {
        when {
            page.endsWith("/edit") -> page = page.removeSuffix("/edit")
            page != "setup" -> page = "setup"
            else -> onBack()
        }
    }
    BackHandler(onBack = back)
    val isSystem = page.startsWith("systems")
    val catalog = if (isSystem) systemPrompts else prompts
    val category = if (isSystem) "System prompts" else "Prompts"
    val singular = if (isSystem) "system prompt" else "prompt"
    when (page) {
        "setup" -> EvalScreen(scrollable = true, verticalArrangement = Arrangement.spacedBy(12.dp),
            topBar = { EvalTitleBar("AI setup", onBackClick = back, onEvalClick = onBackToGame) }) {
            SettingsNavigationCard("System prompts", "Reusable guidance for the AI", { page = "systems" })
            SettingsNavigationCard("Prompts", "Reusable questions with chess context", { page = "prompts" })
            SettingsNavigationCard("AI instructions", "Reusable AI instruction text", { page = "instructions" })
        }
        "systems", "prompts" -> AiPromptListScreen(
            title = category, singular = singular, entries = catalog,
            onBack = back, onBackToGame = onBackToGame,
            onEdit = { editingId = it; page += "/edit" },
            onAdd = { editingId = null; page += "/edit" },
            onDelete = { onDeletePrompt(it, isSystem) }
        )
        "systems/edit", "prompts/edit" -> AiPromptEditScreen(
            entry = catalog.find { it.id == editingId }, singular = singular,
            onBack = back, onBackToGame = onBackToGame,
            onSave = { onSavePrompt(it, isSystem); back() }
        )
        "instructions" -> AiInstructionsListScreen(
            instructions, back, onBackToGame,
            onEditInstruction = { editingId = it; page = "instructions/edit" },
            onAddInstruction = { editingId = null; page = "instructions/edit" },
            onCopyInstruction = {
                val copy = it.copy(id = UUID.randomUUID().toString(), name = it.name + " (copy)")
                onSaveInstruction(copy); editingId = copy.id; page = "instructions/edit"
            }, onDeleteInstruction = onDeleteInstruction
        )
        "instructions/edit" -> AiInstructionEditScreen(
            existingInstruction = instructions.find { it.id == editingId },
            onBackToList = back, onBackToGame = onBackToGame,
            onSave = { onSaveInstruction(it); back() }
        )
    }
}

@Composable
private fun AiPromptListScreen(
    title: String,
    singular: String,
    entries: List<AiPromptEntry>,
    onBack: () -> Unit,
    onBackToGame: () -> Unit,
    onEdit: (String) -> Unit,
    onAdd: () -> Unit,
    onDelete: (String) -> Unit
) {
    var deletingId by rememberSaveable { mutableStateOf<String?>(null) }
    val deleting = entries.find { it.id == deletingId }
    if (deleting != null) {
        BackHandler { deletingId = null }
        EvalScreen(verticalArrangement = Arrangement.spacedBy(16.dp),
            topBar = { EvalTitleBar("Delete $singular", onBackClick = { deletingId = null }, onEvalClick = onBackToGame) }) {
            Text("Delete \"${deleting.name}\"?", color = MaterialTheme.colorScheme.onBackground)
            Spacer(Modifier.weight(1f))
            Button(onClick = { onDelete(deleting.id); deletingId = null }, modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Delete") }
            TextButton(onClick = { deletingId = null }, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
        }
        return
    }
    EvalScreen(scrollable = true, verticalArrangement = Arrangement.spacedBy(12.dp),
        topBar = { EvalTitleBar(title, onBackClick = onBack, onEvalClick = onBackToGame) }) {
        Text("Save reusable text here, then select it when calling AI. Type @ in the editor to insert chess context.",
            style = MaterialTheme.typography.bodySmall, color = AppColors.SubtleText)
        if (entries.isEmpty()) Text("No ${title.lowercase()} yet.", color = AppColors.SubtleText)
        entries.sortedBy { it.name.lowercase() }.forEach { entry ->
            Card(modifier = Modifier.fillMaxWidth().clickable { onEdit(entry.id) }) {
                Row(Modifier.fillMaxWidth().padding(16.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(entry.name, style = MaterialTheme.typography.titleMedium)
                        Text(entry.text, maxLines = 3, style = MaterialTheme.typography.bodySmall, color = AppColors.SubtleText)
                    }
                    TextButton(onClick = { deletingId = entry.id },
                        modifier = Modifier.semantics { contentDescription = "Delete ${entry.name}" }) {
                        Text("X", color = AppColors.NegativeRed)
                    }
                }
            }
        }
        Button(onClick = onAdd, modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = AppColors.ButtonGreen)) { Text("+ Add $singular") }
    }
}

@Composable
private fun AiPromptEditScreen(
    entry: AiPromptEntry?, singular: String, onBack: () -> Unit, onBackToGame: () -> Unit,
    onSave: (AiPromptEntry) -> Unit
) {
    var name by rememberSaveable(entry?.id) { mutableStateOf(entry?.name ?: "") }
    var text by rememberSaveable(entry?.id, stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue(entry?.text ?: "")) }
    EvalScreen(verticalArrangement = Arrangement.spacedBy(12.dp),
        topBar = { EvalTitleBar("${if (entry == null) "New" else "Edit"} $singular", onBackClick = onBack, onEvalClick = onBackToGame) }) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") },
                singleLine = true, modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Name" })
            Text("Type @ to choose a position, player or date placeholder. Saved changes are used the next time you select this prompt.",
                style = MaterialTheme.typography.bodySmall, color = AppColors.SubtleText)
            AiCompletionTextField(text, { text = it }, if (singular == "system prompt") "System prompt text" else "Prompt text", allowCommands = false)
        }
        Button(onClick = { onSave(AiPromptEntry(entry?.id ?: UUID.randomUUID().toString(), name.trim(), text.text)) },
            enabled = name.isNotBlank() && text.text.isNotBlank(), modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = AppColors.ButtonGreen)) { Text("Save") }
    }
}
