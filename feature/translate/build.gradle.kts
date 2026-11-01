plugins {
    alias(libs.plugins.pocketmind.android.feature)
}

android {
    namespace = "com.manjit.pocketmind.feature.translate"
}

dependencies {
    implementation(projects.data.documents)

    testImplementation(projects.core.testing)
}
