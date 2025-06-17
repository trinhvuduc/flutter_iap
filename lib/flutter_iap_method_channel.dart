import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

import 'flutter_iap_platform_interface.dart';
import 'models.dart';

/// An implementation of [FlutterIAPPlatform] that uses method channels.
class MethodChannelFlutterIAP extends FlutterIAPPlatform {
  /// The method channel used to interact with the native platform.
  @visibleForTesting
  final methodChannel = const MethodChannel('flutter_iap');

  @override
  Future<bool> initConnection() async {
    // Only available on Android
    if (!defaultTargetPlatform.name.toLowerCase().contains('android')) {
      throw UnsupportedError('initConnection is only available on Android');
    }
    try {
      final result = await methodChannel.invokeMethod<bool>('initConnection');
      return result ?? false;
    } catch (e) {
      throw Exception('Failed to initialize connection: $e');
    }
  }

  @override
  Future<SubscriptionStatus> checkSubscriptionStatus() async {
    try {
      final result = await methodChannel.invokeMethod<Map<Object?, Object?>>('checkSubscriptionStatus');
      return SubscriptionStatus.fromMap(Map<String, dynamic>.from(result ?? {}));
    } catch (e) {
      throw Exception('Failed to check subscription status: $e');
    }
  }

  @override
  Future<List<ProductDetails>> fetchSubscriptions(List<String> productIds) async {
    try {
      final result = await methodChannel.invokeMethod<List<Object?>>('fetchSubscriptions', {
        'productIDs': productIds,
      });
      return (result ?? []).cast<Map<Object?, Object?>>()
          .map((item) => ProductDetails.fromMap(Map<String, dynamic>.from(item)))
          .toList();
    } catch (e) {
      throw Exception('Failed to fetch subscriptions: $e');
    }
  }

  @override
  Future<bool> purchaseProduct(String productId) async {
    try {
      final result = await methodChannel.invokeMethod<bool>('purchaseProduct', {
        'productId': productId,
      });
      return result ?? false;
    } catch (e) {
      throw Exception('Failed to purchase product: $e');
    }
  }

  @override
  Future<bool> restorePurchases() async {
    try {
      final result = await methodChannel.invokeMethod<bool>('restorePurchases');
      return result ?? false;
    } catch (e) {
      throw Exception('Failed to restore purchases: $e');
    }
  }

  @override
  void setMethodCallHandler(Future<dynamic> Function(MethodCall call)? handler) {
    methodChannel.setMethodCallHandler(handler);
  }
}
