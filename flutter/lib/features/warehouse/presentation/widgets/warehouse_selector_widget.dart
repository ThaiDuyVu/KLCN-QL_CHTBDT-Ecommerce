import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../../core/network/api_error.dart';
import '../../../../core/utils/custom_snackbar.dart';
import '../../../cart/presentation/providers/cart_provider.dart';
import '../../../products/presentation/providers/product_providers.dart';
import '../../data/models/warehouse.dart';
import '../providers/warehouse_providers.dart';

class WarehouseSelectorWidget extends ConsumerStatefulWidget {
  const WarehouseSelectorWidget({super.key});

  @override
  ConsumerState<WarehouseSelectorWidget> createState() =>
      _WarehouseSelectorWidgetState();
}

class _WarehouseSelectorWidgetState
    extends ConsumerState<WarehouseSelectorWidget> {
  int _dropdownVersion = 0;

  @override
  Widget build(BuildContext context) {
    // Luôn lắng nghe sự thay đổi của chi nhánh đang chọn
    final selectedWarehouse = ref.watch(selectedWarehouseProvider);
    final warehousesAsync = ref.watch(warehouseListProvider);

    return warehousesAsync.when(
      loading: () => const SizedBox(
        height: 52,
        child: Center(child: CircularProgressIndicator(strokeWidth: 2)),
      ),
      error: (error, stackTrace) => const SizedBox(
        height: 52,
        child: Center(child: Text('Không tải được danh sách chi nhánh')),
      ),
      data: (list) {
        if (list.isEmpty) {
          return const Center(child: Text('Hệ thống chưa có chi nhánh.'));
        }

        // Tự động gán kho ban đầu khi chưa có kho nào được chọn
        WidgetsBinding.instance.addPostFrameCallback((_) {
          // Nếu đã có kho đang chọn, giữ nguyên lựa chọn của người dùng, tuyệt đối không ghi đè
          if (selectedWarehouse != null) return;
          if (list.isEmpty) return;

          final currentCart = ref.read(cartProvider).valueOrNull;
          final cartWhId =
              (currentCart != null && currentCart.items.isNotEmpty)
                  ? currentCart.warehouseId
                  : null;

          // 1. Khi mới mở app chưa chọn kho nào, nếu giỏ có đồ thì lấy kho của giỏ
          if (cartWhId != null && cartWhId.isNotEmpty) {
            final match = list.cast<Warehouse?>().firstWhere(
              (w) => w?.id == cartWhId,
              orElse: () => null,
            );
            if (match != null) {
              ref.read(selectedWarehouseProvider.notifier).select(match);
              return;
            }
          }

          // 2. Nếu giỏ trống và chưa chọn kho nào, chọn kho đầu tiên
          ref.read(selectedWarehouseProvider.notifier).select(list.first);
        });

        // Lấy ID hiện tại, nếu không khớp với list thì gán bằng null
        final isSelectedValid = list.any((w) => w.id == selectedWarehouse?.id);
        final currentValue = isSelectedValid ? selectedWarehouse?.id : null;

        return DropdownButtonFormField<String>(
          key: ValueKey('${selectedWarehouse?.id}_$_dropdownVersion'),
          initialValue: currentValue,
          isExpanded: true,
          decoration: InputDecoration(
            prefixIcon: const Icon(
              Icons.location_on_outlined,
              color: Colors.deepOrange,
            ),
            labelText: 'Chi nhánh xem hàng',
            border: OutlineInputBorder(borderRadius: BorderRadius.circular(12)),
            contentPadding: const EdgeInsets.symmetric(
              horizontal: 12,
              vertical: 12,
            ),
          ),
          hint: const Text('Chọn chi nhánh'),
          items: list.map((w) {
            return DropdownMenuItem<String>(value: w.id, child: Text(w.name));
          }).toList(),
          onChanged: (String? newId) async {
            if (newId == null) return;

            final newWarehouse = list.firstWhere((w) => w.id == newId);

            if (newId == selectedWarehouse?.id) return;

            // 1. Lấy trạng thái giỏ hàng hiện tại (chờ future nếu chưa có)
            CartSummary? cart = ref.read(cartProvider).valueOrNull;
            if (cart == null) {
              try {
                cart = await ref.read(cartProvider.future);
              } catch (_) {}
            }

            // Lấy ID kho ĐANG CHỨA đồ trong giỏ hàng (Không phải kho đang hiển thị)
            final cartWarehouseId = cart?.warehouseId;
            bool clearItems = false;

            // Bật cảnh báo nếu giỏ có hàng VÀ chọn chi nhánh KHÁC với chi nhánh đang chứa hàng
            if (cart != null &&
                cart.items.isNotEmpty &&
                cartWarehouseId != null &&
                cartWarehouseId.isNotEmpty &&
                newWarehouse.id != cartWarehouseId) {
              final oldWarehouseName = cart.warehouseName ??
                  selectedWarehouse?.name ??
                  'chi nhánh hiện tại';
              final should = await showDialog<bool>(
                context: context,
                builder: (dctx) => AlertDialog(
                  shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(12),
                  ),
                  title: const Text(
                    'Đổi chi nhánh?',
                    style: TextStyle(
                      color: Colors.deepOrange,
                      fontWeight: FontWeight.bold,
                    ),
                  ),
                  content: Text(
                    'Giỏ hàng của bạn đang có ${cart!.items.length} sản phẩm tại "$oldWarehouseName". Việc chuyển sang "${newWarehouse.name}" sẽ xóa toàn bộ sản phẩm trong giỏ. Bạn có chắc chắn muốn đổi?',
                    style: const TextStyle(fontSize: 14),
                  ),
                  actions: [
                    TextButton(
                      onPressed: () => Navigator.pop(dctx, false),
                      child: const Text('Hủy'),
                    ),
                    ElevatedButton(
                      onPressed: () => Navigator.pop(dctx, true),
                      style: ElevatedButton.styleFrom(
                        backgroundColor: Colors.red,
                        foregroundColor: Colors.white,
                        shape: RoundedRectangleBorder(
                          borderRadius: BorderRadius.circular(8),
                        ),
                      ),
                      child: const Text('Đồng ý đổi'),
                    ),
                  ],
                ),
              );

              if (should != true) {
                setState(() {
                  _dropdownVersion++;
                });
                return;
              }
              clearItems = true;
            }

            // 2. Thực hiện đổi chi nhánh qua API
            try {
              await ref.read(cartRepositoryProvider).selectWarehouse(
                    newWarehouse.id,
                    clearItems: clearItems,
                  );

              ref.read(selectedWarehouseProvider.notifier).select(newWarehouse);

              ref.invalidate(cartProvider);
              ref.invalidate(cartListProvider);
              ref.invalidate(productListProvider);

              if (context.mounted) {
                CustomSnackbar.showSuccess(
                  context,
                  'Đổi chi nhánh sang "${newWarehouse.name}" thành công',
                );
              }
            } on DioException catch (e) {
              // Bắt trường hợp 409 từ Backend
              if (e.response?.statusCode == 409 &&
                  (e.response?.data
                          .toString()
                          .contains('cần xác nhận xóa giỏ') ??
                      false)) {
                if (!context.mounted) return;
                final confirmClear = await showDialog<bool>(
                  context: context,
                  builder: (dctx) => AlertDialog(
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(12),
                    ),
                    title: const Text(
                      'Xóa giỏ hàng?',
                      style: TextStyle(
                        color: Colors.deepOrange,
                        fontWeight: FontWeight.bold,
                      ),
                    ),
                    content: Text(
                      'Giỏ hàng đang có sản phẩm tại chi nhánh khác. Bạn có đồng ý xóa giỏ để chuyển sang "${newWarehouse.name}"?',
                      style: const TextStyle(fontSize: 14),
                    ),
                    actions: [
                      TextButton(
                        onPressed: () => Navigator.pop(dctx, false),
                        child: const Text('Hủy'),
                      ),
                      ElevatedButton(
                        onPressed: () => Navigator.pop(dctx, true),
                        style: ElevatedButton.styleFrom(
                          backgroundColor: Colors.red,
                          foregroundColor: Colors.white,
                          shape: RoundedRectangleBorder(
                            borderRadius: BorderRadius.circular(8),
                          ),
                        ),
                        child: const Text('Đồng ý xóa'),
                      ),
                    ],
                  ),
                );

                if (confirmClear == true) {
                  try {
                    await ref.read(cartRepositoryProvider).selectWarehouse(
                          newWarehouse.id,
                          clearItems: true,
                        );
                    ref
                        .read(selectedWarehouseProvider.notifier)
                        .select(newWarehouse);
                    ref.invalidate(cartProvider);
                    ref.invalidate(cartListProvider);
                    ref.invalidate(productListProvider);
                    if (context.mounted) {
                      CustomSnackbar.showSuccess(
                        context,
                        'Đổi chi nhánh sang "${newWarehouse.name}" thành công',
                      );
                    }
                    return;
                  } catch (retryErr) {
                    if (context.mounted) {
                      CustomSnackbar.showError(
                        context,
                        'Không thể đổi chi nhánh: ${apiErrorMessage(retryErr)}',
                      );
                    }
                  }
                }
              }

              setState(() {
                _dropdownVersion++;
              });
              if (context.mounted) {
                CustomSnackbar.showError(
                  context,
                  'Không thể đổi chi nhánh: ${apiErrorMessage(e)}',
                );
              }
            } catch (e) {
              setState(() {
                _dropdownVersion++;
              });
              if (context.mounted) {
                CustomSnackbar.showError(
                  context,
                  'Không thể đổi chi nhánh: ${apiErrorMessage(e)}',
                );
              }
            }
          },
        );
      },
    );
  }
}
