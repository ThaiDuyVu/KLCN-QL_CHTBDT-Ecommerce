import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:ecommerce_app/core/storage/cookie_storage.dart';
import 'package:ecommerce_app/features/auth/presentation/providers/auth_provider.dart';
import 'package:ecommerce_app/features/profile/presentation/providers/profile_provider.dart';

class ProfileScreen extends ConsumerWidget {
  const ProfileScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final profileAsync = ref.watch(profileProvider);

    return Scaffold(
      appBar: AppBar(title: const Text('Hồ sơ')),
      body: profileAsync.when(
        data: (user) {
          return Padding(
            padding: const EdgeInsets.all(16),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text('Tên: ${user['fullName'] ?? user['name'] ?? user['username'] ?? '-'}', style: const TextStyle(fontSize: 18, fontWeight: FontWeight.bold)),
                const SizedBox(height: 12),
                Text('Email: ${user['email'] ?? '-'}'),
                const SizedBox(height: 12),
                Text('Username: ${user['username'] ?? '-'}'),
                const SizedBox(height: 24),
                SizedBox(
                  width: double.infinity,
                  child: ElevatedButton(
                    onPressed: () async {
                      try {
                        await ref.read(profileRepositoryProvider).logout();
                        await CookieStorage.clearCookies();
                        ref.read(authProvider.notifier).logout();
                        if (context.mounted) context.go('/login');
                      } catch (_) {
                        if (context.mounted) {
                          ScaffoldMessenger.of(context).showSnackBar(
                            const SnackBar(content: Text('Đăng xuất thất bại')),
                          );
                        }
                      }
                    },
                    child: const Text('Đăng xuất'),
                  ),
                ),
              ],
            ),
          );
        },
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (error, stack) => Center(child: Text('Lỗi: $error')),
      ),
    );
  }
}
