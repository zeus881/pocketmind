package com.sanjay.pocketmind.feature.personas

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sanjay.pocketmind.core.common.format.formatFixed
import com.sanjay.pocketmind.core.designsystem.component.LoadingState
import com.sanjay.pocketmind.core.designsystem.component.readableWidth
import com.sanjay.pocketmind.core.designsystem.theme.PmSpacing
import com.sanjay.pocketmind.core.model.Persona
import com.sanjay.pocketmind.core.model.SavedPrompt
import com.sanjay.pocketmind.core.model.UserSettings
import com.sanjay.pocketmind.feature.personas.resources.Res
import com.sanjay.pocketmind.feature.personas.resources.feature_personas_back
import com.sanjay.pocketmind.feature.personas.resources.feature_personas_built_in
import com.sanjay.pocketmind.feature.personas.resources.feature_personas_cancel
import com.sanjay.pocketmind.feature.personas.resources.feature_personas_creativity
import com.sanjay.pocketmind.feature.personas.resources.feature_personas_delete
import com.sanjay.pocketmind.feature.personas.resources.feature_personas_edit
import com.sanjay.pocketmind.feature.personas.resources.feature_personas_instructions
import com.sanjay.pocketmind.feature.personas.resources.feature_personas_name
import com.sanjay.pocketmind.feature.personas.resources.feature_personas_needs_pro
import com.sanjay.pocketmind.feature.personas.resources.feature_personas_new_persona
import com.sanjay.pocketmind.feature.personas.resources.feature_personas_new_prompt
import com.sanjay.pocketmind.feature.personas.resources.feature_personas_no_prompts
import com.sanjay.pocketmind.feature.personas.resources.feature_personas_own_creativity
import com.sanjay.pocketmind.feature.personas.resources.feature_personas_prompt_text
import com.sanjay.pocketmind.feature.personas.resources.feature_personas_prompt_title
import com.sanjay.pocketmind.feature.personas.resources.feature_personas_save
import com.sanjay.pocketmind.feature.personas.resources.feature_personas_section_personas
import com.sanjay.pocketmind.feature.personas.resources.feature_personas_section_prompts
import com.sanjay.pocketmind.feature.personas.resources.feature_personas_title
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun PersonasScreen(onBack: () -> Unit, viewModel: PersonasViewModel = koinViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    PersonasContent(
        state = state,
        onBack = onBack,
        actions = PersonasActions(
            onSavePersona = viewModel::savePersona,
            onDeletePersona = viewModel::deletePersona,
            onSavePrompt = viewModel::savePrompt,
            onDeletePrompt = viewModel::deletePrompt,
        ),
    )
}

/** What the screen can ask for (grouped; tests pass no-op lambdas). */
data class PersonasActions(
    val onSavePersona: (id: String?, name: String, instructions: String, temperature: Float?) -> Unit,
    val onDeletePersona: (id: String) -> Unit,
    val onSavePrompt: (id: String?, title: String, text: String) -> Unit,
    val onDeletePrompt: (id: String) -> Unit,
)

private sealed interface Editing {
    /** [persona] = null: a new one. */
    data class PersonaForm(val persona: Persona?) : Editing

    data class PromptForm(val prompt: SavedPrompt?) : Editing
}

