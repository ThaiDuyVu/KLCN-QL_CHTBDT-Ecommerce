import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'core/storage/cookie_storage.dart';
import 'core/network/api_client.dart';
import 'core/router/app_router.dart';
import 'features/products/presentation/widgets/shop_widgets.dart';

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
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        useMaterial3: true,
        colorScheme: ColorScheme.fromSeed(
          seedColor: shopBlue,
          primary: shopBlue,
          secondary: shopOrange,
        ),
        scaffoldBackgroundColor: const Color(0xfff7fafb),
        appBarTheme: const AppBarTheme(
          backgroundColor: Colors.white,
          foregroundColor: shopInk,
          elevation: 0,
        ),
        inputDecorationTheme: InputDecorationTheme(
          filled: true,
          fillColor: Colors.white,
          border: OutlineInputBorder(
            borderRadius: BorderRadius.circular(10),
            borderSide: const BorderSide(color: Color(0xffe0e6eb)),
          ),
        ),
        filledButtonTheme: FilledButtonThemeData(
          style: FilledButton.styleFrom(backgroundColor: shopOrange),
        ),
        navigationBarTheme: const NavigationBarThemeData(
          backgroundColor: Colors.white,
          indicatorColor: Color(0xffe2f0f6),
        ),
      ),
    );
  }
}
