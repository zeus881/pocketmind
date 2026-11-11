plugins {
    alias(libs.plugins.pocketmind.kmp.feature)
}

// feature:history (V3) — the chat list, search, pins and folders, shared by Android and iOS.
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.data.chat)
            // Two-pane History + chat on wide screens (window size classes), multiplatform.
            implementation(libs.jb.compose.material3.adaptive)
        }
    }
}

compose.resources {
    packageOfResClass = "com.sanjay.pocketmind.feature.history.resources"
    // Public: the app's end-to-end test finds screens by these strings.
    publicResClass = true
}