/**
 * Personas: built-in ones are listed read-only (tap shows their instructions); custom ones can be
 * created, edited and deleted. Prompts: the library the chat input can insert from.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonasContent(state: PersonasUiState, onBack: () -> Unit, actions: PersonasActions) {
    var editing by remember { mutableStateOf<Editing?>(null) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.feature_personas_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(Res.string.feature_personas_back))
                    }
                },
            )
        },
    ) { padding ->
        if (state.isLoading) {
            LoadingState(Modifier.padding(padding))
            return@Scaffold
        }
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .readableWidth(),
        ) {
            item { Section(stringResource(Res.string.feature_personas_section_personas)) }
            items(state.personas, key = { it.id }) { persona ->
                ListItem(
                    headlineContent = { Text(persona.name) },
                    supportingContent = { Text(persona.instructions, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                    overlineContent = if (persona.builtIn) {
                        { Text(stringResource(Res.string.feature_personas_built_in)) }
                    } else {
                        null
                    },
                    trailingContent = if (persona.builtIn) {
                        null
                    } else {
                        { DeleteButton(persona.name) { actions.onDeletePersona(persona.id) } }
                    },
                    modifier = Modifier.clickable(enabled = !persona.builtIn) {
                        editing = Editing.PersonaForm(persona)
                    },
                )
            }
            item {
                if (state.canCreatePersonas) {
                    AddRow(stringResource(Res.string.feature_personas_new_persona)) {
                        editing = Editing.PersonaForm(null)
                    }
                } else {
                    ListItem(
                        headlineContent = { Text(stringResource(Res.string.feature_personas_new_persona)) },
                        supportingContent = { Text(stringResource(Res.string.feature_personas_needs_pro)) },
                    )
                }
            }
            item { Section(stringResource(Res.string.feature_personas_section_prompts)) }
            if (state.prompts.isEmpty()) {
                item {
                    Text(
                        stringResource(Res.string.feature_personas_no_prompts),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = PmSpacing.l),
                    )
                }
            }
            items(state.prompts, key = { it.id }) { prompt ->
                ListItem(
                    headlineContent = { Text(prompt.title) },
                    supportingContent = { Text(prompt.text, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                    trailingContent = { DeleteButton(prompt.title) { actions.onDeletePrompt(prompt.id) } },
                    modifier = Modifier.clickable { editing = Editing.PromptForm(prompt) },
                )
            }
            item {
                AddRow(stringResource(Res.string.feature_personas_new_prompt)) { editing = Editing.PromptForm(null) }
            }
        }
    }

    when (val form = editing) {
        null -> Unit

        is Editing.PersonaForm -> PersonaDialog(
            persona = form.persona,
            onSave = { name, instructions, temperature ->
                actions.onSavePersona(form.persona?.id, name, instructions, temperature)
                editing = null
            },
            onDismiss = { editing = null },
        )

        is Editing.PromptForm -> PromptDialog(
            prompt = form.prompt,
            onSave = { title, text ->
                actions.onSavePrompt(form.prompt?.id, title, text)
                editing = null
            },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun Section(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .padding(start = PmSpacing.l, top = PmSpacing.l, bottom = PmSpacing.xs)
            .semantics { heading() },
    )
}

@Composable
private fun AddRow(label: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(label) },
        leadingContent = { Icon(Icons.Outlined.Add, contentDescription = null) },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun DeleteButton(name: String, onDelete: () -> Unit) {
    IconButton(onClick = onDelete) {
        Icon(Icons.Outlined.Delete, contentDescription = stringResource(Res.string.feature_personas_delete, name))
    }
}

/** Name, instructions, and an optional own creativity level (off = use the Settings value). */
@Composable
private fun PersonaDialog(persona: Persona?, onSave: (String, String, Float?) -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf(persona?.name.orEmpty()) }
    var instructions by rememberSaveable { mutableStateOf(persona?.instructions.orEmpty()) }
    var ownTemperature by rememberSaveable { mutableStateOf(persona?.temperature != null) }
    var temperature by rememberSaveable {
        mutableFloatStateOf(persona?.temperature ?: UserSettings.DEFAULT_TEMPERATURE)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (persona ==
                        null
                    ) {
                        Res.string.feature_personas_new_persona
                    } else {
                        Res.string.feature_personas_edit
                    },
                ),
            )
        },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(Res.string.feature_personas_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = instructions,
                    onValueChange = { instructions = it },
                    label = { Text(stringResource(Res.string.feature_personas_instructions)) },
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = PmSpacing.s),
                )
                ListItem(
                    headlineContent = { Text(stringResource(Res.string.feature_personas_own_creativity)) },
                    trailingContent = { Switch(checked = ownTemperature, onCheckedChange = { ownTemperature = it }) },
                )
                if (ownTemperature) {
                    Text(
                        stringResource(
                            Res.string.feature_personas_creativity,
                            temperature.toDouble().formatFixed(1),
                        ),
                    )
                    Slider(
                        value = temperature,
                        onValueChange = { temperature = (it * STEPS).toInt() / STEPS },
                        valueRange = UserSettings.MIN_TEMPERATURE..UserSettings.MAX_TEMPERATURE,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name, instructions, temperature.takeIf { ownTemperature }) },
                enabled = name.isNotBlank() && instructions.isNotBlank(),
            ) { Text(stringResource(Res.string.feature_personas_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.feature_personas_cancel)) }
        },
    )
}

@Composable
private fun PromptDialog(prompt: SavedPrompt?, onSave: (String, String) -> Unit, onDismiss: () -> Unit) {
    var title by rememberSaveable { mutableStateOf(prompt?.title.orEmpty()) }
    var text by rememberSaveable { mutableStateOf(prompt?.text.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (prompt ==
                        null
                    ) {
                        Res.string.feature_personas_new_prompt
                    } else {
                        Res.string.feature_personas_edit
                    },
                ),
            )
        },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(Res.string.feature_personas_prompt_title)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(stringResource(Res.string.feature_personas_prompt_text)) },
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = PmSpacing.s),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(title, text) }, enabled = title.isNotBlank() && text.isNotBlank()) {
                Text(stringResource(Res.string.feature_personas_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.feature_personas_cancel)) }
        },
    )
}

private const val STEPS = 10f
