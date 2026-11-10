plugins {
    alias(libs.plugins.pocketmind.kmp.library)
}

// shared:tools (V3) — the on-device tools (calculator, date & time, unit converter, notes), shared with iOS.
// Pure Kotlin: plain functions over JSON, tested on the JVM and on the iOS simulator without a model or a phone.
kotlin {
    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.serialization.json)
            // Exact decimals (v2: java.math.BigDecimal) and dates (v2: java.time) on every platform.
            api(libs.bignum)
            api(libs.kotlinx.datetime)
        }
    }
}
