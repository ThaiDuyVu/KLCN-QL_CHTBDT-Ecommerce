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
      appBar: AppBar(
        title: const Text('Yêu cầu bảo hành', style: TextStyle(fontWeight: FontWeight.bold, fontSize: 18, color: Colors.white)),
        backgroundColor: Colors.deepOrange,
        foregroundColor: Colors.white,
        elevation: 0,
        scrolledUnderElevation: 0,
        centerTitle: true,
      ),
      body: RefreshIndicator(
        onRefresh: () async {
          ref.invalidate(myWarrantyTicketsProvider);
          await ref.read(myWarrantyTicketsProvider.future);
        },
        child: async.when(
          data: (items) {
            if (items.isEmpty) {
              return ListView(
                physics: const AlwaysScrollableScrollPhysics(),
                children: const [
                  SizedBox(height: 120),
                  Center(
                    child: Column(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        Icon(Icons.confirmation_number_outlined, size: 64, color: Colors.grey),
                        SizedBox(height: 16),
                        Text(
                          'Bạn chưa có yêu cầu bảo hành nào.',
                          style: TextStyle(fontSize: 16, fontWeight: FontWeight.w600, color: Colors.black87),
                        ),
                        SizedBox(height: 8),
                        Text(
                          'Vuốt xuống để làm mới dữ liệu',
                          style: TextStyle(fontSize: 13, color: Colors.grey),
                        ),
                      ],
                    ),
                  ),
                ],
              );
            }

            return ListView.separated(
              physics: const AlwaysScrollableScrollPhysics(),
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
          error: (error, stack) => ListView(
            physics: const AlwaysScrollableScrollPhysics(),
            children: [
              const SizedBox(height: 120),
              Center(
                child: Padding(
                  padding: const EdgeInsets.all(16),
                  child: Text('Lỗi tải ticket: $error'),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
