package com.sanjay.pocketmind.feature.settings

import androidx.compose.runtime.Composable
import com.sanjay.pocketmind.core.billing.PurchaseHost

/** The screen a store purchase sheet opens over (Android: the Activity; iOS: the hosting view controller). */
@Composable
internal expect fun currentPurchaseHost(): PurchaseHost?

/**
 * Whether app lock can be turned on: only if the phone has a fingerprint/face OR a screen lock, otherwise nobody
 * could unlock the app again.
 */
@Composable
internal expect fun rememberAppLockAvailable(): Boolean

/**
 * The system "Save as" screen for the data export ZIP; [onPicked] gets the chosen location as text. Null where the
 * export isn't available yet (iOS has no UserDataControl until V3 phase 2d), so the screen hides the row.
 */
@Composable
internal expect fun rememberZipSaver(onPicked: (uri: String) -> Unit): ((fileName: String) -> Unit)?

/**
 * The app's own language: Android 13+ lists the languages here (LocaleManager); iOS keeps per-app languages in its
 * Settings app, so the row opens that page; older Android follows the phone (nothing shown).
 */
@Composable
internal expect fun LanguageChooser()
