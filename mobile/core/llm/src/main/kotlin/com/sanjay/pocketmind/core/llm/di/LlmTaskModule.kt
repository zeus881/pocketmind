package com.sanjay.pocketmind.core.llm.di

import com.sanjay.pocketmind.core.llm.InferenceSettingsSource
import com.sanjay.pocketmind.core.llm.LlmEngine
import com.sanjay.pocketmind.core.llm.LlmTask
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Provides [LlmTask] (V3: it lives in shared:ai without DI annotations, so iOS can create it too).
 * Separate from [LlmModule] on purpose: end-to-end tests replace the ENGINE (a fake) and keep this real
 * task logic on top of it.
 */
@Module
@InstallIn(SingletonComponent::class)
object LlmTaskModule {
    @Provides
    fun providesLlmTask(engine: LlmEngine, settings: InferenceSettingsSource): LlmTask = LlmTask(engine, settings)
}
