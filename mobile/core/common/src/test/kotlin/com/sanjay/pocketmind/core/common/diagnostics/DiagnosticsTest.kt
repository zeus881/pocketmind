package com.sanjay.pocketmind.core.common.diagnostics

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticsTest {
    private val dir: File = Files.createTempDirectory("crash-log").toFile()

    @Test
    fun `the crash log keeps the last three crashes with their stack traces`() {
        var now = 0L
        val log = CrashLog(File(dir, "crash.log")) { now }

        (1..4).forEach { n ->
            now = n * 1_000L
            log.record("main", IllegalStateException("crash $n"))
        }

        val entries = log.entries()
        assertEquals(3, entries.size)
        assertTrue(entries.first().contains("crash 2"))
        assertTrue(entries.last().startsWith("== 1970-01-01T00:00:04Z on thread main"))
        assertTrue(entries.last().contains("at com.sanjay.pocketmind.core.common.diagnostics.DiagnosticsTest"))

        log.clear()
        assertTrue(log.entries().isEmpty())
    }

    @Test
    fun `the report lists facts, app details and crashes`() {
        val device = DeviceFacts("2.0.0", "15", 35, "Google Pixel 8", 8)

        val withCrash = DiagnosticReport.build(device, mapOf("Model" to "Gemma 4 E2B"), listOf("== boom"))
        val clean = DiagnosticReport.build(device, emptyMap(), emptyList())

        assertTrue(withCrash.contains("App version: 2.0.0\nAndroid: 15 (API 35)\nDevice: Google Pixel 8, 8 GB RAM"))
        assertTrue(withCrash.contains("Model: Gemma 4 E2B"))
        assertTrue(withCrash.contains("Last crashes (1):\n== boom"))
        assertTrue(clean.contains("No crashes recorded."))
    }
}
