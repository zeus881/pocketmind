package com.sanjay.pocketmind.data.models

import android.app.ActivityManager
import android.content.Context
import android.os.StatFs
import com.sanjay.pocketmind.core.llm.ModelFiles
import kotlin.math.ceil

/** Hardware facts that decide which models a phone can run. An interface so tests can fake it. */
interface DeviceInfo {
    /** RAM as marketed ("8 GB"), see [AndroidDeviceInfo.totalRamGb]. */
    fun totalRamGb(): Int

    /** Free bytes on the storage where models are saved. */
    fun freeStorageBytes(): Long
}

class AndroidDeviceInfo(private val context: Context, private val modelFiles: ModelFiles) : DeviceInfo {

    /**
     * `totalMem` excludes memory reserved by the kernel and hardware, so an "8 GB" phone
     * reports about 7.3–7.7 billion bytes. Dividing by 10^9 and rounding UP gives the number
     * on the box, which is what model requirements are written against.
     */
    override fun totalRamGb(): Int {
        val am = context.getSystemService(ActivityManager::class.java)
        val info = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        return ceil(info.totalMem / BYTES_PER_GB).toInt()
    }

    override fun freeStorageBytes(): Long = StatFs(modelFiles.storageRoot().path).availableBytes

    private companion object {
        const val BYTES_PER_GB = 1_000_000_000.0
    }
}
