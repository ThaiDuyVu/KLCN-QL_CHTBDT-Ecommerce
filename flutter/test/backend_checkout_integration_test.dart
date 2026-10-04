// Opt-in: creates one clearly labelled COD order for a development seed account,
// then cancels that order. Never clears an existing cart.
import 'dart:io';
import 'package:cookie_jar/cookie_jar.dart';
import 'package:dio/dio.dart';
import 'package:dio_cookie_manager/dio_cookie_manager.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:ecommerce_app/core/network/api_client.dart';
import 'package:ecommerce_app/core/storage/cookie_storage.dart';
import 'package:ecommerce_app/features/cart/data/cart_repository.dart';

void main() {
  const enabled = bool.fromEnvironment('RUN_BACKEND_CHECKOUT_TEST');
  test(
    'Real backend cookie CSRF / branch / add / PATCH / DELETE / COD checkout / cancel',
    () async {
      final directory = await Directory.systemTemp.createTemp(
        'ecommerce-contract-test-',
      );
      CookieStorage.cookieJar = PersistCookieJar(
        storage: FileStorage(directory.path),
      );
      ApiClient.init();
      addTearDown(() async {
        ApiClient.dio.close();
        await directory.delete(recursive: true);
      });
      final client = ApiClient.dio;
      await client.post(
        '/api/auth/login',
        data: {'username': 'seed.customer2', 'password': '123'},
      );
      final repo = CartRepository();
      final initial = (await repo.fetchCart()).data as Map;
      expect(
        initial['items'] as List,
        isEmpty,
        reason: 'Refuse to modify an existing development cart',
      );
      final warehouses = (await client.get(
        '/api/warehouses',
        queryParameters: {'size': 100},
      )).data;
      final list = warehouses is List
          ? warehouses
          : (warehouses as Map)['content'] as List;
      final warehouse = list.firstWhere(
        (w) => w['warehouseName'] == 'Kho phát triển',
      );
      await repo.selectWarehouse(warehouse['warehouseId'] as String);
      final products =
          (await client.get(
                '/api/v1/products',
                queryParameters: {
                  'warehouseId': warehouse['warehouseId'],
                  'status': 'ACTIVE',
                  'size': 100,
                },
              )).data['content']
              as List;
      final product = products.firstWhere(
        (p) => (p['availableQuantity'] as num? ?? 0) >= 2,
      );
      final variants =
          (await client.get(
                '/api/v1/product-variants',
                queryParameters: {'productId': product['productId']},
              )).data
              as List;
      final variant =
          variants.firstWhere((v) => v['status'] == 'ACTIVE')['variantId']
              as String;
      // Security negative case cannot reach cart writes.
      final unprotected = Dio(BaseOptions(baseUrl: ApiClient.baseUrl));
      unprotected.interceptors.add(CookieManager(CookieStorage.cookieJar));
      final rejected = await unprotected.post(
        '/api/cart/items',
        data: {'variantId': variant, 'quantity': 1},
        options: Options(validateStatus: (_) => true),
      );
      expect(rejected.statusCode, 403);
      unprotected.close();
      String? orderId;
      try {
        final added =
            (await repo.addItem(variantId: variant, quantity: 1)).data as Map;
        final item = (added['items'] as List).single['cartItemId'] as String;
        final changed = (await repo.updateItem(item, 2)).data as Map;
        expect((changed['items'] as List).single['quantity'], 2);
        expect(((await repo.removeItem(item)).data['items'] as List), isEmpty);
        await repo.addItem(variantId: variant, quantity: 1);
        final created = await repo.checkout({
          'recipientName': 'Khách kiểm thử Flutter',
          'recipientPhone': '0900000000',
          'shippingAddress': 'Địa chỉ kiểm thử, TP. Hồ Chí Minh',
          'paymentMethod': 'COD',
          'note': 'Đơn kiểm thử tự động Flutter, hủy sau khi xác minh',
        });
        expect(created.statusCode, 201);
        orderId = created.data['orderId'] as String;
        expect(((await repo.fetchCart()).data['items'] as List), isEmpty);
        expect((await client.get('/api/orders/mine/$orderId')).statusCode, 200);
      } finally {
        if (orderId != null) {
          expect(
            (await client.post('/api/orders/mine/$orderId/cancel')).statusCode,
            200,
          );
        } else {
          // Only remove items added by this test; initial cart was verified empty.
          final cart = (await repo.fetchCart()).data['items'] as List;
          for (final item in cart) {
            await repo.removeItem(item['cartItemId'] as String);
          }
        }
      }
    },
    skip: !enabled,
  );
}
