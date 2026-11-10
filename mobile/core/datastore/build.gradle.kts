plugins {
    alias(libs.plugins.pocketmind.android.library)
    alias(libs.plugins.pocketmind.hilt)
}

android {
    namespace = "com.sanjay.pocketmind.core.datastore"
}

// core:datastore (V3) — the ANDROID side of the settings: where the DataStore file lives ("user_settings",
// unchanged since v1) and the Hilt bindings. SettingsDataSource itself is shared with iOS in shared:data.
dependencies {
    api(projects.shared.data)
    implementation(libs.androidx.datastore.preferences)

    testImplementation(projects.core.testing)
}
