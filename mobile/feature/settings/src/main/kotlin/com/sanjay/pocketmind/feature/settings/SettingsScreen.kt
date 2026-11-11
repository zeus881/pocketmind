package com.sanjay.pocketmind.feature.settings

import android.app.LocaleManager
import android.content.Intent
import android.os.Build
import android.os.LocaleList
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sanjay.pocketmind.core.designsystem.component.LoadingState
import com.sanjay.pocketmind.core.designsystem.component.readableWidth
import com.sanjay.pocketmind.core.designsystem.component.rememberPmHaptics
import com.sanjay.pocketmind.core.designsystem.theme.PmSpacing
import com.sanjay.pocketmind.core.model.ThemeMode
import com.sanjay.pocketmind.core.model.UserSettings
import java.time.LocalDate
import java.util.Locale
import kotlin.math.roundToInt
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun SettingsScreen(navigation: SettingsNavigation, viewModel: SettingsViewModel = koinViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            val message = when (event) {
                SettingsEvent.DataExported -> R.string.feature_settings_data_exported

                SettingsEvent.ExportFailed -> R.string.feature_settings_data_export_failed

                SettingsEvent.DataDeleted -> R.string.feature_settings_data_deleted

                SettingsEvent.ProUnavailable -> R.string.feature_settings_pro_unavailable

                is SettingsEvent.Restored ->
                    if (event.isPro) R.string.feature_settings_pro_restored else R.string.feature_settings_pro_not_found

                is SettingsEvent.ShareDiagnostics -> {
                    // The share sheet shows the text; the user decides whether and where to send it.
                    val subject = resources.getString(R.string.feature_settings_diagnostics_subject)
                    val send = Intent(Intent.ACTION_SEND)
                        .setType("text/plain")
                        .putExtra(Intent.EXTRA_SUBJECT, subject)
                        .putExtra(Intent.EXTRA_TEXT, event.report)
                    context.startActivity(Intent.createChooser(send, null))
                    null
                }
            }
            message?.let { snackbarHostState.showSnackbar(resources.getString(it)) }
        }
    }
    SettingsContent(state, viewModel, navigation, snackbarHostState)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsContent(
    state: SettingsUiState,
    vm: SettingsActions,
    navigation: SettingsNavigation,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.feature_settings_title)) }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (state.isLoading) {
            LoadingState(Modifier.padding(padding))
            return@Scaffold
        }
        val s = state.settings
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                // Centred at a readable width on tablets and foldables.
                .readableWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            ProSection(state, vm)

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
            KeepModelChooser(s.keepModelLoadedMinutes, vm::setKeepModelLoaded)

            Section(R.string.feature_settings_section_answers)
            TemperatureSlider(s.temperature, vm::setTemperature)
            TopKSlider(s.topK, vm::setTopK)
            SystemPromptRow(state.effectiveSystemPrompt, isCustom = s.customSystemPrompt != null, vm)
            NavRow(
                title = stringResource(R.string.feature_settings_personas),
                subtitle = stringResource(R.string.feature_settings_personas_hint),
                onClick = navigation.onOpenPersonas,
            )

            Section(R.string.feature_settings_section_appearance)
            ThemeChooser(s.themeMode, vm::setThemeMode)
            // Per-app language exists from Android 13; older phones follow the phone's language.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) LanguageChooser()
            // Wallpaper colours exist only on Android 12+; older phones always use the brand colours.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                SwitchRow(
                    title = stringResource(R.string.feature_settings_dynamic_color),
                    subtitle = stringResource(R.string.feature_settings_dynamic_color_hint),
                    checked = s.dynamicColor,
                    onChange = vm::setDynamicColor,
                )
            }

            Section(R.string.feature_settings_section_downloads)
            SwitchRow(
                title = stringResource(R.string.feature_settings_mobile_data),
                subtitle = stringResource(R.string.feature_settings_mobile_data_hint),
                checked = s.allowMobileData,
                onChange = vm::setAllowMobileData,
            )

            Section(R.string.feature_settings_section_privacy)
            AppLockRow(s.appLockEnabled, vm::setAppLock)
            if (s.appLockEnabled) LockTimeoutChooser(s.appLockTimeoutSeconds, vm::setAppLockTimeout)
            Text(
                text = stringResource(R.string.feature_settings_privacy_statement),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = PmSpacing.l, vertical = PmSpacing.s),
            )

            Section(R.string.feature_settings_section_data)
            YourData(vm)

            Section(R.string.feature_settings_section_tools)
            NavRow(
                title = stringResource(R.string.feature_settings_benchmark),
                subtitle = stringResource(R.string.feature_settings_benchmark_hint),
                onClick = navigation.onOpenBenchmark,
            )
            NavRow(
                title = stringResource(R.string.feature_settings_diagnostics),
                subtitle = stringResource(R.string.feature_settings_diagnostics_hint),
                onClick = vm::shareDiagnostics,
            )
            NavRow(
                title = stringResource(R.string.feature_settings_licenses),
                subtitle = stringResource(R.string.feature_settings_licenses_hint),
                onClick = navigation.onOpenLicenses,
            )
        }
    }
}

