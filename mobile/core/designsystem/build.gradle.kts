plugins {
    alias(libs.plugins.pocketmind.android.library)
    alias(libs.plugins.pocketmind.android.compose)
}

android {
    namespace = "com.sanjay.pocketmind.core.designsystem"
}

dependencies {
    implementation(projects.shared.core)
    // MarkdownText: the one Markdown renderer every screen uses.
    api(libs.markdown.renderer.m3)
}
