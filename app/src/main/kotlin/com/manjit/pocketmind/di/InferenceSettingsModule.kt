package com.manjit.pocketmind.di

import com.manjit.pocketmind.core.llm.DefaultPrompts
import com.manjit.pocketmind.core.llm.InferenceSettings
import com.manjit.pocketmind.core.llm.InferenceSettingsSource
import com.manjit.pocketmind.core.llm.SamplingParams
import com.manjit.pocketmind.debug.DebugLlmViewModel
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject

/**
 * Phase 3: the chat uses the model pushed with adb and default settings.
 * Phase 4/5 replace this with the downloaded model and the user's saved settings.
 */
class AdbModelSettingsSource @Inject constructor() : InferenceSettingsSource {
    override suspend fun current() = InferenceSettings(
        modelPath = DebugLlmViewModel.DEBUG_MODEL_PATH,
        preferGpu = true,
        systemPrompt = DefaultPrompts.SYSTEM,
        sampling = SamplingParams(),
    )
}

@Module
@InstallIn(SingletonComponent::class)
abstract class InferenceSettingsModule {
    @Binds
    abstract fun bindsInferenceSettingsSource(source: AdbModelSettingsSource): InferenceSettingsSource
}
