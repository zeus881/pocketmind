package com.sanjay.pocketmind

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Guards the privacy promise against library updates: no Google "datatransport" upload backend may be
 * registered in the merged manifest (Play Billing brings one; AndroidManifest.xml removes it). Without a
 * registered backend, queued diagnostic events are deleted on the phone instead of being sent.
 */
@RunWith(RobolectricTestRunner::class)
class NoTelemetryBackendTest {
    @Test
    fun `no datatransport upload backend is registered`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val discovery = ComponentName(context, DISCOVERY)
        val metaData = context.packageManager.getServiceInfo(discovery, PackageManager.GET_META_DATA).metaData
        val backends = metaData?.keySet().orEmpty().filter { it.startsWith("backend:") }

        assertTrue("Telemetry backends registered: $backends", backends.isEmpty())
    }

    private companion object {
        const val DISCOVERY = "com.google.android.datatransport.runtime.backends.TransportBackendDiscovery"
    }
}
