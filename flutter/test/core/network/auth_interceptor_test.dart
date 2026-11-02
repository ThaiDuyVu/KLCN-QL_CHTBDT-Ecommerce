import 'dart:typed_data';
import 'package:cookie_jar/cookie_jar.dart';
import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:ecommerce_app/core/network/auth_interceptor.dart';

class _MockAdapter implements HttpClientAdapter {
  final Future<ResponseBody> Function(RequestOptions options) handler;
  _MockAdapter(this.handler);

  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? stream,
    Future<void>? cancel,
  ) async {
    return handler(options);
  }

  @override
  void close({bool force = false}) {}
}

void main() {
  group('1. Core Network & Security Test (Auth Interceptor & CSRF)', () {
    test('Scenario 1 (Single-flight refresh): 3 concurrent 401 calls trigger /api/auth/refresh exactly once', () async {
      // Arrange
      int refreshCallCount = 0;
      final jar = CookieJar();
      final dio = Dio(BaseOptions(baseUrl: 'http://test.local'));

      // Giả lập callback refresh gọi API /api/auth/refresh
      Future<bool> mockRefreshToken() async {
        refreshCallCount++;
        // Mô phỏng độ trễ mạng khi refresh
        await Future.delayed(const Duration(milliseconds: 30));
        return true;
      }

      final interceptor = AuthInterceptor(
        jar,
        () async => 'csrf-token-123',
        onRefreshToken: mockRefreshToken,
        dio: dio,
      );

      // Adapter trả về 401 ở lần gọi đầu, 200 khi được retry sau refresh
      dio.httpClientAdapter = _MockAdapter((options) async {
        if (options.extra['retried'] != true) {
          options.extra['retried'] = true;
          return ResponseBody.fromString(
            '{"error":"Unauthorized"}',
            401,
            headers: {
              Headers.contentTypeHeader: ['application/json'],
            },
          );
        }
        return ResponseBody.fromString(
          '{"status":"ok"}',
          200,
          headers: {
            Headers.contentTypeHeader: ['application/json'],
          },
        );
      });

      dio.interceptors.add(interceptor);

      // Act: Giả lập 3 API (cart, orders, warranty) cùng gọi và bị 401 cùng lúc
      final future1 = dio.get('/api/cart');
      final future2 = dio.get('/api/orders');
      final future3 = dio.get('/api/warranty');

      final responses = await Future.wait([future1, future2, future3]);

      // Assert
      expect(
        refreshCallCount,
        equals(1),
        reason: 'Hệ thống phải có cơ chế single-flight mutex: chỉ gọi refresh đúng 1 lần cho nhiều request 401 đồng thời',
      );
      for (final resp in responses) {
        expect(resp.statusCode, equals(200));
      }

      dio.close();
    });

    test('Scenario 2 (CSRF Handling): Mutating requests (POST, PUT, DELETE) call ensureCsrfToken and attach X-XSRF-TOKEN', () async {
      // Arrange
      final jar = CookieJar();
      int ensureCsrfCallCount = 0;

      Future<String> mockEnsureCsrfToken() async {
        ensureCsrfCallCount++;
        await jar.saveFromResponse(Uri.parse('http://test.local'), [
          Cookie('XSRF-TOKEN', 'test-xsrf-token')..path = '/',
        ]);
        return 'test-xsrf-token';
      }

      final dio = Dio(BaseOptions(baseUrl: 'http://test.local'));
      final interceptor = AuthInterceptor(jar, mockEnsureCsrfToken);
      final capturedHeaders = <String, Map<String, dynamic>>{};

      dio.httpClientAdapter = _MockAdapter((options) async {
        capturedHeaders[options.method] = Map<String, dynamic>.from(options.headers);
        return ResponseBody.fromString('{}', 200);
      });
      dio.interceptors.add(interceptor);

      // Act: Gọi GET, POST, PUT, DELETE
      await dio.get('/api/products');
      await dio.post('/api/cart/items', data: {'productId': 'p1'});
      await dio.put('/api/cart/warehouse', data: {'warehouseId': 'w1'});
      await dio.delete('/api/cart/items/item1');

      // Assert:
      // 1. GET request không được ép gọi ensureCsrfToken và không cần gắn header X-XSRF-TOKEN
      expect(capturedHeaders['GET']?['X-XSRF-TOKEN'], isNull);

      // 2. 3 mutation request (POST, PUT, DELETE) phải kích hoạt ensureCsrfToken
      expect(
        ensureCsrfCallCount,
        equals(3),
        reason: 'Mỗi mutation request phải đảm bảo có CSRF token',
      );

      // 3. Header X-XSRF-TOKEN phải có mặt trên cả 3 mutation request
      expect(capturedHeaders['POST']?['X-XSRF-TOKEN'], equals('test-xsrf-token'));
      expect(capturedHeaders['PUT']?['X-XSRF-TOKEN'], equals('test-xsrf-token'));
      expect(capturedHeaders['DELETE']?['X-XSRF-TOKEN'], equals('test-xsrf-token'));

      dio.close();
    });
  });
}
