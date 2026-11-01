plugins {
    alias(libs.plugins.pocketmind.android.feature)
}

android {
    namespace = "com.manjit.pocketmind.feature.settings"
}

dependencies {
    implementation(projects.core.datastore)
    implementation(projects.core.llm)
    implementation(projects.data.models)
    implementation(libs.androidx.biometric) // checks whether app lock can be offered

    testImplementation(projects.core.testing)
}
