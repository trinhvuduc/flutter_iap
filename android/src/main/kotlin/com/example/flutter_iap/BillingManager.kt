package com.example.flutter_iap

import android.app.Activity
import com.android.billingclient.api.*
import io.flutter.plugin.common.MethodChannel

class BillingManager(private val activity: Activity) {
    private lateinit var billingClient: BillingClient
    private var methodChannel: MethodChannel? = null
    
    fun initialize(methodChannel: MethodChannel) {
        this.methodChannel = methodChannel
        setupBillingClient()
    }
    
    private fun setupBillingClient() {
        billingClient = BillingClient.newBuilder(activity)
            .setListener { billingResult, purchases ->
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
                    for (purchase in purchases) {
                        handlePurchase(purchase)
                    }
                } else {
                    // Handle purchase errors
                    val errorMessage = when (billingResult.responseCode) {
                        BillingClient.BillingResponseCode.USER_CANCELED -> "Purchase canceled"
                        BillingClient.BillingResponseCode.ITEM_UNAVAILABLE -> "Item not available"
                        BillingClient.BillingResponseCode.BILLING_UNAVAILABLE -> "Billing not available"
                        BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> "Already purchased"
                        else -> "Purchase failed"
                    }
                    
                    methodChannel?.invokeMethod("onPurchaseError", mapOf(
                        "code" to billingResult.responseCode,
                        "message" to errorMessage
                    ))
                }
            }
            .enablePendingPurchases()
            .build()
    }
    
    fun initConnection(result: MethodChannel.Result) {
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    result.success(true)
                } else {
                    val errorMessage = when (billingResult.responseCode) {
                        BillingClient.BillingResponseCode.BILLING_UNAVAILABLE -> 
                            "Billing service is unavailable on this device"
                        BillingClient.BillingResponseCode.FEATURE_NOT_SUPPORTED -> 
                            "In-app purchases are not supported on this device"
                        BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE -> 
                            "Google Play services are unavailable"
                        else -> "Billing setup failed: ${billingResult.debugMessage}"
                    }
                    result.error("BILLING_SETUP_FAILED", errorMessage, billingResult.responseCode)
                }
            }
            
            override fun onBillingServiceDisconnected() {
                result.error("BILLING_DISCONNECTED", "Billing service disconnected", null)
            }
        })
    }
    
    fun fetchSubscriptions(productIDs: List<String>, result: MethodChannel.Result) {
        val productList = productIDs.map { productId ->
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(productId)
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        }
        
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()
            
        billingClient.queryProductDetailsAsync(params) { billingResult, productDetailsList ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                val products = productDetailsList.flatMap { productDetails ->
                    val offers = productDetails.subscriptionOfferDetails ?: emptyList()
    
                    offers.map { offer ->
                        val pricingPhase = offer?.pricingPhases?.pricingPhaseList?.firstOrNull()

                        mapOf(
                            "id" to productDetails.productId,
                            "basePlanId" to offer?.basePlanId,
                            "title" to productDetails.title,
                            "description" to productDetails.description,
                            "price" to pricingPhase?.formattedPrice,
                            "rawPrice" to pricingPhase?.priceAmountMicros,
                        )
                    }
                }
                result.success(products)
            } else {
                result.error("QUERY_FAILED", "Failed to query products: ${billingResult.debugMessage}", null)
            }
        }
    }
    
    fun purchaseProduct(productId: String, result: MethodChannel.Result) {
        val productList = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(productId)
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        )
        
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()
            
        billingClient.queryProductDetailsAsync(params) { billingResult, productDetailsList ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && productDetailsList.isNotEmpty()) {
                val productDetails = productDetailsList[0]
                val offers = productDetails.subscriptionOfferDetails?.orEmpty()

                val trialOffer = offers?.find { offer ->
                    offer.pricingPhases.pricingPhaseList.any { phase ->
                        phase.priceAmountMicros == 0L
                    }
                }

                val selectedOffer = trialOffer ?: offers?.firstOrNull()
                
                if (selectedOffer == null) {
                    result.error("NO_OFFERS", "No subscription offers available for product: $productId", null)
                    return@queryProductDetailsAsync
                }
                
                val productDetailsParamsList = listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(productDetails)
                        .setOfferToken(selectedOffer.offerToken)
                        .build()
                )
                
                val billingFlowParams = BillingFlowParams.newBuilder()
                    .setProductDetailsParamsList(productDetailsParamsList)
                    .build()
                
                val launchResult = billingClient.launchBillingFlow(activity, billingFlowParams)
                if (launchResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    result.success(true)
                } else {
                    result.error("PURCHASE_FAILED", "Failed to launch billing flow: ${launchResult.debugMessage}", null)
                }
            } else {
                result.error("PRODUCT_NOT_FOUND", "Product not found: $productId", null)
            }
        }
    }
    
    fun checkSubscriptionStatus(result: MethodChannel.Result) {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()
            
        billingClient.queryPurchasesAsync(params) { billingResult, purchasesList ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                val hasPurchases = purchasesList.isNotEmpty()
                val oldestPurchaseTime = if (hasPurchases) {
                    purchasesList.minOfOrNull { it.purchaseTime }
                } else {
                    null
                }
                
                result.success(mapOf(
                    "status" to if (hasPurchases) "subscribed" else "notSubscribed",
                    "isPro" to hasPurchases,
                    "activation" to oldestPurchaseTime
                ))
            } else {
                result.error("QUERY_PURCHASES_FAILED", "Failed to query purchases: ${billingResult.debugMessage}", null)
            }
        }
    }
    
    fun restorePurchases(result: MethodChannel.Result) {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()
            
        billingClient.queryPurchasesAsync(params) { billingResult, purchasesList ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                
                // Acknowledge any unacknowledged purchases
                purchasesList.forEach { purchase ->
                    if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED && !purchase.isAcknowledged) {
                        val acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
                            .setPurchaseToken(purchase.purchaseToken)
                            .build()
                            
                        billingClient.acknowledgePurchase(acknowledgePurchaseParams) { _ ->
                            // Acknowledgment handled
                        }
                    }
                }
                
                result.success(true)
            } else {
                result.error("RESTORE_FAILED", "Failed to restore purchases: ${billingResult.debugMessage}", null)
            }
        }
    }

    fun checkRefunded(result: MethodChannel.Result) {
        val params = QueryPurchaseHistoryParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()
            
        billingClient.queryPurchaseHistoryAsync(params) { billingResult, purchaseHistoryRecordList ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchaseHistoryRecordList != null) {
                // Query current purchases to compare with history
                val currentParams = QueryPurchasesParams.newBuilder()
                    .setProductType(BillingClient.ProductType.SUBS)
                    .build()
                    
                billingClient.queryPurchasesAsync(currentParams) { currentBillingResult, currentPurchasesList ->
                    if (currentBillingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                        val currentTokens = currentPurchasesList.map { it.purchaseToken }.toSet()
                        
                        val hasRefunded = purchaseHistoryRecordList.any { historyRecord ->
                            !currentTokens.contains(historyRecord.purchaseToken)
                        }
                        
                        result.success(hasRefunded)
                    } else {
                        result.error("REFUND_CHECK_FAILED", "Failed to check current purchases: ${currentBillingResult.debugMessage}", null)
                    }
                }
            } else {
                result.error("REFUND_CHECK_FAILED", "Failed to check purchase history: ${billingResult.debugMessage}", null)
            }
        }
    }
    
    private fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
            methodChannel?.invokeMethod("onPurchaseSuccess", mapOf(
                "status" to "subscribed",
                "isPro" to true,
                "activation" to purchase.purchaseTime
            ))
        }
    }
}
