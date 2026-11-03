plugins {
    alias(libs.plugins.pocketmind.android.library)
    alias(libs.plugins.pocketmind.hilt)
}

android {
    namespace = "com.sanjay.pocketmind.core.billing"
}

// Only this module talks to Google Play Billing; the rest of the app sees ProStatus.
dependencies {
    implementation(projects.core.common)
    implementation(projects.core.datastore)
    implementation(libs.play.billing.ktx)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(projects.core.testing)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
