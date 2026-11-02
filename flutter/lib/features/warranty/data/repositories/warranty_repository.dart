import 'package:dio/dio.dart';
import 'package:ecommerce_app/core/network/api_client.dart';
import '../models/warranty_model.dart';

class WarrantyRepository {
  final Dio client = ApiClient.dio;

  Future<List<WarrantyModel>> fetchMyWarranties() async {
    final resp = await client.get('/api/warranties/mine');
    if (resp.statusCode != 200) return [];

    final data = resp.data;
    if (data is Map && data['content'] is List) {
      return (data['content'] as List)
          .map((e) => WarrantyModel.fromJson(Map<String, dynamic>.from(e as Map)))
          .toList();
    }
    if (data is List) {
      return data
          .map((e) => WarrantyModel.fromJson(Map<String, dynamic>.from(e as Map)))
          .toList();
    }
    return [];
  }

  Future<WarrantyModel> lookupMyWarranty(String code) async {
    final resp = await client.get('/api/warranties/mine/lookup', queryParameters: {'code': code});
    if (resp.statusCode == 200) {
      return WarrantyModel.fromJson(Map<String, dynamic>.from(resp.data as Map));
    }
    throw Exception('Không tìm thấy thiết bị bảo hành');
  }

  Future<WarrantyModel> fetchWarrantyDetail(String warrantyId) async {
    final resp = await client.get('/api/warranties/mine/$warrantyId');
    if (resp.statusCode == 200) {
      return WarrantyModel.fromJson(Map<String, dynamic>.from(resp.data as Map));
    }
    throw Exception('Không thể lấy chi tiết bảo hành');
  }

  Future<WarrantyTicketModel> createWarrantyTicket({
    required String warrantyId,
    required String issueDescription,
  }) async {
    final resp = await client.post(
      '/api/warranties/mine/$warrantyId/tickets',
      data: {'issueDescription': issueDescription},
    );

    if (resp.statusCode == 200 || resp.statusCode == 201) {
      return WarrantyTicketModel.fromJson(Map<String, dynamic>.from(resp.data as Map));
    }
    throw Exception('Không thể tạo yêu cầu bảo hành');
  }

  Future<List<WarrantyTicketModel>> fetchMyTickets() async {
    final resp = await client.get('/api/warranties/mine/tickets');
    if (resp.statusCode != 200) return [];

    final data = resp.data;
    if (data is Map && data['content'] is List) {
      return (data['content'] as List)
          .map((e) => WarrantyTicketModel.fromJson(Map<String, dynamic>.from(e as Map)))
          .toList();
    }
    if (data is List) {
      return data
          .map((e) => WarrantyTicketModel.fromJson(Map<String, dynamic>.from(e as Map)))
          .toList();
    }
    return [];
  }
}
