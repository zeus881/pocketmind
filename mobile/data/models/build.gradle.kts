plugins {
    alias(libs.plugins.pocketmind.android.library)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.sanjay.pocketmind.data.models"
}

dependencies {
    api(projects.shared.core)
    api(projects.core.llm)
    implementation(projects.core.common)
    implementation(projects.core.billing) // bigger models are a Pro feature
    implementation(projects.core.datastore)

    implementation(libs.okhttp)
    implementation(libs.androidx.work.runtime)
    // ModelDownloadWorker gets its dependencies through Koin's WorkManager factory.
    implementation(libs.koin.androidx.workmanager)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(projects.core.testing)
    testImplementation(libs.okhttp.mockwebserver)
    // Worker + repository tests: a real WorkManager in test mode on Robolectric's Android.
    testImplementation(libs.androidx.work.testing)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.datastore.preferences)
}
