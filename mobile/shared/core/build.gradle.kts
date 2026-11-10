plugins {
    alias(libs.plugins.pocketmind.kmp.library)
    alias(libs.plugins.kotlin.serialization)
}

// shared:core (V3) — the data models every layer uses (Chat, Message, LlmModelInfo, UserSettings, …),
// moved unchanged from v2's core:model into commonMain so Android and iOS share them.
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.serialization.json)
        }
    }
}
