package com.misterwhite.iptvsmart.data.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Une formule Premium telle que la Play Console la décrit (prix déjà formaté dans la devise de l'utilisateur). */
data class PremiumOffer(val plan: PremiumPlan, val price: String, internal val details: ProductDetails)

/** L'achat Premium actif sur le compte Google du téléphone. */
data class ActivePurchase(val plan: PremiumPlan, val autoRenewing: Boolean, val purchaseTime: Long)

enum class PremiumPlan(val productId: String, val productType: String) {
    MONTHLY("premium_mensuel", BillingClient.ProductType.SUBS),
    LIFETIME("premium_a_vie", BillingClient.ProductType.INAPP),
}

/**
 * Achats Premium via Google Play Billing : abonnement à 4 € par mois ou achat unique à vie.
 * Les identifiants des produits doivent correspondre à ceux créés dans la Play Console.
 */
class BillingRepository(context: Context) : PurchasesUpdatedListener {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val client = BillingClient.newBuilder(context.applicationContext)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .build()

    private val _ownsPremium = MutableStateFlow<Boolean?>(null)
    /** Vrai si un abonnement ou l'achat à vie est actif sur le compte Google du téléphone ; null tant qu'on ne sait pas. */
    val ownsPremium: StateFlow<Boolean?> = _ownsPremium.asStateFlow()

    private val _activePurchase = MutableStateFlow<ActivePurchase?>(null)
    val activePurchase: StateFlow<ActivePurchase?> = _activePurchase.asStateFlow()

    private val _offers = MutableStateFlow<List<PremiumOffer>>(emptyList())
    val offers: StateFlow<List<PremiumOffer>> = _offers.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /** Se connecte à Google Play si besoin, puis relit les offres et les achats. */
    fun refresh() {
        if (client.isReady) {
            scope.launch { load() }
            return
        }
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) scope.launch { load() }
                else Log.w(TAG, "Google Play Billing indisponible : ${result.debugMessage}")
            }

            // La connexion est retentée au prochain refresh (retour dans l'appli, ouverture de l'écran Premium).
            override fun onBillingServiceDisconnected() = Unit
        })
    }

    fun launchPurchase(activity: Activity, offer: PremiumOffer) {
        _error.value = null
        val params = BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(offer.details)
        if (offer.plan.productType == BillingClient.ProductType.SUBS) {
            val token = offer.details.subscriptionOfferDetails?.firstOrNull()?.offerToken ?: return
            params.setOfferToken(token)
        }
        val result = client.launchBillingFlow(
            activity,
            BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(params.build())).build(),
        )
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            _error.value = "Achat impossible pour le moment (${result.responseCode})."
        }
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> scope.launch { purchases.orEmpty().forEach { handle(it) } }
            BillingClient.BillingResponseCode.USER_CANCELED -> Unit
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> refresh()
            else -> _error.value = "L'achat n'a pas abouti (${result.responseCode})."
        }
    }

    private suspend fun load() {
        val details = PremiumPlan.entries.groupBy { it.productType }.flatMap { (type, plans) ->
            val params = QueryProductDetailsParams.newBuilder()
                .setProductList(plans.map {
                    QueryProductDetailsParams.Product.newBuilder().setProductId(it.productId).setProductType(type).build()
                })
                .build()
            client.queryProductDetails(params).productDetailsList.orEmpty()
        }
        _offers.value = PremiumPlan.entries.mapNotNull { plan ->
            val d = details.firstOrNull { it.productId == plan.productId } ?: return@mapNotNull null
            val price = when (plan.productType) {
                BillingClient.ProductType.SUBS ->
                    d.subscriptionOfferDetails?.firstOrNull()?.pricingPhases?.pricingPhaseList?.lastOrNull()?.formattedPrice
                else -> d.oneTimePurchaseOfferDetails?.formattedPrice
            } ?: return@mapNotNull null
            PremiumOffer(plan, price, d)
        }

        val owned = listOf(BillingClient.ProductType.SUBS, BillingClient.ProductType.INAPP).flatMap { type ->
            client.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(type).build()).purchasesList
        }
        _ownsPremium.value = owned.any { isPremium(it) }
        // L'achat à vie prime sur un abonnement éventuel.
        _activePurchase.value = owned.filter { isPremium(it) }.mapNotNull { it.toActive() }
            .maxByOrNull { if (it.plan == PremiumPlan.LIFETIME) 1 else 0 }
        owned.forEach { handle(it) }
    }

    private suspend fun handle(purchase: Purchase) {
        if (!isPremium(purchase)) return
        _ownsPremium.value = true
        if (_activePurchase.value?.plan != PremiumPlan.LIFETIME) _activePurchase.value = purchase.toActive()
        // Sans accusé de réception sous 3 jours, Google rembourse l'achat.
        if (!purchase.isAcknowledged) {
            client.acknowledgePurchase(
                AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()
            )
        }
    }

    private fun Purchase.toActive(): ActivePurchase? {
        val plan = PremiumPlan.entries.firstOrNull { it.productId in products } ?: return null
        return ActivePurchase(plan, isAutoRenewing, purchaseTime)
    }

    private fun isPremium(purchase: Purchase) =
        purchase.purchaseState == Purchase.PurchaseState.PURCHASED &&
            purchase.products.any { id -> PremiumPlan.entries.any { it.productId == id } }

    private companion object {
        const val TAG = "Billing"
    }
}
