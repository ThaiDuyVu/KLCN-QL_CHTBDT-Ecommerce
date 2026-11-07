import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:ecommerce_app/core/network/api_client.dart';

/// One line of a return request (backend `ReturnResponse.Line`).
class ReturnLine {
  final String orderItemId;
  final String productName;
  final String? serialNumber;
  final int quantity;

  const ReturnLine({required this.orderItemId, required this.productName, this.serialNumber, required this.quantity});

  factory ReturnLine.fromJson(Map<String, dynamic> json) => ReturnLine(
        orderItemId: (json['orderItemId'] ?? '').toString(),
        productName: (json['productName'] ?? '').toString(),
        serialNumber: json['serialNumber']?.toString(),
        quantity: ((json['quantity'] ?? 0) as num).toInt(),
      );
}

/// A customer's return request (backend `ReturnResponse`).
class ReturnRequestSummary {
  final String id;
  final String code;
  final String status;
  final double refundAmount;
  final String? requestDate;
  final String reason;
  final List<ReturnLine> items;
  /// RETURN (refund) or EXCHANGE (other products, V14).
  final String requestType;
  final String? exchangeOrderCode;

  const ReturnRequestSummary({
    required this.id,
    required this.code,
    required this.status,
    required this.refundAmount,
    this.requestDate,
    required this.reason,
    required this.items,
    this.requestType = 'RETURN',
    this.exchangeOrderCode,
  });

  static String statusLabel(String value) {
    switch (value.toUpperCase()) {
      case 'REQUESTED':
        return 'Chờ duyệt';
      case 'APPROVED':
        return 'Đã duyệt, chờ nhận hàng';
      case 'REJECTED':
        return 'Từ chối';
      case 'RECEIVED':
        return 'Đã nhận hàng, chờ xử lý';
      case 'REFUNDED':
        return 'Đã hoàn tiền';
      case 'EXCHANGED':
        return 'Đã đổi hàng';
      default:
        return value;
    }
  }

  factory ReturnRequestSummary.fromJson(Map<String, dynamic> json) => ReturnRequestSummary(
        id: (json['returnId'] ?? '').toString(),
        code: (json['returnCode'] ?? '').toString(),
        status: (json['status'] ?? 'REQUESTED').toString().toUpperCase(),
        refundAmount: ((json['refundAmount'] ?? 0) as num).toDouble(),
        requestDate: json['requestDate']?.toString(),
        reason: (json['reason'] ?? '').toString(),
        items: ((json['items'] ?? const <dynamic>[]) as List)
            .map((e) => ReturnLine.fromJson(Map<String, dynamic>.from(e as Map)))
            .toList(),
        requestType: (json['requestType'] ?? 'RETURN').toString().toUpperCase(),
        exchangeOrderCode: json['exchangeOrderCode']?.toString(),
      );
}

/// A product the customer may pick in exchange (backend variant search).
class ExchangeCandidate {
  final String variantId;
  final String sku;
  final String productName;
  final double price;

  const ExchangeCandidate({required this.variantId, required this.sku, required this.productName, required this.price});

  factory ExchangeCandidate.fromJson(Map<String, dynamic> json) => ExchangeCandidate(
        variantId: (json['variantId'] ?? '').toString(),
        sku: (json['sku'] ?? '').toString(),
        productName: (json['productName'] ?? '').toString(),
        price: ((json['effectivePrice'] ?? json['price'] ?? 0) as num).toDouble(),
      );
}

class ReturnRepository {
  final Dio client = ApiClient.dio;

  Future<List<ReturnRequestSummary>> forOrder(String orderId) async {
    final resp = await client.get('/api/returns/mine/by-order/$orderId');
    final data = resp.data;
    if (data is! List) return [];
    return data.map((e) => ReturnRequestSummary.fromJson(Map<String, dynamic>.from(e as Map))).toList();
  }

  Future<List<ExchangeCandidate>> searchVariants(String keyword) async {
    final resp = await client.get('/api/v1/product-variants', queryParameters: {'page': 0, 'size': 10, 'keyword': keyword});
    final content = resp.data is Map ? (resp.data as Map)['content'] : null;
    if (content is! List) return [];
    return content.map((e) => ExchangeCandidate.fromJson(Map<String, dynamic>.from(e as Map))).toList();
  }

  /// [quantities]: orderItemId → number of units to return. [exchange]: variantId → quantity wanted instead (EXCHANGE).
  Future<ReturnRequestSummary> create(String orderId, String reason, Map<String, int> quantities,
      {Map<String, int> exchange = const {}}) async {
    final resp = await client.post('/api/returns/mine', data: {
      'orderId': orderId,
      'reason': reason,
      'items': quantities.entries.map((e) => {'orderItemId': e.key, 'quantity': e.value}).toList(),
      if (exchange.isNotEmpty) 'type': 'EXCHANGE',
      if (exchange.isNotEmpty) 'exchangeItems': exchange.entries.map((e) => {'variantId': e.key, 'quantity': e.value}).toList(),
    });
    return ReturnRequestSummary.fromJson(Map<String, dynamic>.from(resp.data as Map));
  }
}

final returnRepositoryProvider = Provider((ref) => ReturnRepository());

final orderReturnsProvider = FutureProvider.family<List<ReturnRequestSummary>, String>((ref, orderId) {
  return ref.read(returnRepositoryProvider).forOrder(orderId);
});
