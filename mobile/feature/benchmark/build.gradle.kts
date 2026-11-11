plugins {
    alias(libs.plugins.pocketmind.kmp.feature)
}

// feature:benchmark (V3) — the first screen shared by Android and iOS: the screen, its ViewModel, strings
// (composeResources, en/hi/pa) and navigation route are common code. Sharing the result uses the platform
// share sheet through shared:designsystem's rememberTextSharer().
kotlin {
    sourceSets {
        commonMain.dependencies {
            // BenchmarkRunner, BenchmarkRepository, BenchmarkReport (moved to shared:ai in phase 2c step 1).
            implementation(projects.shared.ai)
        }
    }
}

compose.resources {
    packageOfResClass = "com.sanjay.pocketmind.feature.benchmark.resources"
}
