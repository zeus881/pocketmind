package com.sanjay.pocketmind.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json

/**
 * The shared models must behave the same on Android (JVM) and iOS (Kotlin/Native): these tests run on
 * both targets (jvmTest here, iosSimulatorArm64Test on the macOS CI runner).
 */
class ModelsTest {
    // Same settings as the app's catalog reader (data:models ModelCatalog).
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun catalogEntryParsesWithItsOptionalFieldsDefaulted() {
        // The real Gemma entry from data/models/src/main/assets/models.json, minus its optional flags.
        val entry = """
            {
              "id": "gemma-4-e2b",
              "displayName": "Gemma 4 E2B",
              "description": "Google's best small model. Strongest answers. Needs a phone with 8 GB RAM.",
              "url": "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/1caa527c1e88b55f046fcd8d2b4265606680663d/gemma-4-E2B-it.litertlm",
              "sizeBytes": 2588147712,
              "sha256": "181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c",
              "minRamGb": 8,
              "license": "Apache-2.0",
              "addedInAFutureVersion": true
            }
        """.trimIndent()

        val model = json.decodeFromString<LlmModelInfo>(entry)

        // 2.6 GB does not fit in an Int: the size must stay a Long on every platform.
        assertEquals(2_588_147_712L, model.sizeBytes)
        assertEquals(ModelKind.CHAT, model.kind)
        assertFalse(model.supportsImages)
        assertFalse(model.supportsTools)
        assertFalse(model.requiresPro)
    }

    @Test
    fun embeddingKindAndCapabilitiesRoundTrip() {
        val model = LlmModelInfo(
            id = "embeddinggemma",
            displayName = "EmbeddingGemma",
            description = "Search model for documents.",
            kind = ModelKind.EMBEDDING,
            url = "https://example.invalid/model.litertlm",
            sizeBytes = 164_626_432L,
            sha256 = "2d079ee2f6f066b1f368e8d7c819f55214eaef1d0513b312321901f30ab286fb",
            minRamGb = 4,
            license = "Gemma",
            supportsTools = true,
        )

        val text = json.encodeToString(LlmModelInfo.serializer(), model)

        assertTrue("\"kind\":\"EMBEDDING\"" in text)
        assertEquals(model, json.decodeFromString<LlmModelInfo>(text))
    }

    @Test
    fun benchmarkAveragesAreZeroWithoutPrompts() {
        val result = benchmark(prompts = emptyList())

        assertEquals(0L, result.averageTimeToFirstTokenMs)
        assertEquals(0.0, result.averageDecodeTokensPerSecond)
    }

    @Test
    fun benchmarkAveragesUseEveryPrompt() {
        val result = benchmark(
            prompts = listOf(
                prompt("short", ttftMs = 300, tokensPerSecond = 24.0),
                prompt("long", ttftMs = 500, tokensPerSecond = 28.6),
            ),
        )

        assertEquals(400L, result.averageTimeToFirstTokenMs)
        assertEquals(26.3, result.averageDecodeTokensPerSecond, absoluteTolerance = 1e-9)
    }

    private fun benchmark(prompts: List<BenchmarkPromptResult>) = BenchmarkResult(
        id = "run-1",
        createdAt = 0L,
        modelId = "gemma-4-e2b",
        modelName = "Gemma 4 E2B",
        backend = "GPU",
        loadMs = 5_454L,
        prompts = prompts,
        peakMemoryMb = 960,
        device = "test device",
        androidVersion = "16",
    )

    private fun prompt(name: String, ttftMs: Long, tokensPerSecond: Double) = BenchmarkPromptResult(
        name = name,
        timeToFirstTokenMs = ttftMs,
        decodeTokensPerSecond = tokensPerSecond,
        generatedTokens = 128,
        totalMs = 5_000L,
        fromEngine = true,
    )
}
