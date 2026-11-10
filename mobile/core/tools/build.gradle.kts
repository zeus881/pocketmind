plugins {
    alias(libs.plugins.pocketmind.jvm.library)
}

// Pure Kotlin: the tools are plain functions over JSON, tested on the JVM without a model or a phone.
dependencies {
    api(libs.kotlinx.serialization.json)
}
