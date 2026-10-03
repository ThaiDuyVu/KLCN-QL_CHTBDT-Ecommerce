import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../providers/order_providers.dart';

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
          return ListView(
            padding: const EdgeInsets.all(16),
            children: [
              Text('Mã đơn: ${order.id}', style: const TextStyle(fontWeight: FontWeight.bold)),
              const SizedBox(height: 8),
              Text('Trạng thái: ${order.status}'),
              if (order.createdAt != null) Text('Ngày: ${order.createdAt}'),
              const SizedBox(height: 16),
              const Text('Sản phẩm', style: TextStyle(fontWeight: FontWeight.bold)),
              const SizedBox(height: 8),
              ...order.items.map((item) => Card(
                    child: ListTile(
                      title: Text(item.productName),
                      subtitle: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text('Số lượng: ${item.quantity}'),
                          if (item.serialNumber != null) Text('Serial: ${item.serialNumber}'),
                          if (item.imei != null) Text('IMEI: ${item.imei}'),
                        ],
                      ),
                      trailing: Text('${item.unitPrice * item.quantity} đ'),
                    ),
                  )),
              const SizedBox(height: 16),
              Text('Tổng tiền: ${order.totalAmount} đ', style: const TextStyle(fontWeight: FontWeight.bold)),
            ],
          );
        },
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (error, stack) => Center(child: Text('Lỗi: $error')),
      ),
    );
  }
}
