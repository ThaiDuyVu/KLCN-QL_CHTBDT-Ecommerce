import 'package:ecommerce_app/core/network/api_client.dart';
import 'package:ecommerce_app/features/warehouse/data/models/warehouse.dart';

class WarehouseRepository {
  WarehouseRepository();

  Future<List<Warehouse>> fetchWarehouses() async {
    try {
      final response = await ApiClient.dio.get('/api/warehouses');
      
      List<dynamic> listData;
      
      if (response.data is Map<String, dynamic>) {
        final mapData = response.data as Map<String, dynamic>;
        
        // Spring Boot Pageable thường trả list trong key 'content'
        if (mapData.containsKey('content')) {
          listData = mapData['content'];
        } 
        // Hoặc trả trong key 'data'
        else if (mapData.containsKey('data')) {
          listData = mapData['data'];
        } 
        else {
          throw Exception('Không tìm thấy mảng dữ liệu trong response: ${response.data}');
        }
      } else if (response.data is List) {
         listData = response.data;
      } else {
         throw Exception('Định dạng dữ liệu không hợp lệ');
      }

      return listData.map((json) => Warehouse.fromJson(json)).toList();
      
    } catch (e) {
      throw Exception('Lỗi khi lấy danh sách chi nhánh: $e');
    }
  }
}
