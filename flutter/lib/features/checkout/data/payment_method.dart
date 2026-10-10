import 'package:flutter/material.dart';

enum PaymentMethodOption {
  cod(
    value: 'COD',
    title: 'Thanh toán khi nhận hàng (COD)',
    subtitle: 'Thanh toán tiền mặt hoặc chuyển khoản khi nhận hàng',
    icon: Icons.local_shipping_outlined,
  ),
  vnpay(
    value: 'VNPAY',
    title: 'Cổng thanh toán VNPAY',
    subtitle: 'Thẻ ATM nội địa, QR Pay, Visa/Mastercard',
    icon: Icons.qr_code_scanner_outlined,
  ),
  banking(
    value: 'COD', // Backend hiện tại dùng COD kèm ghi chú chuyển khoản
    backendCode: 'BANKING',
    title: 'Chuyển khoản ngân hàng (Banking)',
    subtitle: 'Chuyển khoản trực tiếp tới số tài khoản cửa hàng',
    icon: Icons.account_balance_outlined,
  );

  final String value;
  final String? backendCode;
  final String title;
  final String subtitle;
  final IconData icon;

  const PaymentMethodOption({
    required this.value,
    this.backendCode,
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