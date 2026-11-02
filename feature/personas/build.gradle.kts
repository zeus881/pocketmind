plugins {
    alias(libs.plugins.pocketmind.android.feature)
}

android {
    namespace = "com.sanjay.pocketmind.feature.personas"
}

dependencies {
    implementation(projects.data.chat)

    testImplementation(projects.core.testing)
}
