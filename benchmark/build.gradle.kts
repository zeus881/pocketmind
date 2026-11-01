import com.android.build.api.dsl.TestExtension

/*
 * Macrobenchmark + Baseline Profile generator.
 *
 * A "com.android.test" module is a separate APK that drives the REAL app (installed as a
 * release-like build) from the outside with UiAutomator, and measures it: startup time,
 * frame times. It must run on a physical phone (see PROGRESS.md → manual steps).
 */
plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.androidx.baselineprofile)
}

extensions.configure<TestExtension> {
    namespace = "com.manjit.pocketmind.benchmark"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        // Baseline Profile generation needs Android 9 (API 28) or newer on the test phone.
        minSdk = 28
        targetSdk = libs.versions.targetSdk.get().toInt()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // The app under test. The test APK runs in its own process ("self-instrumenting") so it
    // can kill and restart the app between iterations.
    targetProjectPath = ":app"
    experimentalProperties["android.experimental.self-instrumenting"] = true

    lint {
        lintConfig = rootProject.file("lint.xml")
        warningsAsErrors = true
    }
}

baselineProfile {
    // Use a phone connected over USB (no Gradle-managed emulator).
    useConnectedDevices = true
}

dependencies {
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.test.runner)
    implementation(libs.androidx.uiautomator)
    implementation(libs.androidx.benchmark.macro)
}
