import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:dio/dio.dart';
import '../providers/cart_provider.dart';
import '../../../warehouse/presentation/providers/warehouse_providers.dart';

class CartScreen extends ConsumerStatefulWidget {
  const CartScreen({super.key});

  @override
  ConsumerState<CartScreen> createState() => _CartScreenState();
}

class _CartScreenState extends ConsumerState<CartScreen> {
  bool isCheckingOut = false;

  // Hàm xử lý Thanh toán 
  Future<void> _handleCheckout(List<dynamic> cartItems) async {
    final warehouse = ref.read(selectedWarehouseProvider);
    if (warehouse == null) return;

    setState(() => isCheckingOut = true);
    try {
      final repo = ref.read(cartRepositoryProvider);
      
      // Gọi API Checkout (Gửi payload tùy theo Backend yêu cầu)
      await repo.checkout({
        'warehouseId': warehouse.id,
        'paymentMethod': 'COD', 
      });

      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('🎉 Thanh toán thành công!'), backgroundColor: Colors.green)
        );
        ref.invalidate(cartListProvider); // Tải lại giỏ hàng 
        Navigator.pop(context); // Quay về trang trước
      }
    } on DioException catch (e) {
      if (e.response?.statusCode == 409) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('⚠️ Có sản phẩm đã hết hàng trong kho!'), backgroundColor: Colors.red)
        );
      } else {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Lỗi thanh toán: ${e.response?.statusCode}'))
        );
      }
    } finally {
      if (mounted) setState(() => isCheckingOut = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final cartAsync = ref.watch(cartListProvider);

    return Scaffold(
      appBar: AppBar(title: const Text('Giỏ Hàng')),
      body: cartAsync.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (err, stack) => Center(child: Text('Lỗi: $err')),
        data: (list) {
          if (list.isEmpty) {
            return const Center(child: Text('Giỏ hàng của bạn đang trống!', style: TextStyle(fontSize: 16)));
          }
          
          // Tính tổng tiền
          double total = 0;
          for (var item in list) {
             final price = (item['price'] ?? item['unitPrice'] ?? 0).toDouble();
             final qty = (item['quantity'] ?? 1).toInt();
             total += (price * qty);
          }

          return Column(
            children: [
              Expanded(
                child: ListView.builder(
                  itemCount: list.length,
                  itemBuilder: (context, index) {
                    final item = list[index];
                    final name = item['productName'] ?? item['name'] ?? 'Sản phẩm';
                    final qty = item['quantity'] ?? 1;
                    final price = item['price'] ?? item['unitPrice'] ?? 0;

                    return Card(
                      margin: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
                      child: ListTile(
                        title: Text(name.toString(), style: const TextStyle(fontWeight: FontWeight.bold)),
                        subtitle: Text('Số lượng: $qty'),
                        trailing: Text('${price * qty} đ', style: const TextStyle(color: Colors.red, fontWeight: FontWeight.bold)),
                        leading: IconButton(
                          icon: const Icon(Icons.remove_circle, color: Colors.grey),
                          onPressed: () async {
                            final itemId = item['cartItemId'] ?? item['id'];
                            if (itemId != null) {
                              await ref.read(cartRepositoryProvider).removeItem(itemId.toString());
                              ref.invalidate(cartListProvider); // Tự động load lại UI sau khi xóa
                            }
                          },
                        ),
                      ),
                    );
                  },
                ),
              ),
              // Tính tiền
              Container(
                padding: const EdgeInsets.all(16),
                decoration: BoxDecoration(color: Colors.white, boxShadow: [BoxShadow(color: Colors.grey.shade300, blurRadius: 10)]),
                child: Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    Text('Tổng: $total đ', style: const TextStyle(fontSize: 18, fontWeight: FontWeight.bold)),
                    ElevatedButton(
                      onPressed: isCheckingOut ? null : () => _handleCheckout(list),
                      style: ElevatedButton.styleFrom(backgroundColor: Colors.blue, padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 12)),
                      child: isCheckingOut 
                          ? const SizedBox(width: 20, height: 20, child: CircularProgressIndicator(color: Colors.white, strokeWidth: 2))
                          : const Text('Thanh Toán', style: TextStyle(color: Colors.white, fontWeight: FontWeight.bold)),
                    )
                  ],
                ),
              )
            ],
          );
        },
      ),
    );
  }
}