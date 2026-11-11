package com.sanjay.pocketmind.data.benchmark

import android.os.Build
import android.os.Debug
import android.os.SystemClock

class AndroidDeviceMetrics : DeviceMetrics {
    /**
     * PSS ("proportional set size") counts this process's private memory plus its fair share of
     * shared pages. It includes the native memory LiteRT-LM allocates for weights and KV cache,
     * which the Java heap numbers would completely miss. Reading it takes ~1 ms, so we sample.
     */
    override fun processMemoryMb(): Int = (Debug.getPss() / KB_PER_MB).toInt()

    override fun elapsedMs(): Long = SystemClock.elapsedRealtime()

    override fun deviceName(): String {
        val soc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) " · ${Build.SOC_MODEL}" else ""
        return "${Build.MANUFACTURER} ${Build.MODEL}$soc"
    }

    override fun androidVersion(): String = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"

    private companion object {
        const val KB_PER_MB = 1024L
    }
}
