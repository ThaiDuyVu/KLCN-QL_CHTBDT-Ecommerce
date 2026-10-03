import 'package:dio/dio.dart';
import '../../../core/network/api_client.dart';

class CartRepository {

  Future<Response> fetchCart() async {
    return await ApiClient.dio.get('/api/cart');
  }

  Future<Response> addItem({
    required String productId,
    required int quantity,
    required String warehouseId,
  }) async {
    return await ApiClient.dio.post('/api/cart/items', data: {
      'productId': productId,
      'quantity': quantity,
      'warehouseId': warehouseId,
    });
  }

  Future<Response> updateItem(String itemId, int quantity) async {
    return await ApiClient.dio.put('/api/cart/items/$itemId', data: {'quantity': quantity});
  }

  Future<Response> removeItem(String itemId) async {
    return await ApiClient.dio.delete('/api/cart/items/$itemId');
  }

  Future<Response> clearCart() async {
    return await ApiClient.dio.delete('/api/cart');
  }

  Future<Response> checkout(Map<String, dynamic> payload) async {
    return await ApiClient.dio.post('/api/orders/checkout', data: payload);
  }
}