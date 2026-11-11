plugins {
    alias(libs.plugins.pocketmind.kmp.feature)
}

// feature:translate (V3) — shared by Android and iOS. PDF text extraction is platform code (data:documents: PdfBox on
// Android); this screen sees the common PdfTextExtractor / DocumentRepository / Summarizer / Translator in shared:ai.
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.shared.ai)
        }
        getByName("androidHostTest").dependencies {
            implementation(libs.androidx.test.core)
            implementation(projects.core.database)
            implementation(libs.androidx.room.runtime)
        }
    }
}

compose.resources {
    packageOfResClass = "com.sanjay.pocketmind.feature.translate.resources"
    // Public: the app's end-to-end test finds screens by these strings.
    publicResClass = true
}
