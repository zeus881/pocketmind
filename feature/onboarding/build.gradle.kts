plugins {
    alias(libs.plugins.pocketmind.android.feature)
}

android {
    namespace = "com.sanjay.pocketmind.feature.onboarding"
}

dependencies {
    implementation(projects.data.models)
    implementation(projects.core.datastore)

    testImplementation(projects.core.testing)
    testImplementation(libs.androidx.datastore.preferences)
}
