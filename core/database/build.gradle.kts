plugins {
    alias(libs.plugins.pocketmind.android.library)
    alias(libs.plugins.pocketmind.android.room)
    alias(libs.plugins.pocketmind.hilt)
}

android {
    namespace = "com.manjit.pocketmind.core.database"
}

dependencies {
    api(projects.core.model)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
