plugins {
    alias(libs.plugins.pocketmind.android.library)
}

android {
    namespace = "com.sanjay.pocketmind.data.benchmark"
}

dependencies {
    api(projects.shared.core)
    api(projects.core.llm)
    implementation(projects.core.common)
    implementation(projects.core.database)

    testImplementation(projects.core.testing)
}
