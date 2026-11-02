import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:ecommerce_app/features/orders/data/models/order_model.dart';
import 'package:ecommerce_app/features/orders/presentation/pages/order_detail_screen.dart';
import 'package:ecommerce_app/features/orders/presentation/providers/order_providers.dart';

void main() {
  group('4. Order & Warranty Data Test (Không tự suy diễn trạng thái)', () {
    test('Kịch bản 1: Order model KHÔNG tự suy diễn canCancel từ status (Chỉ true khi allowedStatuses có CANCELLED)', () {
      // Arrange 1: Status = 'PENDING', nhưng allowedStatuses KHÔNG có 'CANCELLED'
      // Trong nghiệp vụ thực tế, có thể đơn hàng đã khóa sổ hoặc đang đóng gói nên backend không cho hủy
      final jsonCannotCancel = {
        'id': 'ord-001',
        'orderCode': 'ORD001',
        'status': 'PENDING',
        'totalAmount': 1500000,
        'allowedStatuses': ['PROCESSING'], // Không có CANCELLED
        'items': [],
      };

      // Act 1
      final order1 = Order.fromJson(jsonCannotCancel);

      // Assert 1: Không được tự ý gán canCancel = true chỉ vì status == PENDING
      expect(
        order1.canCancel,
        isFalse,
        reason: 'Order model tuyệt đối không được tự suy đoán quyền hủy đơn nếu backend không cho phép trong allowedStatuses',
      );

      // Arrange 2: allowedStatuses chứa 'CANCELLED'
      final jsonCanCancel = {
        'id': 'ord-002',
        'orderCode': 'ORD002',
        'status': 'PENDING',
        'totalAmount': 2500000,
        'allowedStatuses': ['CANCELLED', 'PROCESSING'],
        'items': [],
      };

      // Act 2
      final order2 = Order.fromJson(jsonCanCancel);

      // Assert 2
      expect(order2.canCancel, isTrue);
    });

    testWidgets('Kịch bản 2: UI hiển thị thông điệp Serial/IMEI sẽ được cập nhật khi IMEI/Serial từ backend trả về null', (tester) async {
      // Arrange: Đơn hàng mới đặt thành công, backend chưa gán số Serial / IMEI (trường null)
      final testOrder = Order(
        id: 'ord-serial-test',
        orderCode: 'ORD999',
        status: 'CONFIRMED',
        totalAmount: 32000000,
        allowedStatuses: const [],
        items: [
          OrderItem(
            id: 'item-1',
            productId: 'prod-macbook',
            productName: 'MacBook Air M3',
            quantity: 1,
            unitPrice: 32000000,
            serialNumber: null, // Chưa có serial
            imeiNumbers: const [], // Chưa có IMEI
          ),
        ],
      );

      // Act: Render màn hình OrderDetailScreen
      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            orderDetailProvider('ord-serial-test').overrideWith(
              (ref) async => testOrder,
            ),
          ],
          child: const MaterialApp(
            home: OrderDetailScreen(orderId: 'ord-serial-test'),
          ),
        ),
      );
      await tester.pumpAndSettle();

      // Assert:
      // 1. Tuyệt đối không render chữ "null" hoặc "Serial: null" thô thiển lên giao diện
      expect(find.textContaining('null'), findsNothing);

      // 2. Phải hiển thị dòng thông báo giải thích rõ ràng cho khách hàng
      expect(
        find.text('Serial/IMEI sẽ được cập nhật khi đơn được xử lý'),
        findsOneWidget,
        reason: 'UI phải hiển thị câu thông báo chuyên nghiệp thay vì để trống hoặc hiện null',
      );

      // 3. Phải hiển thị tên sản phẩm và giá tiền chuẩn xác
      expect(find.text('MacBook Air M3'), findsOneWidget);
      expect(find.text('32000000 đ'), findsOneWidget);
    });
  });
}

