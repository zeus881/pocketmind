package com.sanjay.pocketmind.core.llm.di

import com.sanjay.pocketmind.core.llm.LiteRtLmEmbedder
import com.sanjay.pocketmind.core.llm.LiteRtLmEngine
import com.sanjay.pocketmind.core.llm.LlmEngine
import com.sanjay.pocketmind.core.llm.TextEmbedder
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Tells Hilt: "whenever someone asks for [LlmEngine], give them the [LiteRtLmEngine]
 * singleton". `@Binds` is the cheapest way to map an interface to its implementation.
 * Tests replace this binding with `FakeLlmEngine`.
 */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class LlmModule {
    @Binds
    abstract fun bindsLlmEngine(engine: LiteRtLmEngine): LlmEngine

    @Binds
    abstract fun bindsTextEmbedder(embedder: LiteRtLmEmbedder): TextEmbedder
}
