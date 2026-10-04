import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:ecommerce_app/features/auth/presentation/pages/login_screen.dart';

void main() {
  testWidgets('Login form requires username and password', (tester) async {
    await tester.pumpWidget(const ProviderScope(
      child: MaterialApp(home: LoginScreen()),
    ));

    expect(find.widgetWithText(TextFormField, 'Tài khoản'), findsOneWidget);
    expect(find.widgetWithText(TextFormField, 'Mật khẩu'), findsOneWidget);
    await tester.tap(find.widgetWithText(ElevatedButton, 'Đăng nhập'));
    await tester.pump();

    expect(find.text('Nhập tài khoản'), findsOneWidget);
    expect(find.text('Nhập mật khẩu'), findsOneWidget);
  });
}
