package com.sanjay.pocketmind.core.testing

import android.app.Activity
import com.sanjay.pocketmind.core.billing.BuyResult
import com.sanjay.pocketmind.core.billing.ProOffer
import com.sanjay.pocketmind.core.billing.ProStatus
import kotlinx.coroutines.flow.MutableStateFlow

/** [ProStatus] for tests: set [isPro] directly; [buy] unlocks Pro like a finished Play purchase. */
class FakeProStatus(pro: Boolean = false) : ProStatus {
    override val isPro = MutableStateFlow(pro)
    override val offer = MutableStateFlow<ProOffer?>(ProOffer("₹249.00"))
    var refreshes = 0
        private set

    override suspend fun refresh() {
        refreshes++
    }

    override suspend fun buy(host: Activity): BuyResult {
        if (isPro.value) return BuyResult.ALREADY_OWNED
        isPro.value = true
        return BuyResult.STARTED
    }
}
