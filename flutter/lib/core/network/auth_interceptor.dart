import 'package:cookie_jar/cookie_jar.dart';
import 'package:dio/dio.dart';

/// Uses the same jar as CookieManager, preserving scoped refresh cookies.
class AuthInterceptor extends Interceptor {
  final CookieJar cookies;
  final Future<String> Function() ensureCsrfToken;
  final Future<bool> Function()? onRefreshToken;
  final Dio? dio;

  static Future<bool>? _refreshFuture;

  AuthInterceptor(
    this.cookies,
    this.ensureCsrfToken, {
    this.onRefreshToken,
    this.dio,
  });

  @override
  Future<void> onRequest(
    RequestOptions options,
    RequestInterceptorHandler handler,
  ) async {
    if (['GET', 'HEAD', 'OPTIONS'].contains(options.method.toUpperCase())) {
      handler.next(options);
      return;
    }
    try {
      final token = await ensureCsrfToken();
      // CookieManager already ran; reload after CSRF initialization to include
      // the newly issued cookie, along with every cookie matching this URI.
      final values = await cookies.loadForRequest(options.uri);
      options.headers['cookie'] = values
          .map((c) => '${c.name}=${c.value}')
          .join('; ');
      options.headers['X-XSRF-TOKEN'] = token;
      handler.next(options);
    } catch (error) {
      handler.reject(
        DioException(
          requestOptions: options,
          error: error,
          message: 'Không lấy được CSRF. Thao tác chưa được gửi.',
          type: DioExceptionType.unknown,
        ),
      );
    }
  }

  @override
  Future<void> onError(
    DioException err,
    ErrorInterceptorHandler handler,
  ) async {
    final path = err.requestOptions.path;
    final statusCode = err.response?.statusCode;

    // Không chặn lặp lại khi chính request refresh hoặc login bị lỗi
    if (statusCode == 401 &&
        !path.contains('/api/auth/refresh') &&
        !path.contains('/api/auth/login')) {
      if (onRefreshToken != null) {
        try {
          // Single-flight refresh: chỉ 1 request refresh được bay đi tại một thời điểm
          _refreshFuture ??= onRefreshToken!().whenComplete(() {
            _refreshFuture = null;
          });

          final success = await _refreshFuture!;
          if (success) {
            // Thử lại request ban đầu sau khi đã refresh token thành công
            final opts = err.requestOptions;
            final client = dio ?? Dio(BaseOptions(baseUrl: opts.baseUrl));
            final response = await client.fetch(opts);
            return handler.resolve(response);
          }
        } catch (refreshErr) {
          return handler.reject(
            DioException(
              requestOptions: err.requestOptions,
              error: refreshErr,
              message: 'Phiên làm việc hết hạn. Vui lòng đăng nhập lại.',
              type: DioExceptionType.badResponse,
              response: err.response,
            ),
          );
        }
      }
    }
    handler.next(err);
  }
}
