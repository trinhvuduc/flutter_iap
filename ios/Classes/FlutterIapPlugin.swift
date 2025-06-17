// Author: phongnd

import Flutter
import UIKit
import StoreKit

public class FlutterIAPPlugin: NSObject, FlutterPlugin {
  
    enum MethodChannelName: String {
        case checkSubscriptionStatus
        case fetchSubscriptions
        case purchaseProduct
        case restorePurchases
    }
    
    private var methodChannel: FlutterMethodChannel?
  
    public static func register(with registrar: FlutterPluginRegistrar) {
        let channel = FlutterMethodChannel(name: "flutter_iap", binaryMessenger: registrar.messenger())
        let instance = FlutterIAPPlugin()
        instance.methodChannel = channel
        
        Task {
            await MainActor.run {
                IAPManager.shared.startTransactionListener(methodChannel: channel)
            }
        }
        
        registrar.addMethodCallDelegate(instance, channel: channel)
    }

    public func handle(_ call: FlutterMethodCall, result: @escaping FlutterResult) {
        switch call.method {
        case MethodChannelName.checkSubscriptionStatus.rawValue:
            Task {
                let status = await IAPManager.shared.checkSubscriptionStatus()
                switch status {
                case .subscribed(let expirationDate, _):
                    result(["status": "subscribed", "isPro": true, "expiration": expirationDate.timeIntervalSince1970])
                case .notSubscribed:
                    result(["status": "notSubscribed", "isPro": false])
                }
            }
        case MethodChannelName.fetchSubscriptions.rawValue:
            if let args = call.arguments as? [String: Any],
               let productIDs = args["productIDs"] as? [String] {
                Task {
                    do {
                        let products = try await IAPManager.shared.fetchSubscriptions(with: productIDs)
                        let productData = products.map { product in
                            [
                                "id": product.id,
                                "title": product.displayName,
                                "description": product.description,
                                "price": product.displayPrice,
                                "rawPrice": product.price
                            ] as [String: Any]
                        }
                        result(productData)
                    } catch {
                        print("SWIFT: Error fetching products: \(error)")
                        result(FlutterError(code: "FETCH_ERROR", message: error.localizedDescription, details: nil))
                    }
                }
            } else {
                result(FlutterError(code: "INVALID_ARGUMENTS", message: "Invalid arguments. Expected productIDs as [String]", details: nil))
            }
        case MethodChannelName.purchaseProduct.rawValue:
            if let args = call.arguments as? [String: Any],
               let productId = args["productId"] as? String {
                Task {
                    if let product = await IAPManager.shared.fetchProduct(with: productId) {
                        let isSuccess = try? await IAPManager.shared.purchase(product: product)
                        result(isSuccess)
                    } else {
                        result(FlutterError(code: "PRODUCT_NOT_FOUND", message: "Product not found", details: nil))
                    }
                }
            } else {
                result(FlutterError(code: "INVALID_ARGUMENTS", message: "Invalid arguments", details: nil))
            }
        case MethodChannelName.restorePurchases.rawValue:
            Task {
                let restoreResult = await IAPManager.shared.restorePurchases()
                switch restoreResult {
                case .success(let restored):
                    result(restored)
                case .failure(let error):
                    result(FlutterError(code: "RESTORE_FAILED", message: error.localizedDescription, details: nil))
                }
            }
        default:
            result(FlutterMethodNotImplemented)
        }
    }
}

// Enum để thể hiện trạng thái đăng ký của người dùng
@available(iOS 15.0, *)
enum SubscriptionStatus {
    case subscribed(expirationDate: Date, transaction: Transaction)
    case notSubscribed
}

enum MethodChannelCallback: String {
    case onPurchaseSuccess
    case onPurchaseError
}

@MainActor
final class IAPManager {
    static let shared = IAPManager()
    private init() {}

    private var transactionListenerTask: Task<Void, Never>?
    private weak var methodChannel: FlutterMethodChannel?
    
    // Bắt đầu lắng nghe các cập nhật giao dịch
    func startTransactionListener(methodChannel: FlutterMethodChannel) {
        self.methodChannel = methodChannel
        
        transactionListenerTask = Task.detached(priority: .background) {
            for await result in Transaction.updates {
                do {
                    let transaction = try await self.checkVerified(result)
                                        
                    let subscriptionStatus = await self.checkSubscriptionStatus()
                    
                    switch subscriptionStatus {
                    case .subscribed(_, let transaction):
                        print("SWIFT: Người dùng đã có subscription hợp lệ: \(transaction.productID)")
                        // Call Flutter success callback
                        await MainActor.run {
                            self.methodChannel?.invokeMethod(MethodChannelCallback.onPurchaseSuccess.rawValue, arguments: [
                                "status": "subscribed",
                                "isPro": true,
                                "expiration": transaction.expirationDate?.timeIntervalSince1970 ?? 0
                            ])
                        }
                        break
                        
                    case .notSubscribed:
                        print("SWIFT: Người dùng không có subscription hợp lệ.")
                        // Call Flutter success callback
                        await MainActor.run {
                            self.methodChannel?.invokeMethod(MethodChannelCallback.onPurchaseSuccess.rawValue, arguments: [
                                "status": "notSubscribed",
                                "isPro": false
                            ])
                        }
                        break
                    }
                     
                    await transaction.finish()
                    print("SWIFT: ✅ Transaction update (listener): \(transaction.productID)")
                } catch {
                    print("SWIFT: ❌ Transaction update error: \(error)")
                    
                    // Call Flutter error callback
                    await MainActor.run {
                        self.methodChannel?.invokeMethod(MethodChannelCallback.onPurchaseError.rawValue, arguments: [
                            "error": error.localizedDescription,
                            "code": "TRANSACTION_UPDATE_ERROR"
                        ])
                    }
                }
            }
        }
    }
    
