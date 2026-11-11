package com.sanjay.pocketmind.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.uikit.LocalUIViewController
import com.sanjay.pocketmind.core.billing.PurchaseHost
import com.sanjay.pocketmind.feature.settings.resources.Res
import com.sanjay.pocketmind.feature.settings.resources.feature_settings_language
import org.jetbrains.compose.resources.stringResource
import platform.Foundation.NSURL
import platform.LocalAuthentication.LAContext
import platform.LocalAuthentication.LAPolicyDeviceOwnerAuthentication
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString

@Composable
internal actual fun currentPurchaseHost(): PurchaseHost? = LocalUIViewController.current

/** Face ID / Touch ID OR the device passcode ("device owner authentication"), like Android's weak + credential. */
@Composable
internal actual fun rememberAppLockAvailable(): Boolean = remember {
    LAContext().canEvaluatePolicy(LAPolicyDeviceOwnerAuthentication, error = null)
}

/** No data export on iOS yet (its UserDataControl comes in V3 phase 2d), so the row is not shown. */
@Composable
internal actual fun rememberZipSaver(onPicked: (uri: String) -> Unit): ((fileName: String) -> Unit)? = null

/** iOS keeps each app's language in the Settings app (Settings > PocketMind > Language): open that page. */
@Composable
internal actual fun LanguageChooser() {
    ListItem(
        headlineContent = { Text(stringResource(Res.string.feature_settings_language)) },
        modifier = Modifier
            .fillMaxWidth()
            .clickable { openAppSettings() },
    )
}

private fun openAppSettings() {
    val url = NSURL(string = UIApplicationOpenSettingsURLString)
    UIApplication.sharedApplication.openURL(url, options = emptyMap<Any?, Any>(), completionHandler = null)
}
