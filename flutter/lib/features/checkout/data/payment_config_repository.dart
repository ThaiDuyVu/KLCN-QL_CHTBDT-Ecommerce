import 'package:dio/dio.dart';
import '../../../core/network/api_client.dart';

class InstallmentProvider {
  final String id;
  final String name;

  const InstallmentProvider({required this.id, required this.name});

  factory InstallmentProvider.fromJson(Map<String, dynamic> json) {
    return InstallmentProvider(
      id: (json['providerId'] ?? '').toString(),
      name: (json['providerName'] ?? json['providerCode'] ?? '').toString(),
    );
  }
}

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

  /// Các đơn vị trả góp đang hoạt động (giống installmentApi.activeProviders của web).
  Future<List<InstallmentProvider>> fetchActiveInstallmentProviders() async {
    final response = await client.get('/api/installment-providers');
    final data = response.data;
    if (data is! List) return const [];
    return data
        .map((e) => InstallmentProvider.fromJson(Map<String, dynamic>.from(e as Map)))
        .where((p) => p.id.isNotEmpty)
        .toList();
  }
}
