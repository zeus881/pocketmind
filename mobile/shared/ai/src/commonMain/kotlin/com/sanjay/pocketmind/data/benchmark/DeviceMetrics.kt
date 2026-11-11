package com.sanjay.pocketmind.data.benchmark

/**
 * Hardware facts and timers the benchmark needs. An interface so the runner can be unit-tested
 * with a fake (real memory readings and clocks make tests non-deterministic).
 *
 * V3: the interface is shared; each platform reads its own numbers (Android: AndroidDeviceMetrics in
 * data:benchmark).
 */
interface DeviceMetrics {
    /** Current memory of this app process (PSS) in MB, including the model's native memory. */
    fun processMemoryMb(): Int

    /** Monotonic time in ms (not affected by the user changing the wall clock). */
    fun elapsedMs(): Long

    fun deviceName(): String

    fun androidVersion(): String
}
