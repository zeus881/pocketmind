package com.sanjay.pocketmind.core.llm

/**
 * Lifecycle of the on-device model, exposed as a StateFlow so every screen can show
 * "Loading model…", the backend label, or an error — without asking the engine imperatively.
 *
 * ```
 *  Idle ──load()──▶ Loading ──ok──▶ Ready(backend, loadMs)
 *    ▲                 │
 *    │                 └─fail──▶ Failed(reason)
 *    └──────unload() / onTrimMemory────────┘
 * ```
 */
sealed interface EngineState {
    /** No model in memory. */
    data object Idle : EngineState

    /** `Engine.initialize()` is running (can take ~10 s for a 2.6 GB model). */
    data class Loading(val modelPath: String) : EngineState

    /** Model loaded. [backend] records which hardware actually worked; [loadMs] is the load time. */
    data class Ready(val modelPath: String, val backend: LlmBackend, val loadMs: Long) : EngineState

    /** Loading failed on every backend we tried. */
    data class Failed(val modelPath: String, val reason: FailureReason) : EngineState
}

/** Hardware the model runs on. */
enum class LlmBackend(val label: String) {
    GPU("GPU"),
    CPU("CPU"),
}

/** Why loading failed — the UI maps each value to a translated message. */
enum class FailureReason {
    /** The model file does not exist or the app cannot read it. */
    FILE_NOT_FOUND,

    /**
     * The file's size differs from the catalog: it was truncated or changed after download.
     * Feeding it to native code could crash the app, so it is never loaded.
     */
    FILE_DAMAGED,

    /** The runtime rejected the model on every backend (corrupt file, unsupported phone, out of memory). */
    INIT_FAILED,

    /**
     * Loading this model crashed the whole app in native code on every allowed backend before
     * (see [LoadGuard]). We don't try again automatically; the user can retry on purpose.
     */
    CRASHED_LAST_TIME,
}
