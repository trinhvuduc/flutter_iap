/// Product details model for in-app purchases
class ProductDetails {
  /// Product identifier
  final String id;
  
  /// Product title/display name
  final String title;
  
  /// Product description
  final String description;
  
  /// Formatted price string (e.g., "$9.99")
  final String price;
  
  /// Raw price value in micros (Android) or decimal (iOS)
  final num? rawPrice;
  
  /// Base plan identifier (Android only)
  final String? basePlanId;

  const ProductDetails({
    required this.id,
    required this.title,
    required this.description,
    required this.price,
    required this.rawPrice,
    this.basePlanId,
  });

  /// Create ProductDetails from a Map
  factory ProductDetails.fromMap(Map<String, dynamic> map) {
    return ProductDetails(
      id: map['id'] as String,
      title: map['title'] as String? ?? '',
      description: map['description'] as String? ?? '',
      price: map['price'] as String? ?? '',
      rawPrice: map['rawPrice'] as num?,
      basePlanId: map['basePlanId'] as String?,
    );
  }

  /// Convert ProductDetails to a Map
  Map<String, dynamic> toMap() {
    return {
      'id': id,
      'title': title,
      'description': description,
      'price': price,
      'rawPrice': rawPrice,
      'basePlanId': basePlanId,
    };
  }

  @override
  String toString() {
    return 'ProductDetails(id: $id, title: $title, description: $description, price: $price, rawPrice: $rawPrice${basePlanId != null ? ', basePlanId: $basePlanId' : ''})';
  }

  @override
  bool operator ==(Object other) {
    if (identical(this, other)) return true;
    return other is ProductDetails &&
        other.id == id &&
        other.title == title &&
        other.description == description &&
        other.price == price &&
        other.rawPrice == rawPrice &&
        other.basePlanId == basePlanId;
  }

  @override
  int get hashCode {
    return Object.hash(
      id,
      title,
      description,
      price,
      rawPrice,
      basePlanId,
    );
  }
}

/// Subscription status model
class SubscriptionStatus {
  /// Subscription status: "subscribed", "notSubscribed"
  final String status;
  
  /// Whether user has premium access
  final bool isPro;
  
  /// Timestamp when subscription was activated (Android)
  final num? activation;
  
  /// Timestamp when subscription expires (iOS)
  final num? expiration;

  const SubscriptionStatus({
    required this.status,
    required this.isPro,
    this.activation,
    this.expiration,
  });

  /// Create SubscriptionStatus from a Map
  factory SubscriptionStatus.fromMap(Map<String, dynamic> map) {
    return SubscriptionStatus(
      status: map['status'] as String? ?? 'unknown',
      isPro: map['isPro'] as bool? ?? false,
      activation: map['activation'] as num?,
      expiration: map['expiration'] as num?,
    );
  }

  /// Convert SubscriptionStatus to a Map
  Map<String, dynamic> toMap() {
    return {
      'status': status,
      'isPro': isPro,
      'activation': activation,
      'expiration': expiration,
    };
  }

  /// Check if the subscription is active
  bool get isSubscribed => status == 'subscribed';

  /// Get activation date (Android)
  DateTime? get activationDate => activation != null 
      ? DateTime.fromMillisecondsSinceEpoch(activation!.toInt())
      : null;

  /// Get expiration date (iOS)
  DateTime? get expirationDate => expiration != null 
      ? DateTime.fromMillisecondsSinceEpoch((expiration! * 1000).toInt())
      : null;

  @override
  String toString() {
    return 'SubscriptionStatus(status: $status, isPro: $isPro${activation != null ? ', activation: $activationDate' : ''}${expiration != null ? ', expiration: $expirationDate' : ''})';
  }

  @override
  bool operator ==(Object other) {
    if (identical(this, other)) return true;
    return other is SubscriptionStatus &&
        other.status == status &&
        other.isPro == isPro &&
        other.activation == activation &&
        other.expiration == expiration;
  }

  @override
  int get hashCode {
    return Object.hash(
      status,
      isPro,
      activation,
      expiration,
    );
  }
}
