import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:ecommerce_app/features/cart/presentation/providers/cart_provider.dart';
import 'package:ecommerce_app/features/warehouse/presentation/providers/warehouse_providers.dart';

class WarehouseSelectorWidget extends ConsumerWidget {
  const WarehouseSelectorWidget({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
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

        // Lấy ID hiện tại, nếu không khớp với list thì gán bằng null
        final isSelectedValid = list.any((w) => w.id == selectedWarehouse?.id);
        final currentValue = isSelectedValid ? selectedWarehouse?.id : null;

        return DropdownButtonFormField<String>(
          initialValue: currentValue,
          isExpanded: true,
          decoration: InputDecoration(
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
            if (newId == null || newId == selectedWarehouse?.id) return;

            final newWarehouse = list.firstWhere((w) => w.id == newId);

            // ĐỌC STATE CỦA GIỎ HÀNG THAY VÌ REPOSITORY
            final cartList = ref.read(cartListProvider).valueOrNull;

            // Kiểm tra nếu giỏ hàng có đồ (danh sách không rỗng)
            if (cartList != null &&
                cartList.isNotEmpty &&
                selectedWarehouse != null) {
              final should = await _confirmWarehouseChange(context);
              if (should != true) return; // Nếu user hủy thì không làm gì cả

              // Nếu user đồng ý, gọi API xóa giỏ hàng
              await ref
                  .read(cartRepositoryProvider)
                  .selectWarehouse(newWarehouse.id, clearItems: true);
              // Refresh lại UI giỏ hàng
              ref.invalidate(cartListProvider);
            }

            ref.read(selectedWarehouseProvider.notifier).select(newWarehouse);
          },
        );
      },
    );
  }

  Future<bool?> _confirmWarehouseChange(BuildContext context) {
    return showDialog<bool>(
      context: context,
      builder: (dctx) => AlertDialog(
        title: const Text('Thay đổi chi nhánh'),
        content: const Text(
          'Thay đổi chi nhánh sẽ xóa giỏ hàng hiện tại. Bạn có muốn tiếp tục?',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(dctx, false),
            child: const Text('Hủy'),
          ),
          TextButton(
            onPressed: () => Navigator.pop(dctx, true),
            child: const Text('Tiếp tục'),
          ),
        ],
      ),
    );
  }
}
