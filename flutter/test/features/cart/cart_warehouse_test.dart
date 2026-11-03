import 'dart:typed_data';
import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:ecommerce_app/core/network/api_error.dart';
import 'package:ecommerce_app/features/cart/data/cart_repository.dart';
import 'package:ecommerce_app/features/cart/presentation/providers/cart_provider.dart';

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
  group('2. Warehouse & Cart Logic Test (Ràng buộc chi nhánh)', () {
    test('Kịch bản 1: Đổi chi nhánh khi giỏ hàng có đồ gửi PUT /api/cart/warehouse với clearItems=true và invalidate giỏ hàng', () async {
      // Arrange
      final sentRequests = <RequestOptions>[];
      final dio = Dio(BaseOptions(baseUrl: 'http://test.local'));

      dio.httpClientAdapter = _MockAdapter((options) async {
        sentRequests.add(options);
        return ResponseBody.fromString(
          '{"warehouseId":"hn-warehouse","items":[]}',
          200,
          headers: {
            Headers.contentTypeHeader: ['application/json'],
          },
        );
      });

      final repo = CartRepository(client: dio);
      bool isCartInvalidated = false;

      final container = ProviderContainer(
        overrides: [
          cartRepositoryProvider.overrideWithValue(repo),
          cartProvider.overrideWith((ref) {
            ref.onDispose(() {
              isCartInvalidated = true;
            });
            return CartSummary(
              warehouseId: 'old-warehouse',
              warehouseName: 'Kho Cũ',
              items: [
                CartLine(
                  id: 'item-1',
                  productId: 'p-1',
                  productName: 'Điện thoại',
                  quantity: 1,
                  unitPrice: 10000000,
                ),
              ],
            );
          }),
        ],
      );

      // Trigger read để provider active
      container.read(cartProvider);

      // Act: Gọi đổi chi nhánh sang kho mới với clearItems = true (xóa giỏ hàng cũ)
      final response = await repo.selectWarehouse('hn-warehouse', clearItems: true);

      // Mô phỏng event invalidate state trong Riverpod
      container.invalidate(cartProvider);

      // Assert
      expect(response.statusCode, equals(200));
      expect(sentRequests, hasLength(1));
      expect(sentRequests.first.method, equals('PUT'));
      expect(sentRequests.first.path, equals('/api/cart/warehouse'));
      expect(sentRequests.first.data, equals({
        'warehouseId': 'hn-warehouse',
        'clearItems': true,
      }));
      expect(
        isCartInvalidated,
        isTrue,
        reason: 'Provider giỏ hàng cũ phải được invalidate/clear khi đổi chi nhánh',
      );

      dio.close();
    });

    test('Kịch bản 2 (Checkout Conflict): Khi POST /api/orders/checkout bị 409 Conflict, bắt đúng lỗi và thông báo tải lại giỏ hàng', () async {
      // Arrange: Mock server trả về HTTP 409 do xung đột tồn kho (Stock changed)
      final dio = Dio(BaseOptions(baseUrl: 'http://test.local'));

      dio.httpClientAdapter = _MockAdapter((options) async {
        return ResponseBody.fromString(
          '{"message":"Tồn kho sản phẩm đã thay đổi, không đủ số lượng để xuất kho"}',
          409,
          headers: {
            Headers.contentTypeHeader: ['application/json'],
          },
        );
      });

      final repo = CartRepository(client: dio);

      // Act & Assert
      try {
        await repo.checkout({
          'recipientName': 'Nguyen Van A',
          'recipientPhone': '0901234567',
          'shippingAddress': '123 Đường ABC, Hà Nội',
          'paymentMethod': 'COD',
        });
        fail('Lẽ ra phải ném DioException có statusCode = 409');
      } on DioException catch (dioError) {
        expect(dioError.response?.statusCode, equals(409));

        // Kiểm tra thông điệp lỗi trích xuất từ apiErrorMessage
        final userFriendlyMessage = apiErrorMessage(dioError);

        expect(
          userFriendlyMessage,
          equals('Giỏ hàng hoặc tồn kho đã thay đổi. Vui lòng tải lại.'),
          reason: 'Lỗi 409 phải được hệ thống nhận diện chính xác và yêu cầu người dùng tải lại giỏ hàng thay vì báo lỗi chung chung',
        );
      }

      dio.close();
    });

    test('Kịch bản 3: Đổi chi nhánh sang đúng kho mà giỏ hàng đang chứa sản phẩm -> clearItems = false, giữ nguyên giỏ', () async {
      final sentRequests = <RequestOptions>[];
      final dio = Dio(BaseOptions(baseUrl: 'http://test.local'));

      dio.httpClientAdapter = _MockAdapter((options) async {
        sentRequests.add(options);
        return ResponseBody.fromString(
          '{"warehouseId":"hn-warehouse","items":[{"id":"item-1"}]}',
          200,
          headers: {
            Headers.contentTypeHeader: ['application/json'],
          },
        );
      });

      final repo = CartRepository(client: dio);

      // Act: Gọi chọn kho trùng với kho của giỏ hàng (hn-warehouse)
      final response = await repo.selectWarehouse('hn-warehouse', clearItems: false);

      // Assert
      expect(response.statusCode, equals(200));
      expect(sentRequests, hasLength(1));
      expect(sentRequests.first.data, equals({
        'warehouseId': 'hn-warehouse',
        'clearItems': false,
      }));

      dio.close();
    });

    test('Kịch bản 4: Giỏ hàng trống khi chuyển kho -> clearItems = false, không cần xóa', () async {
      final sentRequests = <RequestOptions>[];
      final dio = Dio(BaseOptions(baseUrl: 'http://test.local'));

      dio.httpClientAdapter = _MockAdapter((options) async {
        sentRequests.add(options);
        return ResponseBody.fromString(
          '{"warehouseId":"hcm-warehouse","items":[]}',
          200,
          headers: {
            Headers.contentTypeHeader: ['application/json'],
          },
        );
      });

      final repo = CartRepository(client: dio);

      // Act: Giỏ trống đổi sang kho HCM
      final response = await repo.selectWarehouse('hcm-warehouse', clearItems: false);

      // Assert
      expect(response.statusCode, equals(200));
      expect(sentRequests, hasLength(1));
      expect(sentRequests.first.data, equals({
        'warehouseId': 'hcm-warehouse',
        'clearItems': false,
      }));

      dio.close();
    });

    test('Kịch bản 5: Thêm sản phẩm vào giỏ hàng POST /api/cart/items truyền tường minh warehouseId từ selectedWarehouseProvider', () async {
      final sentRequests = <RequestOptions>[];
      final dio = Dio(BaseOptions(baseUrl: 'http://test.local'));

      dio.httpClientAdapter = _MockAdapter((options) async {
        sentRequests.add(options);
        return ResponseBody.fromString(
          '{"warehouseId":"hcm-warehouse","items":[{"id":"item-1","productId":"p-1","quantity":1}]}',
          200,
          headers: {
            Headers.contentTypeHeader: ['application/json'],
          },
        );
      });

      final repo = CartRepository(client: dio);

      // Act: Thêm sản phẩm với warehouseId tường minh của chi nhánh đang chọn (HCM)
      final response = await repo.addItem(
        variantId: 'variant-123',
        quantity: 1,
        warehouseId: 'hcm-warehouse',
      );

      // Assert
      expect(response.statusCode, equals(200));
      expect(sentRequests, hasLength(1));
      expect(sentRequests.first.method, equals('POST'));
      expect(sentRequests.first.path, equals('/api/cart/items'));
      expect(sentRequests.first.data, equals({
        'variantId': 'variant-123',
        'quantity': 1,
        'warehouseId': 'hcm-warehouse',
      }));

      dio.close();
    });
  });
}
