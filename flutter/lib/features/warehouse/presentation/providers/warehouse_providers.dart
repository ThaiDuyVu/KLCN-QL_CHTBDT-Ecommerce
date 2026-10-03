import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../data/models/warehouse.dart';
import '../../data/repositories/warehouse_repository.dart';

final warehouseRepositoryProvider = Provider((ref) => WarehouseRepository());

final warehouseListProvider = FutureProvider<List<Warehouse>>((ref) async {
  final repo = ref.read(warehouseRepositoryProvider);
  return repo.fetchWarehouses();
});

final selectedWarehouseIdProvider = StateProvider<String?>((ref) => null);

class SelectedWarehouseNotifier extends StateNotifier<Warehouse?> {
  final Ref _ref;

  SelectedWarehouseNotifier(this._ref) : super(null);

  void select(Warehouse? w) {
    state = w;
    _ref.read(selectedWarehouseIdProvider.notifier).state = w?.id;
  }

  void clear() {
    state = null;
    _ref.read(selectedWarehouseIdProvider.notifier).state = null;
  }
}

final selectedWarehouseProvider = StateNotifierProvider<SelectedWarehouseNotifier, Warehouse?>((ref) {
  return SelectedWarehouseNotifier(ref);
});
