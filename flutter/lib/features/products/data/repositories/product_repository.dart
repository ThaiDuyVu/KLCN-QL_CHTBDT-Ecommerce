import 'package:ecommerce_app/core/network/api_client.dart';
import 'package:ecommerce_app/features/products/data/models/product_model.dart';

class ProductRepository {
  final client = ApiClient.dio; 

  Future<List<Product>> getProducts(String warehouseId) async {
    try {
      final response = await client.get(
        '/api/v1/products',
        queryParameters: {'warehouseId': warehouseId},
      );

      // Bóc tách lớp vỏ 'content' (hoặc 'data') của Spring Boot
      List<dynamic> listData = [];
      if (response.data is Map<String, dynamic>) {
        final mapData = response.data as Map<String, dynamic>;
        listData = mapData['content'] ?? mapData['data'] ?? mapData['result'] ?? [];
      } else if (response.data is List) {
        listData = response.data;
      }

      // Ép kiểu JSON vào class Product
      print('📦 JSON SẢN PHẨM ĐẦU TIÊN: ${listData.isNotEmpty ? listData.first : "Trống"}');
      return listData.map((json) => Product.fromJson(json as Map<String, dynamic>)).toList();
      
    } catch (e) {
      throw Exception('Lỗi khi lấy danh sách sản phẩm: $e');
    }
  }
}