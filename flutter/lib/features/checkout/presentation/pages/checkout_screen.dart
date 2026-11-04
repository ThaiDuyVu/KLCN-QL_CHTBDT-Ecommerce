import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:url_launcher/url_launcher.dart';
import '../../../../core/network/api_error.dart';
import '../../../cart/presentation/providers/cart_provider.dart';
import '../../../orders/presentation/providers/order_providers.dart';
import '../../../products/presentation/widgets/shop_widgets.dart' show money;
import '../../data/address_model.dart';
import '../../data/payment_config_repository.dart';
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
  final _note = TextEditingController();
  final _termMonths = TextEditingController();
  final _downPayment = TextEditingController(text: '0');
  String? _installmentProviderId;
  bool _submitting = false;

  @override
  void dispose() {
    _note.dispose();
    _termMonths.dispose();
    _downPayment.dispose();
    super.dispose();
  }

  /// Giống web: dùng địa chỉ đang chọn nếu còn trong sổ địa chỉ của tài khoản hiện tại,
  /// nếu không thì lấy địa chỉ mặc định (hoặc địa chỉ đầu tiên).
  AddressModel? _activeAddress(List<AddressModel> addresses, AddressModel? selected) {
    if (addresses.isEmpty) return null;
    for (final a in addresses) {
      if (a.id == selected?.id) return a;
    }
    return addresses.firstWhere((a) => a.isDefault, orElse: () => addresses.first);
  }

  void _openAddressSheet(AddressModel? active) {
    // Đánh dấu địa chỉ đang dùng để sheet hiển thị đúng lựa chọn hiện tại.
    if (active != null) ref.read(selectedAddressProvider.notifier).select(active);
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(16)),
      ),
      builder: (_) => const AddressSelectionSheet(),
    );
  }

  Future<void> _submitOrder(CartSummary cart, AddressModel? address) async {
    if (address == null || _submitting) return;
    final paymentOption = ref.read(selectedPaymentMethodProvider);
    if (paymentOption == PaymentMethodOption.installment &&
        !_formKey.currentState!.validate()) {
      return;
    }

    setState(() => _submitting = true);

    try {
      final repo = ref.read(cartRepositoryProvider);
      final note = _note.text.trim();
      // Cùng payload với trang checkout web: backend tự lấy người nhận/địa chỉ từ addressId.
      final payload = <String, dynamic>{
        'addressId': address.id,
        'note': note.isEmpty ? null : note,
        'paymentMethod': paymentOption.backendValue,
        if (paymentOption == PaymentMethodOption.installment) ...{
          'providerId': _installmentProviderId,
          'termMonths': int.parse(_termMonths.text.trim()),
          'downPayment': double.parse(_downPayment.text.trim()),
        },
      };

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
              paymentMethod: paymentOption.backendValue,
              paymentUrl: paymentUrl,
              recipientName: address.recipientName,
              recipientPhone: address.phoneNumber,
              shippingAddress: address.formattedAddress,
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
    final addressesAsync = ref.watch(addressListProvider);
    final selectedPayment = ref.watch(selectedPaymentMethodProvider);
    final address = _activeAddress(
      addressesAsync.valueOrNull ?? const [],
      ref.watch(selectedAddressProvider),
    );

    // VNPAY đang tắt ở backend thì không cho giữ lựa chọn này
    ref.listen<AsyncValue<bool>>(vnpayEnabledProvider, (_, next) {
      if (next.valueOrNull == false &&
          ref.read(selectedPaymentMethodProvider) == PaymentMethodOption.vnpay) {
        ref.read(selectedPaymentMethodProvider.notifier).state =
            PaymentMethodOption.cod;
      }
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
                        _buildAddressSection(addressesAsync, address),
                        const SizedBox(height: 16),

                        // 2. DANH SÁCH SẢN PHẨM TRONG ĐƠN
                        _buildCartItemsSummary(cart),
                        const SizedBox(height: 16),

                        // 3. GHI CHÚ ĐƠN HÀNG
                        TextFormField(
                          controller: _note,
                          decoration: InputDecoration(
                            labelText: 'Ghi chú (không bắt buộc)',
                            hintText: 'Ghi chú cho người giao hàng',
                            prefixIcon: const Icon(Icons.note_alt_outlined),
                            border: OutlineInputBorder(
                              borderRadius: BorderRadius.circular(10),
                            ),
                          ),
                          maxLines: 2,
                        ),
                        const SizedBox(height: 16),

                        // 4. PHƯƠNG THỨC THANH TOÁN (COD / VNPAY / TRẢ GÓP)
                        _buildPaymentMethodSection(selectedPayment, cart),
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
              _buildStickyBottomBar(cart, address, selectedPayment),
            ],
          );
        },
      ),
    );
  }

  Future<void> _openAddAddress() async {
    final created = await Navigator.push<AddressModel>(
      context,
      MaterialPageRoute(builder: (_) => const AddAddressScreen()),
    );
    if (created != null) {
      ref.read(selectedAddressProvider.notifier).select(created);
    }
  }

  // Khối địa chỉ giao hàng phong cách Shopee
  Widget _buildAddressSection(
    AsyncValue<List<AddressModel>> addressesAsync,
    AddressModel? selectedAddress,
  ) {
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
                    if (addressesAsync.hasValue) TextButton(
                      style: TextButton.styleFrom(
                        padding: EdgeInsets.zero,
                        minimumSize: const Size(50, 30),
                        tapTargetSize: MaterialTapTargetSize.shrinkWrap,
                      ),
                      onPressed: selectedAddress != null
                          ? () => _openAddressSheet(selectedAddress)
                          : _openAddAddress,
                      child: Text(
                        selectedAddress != null ? 'Thay đổi' : 'Thêm địa chỉ',
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
                ] else if (addressesAsync.isLoading) ...[
                  const Padding(
                    padding: EdgeInsets.symmetric(vertical: 8),
                    child: Text('Đang tải địa chỉ…'),
                  ),
                ] else if (addressesAsync.hasError) ...[
                  Text(
                    apiErrorMessage(addressesAsync.error!),
                    style: const TextStyle(color: Colors.red, fontSize: 13),
                  ),
                  TextButton(
                    onPressed: () => ref.invalidate(addressListProvider),
                    child: const Text('Thử lại'),
                  ),
                ] else ...[
                  InkWell(
                    onTap: _openAddAddress,
                    child: Padding(
                      padding: const EdgeInsets.symmetric(vertical: 8),
                      child: Row(
                        children: [
                          Icon(Icons.add_circle_outline,
                              color: Colors.grey.shade600, size: 20),
                          const SizedBox(width: 8),
                          Expanded(
                            child: Text(
                              'Bạn chưa có địa chỉ. Hãy thêm địa chỉ trước khi đặt hàng.',
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
                            '${item.sku.isNotEmpty ? '${item.sku} · ' : ''}${money(item.unitPrice)} x${item.quantity}',
                            style: TextStyle(
                              color: Colors.grey.shade600,
                              fontSize: 13,
                            ),
                          ),
                        ],
                      ),
                    ),
                    Text(
                      money(item.lineTotal > 0 ? item.lineTotal : item.unitPrice * item.quantity),
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

  // Phương thức thanh toán: COD / VNPAY (khi backend bật) / Trả góp nội bộ
  Widget _buildPaymentMethodSection(
    PaymentMethodOption selectedPayment,
    CartSummary cart,
  ) {
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
            ...availablePaymentMethods(
              vnpayEnabled: ref.watch(vnpayEnabledProvider).valueOrNull ?? false,
            ).map((method) {
              final isSelected = selectedPayment == method;
              return Padding(
                padding: const EdgeInsets.only(bottom: 8),
                child: Material(
                  color: isSelected
                      ? Colors.deepOrange.withValues(alpha: 0.04)
                      : Colors.white,
                  shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(8),
                    side: BorderSide(
                      color: isSelected ? Colors.deepOrange : Colors.grey.shade300,
                      width: isSelected ? 1.5 : 1.0,
                    ),
                  ),
                  clipBehavior: Clip.antiAlias,
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
              ),
            );
          }),
            if (selectedPayment == PaymentMethodOption.installment)
              _buildInstallmentFields(cart.subtotal),
          ],
        ),
      ),
    );
  }

  // Thông tin hồ sơ trả góp, cùng các trường với trang checkout web
  Widget _buildInstallmentFields(double totalAmount) {
    final providersAsync = ref.watch(installmentProvidersProvider);
    final downPayment = double.tryParse(_downPayment.text.trim()) ?? 0;
    final remaining = (totalAmount - downPayment).clamp(0, totalAmount).toDouble();

    return providersAsync.when(
      loading: () => const Padding(
        padding: EdgeInsets.symmetric(vertical: 8),
        child: Text('Đang tải đơn vị trả góp…'),
      ),
      error: (error, _) => Row(
        children: [
          Expanded(
            child: Text(
              apiErrorMessage(error),
              style: const TextStyle(color: Colors.red, fontSize: 13),
            ),
          ),
          TextButton(
            onPressed: () => ref.invalidate(installmentProvidersProvider),
            child: const Text('Thử lại'),
          ),
        ],
      ),
      data: (List<InstallmentProvider> providers) {
        if (providers.isEmpty) {
          return const Text(
            'Chưa có đơn vị trả góp đang hoạt động. Vui lòng liên hệ cửa hàng.',
            style: TextStyle(color: Colors.red, fontSize: 13),
          );
        }
        if (!providers.any((p) => p.id == _installmentProviderId)) {
          _installmentProviderId = null;
        }
        return Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Text(
              'Hồ sơ sẽ được quản lý cửa hàng tiếp nhận và xét duyệt trong hệ thống.',
              style: TextStyle(fontSize: 12, color: Colors.grey.shade600),
            ),
            const SizedBox(height: 12),
            DropdownButtonFormField<String>(
              initialValue: _installmentProviderId,
              decoration: const InputDecoration(
                labelText: 'Đơn vị trả góp',
                border: OutlineInputBorder(),
              ),
              items: providers
                  .map((p) => DropdownMenuItem(value: p.id, child: Text(p.name)))
                  .toList(),
              onChanged: (value) => setState(() => _installmentProviderId = value),
              validator: (value) => value == null ? 'Chọn đơn vị trả góp' : null,
            ),
            const SizedBox(height: 12),
            TextFormField(
              controller: _termMonths,
              keyboardType: TextInputType.number,
              decoration: const InputDecoration(
                labelText: 'Kỳ hạn (tháng)',
                hintText: 'Ví dụ: 12',
                border: OutlineInputBorder(),
              ),
              validator: (value) {
                final months = int.tryParse((value ?? '').trim());
                return months == null || months < 1 ? 'Kỳ hạn phải lớn hơn 0' : null;
              },
            ),
            const SizedBox(height: 12),
            TextFormField(
              controller: _downPayment,
              keyboardType: const TextInputType.numberWithOptions(decimal: true),
              decoration: const InputDecoration(
                labelText: 'Tiền trả trước',
                border: OutlineInputBorder(),
              ),
              onChanged: (_) => setState(() {}),
              validator: (value) {
                final amount = double.tryParse((value ?? '').trim());
                if (amount == null || amount < 0) return 'Tiền trả trước không được âm';
                if (amount > totalAmount) return 'Không vượt quá tổng thanh toán';
                return null;
              },
            ),
            const SizedBox(height: 8),
            _buildPriceRow('Số tiền còn lại', money(remaining), isBold: true),
          ],
        );
      },
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
          _buildPriceRow('Tạm tính', money(cart.originalSubtotal ?? cart.subtotal)),
          const SizedBox(height: 8),
          _buildPriceRow('Giảm giá', money(cart.discountAmount)),
          const SizedBox(height: 8),
          _buildPriceRow('Phí giao hàng', money(0), valueColor: Colors.green),
          const Divider(height: 16),
          _buildPriceRow(
            'Tổng thanh toán',
            money(cart.subtotal),
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
  Widget _buildStickyBottomBar(
    CartSummary cart,
    AddressModel? address,
    PaymentMethodOption payment,
  ) {
    final vnpayReady = ref.watch(vnpayEnabledProvider).valueOrNull ?? false;
    final installmentReady =
        ref.watch(installmentProvidersProvider).valueOrNull?.isNotEmpty ?? false;
    // Cùng điều kiện khoá nút với web: phải có địa chỉ, chi nhánh và phương thức khả dụng.
    final canSubmit = !_submitting &&
        address != null &&
        (cart.warehouseId ?? '').isNotEmpty &&
        (payment != PaymentMethodOption.vnpay || vnpayReady) &&
        (payment != PaymentMethodOption.installment || installmentReady);
    final submitLabel = switch (payment) {
      PaymentMethodOption.cod => 'Đặt hàng',
      PaymentMethodOption.vnpay => 'Đặt hàng & thanh toán',
      PaymentMethodOption.installment => 'Gửi hồ sơ trả góp',
    };

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
                    money(cart.subtotal),
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
                onPressed: canSubmit ? () => _submitOrder(cart, address) : null,
                child: _submitting
                    ? const SizedBox(
                        width: 20,
                        height: 20,
                        child: CircularProgressIndicator(
                          strokeWidth: 2,
                          color: Colors.white,
                        ),
                      )
                    : Text(
                        submitLabel,
                        style: const TextStyle(
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
