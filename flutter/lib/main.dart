import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'core/storage/cookie_storage.dart';
import 'core/network/api_client.dart';
import 'core/router/app_router.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();

  // Khởi tạo storage lưu cookie và API client (tích hợp cookie + CSRF) trước khi chạy UI
  await CookieStorage.init();
  ApiClient.init();
  //bọc app bằng ProviderScope để sử dụng Riverpod
  runApp(const ProviderScope(child: MyApp()));
}

class MyApp extends ConsumerWidget {
  const MyApp({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final router = ref.watch(appRouterProvider);

    return MaterialApp.router(
      title: 'Ecommerce App',
      routerConfig: router,
      theme: ThemeData(primarySwatch: Colors.blue),
    );
  }
}