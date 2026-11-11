plugins {
    alias(libs.plugins.pocketmind.android.library)
}

android {
    namespace = "com.sanjay.pocketmind.core.database"
}

// core:database (V3) — the ANDROID side of the local database: SQLCipher encryption with a key wrapped
// by Android Keystore, the one-time plain → encrypted migration, and the Hilt bindings. The schema itself
// (entities, DAOs, migrations, PocketMindDatabase) is shared with iOS in shared:data.
dependencies {
    api(projects.shared.data)
    // Room's Android builder (openHelperFactory) and the framework SQLite open helper for the plain fallback.
    implementation(libs.androidx.room.runtime)
    implementation(libs.kotlinx.serialization.json)
    // Encrypts the database file (chats, notes) with a key kept by Android Keystore.
    implementation(libs.sqlcipher.android)

    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.kotlinx.coroutines.test)

    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
