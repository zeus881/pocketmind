plugins {
    alias(libs.plugins.pocketmind.android.feature)
}

android {
    namespace = "com.sanjay.pocketmind.feature.documents"
}

dependencies {
    implementation(projects.data.documents)
    implementation(libs.androidx.activity.compose) // system file picker launcher

    testImplementation(projects.core.testing)
    testImplementation(libs.androidx.test.core)
    testImplementation(projects.core.database)
    testImplementation(libs.androidx.room.runtime)
}
