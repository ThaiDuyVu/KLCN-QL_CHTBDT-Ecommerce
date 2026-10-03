import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:ecommerce_app/features/cart/data/cart_repository.dart';

class CartLine {
  final String id;
  final String productId;
  final String productName;
  final int quantity;
  final double unitPrice;

  CartLine({
    required this.id,
    required this.productId,
    required this.productName,
    required this.quantity,
    required this.unitPrice,
  });

  factory CartLine.fromJson(Map<String, dynamic> json) {
    final product = json['product'];
    final name = json['productName'] ??
        (product is Map ? product['name'] : null) ??
        'Sản phẩm';

    return CartLine(
      id: (json['id'] ?? json['cartItemId'] ?? json['lineId'] ?? '').toString(),
      productId: (json['productId'] ?? json['product_id'] ?? '').toString(),
      productName: name.toString(),
      quantity: ((json['quantity'] ?? 0) as num).toInt(),
      unitPrice: ((json['unitPrice'] ?? json['price'] ?? 0) as num).toDouble(),
    );
  }
}

class CartSummary {
  final String? cartId;
  final String? warehouseId;
  final List<CartLine> items;
  final double subtotal;

  CartSummary({
    this.cartId,
    this.warehouseId,
    this.items = const [],
    this.subtotal = 0,
  });

  factory CartSummary.fromJson(Map<String, dynamic> json) {
    final rawItems = json['items'] ?? json['cartItems'] ?? json['content'] ?? const [];
    final items = (rawItems as List)
        .map((e) => CartLine.fromJson(Map<String, dynamic>.from(e as Map)))
        .toList();

    return CartSummary(
      cartId: json['cartId']?.toString() ?? json['id']?.toString(),
      warehouseId: json['warehouseId']?.toString() ?? json['warehouse_id']?.toString(),
      items: items,
      subtotal: ((json['subtotal'] ?? json['totalAmount'] ?? 0) as num).toDouble(),
    );
  }
}

final cartRepositoryProvider = Provider<CartRepository>((ref) => CartRepository());

final cartListProvider = FutureProvider.autoDispose<List<dynamic>>((ref) async {
  try {
    final repo = ref.read(cartRepositoryProvider);
    final response = await repo.fetchCart();
    final data = response.data;

    if (data is List) {
      return data;
    }
    if (data is Map<String, dynamic>) {
      final items = data['items'] ?? data['cartItems'] ?? data['content'] ?? const [];
      if (items is List) return items;
      return const [];
    }
    if (data is Map) {
      final items = data['items'] ?? data['cartItems'] ?? data['content'] ?? const [];
      if (items is List) return items;
      return const [];
    }
    return const [];
  } on DioException catch (e) {
    throw e;
  }
});

final cartProvider = FutureProvider.autoDispose<CartSummary>((ref) async {
  try {
    final repo = ref.read(cartRepositoryProvider);
    final response = await repo.fetchCart();
    final data = response.data;

    if (data is Map<String, dynamic>) {
      return CartSummary.fromJson(data);
    }
    if (data is Map) {
      return CartSummary.fromJson(Map<String, dynamic>.from(data));
    }
    return CartSummary(items: const []);
  } on DioException catch (e) {
    throw e;
  }
});