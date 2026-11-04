import 'package:flutter/material.dart';

/// Khớp enum PaymentMethod của backend (COD, VNPAY, INSTALLMENT) và trang checkout web.
enum PaymentMethodOption {
  cod(
    value: 'COD',
    title: 'Thanh toán khi nhận hàng (COD)',
    subtitle: 'Thanh toán trực tiếp khi đơn được giao đến bạn',
    icon: Icons.local_shipping_outlined,
  ),
  vnpay(
    value: 'VNPAY',
    title: 'VNPAY Sandbox',
    subtitle: 'Bạn sẽ được chuyển đến cổng thanh toán thử nghiệm',
    icon: Icons.qr_code_scanner_outlined,
  ),
  installment(
    value: 'INSTALLMENT',
    title: 'Trả góp nội bộ',
    subtitle: 'Đơn chỉ được xác nhận sau khi hồ sơ được duyệt',
    icon: Icons.account_balance_outlined,
  );

  final String value;
  final String title;
  final String subtitle;
  final IconData icon;

  const PaymentMethodOption({
    required this.value,
    required this.title,
    required this.subtitle,
    required this.icon,
  });

  String get labelVn => title;
  String get note => subtitle;
  String get backendValue => value;
  String get displayName => title;
  String get description => subtitle;
}

typedef PaymentMethod = PaymentMethodOption;

/// Chỉ hiện VNPAY khi backend đang bật cổng thanh toán này.
List<PaymentMethodOption> availablePaymentMethods({required bool vnpayEnabled}) {
  return PaymentMethodOption.values
      .where((m) => m != PaymentMethodOption.vnpay || vnpayEnabled)
      .toList();
}
