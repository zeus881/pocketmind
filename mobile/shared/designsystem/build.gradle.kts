plugins {
    alias(libs.plugins.pocketmind.kmp.android.library)
    alias(libs.plugins.pocketmind.kmp.compose)
}

// shared:designsystem (V3) — PocketMind's Material 3 theme, tokens, illustrations, state views and the Markdown
// renderer, shared by the Android and iOS UI. Moved unchanged from v2's core:designsystem; only the wallpaper
// colours (Android 12+) are platform code. Phase 3 replaces the look with the V3 design system.
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.shared.core)
            // MarkdownText: the one Markdown renderer every screen uses (multiplatform).
            api(libs.markdown.renderer.m3)
        }
    }
}

compose.resources {
    packageOfResClass = "com.sanjay.pocketmind.core.designsystem.resources"
}
