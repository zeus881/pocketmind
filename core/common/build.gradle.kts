plugins {
    alias(libs.plugins.pocketmind.android.library)
    alias(libs.plugins.pocketmind.hilt)
}

android {
    namespace = "com.manjit.pocketmind.core.common"
}

dependencies {
    api(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
}
