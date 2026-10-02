import 'package:dio/dio.dart';
import 'package:ecommerce_app/core/network/api_client.dart';

class ProfileRepository {
  final Dio client = ApiClient.dio;

  Future<Map<String, dynamic>> getProfile() async {
    final resp = await client.get('/api/auth/me');
    if (resp.statusCode == 200) {
      if (resp.data is Map) {
        return Map<String, dynamic>.from(resp.data as Map);
      }
    }
    throw Exception('Không thể lấy thông tin người dùng');
  }

  Future<void> logout() async {
    await client.post('/api/auth/logout');
  }
}