/**
 * PocketMind Pro: one-time purchase through Google Play. The price is Play's own text in the user's
 * currency (set by the owner in Play Console, never in code).
 */
@Composable
private fun ProSection(state: SettingsUiState, vm: SettingsActions) {
    Section(R.string.feature_settings_section_pro)
    if (state.isPro) {
        ListItem(
            headlineContent = { Text(stringResource(R.string.feature_settings_pro_unlocked)) },
            supportingContent = { Text(stringResource(R.string.feature_settings_pro_benefits)) },
        )
        return
    }
    val activity = LocalActivity.current
    NavRow(
        title = stringResource(
            R.string.feature_settings_pro_get,
            state.proPrice ?: stringResource(R.string.feature_settings_pro_price_unknown),
        ),
        subtitle = stringResource(R.string.feature_settings_pro_benefits),
        onClick = { activity?.let(vm::buyPro) },
    )
    NavRow(
        title = stringResource(R.string.feature_settings_pro_restore),
        subtitle = stringResource(R.string.feature_settings_pro_restore_hint),
        onClick = vm::restorePro,
    )
}

/**
 * Export everything (a ZIP the user saves where they like) and delete everything (after an explicit
 * confirmation that says what goes and what stays).
 */
@Composable
private fun YourData(vm: SettingsActions) {
    val saveZip = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        uri?.let { vm.exportAllData(it.toString()) }
    }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    NavRow(
        title = stringResource(R.string.feature_settings_export_data),
        subtitle = stringResource(R.string.feature_settings_export_data_hint),
        onClick = { saveZip.launch("pocketmind-export-${LocalDate.now()}.zip") },
    )
    NavRow(
        title = stringResource(R.string.feature_settings_delete_data),
        subtitle = stringResource(R.string.feature_settings_delete_data_hint),
        onClick = { confirmDelete = true },
    )
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.feature_settings_delete_data_confirm_title)) },
            text = { Text(stringResource(R.string.feature_settings_delete_data_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    vm.deleteAllData()
                }) {
                    Text(
                        stringResource(R.string.feature_settings_delete_data_confirm),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(stringResource(R.string.feature_settings_cancel))
                }
            },
        )
    }
}

@Composable
private fun Section(titleRes: Int) {
    HorizontalDivider(Modifier.padding(top = PmSpacing.s))
    Text(
        text = stringResource(titleRes),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .padding(start = PmSpacing.l, top = PmSpacing.l, bottom = PmSpacing.xs)
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
    val haptics = rememberPmHaptics()
    // The whole row toggles: large touch target and one TalkBack announcement ("Switch, on").
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
        modifier = Modifier.toggleable(value = checked, role = Role.Switch) { on ->
            haptics.toggle(on)
            onChange(on)
        },
    )
}

/**
 * Sliders keep a local value while dragging and save only when the finger lifts
 * (onValueChangeFinished): one DataStore write instead of dozens per drag.
 */
