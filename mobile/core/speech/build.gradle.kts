plugins {
    alias(libs.plugins.pocketmind.android.library)
}

android {
    namespace = "com.sanjay.pocketmind.core.speech"
}

dependencies {
    // V3: SpeechInput/SpeechOutput and SpeechText are shared with iOS (shared:ai); this module is the Android side.
    api(projects.shared.ai)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
