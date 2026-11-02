import 'dart:io';
import 'dart:typed_data';
import 'package:dio/dio.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:ecommerce_app/core/network/api_client.dart';
import 'package:ecommerce_app/core/storage/cookie_storage.dart';
import 'package:ecommerce_app/features/auth/data/auth_repository.dart';
import 'package:ecommerce_app/features/auth/presentation/providers/auth_provider.dart';

class MockFlutterSecureStorage extends Mock implements FlutterSecureStorage {}

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
  group('6.1. Security Cache Test (Chống lưu dữ liệu nhạy cảm xuống Local Storage)', () {
    late MockFlutterSecureStorage mockSecureStorage;

    setUp(() {
      mockSecureStorage = MockFlutterSecureStorage();
    });

    test('Mô phỏng đăng nhập thành công: KHÔNG có bất kỳ key nhạy cảm (jwt, access_token, password) nào bị ghi vào SecureStorage', () async {
      // Arrange
      final writtenKeys = <String>[];

      // Đăng ký mock handler bắt mọi lệnh write vào storage
      when(() => mockSecureStorage.write(
            key: any(named: 'key'),
            value: any(named: 'value'),
          )).thenAnswer((invocation) async {
        final key = invocation.namedArguments[#key] as String;
        writtenKeys.add(key);
      });

      // Mock Dio trả về Set-Cookie trong header của response đăng nhập (chuẩn Cookie Browser)
      final dio = Dio(BaseOptions(baseUrl: 'http://test.local'));
      dio.httpClientAdapter = _MockAdapter((options) async {
        if (options.path == '/api/auth/login') {
          return ResponseBody.fromString(
            '{"id":"u1","username":"customer1","roleName":"CUSTOMER"}',
            200,
            headers: {
              Headers.contentTypeHeader: ['application/json'],
              'set-cookie': [
                'ACCESS_TOKEN=fake-jwt-token; Path=/; HttpOnly',
                'REFRESH_TOKEN=fake-refresh-token; Path=/api/auth; HttpOnly',
              ],
            },
          );
        }
        return ResponseBody.fromString('{}', 200);
      });

      // Giả lập CookieJar đã có sẵn cookie XSRF-TOKEN
      await CookieStorage.cookieJar.saveFromResponse(
        Uri.parse('${ApiClient.baseUrl}/api/auth/csrf'),
        [Cookie('XSRF-TOKEN', 'fake-csrf-token')..path = '/'],
      );

      final repo = AuthRepository(client: dio);
      final notifier = AuthNotifier(repo);

      // Act: Thực hiện đăng nhập
      final loginSuccess = await notifier.login('customer1', 'Password@123');

      // Assert:
      // 1. Đăng nhập thành công và trạng thái là authenticated
      expect(loginSuccess, isTrue);
      expect(notifier.state.status, equals(AuthStateStatus.authenticated));

      // 2. Không được phép ghi thủ công các key nhạy cảm vào FlutterSecureStorage hay SharedPreferences
      expect(
        writtenKeys.contains('jwt'),
        isFalse,
        reason: 'Nghiêm cấm lưu jwt thủ công xuống local storage; phải để CookieJar tự quản lý',
      );
      expect(
        writtenKeys.contains('access_token'),
        isFalse,
        reason: 'Nghiêm cấm lưu access_token thủ công',
      );
      expect(
        writtenKeys.contains('password'),
        isFalse,
        reason: 'Tuyệt đối không lưu mật khẩu người dùng vào storage',
      );
      expect(
        writtenKeys.contains('token'),
        isFalse,
        reason: 'Tuyệt đối không lưu token thủ công',
      );

      // 3. Toàn bộ danh sách key ghi xuống storage phải hoàn toàn rỗng
      expect(writtenKeys, isEmpty);

      dio.close();
    });
  });
}
