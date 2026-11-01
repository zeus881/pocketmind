plugins {
    alias(libs.plugins.pocketmind.android.feature)
}

android {
    namespace = "com.manjit.pocketmind.feature.benchmark"
}

dependencies {
    implementation(projects.data.benchmark)

    testImplementation(projects.core.testing)
}