@Composable
private fun TemperatureSlider(saved: Float, onSave: (Float) -> Unit) {
    var value by remember(saved) { mutableFloatStateOf(saved) }
    Column(Modifier.padding(horizontal = PmSpacing.l, vertical = PmSpacing.s)) {
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
    Column(Modifier.padding(horizontal = PmSpacing.l, vertical = PmSpacing.s)) {
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
private fun SystemPromptRow(current: String, isCustom: Boolean, vm: SettingsActions) {
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

/**
 * App lock can only be turned on if the phone has a fingerprint/face OR a screen lock;
 * otherwise nobody could unlock the app again.
 */
@Composable
private fun AppLockRow(enabled: Boolean, onChange: (Boolean) -> Unit) {
    val context = LocalContext.current
    val available = remember {
        BiometricManager.from(context).canAuthenticate(BIOMETRIC_WEAK or DEVICE_CREDENTIAL) ==
            BiometricManager.BIOMETRIC_SUCCESS
    }
    if (available || enabled) {
        SwitchRow(
            title = stringResource(R.string.feature_settings_app_lock),
            subtitle = stringResource(R.string.feature_settings_app_lock_hint),
            checked = enabled,
            onChange = onChange,
        )
    } else {
        ListItem(
            headlineContent = { Text(stringResource(R.string.feature_settings_app_lock)) },
            supportingContent = { Text(stringResource(R.string.feature_settings_app_lock_unavailable)) },
        )
    }
}

/** "Lock after: immediately / 30 s / 1 min / 5 min" as one radio group. */
@Composable
private fun LockTimeoutChooser(current: Int, onChange: (Int) -> Unit) {
    Text(
        text = stringResource(R.string.feature_settings_lock_after),
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.padding(start = PmSpacing.l, top = PmSpacing.s),
    )
    Column(Modifier.selectableGroup()) {
        UserSettings.LOCK_TIMEOUT_OPTIONS.forEach { seconds ->
            val label = when (seconds) {
                0 -> stringResource(R.string.feature_settings_lock_immediately)

                else -> if (seconds < SECONDS_PER_MINUTE) {
                    pluralStringResource(R.plurals.feature_settings_lock_seconds, seconds, seconds)
                } else {
                    val minutes = seconds / SECONDS_PER_MINUTE
                    pluralStringResource(R.plurals.feature_settings_lock_minutes, minutes, minutes)
                }
            }
            ListItem(
                headlineContent = { Text(label) },
                leadingContent = { RadioButton(selected = current == seconds, onClick = null) },
                modifier = Modifier.selectable(
                    selected = current == seconds,
                    role = Role.RadioButton,
                    onClick = { onChange(seconds) },
                ),
            )
        }
    }
}

private const val SECONDS_PER_MINUTE = 60

/** "Keep the model loaded after leaving the app: unload right away / 5 min / 15 min". */
@Composable
private fun KeepModelChooser(current: Int, onChange: (Int) -> Unit) {
    Text(
        text = stringResource(R.string.feature_settings_keep_model),
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.padding(start = PmSpacing.l, top = PmSpacing.s, end = PmSpacing.l),
    )
    Text(
        text = stringResource(R.string.feature_settings_keep_model_hint),
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(horizontal = PmSpacing.l),
    )
    Column(Modifier.selectableGroup()) {
        UserSettings.KEEP_MODEL_OPTIONS.forEach { minutes ->
            val label = if (minutes == 0) {
                stringResource(R.string.feature_settings_keep_model_none)
            } else {
                pluralStringResource(R.plurals.feature_settings_keep_model_minutes, minutes, minutes)
            }
            ListItem(
                headlineContent = { Text(label) },
                leadingContent = { RadioButton(selected = current == minutes, onClick = null) },
                modifier = Modifier.selectable(
                    selected = current == minutes,
                    role = Role.RadioButton,
                    onClick = { onChange(minutes) },
                ),
            )
        }
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

/** The app's languages (same list as res/xml/locales_config.xml); "" = follow the phone. */
private val APP_LANGUAGES = listOf(
    "" to R.string.feature_settings_language_system,
    "en" to R.string.feature_settings_language_en,
    "hi" to R.string.feature_settings_language_hi,
    "pa" to R.string.feature_settings_language_pa,
)

/**
 * Android's own per-app language setting (LocaleManager, Android 13+). Android stores the choice
 * and recreates the screen in the new language; the same setting appears under
 * Settings > Apps > PocketMind > Language.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun LanguageChooser() {
    val context = LocalContext.current
    val manager = remember { context.getSystemService(LocaleManager::class.java) }
    val current = remember { manager?.applicationLocales?.get(0)?.language.orEmpty() }
    Text(
        text = stringResource(R.string.feature_settings_language),
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(start = PmSpacing.l, top = PmSpacing.m),
    )
    Column(Modifier.selectableGroup()) {
        APP_LANGUAGES.forEach { (tag, label) ->
            ListItem(
                headlineContent = { Text(stringResource(label)) },
                leadingContent = { RadioButton(selected = current == tag, onClick = null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(selected = current == tag, role = Role.RadioButton) {
                        manager?.applicationLocales = LocaleList.forLanguageTags(tag)
                    },
            )
        }
    }
}

private const val STEPS_PER_UNIT = 10f
