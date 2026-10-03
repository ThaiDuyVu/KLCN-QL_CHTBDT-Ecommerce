import 'package:dio/dio.dart';
import 'package:ecommerce_app/core/network/api_client.dart';
import '../models/warranty_model.dart';

class WarrantyRepository {
  final Dio client = ApiClient.dio;

  Future<List<WarrantyTicket>> fetchMyWarranty() async {
    final resp = await client.get('/api/warranties/mine');
    if (resp.statusCode != 200) return [];

    final data = resp.data;
    if (data is List) {
      return data
          .map((e) => WarrantyTicket.fromJson(Map<String, dynamic>.from(e as Map)))
          .toList();
    }
    if (data is Map && data['content'] is List) {
      return (data['content'] as List)
          .map((e) => WarrantyTicket.fromJson(Map<String, dynamic>.from(e as Map)))
          .toList();
    }
    return [];
  }

  Future<WarrantyTicket> createWarrantyTicket({
    required String productId,
    required String issue,
    required String orderItemId,
  }) async {
    final resp = await client.post('/api/warranties', data: {
      'productId': productId,
      'issue': issue,
      'orderItemId': orderItemId,
    });

    if (resp.statusCode == 200 || resp.statusCode == 201) {
      return WarrantyTicket.fromJson(Map<String, dynamic>.from(resp.data as Map));
    }
    throw Exception('Không thể tạo yêu cầu bảo hành');
  }
}
