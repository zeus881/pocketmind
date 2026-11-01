plugins {
    alias(libs.plugins.pocketmind.android.feature)
}

android {
    namespace = "com.manjit.pocketmind.feature.models"
}

dependencies {
    implementation(projects.data.models)
    implementation(projects.core.datastore)
    implementation(libs.androidx.activity.compose) // permission request launcher

    testImplementation(projects.core.testing)
    testImplementation(libs.androidx.datastore.preferences)
}
