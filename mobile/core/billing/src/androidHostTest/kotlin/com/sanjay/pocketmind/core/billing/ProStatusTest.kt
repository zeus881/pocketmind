package com.sanjay.pocketmind.core.billing

import android.app.Activity
import com.sanjay.pocketmind.core.datastore.SettingsDataSource
import com.sanjay.pocketmind.core.testing.InMemoryPreferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ProStatusTest {
    private fun pro(purchased: Boolean = true, acknowledged: Boolean = true, token: String = "t1") =
        PurchaseRecord(listOf(PRO_PRODUCT_ID), purchased, acknowledged, token)

    private class FakeGateway : BillingGateway {
        var purchases: List<PurchaseRecord>? = emptyList()
        var offer: ProOffer? = ProOffer("₹249.00")
        var launchResult = BuyResult.STARTED
        val acknowledged = mutableListOf<String>()
        var launches = 0
        lateinit var updated: (List<PurchaseRecord>) -> Unit

        override suspend fun purchases() = purchases

        override suspend fun offer() = offer

        override suspend fun acknowledge(token: String) = acknowledged.add(token)

        override suspend fun launchPurchase(host: Activity): BuyResult = launchResult.also { launches++ }

        override fun setOnPurchasesUpdated(listener: (List<PurchaseRecord>) -> Unit) {
            updated = listener
        }
    }

    /**
     * The app scope for the code under test. Unconfined: `isPro` (a stateIn in this scope) updates
     * at once; advanceUntilIdle() would not wait for backgroundScope work.
     */
    private fun TestScope.eagerScope() =
        CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler))

    @Test
    fun `only a completed Pro purchase unlocks, and unacknowledged ones are found`() {
        assertTrue(Entitlement.ownsPro(listOf(pro())))
        assertFalse(Entitlement.ownsPro(listOf(pro(purchased = false)))) // pending cash payment
        assertFalse(Entitlement.ownsPro(listOf(PurchaseRecord(listOf("other"), true, true, "x"))))
        val toAcknowledge = Entitlement.toAcknowledge(listOf(pro(), pro(acknowledged = false, token = "t2")))
        assertEquals(listOf("t2"), toAcknowledge.map { it.token })
    }

    @Test
    fun `a purchase found by refresh unlocks Pro, is acknowledged and remembered`() = runTest {
        val gateway = FakeGateway().apply { purchases = listOf(pro(acknowledged = false)) }
        val settings = SettingsDataSource(InMemoryPreferencesDataStore())
        val status = PlayProStatus(gateway, settings, eagerScope())

        status.refresh()
        advanceUntilIdle()

        assertTrue(status.isPro.value)
        assertEquals(listOf("t1"), gateway.acknowledged)
        assertEquals(ProOffer("₹249.00"), status.offer.value)
        assertTrue(settings.proCached.first())
    }

    @Test
    fun `offline the cached answer counts, a refund turns Pro off`() = runTest {
        val settings = SettingsDataSource(InMemoryPreferencesDataStore()).apply { setProCached(true) }
        val gateway = FakeGateway().apply { purchases = null } // Play can't be asked
        val status = PlayProStatus(gateway, settings, eagerScope())

        status.refresh()
        advanceUntilIdle()
        assertTrue(status.isPro.value)

        gateway.purchases = emptyList() // refunded
        status.refresh()
        advanceUntilIdle()
        assertFalse(status.isPro.value)
        assertFalse(settings.proCached.first())
    }

    @Test
    fun `buying opens Play's sheet unless Pro is owned, and a finished purchase unlocks`() = runTest {
        val gateway = FakeGateway()
        val status = PlayProStatus(gateway, SettingsDataSource(InMemoryPreferencesDataStore()), eagerScope())
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()

        assertEquals(BuyResult.STARTED, status.buy(activity))
        gateway.updated(listOf(pro(acknowledged = false)))
        advanceUntilIdle()

        assertTrue(status.isPro.value)
        assertEquals(BuyResult.ALREADY_OWNED, status.buy(activity))
        assertEquals(1, gateway.launches)
    }
}
