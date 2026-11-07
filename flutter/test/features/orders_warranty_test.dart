import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:ecommerce_app/features/orders/data/models/order_model.dart';
import 'package:ecommerce_app/features/returns/data/return_repository.dart';
import 'package:ecommerce_app/features/reviews/data/review_repository.dart';
import 'package:ecommerce_app/features/products/data/models/product_model.dart';
import 'package:ecommerce_app/features/chatbot/data/chat_repository.dart';
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
      expect(find.textContaining('32.000.000'), findsWidgets);
    });
  
    test('Kịch bản P1.1: đọc thông tin vận chuyển từ backend, không có shipment thì để null', () {
      final shipped = Order.fromJson({
        'orderId': 'ord-ship',
        'orderCode': 'ORD-SHIP',
        'status': 'SHIPPED',
        'totalAmount': 1000000,
        'items': [],
        'shipment': {
          'status': 'SHIPPING',
          'employeeName': 'Phan Văn An',
          'employeePhone': '0901234567',
          'shippingProvider': 'Giao nội bộ',
          'trackingNumber': 'GH-ABC123',
          'shippedDate': '2026-11-06T03:00:00Z',
        },
      });
      expect(shipped.shipment, isNotNull);
      expect(shipped.shipment!.trackingNumber, 'GH-ABC123');
      expect(OrderShipment.statusLabel(shipped.shipment!.status), 'Đang giao');
      expect(OrderShipment.statusLabel('FAILED'), 'Giao không thành công');
      expect(Order.fromJson({'orderId': 'o', 'status': 'PENDING', 'totalAmount': 1, 'items': []}).shipment, isNull);
    });

    test('Kịch bản P1.2: đọc yêu cầu đổi trả từ backend', () {
      final row = ReturnRequestSummary.fromJson({
        'returnId': 'r1',
        'returnCode': 'TH-20261106-ABC123',
        'status': 'approved',
        'refundAmount': 10000000,
        'reason': 'Máy lỗi',
        'items': [
          {'orderItemId': 'i1', 'productName': 'Điện thoại X', 'serialNumber': 'SN-1', 'quantity': 1},
        ],
      });
      expect(row.status, 'APPROVED');
      expect(ReturnRequestSummary.statusLabel(row.status), 'Đã duyệt, chờ nhận hàng');
      expect(row.items.single.orderItemId, 'i1');
      expect(row.refundAmount, 10000000);
    });

    test('Kịch bản P1.3: đọc số hóa đơn của đơn đã giao', () {
      final order = Order.fromJson({
        'orderId': 'o1', 'status': 'DELIVERED', 'totalAmount': 1, 'items': [],
        'invoice': {'invoiceNumber': 'HD-20261106-0001', 'status': 'ISSUED'},
      });
      expect(order.invoiceNumber, 'HD-20261106-0001');
      expect(Order.fromJson({'orderId': 'o2', 'status': 'PENDING', 'totalAmount': 1, 'items': []}).invoiceNumber, isNull);
    });

    test('Kịch bản P1.4: đọc đánh giá và điểm trung bình của sản phẩm', () {
      final page = ProductReviewPage.fromJson({
        'averageRating': 4.5,
        'totalReviews': 2,
        'content': [
          {'reviewId': 'r1', 'orderItemId': 'i1', 'customerName': 'An', 'rating': 5, 'replyText': 'Cảm ơn'},
        ],
      });
      expect(page.averageRating, 4.5);
      expect(page.reviews.single.reply, 'Cảm ơn');
      expect(ProductReviewPage.fromJson({'totalReviews': 0, 'content': []}).averageRating, isNull);
    });

    test('Kịch bản P1.5: nhận diện sản phẩm đang khuyến mãi', () {
      final sale = Product.fromJson({
        'productId': 'p1', 'productName': 'TV', 'effectivePrice': 9000000, 'originalPrice': 10000000,
        'promotion': {'promotionName': 'Sale cuối năm'},
      });
      expect(sale.onSale, isTrue);
      expect(sale.discountPercent, 10);
      expect(sale.promotionName, 'Sale cuối năm');
      expect(Product.fromJson({'productId': 'p2', 'effectivePrice': 5, 'originalPrice': 5}).onSale, isFalse);
    });

    test('Kịch bản P1.6: đọc phản hồi chatbot và sinh mã phiên hợp lệ', () {
      final reply = ChatReply.fromJson({
        'session_id': 's',
        'message': 'Gợi ý cho bạn',
        'products': [
          {'product_id': 'p1', 'product_name': 'Laptop A', 'effective_price': '18990000.00'},
        ],
        'suggested_questions': ['So sánh với Laptop B'],
      });
      expect(reply.products.single.effectivePrice, 18990000);
      expect(reply.suggestions.single, 'So sánh với Laptop B');
      expect(RegExp(r'^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$').hasMatch(ChatRepository.newSessionId()), isTrue);
    });

    test('Kịch bản V14: đọc yêu cầu đổi hàng và sản phẩm đổi', () {
      final row = ReturnRequestSummary.fromJson({'returnId': 'r', 'returnCode': 'TH-1', 'status': 'EXCHANGED', 'requestType': 'EXCHANGE',
        'exchangeOrderCode': 'EX-1', 'refundAmount': 400000, 'items': []});
      expect(ReturnRequestSummary.statusLabel(row.status), 'Đã đổi hàng');
      expect(row.requestType, 'EXCHANGE');
      expect(row.exchangeOrderCode, 'EX-1');
      expect(ExchangeCandidate.fromJson({'variantId': 'v', 'sku': 'TN-Y', 'productName': 'Tai nghe', 'price': 1000000}).price, 1000000);
    });
});
}

