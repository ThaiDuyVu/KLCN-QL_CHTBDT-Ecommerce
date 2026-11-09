import 'package:dio/dio.dart';
import 'package:dio_cookie_manager/dio_cookie_manager.dart';
import 'auth_interceptor.dart';
import 'csrf_manager.dart';
import 'dart:io';
import '../storage/cookie_storage.dart';

class ApiClient {
  static const baseUrl = String.fromEnvironment(
    'API_BASE_URL',
    defaultValue: 'http://127.0.0.1:8080',
  );

  // Service Token của Cloudflare Access (chỉ dùng khi gọi server qua shop.devthaiduyvu.bid).
  // Truyền lúc build bằng --dart-define, không ghi cứng vào mã nguồn.
  static const _cfAccessClientId = String.fromEnvironment(
    'CF_ACCESS_CLIENT_ID',
  );
  static const _cfAccessClientSecret = String.fromEnvironment(
    'CF_ACCESS_CLIENT_SECRET',
  );
  static const Map<String, String> accessHeaders =
      _cfAccessClientId == '' || _cfAccessClientSecret == ''
      ? {}
      : {
          'CF-Access-Client-Id': _cfAccessClientId,
          'CF-Access-Client-Secret': _cfAccessClientSecret,
        };

  static const _chatBaseUrl = String.fromEnvironment('CHAT_BASE_URL');

  /// Where `/api/v1/chat` is served, mirroring the web: behind a gateway (Caddy, Cloudflare) it shares
  /// [baseUrl]; when [baseUrl] is the backend itself (port 8080, local dev), the chatbot service listens on
  /// port 8090 of the same host, like the Vite proxy. `--dart-define=CHAT_BASE_URL=...` overrides both.
  static String get chatBaseUrl {
    if (_chatBaseUrl.isNotEmpty) return _chatBaseUrl;
    final uri = Uri.parse(baseUrl);
    return uri.port == 8080 ? uri.replace(port: 8090).toString().replaceFirst(RegExp(r'/$'), '') : baseUrl;
  }

  static String fixUrl(String? rawUrl) {
    if (rawUrl == null || rawUrl.isEmpty) return '';
    final trimmed = rawUrl.trim();
    if (trimmed.isEmpty) return '';

    // Nếu là đường dẫn tương đối (vd: /uploads/img.png)
    if (trimmed.startsWith('/')) {
      return '$baseUrl$trimmed';
    }

    // Quy đổi tất cả localhost, 10.0.2.2 và mọi IP 192.168.x.x về 127.0.0.1
    String fixed = trimmed
        .replaceAll('localhost', '127.0.0.1')
        .replaceAll('10.0.2.2', '127.0.0.1')
        .replaceAll(RegExp(r'192\.168\.\d+\.\d+'), '127.0.0.1');

    return fixed;
  }

  Dio get client => dio;
  static Dio? _dio;
  static Dio get dio => _dio ??= Dio(
    BaseOptions(
      baseUrl: baseUrl,
      connectTimeout: const Duration(seconds: 10),
      receiveTimeout: const Duration(seconds: 10),
      headers: accessHeaders,
    ),
  );
  static set dio(Dio value) => _dio = value;
  static final CsrfManager csrfManager = CsrfManager();

  static void init() {
    dio = Dio(
      BaseOptions(
        baseUrl: baseUrl,
        connectTimeout: const Duration(seconds: 10),
        receiveTimeout: const Duration(seconds: 10),
        headers: accessHeaders,
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
        if (cookie.name == 'XSRF-TOKEN' && cookie.value.isNotEmpty) {
          return cookie.value;
        }
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
          headers: accessHeaders,
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
