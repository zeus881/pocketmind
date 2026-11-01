package com.manjit.pocketmind.core.llm.di

import com.manjit.pocketmind.core.llm.LiteRtLmEngine
import com.manjit.pocketmind.core.llm.LlmEngine
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
}
