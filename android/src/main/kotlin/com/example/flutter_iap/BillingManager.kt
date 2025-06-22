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
                
                // Keep track of unacknowledged purchases that need to be acknowledged
                val unacknowledgedPurchases = purchasesList.filter { purchase ->
                    purchase.purchaseState == Purchase.PurchaseState.PURCHASED && !purchase.isAcknowledged
                }
                
                if (unacknowledgedPurchases.isNotEmpty()) {
                    // Acknowledge unacknowledged purchases
                    var acknowledgedCount = 0
                    
                    unacknowledgedPurchases.forEach { purchase ->
                        val acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
                            .setPurchaseToken(purchase.purchaseToken)
                            .build()
                            
                        billingClient.acknowledgePurchase(acknowledgePurchaseParams) { _ ->
                            acknowledgedCount++
                            
                            // Once all purchases are acknowledged, query again
                            if (acknowledgedCount == unacknowledgedPurchases.size) {
                                queryPurchasesAfterAcknowledge(result)
                            }
                        }
                    }
                } else {
                    // No purchases to acknowledge, query directly
                    queryPurchasesAfterAcknowledge(result)
                }

            } else {
                result.error("RESTORE_FAILED", "Failed to restore purchases: ${billingResult.debugMessage}", null)
            }
        }
    }
    
    private fun queryPurchasesAfterAcknowledge(result: MethodChannel.Result) {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()
            
        billingClient.queryPurchasesAsync(params) { billingResult, purchasesList ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                result.success(purchasesList.isNotEmpty())
            } else {
                result.error("QUERY_FAILED", "Failed to query purchases after acknowledge: ${billingResult.debugMessage}", null)
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
            if (!purchase.isAcknowledged) {
                val params = AcknowledgePurchaseParams.newBuilder()
                    .setPurchaseToken(purchase.purchaseToken)
                    .build()
                billingClient.acknowledgePurchase(params, { billingResult ->
                        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                            
                        } else {

                        }
                    }
                )
            }
        }
    }
}
