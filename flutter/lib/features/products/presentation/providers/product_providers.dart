import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../data/repositories/product_repository.dart';
import '../../data/models/product_model.dart'; // Đừng quên import Model
import '../../../warehouse/presentation/providers/warehouse_providers.dart';

// Provider cung cấp ProductRepository
final productRepositoryProvider = Provider<ProductRepository>((ref) {
  return ProductRepository();
});

final productListProvider = FutureProvider<List<Product>>((ref) async {
  // Lắng nghe chi nhánh đang chọn từ màn hình
  final selected = ref.watch(selectedWarehouseProvider);
  
  // Nếu chưa chọn chi nhánh nào, trả về danh sách trống
  if (selected == null) {
    return [];
  }

  // Gọi API lấy sản phẩm theo ID chi nhánh
  final repo = ref.read(productRepositoryProvider);
  return repo.getProducts(selected.id);
});