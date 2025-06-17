import 'package:plugin_platform_interface/plugin_platform_interface.dart';
import 'package:flutter/services.dart';

import 'flutter_iap_method_channel.dart';
import 'models.dart';

abstract class FlutterIAPPlatform extends PlatformInterface {
  /// Constructs a FlutterIAPPlatform.
  FlutterIAPPlatform() : super(token: _token);

  static final Object _token = Object();

  static FlutterIAPPlatform _instance = MethodChannelFlutterIAP();

  /// The default instance of [FlutterIAPPlatform] to use.
  ///
  /// Defaults to [MethodChannelFlutterIAP].
  static FlutterIAPPlatform get instance => _instance;

  /// Platform-specific implementations should set this with their own
  /// platform-specific class that extends [FlutterIAPPlatform] when
  /// they register themselves.
  static set instance(FlutterIAPPlatform instance) {
    PlatformInterface.verifyToken(instance, _token);
    _instance = instance;
  }

  /// Initialize connection (Android only)
  Future<bool> initConnection() {
    throw UnimplementedError('initConnection() has not been implemented.');
  }

  /// Check subscription status
  Future<SubscriptionStatus> checkSubscriptionStatus() {
    throw UnimplementedError('checkSubscriptionStatus() has not been implemented.');
  }

  /// Fetch available subscriptions
  Future<List<ProductDetails>> fetchSubscriptions(List<String> productIds) {
    throw UnimplementedError('fetchSubscriptions() has not been implemented.');
  }

  /// Purchase a product
  Future<bool> purchaseProduct(String productId) {
    throw UnimplementedError('purchaseProduct() has not been implemented.');
  }

  /// Restore purchases
  Future<bool> restorePurchases() {
    throw UnimplementedError('restorePurchases() has not been implemented.');
  }

  /// Set method call handler for native callbacks
  void setMethodCallHandler(Future<dynamic> Function(MethodCall call)? handler) {
    throw UnimplementedError('setMethodCallHandler() has not been implemented.');
  }
}
