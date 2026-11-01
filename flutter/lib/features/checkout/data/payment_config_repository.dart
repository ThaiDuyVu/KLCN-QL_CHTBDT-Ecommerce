import 'package:dio/dio.dart';
import '../../../core/network/api_client.dart';

class PaymentConfigRepository {
  final Dio client;
  PaymentConfigRepository({Dio? client}) : client = client ?? ApiClient.dio;

  /// Backend trả `{"enabled": true|false}`; lỗi mạng hoặc dữ liệu lạ coi như VNPAY đang tắt.
  Future<bool> isVnpayEnabled() async {
    try {
      final response = await client.get('/api/payments/vnpay/config');
      final data = response.data;
      return data is Map && data['enabled'] == true;
    } catch (_) {
      return false;
    }
  }
}
