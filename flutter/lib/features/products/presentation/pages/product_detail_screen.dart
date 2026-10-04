import 'package:dio/dio.dart';
import '../../../../core/network/api_error.dart';
import '../../../cart/presentation/providers/cart_provider.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../../core/network/api_client.dart';
import '../../../auth/presentation/providers/auth_provider.dart';
import '../../../warehouse/presentation/providers/warehouse_providers.dart';
import '../../data/models/product_model.dart';
import '../providers/product_providers.dart';
import '../widgets/shop_widgets.dart';

final productVariantsProvider = FutureProvider.autoDispose
    .family<List<Map<String, dynamic>>, String>((ref, id) async {
      final response = await readWithSession(
        ref,
        () => ApiClient.dio.get(
          '/api/v1/product-variants',
          queryParameters: {'productId': id},
        ),
      );
      return (response.data as List)
          .map((v) => Map<String, dynamic>.from(v as Map))
          .where((v) => v['status'] == 'ACTIVE')
          .toList();
    });

class ProductDetailScreen extends ConsumerStatefulWidget {
  final Product product;
  const ProductDetailScreen({super.key, required this.product});
  @override
  ConsumerState<ProductDetailScreen> createState() =>
      _ProductDetailScreenState();
}

class _ProductDetailScreenState extends ConsumerState<ProductDetailScreen> {
  String? _variantId;
  bool _adding = false;
  Product get product => widget.product;

