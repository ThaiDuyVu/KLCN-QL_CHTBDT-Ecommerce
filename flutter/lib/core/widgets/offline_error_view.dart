import 'dart:io';
import 'package:dio/dio.dart';
import 'package:flutter/material.dart';

/// Kiểm tra lỗi có phải do mất mạng hoặc sự cố kết nối hay không
bool isNetworkError(Object? error) {
  if (error == null) return false;

  if (error is SocketException) return true;

  if (error is DioException) {
    switch (error.type) {
      case DioExceptionType.connectionTimeout:
      case DioExceptionType.sendTimeout:
      case DioExceptionType.receiveTimeout:
      case DioExceptionType.connectionError:
        return true;
      case DioExceptionType.unknown:
        if (error.error is SocketException) return true;
        break;
      default:
        break;
    }
  }

  final str = error.toString().toLowerCase();
  return str.contains('socketexception') ||
      str.contains('connection refused') ||
      str.contains('connection timed out') ||
      str.contains('network is unreachable') ||
      str.contains('failed host lookup') ||
      str.contains('handshakeexception') ||
      str.contains('connectionerror') ||
      str.contains('mạng') ||
      str.contains('kết nối');
}

/// Giao diện xử lý khi mất mạng (Offline State) chuẩn UX Shopee
class OfflineErrorView extends StatelessWidget {
  final VoidCallback onRetry;
  final String? message;
  final String? title;

  const OfflineErrorView({
    super.key,
    required this.onRetry,
    this.title,
    this.message,
  });

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.symmetric(vertical: 40, horizontal: 24),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Container(
              width: 90,
              height: 90,
              decoration: BoxDecoration(
                color: Colors.deepOrange.withValues(alpha: 0.1),
                shape: BoxShape.circle,
              ),
              child: const Icon(
                Icons.wifi_off_rounded,
                size: 48,
                color: Colors.deepOrange,
              ),
            ),
            const SizedBox(height: 20),
            Text(
              title ?? 'Mất kết nối mạng',
              style: const TextStyle(
                fontSize: 18,
                fontWeight: FontWeight.bold,
                color: Colors.black87,
              ),
              textAlign: TextAlign.center,
            ),
            const SizedBox(height: 8),
            Text(
              message ?? 'Vui lòng kiểm tra lại kết nối Wi-Fi hoặc dữ liệu di động của bạn và thử lại.',
              style: TextStyle(
                fontSize: 13.5,
                color: Colors.grey.shade600,
                height: 1.4,
              ),
              textAlign: TextAlign.center,
            ),
            const SizedBox(height: 24),
            ElevatedButton.icon(
              onPressed: onRetry,
              icon: const Icon(Icons.refresh_rounded, size: 20),
              label: const Text(
                'Chạm để thử lại',
                style: TextStyle(
                  fontSize: 14,
                  fontWeight: FontWeight.w600,
                ),
              ),
              style: ElevatedButton.styleFrom(
                backgroundColor: Colors.deepOrange,
                foregroundColor: Colors.white,
                elevation: 0,
                padding: const EdgeInsets.symmetric(
                  horizontal: 24,
                  vertical: 12,
                ),
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(24),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

/// Sliver wrapper cho OfflineErrorView
class OfflineErrorSliver extends StatelessWidget {
  final VoidCallback onRetry;
  final String? title;
  final String? message;

  const OfflineErrorSliver({
    super.key,
    required this.onRetry,
    this.title,
    this.message,
  });

  @override
  Widget build(BuildContext context) {
    return SliverFillRemaining(
      hasScrollBody: false,
      child: OfflineErrorView(
        onRetry: onRetry,
        title: title,
        message: message,
      ),
    );
  }
}

