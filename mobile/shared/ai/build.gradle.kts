plugins {
    alias(libs.plugins.pocketmind.kmp.android.library)
}

// shared:ai (V3) — the on-device AI logic both phones share: the LlmEngine contract and its states,
// sampling and context-window maths, history trimming, the summarizer and translator, PDF chunking and the
// local RAG (vector search + grounded prompt with page citations). The engines themselves are platform code:
// LiteRT-LM Kotlin in core:llm (Android), the LiteRT-LM Swift bridge on iOS (phase 2e).
kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.shared.core)
            api(projects.shared.data)
            api(projects.shared.tools)
            api(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
