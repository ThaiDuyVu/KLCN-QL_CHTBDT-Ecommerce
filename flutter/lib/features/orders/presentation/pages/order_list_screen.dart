import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../../core/utils/custom_snackbar.dart';
import '../../../../core/widgets/offline_error_view.dart';
import '../../../../core/widgets/shimmer_loading.dart';
import 'order_detail_screen.dart';
import '../providers/order_providers.dart';
import '../../data/models/order_model.dart';

class OrderListScreen extends ConsumerWidget {
  const OrderListScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final ordersAsync = ref.watch(myOrdersProvider);

    return Scaffold(
      appBar: AppBar(
        title: const Text('Đơn hàng của tôi', style: TextStyle(fontWeight: FontWeight.bold, fontSize: 18, color: Colors.white)),
        backgroundColor: Colors.deepOrange,
        foregroundColor: Colors.white,
        elevation: 0,
        scrolledUnderElevation: 0,
        centerTitle: true,
      ),
      body: ordersAsync.when(
        data: (orders) {
          if (orders.isEmpty) {
            return Center(
              child: Padding(
                padding: const EdgeInsets.all(32),
                child: Column(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    Icon(
                      Icons.receipt_long_outlined,
                      size: 64,
                      color: Colors.grey.shade400,
                    ),
                    const SizedBox(height: 16),
                    const Text(
                      'Bạn chưa có đơn hàng nào',
                      style: TextStyle(
                        fontSize: 16,
                        fontWeight: FontWeight.w600,
                        color: Colors.black87,
                      ),
                    ),
                    const SizedBox(height: 6),
                    Text(
                      'Các đơn hàng bạn đã đặt sẽ xuất hiện tại đây',
                      style: TextStyle(
                        fontSize: 13,
                        color: Colors.grey.shade600,
                      ),
                    ),
                  ],
                ),
              ),
            );
          }

          return RefreshIndicator(
            onRefresh: () async {
              ref.invalidate(myOrdersProvider);
              await ref.read(myOrdersProvider.future);
            },
            child: ListView.separated(
              physics: const AlwaysScrollableScrollPhysics(),
              padding: const EdgeInsets.all(12),
              itemCount: orders.length,
              separatorBuilder: (_, _) => const SizedBox(height: 8),
              itemBuilder: (context, index) {
                final order = orders[index];
                final statusText = Order.statusLabel(order.status);

                return Card(
                  child: ListTile(
                    title: Text('Đơn ${order.orderCode.isNotEmpty ? order.orderCode : order.id}'),
                    subtitle: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        const SizedBox(height: 6),
                        Text('Ngày đặt: ${order.createdAt ?? '---'}'),
                        Text('Tổng tiền: ${order.totalAmount.toStringAsFixed(0)} đ'),
                        if ((order.warehouseName ?? '').isNotEmpty) Text('Chi nhánh: ${order.warehouseName}'),
                        const SizedBox(height: 4),
                        Text('Trạng thái: $statusText'),
                      ],
                    ),
                    trailing: order.canCancel
                        ? IconButton(
                            icon: const Icon(Icons.cancel_outlined),
                            tooltip: 'Hủy đơn',
                            onPressed: () async {
                              try {
                                await ref.read(orderRepositoryProvider).cancelOrder(order.id);
                                if (context.mounted) {
                                  CustomSnackbar.showSuccess(context, 'Đã gửi yêu cầu hủy đơn.');
                                }
                                ref.invalidate(myOrdersProvider);
                              } on DioException catch (e) {
                                final message = e.response?.data is Map
                                    ? (e.response?.data['message'] ?? 'Không thể hủy đơn')
                                    : 'Không thể hủy đơn';
                                if (context.mounted) {
                                  CustomSnackbar.showError(context, message.toString());
                                }
                              } catch (e) {
                                if (context.mounted) {
                                  CustomSnackbar.showError(context, 'Không thể hủy đơn: $e');
                                }
                              }
                            },
                          )
                        : const Icon(Icons.check_circle_outline, color: Colors.green),
                    onTap: () => Navigator.push(
                      context,
                      MaterialPageRoute(builder: (_) => OrderDetailScreen(orderId: order.id)),
                    ),
                  ),
                );
              },
            ),
          );
        },
        loading: () => const OrderListShimmer(),
        error: (error, stack) => isNetworkError(error)
            ? OfflineErrorView(
                onRetry: () => ref.invalidate(myOrdersProvider),
              )
            : Center(
                child: Padding(
                  padding: const EdgeInsets.all(24),
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      const Icon(Icons.error_outline, size: 48, color: Colors.redAccent),
                      const SizedBox(height: 12),
                      Text('Lỗi tải đơn hàng: ${error.toString()}'),
                      const SizedBox(height: 12),
                      ElevatedButton(
                        onPressed: () => ref.invalidate(myOrdersProvider),
                        child: const Text('Thử lại'),
                      ),
                    ],
                  ),
                ),
              ),
      ),
    );
  }
}
