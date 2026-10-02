import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../data/repositories/order_repository.dart';
import '../../data/models/order_model.dart';

final orderRepositoryProvider = Provider((ref) => OrderRepository());

final myOrdersProvider = FutureProvider<List<Order>>((ref) {
  return ref.read(orderRepositoryProvider).fetchMyOrders();
});

final orderDetailProvider = FutureProvider.family<Order, String>((ref, orderId) {
  return ref.read(orderRepositoryProvider).fetchOrderDetail(orderId);
});
