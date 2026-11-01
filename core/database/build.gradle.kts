plugins {
    alias(libs.plugins.pocketmind.android.library)
    alias(libs.plugins.pocketmind.android.room)
    alias(libs.plugins.pocketmind.hilt)
}

android {
    namespace = "com.manjit.pocketmind.core.database"
    // Room's JSON schemas are test resources, so migration tests can open old versions.
    sourceSets.getByName("test").assets.directories.add("$projectDir/schemas")
}

dependencies {
    api(projects.core.model)

    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
