package com.manjit.pocketmind.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.manjit.pocketmind.core.designsystem.component.LoadingState
import com.manjit.pocketmind.core.model.ThemeMode
import com.manjit.pocketmind.core.model.UserSettings
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(navigation: SettingsNavigation, viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SettingsContent(state, viewModel, navigation)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsContent(state: SettingsUiState, vm: SettingsViewModel, navigation: SettingsNavigation) {
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.feature_settings_title)) }) }) { padding ->
        if (state.isLoading) {
            LoadingState(Modifier.padding(padding))
            return@Scaffold
        }
        val s = state.settings
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            Section(R.string.feature_settings_section_model)
            NavRow(
                title = stringResource(R.string.feature_settings_model),
                subtitle = state.selectedModelName ?: stringResource(R.string.feature_settings_no_model),
                onClick = navigation.onOpenModels,
            )
            SwitchRow(
                title = stringResource(R.string.feature_settings_gpu),
                subtitle = state.activeBackend?.let { stringResource(R.string.feature_settings_gpu_active, it) }
                    ?: stringResource(R.string.feature_settings_gpu_hint),
                checked = s.preferGpu,
                onChange = vm::setPreferGpu,
            )

            Section(R.string.feature_settings_section_answers)
            TemperatureSlider(s.temperature, vm::setTemperature)
            TopKSlider(s.topK, vm::setTopK)
            SystemPromptRow(state.effectiveSystemPrompt, isCustom = s.customSystemPrompt != null, vm)

            Section(R.string.feature_settings_section_appearance)
            ThemeChooser(s.themeMode, vm::setThemeMode)

            Section(R.string.feature_settings_section_downloads)
            SwitchRow(
                title = stringResource(R.string.feature_settings_mobile_data),
                subtitle = stringResource(R.string.feature_settings_mobile_data_hint),
                checked = s.allowMobileData,
                onChange = vm::setAllowMobileData,
            )

            Section(R.string.feature_settings_section_tools)
            NavRow(
                title = stringResource(R.string.feature_settings_benchmark),
                subtitle = stringResource(R.string.feature_settings_benchmark_hint),
                onClick = navigation.onOpenBenchmark,
            )
        }
    }
}

@Composable
private fun Section(titleRes: Int) {
    HorizontalDivider(Modifier.padding(top = 8.dp))
    Text(
        text = stringResource(titleRes),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .padding(start = 16.dp, top = 16.dp, bottom = 4.dp)
            .semantics { heading() },
    )
}

@Composable
private fun NavRow(title: String, subtitle: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
        modifier = Modifier.clickable(role = Role.Button, onClick = onClick),
    )
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    // The whole row toggles: large touch target and one TalkBack announcement ("Switch, on").
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
        modifier = Modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
    )
}

/**
 * Sliders keep a local value while dragging and save only when the finger lifts
 * (onValueChangeFinished): one DataStore write instead of dozens per drag.
 */
@Composable
private fun TemperatureSlider(saved: Float, onSave: (Float) -> Unit) {
    var value by remember(saved) { mutableFloatStateOf(saved) }
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(stringResource(R.string.feature_settings_temperature, String.format(Locale.US, "%.1f", value)))
        Text(stringResource(R.string.feature_settings_temperature_hint), style = MaterialTheme.typography.bodySmall)
        Slider(
            value = value,
            onValueChange = { value = (it * STEPS_PER_UNIT).roundToInt() / STEPS_PER_UNIT },
            valueRange = UserSettings.MIN_TEMPERATURE..UserSettings.MAX_TEMPERATURE,
            onValueChangeFinished = { onSave(value) },
        )
    }
}

@Composable
private fun TopKSlider(saved: Int, onSave: (Int) -> Unit) {
    var value by remember(saved) { mutableFloatStateOf(saved.toFloat()) }
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(stringResource(R.string.feature_settings_top_k, value.roundToInt()))
        Text(stringResource(R.string.feature_settings_top_k_hint), style = MaterialTheme.typography.bodySmall)
        Slider(
            value = value,
            onValueChange = { value = it },
            valueRange = UserSettings.MIN_TOP_K.toFloat()..UserSettings.MAX_TOP_K.toFloat(),
            onValueChangeFinished = { onSave(value.roundToInt()) },
        )
    }
}

@Composable
private fun SystemPromptRow(current: String, isCustom: Boolean, vm: SettingsViewModel) {
    var editing by rememberSaveable { mutableStateOf(false) }
    ListItem(
        headlineContent = { Text(stringResource(R.string.feature_settings_system_prompt)) },
        supportingContent = { Text(current, maxLines = 2) },
        modifier = Modifier.clickable(role = Role.Button) { editing = true },
    )
    if (editing) {
        var text by rememberSaveable { mutableStateOf(current) }
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text(stringResource(R.string.feature_settings_system_prompt)) },
            text = { OutlinedTextField(value = text, onValueChange = { text = it }, minLines = 4) },
            confirmButton = {
                TextButton(onClick = {
                    vm.setSystemPrompt(text)
                    editing = false
                }) { Text(stringResource(R.string.feature_settings_save)) }
            },
            dismissButton = {
                if (isCustom) {
                    TextButton(onClick = {
                        vm.resetSystemPrompt()
                        editing = false
                    }) { Text(stringResource(R.string.feature_settings_reset)) }
                } else {
                    TextButton(onClick = { editing = false }) {
                        Text(stringResource(R.string.feature_settings_cancel))
                    }
                }
            },
        )
    }
}

@Composable
private fun ThemeChooser(current: ThemeMode, onChange: (ThemeMode) -> Unit) {
    // selectableGroup + Role.RadioButton: TalkBack reads "1 of 3, selected" correctly.
    Column(Modifier.selectableGroup()) {
        ThemeMode.entries.forEach { mode ->
            val label = when (mode) {
                ThemeMode.SYSTEM -> R.string.feature_settings_theme_system
                ThemeMode.LIGHT -> R.string.feature_settings_theme_light
                ThemeMode.DARK -> R.string.feature_settings_theme_dark
            }
            ListItem(
                headlineContent = { Text(stringResource(label)) },
                leadingContent = { RadioButton(selected = current == mode, onClick = null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(selected = current == mode, role = Role.RadioButton) { onChange(mode) },
            )
        }
    }
}

private const val STEPS_PER_UNIT = 10f
