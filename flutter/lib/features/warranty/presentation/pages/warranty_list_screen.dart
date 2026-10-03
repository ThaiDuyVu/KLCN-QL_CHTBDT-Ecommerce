import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../providers/warranty_providers.dart';

class WarrantyListScreen extends ConsumerWidget {
  const WarrantyListScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final async = ref.watch(myWarrantyProvider);

    return Scaffold(
      appBar: AppBar(title: const Text('Thiết bị bảo hành')),
      floatingActionButton: FloatingActionButton(
        onPressed: () => _showCreateForm(context, ref),
        child: const Icon(Icons.add),
      ),
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
                  title: Text(item.productName),
                  subtitle: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      if (item.serialNumber != null) Text('Serial: ${item.serialNumber}'),
                      if (item.imei != null) Text('IMEI: ${item.imei}'),
                      Text('Trạng thái: ${item.status}'),
                      if (item.createdAt != null) Text('Ngày: ${item.createdAt}'),
                    ],
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
    );
  }

  void _showCreateForm(BuildContext context, WidgetRef ref) {
    final productIdController = TextEditingController();
    final orderItemIdController = TextEditingController();
    final issueController = TextEditingController();

    showDialog(
      context: context,
      builder: (dialogContext) {
        return AlertDialog(
          title: const Text('Yêu cầu bảo hành'),
          content: SizedBox(
            width: 400,
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                TextField(
                  controller: productIdController,
                  decoration: const InputDecoration(labelText: 'Product ID'),
                ),
                const SizedBox(height: 8),
                TextField(
                  controller: orderItemIdController,
                  decoration: const InputDecoration(labelText: 'Order Item ID'),
                ),
                const SizedBox(height: 8),
                TextField(
                  controller: issueController,
                  decoration: const InputDecoration(labelText: 'Mô tả lỗi'),
                  minLines: 2,
                  maxLines: 4,
                ),
              ],
            ),
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(dialogContext),
              child: const Text('Hủy'),
            ),
            ElevatedButton(
              onPressed: () async {
                final productId = productIdController.text.trim();
                final orderItemId = orderItemIdController.text.trim();
                final issue = issueController.text.trim();

                if (productId.isEmpty || orderItemId.isEmpty || issue.isEmpty) {
                  ScaffoldMessenger.of(context).showSnackBar(
                    const SnackBar(content: Text('Vui lòng nhập đầy đủ thông tin.')),
                  );
                  return;
                }

                try {
                  await ref.read(warrantyRepositoryProvider).createWarrantyTicket(
                    productId: productId,
                    issue: issue,
                    orderItemId: orderItemId,
                  );
                  if (context.mounted) {
                    ScaffoldMessenger.of(context).showSnackBar(
                      const SnackBar(content: Text('Yêu cầu bảo hành đã được gửi.')),
                    );
                  }
                  ref.invalidate(myWarrantyProvider);
                  if (dialogContext.mounted) Navigator.pop(dialogContext);
                } on DioException catch (e) {
                  final msg = e.response?.data is Map
                      ? (e.response?.data['message'] ?? 'Không thể tạo bảo hành')
                      : 'Không thể tạo bảo hành';
                  if (context.mounted) {
                    ScaffoldMessenger.of(context).showSnackBar(
                      SnackBar(content: Text(msg.toString()), backgroundColor: Colors.red),
                    );
                  }
                } catch (e) {
                  if (context.mounted) {
                    ScaffoldMessenger.of(context).showSnackBar(
                      SnackBar(content: Text('Không thể tạo bảo hành: $e'), backgroundColor: Colors.red),
                    );
                  }
                }
              },
              child: const Text('Gửi yêu cầu'),
            ),
          ],
        );
      },
    );
  }
}