    // Kiểm tra kết quả xác minh giao dịch
    private func checkVerified<T>(_ result: VerificationResult<T>) throws -> T {
        switch result {
        case .verified(let signedType):
            return signedType
        case .unverified(_, let error):
            throw error // Hoặc throw một error tùy biến của bạn
        }
    }

    // Lấy danh sách các gói subscription khả dụng (kèm thông tin trial)
    func fetchSubscriptions(with productIDs: [String]) async throws -> [Product] {
        let products = try await Product.products(for: productIDs)
        return products.filter { $0.type == .autoRenewable }
    }
    
    // Lấy thông tin sản phẩm theo productID
    func fetchProduct(with productID: String) async -> Product? {
        do {
            let products = try await Product.products(for: [productID])
            return products.first
        } catch {
            print("SWIFT: ❌ Lỗi khi lấy sản phẩm \(productID): \(error)")
            return nil
        }
    }

    // Kiểm tra trạng thái subscription hiện tại
    func checkSubscriptionStatus() async -> SubscriptionStatus {
        var latestTransaction: Transaction?
        var latestExpirationDate: Date?
        
        for await result in Transaction.currentEntitlements {
            guard case .verified(let transaction) = result else {
                continue
            }

            if transaction.productType == .autoRenewable &&
                transaction.revocationDate == nil {
                // Tìm giao dịch có ngày hết hạn xa nhất (đảm bảo quyền lợi cao nhất)
                if let expirationDate = transaction.expirationDate {
                    if latestExpirationDate == nil || expirationDate > latestExpirationDate! {
                        latestExpirationDate = expirationDate
                        latestTransaction = transaction
                    }
                }
            }
        }

        // Sau khi đã kiểm tra tất cả các giao dịch, giờ mới quyết định trạng thái
        if let finalTransaction = latestTransaction, let finalExpirationDate = latestExpirationDate {
            // Nếu có giao dịch hợp lệ && ngày hết hạn còn hiệu lực && người dùng không refund
            let isRefund = await self.checkRefunded()
            if finalExpirationDate > Date() && !isRefund {
                print("SWIFT: Người dùng đã đăng ký và còn hạn")
                return .subscribed(expirationDate: finalExpirationDate, transaction: finalTransaction)
            }
        }
        print("SWIFT: không tìm thấy giao dịch hợp lệ nào hoặc tất cả đã hết hạn")
        return .notSubscribed
    }

    // Kiểm tra đã từng dùng trial chưa
    func hasUsedTrial(productID: String) async -> Bool {
        for await result in Transaction.all {
            if case .verified(let transaction) = result,
               transaction.productID == productID,
               let offerType = transaction.offerType,
               offerType == .introductory {
                return true // Đã từng dùng trial
            }
        }
        return false // Chưa từng dùng trial
    }

    // Phát hiện refund
    func checkRefunded() async -> Bool {
        for await result in Transaction.currentEntitlements {
            if case .verified(let transaction) = result,
               transaction.revocationDate != nil {
                return true // Đã bị refund
            }
        }
        return false
    }

    // Mua gói
    func purchase(product: Product) async throws -> Bool {
        let result = try await product.purchase()
        switch result {
        case .success(let verification):
            switch verification {
            case .verified(let transaction):
                await transaction.finish()
                print("SWIFT: ✅ Mua thành công: \(transaction.productID)")
                return true // Mua thành công
            case .unverified(_, let error):
                print("SWIFT: ❌ Giao dịch không xác minh được: \(error)")
                return false // Giao dịch không xác minh được
            }

        case .userCancelled:
            print("SWIFT: ❌ Người dùng đã huỷ")
            return false // Người dùng đã huỷ giao dịch

        case .pending:
            print("SWIFT: ⏳ Đang chờ xác nhận")
            return false // Giao dịch đang chờ xác nhận
            
        @unknown default:
            return false // Trường hợp không xác định, trả về false
        }
    }

    func restorePurchases() async -> Result<Bool, Error> {
        do {
            try await AppStore.sync()
            let status = await checkSubscriptionStatus()
            
            switch status {
            case .subscribed:
                return .success(true)
            case .notSubscribed:
                return .failure(NSError(domain: "IAPManagerError", code: -1, userInfo: [NSLocalizedDescriptionKey: "Không có giao dịch nào để khôi phục."]))
            }
        } catch {
            print("SWIFT: Lỗi khôi phục: \(error)")
            return .failure(error)
        }
    }
}
