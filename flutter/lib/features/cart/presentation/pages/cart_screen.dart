import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../../core/network/api_error.dart';
import '../../../checkout/presentation/pages/checkout_screen.dart';
import '../../../products/presentation/widgets/shop_widgets.dart';
import '../providers/cart_provider.dart';

class CartScreen extends ConsumerStatefulWidget {
  const CartScreen({super.key});
  @override
  ConsumerState<CartScreen> createState() => _CartScreenState();
}

class _CartScreenState extends ConsumerState<CartScreen> {
  bool _busy = false;
  Future<void> _change(Future<dynamic> Function() request) async {
    if (_busy) return;
    setState(() => _busy = true);
    try {
      await request();
    } catch (error) {
      if (mounted)
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text(apiErrorMessage(error))));
    } finally {
      ref.invalidate(cartProvider);
      ref.invalidate(cartListProvider);
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: const Text('Giỏ hàng')),
    body: ref
        .watch(cartProvider)
        .when(
          loading: () => const Center(child: CircularProgressIndicator()),
          error: (error, _) => ShopMessage(
            icon: Icons.shopping_bag_outlined,
            title: 'Không tải được giỏ hàng',
            message: apiErrorMessage(error),
            retry: () => ref.invalidate(cartProvider),
          ),
          data: (cart) => cart.items.isEmpty
              ? const ShopMessage(
                  icon: Icons.shopping_bag_outlined,
                  title: 'Giỏ hàng đang trống',
                  message:
                      'Chọn phiên bản tại trang chi tiết để thêm sản phẩm.',
                )
              : Column(
                  children: [
                    Padding(
                      padding: const EdgeInsets.all(16),
                      child: Text(
                        'Chi nhánh: ${cart.warehouseName ?? 'Chưa chọn'}',
                      ),
                    ),
                    if (_busy) const LinearProgressIndicator(),
                    Expanded(
                      child: ListView.builder(
                        itemCount: cart.items.length,
                        itemBuilder: (context, index) {
                          final item = cart.items[index];
                          final repo = ref.read(cartRepositoryProvider);
                          return Card(
                            margin: const EdgeInsets.symmetric(
                              horizontal: 16,
                              vertical: 6,
                            ),
                            child: Padding(
                              padding: const EdgeInsets.all(16),
                              child: Column(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  Text(
                                    item.productName,
                                    style: const TextStyle(
                                      fontWeight: FontWeight.bold,
                                    ),
                                  ),
                                  Text(
                                    item.sku,
                                    style: const TextStyle(
                                      color: Colors.blueGrey,
                                      fontSize: 12,
                                    ),
                                  ),
                                  const SizedBox(height: 8),
                                  Text(
                                    money(item.unitPrice),
                                    style: const TextStyle(color: shopOrange),
                                  ),
                                  Row(
                                    children: [
                                      IconButton(
                                        tooltip: 'Giảm số lượng',
                                        onPressed: _busy || item.quantity <= 1
                                            ? null
                                            : () => _change(
                                                () => repo.updateItem(
                                                  item.id,
                                                  item.quantity - 1,
                                                ),
                                              ),
                                        icon: const Icon(Icons.remove),
                                      ),
                                      Text('${item.quantity}'),
                                      IconButton(
                                        tooltip: 'Tăng số lượng',
                                        onPressed:
                                            _busy ||
                                                item.quantity >=
                                                    item.availableQuantity
                                            ? null
                                            : () => _change(
                                                () => repo.updateItem(
                                                  item.id,
                                                  item.quantity + 1,
                                                ),
                                              ),
                                        icon: const Icon(Icons.add),
                                      ),
                                      const Spacer(),
                                      IconButton(
                                        tooltip: 'Xóa sản phẩm',
                                        onPressed: _busy
                                            ? null
                                            : () => _change(
                                                () => repo.removeItem(item.id),
                                              ),
                                        icon: const Icon(Icons.delete_outline),
                                      ),
                                    ],
                                  ),
                                  Text('Thành tiền: ${money(item.lineTotal)}'),
                                ],
                              ),
                            ),
                          );
                        },
                      ),
                    ),
                    SafeArea(
                      child: Padding(
                        padding: const EdgeInsets.all(16),
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.stretch,
                          children: [
                            Text(
                              'Tổng tiền: ${money(cart.subtotal)}',
                              style: const TextStyle(
                                fontSize: 20,
                                fontWeight: FontWeight.bold,
                              ),
                            ),
                            const SizedBox(height: 12),
                            FilledButton(
                              onPressed: _busy
                                  ? null
                                  : () async {
                                      await Navigator.push(
                                        context,
                                        MaterialPageRoute(
                                          builder: (_) =>
                                              const CheckoutScreen(),
                                        ),
                                      );
                                      ref.invalidate(cartProvider);
                                    },
                              child: const Text('Tiếp tục đặt hàng · COD'),
                            ),
                          ],
                        ),
                      ),
                    ),
                  ],
                ),
        ),
  );
}
