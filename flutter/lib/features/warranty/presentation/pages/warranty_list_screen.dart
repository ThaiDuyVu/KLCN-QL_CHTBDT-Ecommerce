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
      appBar: AppBar(
        title: const Text('Bảo hành của tôi', style: TextStyle(fontWeight: FontWeight.bold, fontSize: 18, color: Colors.white)),
        backgroundColor: Colors.deepOrange,
        foregroundColor: Colors.white,
        elevation: 0,
        scrolledUnderElevation: 0,
        centerTitle: true,
      ),
      body: RefreshIndicator(
        onRefresh: () async {
          ref.invalidate(myWarrantiesProvider);
          await ref.read(myWarrantiesProvider.future);
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
                        Icon(Icons.security_outlined, size: 64, color: Colors.grey),
                        SizedBox(height: 16),
                        Text(
                          'Bạn chưa có thiết bị nào được bảo hành.',
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
          error: (error, stack) => ListView(
            physics: const AlwaysScrollableScrollPhysics(),
            children: [
              const SizedBox(height: 120),
              Center(
                child: Padding(
                  padding: const EdgeInsets.all(16),
                  child: Text('Lỗi tải bảo hành: ${error.toString()}'),
                ),
              ),
            ],
          ),
        ),
      ),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () => Navigator.push(
          context,
          MaterialPageRoute(builder: (_) => const WarrantyLookupScreen()),
        ),
        backgroundColor: Colors.deepOrange,
        foregroundColor: Colors.white,
        icon: const Icon(Icons.search),
        label: const Text('Tra cứu'),
      ),
    );
  }
}
