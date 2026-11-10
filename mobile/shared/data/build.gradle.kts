plugins {
    alias(libs.plugins.pocketmind.kmp.android.library)
    alias(libs.plugins.pocketmind.kmp.room)
    alias(libs.plugins.kotlin.serialization)
}

// shared:data (V3) — the local database every platform uses: Room entities, DAOs, migrations and the
// database class, moved unchanged in structure from v2's core:database (same tables, schema version 8,
// same file name), so an existing v2 install keeps every chat. Platform parts stay outside:
// Android's SQLCipher encryption + Keystore key and the Hilt wiring live in core:database.
kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.shared.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.coroutines.core)
            api(libs.androidx.datastore.preferences.core)
        }
        iosTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
