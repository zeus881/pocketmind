package com.sanjay.pocketmind

import android.os.StrictMode
import org.junit.After
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Debug builds must really install StrictMode (the default policies detect nothing). */
@RunWith(RobolectricTestRunner::class)
class StrictModePoliciesTest {
    @After
    fun reset() {
        StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.LAX)
        StrictMode.setVmPolicy(StrictMode.VmPolicy.LAX)
    }

    @Test
    fun `enable installs non-lax thread and VM policies`() {
        StrictModePolicies.enable()

        assertNotEquals(StrictMode.ThreadPolicy.LAX.toString(), StrictMode.getThreadPolicy().toString())
        assertNotEquals(StrictMode.VmPolicy.LAX.toString(), StrictMode.getVmPolicy().toString())
    }
}
