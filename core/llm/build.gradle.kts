plugins {
    alias(libs.plugins.pocketmind.android.library)
    alias(libs.plugins.pocketmind.hilt)
}

android {
    namespace = "com.sanjay.pocketmind.core.llm"
}

dependencies {
    api(projects.core.model)
    api(projects.core.tools)
    implementation(projects.core.common)

    // "implementation", NOT "api": LiteRT-LM classes are invisible to every module that
    // depends on core:llm. That is how the build ENFORCES "only core:llm imports LiteRT-LM".
    implementation(libs.litertlm.android)
    implementation(libs.kotlinx.coroutines.android)

    // On-device engine test (LiteRtLmEngineDeviceTest). Needs a phone with a model file; see the test's KDoc.
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}
