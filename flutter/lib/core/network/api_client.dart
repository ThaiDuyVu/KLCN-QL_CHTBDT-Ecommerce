import 'package:dio/dio.dart';
import 'package:dio_cookie_manager/dio_cookie_manager.dart';
import 'auth_interceptor.dart';
import 'csrf_manager.dart';
import 'dart:io';
import '../storage/cookie_storage.dart';

class ApiClient {
  static const baseUrl = String.fromEnvironment(
    'API_BASE_URL',
    defaultValue: 'http://10.0.2.2:8080',
  );
  Dio get client => dio;
  static late final Dio dio;
  static final CsrfManager csrfManager = CsrfManager();

  static void init() {
    dio = Dio(
      BaseOptions(
        baseUrl: baseUrl,
        connectTimeout: const Duration(seconds: 10),
        receiveTimeout: const Duration(seconds: 10),
      ),
    );

    dio.interceptors.add(CookieManager(CookieStorage.cookieJar));
    dio.interceptors.add(
      AuthInterceptor(CookieStorage.cookieJar, ensureCsrfToken),
    );
  }

  static Future<String>? _csrfRequest;

  static Future<String> ensureCsrfToken() {
    return _csrfRequest ??= _loadCsrfToken().whenComplete(
      () => _csrfRequest = null,
    );
  }

  static Future<String> _loadCsrfToken() async {
    final uri = Uri.parse('$baseUrl/api/auth/csrf');
    final jar = CookieStorage.cookieJar;
    String? readToken(List<Cookie> cookies) {
      for (final cookie in cookies) {
        if (cookie.name == 'XSRF-TOKEN' && cookie.value.isNotEmpty)
          return cookie.value;
      }
      return null;
    }

    var token = readToken(await jar.loadForRequest(uri));
    if (token == null) {
      final csrfClient = Dio(
        BaseOptions(
          baseUrl: baseUrl,
          connectTimeout: const Duration(seconds: 10),
          receiveTimeout: const Duration(seconds: 10),
        ),
      );
      csrfClient.interceptors.add(CookieManager(jar));
      try {
        await csrfClient.get('/api/auth/csrf');
      } finally {
        csrfClient.close();
      }
      token = readToken(await jar.loadForRequest(uri));
    }
    if (token == null) throw StateError('Backend không gửi cookie CSRF hợp lệ');
    csrfManager.setToken(token);
    return token;
  }

  static void resetCsrf() {
    csrfManager.token = null;
  }
}
