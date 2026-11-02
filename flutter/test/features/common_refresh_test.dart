import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:ecommerce_app/features/cart/presentation/pages/cart_screen.dart';
import 'package:ecommerce_app/features/cart/presentation/providers/cart_provider.dart';
import 'package:ecommerce_app/features/orders/data/models/order_model.dart';
import 'package:ecommerce_app/features/orders/presentation/pages/order_list_screen.dart';
import 'package:ecommerce_app/features/orders/presentation/providers/order_providers.dart';

void main() {
  group('6.2. Common Refresh Test (Pull-to-refresh & Invalidation)', () {
    testWidgets('Kịch bản 1: Kéo vuốt xuống (Pull-to-refresh) trên CartScreen kích hoạt reload và làm mới giỏ hàng', (tester) async {
      // Arrange: Biến đếm số lần API/Provider được gọi
      int cartFetchCount = 0;

      final testCart = CartSummary(
        warehouseName: 'Kho Cầu Giấy',
        subtotal: 25000000,
        items: [
          CartLine(
            id: 'item-101',
            productId: 'prod-ip16',
            productName: 'iPhone 16 Pro',
            quantity: 1,
            unitPrice: 25000000,
          ),
        ],
      );

      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            cartProvider.overrideWith((ref) async {
              cartFetchCount++;
              return testCart;
            }),
            cartListProvider.overrideWith((ref) async => [testCart]),
          ],
          child: const MaterialApp(
            home: CartScreen(),
          ),
        ),
      );

      // Chờ giao diện render xong lần đầu
      await tester.pumpAndSettle();

      // Assert lần đầu: đã load 1 lần và hiển thị sản phẩm
      expect(cartFetchCount, equals(1));
      expect(find.text('iPhone 16 Pro'), findsOneWidget);

      // Act: Mô phỏng cử chỉ vuốt kéo xuống (Pull-to-refresh)
      // Kéo từ giữa danh sách xuống 300px
      await tester.fling(find.byType(ListView), const Offset(0, 300), 1000);
      await tester.pump(); // Kích hoạt animation của RefreshIndicator
      await tester.pump(const Duration(seconds: 1)); // Chờ spinner chạy
      await tester.pumpAndSettle(); // Chờ hoàn tất

      // Assert:
      // 1. Phải có RefreshIndicator trên màn hình
      expect(find.byType(RefreshIndicator), findsOneWidget);

      // 2. cartFetchCount phải tăng lên 2 do ref.invalidate() ép provider gọi lại
      expect(
        cartFetchCount,
        greaterThanOrEqualTo(2),
        reason: 'Hành động kéo vuốt làm mới phải gọi ref.invalidate() để ép API tải lại dữ liệu mới nhất',
      );
    });

    testWidgets('Kịch bản 2: Kéo vuốt xuống (Pull-to-refresh) trên OrderListScreen kích hoạt ref.invalidate(myOrdersProvider)', (tester) async {
      // Arrange
      int ordersFetchCount = 0;

      final initialOrders = [
        Order(
          id: 'ord-refresh-1',
          orderCode: 'DH-001',
          status: 'DELIVERED',
          totalAmount: 15000000,
          allowedStatuses: const [],
          items: const [],
        ),
      ];

      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            myOrdersProvider.overrideWith((ref) async {
              ordersFetchCount++;
              return initialOrders;
            }),
          ],
          child: const MaterialApp(
            home: OrderListScreen(),
          ),
        ),
      );

      await tester.pumpAndSettle();
      expect(ordersFetchCount, equals(1));
      expect(find.text('Đơn DH-001'), findsOneWidget);

      // Act: Mô phỏng Pull-to-refresh trên danh sách đơn hàng
      await tester.fling(find.byType(ListView), const Offset(0, 300), 1000);
      await tester.pump();
      await tester.pump(const Duration(seconds: 1));
      await tester.pumpAndSettle();

      // Assert:
      expect(find.byType(RefreshIndicator), findsOneWidget);
      expect(
        ordersFetchCount,
        greaterThanOrEqualTo(2),
        reason: 'Pull-to-refresh trên OrderListScreen phải làm mới danh sách đơn hàng',
      );
    });
  });
}

