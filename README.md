# flutter_iap

A Flutter plugin for In-App Purchases supporting both Android and iOS platforms.

## Features

- ✅ Initialize billing connection (Android)
- ✅ Check subscription status
- ✅ Fetch available subscriptions
- ✅ Purchase products
- ✅ Restore purchases
- ✅ Cross-platform support (Android & iOS)
- ✅ Type-safe models (ProductDetails, SubscriptionStatus)

## Exported Methods

This plugin exports the following methods:

1. **`initConnection()`** - Initialize billing connection (Android only)
2. **`checkSubscriptionStatus()`** - Check current subscription status (returns `SubscriptionStatus`)
3. **`fetchSubscriptions(List<String> productIds)`** - Fetch available subscription products (returns `List<ProductDetails>`)
4. **`purchaseProduct(String productId)`** - Purchase a product
5. **`restorePurchases()`** - Restore previous purchases
6. **`setMethodCallHandler(handler)`** - Listen for native callbacks (purchase success/error)

## Models

### ProductDetails
- `id`: Product identifier
- `title`: Product display name
- `description`: Product description
- `price`: Formatted price string
- `rawPrice`: Raw price value
- `basePlanId`: Base plan identifier (Android only)

### SubscriptionStatus
- `status`: Subscription status string
- `isPro`: Premium access boolean
- `isSubscribed`: Convenience getter
- `activation`/`activationDate`: Activation timestamp/date (Android)
- `expiration`/`expirationDate`: Expiration timestamp/date (iOS)

## Quick Start

```dart
import 'package:flutter_iap/flutter_iap.dart';

final flutterIap = FlutterIap();

// Initialize connection (Android only)
if (Platform.isAndroid) {
  await flutterIap.initConnection();
}

// Check subscription status
final status = await flutterIap.checkSubscriptionStatus();
print('Is Premium: ${status.isPro}');
print('Is Subscribed: ${status.isSubscribed}');

// Fetch subscriptions
final subscriptions = await flutterIap.fetchSubscriptions(['premium_monthly']);
for (var product in subscriptions) {
  print('${product.title}: ${product.price}');
}

// Purchase a product
await flutterIap.purchaseProduct('premium_monthly');

// Restore purchases
await flutterIap.restorePurchases();
```

## Documentation

For detailed usage instructions, examples, and platform-specific notes, see [USAGE.md](USAGE.md).

## Getting Started

This project is a starting point for a Flutter
[plug-in package](https://flutter.dev/to/develop-plugins),
a specialized package that includes platform-specific implementation code for
Android and/or iOS.

For help getting started with Flutter development, view the
[online documentation](https://docs.flutter.dev), which offers tutorials,
samples, guidance on mobile development, and a full API reference.

