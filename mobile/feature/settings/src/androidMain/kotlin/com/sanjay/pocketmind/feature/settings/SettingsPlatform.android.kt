package com.sanjay.pocketmind.feature.settings

import android.app.LocaleManager
import android.os.Build
import android.os.LocaleList
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import com.sanjay.pocketmind.core.billing.PurchaseHost
import com.sanjay.pocketmind.core.designsystem.theme.PmSpacing
import com.sanjay.pocketmind.feature.settings.resources.Res
import com.sanjay.pocketmind.feature.settings.resources.feature_settings_language
import com.sanjay.pocketmind.feature.settings.resources.feature_settings_language_en
import com.sanjay.pocketmind.feature.settings.resources.feature_settings_language_hi
import com.sanjay.pocketmind.feature.settings.resources.feature_settings_language_pa
import com.sanjay.pocketmind.feature.settings.resources.feature_settings_language_system
import org.jetbrains.compose.resources.stringResource

@Composable
internal actual fun currentPurchaseHost(): PurchaseHost? = LocalActivity.current

@Composable
internal actual fun rememberAppLockAvailable(): Boolean {
    val context = LocalContext.current
    return remember {
        BiometricManager.from(context).canAuthenticate(BIOMETRIC_WEAK or DEVICE_CREDENTIAL) ==
            BiometricManager.BIOMETRIC_SUCCESS
    }
}

@Composable
internal actual fun rememberZipSaver(onPicked: (uri: String) -> Unit): ((fileName: String) -> Unit)? {
    // CreateDocument = the Storage Access Framework "Save as" screen; null = the user backed out.
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        uri?.let { onPicked(it.toString()) }
    }
    return remember(launcher) { { name -> launcher.launch(name) } }
}

/** The app's languages (same list as res/xml/locales_config.xml); "" = follow the phone. */
private val APP_LANGUAGES = listOf(
    "" to Res.string.feature_settings_language_system,
    "en" to Res.string.feature_settings_language_en,
    "hi" to Res.string.feature_settings_language_hi,
    "pa" to Res.string.feature_settings_language_pa,
)

/**
 * Android's own per-app language setting (LocaleManager, Android 13+). Android stores the choice
 * and recreates the screen in the new language; the same setting appears under
 * Settings > Apps > PocketMind > Language.
 */
@Composable
internal actual fun LanguageChooser() {
    // Per-app language exists from Android 13; older phones follow the phone's language (nothing to show).
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val context = LocalContext.current
    val manager = remember { context.getSystemService(LocaleManager::class.java) }
    val current = remember { manager?.applicationLocales?.get(0)?.language.orEmpty() }
    Text(
        text = stringResource(Res.string.feature_settings_language),
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
