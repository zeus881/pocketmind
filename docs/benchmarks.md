# PocketMind benchmarks

**Only measured numbers go in this file.** Every row comes from the in-app benchmark
(Settings → Benchmark → Run → Share) or from the Macrobenchmark module on a real phone.
Empty cells mean "not measured yet" — never estimate them.

## In-app LLM benchmark (3 fixed prompts, greedy decoding, cold load)

| Phone | SoC | RAM | Android | Model | Backend | Load (s) | TTFT avg (s) | Decode avg (tok/s) | Peak mem (MB) |
|---|---|---|---|---|---|---|---|---|---|
| | | | | Gemma 4 E2B | | | | | |
| | | | | Qwen3.5 0.8B | | | | | |
| | | | | Qwen3 0.6B | | | | | |

How to fill a row: run the benchmark, tap Share, copy the numbers from the shared text. Values marked
`*` in the shared text are estimates (the runtime's counters were unavailable) — mark them the same way here.

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
