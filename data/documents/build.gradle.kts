plugins {
    alias(libs.plugins.pocketmind.android.library)
    alias(libs.plugins.pocketmind.hilt)
}

android {
    namespace = "com.sanjay.pocketmind.data.documents"
}

dependencies {
    api(projects.core.model)
    api(projects.core.llm)
    implementation(projects.core.common)
    implementation(projects.core.billing) // the free version's PDF page limit
    implementation(projects.core.database)

    // PDF text extraction (an Android port of Apache PDFBox). Runs fully offline.
    implementation(libs.pdfbox.android)

    testImplementation(projects.core.testing)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.room.runtime)
}
