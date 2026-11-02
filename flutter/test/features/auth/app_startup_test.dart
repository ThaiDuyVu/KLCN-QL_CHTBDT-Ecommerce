import 'dart:typed_data';
import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:ecommerce_app/features/auth/data/auth_repository.dart';
import 'package:ecommerce_app/features/auth/presentation/providers/auth_provider.dart';

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
  group('3. Startup & Auth State Test (Luồng khởi động)', () {
    test('Kịch bản 1: Mở app gọi GET /api/auth/me trả về 200 -> AuthState = authenticated', () async {
      // Arrange
      final dio = Dio(BaseOptions(baseUrl: 'http://test.local'));
      dio.httpClientAdapter = _MockAdapter((options) async {
        if (options.path == '/api/auth/me') {
          return ResponseBody.fromString(
            '{"id":"usr-001","username":"senior_tester","roleName":"CUSTOMER"}',
            200,
            headers: {
              Headers.contentTypeHeader: ['application/json'],
            },
          );
        }
        return ResponseBody.fromString('Not Found', 404);
      });

      final repo = AuthRepository(client: dio);

      // Act: Khởi tạo AuthNotifier (tự động kích hoạt _init/checkAuth)
      final notifier = AuthNotifier(repo);
      await notifier.checkAuth();

      // Assert
      expect(notifier.state.status, equals(AuthStateStatus.authenticated));
      expect(notifier.state.user?['username'], equals('senior_tester'));
      expect(notifier.state.user?['roleName'], equals('CUSTOMER'));

      dio.close();
    });

    test('Kịch bản 2: Mở app gọi GET /api/auth/me bị 401, gọi refresh thất bại -> Chuyển về unauthenticated và dọn sạch session', () async {
      // Arrange
      final calls = <String>[];
      final dio = Dio(BaseOptions(baseUrl: 'http://test.local'));

      dio.httpClientAdapter = _MockAdapter((options) async {
        calls.add('${options.method} ${options.path}');
        if (options.path == '/api/auth/me') {
          return ResponseBody.fromString(
            '{"error":"Token expired"}',
            401,
            headers: {
              Headers.contentTypeHeader: ['application/json'],
            },
          );
        }
        if (options.path == '/api/auth/refresh') {
          return ResponseBody.fromString(
            '{"error":"Refresh token invalid or expired"}',
            401,
            headers: {
              Headers.contentTypeHeader: ['application/json'],
            },
          );
        }
        return ResponseBody.fromString('Not Found', 404);
      });

      final repo = AuthRepository(client: dio);

      // Act
      final notifier = AuthNotifier(repo);
      await notifier.checkAuth();

      // Assert:
      // Phải gọi GET /api/auth/me trước, khi bị 401 thì gọi tiếp POST /api/auth/refresh
      expect(calls, contains('GET /api/auth/me'));
      expect(calls, contains('POST /api/auth/refresh'));

      // Khi refresh thất bại, trạng thái phải là unauthenticated để router đẩy về route Login
      expect(notifier.state.status, equals(AuthStateStatus.unauthenticated));
      expect(notifier.state.user, isNull);

      dio.close();
    });

    test('Kịch bản 3: Mở app gọi GET /api/auth/me bị 401, nhưng refresh thành công -> AuthState = authenticated', () async {
      // Arrange
      int meCount = 0;
      final dio = Dio(BaseOptions(baseUrl: 'http://test.local'));

      dio.httpClientAdapter = _MockAdapter((options) async {
        if (options.path == '/api/auth/me') {
          meCount++;
          if (meCount == 1) {
            // Lần 1: access token hết hạn
            return ResponseBody.fromString('{"error":"Token expired"}', 401);
          }
          // Lần 2 (sau refresh): trả về thông tin user mới
          return ResponseBody.fromString(
            '{"id":"usr-002","username":"refreshed_user","roleName":"CUSTOMER"}',
            200,
            headers: {
              Headers.contentTypeHeader: ['application/json'],
            },
          );
        }
        if (options.path == '/api/auth/refresh') {
          return ResponseBody.fromString(
            '{"message":"Refreshed successfully"}',
            200,
            headers: {
              Headers.contentTypeHeader: ['application/json'],
            },
          );
        }
        return ResponseBody.fromString('Not Found', 404);
      });

      final repo = AuthRepository(client: dio);

      // Act
      final notifier = AuthNotifier(repo);
      await notifier.checkAuth();

      // Assert
      expect(notifier.state.status, equals(AuthStateStatus.authenticated));
      expect(notifier.state.user?['username'], equals('refreshed_user'));

      dio.close();
    });
  });
}
