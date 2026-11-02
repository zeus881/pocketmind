plugins {
    alias(libs.plugins.pocketmind.android.feature)
}

android {
    namespace = "com.sanjay.pocketmind.feature.history"
}

dependencies {
    implementation(projects.data.chat)
    implementation(libs.compose.material3.adaptive) // two-pane History + chat on wide screens

    testImplementation(projects.core.testing)
}
