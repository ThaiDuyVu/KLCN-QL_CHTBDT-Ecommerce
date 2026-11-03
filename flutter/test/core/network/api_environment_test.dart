import 'package:flutter_test/flutter_test.dart';
import 'package:ecommerce_app/core/network/api_client.dart';

void main() {
  group('5. API Environment & URL Normalization Test (Chống Hard-code)', () {
    test('Kịch bản 1: ApiClient.baseUrl không được hard-code localhost mà dùng String.fromEnvironment với fallback chuẩn', () {
      // Arrange & Act
      const defaultUrl = 'http://127.0.0.1:8080';
      const actualBaseUrl = ApiClient.baseUrl;

      // Assert:
      // Base URL không được phép là chuỗi hard-coded 'http://localhost:8080' vì thiết bị thật/Android Emulator không nhận diện được
      expect(
        actualBaseUrl,
        isNot(equals('http://localhost:8080')),
        reason: 'ApiClient.baseUrl không được hard-code localhost',
      );

      // Giá trị mặc định khi không truyền --dart-define=API_BASE_URL phải trỏ về IP 127.0.0.1 (ADB reverse)
      expect(actualBaseUrl, equals(defaultUrl));
    });

    test('Kịch bản 2: ApiClient.fixUrl quy đổi triệt để localhost, 10.0.2.2 và mọi dải IP LAN 192.168.x.x về 127.0.0.1', () {
      // Arrange: Các định dạng link ảnh / tài nguyên hay gặp trong DB hoặc server trả về
      const localhostUrl = 'http://localhost:8080/uploads/products/iphone16.png';
      const androidEmulatorUrl = 'http://10.0.2.2:8080/uploads/products/macbook.png';
      const lanWifiUrl = 'http://192.168.1.15:8080/uploads/products/ipad.png';
      const lanWifiSubnetUrl = 'http://192.168.100.99:8080/uploads/products/watch.png';
      const relativeUrl = '/uploads/products/airpods.png';
      const alreadyFixedUrl = 'http://127.0.0.1:8080/uploads/products/case.png';

      // Act & Assert:
      // 1. localhost chuyển thành 127.0.0.1
      expect(
        ApiClient.fixUrl(localhostUrl),
        equals('http://127.0.0.1:8080/uploads/products/iphone16.png'),
      );

      // 2. 10.0.2.2 (Android Emulator loopback) chuyển thành 127.0.0.1
      expect(
        ApiClient.fixUrl(androidEmulatorUrl),
        equals('http://127.0.0.1:8080/uploads/products/macbook.png'),
      );

      // 3. IP Wi-Fi tĩnh cũ (192.168.x.x) chuyển thành 127.0.0.1
      expect(
        ApiClient.fixUrl(lanWifiUrl),
        equals('http://127.0.0.1:8080/uploads/products/ipad.png'),
      );
      expect(
        ApiClient.fixUrl(lanWifiSubnetUrl),
        equals('http://127.0.0.1:8080/uploads/products/watch.png'),
      );

      // 4. Đường dẫn tương đối tự động ghép thêm baseUrl
      expect(
        ApiClient.fixUrl(relativeUrl),
        equals('${ApiClient.baseUrl}/uploads/products/airpods.png'),
      );

      // 5. Đường dẫn đã chuẩn giữ nguyên
      expect(
        ApiClient.fixUrl(alreadyFixedUrl),
        equals('http://127.0.0.1:8080/uploads/products/case.png'),
      );

      // 6. Xử lý an toàn chuỗi rỗng / null / whitespace
      expect(ApiClient.fixUrl(null), equals(''));
      expect(ApiClient.fixUrl(''), equals(''));
      expect(ApiClient.fixUrl('   '), equals(''));
    });
  });
}

