plugins {
    alias(libs.plugins.pocketmind.android.feature)
}

android {
    namespace = "com.sanjay.pocketmind.feature.chat"
}

dependencies {
    implementation(projects.data.chat)
    implementation(projects.core.llm)

    testImplementation(projects.core.testing)
    testImplementation(libs.androidx.test.core)
}
