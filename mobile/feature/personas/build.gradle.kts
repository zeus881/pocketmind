plugins {
    alias(libs.plugins.pocketmind.android.feature)
}

android {
    namespace = "com.sanjay.pocketmind.feature.personas"
}

dependencies {
    implementation(projects.data.chat)
    implementation(projects.core.billing) // new custom personas are a Pro feature

    testImplementation(projects.core.testing)
}
