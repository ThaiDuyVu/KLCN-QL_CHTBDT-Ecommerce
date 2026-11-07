import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../../core/network/api_error.dart';
import '../../../../core/utils/custom_snackbar.dart';
import '../../data/models/order_model.dart';
import '../providers/order_providers.dart';
import '../../../returns/data/return_repository.dart';
import '../../../returns/presentation/return_request_screen.dart';
import '../../../reviews/presentation/review_widgets.dart';

class OrderDetailScreen extends ConsumerWidget {
  final String orderId;

  const OrderDetailScreen({super.key, required this.orderId});

  String _formatCurrency(double value) {
    final digits = value.round().toString();
    return '${digits.replaceAllMapped(RegExp(r'(\d)(?=(\d{3})+(?!\d))'), (m) => '${m[1]}.')} ₫';
  }

  Color _getStatusColor(String status) {
    switch (status.toUpperCase()) {
      case 'DELIVERED':
        return Colors.green;
      case 'SHIPPED':
      case 'PROCESSING':
      case 'CONFIRMED':
        return Colors.blue;
      case 'CANCELLED':
        return Colors.red;
      case 'PENDING':
      default:
        return Colors.orange;
    }
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final asyncOrder = ref.watch(orderDetailProvider(orderId));

    return Scaffold(
      backgroundColor: const Color(0xfff5f5f5),
      appBar: AppBar(
        title: const Text(
          'Chi tiết đơn hàng',
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
      body: RefreshIndicator(
        onRefresh: () async {
          ref.invalidate(orderDetailProvider(orderId));
          await ref.read(orderDetailProvider(orderId).future);
        },
        child: asyncOrder.when(
          data: (order) {
            final statusColor = _getStatusColor(order.status);
            final statusText = Order.statusLabel(order.status);
            final canCancel = order.canCancel;

            return ListView(
              padding: const EdgeInsets.all(12),
              children: [
                // Khối Header: Trạng thái & Mã đơn
                Card(
                  elevation: 1,
                  shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(10),
                  ),
                  child: Padding(
                    padding: const EdgeInsets.all(16),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Row(
                          children: [
                            Expanded(
                              child: Text(
                                'Đơn: ${order.orderCode.isNotEmpty ? order.orderCode : order.id}',
                                maxLines: 1,
                                overflow: TextOverflow.ellipsis,
                                style: const TextStyle(
                                  fontWeight: FontWeight.bold,
                                  fontSize: 15,
                                ),
                              ),
                            ),
                            const SizedBox(width: 8),
                            Container(
                              padding: const EdgeInsets.symmetric(
                                horizontal: 10,
                                vertical: 4,
                              ),
                              decoration: BoxDecoration(
                                color: statusColor.withValues(alpha: 0.12),
                                borderRadius: BorderRadius.circular(16),
                                border: Border.all(
                                  color: statusColor.withValues(alpha: 0.5),
                                ),
                              ),
                              child: Text(
                                statusText,
                                style: TextStyle(
                                  color: statusColor,
                                  fontWeight: FontWeight.bold,
                                  fontSize: 12,
                                ),
                              ),
                            ),
                          ],
                        ),
                        if (order.createdAt != null &&
                            order.createdAt!.isNotEmpty) ...[
                          const SizedBox(height: 6),
                          Text(
                            'Thời gian đặt: ${order.createdAt}',
                            style: const TextStyle(
                              color: Colors.grey,
                              fontSize: 12,
                            ),
                          ),
                        ],
                      ],
                    ),
                  ),
                ),
                const SizedBox(height: 10),

                // KHỐI 1: Thông tin nhận hàng (Tên, SĐT, Kho xử lý, Địa chỉ)
                Card(
                  elevation: 1,
                  shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(10),
                  ),
                  child: Padding(
                    padding: const EdgeInsets.all(16),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        const Row(
                          children: [
                            Icon(
                              Icons.location_on_outlined,
                              color: Colors.deepOrange,
                              size: 20,
                            ),
                            SizedBox(width: 8),
                            Text(
                              'THÔNG TIN NHẬN HÀNG',
                              style: TextStyle(
                                fontWeight: FontWeight.bold,
                                fontSize: 14,
                                color: Colors.deepOrange,
                              ),
                            ),
                          ],
                        ),
                        const Divider(height: 20),
                        _buildDetailRow(
                          'Người nhận:',
                          order.recipientName.isNotEmpty
                              ? order.recipientName
                              : 'Chưa cập nhật',
                        ),
                        const SizedBox(height: 8),
                        _buildDetailRow(
                          'Số điện thoại:',
                          order.recipientPhone.isNotEmpty
                              ? order.recipientPhone
                              : 'Chưa cập nhật',
                        ),
                        const SizedBox(height: 8),
                        _buildDetailRow(
                          'Kho xử lý:',
                          (order.warehouseName ?? '').isNotEmpty
                              ? order.warehouseName!
                              : 'Chi nhánh mặc định',
                        ),
                        const SizedBox(height: 8),
                        _buildDetailRow(
                          'Địa chỉ giao:',
                          (order.shippingAddress ?? '').isNotEmpty
                              ? order.shippingAddress!
                              : 'Nhận tại cửa hàng',
                        ),
                        if (order.note != null &&
                            order.note!.trim().isNotEmpty) ...[
                          const SizedBox(height: 8),
                          _buildDetailRow('Ghi chú:', order.note!),
                        ],
                        if ((order.cancelReason ?? '').isNotEmpty) ...[
                          const SizedBox(height: 8),
                          _buildDetailRow('Lý do hủy:', order.cancelReason!),
                        ],
                      ],
                    ),
                  ),
                ),
                const SizedBox(height: 10),

                // KHỐI 1b: Vận chuyển (khi đơn đã được bàn giao cho nhân viên giao hàng)
                if (order.shipment != null) ...[
                  Card(
                    elevation: 1,
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(10),
                    ),
                    child: Padding(
                      padding: const EdgeInsets.all(16),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Row(
                            children: [
                              const Icon(
                                Icons.local_shipping_outlined,
                                color: Colors.deepOrange,
                                size: 20,
                              ),
                              const SizedBox(width: 8),
                              Text(
                                'VẬN CHUYỂN · ${OrderShipment.statusLabel(order.shipment!.status).toUpperCase()}',
                                style: const TextStyle(
                                  fontWeight: FontWeight.bold,
                                  fontSize: 14,
                                  color: Colors.deepOrange,
                                ),
                              ),
                            ],
                          ),
                          const Divider(height: 20),
                          _buildDetailRow('Đơn vị:', order.shipment!.shippingProvider),
                          const SizedBox(height: 8),
                          _buildDetailRow('Mã vận đơn:', order.shipment!.trackingNumber),
                          const SizedBox(height: 8),
                          _buildDetailRow(
                            'Người giao:',
                            [
                              order.shipment!.employeeName ?? 'Đang cập nhật',
                              if ((order.shipment!.employeePhone ?? '').isNotEmpty) order.shipment!.employeePhone!,
                            ].join(' · '),
                          ),
                          if (order.shipment!.shippedDate != null) ...[
                            const SizedBox(height: 8),
                            _buildDetailRow('Ngày gửi:', order.shipment!.shippedDate!),
                          ],
                          if (order.shipment!.deliveredDate != null) ...[
                            const SizedBox(height: 8),
                            _buildDetailRow('Ngày nhận:', order.shipment!.deliveredDate!),
                          ],
                        ],
                      ),
                    ),
                  ),
                  const SizedBox(height: 10),
                ],

