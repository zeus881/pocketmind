plugins {
    alias(libs.plugins.pocketmind.kmp.feature)
}

// feature:models (V3) — shared by Android and iOS. Model downloads themselves are platform code (data:models on
// Android); this screen only sees the common ModelRepository in shared:ai.
kotlin {
    sourceSets {
        commonMain.dependencies {
            // ModelRepository / ModelState / ModelStatus; it also brings shared:data (SettingsDataSource).
            implementation(projects.shared.ai)
        }
        getByName("androidHostTest").dependencies {
            implementation(libs.androidx.datastore.preferences)
        }
    }
}

compose.resources {
    packageOfResClass = "com.sanjay.pocketmind.feature.models.resources"
    // Public: the app's end-to-end test finds screens by these strings.
    publicResClass = true
}
