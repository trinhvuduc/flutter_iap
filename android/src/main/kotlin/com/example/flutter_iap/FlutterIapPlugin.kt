// Author: ductv

package com.example.flutter_iap

import android.app.Activity
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result

/** FlutterIAPPlugin */
class FlutterIAPPlugin: FlutterPlugin, MethodCallHandler, ActivityAware {
  /// The MethodChannel that will the communication between Flutter and native Android
  ///
  /// This local reference serves to register the plugin with the Flutter Engine and unregister it
  /// when the Flutter Engine is detached from the Activity
  private lateinit var channel : MethodChannel
  private var activity: Activity? = null
  private var billingManager: BillingManager? = null

  override fun onAttachedToEngine(flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
    channel = MethodChannel(flutterPluginBinding.binaryMessenger, "flutter_iap")
    channel.setMethodCallHandler(this)
  }

  override fun onMethodCall(call: MethodCall, result: Result) {
    val billing = billingManager
    if (billing == null) {
      result.error("NOT_INITIALIZED", "BillingManager not initialized", null)
      return
    }
    
    when (call.method) {
      "initConnection" -> {
        billing.initConnection(result)
      }
      "checkSubscriptionStatus" -> {
        billing.checkSubscriptionStatus(result)
      }
      "fetchSubscriptions" -> {
        val productIDs = call.argument<List<String>>("productIDs") ?: emptyList()
        billing.fetchSubscriptions(productIDs, result)
      }
      "purchaseProduct" -> {
        val productId = call.argument<String>("productId")
        if (productId != null) {
          billing.purchaseProduct(productId, result)
        } else {
          result.error("INVALID_ARGUMENTS", "Product ID is required", null)
        }
      }
      "restorePurchases" -> {
        billing.restorePurchases(result)
      }
      else -> {
        result.notImplemented()
      }
    }
  }

  override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
    channel.setMethodCallHandler(null)
  }

  override fun onAttachedToActivity(binding: ActivityPluginBinding) {
    activity = binding.activity
    initializeBillingManager()
  }

  override fun onDetachedFromActivityForConfigChanges() {
    // Keep the activity reference for config changes
  }

  override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
    activity = binding.activity
    initializeBillingManager()
  }

  override fun onDetachedFromActivity() {
    activity = null
    billingManager = null
  }

  private fun initializeBillingManager() {
    activity?.let { act ->
      billingManager = BillingManager(act)
      billingManager?.initialize(channel)
    }
  }
}
