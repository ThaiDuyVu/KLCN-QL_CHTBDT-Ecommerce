import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:ecommerce_app/features/cart/presentation/providers/cart_provider.dart';
import 'package:ecommerce_app/features/warehouse/presentation/providers/warehouse_providers.dart';
import '../../data/models/product_model.dart';
import 'package:dio/dio.dart';


class ProductDetailScreen extends ConsumerStatefulWidget {
  final Product product;
  const ProductDetailScreen({super.key, required this.product});

  @override
  ConsumerState<ProductDetailScreen> createState() => _ProductDetailScreenState();
}

class _ProductDetailScreenState extends ConsumerState<ProductDetailScreen> {
  int qty = 1;

 @override
  Widget build(BuildContext context) {
    final selectedWarehouse = ref.watch(selectedWarehouseProvider);
    final available = widget.product.availableQuantity;

    return Scaffold(
      appBar: AppBar(title: Text(widget.product.name)),
      body: Column(
        children: [
          if (widget.product.imageUrl != null) Image.network(widget.product.imageUrl!, height: 240, fit: BoxFit.cover),
          Padding(
            padding: const EdgeInsets.all(12.0),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(widget.product.name, style: const TextStyle(fontSize: 20, fontWeight: FontWeight.bold)),
                const SizedBox(height: 8),
                Text('${widget.product.price} đ', style: const TextStyle(fontSize: 18, color: Colors.red)),
                const SizedBox(height: 8),
                Text('Thương hiệu: ${widget.product.brand}'),
                const SizedBox(height: 12),
                Text(available > 0 ? 'Còn $available sản phẩm' : 'Hết hàng', style: TextStyle(color: available > 0 ? Colors.green : Colors.red)),
                const SizedBox(height: 16),
                ElevatedButton(
                  onPressed: (selectedWarehouse == null || available == 0)
                      ? null
                      : () async {
                          final productId = widget.product.id;
                          if (productId.isEmpty) {
                            ScaffoldMessenger.of(context).showSnackBar(
                              const SnackBar(content: Text('Không xác định được mã sản phẩm')),
                            );
                            return;
                          }

                          try {
                            await ref.read(cartRepositoryProvider).addItem(
                              productId: productId,
                              quantity: qty,
                              warehouseId: selectedWarehouse.id,
                            );
                            
                            // Tự động tải lại giỏ hàng
                            ref.invalidate(cartListProvider);

                            if (context.mounted) {
                              ScaffoldMessenger.of(context).showSnackBar(
                                const SnackBar(content: Text('Đã thêm vào giỏ hàng thành công!'), backgroundColor: Colors.green),
                              );
                            }
                          } on DioException catch (e) {
                            if (context.mounted) {
                              ScaffoldMessenger.of(context).showSnackBar(
                                SnackBar(
                                  content: Text('Lỗi từ Server: ${e.response?.statusCode} - Có thể do chưa cấp quyền.'),
                                  backgroundColor: Colors.red,
                                ),
                              );
                            }
                          } catch (e) {
                            if (context.mounted) {
                              ScaffoldMessenger.of(context).showSnackBar(
                                SnackBar(content: Text('Lỗi hệ thống: $e')),
                              );
                            }
                          }
                        },
                  child: const Text('Thêm vào giỏ'),
                )
              ],
            ),
          )
        ],
      ),
    );
  }}