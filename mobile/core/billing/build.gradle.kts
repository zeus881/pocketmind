plugins {
    alias(libs.plugins.pocketmind.kmp.android.library)
}

// core:billing (V3) — PocketMind Pro. The entitlement rules and ProStatus (cached answer, acknowledge, refresh) are
// common code; only Google Play Billing (PlayBillingGateway) is Android code. iOS has no store gateway yet: an
// App Store purchase needs the owner's paid Apple Developer account (PROGRESS.md, manual steps).
kotlin {
    sourceSets {
        commonMain.dependencies {
            // SettingsDataSource: the cached "owns Pro" answer.
            implementation(projects.shared.data)
            implementation(libs.kotlinx.coroutines.core)
        }
        androidMain.dependencies {
            implementation(projects.core.common) // PmQualifiers.AppScope
            implementation(libs.play.billing.ktx)
            implementation(libs.kotlinx.coroutines.android)
            implementation(libs.koin.android)
            implementation(libs.kermit)
        }
        getByName("androidHostTest").dependencies {
            implementation(projects.core.testing)
            implementation(libs.robolectric)
            implementation(libs.androidx.test.core)
        }
    }
}
