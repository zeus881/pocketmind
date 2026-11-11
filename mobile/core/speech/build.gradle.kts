plugins {
    alias(libs.plugins.pocketmind.android.library)
}

android {
    namespace = "com.sanjay.pocketmind.core.speech"
}

dependencies {
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
