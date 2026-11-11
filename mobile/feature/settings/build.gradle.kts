plugins {
    alias(libs.plugins.pocketmind.kmp.feature)
}

// feature:settings (V3) — shared by Android and iOS. The few platform pieces (store purchase host, app-lock
// availability, the "Save as" screen for the data export, the per-app language) are expect/actual functions in
// SettingsPlatform.kt.
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.shared.ai) // LlmEngine, DefaultPrompts, ModelRepository
            implementation(projects.data.chat) // export / delete all my data
            implementation(projects.core.billing)
        }
        androidMain.dependencies {
            implementation(libs.androidx.biometric) // checks whether app lock can be offered
            implementation(libs.androidx.activity.compose) // LocalActivity, the "Save as" launcher
        }
    }
}

compose.resources {
    packageOfResClass = "com.sanjay.pocketmind.feature.settings.resources"
    // Public: the app's end-to-end test finds screens by these strings.
    publicResClass = true
}
