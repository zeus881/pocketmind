package com.sanjay.pocketmind.core.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber

/**
 * [BillingGateway] with Google Play Billing Library 9.1.0 (API checked in the artifact). Billing runs
 * in the Play Store app; PocketMind sends nothing of its own (decision 5). Without a Play Store every
 * call answers "unavailable" and the app keeps working in the free version.
 */
@Singleton
internal class PlayBillingGateway @Inject constructor(@ApplicationContext context: Context) : BillingGateway {
    private var onUpdated: (List<PurchaseRecord>) -> Unit = {}

    private val client: BillingClient = BillingClient.newBuilder(context)
        .setListener { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                onUpdated(purchases.orEmpty().map { it.toRecord() })
            } else {
                Timber.i("Purchase flow ended: %d", result.responseCode) // e.g. USER_CANCELED
            }
        }
        // Required for one-time products: cash payments complete later (PENDING until then).
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection()
        .build()

    private val connecting = Mutex()
    private var details: ProductDetails? = null

    override fun setOnPurchasesUpdated(listener: (List<PurchaseRecord>) -> Unit) {
        onUpdated = listener
    }

    private suspend fun connected(): Boolean = connecting.withLock {
        if (client.isReady) return@withLock true
        suspendCancellableCoroutine { continuation ->
            client.startConnection(
                object : BillingClientStateListener {
                    override fun onBillingSetupFinished(result: BillingResult) {
                        if (continuation.isActive) continuation.resume(result.ok())
                    }

                    override fun onBillingServiceDisconnected() {
                        if (continuation.isActive) continuation.resume(false)
                    }
                },
            )
        }
    }

    override suspend fun purchases(): List<PurchaseRecord>? {
        if (!connected()) return null
        val params = QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()
        val result = client.queryPurchasesAsync(params)
        return if (result.billingResult.ok()) result.purchasesList.map { it.toRecord() } else null
    }

    override suspend fun offer(): ProOffer? = productDetails()?.oneTimePurchaseOfferDetails?.let {
        ProOffer(it.formattedPrice)
    }

    private suspend fun productDetails(): ProductDetails? = details ?: if (connected()) fetchDetails() else null

    private suspend fun fetchDetails(): ProductDetails? {
        val product = QueryProductDetailsParams.Product.newBuilder()
            .setProductId(PRO_PRODUCT_ID)
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        val params = QueryProductDetailsParams.newBuilder().setProductList(listOf(product)).build()
        val result = client.queryProductDetails(params)
        if (!result.billingResult.ok()) return null
        return result.productDetailsList?.firstOrNull { it.productId == PRO_PRODUCT_ID }?.also { details = it }
    }

    override suspend fun acknowledge(token: String): Boolean {
        if (!connected()) return false
        val params = AcknowledgePurchaseParams.newBuilder().setPurchaseToken(token).build()
        return client.acknowledgePurchase(params).ok()
    }

    override suspend fun launchPurchase(activity: Activity): BuyResult {
        val product = productDetails() ?: return BuyResult.UNAVAILABLE
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(product).build()),
            )
            .build()
        val result = client.launchBillingFlow(activity, params)
        return when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> BuyResult.STARTED
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> BuyResult.ALREADY_OWNED
            else -> BuyResult.UNAVAILABLE
        }
    }

    private fun BillingResult.ok() = responseCode == BillingClient.BillingResponseCode.OK

    private fun Purchase.toRecord() = PurchaseRecord(
        products = products,
        purchased = purchaseState == Purchase.PurchaseState.PURCHASED,
        acknowledged = isAcknowledged,
        token = purchaseToken,
    )
}
