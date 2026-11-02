import 'package:flutter/material.dart';
import 'package:url_launcher/url_launcher.dart';
import '../../../orders/presentation/pages/order_list_screen.dart';

class OrderSuccessScreen extends StatelessWidget {
  final String orderId;
  final String? orderCode;
  final double? totalAmount;
  final String? paymentMethod;
  final String? paymentUrl;
  final String? recipientName;
  final String? recipientPhone;
  final String? shippingAddress;

  const OrderSuccessScreen({
    super.key,
    required this.orderId,
    this.orderCode,
    this.totalAmount,
    this.paymentMethod,
    this.paymentUrl,
    this.recipientName,
    this.recipientPhone,
    this.shippingAddress,
  });

  String get _paymentMethodDisplay {
    final method = (paymentMethod ?? '').toUpperCase();
    if (method.contains('VNPAY')) return 'VNPay (Cổng thanh toán)';
    if (method.contains('BANK') || method.contains('MOMO')) {
      return 'Chuyển khoản ngân hàng (QR / MoMo)';
    }
    return 'Thanh toán khi nhận hàng (COD)';
  }

  Future<void> _openVNPay(BuildContext context) async {
    final url = paymentUrl;
    if (url == null || url.isEmpty) return;

    final uri = Uri.tryParse(url);
    if (uri == null) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Đường dẫn thanh toán không hợp lệ')),
      );
      return;
    }

    try {
      final launched = await launchUrl(
        uri,
        mode: LaunchMode.externalApplication,
      );
      if (!launched && context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Không thể mở liên kết thanh toán')),
        );
      }
    } catch (e) {
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Lỗi mở cổng thanh toán: $e')),
        );
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final displayCode = (orderCode != null && orderCode!.isNotEmpty)
        ? orderCode!
        : (orderId.isNotEmpty ? orderId : 'N/A');

    return Scaffold(
      appBar: AppBar(
        title: const Text('Đặt hàng thành công'),
        automaticallyImplyLeading: false,
      ),
      body: SafeArea(
        child: SingleChildScrollView(
          padding: const EdgeInsets.all(20),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              const SizedBox(height: 16),
              const Center(
                child: Icon(
                  Icons.check_circle_rounded,
                  size: 80,
                  color: Colors.green,
                ),
              ),
              const SizedBox(height: 16),
              const Center(
                child: Text(
                  'Đặt hàng thành công!',
                  style: TextStyle(
                    fontSize: 22,
                    fontWeight: FontWeight.bold,
                  ),
                ),
              ),
              const SizedBox(height: 8),
              Center(
                child: Text(
                  'Cảm ơn bạn đã mua sắm. Đơn hàng đang được xử lý.',
                  textAlign: TextAlign.center,
                  style: TextStyle(
                    fontSize: 14,
                    color: Colors.grey.shade600,
                  ),
                ),
              ),
              const SizedBox(height: 24),

              // Thông tin chi tiết đơn hàng
              Card(
                elevation: 1,
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(12),
                ),
                child: Padding(
                  padding: const EdgeInsets.all(16),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      const Text(
                        'Thông tin đơn hàng',
                        style: TextStyle(
                          fontSize: 16,
                          fontWeight: FontWeight.bold,
                        ),
                      ),
                      const Divider(height: 20),
                      _buildRow('Mã đơn hàng:', displayCode, isBold: true),
                      if (totalAmount != null) ...[
                        const SizedBox(height: 8),
                        _buildRow(
                          'Tổng thanh toán:',
                          '${totalAmount!.toStringAsFixed(0)} đ',
                          textColor: Colors.red.shade700,
                          isBold: true,
                        ),
                      ],
                      const SizedBox(height: 8),
                      _buildRow(
                        'Phương thức thanh toán:',
                        _paymentMethodDisplay,
                      ),
                      if (recipientName != null && recipientName!.isNotEmpty) ...[
                        const SizedBox(height: 8),
                        _buildRow('Người nhận:', recipientName!),
                      ],
                      if (recipientPhone != null && recipientPhone!.isNotEmpty) ...[
                        const SizedBox(height: 8),
                        _buildRow('Số điện thoại:', recipientPhone!),
                      ],
                      if (shippingAddress != null && shippingAddress!.isNotEmpty) ...[
                        const SizedBox(height: 8),
                        _buildRow('Địa chỉ nhận:', shippingAddress!),
                      ],
                    ],
                  ),
                ),
              ),

              // Nếu có link thanh toán VNPay
              if (paymentUrl != null && paymentUrl!.isNotEmpty) ...[
                const SizedBox(height: 16),
                Container(
                  padding: const EdgeInsets.all(16),
                  decoration: BoxDecoration(
                    color: Colors.blue.shade50,
                    borderRadius: BorderRadius.circular(12),
                    border: Border.all(color: Colors.blue.shade200),
                  ),
                  child: Column(
                    children: [
                      Row(
                        children: [
                          Icon(Icons.payment, color: Colors.blue.shade700),
                          const SizedBox(width: 8),
                          Expanded(
                            child: Text(
                              'Đơn hàng cần hoàn tất thanh toán VNPay',
                              style: TextStyle(
                                fontWeight: FontWeight.bold,
                                color: Colors.blue.shade900,
                              ),
                            ),
                          ),
                        ],
                      ),
                      const SizedBox(height: 8),
                      Text(
                        'Vui lòng nhấn nút bên dưới để chuyển tiếp tới cổng VNPay và thanh toán an toàn.',
                        style: TextStyle(
                          fontSize: 13,
                          color: Colors.blue.shade800,
                        ),
                      ),
                      const SizedBox(height: 12),
                      ElevatedButton.icon(
                        style: ElevatedButton.styleFrom(
                          backgroundColor: Colors.blue.shade700,
                          foregroundColor: Colors.white,
                          minimumSize: const Size.fromHeight(44),
                        ),
                        onPressed: () => _openVNPay(context),
                        icon: const Icon(Icons.open_in_browser),
                        label: const Text('Thanh toán ngay qua VNPay'),
                      ),
                    ],
                  ),
                ),
              ],

              const SizedBox(height: 32),

              // Các nút hành động chính
              ElevatedButton(
                style: ElevatedButton.styleFrom(
                  minimumSize: const Size.fromHeight(48),
                  backgroundColor: Theme.of(context).primaryColor,
                  foregroundColor: Colors.white,
                ),
                onPressed: () {
                  Navigator.pushReplacement(
                    context,
                    MaterialPageRoute(
                      builder: (_) => const OrderListScreen(),
                    ),
                  );
                },
                child: const Text(
                  'Đơn hàng của tôi',
                  style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold),
                ),
              ),
              const SizedBox(height: 12),
              OutlinedButton(
                style: OutlinedButton.styleFrom(
                  minimumSize: const Size.fromHeight(48),
                ),
                onPressed: () {
                  Navigator.of(context).popUntil((route) => route.isFirst);
                },
                child: const Text('Tiếp tục mua sắm'),
              ),
              const SizedBox(height: 16),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildRow(
    String label,
    String value, {
    bool isBold = false,
    Color? textColor,
  }) {
    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        SizedBox(
          width: 140,
          child: Text(
            label,
            style: const TextStyle(fontSize: 13, color: Colors.black54),
          ),
        ),
        Expanded(
          child: Text(
            value,
            textAlign: TextAlign.right,
            style: TextStyle(
              fontSize: 13,
              fontWeight: isBold ? FontWeight.bold : FontWeight.normal,
              color: textColor ?? Colors.black87,
            ),
          ),
        ),
      ],
    );
  }
}

