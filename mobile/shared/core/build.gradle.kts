plugins {
    alias(libs.plugins.pocketmind.kmp.library)
    alias(libs.plugins.kotlin.serialization)
}

// shared:core (V3) — the data models every layer uses (Chat, Message, LlmModelInfo, UserSettings, …),
// moved unchanged from v2's core:model, plus AppResult/ErrorKind and the Flow helpers from core:common.
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.serialization.json)
            // AppResult and the Flow helpers (throttleLatest for streaming answers), moved from core:common.
            api(libs.kotlinx.coroutines.core)
            // PmQualifiers: the Koin names of the dispatchers and the app scope (moved from core:common).
            api(libs.koin.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
