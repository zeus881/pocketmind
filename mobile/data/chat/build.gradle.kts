plugins {
    alias(libs.plugins.pocketmind.kmp.android.library)
}

// data:chat (V3) — chats, folders, personas and the model's tools. The repositories are common code (shared
// with iOS); photos, Markdown/PDF export and "your data" (ZIP export, delete everything) are Android code in
// androidMain, behind the common ChatImageStore / ChatExporter / UserDataControl interfaces.
kotlin {
    android {
        // The PDF export test draws real pages, so it runs on a phone or emulator (connectedAndroidDeviceTest).
        withDeviceTest {
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }
    }
    sourceSets {
        commonMain.dependencies {
            api(projects.shared.core)
            // LlmEngine, InferenceSettings, HistoryTrimmer; it also brings shared:data (DAOs) and shared:tools.
            api(projects.shared.ai)
            implementation(libs.koin.core)
            implementation(libs.kermit)
        }
        androidMain.dependencies {
            implementation(projects.core.common) // PmQualifiers (the IO dispatcher)
            implementation(projects.core.database) // UserDataStore: one read of everything for the export
            implementation(projects.core.datastore) // settings, for the data export and "delete all my data"
            implementation(libs.koin.android)
            implementation(libs.androidx.core.ktx) // Canvas.withSave for the PDF export, String.toUri
            // Photo orientation; the framework ExifInterface has parsing bugs on old Android versions.
            implementation(libs.androidx.exifinterface)
        }
        getByName("androidHostTest").dependencies {
            implementation(projects.core.testing)
            implementation(libs.robolectric)
            implementation(libs.androidx.test.core)
            implementation(libs.androidx.room.runtime)
        }
        getByName("androidDeviceTest").dependencies {
            implementation(libs.androidx.test.core)
            implementation(libs.androidx.test.ext.junit)
            implementation(libs.androidx.test.runner)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
