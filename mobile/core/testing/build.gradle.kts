plugins {
    alias(libs.plugins.pocketmind.android.library)
    alias(libs.plugins.pocketmind.android.compose)
}

android {
    namespace = "com.sanjay.pocketmind.core.testing"
}

// Test helpers shared by many modules. Other modules add this ONLY as `testImplementation`,
// so FakeLlmEngine can never end up inside the release APK.
dependencies {
    api(projects.core.llm)
    api(projects.shared.core)
    api(projects.core.billing) // FakeProStatus
    api(projects.core.designsystem)
    api(libs.junit)
    api(libs.kotlinx.coroutines.test)
    api(libs.androidx.datastore.preferences)

    // Screenshot tests (captureScreenshots): Compose rendered by Robolectric, compared by Roborazzi.
    api(libs.robolectric)
    api(libs.roborazzi)
    api(libs.roborazzi.compose)
}