  Future<void> _addToCart() async {
    final warehouse = ref.read(selectedWarehouseProvider);
    if (warehouse == null || _variantId == null || _adding) return;
    setState(() => _adding = true);
    try {
      final repo = ref.read(cartRepositoryProvider);
      try {
        await repo.selectWarehouse(warehouse.id);
      } on DioException catch (error) {
        // Only this specific server rule permits clearing a different branch.
        if (error.response?.statusCode != 409 ||
            !(error.response?.data.toString().contains(
                  'cần xác nhận xóa giỏ',
                ) ??
                false))
          rethrow;
        if (!mounted) return;
        final confirmed = await showDialog<bool>(
          context: context,
          builder: (context) => AlertDialog(
            title: const Text('Đổi chi nhánh giỏ hàng?'),
            content: const Text(
              'Giỏ đang có sản phẩm tại chi nhánh khác. Đổi chi nhánh sẽ xóa các sản phẩm đó.',
            ),
            actions: [
              TextButton(
                onPressed: () => Navigator.pop(context, false),
                child: const Text('Hủy'),
              ),
              FilledButton(
                onPressed: () => Navigator.pop(context, true),
                child: const Text('Đổi chi nhánh'),
              ),
            ],
          ),
        );
        if (confirmed != true) return;
        await repo.selectWarehouse(warehouse.id, clearItems: true);
      }
      await repo.addItem(variantId: _variantId!, quantity: 1);
      if (mounted)
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Đã thêm phiên bản vào giỏ hàng')),
        );
    } catch (error) {
      if (mounted)
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text(apiErrorMessage(error))));
    } finally {
      if (mounted) {
        ref.invalidate(cartProvider);
        ref.invalidate(cartListProvider);
        setState(() => _adding = false);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final detail = ref.watch(productDetailProvider(product.id));
    final warehouse = ref.watch(selectedWarehouseProvider);
    final customer = ref.watch(authProvider).user?['roleName'] == 'CUSTOMER';
    return Scaffold(
      appBar: AppBar(title: const Text('Chi tiết sản phẩm')),
      body: detail.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (_, _) => ShopMessage(
          icon: Icons.wifi_off,
          title: 'Không tải được sản phẩm',
          message: 'Vui lòng kiểm tra kết nối.',
          retry: () => ref.invalidate(productDetailProvider(product.id)),
        ),
        data: (p) => ListView(
          padding: const EdgeInsets.all(20),
          children: [
            Container(
              height: 290,
              padding: const EdgeInsets.all(20),
              decoration: BoxDecoration(
                color: Colors.white,
                borderRadius: BorderRadius.circular(16),
              ),
              child: Hero(
                tag: 'product-${p.id}',
                child: ProductImage(product: p),
              ),
            ),
            const SizedBox(height: 24),
            Text(
              '${p.brand} · ${p.category}',
              style: const TextStyle(color: shopBlue),
            ),
            const SizedBox(height: 8),
            Text(
              p.name,
              style: const TextStyle(
                fontSize: 26,
                fontWeight: FontWeight.w800,
                color: shopInk,
              ),
            ),
            const SizedBox(height: 16),
            ProductPrice(product: p),
            const SizedBox(height: 16),
            Container(
              padding: const EdgeInsets.all(16),
              decoration: BoxDecoration(
                color: shopTint,
                borderRadius: BorderRadius.circular(12),
              ),
              child: Row(
                children: [
                  const Icon(Icons.storefront_outlined, color: shopBlue),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Text(
                      warehouse == null
                          ? 'Chọn chi nhánh ở trang sản phẩm để xem tồn kho.'
                          : '${warehouse.name}\n${p.availableQuantity > 0 ? 'Còn ${p.availableQuantity} sản phẩm' : 'Hết hàng tại chi nhánh'}',
                    ),
                  ),
                ],
              ),
            ),
            const SizedBox(height: 24),
            const Text(
              'Phiên bản & cấu hình',
              style: TextStyle(fontSize: 18, fontWeight: FontWeight.w700),
            ),
            const SizedBox(height: 12),
            ref
                .watch(productVariantsProvider(p.id))
                .when(
                  loading: () => const LinearProgressIndicator(),
                  error: (_, _) => TextButton(
                    onPressed: () =>
                        ref.invalidate(productVariantsProvider(p.id)),
                    child: const Text('Không tải được phiên bản · Thử lại'),
                  ),
                  data: (variants) => variants.isEmpty
                      ? const Text('Chưa có phiên bản khả dụng.')
                      : Column(
                          children: variants
                              .map(
                                (v) => InkWell(
                                  onTap: () => setState(
                                    () => _variantId = v['variantId'] as String,
                                  ),
                                  child: Container(
                                    margin: const EdgeInsets.only(bottom: 8),
                                    padding: const EdgeInsets.all(14),
                                    decoration: BoxDecoration(
                                      border: Border.all(
                                        color: _variantId == v['variantId']
                                            ? shopOrange
                                            : const Color(0xffe0e6eb),
                                        width: _variantId == v['variantId']
                                            ? 2
                                            : 1,
                                      ),
                                      borderRadius: BorderRadius.circular(10),
                                    ),
                                    child: Column(
                                      crossAxisAlignment:
                                          CrossAxisAlignment.start,
                                      children: [
                                        Row(
                                          children: [
                                            Expanded(
                                              child: Text(
                                                ['color', 'storage', 'ram']
                                                    .map(
                                                      (key) =>
                                                          v[key]?.toString() ??
                                                          '',
                                                    )
                                                    .where(
                                                      (value) =>
                                                          value.isNotEmpty,
                                                    )
                                                    .join(' · '),
                                                style: const TextStyle(
                                                  fontWeight: FontWeight.w600,
                                                ),
                                              ),
                                            ),
                                            const SizedBox(width: 8),
                                            Text(
                                              money(
                                                (v['effectivePrice'] as num?)
                                                    ?.toDouble(),
                                              ),
                                              style: const TextStyle(
                                                color: shopOrange,
                                                fontWeight: FontWeight.bold,
                                              ),
                                            ),
                                          ],
                                        ),
                                        const SizedBox(height: 6),
                                        Text(
                                          'SKU: ${v['sku'] ?? '—'} · Bảo hành: ${v['warrantyMonths'] ?? 0} tháng',
                                          style: const TextStyle(
                                            color: Colors.blueGrey,
                                            fontSize: 12,
                                          ),
                                        ),
                                      ],
                                    ),
                                  ),
                                ),
                              )
                              .toList(),
                        ),
                ),
            const SizedBox(height: 24),
            const Text(
              'Thông tin sản phẩm',
              style: TextStyle(fontSize: 18, fontWeight: FontWeight.w700),
            ),
            const SizedBox(height: 12),
            Text(
              p.description?.isNotEmpty == true
                  ? p.description!
                  : 'Chưa có mô tả sản phẩm.',
              style: const TextStyle(height: 1.7, color: shopInk),
            ),
            const SizedBox(height: 28),
          ],
        ),
      ),
      bottomNavigationBar: customer
          ? SafeArea(
              child: Padding(
                padding: const EdgeInsets.fromLTRB(20, 12, 20, 12),
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    SizedBox(
                      width: double.infinity,
                      child: FilledButton(
                        onPressed:
                            warehouse == null ||
                                _variantId == null ||
                                _adding ||
                                detail.valueOrNull?.status != 'ACTIVE'
                            ? null
                            : _addToCart,
                        child: Text(
                          _adding ? 'Đang thêm…' : 'Thêm phiên bản vào giỏ',
                        ),
                      ),
                    ),
                    SizedBox(height: 4),
                    Text(
                      warehouse == null
                          ? 'Chọn chi nhánh ở trang sản phẩm trước.'
                          : _variantId == null
                          ? 'Chạm vào phiên bản để chọn.'
                          : 'Số lượng: 1 · Có thể chỉnh trong giỏ hàng.',
                      style: TextStyle(fontSize: 11, color: Colors.blueGrey),
                    ),
                  ],
                ),
              ),
            )
          : null,
    );
  }
}
