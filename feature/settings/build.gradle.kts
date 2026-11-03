plugins {
    alias(libs.plugins.pocketmind.android.feature)
}

android {
    namespace = "com.sanjay.pocketmind.feature.settings"
}

dependencies {
    implementation(projects.core.datastore)
    implementation(projects.core.llm)
    implementation(projects.data.models)
    implementation(projects.data.chat) // export / delete all my data
    implementation(projects.core.common)
    implementation(libs.androidx.biometric) // checks whether app lock can be offered

    testImplementation(projects.core.testing)
}
