import 'package:dio/dio.dart';
import '../../../../core/network/api_client.dart';
import 'address_model.dart';

class AddressRepository {
  final Dio client;
  AddressRepository({Dio? client}) : client = client ?? ApiClient.dio;

  static const String endpoint = '/api/customers/me/addresses';

  Future<List<AddressModel>> fetchAddresses() async {
    final res = await client.get(endpoint);
    final data = res.data;
    if (data is List) {
      return data
          .map((e) => AddressModel.fromJson(Map<String, dynamic>.from(e as Map)))
          .toList();
    }
    return const [];
  }

  Future<AddressModel> createAddress(Map<String, dynamic> payload) async {
    final res = await client.post(endpoint, data: payload);
    return AddressModel.fromJson(Map<String, dynamic>.from(res.data as Map));
  }

  Future<AddressModel> updateAddress(String addressId, Map<String, dynamic> payload) async {
    final res = await client.put('$endpoint/$addressId', data: payload);
    return AddressModel.fromJson(Map<String, dynamic>.from(res.data as Map));
  }

  Future<AddressModel> makeDefault(String addressId) async {
    final res = await client.patch('$endpoint/$addressId/default');
    return AddressModel.fromJson(Map<String, dynamic>.from(res.data as Map));
  }

  Future<void> deleteAddress(String addressId) async {
    await client.delete('$endpoint/$addressId');
  }
}

