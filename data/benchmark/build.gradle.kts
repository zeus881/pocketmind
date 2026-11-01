plugins {
    alias(libs.plugins.pocketmind.android.library)
    alias(libs.plugins.pocketmind.hilt)
}

android {
    namespace = "com.manjit.pocketmind.data.benchmark"
}

dependencies {
    api(projects.core.model)
    api(projects.core.llm)
    implementation(projects.core.common)
    implementation(projects.core.database)

    testImplementation(projects.core.testing)
}
