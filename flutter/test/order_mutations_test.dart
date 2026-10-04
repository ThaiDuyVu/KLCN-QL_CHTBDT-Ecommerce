import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:ecommerce_app/features/checkout/presentation/pages/checkout_screen.dart';
import 'package:ecommerce_app/features/cart/presentation/providers/cart_provider.dart';
import 'package:ecommerce_app/features/orders/presentation/providers/order_providers.dart';
import 'dart:io';
import 'dart:typed_data';
import 'package:cookie_jar/cookie_jar.dart';
import 'package:dio/dio.dart';
import 'package:dio_cookie_manager/dio_cookie_manager.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:ecommerce_app/core/network/auth_interceptor.dart';
import 'package:ecommerce_app/features/cart/data/cart_repository.dart';

class RecordingAdapter implements HttpClientAdapter {
  final requests = <RequestOptions>[];
  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? stream,
    Future<void>? cancel,
  ) async {
    requests.add(options);
    return ResponseBody.fromString(
      '{}',
      200,
      headers: {
        Headers.contentTypeHeader: ['application/json'],
      },
    );
  }

  @override
  void close({bool force = false}) {}
}

void main() {
  test(
    'Cart uses variant, branch PUT, quantity PATCH, item DELETE and checkout payload',
    () async {
      final dio = Dio(BaseOptions(baseUrl: 'http://example.test'));
      final adapter = RecordingAdapter();
      dio.httpClientAdapter = adapter;
      final repo = CartRepository(client: dio);
      await repo.selectWarehouse('warehouse');
      await repo.addItem(variantId: 'variant', quantity: 2);
      await repo.updateItem('item', 3);
      await repo.removeItem('item');
      await repo.checkout({
        'recipientName': 'Khách kiểm thử',
        'recipientPhone': '0900000000',
        'shippingAddress': 'Địa chỉ kiểm thử',
        'paymentMethod': 'COD',
      });
      expect(adapter.requests.map((r) => '${r.method} ${r.path}').toList(), [
        'PUT /api/cart/warehouse',
        'POST /api/cart/items',
        'PATCH /api/cart/items/item',
        'DELETE /api/cart/items/item',
        'POST /api/orders/checkout',
      ]);
      expect(adapter.requests[0].data, {
        'warehouseId': 'warehouse',
        'clearItems': false,
      });
      expect(adapter.requests[1].data, {'variantId': 'variant', 'quantity': 2});
      expect(adapter.requests[4].data['paymentMethod'], 'COD');
      dio.close();
    },
  );
  test(
    'CSRF initialized in shared jar preserves refresh cookie and sends matching header',
    () async {
      final jar = CookieJar();
      final uri = Uri.parse('http://example.test/api/auth/refresh');
      await jar.saveFromResponse(uri, [
        Cookie('REFRESH_TOKEN', 'test-refresh')..path = '/api/auth',
      ]);
      final dio = Dio(BaseOptions(baseUrl: 'http://example.test'));
      final adapter = RecordingAdapter();
      dio.httpClientAdapter = adapter;
      dio.interceptors.add(CookieManager(jar));
      dio.interceptors.add(
        AuthInterceptor(jar, () async {
          await jar.saveFromResponse(uri, [
            Cookie('XSRF-TOKEN', 'test-csrf')..path = '/',
          ]);
          return 'test-csrf';
        }),
      );
      await dio.post('/api/auth/refresh');
      expect(
        adapter.requests.single.headers['cookie'],
        contains('REFRESH_TOKEN=test-refresh'),
      );
      expect(
        adapter.requests.single.headers['cookie'],
        contains('XSRF-TOKEN=test-csrf'),
      );
      expect(adapter.requests.single.headers['X-XSRF-TOKEN'], 'test-csrf');
      expect(
        adapter.requests.single.headers.containsKey('Authorization'),
        isFalse,
      );
      dio.close();
    },
  );
  test('CSRF failure blocks mutation before HTTP adapter', () async {
    final dio = Dio(BaseOptions(baseUrl: 'http://example.test'));
    final adapter = RecordingAdapter();
    dio.httpClientAdapter = adapter;
    dio.interceptors.add(
      AuthInterceptor(
        CookieJar(),
        () async => throw StateError('fetch failed'),
      ),
    );
    await expectLater(
      dio.post('/api/orders/checkout'),
      throwsA(isA<DioException>()),
    );
    expect(adapter.requests, isEmpty);
    dio.close();
  });
  testWidgets(
    'Checkout form sends recipient fields and COD once, without deleting cart',
    (tester) async {
      final dio = Dio(BaseOptions(baseUrl: 'http://example.test'));
      final adapter = RecordingAdapter();
      dio.httpClientAdapter = adapter;
      final repo = CartRepository(client: dio);
      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            cartRepositoryProvider.overrideWithValue(repo),
            cartProvider.overrideWith(
              (ref) async => CartSummary(
                subtotal: 100000,
                items: [
                  CartLine(
                    id: 'item',
                    productId: '',
                    productName: 'Điện thoại',
                    quantity: 1,
                    unitPrice: 100000,
                  ),
                ],
              ),
            ),
            cartListProvider.overrideWith((ref) async => []),
            myOrdersProvider.overrideWith((ref) async => []),
          ],
          child: const MaterialApp(home: CheckoutScreen()),
        ),
      );
      await tester.pumpAndSettle();
      final fields = find.byType(TextFormField);
      await tester.enterText(fields.at(0), 'Khách kiểm thử');
      await tester.enterText(fields.at(1), '0900000000');
      await tester.enterText(fields.at(2), 'TP. Hồ Chí Minh');
      await tester.ensureVisible(find.text('Đặt hàng'));
      await tester.tap(find.text('Đặt hàng'));
      await tester.pumpAndSettle();
      expect(adapter.requests, hasLength(1));
      expect(adapter.requests.single.path, '/api/orders/checkout');
      expect(adapter.requests.single.data, {
        'recipientName': 'Khách kiểm thử',
        'recipientPhone': '0900000000',
        'shippingAddress': 'TP. Hồ Chí Minh',
        'note': '',
        'paymentMethod': 'COD',
      });
      expect(find.text('Đơn hàng của tôi'), findsOneWidget);
      expect(tester.takeException(), isNull);
      dio.close();
    },
  );
}
