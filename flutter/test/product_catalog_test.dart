import 'package:flutter/material.dart';
import 'package:ecommerce_app/features/main/presentation/pages/main_main_screen.dart';
import 'package:ecommerce_app/features/products/presentation/pages/product_detail_screen.dart';
import 'package:dio/dio.dart';
import 'package:ecommerce_app/core/network/api_client.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:ecommerce_app/features/products/data/models/product_model.dart';
import 'package:ecommerce_app/features/products/presentation/pages/home_screen.dart';
import 'package:ecommerce_app/features/products/presentation/providers/product_providers.dart';
import 'package:ecommerce_app/features/products/presentation/widgets/shop_widgets.dart';
import 'package:ecommerce_app/features/auth/presentation/providers/auth_provider.dart';
import 'package:ecommerce_app/features/auth/data/auth_repository.dart';
import 'package:ecommerce_app/features/warehouse/presentation/providers/warehouse_providers.dart';

class TestRepository extends AuthRepository {
  final String role;
  TestRepository([this.role = 'CUSTOMER']);
  @override
  Future<Response> getMe() async => Response(
    requestOptions: RequestOptions(),
    statusCode: 200,
    data: {'roleName': role},
  );
}

class TestAuth extends AuthNotifier {
  TestAuth(super.repo);
  @override
  Future<bool> login(String username, String password) async => false;
}

void main() {
  setUpAll(() {
    ApiClient.dio = Dio();
  });
  test('Reads backend prices and primary image without inventing stock', () {
    final product = Product.fromJson({
      'productId': '1',
      'productName': 'Phone',
      'effectivePrice': 12000000,
      'originalPrice': 15000000,
      'primaryImageUrl': '/images/products/iphone-16/main.jpg',
    });
    expect(product.price, 12000000);
    expect(product.originalPrice, 15000000);
    expect(product.availableQuantity, 0);
    expect(product.imageUrl, '/images/products/iphone-16/main.jpg');
    expect(money(product.price), '12.000.000 ₫');
    expect(money(null), 'Xem giá phiên bản');
  });
  testWidgets(
    'Catalog searches real data and allows viewing out of stock products',
    (tester) async {
      final products = [
        Product(
          id: '1',
          name: 'iPhone 16',
          price: 20000000,
          availableQuantity: 0,
          brand: 'Apple',
          category: 'Điện thoại',
        ),
        Product(
          id: '2',
          name: 'Dell XPS',
          price: 30000000,
          availableQuantity: 2,
          brand: 'Dell',
          category: 'Laptop',
        ),
      ];
      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            productListProvider.overrideWith((ref) async => products),
            warehouseListProvider.overrideWith((ref) async => []),
            productDetailProvider(
              '1',
            ).overrideWith((ref) async => products.first),
            productVariantsProvider('1').overrideWith((ref) async => []),
            authProvider.overrideWith((ref) => TestAuth(TestRepository())),
          ],
          child: const MaterialApp(home: HomeScreen()),
        ),
      );
      await tester.pumpAndSettle();
      expect(find.text('iPhone 16'), findsOneWidget);
      expect(find.text('Dell XPS'), findsOneWidget);
      await tester.enterText(find.byType(TextField), 'apple');
      await tester.pumpAndSettle();
      expect(find.text('iPhone 16'), findsOneWidget);
      expect(find.text('Dell XPS'), findsNothing);
      await tester.ensureVisible(find.text('iPhone 16'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('iPhone 16'));
      await tester.pumpAndSettle();
      expect(find.text('Chi tiết sản phẩm'), findsOneWidget);
      expect(
        tester.widget<FilledButton>(find.byType(FilledButton)).onPressed,
        isNull,
      );
      expect(tester.takeException(), isNull);
    },
  );
  for (final role in ['CUSTOMER', 'ADMIN', 'MANAGER', 'STAFF']) {
    testWidgets('$role has appropriate mobile destinations', (tester) async {
      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            authProvider.overrideWith((ref) => TestAuth(TestRepository(role))),
            productListProvider.overrideWith((ref) async => []),
            warehouseListProvider.overrideWith((ref) async => []),
          ],
          child: const MaterialApp(home: MainMainScreen()),
        ),
      );
      await tester.pumpAndSettle();
      expect(
        find.byType(NavigationDestination),
        findsNWidgets(role == 'CUSTOMER' ? 4 : 2),
      );
      expect(
        find.text('Giỏ hàng'),
        role == 'CUSTOMER' ? findsOneWidget : findsNothing,
      );
      expect(
        find.text('Đơn hàng'),
        role == 'CUSTOMER' ? findsOneWidget : findsNothing,
      );
      expect(tester.takeException(), isNull);
    });
  }
  for (final status in [401, 403]) {
    test('GET $status handles session without calling mutations', () async {
      final container = ProviderContainer(
        overrides: [
          authProvider.overrideWith((ref) => TestAuth(TestRepository())),
        ],
      );
      addTearDown(container.dispose);
      container.read(authProvider);
      await Future<void>.delayed(Duration.zero);
      final requestProvider = Provider<Future<void>>(
        (ref) => readWithSession(ref, () async {
          throw DioException(
            requestOptions: RequestOptions(),
            response: Response(
              requestOptions: RequestOptions(),
              statusCode: status,
            ),
          );
        }),
      );
      await expectLater(
        container.read(requestProvider),
        throwsA(isA<DioException>()),
      );
      expect(
        container.read(authProvider).status,
        status == 401
            ? AuthStateStatus.unauthenticated
            : AuthStateStatus.authenticated,
      );
    });
  }
}
