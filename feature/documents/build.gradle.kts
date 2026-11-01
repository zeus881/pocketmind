plugins {
    alias(libs.plugins.pocketmind.android.feature)
}

android {
    namespace = "com.manjit.pocketmind.feature.documents"
}

dependencies {
    implementation(projects.data.documents)
    implementation(libs.androidx.activity.compose) // system file picker launcher
    implementation(libs.markdown.renderer.m3)

    testImplementation(projects.core.testing)
    testImplementation(libs.androidx.test.core)
    testImplementation(projects.core.database)
    testImplementation(libs.androidx.room.runtime)
}
