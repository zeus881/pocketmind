plugins {
    alias(libs.plugins.pocketmind.android.library)
    alias(libs.plugins.pocketmind.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.sanjay.pocketmind.data.models"
}

dependencies {
    api(projects.core.model)
    api(projects.core.llm)
    implementation(projects.core.common)
    implementation(projects.core.billing) // bigger models are a Pro feature
    implementation(projects.core.datastore)

    implementation(libs.okhttp)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(projects.core.testing)
    testImplementation(libs.okhttp.mockwebserver)
    // Worker + repository tests: a real WorkManager in test mode on Robolectric's Android.
    testImplementation(libs.androidx.work.testing)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.datastore.preferences)
}
