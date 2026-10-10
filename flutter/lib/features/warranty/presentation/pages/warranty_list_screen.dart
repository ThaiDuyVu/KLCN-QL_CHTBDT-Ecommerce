import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'warranty_lookup_screen.dart';
import '../../data/models/warranty_model.dart';
import '../providers/warranty_providers.dart';
import 'warranty_detail_screen.dart';

class WarrantyListScreen extends ConsumerWidget {
  const WarrantyListScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final async = ref.watch(myWarrantiesProvider);

    return Scaffold(
      appBar: AppBar(title: const Text('Bảo hành của tôi')),
      body: async.when(
        data: (items) {
          if (items.isEmpty) {
            return const Center(child: Text('Bạn chưa có thiết bị nào được bảo hành.'));
          }

          return ListView.separated(
            padding: const EdgeInsets.all(12),
            itemCount: items.length,
            separatorBuilder: (_, _) => const SizedBox(height: 8),
            itemBuilder: (context, index) {
              final item = items[index];

              return Card(
                child: ListTile(
                  title: Text(item.productName),
                  subtitle: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      if (item.sku.isNotEmpty) Text('SKU: ${item.sku}'),
                      if (item.serialNumber.isNotEmpty) Text('Serial: ${item.serialNumber}'),
                      if (item.imeiNumbers.isNotEmpty) Text('IMEI: ${item.imeiNumbers.join(', ')}'),
                      Text('Trạng thái: ${WarrantyStatusValue.label(item.status)}'),
                      Text('Hiệu lực: ${item.startDate} → ${item.endDate}'),
                    ],
                  ),
                  trailing: item.eligible
                      ? const Icon(Icons.check_circle, color: Colors.green)
                      : const Icon(Icons.warning_amber_rounded, color: Colors.orange),
                  onTap: () => Navigator.push(
                    context,
                    MaterialPageRoute(builder: (_) => WarrantyDetailScreen(warrantyId: item.id)),
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
            child: Text('Lỗi tải bảo hành: ${error.toString()}'),
          ),
        ),
      ),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () => Navigator.push(
          context,
          MaterialPageRoute(builder: (_) => const WarrantyLookupScreen()),
        ),
        icon: const Icon(Icons.search),
        label: const Text('Lookup'),
      ),
    );
  }
}
