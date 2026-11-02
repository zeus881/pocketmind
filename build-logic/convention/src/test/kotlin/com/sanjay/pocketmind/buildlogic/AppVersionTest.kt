package com.sanjay.pocketmind.buildlogic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AppVersionTest {
    @Test
    fun `a release tag gives name and code`() {
        assertEquals(AppVersion("2.1.3", 2_010_399), AppVersion.fromTag("v2.1.3"))
        assertEquals(AppVersion("1.0.0", 1_000_099), AppVersion.fromTag("1.0.0"))
    }

    @Test
    fun `pre-releases sort before the release and in stage order`() {
        val codes = listOf("v2.0.0-alpha1", "v2.0.0-alpha2", "v2.0.0-beta1", "v2.0.0-rc.1", "v2.0.0", "v2.0.1-alpha1")
            .map { AppVersion.fromTag(it).code }
        assertEquals(codes.sorted(), codes)
        assertEquals(codes.size, codes.toSet().size)
    }

    @Test
    fun `any later version has a higher code`() {
        assertTrue(AppVersion.fromTag("v1.99.99").code < AppVersion.fromTag("v2.0.0-alpha1").code)
        assertTrue(AppVersion.fromTag("v1.2.99").code < AppVersion.fromTag("v1.3.0-alpha1").code)
    }

    @Test
    fun `no tag means a development build`() {
        assertEquals(AppVersion.DEVELOPMENT, AppVersion.fromTag(null))
        assertEquals(AppVersion.DEVELOPMENT, AppVersion.fromTag("  "))
    }

    @Test
    fun `malformed or out-of-range tags are rejected`() {
        listOf("release-1", "v1.2", "v1.100.0", "v1.0.0-alpha30", "v1.0.0-rc0", "v1.0.0-gamma1").forEach { tag ->
            assertThrows(tag, IllegalArgumentException::class.java) { AppVersion.fromTag(tag) }
        }
    }
}
