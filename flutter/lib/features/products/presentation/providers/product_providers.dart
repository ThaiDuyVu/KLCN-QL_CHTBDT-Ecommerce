import '../../../auth/presentation/providers/auth_provider.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../data/repositories/product_repository.dart';
import '../../data/models/product_model.dart'; // Đừng quên import Model
import '../../../warehouse/presentation/providers/warehouse_providers.dart';

// Provider cung cấp ProductRepository
final productRepositoryProvider = Provider<ProductRepository>((ref) {
  return ProductRepository();
});

final productListProvider = FutureProvider<List<Product>>((ref) async {
  ref.watch(authProvider.select((state) => state.user?['userId']));

  // Dùng ID thuần túy để truyền chính xác vào API
  final selectedWarehouseId = ref.watch(selectedWarehouseIdProvider);

  final repo = ref.read(productRepositoryProvider);
  return readWithSession(
    ref,
    () => repo.getProducts(selectedWarehouseId),
  );
});
final productDetailProvider = FutureProvider.autoDispose
    .family<Product, String>((ref, id) {
      final warehouse = ref.watch(selectedWarehouseProvider);
      return readWithSession(
        ref,
        () => ref.read(productRepositoryProvider).getProduct(id, warehouse?.id),
      );
    });
