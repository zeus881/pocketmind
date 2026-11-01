plugins {
    alias(libs.plugins.pocketmind.android.library)
    alias(libs.plugins.pocketmind.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.manjit.pocketmind.data.models"
}

dependencies {
    api(projects.core.model)
    api(projects.core.llm)
    implementation(projects.core.common)
    implementation(projects.core.datastore)

    implementation(libs.okhttp)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(projects.core.testing)
    testImplementation(libs.okhttp.mockwebserver)
}
