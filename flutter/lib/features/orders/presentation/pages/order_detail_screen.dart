import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../providers/order_providers.dart';
import '../../data/models/order_model.dart';

class OrderDetailScreen extends ConsumerWidget {
  final String orderId;

  const OrderDetailScreen({super.key, required this.orderId});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final asyncOrder = ref.watch(orderDetailProvider(orderId));

    return Scaffold(
      appBar: AppBar(title: const Text('Chi tiết đơn hàng')),
      body: asyncOrder.when(
        data: (order) {
          final statusText = Order.statusLabel(order.status);
          final canCancel = order.allowedStatuses.contains('CANCELLED');

          return ListView(
            padding: const EdgeInsets.all(16),
            children: [
              Text('Mã đơn: ${order.orderCode.isNotEmpty ? order.orderCode : order.id}', style: const TextStyle(fontWeight: FontWeight.bold)),
              const SizedBox(height: 8),
              Text('Trạng thái: $statusText'),
              if (order.createdAt != null) Text('Ngày: ${order.createdAt}'),
              if ((order.warehouseName ?? '').isNotEmpty) Text('Chi nhánh: ${order.warehouseName}'),
              const SizedBox(height: 16),
              const Text('Sản phẩm', style: TextStyle(fontWeight: FontWeight.bold)),
              const SizedBox(height: 8),
              ...order.items.map((item) {
                final hasSerialOrImei = (item.serialNumber != null && item.serialNumber!.isNotEmpty) || item.imeiNumbers.isNotEmpty;

                return Card(
                  child: ListTile(
                    title: Text(item.productName),
                    subtitle: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text('Số lượng: ${item.quantity}'),
                        if (hasSerialOrImei) ...[
                          const SizedBox(height: 4),
                          Container(
                            padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                            decoration: BoxDecoration(
                              color: Colors.green.shade100,
                              borderRadius: BorderRadius.circular(999),
                            ),
                            child: const Text('Serial/IMEI đã được cấp', style: TextStyle(color: Colors.green, fontWeight: FontWeight.w600)),
                          ),
                          if (item.serialNumber != null && item.serialNumber!.isNotEmpty) Text('Serial: ${item.serialNumber}'),
                          if (item.imeiNumbers.isNotEmpty) Text('IMEI: ${item.imeiNumbers.join(', ')}'),
                        ] else ...[
                          const SizedBox(height: 4),
                          Text('Serial/IMEI sẽ được cập nhật khi đơn được xử lý'),
                        ],
                      ],
                    ),
                    trailing: Text('${(item.unitPrice * item.quantity).toStringAsFixed(0)} đ'),
                  ),
                );
              }),
              const SizedBox(height: 16),
              Text('Tổng tiền: ${order.totalAmount.toStringAsFixed(0)} đ', style: const TextStyle(fontWeight: FontWeight.bold)),
              const SizedBox(height: 16),
              if (canCancel)
                SizedBox(
                  width: double.infinity,
                  child: ElevatedButton.icon(
                    onPressed: () async {
                      try {
                        await ref.read(orderRepositoryProvider).cancelOrder(order.id);
                        if (context.mounted) {
                          ScaffoldMessenger.of(context).showSnackBar(
                            const SnackBar(content: Text('Đã gửi yêu cầu hủy đơn.')),
                          );
                        }
                        ref.invalidate(myOrdersProvider);
                        if (context.mounted) Navigator.pop(context);
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
                    icon: const Icon(Icons.cancel_outlined),
                    label: const Text('Hủy đơn'),
                    style: ElevatedButton.styleFrom(backgroundColor: Colors.red),
                  ),
                ),
            ],
          );
        },
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (error, stack) => Center(child: Text('Lỗi: $error')),
      ),
    );
  }
}
