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
  try {
    final response = await ApiClient.dio.get(
      '/api/v1/products/$id',
      queryParameters: {
        if (warehouseId != null && warehouseId.isNotEmpty) 'warehouseId': warehouseId,
      },
    );

    // 1. Thêm dòng in ra log để bạn xem Backend Neon thực sự trả về chữ gì
    print('=== DỮ LIỆU CHI TIẾT SẢN PHẨM ($id): ${response.data} ===');

    // 2. Trả về đối tượng sản phẩm sau khi parse dữ liệu thành công
    return Product.fromJson(Map<String, dynamic>.from(response.data as Map));

  } catch (e) {
    // 3. In ra log lỗi nếu API hoặc quá trình parse dữ liệu bị sập
    print('=== LỖI HÀM GET_PRODUCT: $e ===');
    throw Exception('Lỗi lấy chi tiết sản phẩm: $e');
  }
  }}

