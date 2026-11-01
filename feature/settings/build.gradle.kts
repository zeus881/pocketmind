plugins {
    alias(libs.plugins.pocketmind.android.feature)
}

android {
    namespace = "com.manjit.pocketmind.feature.settings"
}

dependencies {
    implementation(projects.core.datastore)
    implementation(projects.core.llm)
    implementation(projects.data.models)

    testImplementation(projects.core.testing)
}
