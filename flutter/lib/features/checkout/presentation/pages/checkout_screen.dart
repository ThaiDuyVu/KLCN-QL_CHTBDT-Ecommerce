import '../../../orders/presentation/providers/order_providers.dart';
import '../../../../core/network/api_error.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:ecommerce_app/features/cart/presentation/providers/cart_provider.dart';
import 'package:ecommerce_app/features/orders/presentation/pages/order_list_screen.dart';

class CheckoutScreen extends ConsumerStatefulWidget {
  const CheckoutScreen({super.key});

  @override
  ConsumerState<CheckoutScreen> createState() => _CheckoutScreenState();
}

class _CheckoutScreenState extends ConsumerState<CheckoutScreen> {
  final _formKey = GlobalKey<FormState>();
  final _receiverName = TextEditingController();
  final _phone = TextEditingController();
  final _address = TextEditingController();
  final _note = TextEditingController();
  bool _submitting = false;

  @override
  Widget build(BuildContext context) {
    final cartAsync = ref.watch(cartProvider);

    return Scaffold(
      appBar: AppBar(title: const Text('Thanh toán')),
      body: cartAsync.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (error, stack) => Center(child: Text(_extractError(error))),
        data: (cart) {
          if (cart.items.isEmpty) {
            return const Center(child: Text('Giỏ hàng đang trống.'));
          }

          return Padding(
            padding: const EdgeInsets.all(16),
            child: Form(
              key: _formKey,
              child: SingleChildScrollView(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    TextFormField(
                      controller: _receiverName,
                      decoration: const InputDecoration(
                        labelText: 'Người nhận',
                      ),
                      validator: (v) => (v == null || v.trim().isEmpty)
                          ? 'Nhập tên người nhận'
                          : null,
                    ),
                    const SizedBox(height: 12),
                    TextFormField(
                      controller: _phone,
                      decoration: const InputDecoration(
                        labelText: 'Số điện thoại',
                      ),
                      keyboardType: TextInputType.phone,
                      validator: (v) => (v == null || v.trim().isEmpty)
                          ? 'Nhập số điện thoại'
                          : null,
                    ),
                    const SizedBox(height: 12),
                    TextFormField(
                      controller: _address,
                      decoration: const InputDecoration(
                        labelText: 'Địa chỉ giao hàng',
                      ),
                      maxLines: 2,
                      validator: (v) => (v == null || v.trim().isEmpty)
                          ? 'Nhập địa chỉ'
                          : null,
                    ),
                    const SizedBox(height: 12),
                    TextFormField(
                      controller: _note,
                      decoration: const InputDecoration(
                        labelText: 'Ghi chú (tuỳ chọn)',
                      ),
                      maxLines: 2,
                    ),
                    const SizedBox(height: 20),
                    Container(
                      padding: const EdgeInsets.all(12),
                      decoration: BoxDecoration(
                        color: Colors.grey.shade100,
                        borderRadius: BorderRadius.circular(10),
                      ),
                      child: Row(
                        mainAxisAlignment: MainAxisAlignment.spaceBetween,
                        children: [
                          const Text('Tổng tiền'),
                          Text(
                            '${cart.subtotal.toStringAsFixed(0)} đ',
                            style: const TextStyle(fontWeight: FontWeight.bold),
                          ),
                        ],
                      ),
                    ),
                    const SizedBox(height: 20),
                    ElevatedButton(
                      onPressed: _submitting ? null : _submitOrder,
                      child: _submitting
                          ? const SizedBox(
                              width: 18,
                              height: 18,
                              child: CircularProgressIndicator(strokeWidth: 2),
                            )
                          : const Text('Đặt hàng'),
                    ),
                  ],
                ),
              ),
            ),
          );
        },
      ),
    );
  }

  Future<void> _submitOrder() async {
    if (!_formKey.currentState!.validate()) return;

    setState(() => _submitting = true);

    try {
      final repo = ref.read(cartRepositoryProvider);
      final payload = {
        'recipientName': _receiverName.text.trim(),
        'recipientPhone': _phone.text.trim(),
        'shippingAddress': _address.text.trim(),
        'note': _note.text.trim(),
        'paymentMethod': 'COD',
      };

      final response = await repo.checkout(payload);
      if (response.statusCode == 200 || response.statusCode == 201) {
        if (!mounted) return;

        ScaffoldMessenger.of(
          context,
        ).showSnackBar(const SnackBar(content: Text('Đặt hàng thành công')));

        ref.invalidate(cartProvider);
        ref.invalidate(cartListProvider);
        ref.invalidate(myOrdersProvider);

        if (!mounted) return;
        Navigator.pushReplacement(
          context,
          MaterialPageRoute(builder: (_) => const OrderListScreen()),
        );
        return;
      }
    } catch (error) {
      if (mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text(apiErrorMessage(error))));
      }
      ref.invalidate(cartProvider);
    } finally {
      if (mounted) {
        setState(() => _submitting = false);
      }
    }
  }

  String _extractError(Object error) => apiErrorMessage(error);

  @override
  void dispose() {
    _receiverName.dispose();
    _phone.dispose();
    _address.dispose();
    _note.dispose();
    super.dispose();
  }
}
