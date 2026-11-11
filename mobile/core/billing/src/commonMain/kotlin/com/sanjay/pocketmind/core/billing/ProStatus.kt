package com.sanjay.pocketmind.core.billing

import com.sanjay.pocketmind.core.datastore.SettingsDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The one-time product created in Play Console. Its PRICE is set there, never in code (decision 4). */
const val PRO_PRODUCT_ID = "pocketmind_pro"

/** What Play offers, in the user's currency, exactly as Play formats it ("₹249.00"). */
data class ProOffer(val formattedPrice: String)

enum class BuyResult {
    /** Play's purchase sheet is open; the result arrives through [ProStatus.isPro]. */
    STARTED,

    ALREADY_OWNED,

    /** No Play Store, not signed in, or the product isn't published yet. */
    UNAVAILABLE,
}

/**
 * PocketMind Pro: a one-time purchase that unlocks bigger models, any PDF length and custom personas
 * (decision 2; voice stays free). Server-free (decision 2): Play itself is asked who owns what.
 */
interface ProStatus {
    val isPro: StateFlow<Boolean>

    /** The price to show, or null while unknown (no Play Store, offline, product not published). */
    val offer: StateFlow<ProOffer?>

    /** Asks Play again (app start, "Restore purchase"). Also acknowledges a fresh purchase. */
    suspend fun refresh()

    suspend fun buy(host: PurchaseHost): BuyResult
}

/** One purchase as plain facts, so the rules below are tested without Play. */
data class PurchaseRecord(
    val products: List<String>,
    val purchased: Boolean,
    val acknowledged: Boolean,
    val token: String,
)

/**
 * The entitlement rules. PENDING purchases (cash payments that haven't cleared) do not unlock Pro
 * yet. Play refunds a purchase that isn't acknowledged within 3 days, so every completed one is
 * acknowledged as soon as the app sees it.
 */
object Entitlement {
    fun ownsPro(purchases: List<PurchaseRecord>): Boolean =
        purchases.any { PRO_PRODUCT_ID in it.products && it.purchased }

    fun toAcknowledge(purchases: List<PurchaseRecord>): List<PurchaseRecord> =
        purchases.filter { PRO_PRODUCT_ID in it.products && it.purchased && !it.acknowledged }
}

/** The Play Billing operations PocketMind needs (real one: [PlayBillingGateway]; tests: a fake). */
interface BillingGateway {
    /** Purchases Play knows for this user, or null if Play can't be asked right now. */
    suspend fun purchases(): List<PurchaseRecord>?

    suspend fun offer(): ProOffer?

    suspend fun acknowledge(token: String): Boolean

    /** Opens Play's purchase sheet for Pro. */
    suspend fun launchPurchase(host: PurchaseHost): BuyResult

    /** Called with the purchases whenever a purchase flow finishes. */
    fun setOnPurchasesUpdated(listener: (List<PurchaseRecord>) -> Unit)
}

internal class PlayProStatus(
    private val gateway: BillingGateway,
    private val settings: SettingsDataSource,
    private val scope: CoroutineScope,
) : ProStatus {
    /** What Play said this session (null = not asked yet: the cached answer counts until then). */
    private val confirmed = MutableStateFlow<Boolean?>(null)

    override val isPro: StateFlow<Boolean> = combine(settings.proCached, confirmed) { cached, fromPlay ->
        fromPlay ?: cached
    }.stateIn(scope, SharingStarted.Eagerly, false)

    private val _offer = MutableStateFlow<ProOffer?>(null)
    override val offer: StateFlow<ProOffer?> = _offer.asStateFlow()

    init {
        gateway.setOnPurchasesUpdated { purchases -> scope.launch { apply(purchases) } }
    }

    override suspend fun refresh() {
        gateway.purchases()?.let { apply(it) } // null: offline or no Play: keep the cached answer
        _offer.value = gateway.offer()
    }

    override suspend fun buy(host: PurchaseHost): BuyResult =
        if (isPro.value) BuyResult.ALREADY_OWNED else gateway.launchPurchase(host)

    private suspend fun apply(purchases: List<PurchaseRecord>) {
        Entitlement.toAcknowledge(purchases).forEach { gateway.acknowledge(it.token) }
        val owned = Entitlement.ownsPro(purchases)
        confirmed.value = owned
        settings.setProCached(owned)
    }
}
