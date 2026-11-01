plugins {
    alias(libs.plugins.pocketmind.android.library)
}

android {
    namespace = "com.manjit.pocketmind.core.testing"
}

// Test helpers shared by many modules. Other modules add this ONLY as `testImplementation`,
// so FakeLlmEngine can never end up inside the release APK.
dependencies {
    api(projects.core.llm)
    api(projects.core.model)
    api(libs.junit)
    api(libs.kotlinx.coroutines.test)
}
