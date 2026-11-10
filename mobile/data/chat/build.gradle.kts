plugins {
    alias(libs.plugins.pocketmind.android.library)
    alias(libs.plugins.pocketmind.hilt)
}

android {
    namespace = "com.sanjay.pocketmind.data.chat"
}

dependencies {
    api(projects.shared.core)
    api(projects.core.llm)
    implementation(projects.core.common)
    implementation(projects.core.database)
    implementation(projects.core.datastore) // settings, for the data export and "delete all my data"
    implementation(libs.androidx.core.ktx) // Canvas.withSave for the PDF export
    // Photo orientation; the framework ExifInterface has parsing bugs on old Android versions.
    implementation(libs.androidx.exifinterface)

    testImplementation(projects.core.testing)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.room.runtime)

    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}
