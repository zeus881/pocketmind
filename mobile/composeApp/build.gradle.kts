import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
}

// composeApp (V3) — the shared Compose Multiplatform UI.
// iOS: built into the static framework "ComposeApp" that iosApp/ embeds (see iosApp/project.yml).
// Android: a KMP Android library; androidApp starts using it when the v2 screens move here (phase 2c).
kotlin {
    // AGP 9: the Android target of a KMP module is configured here (plugin com.android.kotlin.multiplatform.library).
    android {
        namespace = "com.sanjay.pocketmind.composeapp"
        compileSdk = libs.versions.compileSdk.get().toInt()
        minSdk = libs.versions.minSdk.get().toInt()
        // Compose Multiplatform strings (composeResources/) are packaged as Android resources.
        androidResources { enable = true }
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
        lint {
            warningsAsErrors = true
            abortOnError = true
            lintConfig = rootProject.file("lint.xml")
        }
    }

    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "ComposeApp"
            // Static: linked into the app binary, so the iOS app ships one executable and starts faster.
            isStatic = true
        }
    }

    compilerOptions {
        // Same rule as every other module: a compiler warning fails the build.
        allWarningsAsErrors.set(true)
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.jb.compose.runtime)
            implementation(libs.jb.compose.foundation)
            implementation(libs.jb.compose.ui)
            implementation(libs.jb.compose.material3)
            implementation(libs.jb.compose.components.resources)
        }
    }
}

compose.resources {
    // Generated accessor class for composeResources/ (Res.string.app_name, …).
    packageOfResClass = "com.sanjay.pocketmind.app.resources"
}
