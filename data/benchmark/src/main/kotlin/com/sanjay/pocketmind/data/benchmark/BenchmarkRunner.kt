package com.sanjay.pocketmind.data.benchmark

import com.sanjay.pocketmind.core.common.dispatchers.Dispatcher
import com.sanjay.pocketmind.core.common.dispatchers.PmDispatchers
import com.sanjay.pocketmind.core.common.result.ErrorKind
import com.sanjay.pocketmind.core.common.time.Clock
import com.sanjay.pocketmind.core.llm.EngineState
import com.sanjay.pocketmind.core.llm.InferenceSettingsSource
import com.sanjay.pocketmind.core.llm.LlmEngine
import com.sanjay.pocketmind.core.llm.SamplingParams
import com.sanjay.pocketmind.core.model.BenchmarkPromptResult
import com.sanjay.pocketmind.core.model.BenchmarkResult
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** What the benchmark screen shows while a run is in progress. */
sealed interface BenchmarkProgress {
    data object LoadingModel : BenchmarkProgress

    data class RunningPrompt(val index: Int, val total: Int, val name: String) : BenchmarkProgress

    data class Finished(val result: BenchmarkResult) : BenchmarkProgress

    data class Failed(val kind: ErrorKind) : BenchmarkProgress
}

/**
 * Runs the fixed benchmark and measures, on THIS phone:
 * 1. Model load time — the model is unloaded first, so this is a cold load.
 * 2. For each of 3 prompts: time to first token, decode tokens/second, tokens generated.
 * 3. Peak memory (PSS) while loading and generating, sampled every 250 ms in parallel.
 *
 * Sampling is greedy (top-K = 1) with a fixed seed, so every run generates the same text and
 * runs are comparable.
 *
 * Built with `channelFlow` (not `flow`) because a SECOND coroutine (the memory sampler) runs
 * alongside the measurements, and a plain `flow {}` may only emit from its own coroutine.
 */
class BenchmarkRunner @Inject constructor(
    private val engine: LlmEngine,
    private val settingsSource: InferenceSettingsSource,
    private val repository: BenchmarkRepository,
    private val metrics: DeviceMetrics,
    private val clock: Clock,
    @Dispatcher(PmDispatchers.IO) private val io: CoroutineDispatcher,
) {
    fun run(): Flow<BenchmarkProgress> = channelFlow {
        val settings = settingsSource.current()
        if (settings == null) {
            send(BenchmarkProgress.Failed(ErrorKind.NO_MODEL))
            return@channelFlow
        }

        val peakMb = AtomicInteger(0)
        val sampler = launch(io) {
            while (isActive) {
                peakMb.accumulateAndGet(metrics.processMemoryMb(), ::maxOf)
                delay(SAMPLE_INTERVAL_MS)
            }
        }

        send(BenchmarkProgress.LoadingModel)
        engine.unload() // force a cold load so "load time" is honest
        engine.load(settings.modelPath, settings.preferGpu, settings.expectedSizeBytes)
        val ready = engine.state.value as? EngineState.Ready
        if (ready == null) {
            sampler.cancel()
            send(BenchmarkProgress.Failed(ErrorKind.MODEL_LOAD_FAILED))
            return@channelFlow
        }

        val results = BenchmarkPrompts.all.mapIndexed { index, prompt ->
            send(BenchmarkProgress.RunningPrompt(index + 1, BenchmarkPrompts.all.size, prompt.name))
            // A fresh session per prompt: no previous answer influences the next measurement.
            val session = engine.newSession(BenchmarkPrompts.SYSTEM_PROMPT, emptyList(), GREEDY)
            val start = metrics.elapsedMs()
            var pieces = 0
            engine.generate(prompt.text, session).collect { pieces++ }
            val totalMs = metrics.elapsedMs() - start
            val stats = engine.lastStats
            BenchmarkPromptResult(
                name = prompt.name,
                timeToFirstTokenMs = stats?.timeToFirstTokenMs ?: totalMs,
                decodeTokensPerSecond = stats?.decodeTokensPerSecond ?: 0.0,
                generatedTokens = stats?.generatedTokens ?: pieces,
                totalMs = totalMs,
                fromEngine = stats?.fromEngine ?: false,
            )
        }
        sampler.cancel()
        peakMb.accumulateAndGet(metrics.processMemoryMb(), ::maxOf)

        val result = BenchmarkResult(
            id = UUID.randomUUID().toString(),
            createdAt = clock.nowMillis(),
            modelId = settings.modelId,
            modelName = settings.modelName,
            backend = ready.backend.label,
            loadMs = ready.loadMs,
            prompts = results,
            peakMemoryMb = peakMb.get(),
            device = metrics.deviceName(),
            androidVersion = metrics.androidVersion(),
        )
        repository.save(result)
        send(BenchmarkProgress.Finished(result))
    }

    private companion object {
        const val SAMPLE_INTERVAL_MS = 250L

        /** Greedy decoding: always the most likely token → identical output every run. */
        val GREEDY = SamplingParams(temperature = 1.0, topK = 1, topP = 1.0, seed = 42)
    }
}
