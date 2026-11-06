import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/network/api_error.dart';
import '../../../core/utils/custom_snackbar.dart';
import '../../orders/data/models/order_model.dart';
import '../data/return_repository.dart';

/// Customer picks the delivered items to send back and gives a reason. The server enforces the
/// 7-day window and remaining quantities; [claimed] only hides what is already requested.
class ReturnRequestScreen extends ConsumerStatefulWidget {
  final Order order;
  final Map<String, int> claimed;

  const ReturnRequestScreen({super.key, required this.order, this.claimed = const {}});

  @override
  ConsumerState<ReturnRequestScreen> createState() => _ReturnRequestScreenState();
}

class _ReturnRequestScreenState extends ConsumerState<ReturnRequestScreen> {
  final Map<String, int> _selected = {};
  final TextEditingController _reason = TextEditingController();
  bool _busy = false;

  int _remaining(OrderItem item) => item.quantity - (widget.claimed[item.id] ?? 0);

  @override
  void dispose() {
    _reason.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    if (_selected.isEmpty) {
      CustomSnackbar.showWarning(context, 'Chọn ít nhất một sản phẩm cần trả');
      return;
    }
    if (_reason.text.trim().isEmpty) {
      CustomSnackbar.showWarning(context, 'Vui lòng nhập lý do đổi trả');
      return;
    }
    setState(() => _busy = true);
    try {
      final created = await ref.read(returnRepositoryProvider).create(widget.order.id, _reason.text.trim(), _selected);
      ref.invalidate(orderReturnsProvider(widget.order.id));
      if (!mounted) return;
      CustomSnackbar.showSuccess(context, 'Đã gửi yêu cầu ${created.code}');
      Navigator.pop(context, true);
    } on DioException catch (e) {
      if (mounted) CustomSnackbar.showError(context, apiErrorMessage(e));
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final items = widget.order.items.where((item) => _remaining(item) > 0).toList();
    return Scaffold(
      appBar: AppBar(title: Text('Đổi trả ${widget.order.orderCode}')),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          const Text(
            'Được đổi trả trong 7 ngày kể từ ngày nhận hàng. Tiền được hoàn sau khi cửa hàng nhận và kiểm tra hàng.',
            style: TextStyle(color: Colors.blueGrey, fontSize: 13),
          ),
          const SizedBox(height: 12),
          if (items.isEmpty) const Text('Tất cả sản phẩm của đơn đã có yêu cầu đổi trả.'),
          ...items.map((item) {
            final remaining = _remaining(item);
            final picked = _selected.containsKey(item.id);
            return Card(
              child: Column(
                children: [
                  CheckboxListTile(
                    value: picked,
                    title: Text(item.productName),
                    subtitle: Text(item.serialNumber != null ? 'Serial: ${item.serialNumber}' : 'Có thể trả: $remaining'),
                    onChanged: _busy
                        ? null
                        : (value) => setState(() {
                              if (value == true) {
                                _selected[item.id] = remaining;
                              } else {
                                _selected.remove(item.id);
                              }
                            }),
                  ),
                  if (picked && remaining > 1)
                    Row(
                      mainAxisAlignment: MainAxisAlignment.end,
                      children: [
                        IconButton(
                          icon: const Icon(Icons.remove_circle_outline),
                          onPressed: _selected[item.id]! > 1 ? () => setState(() => _selected[item.id] = _selected[item.id]! - 1) : null,
                        ),
                        Text('${_selected[item.id]}'),
                        IconButton(
                          icon: const Icon(Icons.add_circle_outline),
                          onPressed: _selected[item.id]! < remaining ? () => setState(() => _selected[item.id] = _selected[item.id]! + 1) : null,
                        ),
                      ],
                    ),
                ],
              ),
            );
          }),
          const SizedBox(height: 12),
          TextField(
            controller: _reason,
            maxLength: 1000,
            maxLines: 3,
            enabled: !_busy,
            decoration: const InputDecoration(labelText: 'Lý do đổi trả', border: OutlineInputBorder()),
          ),
          const SizedBox(height: 12),
          ElevatedButton(
            onPressed: _busy || items.isEmpty ? null : _submit,
            child: Text(_busy ? 'Đang gửi…' : 'Gửi yêu cầu đổi trả'),
          ),
        ],
      ),
    );
  }
}
