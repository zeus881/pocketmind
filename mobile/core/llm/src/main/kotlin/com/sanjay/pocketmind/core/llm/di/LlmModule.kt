package com.sanjay.pocketmind.core.llm.di

import com.sanjay.pocketmind.core.common.dispatchers.PmQualifiers
import com.sanjay.pocketmind.core.llm.AndroidExitReasonSource
import com.sanjay.pocketmind.core.llm.ExitReasonSource
import com.sanjay.pocketmind.core.llm.LiteRtLmEmbedder
import com.sanjay.pocketmind.core.llm.LiteRtLmEngine
import com.sanjay.pocketmind.core.llm.LlmEngine
import com.sanjay.pocketmind.core.llm.LlmTask
import com.sanjay.pocketmind.core.llm.LoadGuard
import com.sanjay.pocketmind.core.llm.ModelFiles
import com.sanjay.pocketmind.core.llm.TextEmbedder
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * "Whenever someone asks for [LlmEngine], give them the [LiteRtLmEngine] singleton": a loaded model uses
 * gigabytes of RAM, so the whole app shares ONE engine. End-to-end tests replace this whole module with fakes.
 */
val llmModule = module {
    single<LlmEngine> { LiteRtLmEngine(androidContext(), get(PmQualifiers.Io), get()) }
    single<TextEmbedder> { LiteRtLmEmbedder(androidContext(), get(PmQualifiers.Io)) }
    factory<ExitReasonSource> { AndroidExitReasonSource(androidContext()) }
}

/**
 * The crash-loop guard, the model folder and [LlmTask] (shared:ai). Separate from [llmModule]: tests swap the
 * ENGINE and keep this real logic on top of it.
 */
val llmSupportModule = module {
    single { LoadGuard(androidContext(), get()) }
    single { ModelFiles(androidContext()) }
    factory { LlmTask(get(), get()) }
}
