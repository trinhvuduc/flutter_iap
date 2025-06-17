import 'package:flutter/services.dart';
import 'flutter_iap_platform_interface.dart';
import 'models.dart';

// Export models for external use
export 'models.dart';

class FlutterIAP {

  /// Initialize connection (Android only)
  /// 
  /// This method should be called before using any other IAP methods on Android.
  /// On iOS, this method is not required as StoreKit initializes automatically.
  /// 
  /// Returns true if connection was successful, false otherwise.
  /// Throws UnsupportedError on non-Android platforms.
  Future<bool> initConnection() {
    return FlutterIAPPlatform.instance.initConnection();
  }

  /// Check the current subscription status
  /// 
  /// Returns a SubscriptionStatus object containing:
  /// - status: "subscribed" or "notSubscribed"
  /// - isPro: boolean indicating if user has premium access
  /// - activation: timestamp when subscription was activated (Android)
  /// - expiration: timestamp when subscription expires (iOS)
  Future<SubscriptionStatus> checkSubscriptionStatus() {
    return FlutterIAPPlatform.instance.checkSubscriptionStatus();
  }

  /// Fetch available subscription products
  /// 
  /// [productIds] - List of product IDs to fetch
  /// 
  /// Returns a List of ProductDetails objects containing product information:
  /// - id: product identifier
  /// - title: product title
  /// - description: product description
  /// - price: formatted price string
  /// - rawPrice: raw price value in micros (Android) or decimal (iOS)
  /// - basePlanId: base plan identifier (Android only)
  Future<List<ProductDetails>> fetchSubscriptions(List<String> productIds) {
    return FlutterIAPPlatform.instance.fetchSubscriptions(productIds);
  }

  /// Purchase a subscription product
  /// 
  /// [productId] - The product identifier to purchase
  /// 
  /// Returns true if purchase was initiated successfully.
  /// Listen to platform-specific callbacks for purchase completion events.
  Future<bool> purchaseProduct(String productId) {
    return FlutterIAPPlatform.instance.purchaseProduct(productId);
  }

  /// Restore previous purchases
  /// 
  /// This method queries the app store for previously purchased items
  /// and restores them if they are still valid.
  /// 
  /// Returns true if restoration was successful, false otherwise.
  Future<bool> restorePurchases() {
    return FlutterIAPPlatform.instance.restorePurchases();
  }

  /// Set a method call handler to listen for callbacks from native platforms
  /// 
  /// This allows you to listen for events like purchase success, purchase errors,
  /// and other callbacks from the native Android and iOS implementations.
  /// 
  /// Example usage:
  /// ```dart
  /// final flutterIap = FlutterIAP();
  /// flutterIap.setMethodCallHandler((call) async {
  ///   switch (call.method) {
  ///     case 'onPurchaseSuccess':
  ///       final data = call.arguments as Map<String, dynamic>;
  ///       // Handle successful purchase
  ///       print('Purchase successful: $data');
  ///       break;
  ///     case 'onPurchaseError':
  ///       final data = call.arguments as Map<String, dynamic>;
  ///       // Handle purchase error
  ///       print('Purchase error: $data');
  ///       break;
  ///   }
  /// });
  /// ```
  void setMethodCallHandler(Future<dynamic> Function(MethodCall call)? handler) {
    return FlutterIAPPlatform.instance.setMethodCallHandler(handler);
  }
}
