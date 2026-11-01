plugins {
    alias(libs.plugins.pocketmind.android.application)
    alias(libs.plugins.pocketmind.android.compose)
    alias(libs.plugins.pocketmind.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.manjit.pocketmind"

    defaultConfig {
        applicationId = "com.manjit.pocketmind"
        versionCode = 1
        versionName = "1.0.0"
    }

    buildFeatures {
        // BuildConfig.DEBUG decides whether Timber logs anything.
        buildConfig = true
    }

    buildTypes {
        release {
            // R8 shrinks, optimizes and obfuscates the code; shrinkResources removes unused
            // images/strings. Together they make the APK much smaller.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}

dependencies {
    implementation(projects.core.model)
    implementation(projects.core.common)
    implementation(projects.core.designsystem)
    implementation(projects.core.llm)
    implementation(projects.data.chat)

    implementation(projects.feature.chat)
    implementation(projects.feature.history)
    implementation(projects.feature.models)
    implementation(projects.feature.settings)
    implementation(projects.feature.benchmark)
    implementation(projects.feature.onboarding)
    implementation(projects.feature.documents)
    implementation(projects.feature.translate)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)

    testImplementation(projects.core.testing)
    testImplementation(libs.turbine)
}