                // KHỐI 2: Danh sách sản phẩm
                Card(
                  elevation: 1,
                  shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(10),
                  ),
                  child: Padding(
                    padding: const EdgeInsets.all(16),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Row(
                          children: [
                            const Icon(
                              Icons.shopping_bag_outlined,
                              color: Colors.deepOrange,
                              size: 20,
                            ),
                            const SizedBox(width: 8),
                            Text(
                              'SẢN PHẨM (${order.items.length})',
                              style: const TextStyle(
                                fontWeight: FontWeight.bold,
                                fontSize: 14,
                                color: Colors.deepOrange,
                              ),
                            ),
                          ],
                        ),
                        const Divider(height: 20),
                        ListView.separated(
                          shrinkWrap: true,
                          physics: const NeverScrollableScrollPhysics(),
                          itemCount: order.items.length,
                          separatorBuilder: (_, __) =>
                              const Divider(height: 16),
                          itemBuilder: (context, index) {
                            final item = order.items[index];
                            final hasSerialOrImei =
                                (item.serialNumber != null &&
                                        item.serialNumber!.isNotEmpty) ||
                                    item.imeiNumbers.isNotEmpty;

                            return Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Row(
                                  crossAxisAlignment: CrossAxisAlignment.start,
                                  children: [
                                    Expanded(
                                      child: Column(
                                        crossAxisAlignment:
                                            CrossAxisAlignment.start,
                                        children: [
                                          Text(
                                            item.productName,
                                            style: const TextStyle(
                                              fontWeight: FontWeight.w600,
                                              fontSize: 14,
                                            ),
                                          ),
                                          if (item.sku.isNotEmpty) ...[
                                            const SizedBox(height: 2),
                                            Text(
                                              'SKU: ${item.sku}',
                                              style: const TextStyle(
                                                color: Colors.blueGrey,
                                                fontSize: 12,
                                              ),
                                            ),
                                          ],
                                          const SizedBox(height: 4),
                                          Text(
                                            'Số lượng: x${item.quantity}',
                                            style: const TextStyle(
                                              color: Colors.black54,
                                              fontSize: 13,
                                            ),
                                          ),
                                        ],
                                      ),
                                    ),
                                    Text(
                                      _formatCurrency(
                                        item.finalUnitPrice * item.quantity,
                                      ),
                                      style: const TextStyle(
                                        fontWeight: FontWeight.bold,
                                        fontSize: 14,
                                        color: Colors.deepOrange,
                                      ),
                                    ),
                                  ],
                                ),
                                const SizedBox(height: 6),
                                if (hasSerialOrImei) ...[
                                  Container(
                                    padding: const EdgeInsets.symmetric(
                                      horizontal: 8,
                                      vertical: 3,
                                    ),
                                    decoration: BoxDecoration(
                                      color: Colors.green.shade50,
                                      borderRadius: BorderRadius.circular(4),
                                      border: Border.all(
                                        color: Colors.green.shade300,
                                      ),
                                    ),
                                    child: Column(
                                      crossAxisAlignment:
                                          CrossAxisAlignment.start,
                                      children: [
                                        if (item.serialNumber != null &&
                                            item.serialNumber!.isNotEmpty)
                                          Text(
                                            'Serial: ${item.serialNumber}',
                                            style: TextStyle(
                                              color: Colors.green.shade900,
                                              fontSize: 12,
                                              fontWeight: FontWeight.w500,
                                            ),
                                          ),
                                        if (item.imeiNumbers.isNotEmpty)
                                          Text(
                                            'IMEI: ${item.imeiNumbers.join(', ')}',
                                            style: TextStyle(
                                              color: Colors.green.shade900,
                                              fontSize: 12,
                                              fontWeight: FontWeight.w500,
                                            ),
                                          ),
                                      ],
                                    ),
                                  ),
                                ] else ...[
                                  Text(
                                    'Serial/IMEI sẽ được cập nhật khi đơn được xử lý',
                                    style: TextStyle(
                                      color: Colors.grey.shade600,
                                      fontSize: 11,
                                      fontStyle: FontStyle.italic,
                                    ),
                                  ),
                                ],
                              ],
                            );
                          },
                        ),
                      ],
                    ),
                  ),
                ),
                const SizedBox(height: 10),

                // KHỐI 3: Tổng kết đơn hàng (Tạm tính, Giảm giá, Phí giao hàng, Tổng tiền)
                Card(
                  elevation: 1,
                  shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(10),
                  ),
                  child: Padding(
                    padding: const EdgeInsets.all(16),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        const Row(
                          children: [
                            Icon(
                              Icons.receipt_long_outlined,
                              color: Colors.deepOrange,
                              size: 20,
                            ),
                            SizedBox(width: 8),
                            Text(
                              'TỔNG KẾT ĐƠN HÀNG',
                              style: TextStyle(
                                fontWeight: FontWeight.bold,
                                fontSize: 14,
                                color: Colors.deepOrange,
                              ),
                            ),
                          ],
                        ),
                        const Divider(height: 20),
                        _buildSummaryRow(
                          'Tạm tính:',
                          _formatCurrency(order.subtotal),
                        ),
                        const SizedBox(height: 8),
                        _buildSummaryRow(
                          'Giảm giá:',
                          order.discountAmount > 0
                              ? '- ${_formatCurrency(order.discountAmount)}'
                              : '0 ₫',
                          color: order.discountAmount > 0 ? Colors.green : null,
                        ),
                        const SizedBox(height: 8),
                        _buildSummaryRow(
                          'Phí giao hàng:',
                          order.shippingFee > 0
                              ? _formatCurrency(order.shippingFee)
                              : 'Miễn phí',
                          color: order.shippingFee == 0 ? Colors.green : null,
                        ),
                        const SizedBox(height: 8),
                        _buildSummaryRow(
                          'Phương thức:',
                          order.paymentMethod,
                        ),
                        if (order.invoiceNumber != null) ...[
                          const SizedBox(height: 8),
                          _buildSummaryRow(
                            'Hóa đơn:',
                            order.invoiceStatus == 'CANCELLED'
                                ? '${order.invoiceNumber} (đã hủy)'
                                : order.invoiceNumber!,
                          ),
                        ],
                        const SizedBox(height: 8),
                        _buildSummaryRow(
                          'Trạng thái thanh toán:',
                          switch (order.paymentStatus) {
                            'PAID' => 'Đã thanh toán',
                            'REFUNDED' => 'Đã hoàn tiền',
                            'FAILED' => 'Thanh toán thất bại',
                            _ => 'Chưa thanh toán',
                          },
                          color: switch (order.paymentStatus) {
                            'PAID' => Colors.green,
                            'REFUNDED' => Colors.blueGrey,
                            'FAILED' => Colors.red,
                            _ => Colors.orange,
                          },
                        ),
                        const Divider(height: 24),
                        Row(
                          mainAxisAlignment: MainAxisAlignment.spaceBetween,
                          children: [
                            const Text(
                              'Tổng thanh toán:',
                              style: TextStyle(
                                fontWeight: FontWeight.bold,
                                fontSize: 15,
                              ),
                            ),
                            Text(
                              _formatCurrency(order.totalAmount),
                              style: const TextStyle(
                                fontWeight: FontWeight.bold,
                                fontSize: 18,
                                color: Colors.deepOrange,
                              ),
                            ),
                          ],
                        ),
                      ],
                    ),
                  ),
                ),
                const SizedBox(height: 16),

                // Đổi trả: chỉ với đơn đã giao
                if (order.status == 'DELIVERED') ...[
                  OrderReviewsSection(order: order),
                  const SizedBox(height: 10),
                  _OrderReturnsSection(order: order),
                  const SizedBox(height: 16),
                ],

                // Nút Hủy đơn nếu được phép
                if (canCancel)
                  SizedBox(
                    width: double.infinity,
                    child: OutlinedButton.icon(
                      onPressed: () async {
                        final confirm = await showDialog<bool>(
                          context: context,
                          builder: (dctx) => AlertDialog(
                            shape: RoundedRectangleBorder(
                              borderRadius: BorderRadius.circular(12),
                            ),
                            title: const Text(
                              'Hủy đơn hàng?',
                              style: TextStyle(
                                color: Colors.red,
                                fontWeight: FontWeight.bold,
                              ),
                            ),
                            content: const Text(
                              'Bạn có chắc chắn muốn hủy đơn hàng này không?',
                            ),
                            actions: [
                              TextButton(
                                onPressed: () => Navigator.pop(dctx, false),
                                child: const Text('Không'),
                              ),
                              ElevatedButton(
                                onPressed: () => Navigator.pop(dctx, true),
                                style: ElevatedButton.styleFrom(
                                  backgroundColor: Colors.red,
                                  foregroundColor: Colors.white,
                                ),
                                child: const Text('Đồng ý hủy'),
                              ),
                            ],
                          ),
                        );

                        if (confirm != true) return;

                        try {
                          await ref
                              .read(orderRepositoryProvider)
                              .cancelOrder(order.id);
                          if (context.mounted) {
                            CustomSnackbar.showSuccess(
                              context,
                              'Hủy đơn hàng thành công',
                            );
                          }
                          ref.invalidate(orderDetailProvider(orderId));
                          ref.invalidate(myOrdersProvider);
                        } on DioException catch (e) {
                          if (context.mounted) {
                            CustomSnackbar.showError(
                              context,
                              apiErrorMessage(e),
                            );
                          }
                        } catch (e) {
                          if (context.mounted) {
                            CustomSnackbar.showError(
                              context,
                              'Không thể hủy đơn: $e',
                            );
                          }
                        }
                      },
                      icon: const Icon(Icons.cancel_outlined, color: Colors.red),
                      label: const Text(
                        'Hủy đơn hàng',
                        style: TextStyle(color: Colors.red),
                      ),
                      style: OutlinedButton.styleFrom(
                        side: const BorderSide(color: Colors.red),
                        padding: const EdgeInsets.symmetric(vertical: 12),
                        shape: RoundedRectangleBorder(
                          borderRadius: BorderRadius.circular(8),
                        ),
                      ),
                    ),
                  ),
                const SizedBox(height: 24),
              ],
            );
          },
          loading: () => const Center(child: CircularProgressIndicator()),
          error: (error, stack) => Center(
            child: Padding(
              padding: const EdgeInsets.all(24),
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  const Icon(Icons.error_outline, size: 48, color: Colors.red),
                  const SizedBox(height: 12),
                  Text(
                    'Không thể tải chi tiết đơn hàng:\n${apiErrorMessage(error)}',
                    textAlign: TextAlign.center,
                    style: const TextStyle(fontSize: 14),
                  ),
                  const SizedBox(height: 16),
                  ElevatedButton(
                    onPressed: () =>
                        ref.invalidate(orderDetailProvider(orderId)),
                    child: const Text('Thử lại'),
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildDetailRow(String label, String value) {
    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        SizedBox(
          width: 105,
          child: Text(
            label,
            style: const TextStyle(
              color: Colors.blueGrey,
              fontSize: 13,
            ),
          ),
        ),
        Expanded(
          child: Text(
            value,
            style: const TextStyle(
              fontWeight: FontWeight.w600,
              fontSize: 13,
              color: Colors.black87,
            ),
          ),
        ),
      ],
    );
  }

  Widget _buildSummaryRow(String label, String value, {Color? color}) {
    return Row(
      mainAxisAlignment: MainAxisAlignment.spaceBetween,
      children: [
        Text(
          label,
          style: const TextStyle(
            color: Colors.black87,
            fontSize: 13,
          ),
        ),
        Text(
          value,
          style: TextStyle(
            fontWeight: FontWeight.w600,
            fontSize: 13,
            color: color ?? Colors.black87,
          ),
        ),
      ],
    );
  }
}

