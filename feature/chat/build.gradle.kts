plugins {
    alias(libs.plugins.pocketmind.android.feature)
}

android {
    namespace = "com.manjit.pocketmind.feature.chat"
}

dependencies {
    implementation(projects.data.chat)
    implementation(projects.core.llm)
    implementation(libs.markdown.renderer.m3)

    testImplementation(projects.core.testing)
    testImplementation(libs.androidx.test.core)
}
