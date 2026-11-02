plugins {
    alias(libs.plugins.pocketmind.android.feature)
}

android {
    namespace = "com.sanjay.pocketmind.feature.translate"
}

dependencies {
    implementation(projects.data.documents)

    testImplementation(projects.core.testing)
}
