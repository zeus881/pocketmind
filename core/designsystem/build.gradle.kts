plugins {
    alias(libs.plugins.pocketmind.android.library)
    alias(libs.plugins.pocketmind.android.compose)
}

android {
    namespace = "com.sanjay.pocketmind.core.designsystem"
}

dependencies {
    implementation(projects.core.model)
}
