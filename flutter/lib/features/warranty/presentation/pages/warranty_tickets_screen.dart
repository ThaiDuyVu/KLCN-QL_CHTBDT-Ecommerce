import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../data/models/warranty_model.dart';
import '../providers/warranty_providers.dart';

class WarrantyTicketsScreen extends ConsumerWidget {
  const WarrantyTicketsScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final async = ref.watch(myWarrantyTicketsProvider);

    return Scaffold(
      appBar: AppBar(title: const Text('Yêu cầu bảo hành của tôi')),
      body: async.when(
        data: (items) {
          if (items.isEmpty) {
            return const Center(child: Text('Bạn chưa có yêu cầu bảo hành nào.'));
          }

          return ListView.separated(
            padding: const EdgeInsets.all(12),
            itemCount: items.length,
            separatorBuilder: (_, _) => const SizedBox(height: 8),
            itemBuilder: (context, index) {
              final item = items[index];

              return Card(
                child: ListTile(
                  title: Text(item.ticketCode.isNotEmpty ? item.ticketCode : item.id),
                  subtitle: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text('Sản phẩm: ${item.productName}'),
                      if (item.sku.isNotEmpty) Text('SKU: ${item.sku}'),
                      if (item.serialNumber.isNotEmpty) Text('Serial: ${item.serialNumber}'),
                      if (item.imeiNumbers.isNotEmpty) Text('IMEI: ${item.imeiNumbers.join(', ')}'),
                      if (item.issueDescription.isNotEmpty) Text('Mô tả: ${item.issueDescription}'),
                      Text('Trạng thái: ${WarrantyTicketStatusValue.label(item.status)}'),
                      if (item.createdAt != null) Text('Tạo lúc: ${item.createdAt}'),
                    ],
                  ),
                ),
              );
            },
          );
        },
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (error, stack) => Center(child: Text('Lỗi tải ticket: $error')),
      ),
    );
  }
}
