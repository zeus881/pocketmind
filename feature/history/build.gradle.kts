plugins {
    alias(libs.plugins.pocketmind.android.feature)
}

android {
    namespace = "com.sanjay.pocketmind.feature.history"
}

dependencies {
    implementation(projects.data.chat)

    testImplementation(projects.core.testing)
}
