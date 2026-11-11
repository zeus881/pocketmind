plugins {
    alias(libs.plugins.pocketmind.android.library)
}

android {
    namespace = "com.sanjay.pocketmind.core.common"
}

dependencies {
    // V3: AppResult, the Flow helpers and the Clock interface live in shared:core; every module that uses
    // core:common keeps seeing them.
    api(projects.shared.core)
    api(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
