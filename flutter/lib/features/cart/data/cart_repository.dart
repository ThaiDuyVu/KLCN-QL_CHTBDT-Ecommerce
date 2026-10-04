import 'package:dio/dio.dart';
import '../../../core/network/api_client.dart';

class CartRepository {
  final Dio client;
  CartRepository({Dio? client}) : client = client ?? ApiClient.dio;
  Future<Response> fetchCart() async {
    return await client.get('/api/cart');
  }

  Future<Response> addItem({
    required String variantId,
    required int quantity,
  }) async {
    return await client.post(
      '/api/cart/items',
      data: {'variantId': variantId, 'quantity': quantity},
    );
  }

  Future<Response> updateItem(String itemId, int quantity) async {
    return await client.patch(
      '/api/cart/items/$itemId',
      data: {'quantity': quantity},
    );
  }

  Future<Response> removeItem(String itemId) async {
    return await client.delete('/api/cart/items/$itemId');
  }

  Future<Response> selectWarehouse(
    String warehouseId, {
    bool clearItems = false,
  }) {
    return client.put(
      '/api/cart/warehouse',
      data: {'warehouseId': warehouseId, 'clearItems': clearItems},
    );
  }

  Future<Response> checkout(Map<String, dynamic> payload) async {
    return await client.post('/api/orders/checkout', data: payload);
  }
}
