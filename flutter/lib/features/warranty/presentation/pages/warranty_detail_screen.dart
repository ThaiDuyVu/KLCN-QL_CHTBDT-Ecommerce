import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../data/models/warranty_model.dart';
import '../providers/warranty_providers.dart';

class WarrantyDetailScreen extends ConsumerWidget {
  final String warrantyId;

  const WarrantyDetailScreen({super.key, required this.warrantyId});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final warrantyAsync = ref.watch(warrantyDetailProvider(warrantyId));

    return Scaffold(
      appBar: AppBar(title: const Text('Chi tiết bảo hành')),
      body: warrantyAsync.when(
        data: (warranty) {
          final statusText = WarrantyStatusValue.label(warranty.status);
          final canCreateTicket = warranty.eligible;

          return ListView(
            padding: const EdgeInsets.all(16),
            children: [
              Text(warranty.productName, style: const TextStyle(fontSize: 20, fontWeight: FontWeight.bold)),
              const SizedBox(height: 8),
              Text('SKU: ${warranty.sku.isNotEmpty ? warranty.sku : '—'}'),
              Text('Serial: ${warranty.serialNumber.isNotEmpty ? warranty.serialNumber : '—'}'),
              if (warranty.imeiNumbers.isNotEmpty)
                Text('IMEI: ${warranty.imeiNumbers.join(', ')}')
              else
                const Text('IMEI: Chưa có dữ liệu'),
              const SizedBox(height: 8),
              Text('Trạng thái: $statusText'),
              Text('Hiệu lực: ${warranty.startDate} → ${warranty.endDate}'),
              const SizedBox(height: 16),
              if (canCreateTicket)
                SizedBox(
                  width: double.infinity,
                  child: ElevatedButton.icon(
                    onPressed: () => _openCreateTicketDialog(context, ref, warranty.id),
                    icon: const Icon(Icons.build_circle_outlined),
                    label: const Text('[Gửi yêu cầu bảo hành]'),
                  ),
                )
              else
                const Card(
                  child: Padding(
                    padding: EdgeInsets.all(12),
                    child: Text('Bảo hành đã hết hạn hoặc không còn hiệu lực. Không thể tạo ticket mới.'),
                  ),
                ),
              const SizedBox(height: 16),
              const Text('Lịch sử ticket', style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold)),
              const SizedBox(height: 8),
              if (warranty.tickets.isEmpty)
                const Text('Chưa có yêu cầu bảo hành nào cho thiết bị này.')
              else
                ...warranty.tickets.map((ticket) => Card(
                      child: ListTile(
                        title: Text(ticket.ticketCode.isNotEmpty ? ticket.ticketCode : ticket.id),
                        subtitle: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text('Trạng thái: ${WarrantyTicketStatusValue.label(ticket.status)}'),
                            if (ticket.issueDescription.isNotEmpty) Text('Mô tả: ${ticket.issueDescription}'),
                            if (ticket.createdAt != null) Text('Tạo lúc: ${ticket.createdAt}'),
                          ],
                        ),
                      ),
                    )),
            ],
          );
        },
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (error, stack) => Center(child: Text('Lỗi: $error')),
      ),
    );
  }

  void _openCreateTicketDialog(BuildContext context, WidgetRef ref, String warrantyId) {
    final issueController = TextEditingController();

    showDialog(
      context: context,
      builder: (dialogContext) {
        return AlertDialog(
          title: const Text('Gửi yêu cầu bảo hành'),
          content: SizedBox(
            width: 420,
            child: TextField(
              controller: issueController,
              minLines: 4,
              maxLines: 6,
              decoration: const InputDecoration(
                labelText: 'Mô tả sự cố',
                hintText: 'Ví dụ: màn hình không sáng, pin tụt nhanh...',
                border: OutlineInputBorder(),
              ),
            ),
          ),
          actions: [
            TextButton(onPressed: () => Navigator.pop(dialogContext), child: const Text('Hủy')),
            ElevatedButton(
              onPressed: () async {
                final issue = issueController.text.trim();
                if (issue.isEmpty) {
                  ScaffoldMessenger.of(context).showSnackBar(
                    const SnackBar(content: Text('Vui lòng nhập mô tả sự cố.')),
                  );
                  return;
                }

                try {
                  await ref.read(warrantyRepositoryProvider).createWarrantyTicket(
                    warrantyId: warrantyId,
                    issueDescription: issue,
                  );

                  if (context.mounted) {
                    ScaffoldMessenger.of(context).showSnackBar(
                      const SnackBar(content: Text('Đã gửi yêu cầu bảo hành.')),
                    );
                  }

                  ref.invalidate(warrantyDetailProvider(warrantyId));
                  ref.invalidate(myWarrantiesProvider);
                  ref.invalidate(myWarrantyTicketsProvider);

                  if (dialogContext.mounted) Navigator.pop(dialogContext);
                } on DioException catch (e) {
                  final message = e.response?.data is Map
                      ? (e.response?.data['message'] ?? 'Không thể gửi yêu cầu bảo hành')
                      : 'Không thể gửi yêu cầu bảo hành';
                  if (context.mounted) {
                    ScaffoldMessenger.of(context).showSnackBar(
                      SnackBar(content: Text(message.toString()), backgroundColor: Colors.red),
                    );
                  }
                } catch (e) {
                  if (context.mounted) {
                    ScaffoldMessenger.of(context).showSnackBar(
                      SnackBar(content: Text('Không thể gửi yêu cầu bảo hành: $e'), backgroundColor: Colors.red),
                    );
                  }
                }
              },
              child: const Text('Gửi'),
            ),
          ],
        );
      },
    );
  }
}
