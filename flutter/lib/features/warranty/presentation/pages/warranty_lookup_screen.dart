
import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../../core/network/api_error.dart';
import '../../data/models/warranty_model.dart';

import '../providers/warranty_providers.dart';

class WarrantyLookupScreen extends ConsumerStatefulWidget {
  const WarrantyLookupScreen({super.key});

  @override

  ConsumerState<WarrantyLookupScreen> createState() =>
      _WarrantyLookupScreenState();
}

class _WarrantyLookupScreenState extends ConsumerState<WarrantyLookupScreen> {
  final TextEditingController _controller = TextEditingController();
  String _searchedCode = '';

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  String _formatErrorMessage(Object error) {
    if (error is DioException) {
      if (error.response?.statusCode == 404) {
        return 'Không tìm thấy thiết bị nào khớp với mã Serial/IMEI đã nhập. Vui lòng kiểm tra lại.';
      }
      return apiErrorMessage(error);
    }
    return 'Đã xảy ra lỗi. Vui lòng thử lại.';
  }

  void _onSearch() {
    final code = _controller.text.trim();
    if (code.isNotEmpty) {
      setState(() {
        _searchedCode = code;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final lookupFuture = _searchedCode.isEmpty
        ? null
        : ref.watch(warrantyLookupProvider(_searchedCode));

    return Scaffold(
      appBar: AppBar(
        title: const Text(
          'Tra cứu bảo hành',
          style: TextStyle(
            fontWeight: FontWeight.bold,
            fontSize: 18,
            color: Colors.white,
          ),
        ),
        backgroundColor: Colors.deepOrange,
        foregroundColor: Colors.white,
        iconTheme: const IconThemeData(color: Colors.white),
        elevation: 0,
        scrolledUnderElevation: 0,
        centerTitle: true,
      ),

      body: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          children: [
            TextField(

              controller: _controller,
              decoration: InputDecoration(
                labelText: 'Serial hoặc IMEI',
                hintText: 'Nhập serial hoặc IMEI để tra cứu',
                border: const OutlineInputBorder(),
                suffixIcon: IconButton(
                  icon: const Icon(Icons.search),
                  onPressed: _onSearch,
                ),
              ),
              onSubmitted: (_) => _onSearch(),

            ),
            const SizedBox(height: 16),
            SizedBox(
              width: double.infinity,
              child: ElevatedButton(

                onPressed: _controller.text.trim().isEmpty ? null : _onSearch,
                style: ElevatedButton.styleFrom(
                  backgroundColor: Colors.deepOrange,
                  foregroundColor: Colors.white,
                  padding: const EdgeInsets.symmetric(vertical: 12),
                  shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(8),
                  ),
                ),
                child: const Text(
                  'Tra cứu',
                  style: TextStyle(fontWeight: FontWeight.bold),
                ),

              ),
            ),
            const SizedBox(height: 16),
            if (lookupFuture == null)

              const Expanded(
                child: Center(
                  child: Text(
                    'Nhập serial hoặc IMEI để tra cứu thiết bị.',
                    style: TextStyle(color: Colors.grey, fontSize: 14),
                  ),
                ),
              )

            else
              Expanded(
                child: lookupFuture.when(
                  data: (warranty) {
                    return ListView(
                      children: [
                        Card(

                          elevation: 2,
                          shape: RoundedRectangleBorder(
                            borderRadius: BorderRadius.circular(12),
                          ),
                          child: Padding(
                            padding: const EdgeInsets.all(16),
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Text(
                                  warranty.productName,
                                  style: const TextStyle(
                                    fontSize: 18,
                                    fontWeight: FontWeight.bold,
                                  ),
                                ),
                                const Divider(height: 24),
                                _buildInfoRow(
                                  'SKU:',
                                  warranty.sku.isNotEmpty ? warranty.sku : '—',
                                ),
                                const SizedBox(height: 8),
                                _buildInfoRow(
                                  'Serial:',
                                  warranty.serialNumber.isNotEmpty
                                      ? warranty.serialNumber
                                      : '—',
                                ),
                                const SizedBox(height: 8),
                                _buildInfoRow(
                                  'IMEI:',
                                  warranty.imeiNumbers.isNotEmpty
                                      ? warranty.imeiNumbers.join(', ')
                                      : 'Chưa có dữ liệu',
                                ),
                                const SizedBox(height: 8),
                                _buildInfoRow(
                                  'Trạng thái:',
                                  WarrantyStatusValue.label(warranty.status),
                                  highlight: true,
                                ),
                                const SizedBox(height: 8),
                                _buildInfoRow(
                                  'Hiệu lực:',
                                  '${warranty.startDate} → ${warranty.endDate}',
                                ),
                              ],
                            ),
                          ),
                        ),
                      ],
                    );
                  },
                  loading: () =>
                      const Center(child: CircularProgressIndicator()),
                  error: (error, stack) {
                    final message = _formatErrorMessage(error);
                    return Align(
                      alignment: Alignment.topCenter,
                      child: Container(
                        width: double.infinity,
                        margin: const EdgeInsets.only(top: 8),
                        padding: const EdgeInsets.symmetric(
                          horizontal: 16,
                          vertical: 14,
                        ),
                        decoration: BoxDecoration(
                          color: Colors.red.shade50,
                          borderRadius: BorderRadius.circular(10),
                          border: Border.all(color: Colors.red.shade200),
                        ),
                        child: Row(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Icon(
                              Icons.error_outline,
                              color: Colors.red.shade900,
                              size: 22,
                            ),
                            const SizedBox(width: 12),
                            Expanded(
                              child: Text(
                                message,
                                style: TextStyle(
                                  color: Colors.red.shade900,
                                  fontSize: 14,
                                  fontWeight: FontWeight.w500,
                                  height: 1.4,
                                ),
                              ),
                            ),
                          ],
                        ),
                      ),
                    );
                  },
                ),
              ),
          ],
        ),
      ),
    );
  }

  Widget _buildInfoRow(String label, String value, {bool highlight = false}) {
    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        SizedBox(
          width: 90,
          child: Text(
            label,
            style: const TextStyle(
              color: Colors.blueGrey,
              fontWeight: FontWeight.w500,
            ),
          ),
        ),
        Expanded(
          child: Text(
            value,
            style: TextStyle(
              fontWeight: highlight ? FontWeight.bold : FontWeight.w600,
              color: highlight ? Colors.teal : Colors.black87,
            ),
          ),
        ),
      ],
    );
  }

}