/// Return requests of a delivered order and the entry point to create a new one.
class _OrderReturnsSection extends ConsumerWidget {
  final Order order;

  const _OrderReturnsSection({required this.order});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final returns = ref.watch(orderReturnsProvider(order.id));
    return Card(
      elevation: 1,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: returns.when(
          loading: () => const Center(child: CircularProgressIndicator()),
          error: (e, _) => Text('Không tải được yêu cầu đổi trả: $e'),
          data: (rows) {
            final claimed = <String, int>{};
            for (final row in rows.where((r) => r.status != 'REJECTED')) {
              for (final line in row.items) {
                claimed[line.orderItemId] = (claimed[line.orderItemId] ?? 0) + line.quantity;
              }
            }
            final canRequest = order.items.any((item) => item.quantity > (claimed[item.id] ?? 0));
            return Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Text(
                  'ĐỔI TRẢ HÀNG',
                  style: TextStyle(fontWeight: FontWeight.bold, fontSize: 14, color: Colors.deepOrange),
                ),
                const Divider(height: 20),
                ...rows.map((row) => Padding(
                      padding: const EdgeInsets.only(bottom: 8),
                      child: Text(
                        '${row.code}${row.requestType == 'EXCHANGE' ? ' (đổi hàng${row.exchangeOrderCode != null ? ' → ${row.exchangeOrderCode}' : ''})' : ''}'
                        ' · ${ReturnRequestSummary.statusLabel(row.status)}\n'
                        '${row.items.map((l) => '${l.productName} × ${l.quantity}').join(', ')}',
                        style: const TextStyle(fontSize: 13),
                      ),
                    )),
                if (canRequest)
                  SizedBox(
                    width: double.infinity,
                    child: OutlinedButton.icon(
                      icon: const Icon(Icons.assignment_return_outlined),
                      label: const Text('Yêu cầu đổi trả'),
                      onPressed: () => Navigator.push(
                        context,
                        MaterialPageRoute(builder: (_) => ReturnRequestScreen(order: order, claimed: claimed)),
                      ),
                    ),
                  ),
              ],
            );
          },
        ),
      ),
    );
  }
}
