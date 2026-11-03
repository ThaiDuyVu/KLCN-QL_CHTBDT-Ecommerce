import 'package:flutter/material.dart';

class CustomSnackbar {
  CustomSnackbar._();

  /// Hiển thị thông báo thành công (Màu xanh lá, icon check, bo góc 12px, floating)
  static void showSuccess(BuildContext context, String message) {
    _show(
      context: context,
      message: message,
      icon: Icons.check_circle_rounded,
      backgroundColor: const Color(0xFF2E7D32),
      duration: const Duration(seconds: 3),
    );
  }

  /// Hiển thị thông báo lỗi (Màu đỏ tươi, icon error, bo góc 12px, floating)
  static void showError(BuildContext context, String message) {
    _show(
      context: context,
      message: message,
      icon: Icons.error_outline_rounded,
      backgroundColor: const Color(0xFFD32F2F),
      duration: const Duration(seconds: 4),
    );
  }

  /// Hiển thị thông báo cảnh báo (Màu cam đậm, icon cảnh báo, bo góc 12px, floating)
  static void showWarning(BuildContext context, String message) {
    _show(
      context: context,
      message: message,
      icon: Icons.warning_amber_rounded,
      backgroundColor: const Color(0xFFE65100),
      duration: const Duration(seconds: 3),
    );
  }

  /// Hiển thị thông tin chung (Màu xanh đậm hoặc ghi đậm, floating)
  static void showInfo(BuildContext context, String message) {
    _show(
      context: context,
      message: message,
      icon: Icons.info_outline_rounded,
      backgroundColor: const Color(0xFF37474F),
      duration: const Duration(seconds: 3),
    );
  }

  static void _show({
    required BuildContext context,
    required String message,
    required IconData icon,
    required Color backgroundColor,
    required Duration duration,
  }) {
    final messenger = ScaffoldMessenger.maybeOf(context);
    if (messenger == null) return;

    messenger.hideCurrentSnackBar();
    messenger.showSnackBar(
      SnackBar(
        behavior: SnackBarBehavior.floating,
        margin: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
        backgroundColor: backgroundColor,
        elevation: 6,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(12),
        ),
        duration: duration,
        content: Row(
          children: [
            Icon(icon, color: Colors.white, size: 22),
            const SizedBox(width: 12),
            Expanded(
              child: Text(
                message,
                style: const TextStyle(
                  color: Colors.white,
                  fontSize: 13.5,
                  fontWeight: FontWeight.w600,
                  height: 1.3,
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

