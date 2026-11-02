# Performance budget

A performance budget is a list of limits the app must stay inside, like a financial budget. Every number in the
"Measured" column was measured on a real phone with the method shown. "Not measured yet" means exactly that: we never
fill this table with estimates.

Reference phone: **iQOO Neo 10R** (Snapdragon 8s Gen 3, 8 GB RAM, Android 16), model **Gemma 4 E2B** on GPU.

| Metric | Budget | Measured | How it is measured | Date |
|---|---|---|---|---|
| Cold app start (release, to first frame) | < 500 ms | Not measured yet | `StartupBenchmark` (Macrobenchmark, `StartupMode.COLD`), median of 10 | — |
| Model load (warm GPU program cache) | < 8 s | 5.5 s (5,454 ms) | Benchmark screen | 2026-11-02 |
| Model load (first ever, GPU programs compiled) | < 20 s | 13.3 s | logcat `Model loaded on GPU in … ms` | 2026-11-02 |
| Time to first token | < 1.0 s | 0.39 s | Benchmark screen (LiteRT-LM `BenchmarkInfo`) | 2026-11-02 |
| Decode speed, GPU | ≥ 20 tok/s | 26.3 tok/s | Benchmark screen, greedy sampling, 3 fixed prompts | 2026-11-02 |
| Peak memory during generation | < 1.5 GB PSS | 960 MB | Benchmark screen (`Debug.getPss`; PSS undercounts GPU memory) | 2026-11-02 |
| Frames over 16 ms while streaming an answer | < 1 % | Not measured yet | Macrobenchmark `FrameTimingMetric` on a streaming journey | — |
| Release APK size (arm64) | < 30 MB | 27.27 MB (was 31.2 MB before removing unused Bouncy Castle PQC data, v2 phase 5) | `app-release-unsigned.apk` file size | 2026-11-02 |
| Model reload after a short app switch (< 5 min) | 0 s (no reload) | Not measured yet | logcat, switch apps for 30 s and return | — |

## Rules

1. A pull request that pushes a measured value over its budget must say so in its description and in this file.
2. Numbers are re-measured after every phase that touches startup, the engine, the chat list or the APK contents.
3. Measure release builds for startup and frames. Debug builds are 2–5× slower and say nothing about users.
