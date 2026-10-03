import 'package:dio/dio.dart';
import 'package:ecommerce_app/core/network/api_client.dart';
import '../models/order_model.dart';

class OrderRepository {
  final Dio client = ApiClient.dio;

  Future<List<Order>> fetchMyOrders() async {
    final resp = await client.get('/api/orders/mine');
    if (resp.statusCode != 200) return [];

    final data = resp.data;
    if (data is List) {
      return data.map((e) => Order.fromJson(Map<String, dynamic>.from(e as Map))).toList();
    }
    if (data is Map && data['content'] is List) {
      return (data['content'] as List)
          .map((e) => Order.fromJson(Map<String, dynamic>.from(e as Map)))
          .toList();
    }
    return [];
  }

  Future<Order> fetchOrderDetail(String orderId) async {
    final resp = await client.get('/api/orders/mine/$orderId');
    if (resp.statusCode == 200) {
      return Order.fromJson(Map<String, dynamic>.from(resp.data as Map));
    }
    throw Exception('Không thể lấy chi tiết đơn hàng');
  }

  Future<void> cancelOrder(String orderId) async {
    await client.post('/api/orders/mine/$orderId/cancel');
  }
}
