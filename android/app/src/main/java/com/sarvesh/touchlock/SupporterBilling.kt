package com.sarvesh.touchlock

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Manages a single "Supporter" in-app purchase via Google Play Billing.
 *
 * The IAP is a one-time (non-consumable) purchase that acts as a tip jar.
 * When purchased, it sets [isSupporter] = true and the UI shows a supporter badge.
 */
class SupporterBilling private constructor(
    private val context: Context,
) : PurchasesUpdatedListener {

    companion object {
        private const val PRODUCT_ID = "supporter"
        private const val TAG = "SupporterBilling"

        @Volatile
        private var instance: SupporterBilling? = null

        fun get(context: Context): SupporterBilling {
            return instance ?: synchronized(this) {
                instance ?: SupporterBilling(context.applicationContext).also { instance = it }
            }
        }
    }

    private val _isSupporter = MutableStateFlow(false)
    val isSupporter: StateFlow<Boolean> = _isSupporter

    private val _productDetails = MutableStateFlow<ProductDetails?>(null)
    val productDetails: StateFlow<ProductDetails?> = _productDetails

    private val _purchaseFlowState = MutableStateFlow<PurchaseFlowState>(PurchaseFlowState.Idle)
    val purchaseFlowState: StateFlow<PurchaseFlowState> = _purchaseFlowState

    private var billingClient: BillingClient? = null
    private var isClientReady = false

    fun startConnection() {
        if (billingClient != null) return

        val pendingPurchasesParams = PendingPurchasesParams.newBuilder()
            .enableOneTimeProducts()
            .build()

        billingClient = BillingClient.newBuilder(context)
            .setListener(this)
            .enablePendingPurchases(pendingPurchasesParams)
            .build()

        billingClient?.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    isClientReady = true
                    queryProductDetails()
                    queryExistingPurchases()
                }
            }

            override fun onBillingServiceDisconnected() {
                isClientReady = false
            }
        })
    }

    fun endConnection() {
        billingClient?.endConnection()
        billingClient = null
        isClientReady = false
    }

    private fun queryProductDetails() {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(PRODUCT_ID)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build()
                )
            )
            .build()

        billingClient?.queryProductDetailsAsync(params) { billingResult, productDetailsList ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                _productDetails.value = productDetailsList.firstOrNull()
            }
        }
    }

    private fun queryExistingPurchases() {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()

        billingClient?.queryPurchasesAsync(params) { billingResult, purchasesList ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                val hasSupporter = purchasesList.any { purchase ->
                    purchase.products.contains(PRODUCT_ID) &&
                        purchase.purchaseState == Purchase.PurchaseState.PURCHASED
                }
                if (hasSupporter) {
                    _isSupporter.value = true
                    // Acknowledge any unacknowledged purchases
                    purchasesList.forEach { purchase ->
                        if (purchase.products.contains(PRODUCT_ID) && !purchase.isAcknowledged) {
                            acknowledgePurchase(purchase)
                        }
                    }
                }
            }
        }
    }

    fun launchPurchaseFlow(activity: Activity) {
        val details = _productDetails.value ?: return
        if (!isClientReady) return

        val productDetailsParamsList = listOf(
            BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(details)
                .build()
        )

        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(productDetailsParamsList)
            .build()

        val result = billingClient?.launchBillingFlow(activity, flowParams)
        if (result?.responseCode != BillingClient.BillingResponseCode.OK) {
            _purchaseFlowState.value = PurchaseFlowState.Error("Could not start purchase. Play Store may be unavailable.")
        }
    }

    override fun onPurchasesUpdated(
        billingResult: BillingResult,
        purchases: MutableList<Purchase>?,
    ) {
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                purchases?.forEach { purchase ->
                    if (purchase.products.contains(PRODUCT_ID) &&
                        purchase.purchaseState == Purchase.PurchaseState.PURCHASED
                    ) {
                        if (!purchase.isAcknowledged) {
                            acknowledgePurchase(purchase)
                        }
                        _isSupporter.value = true
                        _purchaseFlowState.value = PurchaseFlowState.Success
                    }
                }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                _purchaseFlowState.value = PurchaseFlowState.Cancelled
            }
            else -> {
                _purchaseFlowState.value = PurchaseFlowState.Error(
                    billingResult.debugMessage.ifEmpty { "Purchase failed." }
                )
            }
        }
    }

    private fun acknowledgePurchase(purchase: Purchase) {
        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()
        billingClient?.acknowledgePurchase(params) { }
    }

    fun resetFlowState() {
        _purchaseFlowState.value = PurchaseFlowState.Idle
    }
}

sealed class PurchaseFlowState {
    data object Idle : PurchaseFlowState()
    data object Success : PurchaseFlowState()
    data object Cancelled : PurchaseFlowState()
    data class Error(val message: String) : PurchaseFlowState()
}
