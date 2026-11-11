plugins {
    alias(libs.plugins.pocketmind.kmp.feature)
}

// feature:chat (V3) — the chat screen, shared by Android and iOS. Platform pieces (the "Save as" screen for export,
// the microphone permission, the photo picker, thumbnail decoding) are expect/actual functions in ChatPlatform.kt.
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.data.chat)
            // LlmEngine states, SpeechInput/SpeechOutput (the Android speech engines live in core:speech).
            implementation(projects.shared.ai)
        }
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose) // "Save as", permission and photo picker launchers
        }
        getByName("androidHostTest").dependencies {
            implementation(libs.androidx.test.core)
        }
    }
}

compose.resources {
    packageOfResClass = "com.sanjay.pocketmind.feature.chat.resources"
    // Public: the app's end-to-end test finds screens by these strings.
    publicResClass = true
}
