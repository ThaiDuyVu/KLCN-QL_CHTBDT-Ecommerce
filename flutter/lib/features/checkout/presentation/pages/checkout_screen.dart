import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:url_launcher/url_launcher.dart';
import '../../../../core/network/api_error.dart';
import '../../../cart/presentation/providers/cart_provider.dart';
import '../../../orders/presentation/providers/order_providers.dart';
import '../../data/address_model.dart';
import '../../data/payment_method.dart';
import '../providers/address_providers.dart';
import 'address_selection_sheet.dart';
import 'add_address_screen.dart';
import 'order_success_screen.dart';

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
  bool _hasAutoSelectedAddress = false;

  @override
  void dispose() {
    _receiverName.dispose();
    _phone.dispose();
    _address.dispose();
    _note.dispose();
    super.dispose();
  }

  void _syncControllersWithAddress(AddressModel address) {
    _receiverName.text = address.recipientName;
    _phone.text = address.phoneNumber;
    _address.text = address.formattedAddress;
  }

  void _openAddressSheet() {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(16)),
      ),
      builder: (_) => const AddressSelectionSheet(),
    );
  }

  Future<void> _submitOrder(CartSummary cart) async {
    final selectedAddress = ref.read(selectedAddressProvider);

    if (selectedAddress == null) {
      if (!_formKey.currentState!.validate()) return;
    }

    setState(() => _submitting = true);

    try {
      final repo = ref.read(cartRepositoryProvider);
      final paymentOption = ref.read(selectedPaymentMethodProvider);

      final Map<String, dynamic> payload;
      if (selectedAddress != null) {
        payload = {
          'addressId': selectedAddress.id,
          'recipientName': selectedAddress.recipientName,
          'recipientPhone': selectedAddress.phoneNumber,
          'shippingAddress': selectedAddress.formattedAddress,
          'note': _note.text.trim(),
          'paymentMethod': paymentOption.backendValue,
        };
      } else {
        payload = {
          'recipientName': _receiverName.text.trim(),
          'recipientPhone': _phone.text.trim(),
          'shippingAddress': _address.text.trim(),
          'note': _note.text.trim(),
          'paymentMethod': paymentOption.backendValue,
        };
      }

      final response = await repo.checkout(payload);

      if (response.statusCode == 200 || response.statusCode == 201) {
        if (!mounted) return;

        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Đặt hàng thành công')),
        );

        ref.invalidate(cartProvider);
        ref.invalidate(cartListProvider);
        ref.invalidate(myOrdersProvider);

        final data = response.data;
        final String orderId = (data is Map
                ? (data['orderId'] ?? data['id'] ?? '')
                : '')
            .toString();
        final String? orderCode =
            data is Map ? data['orderCode']?.toString() : null;
        final double totalAmt = (data is Map && data['totalAmount'] is num)
            ? (data['totalAmount'] as num).toDouble()
            : cart.subtotal;
        final paymentData = data is Map ? data['payment'] : null;
        final String? paymentUrl =
            paymentData is Map ? paymentData['paymentUrl']?.toString() : null;

        if (paymentOption == PaymentMethodOption.vnpay &&
            paymentUrl != null &&
            paymentUrl.isNotEmpty) {
          try {
            await launchUrl(
              Uri.parse(paymentUrl),
              mode: LaunchMode.externalApplication,
            );
          } catch (_) {}
        }

        if (!mounted) return;
        Navigator.pushReplacement(
          context,
          MaterialPageRoute(
            builder: (_) => OrderSuccessScreen(
              orderId: orderId,
              orderCode: orderCode,
              totalAmount: totalAmt,
              paymentMethod: paymentOption.displayName,
              paymentUrl: paymentUrl,
              recipientName: selectedAddress?.recipientName ??
                  _receiverName.text.trim(),
              recipientPhone:
                  selectedAddress?.phoneNumber ?? _phone.text.trim(),
              shippingAddress: selectedAddress?.formattedAddress ??
                  _address.text.trim(),
            ),
          ),
        );
        return;
      }
    } catch (error) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text(apiErrorMessage(error))),
        );
      }
      ref.invalidate(cartProvider);
    } finally {
      if (mounted) {
        setState(() => _submitting = false);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final cartAsync = ref.watch(cartProvider);
    final selectedAddress = ref.watch(selectedAddressProvider);
    final selectedPayment = ref.watch(selectedPaymentMethodProvider);

    // Tự động gán địa chỉ mặc định khi danh sách địa chỉ tải xong
    ref.listen<AsyncValue<List<AddressModel>>>(addressListProvider, (_, next) {
      next.whenData((addresses) {
        if (!_hasAutoSelectedAddress &&
            ref.read(selectedAddressProvider) == null &&
            addresses.isNotEmpty) {
          _hasAutoSelectedAddress = true;
          final defaultAddr = addresses.firstWhere(
            (a) => a.isDefault,
            orElse: () => addresses.first,
          );
          ref.read(selectedAddressProvider.notifier).select(defaultAddr);
          _syncControllersWithAddress(defaultAddr);
        }
      });
    });

    return Scaffold(
      appBar: AppBar(
        title: const Text('Thanh toán'),
        centerTitle: true,
      ),
      body: cartAsync.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (error, stack) => Center(child: Text(apiErrorMessage(error))),
        data: (cart) {
          if (cart.items.isEmpty) {
            return const Center(child: Text('Giỏ hàng đang trống.'));
          }

          return Column(
            children: [
              Expanded(
                child: SingleChildScrollView(
                  padding: const EdgeInsets.all(16),
                  child: Form(
                    key: _formKey,
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.stretch,
                      children: [
                        // 1. KHỐI ĐỊA CHỈ GIAO HÀNG (SHOPEE STYLE)
                        _buildAddressSection(selectedAddress),
                        const SizedBox(height: 16),

                        // Form nhập nhanh người nhận khi chưa có địa chỉ lưu
                        if (selectedAddress == null) ...[
                          _buildManualAddressInputs(),
                          const SizedBox(height: 16),
                        ],

                        // 2. DANH SÁCH SẢN PHẨM TRONG ĐƠN
                        _buildCartItemsSummary(cart),
                        const SizedBox(height: 16),

                        // 3. GHI CHÚ ĐƠN HÀNG
                        TextFormField(
                          controller: _note,
                          decoration: InputDecoration(
                            labelText: 'Ghi chú cho người bán',
                            hintText: 'Ví dụ: Giao giờ hành chính, gọi trước khi đến...',
                            prefixIcon: const Icon(Icons.note_alt_outlined),
                            border: OutlineInputBorder(
                              borderRadius: BorderRadius.circular(10),
                            ),
                          ),
                          maxLines: 2,
                        ),
                        const SizedBox(height: 16),

                        // 4. PHƯƠNG THỨC THANH TOÁN (3 PHƯƠNG THỨC)
                        _buildPaymentMethodSection(selectedPayment),
                        const SizedBox(height: 16),

                        // 5. TỔNG KẾT TIỀN ĐƠN HÀNG
                        _buildPriceSummary(cart),
                        const SizedBox(height: 20),
                      ],
                    ),
                  ),
                ),
              ),

              // 6. BOTTOM BAR CỐ ĐỊNH (STICKY BOTTOM BAR SHOPEE STYLE)
              _buildStickyBottomBar(cart),
            ],
          );
        },
      ),
    );
  }

  // Khối địa chỉ giao hàng phong cách Shopee
  Widget _buildAddressSection(AddressModel? selectedAddress) {
    return Container(
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: Colors.grey.shade300),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withValues(alpha: 0.04),
            blurRadius: 6,
            offset: const Offset(0, 2),
          ),
        ],
      ),
      child: Column(
        children: [
          // Dải màu trang trí phong cách phong bì thư Shopee
          Container(
            height: 4,
            decoration: const BoxDecoration(
              borderRadius: BorderRadius.vertical(top: Radius.circular(12)),
              gradient: LinearGradient(
                colors: [
                  Colors.deepOrange,
                  Colors.blue,
                  Colors.deepOrange,
                  Colors.blue,
                ],
              ),
            ),
          ),
          Padding(
            padding: const EdgeInsets.all(14),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    const Icon(
                      Icons.location_on,
                      color: Colors.deepOrange,
                      size: 20,
                    ),
                    const SizedBox(width: 8),
                    const Text(
                      'Địa chỉ nhận hàng',
                      style: TextStyle(
                        fontWeight: FontWeight.bold,
                        fontSize: 15,
                      ),
                    ),
                    const Spacer(),
                    TextButton(
                      style: TextButton.styleFrom(
                        padding: EdgeInsets.zero,
                        minimumSize: const Size(50, 30),
                        tapTargetSize: MaterialTapTargetSize.shrinkWrap,
                      ),
                      onPressed: _openAddressSheet,
                      child: Text(
                        selectedAddress != null ? 'Thay đổi' : 'Chọn địa chỉ',
                        style: const TextStyle(
                          color: Colors.deepOrange,
                          fontWeight: FontWeight.w600,
                        ),
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 8),
                if (selectedAddress != null) ...[
                  Row(
                    children: [
                      Text(
                        selectedAddress.recipientName,
                        style: const TextStyle(
                          fontWeight: FontWeight.bold,
                          fontSize: 14,
                        ),
                      ),
                      const SizedBox(width: 8),
                      Text(
                        selectedAddress.phoneNumber,
                        style: TextStyle(
                          color: Colors.grey.shade700,
                          fontSize: 14,
                        ),
                      ),
                      if (selectedAddress.isDefault) ...[
                        const SizedBox(width: 8),
                        Container(
                          padding: const EdgeInsets.symmetric(
                            horizontal: 6,
                            vertical: 2,
                          ),
                          decoration: BoxDecoration(
                            border: Border.all(color: Colors.deepOrange),
                            borderRadius: BorderRadius.circular(4),
                          ),
                          child: const Text(
                            'Mặc định',
                            style: TextStyle(
                              color: Colors.deepOrange,
                              fontSize: 11,
                              fontWeight: FontWeight.w500,
                            ),
                          ),
                        ),
                      ],
                    ],
                  ),
                  const SizedBox(height: 6),
                  Text(
                    selectedAddress.formattedAddress,
                    style: TextStyle(
                      color: Colors.grey.shade800,
                      fontSize: 13,
                      height: 1.3,
                    ),
                  ),
                ] else ...[
                  InkWell(
                    onTap: () {
                      Navigator.push(
                        context,
                        MaterialPageRoute(
                          builder: (_) => const AddAddressScreen(),
                        ),
                      );
                    },
                    child: Padding(
                      padding: const EdgeInsets.symmetric(vertical: 8),
                      child: Row(
                        children: [
                          Icon(Icons.add_circle_outline,
                              color: Colors.grey.shade600, size: 20),
                          const SizedBox(width: 8),
                          Expanded(
                            child: Text(
                              'Chưa có địa chỉ lưu sẵn. Bấm để thêm hoặc nhập nhanh bên dưới.',
                              style: TextStyle(
                                color: Colors.grey.shade600,
                                fontSize: 13,
                              ),
                            ),
                          ),
                          const Icon(Icons.chevron_right, color: Colors.grey),
                        ],
                      ),
                    ),
                  ),
                ],
              ],
            ),
          ),
        ],
      ),
    );
  }

  // Trường nhập tay dự phòng (tương thích cả với test tự động)
  Widget _buildManualAddressInputs() {
    return Card(
      elevation: 0.5,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
      child: Padding(
        padding: const EdgeInsets.all(14),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text(
              'Nhập thông tin nhận hàng nhanh:',
              style: TextStyle(fontWeight: FontWeight.bold, fontSize: 14),
            ),
            const SizedBox(height: 12),
            TextFormField(
              controller: _receiverName,
              decoration: const InputDecoration(
                labelText: 'Người nhận',
                prefixIcon: Icon(Icons.person_outline),
                border: OutlineInputBorder(),
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
                prefixIcon: Icon(Icons.phone_outlined),
                border: OutlineInputBorder(),
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
                prefixIcon: Icon(Icons.home_outlined),
                border: OutlineInputBorder(),
              ),
              maxLines: 2,
              validator: (v) =>
                  (v == null || v.trim().isEmpty) ? 'Nhập địa chỉ' : null,
            ),
          ],
        ),
      ),
    );
  }

  // Danh sách sản phẩm trong giỏ
  Widget _buildCartItemsSummary(CartSummary cart) {
    return Card(
      elevation: 0.5,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
      child: Padding(
        padding: const EdgeInsets.all(14),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Text(
                  'Sản phẩm trong đơn (${cart.items.length})',
                  style: const TextStyle(
                    fontWeight: FontWeight.bold,
                    fontSize: 15,
                  ),
                ),
                if ((cart.warehouseName ?? '').isNotEmpty)
                  Text(
                    'Kho: ${cart.warehouseName}',
                    style: TextStyle(
                      fontSize: 12,
                      color: Colors.grey.shade600,
                    ),
                  ),
              ],
            ),
            const Divider(height: 20),
            ListView.separated(
              shrinkWrap: true,
              physics: const NeverScrollableScrollPhysics(),
              itemCount: cart.items.length,
              separatorBuilder: (_, index) => const Divider(height: 16),
              itemBuilder: (context, index) {
                final item = cart.items[index];
                return Row(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Container(
                      width: 48,
                      height: 48,
                      decoration: BoxDecoration(
                        color: Colors.grey.shade100,
                        borderRadius: BorderRadius.circular(6),
                      ),
                      child: const Icon(
                        Icons.shopping_bag_outlined,
                        color: Colors.grey,
                      ),
                    ),
                    const SizedBox(width: 12),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            item.productName,
                            style: const TextStyle(
                              fontWeight: FontWeight.w600,
                              fontSize: 14,
                            ),
                            maxLines: 2,
                            overflow: TextOverflow.ellipsis,
                          ),
                          const SizedBox(height: 4),
                          Text(
                            '${item.unitPrice.toStringAsFixed(0)} đ  x${item.quantity}',
                            style: TextStyle(
                              color: Colors.grey.shade600,
                              fontSize: 13,
                            ),
                          ),
                        ],
                      ),
                    ),
                    Text(
                      '${(item.unitPrice * item.quantity).toStringAsFixed(0)} đ',
                      style: const TextStyle(
                        fontWeight: FontWeight.bold,
                        fontSize: 14,
                      ),
                    ),
                  ],
                );
              },
            ),
          ],
        ),
      ),
    );
  }

  // 3 Phương thức thanh toán Shopee-style Radio Cards
  Widget _buildPaymentMethodSection(PaymentMethodOption selectedPayment) {
    return Card(
      elevation: 0.5,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
      child: Padding(
        padding: const EdgeInsets.all(14),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text(
              'Phương thức thanh toán',
              style: TextStyle(fontWeight: FontWeight.bold, fontSize: 15),
            ),
            const SizedBox(height: 12),
            ...PaymentMethodOption.values.map((method) {
              final isSelected = selectedPayment == method;
              return Container(
                margin: const EdgeInsets.only(bottom: 8),
                decoration: BoxDecoration(
                  borderRadius: BorderRadius.circular(8),
                  border: Border.all(
                    color: isSelected ? Colors.deepOrange : Colors.grey.shade300,
                    width: isSelected ? 1.5 : 1.0,
                  ),
                  color: isSelected
                      ? Colors.deepOrange.withValues(alpha: 0.04)
                      : Colors.white,
                ),
                child: RadioListTile<PaymentMethodOption>(
                  value: method,
                  groupValue: selectedPayment,
                  activeColor: Colors.deepOrange,
                  contentPadding:
                      const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                  title: Row(
                    children: [
                      Icon(method.icon,
                          color: isSelected
                              ? Colors.deepOrange
                              : Colors.grey.shade700,
                          size: 22),
                      const SizedBox(width: 10),
                      Expanded(
                        child: Text(
                          method.displayName,
                          style: TextStyle(
                            fontWeight: isSelected
                                ? FontWeight.bold
                                : FontWeight.normal,
                            fontSize: 14,
                          ),
                        ),
                      ),
                    ],
                  ),
                  subtitle: Padding(
                    padding: const EdgeInsets.only(left: 32, top: 4),
                    child: Text(
                      method.description,
                      style: TextStyle(
                        fontSize: 12,
                        color: Colors.grey.shade600,
                      ),
                    ),
                  ),
                  onChanged: (val) {
                    if (val != null) {
                      ref.read(selectedPaymentMethodProvider.notifier).state =
                          val;
                    }
                  },
                ),
              );
            }),
          ],
        ),
      ),
    );
  }

  // Chi tiết giá tiền
  Widget _buildPriceSummary(CartSummary cart) {
    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: Colors.grey.shade50,
        borderRadius: BorderRadius.circular(10),
        border: Border.all(color: Colors.grey.shade200),
      ),
      child: Column(
        children: [
          _buildPriceRow('Tổng tiền hàng', '${cart.subtotal.toStringAsFixed(0)} đ'),
          const SizedBox(height: 8),
          _buildPriceRow('Phí vận chuyển', '0 đ', valueColor: Colors.green),
          const Divider(height: 16),
          _buildPriceRow(
            'Tổng thanh toán',
            '${cart.subtotal.toStringAsFixed(0)} đ',
            isBold: true,
            valueColor: Colors.deepOrange,
            fontSize: 16,
          ),
        ],
      ),
    );
  }

  Widget _buildPriceRow(
    String label,
    String value, {
    bool isBold = false,
    Color? valueColor,
    double fontSize = 13,
  }) {
    return Row(
      mainAxisAlignment: MainAxisAlignment.spaceBetween,
      children: [
        Text(
          label,
          style: TextStyle(
            fontSize: fontSize,
            fontWeight: isBold ? FontWeight.bold : FontWeight.normal,
            color: isBold ? Colors.black87 : Colors.grey.shade700,
          ),
        ),
        Text(
          value,
          style: TextStyle(
            fontSize: fontSize,
            fontWeight: isBold ? FontWeight.bold : FontWeight.w500,
            color: valueColor ?? Colors.black87,
          ),
        ),
      ],
    );
  }

  // Sticky Bottom Bar
  Widget _buildStickyBottomBar(CartSummary cart) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
      decoration: BoxDecoration(
        color: Colors.white,
        boxShadow: [
          BoxShadow(
            color: Colors.black.withValues(alpha: 0.06),
            offset: const Offset(0, -3),
            blurRadius: 8,
          ),
        ],
      ),
      child: SafeArea(
        child: Row(
          children: [
            Expanded(
              child: Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const Text(
                    'Tổng thanh toán',
                    style: TextStyle(fontSize: 12, color: Colors.grey),
                  ),
                  Text(
                    '${cart.subtotal.toStringAsFixed(0)} đ',
                    style: const TextStyle(
                      fontSize: 18,
                      fontWeight: FontWeight.bold,
                      color: Colors.deepOrange,
                    ),
                  ),
                ],
              ),
            ),
            SizedBox(
              height: 46,
              child: ElevatedButton(
                style: ElevatedButton.styleFrom(
                  backgroundColor: Colors.deepOrange,
                  foregroundColor: Colors.white,
                  shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(8),
                  ),
                  padding: const EdgeInsets.symmetric(horizontal: 24),
                ),
                onPressed: _submitting ? null : () => _submitOrder(cart),
                child: _submitting
                    ? const SizedBox(
                        width: 20,
                        height: 20,
                        child: CircularProgressIndicator(
                          strokeWidth: 2,
                          color: Colors.white,
                        ),
                      )
                    : const Text(
                        'Đặt hàng',
                        style: TextStyle(
                          fontSize: 16,
                          fontWeight: FontWeight.bold,
                        ),
                      ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
