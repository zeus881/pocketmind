plugins {
    alias(libs.plugins.pocketmind.android.feature)
}

android {
    namespace = "com.sanjay.pocketmind.feature.chat"
}

dependencies {
    implementation(projects.data.chat)
    implementation(projects.core.llm)
    implementation(projects.core.speech)
    implementation(libs.androidx.activity.compose) // "Save as" launcher for chat export

    testImplementation(projects.core.testing)
    testImplementation(libs.androidx.test.core)
}
