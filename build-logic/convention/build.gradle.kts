import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    `kotlin-dsl`
}

group = "com.sanjay.pocketmind.buildlogic"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

dependencies {
    // compileOnly: we only need these APIs to WRITE the plugins. The real plugin versions are
    // put on the classpath by the root build.gradle.kts ("apply false" block).
    compileOnly(libs.android.gradle.plugin)
    compileOnly(libs.kotlin.gradle.plugin)
    compileOnly(libs.ksp.gradle.plugin)
    compileOnly(libs.compose.gradle.plugin)
    compileOnly(libs.room.gradle.plugin)
    compileOnly(libs.kover.gradle.plugin)
    compileOnly(libs.roborazzi.gradle.plugin)

    testImplementation(libs.junit)
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "pocketmind.android.application"
            implementationClass = "AndroidApplicationConventionPlugin"
        }
        register("androidLibrary") {
            id = "pocketmind.android.library"
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        register("androidFeature") {
            id = "pocketmind.android.feature"
            implementationClass = "AndroidFeatureConventionPlugin"
        }
        register("androidCompose") {
            id = "pocketmind.android.compose"
            implementationClass = "AndroidComposeConventionPlugin"
        }
        register("androidRoom") {
            id = "pocketmind.android.room"
            implementationClass = "AndroidRoomConventionPlugin"
        }
        register("hilt") {
            id = "pocketmind.hilt"
            implementationClass = "HiltConventionPlugin"
        }
        register("jvmLibrary") {
            id = "pocketmind.jvm.library"
            implementationClass = "JvmLibraryConventionPlugin"
        }
    }
}
