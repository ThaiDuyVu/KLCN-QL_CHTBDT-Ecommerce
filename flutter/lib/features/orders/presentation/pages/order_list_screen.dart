import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'order_detail_screen.dart';
import '../providers/order_providers.dart';
import '../../data/models/order_model.dart';

class OrderListScreen extends ConsumerWidget {
  const OrderListScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final ordersAsync = ref.watch(myOrdersProvider);

    return Scaffold(
      appBar: AppBar(title: const Text('Đơn hàng của tôi')),
      body: ordersAsync.when(
        data: (orders) {
          if (orders.isEmpty) {
            return const Center(child: Text('Bạn chưa có đơn hàng nào.'));
          }

          return ListView.separated(
            padding: const EdgeInsets.all(12),
            itemCount: orders.length,
            separatorBuilder: (_, _) => const SizedBox(height: 8),
            itemBuilder: (context, index) {
              final order = orders[index];
              final statusText = Order.statusLabel(order.status);

              return Card(
                child: ListTile(
                  title: Text('Đơn ${order.orderCode.isNotEmpty ? order.orderCode : order.id}'),
                  subtitle: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      const SizedBox(height: 6),
                      Text('Ngày đặt: ${order.createdAt ?? '---'}'),
                      Text('Tổng tiền: ${order.totalAmount.toStringAsFixed(0)} đ'),
                      if ((order.warehouseName ?? '').isNotEmpty) Text('Chi nhánh: ${order.warehouseName}'),
                      const SizedBox(height: 4),
                      Text('Trạng thái: $statusText'),
                    ],
                  ),
                  trailing: order.canCancel
                      ? IconButton(
                          icon: const Icon(Icons.cancel_outlined),
                          tooltip: 'Hủy đơn',
                          onPressed: () async {
                            try {
                              await ref.read(orderRepositoryProvider).cancelOrder(order.id);
                              if (context.mounted) {
                                ScaffoldMessenger.of(context).showSnackBar(
                                  const SnackBar(content: Text('Đã gửi yêu cầu hủy đơn.')),
                                );
                              }
                              ref.invalidate(myOrdersProvider);
                            } on DioException catch (e) {
                              final message = e.response?.data is Map
                                  ? (e.response?.data['message'] ?? 'Không thể hủy đơn')
                                  : 'Không thể hủy đơn';
                              if (context.mounted) {
                                ScaffoldMessenger.of(context).showSnackBar(
                                  SnackBar(content: Text(message.toString()), backgroundColor: Colors.red),
                                );
                              }
                            } catch (e) {
                              if (context.mounted) {
                                ScaffoldMessenger.of(context).showSnackBar(
                                  SnackBar(content: Text('Không thể hủy đơn: $e'), backgroundColor: Colors.red),
                                );
                              }
                            }
                          },
                        )
                      : const Icon(Icons.check_circle_outline, color: Colors.green),
                  onTap: () => Navigator.push(
                    context,
                    MaterialPageRoute(builder: (_) => OrderDetailScreen(orderId: order.id)),
                  ),
                ),
              );
            },
          );
        },
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (error, stack) => Center(
          child: Padding(
            padding: const EdgeInsets.all(16),
            child: Text('Lỗi tải đơn hàng: ${error.toString()}'),
          ),
        ),
      ),
    );
  }
}
