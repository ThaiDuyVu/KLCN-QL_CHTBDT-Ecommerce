import 'package:ecommerce_app/core/network/api_client.dart';
import '../models/product_model.dart';

class ProductRepository {
  Future<List<Product>> getProducts(String? warehouseId) async {
    final products = <Product>[];
    var page = 0;
    int totalPages;
    do {
      final response = await ApiClient.dio.get(
        '/api/v1/products',
        queryParameters: {
          if (warehouseId != null) 'warehouseId': warehouseId,
          'status': 'ACTIVE',
          'page': page,
          'size': 100,
        },
      );
      final data = response.data as Map<String, dynamic>;
      products.addAll(
        (data['content'] as List).map(
          (item) => Product.fromJson(Map<String, dynamic>.from(item as Map)),
        ),
      );
      totalPages = (data['totalPages'] as num).toInt();
      page++;
    } while (page < totalPages);
    return products;
  }

  Future<Product> getProduct(String id, String? warehouseId) async {
    final response = await ApiClient.dio.get(
      '/api/v1/products/$id',
      queryParameters: {if (warehouseId != null) 'warehouseId': warehouseId},
    );
    return Product.fromJson(Map<String, dynamic>.from(response.data as Map));
  }
}
