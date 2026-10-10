import 'package:ecommerce_app/features/warranty/data/models/warranty_model.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../providers/warranty_providers.dart';

class WarrantyLookupScreen extends ConsumerStatefulWidget {
  const WarrantyLookupScreen({super.key});

  @override
  ConsumerState<WarrantyLookupScreen> createState() => _WarrantyLookupScreenState();
}

class _WarrantyLookupScreenState extends ConsumerState<WarrantyLookupScreen> {
  final controller = TextEditingController();

  @override
  Widget build(BuildContext context) {
    final lookupCode = controller.text.trim();
    final lookupFuture = lookupCode.isEmpty ? null : ref.watch(warrantyLookupProvider(lookupCode));

    return Scaffold(
      appBar: AppBar(title: const Text('Tra cứu bảo hành')),
      body: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          children: [
            TextField(
              controller: controller,
              decoration: const InputDecoration(
                labelText: 'Serial hoặc IMEI',
                hintText: 'Nhập serial hoặc IMEI để tra cứu',
                border: OutlineInputBorder(),
                suffixIcon: Icon(Icons.search),
              ),
              onChanged: (_) => setState(() {}),
            ),
            const SizedBox(height: 16),
            SizedBox(
              width: double.infinity,
              child: ElevatedButton(
                onPressed: controller.text.trim().isEmpty
                    ? null
                    : () {
                        setState(() {});
                      },
                child: const Text('Tra cứu'),
              ),
            ),
            const SizedBox(height: 16),
            if (lookupFuture == null)
              const Expanded(child: Center(child: Text('Nhập serial hoặc IMEI để tra cứu thiết bị.')))
            else
              Expanded(
                child: lookupFuture.when(
                  data: (warranty) {
                    return ListView(
                      children: [
                        Card(
                          child: Padding(
                            padding: const EdgeInsets.all(12),
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Text(warranty.productName, style: const TextStyle(fontSize: 20, fontWeight: FontWeight.bold)),
                                const SizedBox(height: 8),
                                Text('SKU: ${warranty.sku.isNotEmpty ? warranty.sku : '—'}'),
                                Text('Serial: ${warranty.serialNumber.isNotEmpty ? warranty.serialNumber : '—'}'),
                                if (warranty.imeiNumbers.isNotEmpty)
                                  Text('IMEI: ${warranty.imeiNumbers.join(', ')}')
                                else
                                  const Text('IMEI: Chưa có dữ liệu'),
                                Text('Trạng thái: ${WarrantyStatusValue.label(warranty.status)}'),
                                Text('Hiệu lực: ${warranty.startDate} → ${warranty.endDate}'),
                              ],
                            ),
                          ),
                        )
                      ],
                    );
                  },
                  loading: () => const Center(child: CircularProgressIndicator()),
                  error: (error, stack) => Center(child: Text('Không tìm thấy: $error')),
                ),
              ),
          ],
        ),
      ),
    );
  }
}
