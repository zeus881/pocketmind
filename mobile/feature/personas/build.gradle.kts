plugins {
    alias(libs.plugins.pocketmind.kmp.feature)
}

// feature:personas (V3) — personas and saved prompts, shared by Android and iOS.
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.data.chat)
            implementation(projects.core.billing) // new custom personas are a Pro feature
        }
    }
}

compose.resources {
    packageOfResClass = "com.sanjay.pocketmind.feature.personas.resources"
}
