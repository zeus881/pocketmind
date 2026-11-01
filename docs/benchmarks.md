# PocketMind benchmarks

**Only measured numbers go in this file.** Every row comes from the in-app benchmark
(Settings → Benchmark → Run → Share) or from the Macrobenchmark module on a real phone.
Empty cells mean "not measured yet" — never estimate them.

## In-app LLM benchmark (3 fixed prompts, greedy decoding, cold load)

| Phone | SoC | RAM | Android | Model | Backend | Load (s) | TTFT avg (s) | Decode avg (tok/s) | Peak mem (MB) |
|---|---|---|---|---|---|---|---|---|---|
| iQOO Neo 10R | Snapdragon 8s Gen 3 (SM8635) | 8 GB | 16 | Gemma 4 E2B | GPU | 5.5 | 0.39 | 26.3 | 960 |
| | | | | Gemma 4 E2B | | | | | |
| | | | | Qwen3.5 0.8B | | | | | |
| | | | | Qwen3 0.6B | | | | | |

Notes on the iQOO Neo 10R row (2026-11-02, debug build, phone charging):
- **Load 5.5 s** was a re-load: the first-ever load on this phone took **13.3 s** because the GPU backend
  compiles its shader programs once and caches them in the app's cache dir (`EngineConfig.cacheDir`).
  Clearing the app cache brings back the slower first load.
- **Peak memory** is process PSS (`Debug.getPss`). It does NOT fully include the memory-mapped 2.6 GB model
  file or buffers the GPU driver allocates outside the process, so it understates the true RAM footprint.
  Treat it as "app process memory", not "total model memory".

How to fill a row: run the benchmark, tap Share, copy the numbers from the shared text. Values marked
`*` in the shared text are estimates (the runtime's counters were unavailable) — mark them the same way here.

## First real-device observations (single chat answer, not the benchmark screen)

Measured 2026-11-02 on an iQOO Neo 10R (model I2221, Qualcomm SM8635 / Snapdragon 8s Gen 3, 8 GB RAM,
Android 16), debug build, Gemma 4 E2B:

| Metric | Value | Source |
|---|---|---|
| Model load (first, cold) | 13.3 s on GPU | app log `Model loaded on GPU in 13282 ms` |
| Decode speed, one chat answer | 21.7 tok/s · GPU | label under the answer |

These are single observations from a debug build; use the benchmark screen (3 fixed prompts, greedy
decoding) for the comparable numbers in the table above.

## Startup (Macrobenchmark, `./gradlew :benchmark:connectedBenchmarkReleaseAndroidTest`)

| Phone | Cold start, no profile (median ms) | Cold start, Baseline Profile (median ms) | Improvement |
|---|---|---|---|
| | | | |

## Tab-switch frame timing (`tabSwitchingFrames`)

| Phone | frameDurationCpuMs P50 | P90 | P99 | Janky frames % |
|---|---|---|---|---|
| | | | | |

## Reference (NOT our measurement)

Google's published LiteRT-LM figures for Gemma 4 E2B on a Samsung Galaxy S26 Ultra: ~47 tok/s decode on
CPU, ~52 tok/s on GPU, ~0.3 s time to first token on GPU (LiteRT-LM overview page). Use only as context.
